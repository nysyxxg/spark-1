package org.apache.spark.examples.sql

import org.apache.spark.sql.SparkSession

import scala.collection.mutable.ArrayBuffer


object SparkSQLUDFTest {

  case class Worker(name: String, age: Int, job: String)

  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder().appName("sparkSql").master("local[4]")
      .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer").getOrCreate()

    val sc = spark.sparkContext
    val sqlContext = spark.sqlContext

    val workerRDD = sc.textFile("F://Workers.txt").mapPartitions(itor => {
      val array = new ArrayBuffer[Worker]()
      while (itor.hasNext) {
        val splited = itor.next().split(",")
        array.append(new Worker(splited(0), splited(2).toInt, splited(2)))
      }
      array.toIterator
    })
    //注册UDF
    spark.udf.register("strLen", (str: String, addr: String) => str.length + addr.length)
    //    val workDS = workerRDD.toDF()

    //    workDS.createOrReplaceTempView("worker")
    val resultDF = spark.sql("select strLen(name,addr) from worker")
    val resultDS = resultDF.as("WO")
    resultDS.show()


    // 第一种
    def makeDT(date: String, time: String, tz: String) = s"$date $time $tz"

    sqlContext.udf.register("makeDt", makeDT(_: String, _: String, _: String))

    // Now we can use our function directly in SparkSQL.
    sqlContext.sql("SELECT amount, makeDt(date, time, tz) from df").take(2)
    // but not outside
    // df.select($"customer_id", makeDt($"date", $"time", $"tz"), $"amount").take(2) // fails


    // 第二种
    import org.apache.spark.sql.functions.udf
    val makeDt = udf(makeDT(_: String, _: String, _: String))
    // now this works
    // df.select($"customer_id", makeDt($"date", $"time", $"tz"), $"amount").take(2)

    spark.stop()

  }

}
