package org.apache.spark.examples

import java.util.concurrent.Executors

import org.apache.spark.sql.SparkSession

object MultiJobTest {
  // spark.scheduler.mode=FAIR
  def main(args: Array[String]): Unit = {

    val spark = SparkSession.builder().master("local").getOrCreate()

    val rdd = spark.sparkContext.textFile("D:\\Spark_Ws\\spark-apache\\data\\streaming\\input\\wordCount.txt")
      .map(_.split("\\s+")).flatMap(data=>data)
      .map(x => (x  , 1))

    val jobExecutor = Executors.newFixedThreadPool(2)

    jobExecutor.execute(new Runnable {
      override def run(): Unit = {
        spark.sparkContext.setLocalProperty("spark.scheduler.pool", "count-pool")
        val cnt = rdd.groupByKey().count()
        println(s"Count: $cnt")
      }
    })

    jobExecutor.execute(new Runnable {
      override def run(): Unit = {
        spark.sparkContext.setLocalProperty("spark.scheduler.pool", "take-pool")
        val data = rdd.sortByKey().take(10)
        println(s"Data Samples: ")
        data.foreach { x => println( x._1 + ", " + x._2) }
      }
    })

    jobExecutor.shutdown()
    while (!jobExecutor.isTerminated) {}
    println("Done!")
  }
}