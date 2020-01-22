package org.apache.spark.examples.sql

import java.util.Properties
import org.apache.spark.sql.{Row, SQLContext, SparkSession}
import org.apache.spark.sql.types.{IntegerType, StringType, StructField, StructType}
import org.apache.spark.{SparkConf, SparkContext}

/**
  * spark 读写mysql
  * spark-shell
  * --master spark://hadoop1:7077
  * --jars mysql-connector-java-5.1.35-bin.jar
  * --driver-class-path mysql-connector-java-5.1.35-bin.jar
  * spark-submit
  * --class cn.itcast.spark.sql.jdbcDF
  * --master spark://hadoop1:7077
  * --jars mysql-connector-java-5.1.35-bin.jar
  * --driver-class-path mysql-connector-java-5.1.35-bin.jar
  * /root/demo.jar
  *
  **/
object JdbcDFDemo {
  def main(args: Array[String]): Unit = {
    val conf = new SparkConf().setAppName("MysqlDemo").setMaster("local[2]")
    val sc = new SparkContext(conf)
    //创建SQLContext spark1.6.1以下的写法
    //val sqlContext = new SQLContext(sc)

    //spark2.0 以上的写法
    val sqlContext = SparkSession.builder().config(conf).getOrCreate()

    // 从mysql中加载数据
    val jdbcDF = sqlContext.read.format("jdbc")
      .options(
        Map("url" -> "jdbc:mysql://hadoop1:3306/bigdata",
          "driver" -> "com.mysql.jdbc.Driver",
          "dbtable" -> "person", "user" -> "root",
          "password" -> "123456")
      ).load()
    // 执行查询
    jdbcDF.show()


    //通过并行化创建RDD
    val personRDD = sc.parallelize(Array("1 tom 5", "2 jerry 3", "3 kitty 6")).map(_.split(" "))
    //通过StructType直接指定每个字段的schema
    val schema = StructType(
      List(
        StructField("id", IntegerType, true),
        StructField("name", StringType, true),
        StructField("age", IntegerType, true)
      )
    )

    //将RDD映射到rowRDD
    val rowRDD = personRDD.map(p => Row(p(0).toInt, p(1).trim, p(2).toInt))
    //将schema信息应用到rowRDD上
    val personDataFrame = sqlContext.createDataFrame(rowRDD, schema)
    //创建Properties存储数据库相关属性
    val prop = new Properties()
    prop.put("user", "root")
    prop.put("password", "123456")
    //将数据追加到数据库
    personDataFrame.write.mode("append").jdbc("jdbc:mysql://localhost:3306/bigdata", "bigdata.person", prop)

    sc.stop()
  }
}