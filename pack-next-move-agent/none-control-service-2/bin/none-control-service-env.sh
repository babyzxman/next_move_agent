#!/usr/bin/env bash
if [ -z "${NEXT_MOVE_SERVICE_HOME}" ]; then
  export NEXT_MOVE_SERVICE_HOME="$(cd "`dirname "$0"`"/..; pwd)"
fi

if [ -z "${NEXT_MOVE_SERVICE_DATA}" ]; then
  export NEXT_MOVE_SERVICE_DATA=/Users/anucha.r/MyWork/tmp/test-pack-nextmove/data/next-move-service
  # export NEXT_MOVE_SERVICE_DATA=/data/next-move-service
fi
mkdir -p ${NEXT_MOVE_SERVICE_DATA}

if [ -z "${NEXT_MOVE_SERVICE_LOG}" ]; then
  export NEXT_MOVE_SERVICE_LOG=/Users/anucha.r/MyWork/tmp/test-pack-nextmove/data/logs/blendata/next-move-service/none-ctrl-02
  # export NEXT_MOVE_SERVICE_LOG=/data/logs/blendata/next-move-service
fi
mkdir -p ${NEXT_MOVE_SERVICE_LOG}

echo "NEXT_MOVE_SERVICE_LOG=${NEXT_MOVE_SERVICE_LOG}"
