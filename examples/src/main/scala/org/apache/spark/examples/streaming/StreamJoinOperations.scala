package org.apache.spark.examples.streaming

import org.apache.spark.SparkConf
import org.apache.spark.examples.SparkAppCommon
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.streaming.dstream.DStream

/**
  *  1: 在服务器上运行 nc -l 7777
  *  2： 启动程序
  * Join Operations连接操作
  *  值得强调的是，你可以轻松地在Spark Streaming中执行不同类型的连接Joins。
  *    stream1.join(stream2)
  */
object StreamJoinOperations extends  SparkAppCommon{
  def main(args: Array[String]): Unit = {
    // 如果本地测试streaming 必须设置 local[num]  其中num >=2
    val sparkConf = new SparkConf().setAppName("NetworkWordCount").setMaster("local[*]")
    val ssc = new StreamingContext(sparkConf, Seconds(20))
    val stream7777 = ssc.socketTextStream(args(0), 7777, StorageLevel.MEMORY_AND_DISK_SER)
    val stream8888 = ssc.socketTextStream(args(0), 9999, StorageLevel.MEMORY_AND_DISK_SER)
    val words7 = stream7777.flatMap(_.split(" "))
    val words8 = stream8888.flatMap(_.split(" "))

    val stream1: DStream[(String, Int)] = words7.map(word => (word, 1))

    val stream2: DStream[(String, Int)] = words8.map(word => (word, 1))
    val joinedStream = stream1.join(stream2)
    val wordCounts = joinedStream.map(x => (x, 1)).reduceByKey(_ + _)
    wordCounts.print()

    wordCounts.foreachRDD(reslut =>{
      reslut.foreach(line=>{
        println(line)
      })
    })

   // 当在前面说明DStream.transform操作时，这已经展示了。这里是将窗口流与数据集dataset连接的另一个实例。
    val dataset: RDD[(String, Int)] = null
    val windowedStream = stream1.window(Seconds(20));
    val joinedStream2 = windowedStream.transform {
      rdd => rdd.join(dataset)
    }
    val wordCounts2 = joinedStream2.map(x => (x, 1)).reduceByKey(_ + _)
    wordCounts2.print()

    ssc.start()
    ssc.awaitTermination()

  }

}
