package org.apache.spark.examples.sql

import org.apache.spark.sql.SQLContext
import org.apache.spark.{SparkConf, SparkContext}

object SparkSqlTestV2 {

  def main(args: Array[String]): Unit = {

    val conf = new SparkConf().setAppName("Spark Sql Test").setMaster("local")
    val sc = new SparkContext(conf)
    val sqlContext = new SQLContext(sc)

    import sqlContext._
    import sqlContext.implicits._

    val people = sc.textFile("D:\\Spark_Ws\\spark-apache\\data\\data1.txt")
      .map(_.split("\\|")).map(p => Person( p(0), p(1), p(2).trim.toInt, p(3))).toDF()

    people.createOrReplaceTempView("people")

    val teenagers = sql("SELECT name, age, message,addr FROM people ORDER BY age")

    teenagers.map(t => "name:" + t(0) + " age:" + t(1) + " addr:" + t(2))
      .collect().foreach(println)

    sc.stop();
  }

  case class Person(name: String, message: String, age: Int, addr: String)

}
