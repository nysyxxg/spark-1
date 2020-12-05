package org.apache.spark.examples

import org.apache.spark.sql.SparkSession

object SSCTest {

  def main(args: Array[String]): Unit = {

    var path = "D:\\Spark_Ws\\spark-apache\\examples\\src\\main\\resources\\word.txt"

    val spark = SparkSession
      .builder
      .appName("Spark Pi").master("local[2]")
      .getOrCreate()

    val sc = spark.sparkContext
    println("-----------------------------------")
    println(sc.getConf.toDebugString)
    println("-----------------------------------")
    sc.getExecutorMemoryStatus.foreach(data=>{
      val  threadName =  Thread.currentThread().getName
      println("----------threadName-----" + threadName + "------data--->" + data._1)
    })
    println("-----------------------------------")
    println(sc.master)
  }

}
