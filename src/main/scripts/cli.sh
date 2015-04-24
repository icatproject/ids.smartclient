#!/bin/sh
c=/home/fisher/workspace/ids.smartclient/target/classes
c=$c:/home/fisher/.m2/repository/net/sf/jopt-simple/jopt-simple/4.8/jopt-simple-4.8.jar
c=$c:/home/fisher/.m2/repository/org/apache/httpcomponents/httpclient/4.3.4/httpclient-4.3.4.jar
c=$c:/home/fisher/.m2/repository/org/apache/httpcomponents/httpcore/4.3.2/httpcore-4.3.2.jar
c=$c:/home/fisher/.m2/repository/org/glassfish/javax.json/1.0.4/javax.json-1.0.4.jar
c=$c:/home/fisher/.m2/repository/commons-logging/commons-logging/1.1.3/commons-logging-1.1.3.jar
java -classpath $c org.icatproject.ids.smartclient.Cli "$@"