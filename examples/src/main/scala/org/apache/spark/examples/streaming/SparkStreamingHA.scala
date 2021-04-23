package org.apache.spark.examples.streaming

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.sql.SparkSession
import org.apache.spark.streaming.dstream.InputDStream
import org.apache.spark.streaming.kafka010.{ConsumerStrategies, KafkaUtils, LocationStrategies}
import org.apache.spark.streaming.{Seconds, StreamingContext}

/**
  * Driver HA（Standalone或者Mesos）
  * 因为SparkStreaming是7*24小时运行，Driver只是一个简单的进程，有可能挂掉，
  * 所以实现Driver的HA就有必要（如果使用的Client模式就无法实现Driver HA，这里针对的是cluster模式）。
  * Yarn平台cluster模式提交任务，AM(AplicationMaster)相当于Driver，如果挂掉会自动启动AM。
  * 这里所说的DriverHA针对的是Spark standalone和Mesos资源调度的情况下。
  * 实现Driver的高可用有两个步骤:
  *
  * 第一：提交任务层面，在提交任务的时候加上选项 --supervise,当Driver挂掉的时候会自动重启Driver。
  * 第二：代码层面，使用JavaStreamingContext.getOrCreate（checkpoint路径，JavaStreamingContextFactory）
  *
  * Driver中元数据包括：
  *   1: 创建应用程序的配置信息。
  *   2: DStream的操作逻辑。
  *   3: job中没有完成的批次数据，也就是job的执行进度。
  *   4: SparkStreaming+Kafka
  *   5: receiver模式
  */
object SparkStreamingHA {


  private val brokers = "hadoop01:9092"
  private val topics = "lj01"
  private val checkPointPath = "hdfs://hadoop01:9000/sparkStreaming/kafka6"


  def main(args: Array[String]): Unit = {

    // HA 实现1
    val spark = getSparkSession()
    val streamingContext = StreamingContext.getOrCreate(checkPointPath, () => {
      val ssc = new StreamingContext(spark.sparkContext, Seconds(5))
      ssc.checkpoint(checkPointPath)
      val kafkaInputStream = getKafkaInputStream(ssc)
      val result = kafkaInputStream.map(x => x.value()).flatMap(x => {
        x.split(" ").map(x => {
          (x, 1)
        })
      }).reduceByKey(_ + _)
      result.print()
      ssc
    })
    streamingContext.start()
    streamingContext.awaitTermination()

    // HA 实现2
    val streamingContext2 = StreamingContext.getOrCreate(checkPointPath, streamingTask)
    streamingContext2.start()
    streamingContext2.awaitTermination()
  }

  def streamingTask(): StreamingContext = {
    val spark = getSparkSession()
    val ssc = new StreamingContext(spark.sparkContext, Seconds(5))
    ssc.checkpoint(checkPointPath)
    val kafkaInputStream = getKafkaInputStream(ssc)
    val result = kafkaInputStream.map(x => x.value()).flatMap(x => {
      x.split(" ").map(x => {
        (x, 1)
      })
    }).reduceByKey(_ + _)
    result.print()
    ssc
  }

  def getSparkSession(): SparkSession = {
    SparkSession.builder()
      .appName("kafka_test")
      .master("local[4]")
      .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .getOrCreate()
  }

  def getKafkaInputStream(ssc: StreamingContext): InputDStream[ConsumerRecord[String, String]] = {
    val topicArray = topics.split(",").toList

    val kafkaParams = Map[String, Object](
      "bootstrap.servers" -> brokers,
      "key.deserializer" -> classOf[StringDeserializer],
      "value.deserializer" -> classOf[StringDeserializer],
      "group.id" -> "lj00",
      "auto.offset.reset" -> "latest",
      "enable.auto.commit" -> (false: java.lang.Boolean)
    )

    KafkaUtils.createDirectStream[String, String](
      ssc,
      LocationStrategies.PreferConsistent,
      ConsumerStrategies.Subscribe[String, String](topicArray, kafkaParams)
    )
  }

}
