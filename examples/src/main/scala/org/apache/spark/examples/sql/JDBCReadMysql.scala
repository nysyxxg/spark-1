package org.apache.spark.examples.sql

import org.apache.spark.examples.SparkAppCommon
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.sql.SparkSession

object JDBCReadMysql extends  SparkAppCommon{

  def main(args: Array[String]): Unit = {
    var jarArray = new Array[String](10)
    jarArray(0)={"C://SoftWare/jdbc/mysql-connector-java-5.1.45.jar"}
    val conf = new SparkConf().setAppName("MysqlDemo").setMaster("local[2]").setJars(jarArray)

    val sc = new SparkContext(conf)
    //创建SQLContext spark1.6.1以下的写法
    //val sqlContext = new SQLContext(sc)
    //spark2.0 以上的写法
    val sqlContext = SparkSession.builder().config(conf).getOrCreate()
    // 从mysql中加载数据
    val jdbcDF = sqlContext.read.format("jdbc")
      .options(
        Map("url" -> "jdbc:mysql://172.21.1.185:3306/mytest_xxg",
          "driver" -> "com.mysql.jdbc.Driver",
          "dbtable" -> "t_person",
          "user" -> "root",
          "password" -> "123456")
      ).load()
    // 执行查询
    jdbcDF.show()
  }
}
