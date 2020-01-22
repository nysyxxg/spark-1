package org.apache.spark.examples.streaming

import org.apache.spark.SparkConf
import org.apache.spark.examples.SparkAppCommon
import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.{Seconds, StreamingContext}

/**
  * Join Operations连接操作
  *  值得强调的是，你可以轻松地在Spark Streaming中执行不同类型的连接Joins。
  *    stream1.union(stream2)
  */
object StreamJoinOperations extends  SparkAppCommon{
  def main(args: Array[String]): Unit = {
    // 如果本地测试streaming 必须设置 local[num]  其中num >=2
    val sparkConf = new SparkConf().setAppName("NetworkWordCount").setMaster("local[*]")
    val ssc = new StreamingContext(sparkConf, Seconds(20))
    val stream7777 = ssc.socketTextStream("172.21.1.185", 7777, StorageLevel.MEMORY_AND_DISK_SER)
    val stream8888 = ssc.socketTextStream("172.21.1.185", 9999, StorageLevel.MEMORY_AND_DISK_SER)
    val words7 = stream7777.flatMap(_.split(" "))
    val words8 = stream8888.flatMap(_.split(" "))

    val stream1: DStream[String] = words7.map(_.toLowerCase)
    val stream2: DStream[String] = words8.map(_.toLowerCase)
    val joinedStream = stream1.union(stream2)
    val wordCounts = joinedStream.map(x => (x, 1)).reduceByKey(_ + _)
    wordCounts.print()

    wordCounts.foreachRDD(reslut =>{
      reslut.foreach(line=>{
        println(line)
      })
    })

    ssc.start()
    ssc.awaitTermination()

  }

}
