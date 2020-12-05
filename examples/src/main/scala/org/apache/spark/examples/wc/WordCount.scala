package org.apache.spark.examples.wc

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession

/**
  * 开发Spark案例之WordCount
  */
object WordCount {

  def main(args: Array[String]): Unit = {

    // -Dhadoop.home.dir=C:\\SoftWare\\hadoop-2.7.7
    // System.setProperty("hadoop.home.dir", "C:\\SoftWare\\hadoop-2.7.7")
    //    val conf = new SparkConf()
    //    conf.setAppName("WordCountLocal")
    //    conf.setMaster("local")
    //    val sparkContext = new SparkContext(conf)
    //    val textFileRDD = sparkContext.textFile("D:\\Spark_Ws\\spark-apache\\examples\\src\\main\\resources\\word.txt")
    //    val wordRDD = textFileRDD.flatMap(line => line.split(" "))
    //    val pairWordRDD = wordRDD.map(word => (word, 1))
    //    val wordCountRDD = pairWordRDD.reduceByKey((a, b) => a + b)
    //    wordCountRDD.saveAsTextFile("wordcount")

    val spark = SparkSession
      .builder
      .appName("Spark Pi").master("local[3]")  // local[3] // 3代表其中三个线程
       .getOrCreate()
    val lines = spark.read.textFile("D:\\Spark_Ws\\spark-apache\\examples\\src\\main\\resources\\word.txt").rdd
    println("----------打印Dataset----------")

    //整理数据切分压平
    //Dataset只有一列，默认列名为value
    val wordRDD: RDD[String] = lines.flatMap(w=>{
     var array:Array[String] =  w.split(",")
      array
    })
    println("----------打印切分压平之后的Dataset----------")


    val pairWordRDD = wordRDD.map(word => {
      println(word)
      new Tuple2(word, 1)
    })
    pairWordRDD.count();

    pairWordRDD.collect().foreach(line => {
      val  threadName =  Thread.currentThread().getName
      println(threadName + "-------1----------->" + line._1 + "----------->" + line._2)
    })


    val wordCountRDD = pairWordRDD.reduceByKey((a, b) => a + b).repartition(2)

    wordCountRDD.collect().foreach(line => {
      val  threadName =  Thread.currentThread().getName
      println(threadName + "----------2--------->" + line._1 + "----------->" + line._2)
    })

   // 多个线程答应测试
    wordCountRDD.foreach(line => {
      val  threadName =  Thread.currentThread().getName
      println(threadName + "-------3------------->" + line._1 + "----------->" + line._2)
    })

    // wordCountRDD.saveAsTextFile("wordcount/res4")

  }

}
