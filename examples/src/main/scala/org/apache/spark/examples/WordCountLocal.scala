package org.apache.spark.examples

import org.apache.spark.sql.{DataFrame, Dataset, SparkSession}
import org.apache.spark.{SparkConf, SparkContext}

/**
  * 开发Spark案例之WordCount
  */
object WordCountLocal {

  def main(args: Array[String]): Unit = {
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
      .appName("Spark Pi").master("local")
      .getOrCreate()
    val lines = spark.read.textFile("D:\\Spark_Ws\\spark-apache\\examples\\src\\main\\resources\\word.txt")
    println("----------打印Dataset----------")
    lines.show()
    //添加隐式转换
    import spark.implicits._
    //整理数据切分压平
    //Dataset只有一列，默认列名为value
    val words: Dataset[String] = lines.flatMap(_.split(" "))
    println("----------打印切分压平之后的Dataset----------")
    words.show()
    //注册视图
    words.createTempView("t_wc")
    //执行sql（lazy）
    val dataFrame: DataFrame = spark.sql("select value, count(*) counts from t_wc group by value order by value desc")
    //执行计算
    println("----------wordcount统计----------")
    dataFrame.show()

  }

}

