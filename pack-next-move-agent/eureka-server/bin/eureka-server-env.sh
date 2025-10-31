#!/usr/bin/env bash
if [ -z "${EUREKA_SERVER_HOME}" ]; then
  export EUREKA_SERVER_HOME="$(cd "`dirname "$0"`"/..; pwd)"
fi

if [ -z "${EUREKA_SERVER_LOG}" ]; then
  export EUREKA_SERVER_LOG=/data/logs/blendata/eureka_server
fi
mkdir -p ${EUREKA_SERVER_LOG}

echo "EUREKA_SERVER_LOG=${EUREKA_SERVER_LOG}"
