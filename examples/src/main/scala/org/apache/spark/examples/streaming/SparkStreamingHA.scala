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

    /** 虽然checkpoint是对Spark Streaming运行过程中的元数据和每次RDD的数据状态
      * 保存到一个持久化系统中，实现高可用性。
      * 即使当程序修改后打包成新程序后，可能会报错，若删除checkpoint的开头文件，只保留数据文件：
      * hadoop dfs -rmr /checkpoint/checkpoint*
      * 但是新程序虽然能重新启动，但是不会读取上次的数据文件，而是重新开始计算，这样仍然会丢失数据
      *
      * 但checkpoint的弊端：
      *   若流式程序代码或配置改变，则先停掉旧的spark Streaming程序，然后把新的程序打包编译后重新执行，
      *   会造成两种情况：
      *   1、启动报错，反序列化异常
      *   2、启动正常，但可能运行的代码是上一次的旧程序代码
      *
      *   为何有如此情况？？？
      *   这是因为checkpoint第一次持久化时会把整个相关程序的jar包给序列化成一个二进制文件，
      *   每次重启都会从checkpoint目录中恢复，即使把新的程序打包序列化后加载的仍然是旧的序列化二进制文件，
      *   会导致报错或者依旧执行旧代码程序。
      *   若直接把上次的checkpoint删除，当启动新的程序时，只能从kafka的smallest或largest（默认是最新的）的偏移量消费，
      *   若配置成smallest则会导致数据重复，若配置成largest则会导致数据丢失。
      *
      *   针对以上问题，有两种解决方案：
      *   1、旧程序不关闭，新程序启动，两个程序并存一段时间执行消费
      *   2、在旧程序关闭时记录其偏移量，当新程序启动时可直接从偏移量出开始消费。
      *
      *
      *   但是若不使用checkpoint功能，像类似upstatebykey等有状态的函数如何使用？？？？？
      * */

    /**
      * 启动预写日志机制
      * 预写日志机制（Write Ahead Log,WAL），若启动该机制，Receiver 接收到的所有数据都会
      * 被写入配置的checkpoint目录中，driver 恢复数据时避免数据丢失。
      * 调用StreamingContext.checkpoint配置一个检查点目录，然后
      * spark.streaming.receiver.writeAheadLog.enable设置为true
      * */

    // 在Spark Streaming应用程序挂掉后，若重新编译Spark Streaming应用程序再运行，是不能从
    // 挂掉的位置恢复的，因为重新编译会导致不能回反序列化Checkpoint

    /**
      * 从Driver故障中重启并恢复应用的条件：
      * 1、若应用程序首次启动，将创建一个新的StreamingContext实例，设置一个目录保存Checkpoint数据
      * 2、若从Driver失败中重启并恢复，则必须从Checkpoint目录中导入Checkpoint数据来重新创建StreamingContext实例。
      * 上面两点可通过 StreamingContext.getOrCreate 方法实现
      **/

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
    val result = kafkaInputStream.map(x => x.value()).flatMap(x => {x.split(" ").map(x => {(x, 1)})
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
