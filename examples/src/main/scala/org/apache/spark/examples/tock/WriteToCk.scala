package org.apache.spark.examples.tock

import java.util
import java.util.Properties

import org.apache.spark.sql.execution.datasources.jdbc.JDBCOptions
import org.apache.spark.SparkConf
import org.apache.spark.sql.{SaveMode, SparkSession}
import org.apache.spark.storage.StorageLevel

/**
  * 参数说明
  * your-user-name：目标ClickHouse集群中创建的数据库账号名。
  * your-pasword：数据库账号名对应的密码。
  * your-url：目标ClickHouse集群地址。
  * /your/path/to/test/data/a.txt：要导入的数据文件的路径，包含文件地址和文件名。
  * 说明 文件中的数据及schema，需要与ClickHouse中目标表的结构保持一致。
  * your-table-name：ClickHouse集群中的目标表名称。
  *
  *  ${SPARK_HOME}/bin/spark-submit
  *  --class "com.spark.test.WriteToCk"
  *  --master local[4]
  *  -conf "spark.driver.extraClassPath=${HOME}/.m2/repository/ru/yandex/clickhouse/clickhouse-jdbc/0.2.4/clickhouse-jdbc-0.2.4.jar"
  *  --conf "spark.executor.extraClassPath=${HOME}/.m2/repository/ru/yandex/clickhouse/clickhouse-jdbc/0.2.4/clickhouse-jdbc-0.2.4.jar"
  *  target/scala-2.12/simple-project_2.12-1.0.jar
  *
  */
object WriteToCk {

  val properties = new Properties()

  properties.put("driver", "ru.yandex.clickhouse.ClickHouseDriver")
  properties.put("user", "<your-user-name>")
  properties.put("password", "<your-password>")
  properties.put("batchsize", "100000")
  properties.put("socket_timeout", "300000")
  properties.put("numPartitions", "8")
  properties.put("rewriteBatchedStatements", "true")

  val url = "jdbc:clickhouse://<you-url>:8123/default"
  val table = "<your-table-name>"

  def main(args: Array[String]): Unit = {

    val sc = new SparkConf()
    sc.set("spark.driver.memory", "1G")
    sc.set("spark.driver.cores", "4")
    sc.set("spark.executor.memory", "1G")
    sc.set("spark.executor.cores", "2")

    val session = SparkSession.builder().master("local[*]").config(sc).appName("write-to-ck").getOrCreate()

    val df = session.read.format("csv")
      .option("header", "true")
      .option("sep", ",")
      .option("inferSchema", "true")
      .load("</your/path/to/test/data/a.txt>")
      .selectExpr(
        "Year",
        "Quarter",
        "Month"
      ).persist(StorageLevel.MEMORY_ONLY_SER_2)

    println(s"read done")

    df.write.mode(SaveMode.Append).option(JDBCOptions.JDBC_BATCH_INSERT_SIZE, 100000).jdbc(url, table, properties)
    println(s"write done")

    df.unpersist(true)
  }
}