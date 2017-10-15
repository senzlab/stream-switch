FROM ubuntu:14.04

MAINTAINER Eranga Bandara (erangaeb@gmail.com)

# install java and other required packages
RUN apt-get update -y
RUN apt-get install -y python-software-properties
RUN apt-get install -y software-properties-common
RUN add-apt-repository -y ppa:webupd8team/java
RUN apt-get update -y

# install java
RUN echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | debconf-set-selections
RUN add-apt-repository -y ppa:webupd8team/java
RUN apt-get update -y
RUN apt-get install -y oracle-java8-installer
RUN rm -rf /var/lib/apt/lists/*
RUN rm -rf /var/cache/oracle-jdk8-installer

# set JAVA_HOME
ENV JAVA_HOME /usr/lib/jvm/java-8-oracle

# set switch mode env
ENV SWITCH_MODE DEV

# set service variables
ENV MONGO_HOST dev.localhost
ENV MONGO_PORT 27017

# working directory
WORKDIR /app

# copy file
ADD target/scala-2.11/stream-switch-assembly-1.0.jar switch.jar

# logs volume
RUN mkdir logs
VOLUME ["/app/logs"]

# .keys volume
VOLUME ["/app/.keys"]

# Service run on 9090 port
EXPOSE 9090

# command
ENTRYPOINT [ "java", "-jar", "/app/switch.jar" ]
