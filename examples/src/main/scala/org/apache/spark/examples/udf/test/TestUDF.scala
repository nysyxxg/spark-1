package org.apache.spark.examples.udf.test

import org.apache.spark.examples.sql.SparkSqlTestUDF.Person
import org.apache.spark.examples.sql.UDFSqlUtil
import org.apache.spark.sql.SparkSession

object TestUDF {


  def main(args: Array[String]): Unit = {

    val spark = SparkSession.builder.appName("SparkSqlTest").master("local[1]").enableHiveSupport.getOrCreate
    val ssc = spark.sparkContext

    import spark.implicits._
    val people = ssc.textFile("D:\\Spark_Ws\\spark-apache\\data\\data1.txt")
      .map(_.split("\\|")).map(p => Person(p(0), null, p(2).trim.toInt, p(3))).toDF()

    people.createOrReplaceTempView("people");

    val teenagers = spark.sql("SELECT name, age, message,addr FROM people ORDER BY age")
    teenagers.map(t => "name:" + t(0) + " age:" + t(1) + " message:" + t(2) + " addr:" + t(3)).collect().foreach(println)

    var methodName = "toUpperCase"
    spark.udf.register("toUpperCase", UDFSqlUtil.toUpperCase(_: String))

    val teenagers3 = spark.sql("SELECT toUpperCase(name) AS name,   age   r FROM people ORDER BY age")
    teenagers3.map(t => "name:" + t(0) + " age:" + t(1)  ).collect().foreach(println)
    teenagers3.show();

  }


}
