#!/bin/bash
# NO PRODUCTION SAFE WARRANTY!!
# Using GET to retrieve all meta data on EC2 instance.
# version 0.1
digger () {
PADDRESS=$1;
for ENTRY in `GET $PADDRESS`;
        do
                LEN=$((${#ENTRY}-1));
                LC=${ENTRY:$LEN:1};
                if [ $LC == "/" ]; then # Container
                        NEWPADDRESS=$PADDRESS$ENTRY;
                        digger $NEWPADDRESS
                else # Leaf
                        T=`echo $ENTRY | grep "0="`;
                        if [ ! -z $T ]; then
                                ENTRY="0/openssh-key";
                        fi;
                        LEAF="$PADDRESS$ENTRY";
                        CNAME=`echo $LEAF | sed -e 's/^.\{39\}//'`
                        echo "$CNAME: `GET $LEAF`";
                fi;
        done;
echo
PADDRESS=`echo $PADDRESS | sed -e 's/\/[^/]*\/$/\//g'`
}
digger http://169.254.169.254/latest/meta-data/
