 #!/bin/bash
if [ -z "${NEXT_MOVE_SERVICE_HOME}" ]; then
  export NEXT_MOVE_SERVICE_HOME="$(cd "`dirname "$0"`"/..; pwd)"
fi

MODULE_NAME="none-control-service"

recheckAndForceKill() {
	pid=$1
	ps -p $pid > /dev/null
	if [ $? -eq 0 ]; then
		echo "$MODULE_NAME unsuccessfully stopped, wait for 5s and force kill process..."
		sleep 5
		kill -9 $pid
		echo "$MODULE_NAME is stopped..."
	else
		echo "$MODULE_NAME is stopped successfully..."
	fi
}

pid=""
fpid=$NEXT_MOVE_SERVICE_HOME/pid/$MODULE_NAME.txt
if [ -f $fpid ]; then
	pid=$(<$fpid)
	echo stopping process id [$pid]
	if [ ! -z $pid ]; then
		ps -p $pid > /dev/null
		if [ $? -eq 0 ]; then
			kill -9 $pid
			echo "wait for 5s before recheck if process is killed successfully..."
			sleep 5
			recheckAndForceKill $pid
			echo $MODULE_NAME server is stopped...
		else
			echo $MODULE_NAME server is not running...
		fi
	else
		echo $MODULE_NAME server is not started...
	fi
else
	echo $MODULE_NAME server is not started...
fi
