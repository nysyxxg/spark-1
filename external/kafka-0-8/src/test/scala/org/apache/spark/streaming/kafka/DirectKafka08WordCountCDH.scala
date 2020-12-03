package org.apache.spark.streaming.kafka

import kafka.serializer.StringDecoder
import org.apache.spark.SparkConf
import org.apache.spark.streaming.dstream.InputDStream
import org.apache.spark.streaming.kafka.KafkaUtils
import org.apache.spark.streaming.{Seconds, StreamingContext}

//spark-streaming-kafka-0-8-2.1.1
object DirectKafka08WordCountCDH {
  def main(args: Array[String]) {
    //    val brokers = "localhost:9092"
    //    val topics = Set("kafka_test")
    val brokers = "kafka.com:9092"
    val topics = Set("test_topic") //我们需要消费的kafka数据的topic

    // Create context with 2 second batch interval
    val sparkConf = new SparkConf().setAppName("DirectKafka010WordCount")
    val ssc = new StreamingContext(sparkConf, Seconds(2 * 60))
    // Create direct kafka stream with brokers and topics
    val kafkaParams = Map[String, String](
      "bootstrap.servers" -> brokers,
      //      "metadata.broker.list" -> brokers, // kafka的broker list地址
      "auto.offset.reset" -> "smallest", //这个参数可以让streaming消费topic的时候从头开始消费
      //        "key.deserializer" -> classOf[StringDeserializer],
      //        "value.deserializer" -> classOf[StringDeserializer],
      "group.id" -> "tbds_spark_streaming_group",
      "security.protocol" -> "SASL_PLAINTEXT",
      "sasl.mechanism" -> "PLAIN",
      "sasl.kerberos.service.name" -> "kafka"
    )

    val stream: InputDStream[(String, String)] = createStream(ssc, kafkaParams, topics) //建立流，读数据，传入上下文，kafka配置，主题名字

    //val messages = KafkaUtils.createDirectStream[String, String, StringDecoder, StringDecoder](ssc,kafkaParams,topics)

    stream.foreachRDD { rdd =>
        rdd.foreachPartition(line => {
          line.foreach(value1 => println(value1._1 + "----->" + value1._2));
        })
    }
    // Get the lines, split them into words, count the words and print
    //    val wordCount = stream.map(l => (l._2, 1)).reduceByKey(_ + _) //对数据进行处理
    //    wordCount.print() //输出到控制台看看结果
    // Start the computation
    ssc.start()
    ssc.awaitTermination()
  }

  def createStream(scc: StreamingContext, kafkaParam: Map[String, String], topics: Set[String]) = {
    KafkaUtils.createDirectStream[String, String, StringDecoder, StringDecoder](scc, kafkaParam, topics)
  }

}
