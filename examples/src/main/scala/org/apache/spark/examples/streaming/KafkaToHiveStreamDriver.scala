package org.apache.spark.examples.streaming

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId}

import org.apache.commons.lang3.StringUtils
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecord}
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.types.{StringType, StructField, StructType}
import org.apache.spark.sql.{Row, SparkSession}
import org.apache.spark.streaming.kafka010.{ConsumerStrategies, KafkaUtils, LocationStrategies}
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.slf4j.LoggerFactory
;

object KafkaToHiveStreamDriver {


  def main(args: Array[String]) {

    val checkpointDataPath = args(0) //"/user/root/dataacess/"
    val brokers = "xxg.kafka.com:9092"
    val topics = "xxg-test" //我们需要消费的kafka数据的topic

    val spark = SparkSession.builder.appName("topic_" + topics).enableHiveSupport.getOrCreate

    // Create context with 2 second batch interval
    val streamingContext = new StreamingContext(spark.sparkContext, Seconds(5 * 60))
    // Create direct kafka stream with brokers and topics
    val kafkaParams = Map[String, Object](
      ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG -> brokers,
      ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG -> classOf[StringDeserializer],
      ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG -> classOf[StringDeserializer],
      ConsumerConfig.GROUP_ID_CONFIG -> "cdh583_spark_streaming_group6",
      ConsumerConfig.AUTO_OFFSET_RESET_CONFIG -> "earliest",
      ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG -> "52428800"
    )

    if (StringUtils.isNotBlank(checkpointDataPath)) {
      streamingContext.checkpoint(checkpointDataPath)
    }

    val messagesDStream = KafkaUtils.createDirectStream[String, String](
      streamingContext,
      LocationStrategies.PreferConsistent,
      ConsumerStrategies.Subscribe[String, String](topics.split(","), kafkaParams)
    )
    messagesDStream.foreachRDD(rdd => processingRdd(rdd, spark))

    streamingContext.start()
    streamingContext.awaitTermination()
  }


  def processingRdd(rdd: RDD[ConsumerRecord[String, String]], spark: SparkSession): Unit = {
    if (!rdd.isEmpty()) {
      val colList = List("data")
      val hiveDbName = "test"
      val tableName = "test_dataacess_all"
      val nodeLog = LoggerFactory.getLogger(getClass)
      val schema = StructType(List(
        StructField("data", StringType, nullable = true)
      ))

      val valueSave: RDD[Row] = rdd.map(data => Row(data.value()))
      val dataFrame = spark.createDataFrame(valueSave, schema)

      val nowInstant = Instant.now
      val toDayStr = DateTimeFormatter.BASIC_ISO_DATE.format(nowInstant.atZone(ZoneId.systemDefault).toLocalDate)
      val addPartitionSql = s"ALTER TABLE $hiveDbName.$tableName ADD IF NOT EXISTS PARTITION ( ds = '$toDayStr' )"
      nodeLog.info(addPartitionSql)
      spark.sql(addPartitionSql)
      val tmpTableName = s"spark_hive_${tableName}_${nowInstant.getEpochSecond}"
      nodeLog.info(s"tmp table name: $tmpTableName.")
      dataFrame.createOrReplaceTempView(tmpTableName)
      val sqlInsert = s"INSERT INTO TABLE $hiveDbName.$tableName PARTITION ( ds = '$toDayStr' ) SELECT ${colList.mkString(",")} FROM $tmpTableName"
      nodeLog.info(sqlInsert)
      spark.sql(sqlInsert)
      spark.sqlContext.dropTempTable(tmpTableName)
    }
  }

}
