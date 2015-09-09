#!/bin/bash
# NO PRODUCTION SAFE WARRANTY!!
# For EC2 using single security group on eth0. You can modify it to work in more complex case.
# You must manually clone https://github.com/sebsto/tsunami-udp.git under ~/git directory.
# And then, comment out ./distribute.sh in bootstrap.sh.

BSSGID=<sg-id-your-ec2-currently-using>
TMSGID=<sg-id-allowing-tsunami-traffic>
IID=`GET http://169.254.169.254/latest/meta-data/instance-id`
MAC=`GET http://169.254.169.254/latest/meta-data/mac`
INFID=`GET http://169.254.169.254/latest/meta-data/network/interfaces/macs/${MAC}/interface-id`
aws ec2 modify-network-interface-attribute --network-interface-id ${INFID} --groups ${BSSGID} ${TMSGID}
sudo /home/ec2-user/git/tsunami-udp/AWS/bootstrap.sh
cd /usr/local/tsunami-udp/
sudo make install
