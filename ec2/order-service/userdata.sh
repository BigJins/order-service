#!/bin/bash
set -e
apt-get update -y
apt-get install -y openjdk-21-jdk curl

mkdir -p /home/ubuntu/app
chown ubuntu:ubuntu /home/ubuntu/app
echo "ready" > /home/ubuntu/ready.txt
