#!/usr/bin/env bash
if [ -z "${EUREKA_SERVER_HOME}" ]; then
  export EUREKA_SERVER_HOME="$(cd "`dirname "$0"`"/..; pwd)"
fi

. "${EUREKA_SERVER_HOME}/bin/eureka-server-env.sh"

echo "Starting Eureka server..."
nohup java -Xms1g -Xmx1g -jar ${EUREKA_SERVER_HOME}/lib/eureka-*.jar \
--spring.config.location=file:${EUREKA_SERVER_HOME}/config/ \
--spring.resources.static-locations=file:${EUREKA_SERVER_HOME}/web/ \
>${EUREKA_SERVER_LOG}/stdout.log 2>${EUREKA_SERVER_LOG}/stderr.log &echo $!>${EUREKA_SERVER_HOME}/pid/eureka-server.txt

