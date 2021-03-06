package org.apache.spark.examples.sql

import org.apache.spark.sql._
import org.apache.spark.{SparkConf, SparkContext}

import scala.collection.mutable.ListBuffer


object SparkSqlTestWithColumn {

  case class Person(name: String, message: String, age: Int, addr: String)

  def main(args: Array[String]): Unit = {
    //   旧版
    //    val conf = new SparkConf().setAppName("Spark Sql Test").setMaster("local")
    //    val sc = new SparkContext(conf)
    //    val sqlContext = new SQLContext(sc)
    //    import sqlContext.implicits._

    val spark = SparkSession.builder.appName("SparkSqlTest").master("local[1]").enableHiveSupport.getOrCreate
    val sc = spark.sparkContext
    import spark.implicits._
    val people = sc.textFile("D:\\Spark_Ws\\spark-apache\\data\\data1.txt")
      .map(_.split("\\|")).map(p => Person(p(0), p(1), p(2).trim.toInt, p(3))).toDF()
    people.createOrReplaceTempView("people")
    people.show()

    // 一行转多行: 讲一个字段进行分隔成多个值，然后变成多行
    val result: DataFrame = people.withColumn("newMessage", functions.explode(functions.split(functions.col("message"), ",")))
    result.show()

    result.collect().foreach(data => {
      println("--------data------->" + data)
    })

    //    val teenagers = sql("SELECT name, age, message,addr FROM people ORDER BY age")
    //    teenagers.map(t => "name:" + t(0) + " age:" + t(1) + " addr:" + t(2)).collect().foreach(data=>{
    //      println("--------------->" + data)
    //    })

    //    一列变多列
    val Df2 = result.withColumn("newMessage2", functions.split(functions.col("newMessage"), "="))
      .select(
        functions.col("name"),
        functions.col("message"),
        functions.col("newMessage"),
        functions.col("newMessage2").getItem(0).as("start"),
        functions.col("newMessage2").getItem(1).as("end")
      ).drop("newMessage2");

    Df2.show(false);


    // 集成UDF函数的使用：
    spark.udf.register("encryptStr", UDFSqlUtil.encryptStr(_: String));
    spark.udf.register("decryptStr", UDFSqlUtil.decryptStr(_: String));

    var colList = new ListBuffer[String]()
    colList.+=("age")
    colList.+=("addr")

    println("-------------------------------加密处理--------------------------------------")
    val peopleDF = encryptDataTrans(people, colList.toList)
    peopleDF.show()
    peopleDF.createOrReplaceTempView("tmp_xxg")

    var newDataDf = spark.sql("select age,addr from tmp_xxg")
    newDataDf = decryptDataTrans(newDataDf, colList.toList)

    newDataDf.show();
    sc.stop();
  }


  def encryptDataTrans(df: Dataset[Row], colList: List[String]): Dataset[Row] = {
    var dataFrame: Dataset[Row] = df
    dataFrame.schema.foreach {
      col =>
        if (colList.contains(col.name)) {
          dataFrame = dataFrame.withColumn(col.name, functions.callUDF("encryptStr", dataFrame(col.name)))
        }
    }
    dataFrame
  }

  def decryptDataTrans(df: Dataset[Row], colList: List[String]): Dataset[Row] = {
    var dataFrame: Dataset[Row] = df
    dataFrame.schema.foreach {
      col =>
        if (colList.contains(col.name)) {
          dataFrame = dataFrame.withColumn(col.name, functions.callUDF("decryptStr", dataFrame(col.name)))
        }
    }
    dataFrame
  }

}
