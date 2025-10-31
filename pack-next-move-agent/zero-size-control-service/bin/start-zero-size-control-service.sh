#!/usr/bin/env bash
if [ -z "${NEXT_MOVE_SERVICE_HOME}" ]; then
  export NEXT_MOVE_SERVICE_HOME="$(cd "`dirname "$0"`"/..; pwd)"
fi

. "${NEXT_MOVE_SERVICE_HOME}/bin/zero-size-control-service-env.sh"

echo "Starting Zero-Size-Control service..."
nohup java -Xms1g -Xmx1g -jar ${NEXT_MOVE_SERVICE_HOME}/lib/next-move-service-*.jar \
--spring.config.location=file:${NEXT_MOVE_SERVICE_HOME}/config/ \
--spring.resources.static-locations=file:${NEXT_MOVE_SERVICE_HOME}/web/ \
>${NEXT_MOVE_SERVICE_LOG}/stdout.log 2>${NEXT_MOVE_SERVICE_LOG}/stderr.log &echo $!>${NEXT_MOVE_SERVICE_HOME}/pid/zero-size-control-service.txt
