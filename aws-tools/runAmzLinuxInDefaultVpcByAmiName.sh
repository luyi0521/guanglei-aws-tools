#!/bin/bash
KEY=${1}
SGID=${2}
TAG=${3}
PROFILE=${4}
AMIID=`aws ec2 describe-images --filters "Name=name,Values=*amzn-ami-hvm*gp2" "Name=architecture,Values=x86_64" "Name=hypervisor,Values=xen" "Name=owner-alias,Values=amazon" "Name=state,Values=available" "Name=virtualization-type,Values=hvm" --query "Images[*].ImageId" --profile ${PROFILE} | sed -En 's/^.*\"(ami-[[:xdigit:]]{1,})\".*$/\1/p' | sed -n 1p`
echo "AMIID: ${AMIID}"

ID=`aws ec2 run-instances \
       --image-id ${AMIID} \
       --key-name ${KEY} \
       --security-group-ids ${SGID} \
       --instance-type t2.large \
       --count 1 \
       --associate-public-ip-address \
       --profile ${PROFILE} \
       --query "Instances[0].InstanceId" | sed s/\"//g`
echo "instance-id: ${ID}"
