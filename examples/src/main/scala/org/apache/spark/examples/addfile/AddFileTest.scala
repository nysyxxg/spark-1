package org.apache.spark.examples.addfile

import org.apache.spark.SparkFiles
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession

object AddFileTest {

  def main(args: Array[String]): Unit = {

    var path = "D:\\Spark_Ws\\spark-apache\\examples\\src\\main\\resources\\word.txt"

    val spark = SparkSession
      .builder
      .appName("Spark Pi").master("local")
      .getOrCreate()

    val sc = spark.sparkContext


    sc.addFile(path)  // 在每个worker节点都存储备份
    val lines = spark.read.textFile(SparkFiles.get("word.txt")).rdd
    println("----------打印Dataset----------")

    //整理数据切分压平
    //Dataset只有一列，默认列名为value
    val wordRDD: RDD[String] = lines.flatMap(w => {
      var array: Array[String] = w.split(",")
      array
    })
    println("----------打印切分压平之后的Dataset----------")
    val pairWordRDD = wordRDD.map(word => {
      println(word)
      new Tuple2(word, 1)
    })
    pairWordRDD.count();

//    var path1 = "/user/iteblog/ip.txt"
//    sc.addFile(path1)
//    val rdd = sc.textFile(SparkFiles.get(""ip.txt))
//
//
//    var path2 = "/user/iteblog/ip.txt"
//    sc.addFile(path2)
//    val rdd2 = sc.parallelize((0 to 10))
//    rdd2.foreach { index =>
//      val path = SparkFiles.get("ip.txt")
//    }


  }
}
