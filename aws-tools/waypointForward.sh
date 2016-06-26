TAG=${1}
PROFILE=${2}
KEY=${TAG}-${PROFILE}-key
while [ true ]
do
sleep 1
WAYPOINT_PUBLIC_IP=`aws ec2 describe-instances --filters "Name=instance-state-name,Values=running" "Name=tag-key,Values=Name" "Name=tag-value,Values=${TAG}" --profile ${PROFILE} --query "Reservations[0].Instances[0].PublicIpAddress" --output text`
ssh -i ~/${KEY}.pem -ND 8887 ec2-user@${WAYPOINT_PUBLIC_IP}

done
