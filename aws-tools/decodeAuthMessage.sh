#!/bin/bash
# Needs sts:DecodeAuthorizationMessage privilege to execute.
# No production guarantee.
aws sts decode-authorization-message --encoded-message ${1} --query 'DecodedMessage' | sed -e 's/\\"/"/g' -e 's/"{/{/g' -e 's/}"/}/g' | python -m json.tool
