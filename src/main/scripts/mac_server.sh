#!/bin/sh
mkdir -p ~/.smartclient
pid=$(cat ~/.smartclient/pid 2>/dev/null) || pid=0
ps -p $pid > /dev/null 2>&1 && exit 0
nohup ../MacOS/server < /dev/null > /dev/null 2>&1 &
pid=$!
sleep 1
ps -p $pid > /dev/null 2>&1 || exit 1
echo $pid | cat > ~/.smartclient/pid
