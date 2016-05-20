ASG_NAME=beijing-bastion-asg-v1
RAID_MOUNT=/home/ec2-user/git

sudo fsfreeze --freeze ${RAID_MOUNT}
sleep 10;
echo "File system frozen."
IID=`curl -s http://169.254.169.254/latest/meta-data/instance-id`
echo "Instance ID: ${IID}"
DEV=`mount | grep /home/ec2-user/git | sed 's/on .*//g'`
echo "RAID Device: ${DEV}"
echo "Member Device: "
for ENTRY in $(sudo mdadm --detail ${DEV} | grep -A33 "RaidDevice State" | grep -v "RaidDevice State" | sed "s/.*\/dev//g")
do
	echo -n "/dev${ENTRY} -> "
	XDEV=`ls -l /dev${ENTRY} | sed 's/.*-> //'`
	echo -n "/dev/${XDEV} -> "
	VOL=`aws ec2 describe-volumes --filters "Name=attachment.instance-id,Values=${IID}" "Name=attachment.device,Values=/dev/${XDEV}" --query "Volumes[0].VolumeId" | sed 's/"//g'`
	echo -n "${VOL} -> "
	SSID=`aws ec2 create-snapshot --volume-id ${VOL} --description "Snapshot member for ${RAID_MOUNT}" --query "SnapshotId" | sed 's/"//g'` 
	echo -n "${SSID}"
	echo
	SS_LAST_NAME=`aws ec2 describe-snapshots --filters "Name=tag-key,Values=Name" "Name=tag-value,Values=git-snapshot*" --query "Snapshots[*].Tags[?Key=='Name'].[Value]" | grep "\"" | sed 's/ *"//g' | sort -r | sed -n 1p`
	echo "Last snapshot Name Tag was: ${SS_LAST_NAME}"
	SS_LAST_NAME_VERSION=`echo ${SS_LAST_NAME} | sed 's/.*-v//'`
	SS_LAST_NAME_PREFIX=`echo ${SS_LAST_NAME} | sed 's/[[:digit:]]*$//'`
	TODAY_PREFIX=git-snapshot-$(date +%F)-v
        if [ $TODAY_PREFIX != $SS_LAST_NAME_PREFIX ]; then
		CURRENT_SS_NAME=git-snapshot-$(date +%F)-v1
	else
		CURRENT_SS_NAME=${SS_LAST_NAME_PREFIX}$((SS_LAST_NAME_VERSION+1))
	fi	
	aws ec2 create-tags --resources ${SSID} --tags Key=Name,Value=${CURRENT_SS_NAME}
	echo
	echo "Tagging snapshot with Name: ${CURRENT_SS_NAME}"
done
echo "All members are being snapshot..."
sleep 2;
sudo fsfreeze --unfreeze ${RAID_MOUNT}
echo "File system resumed."

# Refresh EBS Volume in another AZ
CURRENT_AZ=`curl -s http://169.254.169.254/latest/meta-data/placement/availability-zone`
PEER_AZ=`aws autoscaling describe-auto-scaling-groups --query "AutoScalingGroups[?AutoScalingGroupName=='${ASG_NAME}'].AvailabilityZones" --output text | sed "s/${CURRENT_AZ}//g" | sed 's/\t//g'`
echo -n "Waiting on snapshot completion ..."
aws ec2 wait snapshot-completed --snapshot-ids ${SSID}
echo "done"
echo -n "Refreshing EBS in Peer AZ ... "
PEER_NEW_VOL=`aws ec2 create-volume --snapshot-id ${SSID} --availability-zone ${PEER_AZ} --volume-type gp2 --query "VolumeId" --output text`
echo -n "Creating new EBS from snapshot ... "
aws ec2 wait volume-available --volume-ids ${PEER_NEW_VOL}
PEER_OLD_VOL=`aws ec2 describe-volumes --filters "Name=tag-key,Values=Name" "Name=tag-value,Values=git-${PEER_AZ}-v1" "Name=status,Values=available" --query "Volumes[0].VolumeId" --output text`
aws ec2 create-tags --resources ${PEER_NEW_VOL} --tags Key=Name,Value=git-${PEER_AZ}-v1
echo -n "Deleting old EBS ... "
aws ec2 delete-volume --volume-id ${PEER_OLD_VOL}
echo "done"

