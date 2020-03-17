#!/usr/bin/env bash

#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Runs a Spark command as a daemon.
#
# Environment Variables
#
#   SPARK_CONF_DIR  Alternate conf dir. Default is ${SPARK_HOME}/conf.
#   SPARK_LOG_DIR   Where log files are stored. ${SPARK_HOME}/logs by default.
#   SPARK_MASTER    host:path where spark code should be rsync'd from
#   SPARK_PID_DIR   The pid files are stored. /tmp by default.
#   SPARK_IDENT_STRING   A string representing this instance of spark. $USER by default
#   SPARK_NICENESS The scheduling priority for daemons. Defaults to 0.
#   SPARK_NO_DAEMONIZE   If set, will run the proposed command in the foreground. It will not output a PID file.
##

usage="Usage: spark-daemon.sh [--config <conf-dir>] (start|stop|submit|status) <spark-command> <spark-instance-number> <args...>"

# if no args specified, show usage  # 如果没有提供参数，则打印usage使用说明并中断脚本
if [ $# -le 1 ]; then
  echo $usage
  exit 1
fi
# 判断是否配置了SPARK_HOME，如果没有则手动设置
if [ -z "${SPARK_HOME}" ]; then
  export SPARK_HOME="$(cd "`dirname "$0"`"/..; pwd)"
fi
# 通过.加上文件名临时执行spark-config.sh脚本
## 设置SPARK_HOME、SPARK_CONF_DIR以及python的一些环境变量
. "${SPARK_HOME}/sbin/spark-config.sh"

# get arguments

# Check if --config is passed as an argument. It is an optional parameter.
# Exit if the argument is not a directory.
# 检查参数中是否包含--config，如果该参数的值不为目录则退出
if [ "$1" == "--config" ]
then
  shift # 移除第一个参数
  conf_dir="$1"
  if [ ! -d "$conf_dir" ]
  then
    echo "ERROR : $conf_dir is not a directory"
    echo $usage
    exit 1
  else
    export SPARK_CONF_DIR="$conf_dir"
  fi
  shift
fi
# 分别对option、command、instance进行赋值
option=$1
shift
command=$1
shift
instance=$1
shift

spark_rotate_log ()
{
    log=$1;
    num=5;
    if [ -n "$2" ]; then
	num=$2
    fi
    if [ -f "$log" ]; then # rotate logs
	while [ $num -gt 1 ]; do
	    prev=`expr $num - 1`
	    [ -f "$log.$prev" ] && mv "$log.$prev" "$log.$num"
	    num=$prev
	done
	mv "$log" "$log.$num";
    fi
}
# 通过.加上文件名临时执行load-spark-env.sh脚本
## 设置SPARK_HOME、SPARK_SCALA_VERSION环境变量
. "${SPARK_HOME}/bin/load-spark-env.sh"

if [ "$SPARK_IDENT_STRING" = "" ]; then
  export SPARK_IDENT_STRING="$USER"
fi

# 设置SPARK_PRINT_LAUNCH_COMMAND环境变量
export SPARK_PRINT_LAUNCH_COMMAND="1"

# get log directory  # 日志相关
if [ "$SPARK_LOG_DIR" = "" ]; then
  export SPARK_LOG_DIR="${SPARK_HOME}/logs"
fi
mkdir -p "$SPARK_LOG_DIR"
touch "$SPARK_LOG_DIR"/.spark_test > /dev/null 2>&1
TEST_LOG_DIR=$?
if [ "${TEST_LOG_DIR}" = "0" ]; then
  rm -f "$SPARK_LOG_DIR"/.spark_test
else
  chown "$SPARK_IDENT_STRING" "$SPARK_LOG_DIR"
fi
# 设置spark进程目录
if [ "$SPARK_PID_DIR" = "" ]; then
  SPARK_PID_DIR=/tmp
fi

# some variables  # 定义log和pid变量
log="$SPARK_LOG_DIR/spark-$SPARK_IDENT_STRING-$command-$instance-$HOSTNAME.out"
pid="$SPARK_PID_DIR/spark-$SPARK_IDENT_STRING-$command-$instance.pid"

# Set default scheduling priority  # 设置默认的调度优先级
if [ "$SPARK_NICENESS" = "" ]; then
    export SPARK_NICENESS=0
fi
 # 该方法
execute_command() {
  # 看不懂+set是啥意思...不过debug的时候会进这个条件判断
  if [ -z ${SPARK_NO_DAEMONIZE+set} ]; then
      # 后台执行脚本命令，此处是获取传入该方法的所有参数，进行运行
      nohup -- "$@" >> $log 2>&1 < /dev/null &
       # 获取最新的后台运行的pid，即上条命令所占用的pid
      newpid="$!"

      echo "$newpid" > "$pid"

      # Poll for up to 5 seconds for the java process to start
      # 共等待5秒来等待上述程序的运行
      for i in {1..10}
      do
        if [[ $(ps -p "$newpid" -o comm=) =~ "java" ]]; then
           break
        fi
        sleep 0.5
      done

      sleep 2  # 再等待2秒
      # Check if the process has died; in that case we'll tail the log so the user can see
      # 当程序正常运行时，打印日志
      if [[ ! $(ps -p "$newpid" -o comm=) =~ "java" ]]; then
        echo "failed to launch: $@"
        tail -2 "$log" | sed 's/^/  /'
        echo "full log in $log"
      fi
  else
      "$@"
  fi
}
# 该方法主要判断是否能继续进行，并做一些预处理
run_command() {
  mode="$1"   # 获取第一个参数赋值给mode
  shift
  # 创建进程目录
  mkdir -p "$SPARK_PID_DIR"
  # 判断进程文件是否存在，如果存在则给出提示：进程已存在，请先停止
  if [ -f "$pid" ]; then
    TARGET_ID="$(cat "$pid")"
    if [[ $(ps -p "$TARGET_ID" -o comm=) =~ "java" ]]; then
      echo "$command running as process $TARGET_ID.  Stop it first."
      exit 1
    fi
  fi
  # 如果spark_master不为空，则在masters(master高可用的情况)中同步删除部分文件
  if [ "$SPARK_MASTER" != "" ]; then
    echo rsync from "$SPARK_MASTER"
    rsync -a -e ssh --delete --exclude=.svn --exclude='logs/*' --exclude='contrib/hod/logs/*' "$SPARK_MASTER/" "${SPARK_HOME}"
  fi

  spark_rotate_log "$log"
  echo "starting $command, logging to $log"
 # 使用mode变量进行匹配
  case "$mode" in
    (class)
      # 当变量为class时调用execute_command，传入参数
      execute_command nice -n "$SPARK_NICENESS" "${SPARK_HOME}"/bin/spark-class "$command" "$@"
      ;;

    (submit)
      # 当变量为submit时调用execute_command，传入参数
      execute_command nice -n "$SPARK_NICENESS" bash "${SPARK_HOME}"/bin/spark-submit --class "$command" "$@"
      ;;

    (*)
      echo "unknown mode: $mode"
      exit 1
      ;;
  esac

}
# 使用option变量进行匹配
case $option in
   # 如果变量为submit则执行run_command方法，并将submit作为第一个参数传入
  (submit)
    run_command submit "$@"
    ;;
  # 如果变量为start则执行run_command方法，并将start作为第一个参数传入
  (start)
    run_command class "$@"
    ;;
   # 如果变量为stop，则执行kill命令，并删除进程文件
  (stop)

    if [ -f $pid ]; then
      TARGET_ID="$(cat "$pid")"
      if [[ $(ps -p "$TARGET_ID" -o comm=) =~ "java" ]]; then
        echo "stopping $command"
        kill "$TARGET_ID" && rm -f "$pid"
      else
        echo "no $command to stop"
      fi
    else
      echo "no $command to stop"
    fi
    ;;
  # 如果变量为status，则打印状态信息
  (status)

    if [ -f $pid ]; then
      TARGET_ID="$(cat "$pid")"
      if [[ $(ps -p "$TARGET_ID" -o comm=) =~ "java" ]]; then
        echo $command is running.
        exit 0
      else
        echo $pid file is present but $command not running
        exit 1
      fi
    else
      echo $command not running.
      exit 2
    fi
    ;;
  # 如果变量未匹配上上述情况，则打印脚本使用方法
  (*)
    echo $usage
    exit 1
    ;;

esac

#debug spark-daemon.sh脚本
#sh -x /usr/local/spark/sbin/spark-daemon.sh start org.apache.spark.deploy.master.Master 1 --host s101 --port 7077 --webui-port 8080
# spark-daemon.sh调用spark-class
#sh -x /usr/local/spark/bin/spark-class org.apache.spark.deploy.master.Master --host s101 --port 7077 --webui-port 8080
