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
package org.apache.spark.examples.streaming

import kafka.serializer.StringDecoder

import org.apache.spark.streaming._
import org.apache.spark.streaming.kafka._
import org.apache.spark.SparkConf

/**
  * Consumes messages from one or more topics in Kafka and does wordcount.
  * Usage: DirectKafkaWordCount <brokers> <topics>
  * <brokers> is a list of one or more Kafka brokers
  * <topics> is a list of one or more kafka topics to consume from
  *
  * Example:
  * $ bin/run-example streaming.DirectKafkaWordCount broker1-host:port,broker2-host:port \
  * topic1,topic2
  */
object DirectKafkaWordCount {
  def main(args: Array[String]) {
    if (args.length < 2) {
      System.err.println(
        s"""
           |Usage: DirectKafkaWordCount <brokers> <topics>
           |  <brokers> is a list of one or more Kafka brokers
           |  <topics> is a list of one or more kafka topics to consume from
           |
        """.stripMargin)
      System.exit(1)
    }

    StreamingExamples.setStreamingLogLevels()

    val Array(brokers, topics) = args

    // Create context with 2 second batch interval
    val sparkConf = new SparkConf().setAppName("DirectKafkaWordCount")

    //启用反压
    sparkConf.set("spark.streaming.backpressure.enabled", "true")
    //最小摄入条数控制
    sparkConf.set("spark.streaming.backpressure.pid.minRate", "1")
    //最大摄入条数控制
    sparkConf.set("spark.streaming.kafka.maxRatePerPartition", "12")

    // 注意:
    //     1: 反压机制真正起作用时需要至少处理一个批：由于反压机制需要根据当前批的速率，预估新批的速率，
    //     所以反压机制真正起作用前，应至少保证处理一个批。

    //    2: 如何保证反压机制真正起作用前应用不会崩溃：要保证反压机制真正起作用前应用不会崩溃,需要控制每个批次最大摄入速率。
    //    3: 若为Direct Stream，如Kafka Direct Stream,则可以通过spark.streaming.kafka.maxRatePerPartition参数来控制。
    //    此参数代表了 每秒每个分区最大摄入的数据条数。
    //    假设BatchDuration为10秒,spark.streaming.kafka.maxRatePerPartition为12条,kafka topic 分区数为3个，
    //    则一个批(Batch)最大读取的数据条数为360条(3*12*10=360)。
    //    同时，需要注意，该参数也代表了整个应用生命周期中的最大速率，即使是背压调整的最大值也不会超过该参数。
    //    假设 spark.streaming.kafka.maxRatePerPartition = 100 ，5分钟处理一次，分区个数为：10
    //    则一个批次最大读取的数据条数： 5 * 60(S)  *  100 * 10  = 300 * 1000


    val ssc = new StreamingContext(sparkConf, Seconds(2))

    // Create direct kafka stream with brokers and topics
    val topicsSet = topics.split(",").toSet
    val kafkaParams = Map[String, String]("metadata.broker.list" -> brokers)
    val messages = KafkaUtils.createDirectStream[String, String, StringDecoder, StringDecoder](ssc, kafkaParams, topicsSet)

    // Get the lines, split them into words, count the words and print
    val lines = messages.map(_._2)
    val words = lines.flatMap(_.split(" "))
    val wordCounts = words.map(x => (x, 1L)).reduceByKey(_ + _)
    wordCounts.print()

    // Start the computation
    ssc.start()
    ssc.awaitTermination()
  }
}

// scalastyle:on println
