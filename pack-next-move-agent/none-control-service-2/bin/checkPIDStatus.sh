#!/bin/bash

if [ -z "${NEXT_MOVE_SERVICE_HOME}" ]; then
  export NEXT_MOVE_SERVICE_HOME="$(cd "`dirname "$0"`"/..; pwd)"
fi

bold=$(tput bold)
red=$(tput setaf 1)
green=$(tput setaf 2)
normal=$(tput sgr0)
checkStatus() {
	r="${red}stopped"
	pid=''
	fpid=$NEXT_MOVE_SERVICE_HOME/pid/$1.txt
	if [ -f $fpid ]; then
		pid=$(<$fpid)
		if [ ! -z $pid ]; then
			ps -p $pid > /dev/null
			if [ $? -eq 0 ]; then
				r="${green}running"
			fi
		fi
	fi
	echo '   ' $1 '[' $pid '] is ' ${bold}$r${normal}
}
checkProcess() {
  r="${red}stopped"
  result=$(pgrep -f $1)
  if [[ $result != "" ]]; then
    r="${green}running"
  fi
  echo '   ' $1 '[' $result '] is ' ${bold}$r${normal}
}


echo '===== Blendata Check Process Status ====='

checkStatus "none-control-service"

echo '=========================================='
