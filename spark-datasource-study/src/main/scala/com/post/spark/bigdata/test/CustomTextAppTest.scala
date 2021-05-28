package com.post.spark.bigdata.test

import org.apache.spark.sql.SparkSession

object CustomTextAppTest {

  def main(args: Array[String]): Unit = {

    val sparkSession = SparkSession.builder()
      .appName("DataSourceAPIApp")
      .master("local[2]")
      .getOrCreate()
    val filePath = "D:\\Spark_Ws\\spark-apache\\spark-datasource-study\\data\\people.txt"
    //需要使用固定写法DefaultSource
    //Caused by: java.lang.ClassNotFoundException: com.post.spark.bigdata.text.DefaultSource
    val people = sparkSession.read
      //  .text("file:///Users/wn/ide/idea/src/main/scala/com/post/spark/bigdata/app/people")
      .format("com.post.spark.bigdata.text.DefaultSource")
      .option("path", filePath).load()

    people.printSchema()
    /*
    root
     |-- id: long (nullable = false)
     |-- name: string (nullable = false)
     |-- gender: string (nullable = false)
     |-- salary: long (nullable = false)
     |-- comm: long (nullable = false)
     */
    import sparkSession.implicits._ //'id>3

    //GreaterThan(id,3) id>3
    people.select('name,'id,'gender,'salary,'comm).filter("name != 'zhangsan'").filter('id >= 2).show()

    var peopeleRDD = people.toDF()
    peopeleRDD.createOrReplaceTempView("people")
    peopeleRDD.sqlContext.sql("select *  from people where id = 8").show()

    sparkSession.stop()
  }

}
