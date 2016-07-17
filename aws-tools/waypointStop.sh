PROFILE=<your_profile_name>
TAG=waypoint
WAYPOINT_ID=`aws ec2 describe-instances --filters "Name=instance-state-name,Values=running" "Name=tag-key,Values=Name" "Name=tag-value,Values=${TAG}" --query "Reservations[*].Instances[*].InstanceId" --output text --profile ${PROFILE}`
echo "WAYPOINT_ID: ${WAYPOINT_ID}"

CID=`echo $WAYPOINT_ID | sed 's/ */#/g'`

if [ $CID = "#" ]; then
  echo "${TAG} was already gone."
else
  aws ec2 terminate-instances --instance-ids ${WAYPOINT_ID} --profile ${PROFILE}
  aws ec2 wait instance-terminated --instance-ids ${WAYPOINT_ID} --profile ${PROFILE}
fi
