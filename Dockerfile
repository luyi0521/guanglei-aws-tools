FROM centos:6
RUN yum -y perl-libwww-perl && yum -y links && mkdir /aws-tools
COPY * /aws-tools/
