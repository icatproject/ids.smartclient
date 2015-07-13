#!/bin/sh
pid=$(cat ~/.smartclient/pid 2>/dev/null) || pid=0
ps -p $pid > /dev/null 2>&1 && exit 0
c=$HOME/workspace/ids.smartclient/target/classes
c=$c:$HOME/.m2/repository/ch/qos/logback/logback-classic/1.1.3/logback-classic-1.1.3.jar
c=$c:$HOME/.m2/repository/org/slf4j/slf4j-api/1.7.7/slf4j-api-1.7.7.jar
c=$c:$HOME/.m2/repository/ch/qos/logback/logback-core/1.1.3/logback-core-1.1.3.jar
c=$c:$HOME/.m2/repository/ch/qos/logback/logback-classic/1.1.3/logback-classic-1.1.3.jar
c=$c:$HOME/.m2/repository/org/icatproject/ids.client/1.3.0-SNAPSHOT/ids.client-1.3.0-SNAPSHOT.jar
c=$c:$HOME/.m2/repository/org/icatproject/icat.client/4.6.0-SNAPSHOT/icat.client-4.6.0-SNAPSHOT.jar
c=$c:$HOME/.m2/repository/org/apache/httpcomponents/httpclient/4.3.4/httpclient-4.3.4.jar
c=$c:$HOME/.m2/repository/org/apache/httpcomponents/httpcore/4.3.2/httpcore-4.3.2.jar
c=$c:$HOME/.m2/repository/org/glassfish/javax.json/1.0.4/javax.json-1.0.4.jar
c=$c:$HOME/.m2/repository/commons-logging/commons-logging/1.1.3/commons-logging-1.1.3.jar
c=$c:$HOME/.m2/repository/org/apache/httpcomponents/httpmime/4.3.4/httpmime-4.3.4.jar
nohup java -classpath $c org.icatproject.ids.smartclient.Server "$@" < /dev/null > ~/.smartclient/log 2>&1 &
pid=$!
sleep 1
ps -p $pid > /dev/null 2>&1 || exit 1
echo $pid | cat > ~/.smartclient/pid
