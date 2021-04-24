package org.apache.spark.examples.sql.df

import org.apache.spark.sql.Row
import org.apache.spark.{SparkConf, SparkContext}

object DFTest1 {

  def main(args: Array[String]): Unit = {
    val conf = new SparkConf().setAppName("MysqlDemo").setMaster("local[2]")
    val sc = new SparkContext(conf)

    //首先初始化一个SparkSession对象
    val spark = org.apache.spark.sql.SparkSession.builder
      .master("local")
      .appName("Spark CSV Reader")
      .getOrCreate;

    import org.apache.spark.sql.types._
    val schema = StructType(List(
      StructField("integer_column", IntegerType, nullable = false),
      StructField("string_column", StringType, nullable = true),
      StructField("date_column", DateType, nullable = true)
    ))

    val rdd = sc.parallelize(Seq(
      Row(1, "First Value", java.sql.Date.valueOf("2010-01-01")),
      Row(2, "Second Value", java.sql.Date.valueOf("2010-02-01"))
    ))
    val df = spark.createDataFrame(rdd, schema)



    //  使用csv文件,spark2.0+之后的版本可用

    //然后使用SparkSessions对象加载CSV成为DataFrame
    val df2 = spark.read
      .format("com.databricks.spark.csv")
      .option("header", "true") //reading the headers
      .option("mode", "DROPMALFORMED")
      .load("csv/file/path"); //.csv("csv/file/path") //spark 2.0 api

    df2.show()

//    本地seq + toDF创建DataFrame示例：
//    val df3 = Seq(
//      (1, "First Value", java.sql.Date.valueOf("2010-01-01")),
//      (2, "Second Value", java.sql.Date.valueOf("2010-02-01"))
//    ).toDF("int_column", "string_column", "date_column")

  }
}
