package org.apache.spark.examples.tock

import java.util.Properties

import com.github.housepower.jdbc.ClickHouseConnection
import com.github.housepower.jdbc.settings.ClickHouseConfig
import org.apache.spark.SparkConf
import org.apache.spark.examples.streaming.StreamingExamples
import org.apache.spark.streaming.{Minutes, Seconds, StreamingContext}
import org.apache.spark.streaming.kafka.KafkaUtils

object StreamingToCk {

  def main(args: Array[String]) {
    if (args.length < 4) {
      System.err.println("Usage: KafkaWordCount <zkQuorum> <group> <topics> <numThreads>")
      System.exit(1)
    }

    StreamingExamples.setStreamingLogLevels()

    val Array(zkQuorum, group, topics, numThreads) = args
    val sparkConf = new SparkConf().setAppName("KafkaWordCount")
    val ssc = new StreamingContext(sparkConf, Seconds(2))
    ssc.checkpoint("checkpoint")

    val topicMap = topics.split(",").map((_, numThreads.toInt)).toMap
    val dStreamRDD = KafkaUtils.createStream(ssc, zkQuorum, group, topicMap).map(_._2)


    dStreamRDD.foreachRDD(rdd =>{

      rdd.foreachPartition(it => {  // 开始处理一个分区的数据
       // 初始化redis客户端
       // InternalRedisClient.build(redisConfig)
        // 获取redis的连接
        val jedis =  null // InternalRedisClient.getPool.getResource
        val userTableSchema :  Seq[((String, String), Int)] = null
        //获取 写入表的元数据schema信息 ： ((name,类型),长度) --> Tuple2[Tuple2[String,String],Int]
         // InternalRedisClient.getTableSchema(jedis, redisETLkey).asScala.toList.zipWithIndex.sortBy(_._2)
        // 获取ck的连接信息
        val clickhouseConfig: Properties =  null // chConfig.value
        val cf = new ClickHouseConfig("jdbc:clickhouse://xxxx:9000/database",clickhouseConfig)
        // 初始化ck连接
        val conn = ClickHouseConnection.createClickHouseConnection(cf)
        // 初始化sql
        val insertSQL = ClickhouseUtils.initPrepareSQL(userTableSchema,clickhouseConfig.getProperty("tableName"))

        val pstmt = conn.prepareStatement(insertSQL)
        // bulkSize = 90000
        val bulkSize = clickhouseConfig.getProperty("insertBulkSize").toInt
        var length = 0
        val keys = userTableSchema.toMap.keys.map(_._1).toList  // 获取字段名称
        while (it.hasNext) {
          length += 1
          // 将数据每一条数据，都转化为json对象
          val json = null  //  NewLogScan.scan(it.next().value(), keys)
          if (null != json){
            ClickhouseUtils.newInsert(json,pstmt,userTableSchema)
          }
          if (length >= bulkSize) {
            pstmt.executeBatch()
            length = 0
          }
        }
        pstmt.executeBatch()
        conn.close()
      })
    })

    ssc.start()
    ssc.awaitTermination()
  }


}
