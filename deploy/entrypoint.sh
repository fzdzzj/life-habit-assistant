#!/bin/sh
set -eu

exec java -XX:MaxRAMPercentage=75.0 $JAVA_OPTS -jar /app/app.jar
