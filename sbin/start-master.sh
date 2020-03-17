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

# Starts the master on the machine this script is executed on.
# 判断是否配置了SPARK_HOME，如果没有设置则先通过$0获取当前脚本的文件名
# 再通过dirname获取sbin目录，再cd回上级目录，pwd获取绝对路径，以此设置SPARK_HOME
if [ -z "${SPARK_HOME}" ]; then
  export SPARK_HOME="$(cd "`dirname "$0"`"/..; pwd)"
fi

# NOTE: This exact class name is matched downstream by SparkSubmit.
# Any changes need to be reflected there.
# 定义CLASS变量
CLASS="org.apache.spark.deploy.master.Master"
# 判断是否以--help或者-h结尾，如果是的话则打印出脚本使用方法
if [[ "$@" = *--help ]] || [[ "$@" = *-h ]]; then
  echo "Usage: ./sbin/start-master.sh [options]"
  pattern="Usage:"
  pattern+="\|Using Spark's default log4j profile:"
  pattern+="\|Registered signal handlers for"

  "${SPARK_HOME}"/bin/spark-class $CLASS --help 2>&1 | grep -v "$pattern" 1>&2
  exit 1
fi
# 通过$@取出所有参数，并将值赋值给ORIGINAL_ARGS
ORIGINAL_ARGS="$@"

# 通过 .加上文件名临时执行spark-config.sh脚本
## 设置SPARK_HOME、SPARK_CONF_DIR以及python的一些环境变量
. "${SPARK_HOME}/sbin/spark-config.sh"

# 通过.加上文件名临时执行load-spark-env.sh脚本
## 设置SPARK_HOME、SPARK_SCALA_VERSION环境变量
. "${SPARK_HOME}/bin/load-spark-env.sh"

# 设置master的通信端口
if [ "$SPARK_MASTER_PORT" = "" ]; then
  SPARK_MASTER_PORT=7077
fi
# 设置master的主机地址
if [ "$SPARK_MASTER_HOST" = "" ]; then
  case `uname` in     # 不同的操作系统使用不同的命令进行查看
      (SunOS)
	  SPARK_MASTER_HOST="`/usr/sbin/check-hostname | awk '{print $NF}'`"
	  ;;
      (*)
	  SPARK_MASTER_HOST="`hostname -f`"
	  ;;
  esac
fi
# 设置master ui端口
if [ "$SPARK_MASTER_WEBUI_PORT" = "" ]; then
  SPARK_MASTER_WEBUI_PORT=8080
fi
# 调用spark-daemon.sh脚本，传入master启动所需参数
"${SPARK_HOME}/sbin"/spark-daemon.sh start $CLASS 1 \
  --host $SPARK_MASTER_HOST --port $SPARK_MASTER_PORT --webui-port $SPARK_MASTER_WEBUI_PORT \
  $ORIGINAL_ARGS

#如果需要调试
## 1.1. 执行 sh -x start-master.sh
#. start-master.sh调用spark-daemon.sh
#sh -x /usr/local/spark/sbin/spark-daemon.sh start org.apache.spark.deploy.master.Master 1 --host s101 --port 7077 --webui-port 8080