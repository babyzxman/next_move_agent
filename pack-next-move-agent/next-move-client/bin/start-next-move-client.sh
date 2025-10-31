#!/usr/bin/env bash
if [ -z "${NEXT_MOVE_CLIENT_HOME}" ]; then
  export NEXT_MOVE_CLIENT_HOME="$(cd "`dirname "$0"`"/..; pwd)"
fi

. "${NEXT_MOVE_CLIENT_HOME}/bin/next-move-client-env.sh"

echo "Starting Nextmove Client..."
nohup java -Xms1g -Xmx1g -jar ${NEXT_MOVE_CLIENT_HOME}/lib/next-move-client-*.jar \
--spring.config.location=file:${NEXT_MOVE_CLIENT_HOME}/config/ \
--spring.resources.static-locations=file:${NEXT_MOVE_CLIENT_HOME}/web/ \
>${NEXT_MOVE_CLIENT_LOG}/stdout.log 2>${NEXT_MOVE_CLIENT_LOG}/stderr.log &echo $!>${NEXT_MOVE_CLIENT_HOME}/pid/next-move-client.txt

