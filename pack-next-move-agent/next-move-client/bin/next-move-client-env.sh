#!/usr/bin/env bash
if [ -z "${NEXT_MOVE_CLIENT_HOME}" ]; then
  export NEXT_MOVE_CLIENT_HOME="$(cd "`dirname "$0"`"/..; pwd)"
fi
if [ -z "${NEXT_MOVE_CLIENT_LOG}" ]; then
  export NEXT_MOVE_CLIENT_LOG=/Users/anucha.r/MyWork/tmp/test-pack-nextmove/data/logs/blendata/next-move-client
  # export NEXT_MOVE_CLIENT_LOG=/data/logs/blendata/next-move-client
fi
mkdir -p ${NEXT_MOVE_CLIENT_LOG}

echo "NEXT_MOVE_CLIENT_LOG=${NEXT_MOVE_CLIENT_LOG}"
