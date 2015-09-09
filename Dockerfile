FROM centos:6
COPY * .
RUN yum -y perl-libwww-perl && yum -y links
