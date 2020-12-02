package org.apache.spark.examples.sql;

import org.apache.spark.sql.{SQLContext, SparkSession}

object SparkSqlTest {

  def main(args: Array[String]): Unit = {

    val spark = SparkSession.builder.appName("SparkSqlTest").master("local[1]").enableHiveSupport.getOrCreate
    val ssc = spark.sparkContext

    import spark.implicits._
    val people = ssc.textFile("D:\\Spark_Ws\\spark-apache\\data\\data1.txt")
      .map(_.split("\\|")).map(p => Person(p(0),null, p(2).trim.toInt, p(3))).toDF()

    people.createOrReplaceTempView("people");

    val teenagers = spark.sql("SELECT name, age, message,addr FROM people ORDER BY age")
    teenagers.map(t => "name:" + t(0) + " age:" + t(1) + " message:" + t(2) + " addr:" + t(3) ).collect().foreach(println)

    // 注册udf
    spark.udf.register("strLen", getLen(_: String, _: String));
    spark.udf.register("concatStr", concatStr(_: String, _: String));
    spark.udf.register("encryptStr", encryptStr(_: String, _: String));
    spark.udf.register("decryptStr", decryptStr(_: String, _: String));
    spark.udf.register("encryptStr", encryptStr(_: String, _: String));
    spark.udf.register("decryptStr", decryptStr(_: String, _: String));

    //    val teenagers2 = spark.sql("SELECT name, age, message,addr, encryptStr(name,addr) as str FROM people ORDER BY age")
    //    teenagers2.map(t => "name:" + t(0) + " age:" + t(1) + " addr:" + t(2)).collect().foreach(println)
    //    teenagers2.show();
    // 加密操作
    val teenagers3 = spark.sql("SELECT encryptStr(name,'123') AS name, encryptStr(age,123) as age, encryptStr(message,'123') as message, encryptStr(addr,'123') as addr FROM people ORDER BY age")
    teenagers3.map(t => "name:" + t(0) + " age:" + t(1) + " addr:" + t(2)).collect().foreach(println)
    teenagers3.show();
    teenagers3.createOrReplaceTempView("people_encrypt");

    // 解密操作
    val teenagers4 = spark.sql("SELECT decryptStr(name,'123') AS name, decryptStr(age,123) as age, decryptStr(message,'123') as message, decryptStr(addr,'123') as addr FROM people_encrypt ORDER BY age")
    teenagers4.show();


    ssc.stop();
  }


  def getLen(str: String, addr: String): Int = {
    str.length + addr.length
  }

  def concatStr(str: String, addr: String): String = {
    str + "<---XXG-->" + addr
  }

  def decryptStr(text: String, password: String): String = {
    var result = ""
    if (text == null || text.trim.isEmpty || text.equalsIgnoreCase("NULL")) {
      text
    } else {
      result =  text.toString + "|" + password
    }
    result
  }

  def encryptStr(text: String, password: String): String = {
    var result = ""
    if (text == null || text.trim.isEmpty || text.equalsIgnoreCase("NULL")) {
      text
    } else {
      result = text.toString + "_" + password
    }
    result
  }

  case class Person(name: String, message: String, age: Int, addr: String)

}
