/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// scalastyle:off println
package org.apache.spark.examples.sql.streaming

import org.apache.spark.examples.SparkAppCommon
import org.apache.spark.sql.SparkSession

/**
 * Counts words in UTF8 encoded, '\n' delimited text received from the network.
 *
 * Usage: StructuredNetworkWordCount <hostname> <port>
 * <hostname> and <port> describe the TCP server that Structured Streaming
 * would connect to receive data.
 *
 * To run this on your local machine, you need to first run a Netcat server
 *    `$ nc -lk 9999`
 * and then run the example
 *    `$ bin/run-example sql.streaming.StructuredNetworkWordCount
 *    localhost 9999`
 */
object StructuredNetworkWordCount  extends  SparkAppCommon{
  def main(args: Array[String]) {
    if (args.length < 2) {
      System.err.println("Usage: StructuredNetworkWordCount <hostname> <port>")
      System.exit(1)
    }

    val host = args(0)
    val port = args(1).toInt
    // 创建一个本地的SparkSession，这是Spark所有功能的开始点
    val spark = SparkSession
      .builder
      .appName("StructuredNetworkWordCount")
      .master("local[2]")
      .getOrCreate()
    // 隐式转换
    import spark.implicits._
     // 接着，让我们创建流DataFrame，它代表监听localhost:9999的服务端接收到的文本数据，并转换为DataFrame以统计单词字数。
    // Create DataFrame representing the stream of input lines from connection to host:port
    val lines = spark.readStream
      .format("socket")
      .option("host", host)
      .option("port", port)
      .load()

    // Split the lines into words
    val words = lines.as[String].flatMap(_.split(" "))
    // Generate running word count
    // lines DataFrame表示一个包含流文本数据的无边界表。
    // 这个表包含一个“value”的字符串列，并且流文本数据的每一行成为表的一行。
    // 注意，目前没有接收到任何数据，因为我们正在设置转换，还没有启动。
    // 接下来，我们使用.as[String]将DataFrame转换为字符串Dataset（数据集），
    // 因此我们可以应用flatMap操作去将每一行分割为多个单词。
    // 组合成的单词Dataset（数据集）包含所有单词。
    // 最终，通过在Dataset（数据集）中的唯一值分组并统计它们数量，
    // 我们定义DataFrame。注意，这是流式DataFrame，它表示流的运行单词数量。
    val wordCounts = words.groupBy("value").count()

    // Start running the query that prints the running counts to the console
    // 我们现在已经设置了对流式数据的查询。剩下的就是实际开始接收数据和计算计数。
    // 为了执行，我们将其设置为每次更新时在控制台上打印完整的一组计数（由outputMode("complete")指定）。
    // 然后使用start()启动流式计算。
    val query = wordCounts.writeStream
      .outputMode("complete")
      .format("console")
      .start()

    /**
      * outputMode “输出模型”被定义为写入到外部存储器的内容。输出可以用不同的模式定义：
      * Complete Mode 完全模式 —— 整个更新的结构表将会被写入到外部存储器。由存储连接器决定如何处理整个表的写入。
      * Append Mode 附加模式 —— 自上次触发以来，只有附加到结果表的新行才会被写入到外部存储器。这仅适用于结果表中的现有行不期望被更改的查询。
      * Update Mode 更新模式 ——自上次触发以来，只有附加到结果表的行才会被写入到外部存储器（自Spark2.1.1可用）。注意这与完全模式不同，这个模式只输出自上次触发更改的行。如果查询没有包含聚合，则它将等同于“追加”模式。
      *
      */
    query.awaitTermination()
  }
}
// scalastyle:on println
