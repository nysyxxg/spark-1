package org.apache.spark.examples.tock

import java.sql.{Connection, DriverManager}
import java.util.Properties

import org.apache.spark.sql.execution.datasources.jdbc.JDBCOptions
import org.apache.spark.sql.{SaveMode, SparkSession}

object SparkWriteToCK {

  val ckProperties = new Properties()
  val url = "jdbc:clickhouse://<您的clickhouse VPC 地址>:8123/default"
  ckProperties.put("driver", "ru.yandex.clickhouse.ClickHouseDriver")
  ckProperties.put("user", "<用户名>")
  ckProperties.put("password", "<密码>")
  ckProperties.put("batchsize", "100000")
  ckProperties.put("socket_timeout", "300000")
  ckProperties.put("numPartitions", "8")
  ckProperties.put("rewriteBatchedStatements", "true")

  /**
    *  创建ClickHouse表
    * 创建非分区表：
    */
  def createCKTable(table: String): Unit = {
    Class.forName(ckProperties.getProperty("driver"))
    var conn: Connection = null
    try {
      conn = DriverManager.getConnection(url, ckProperties.getProperty("user"), ckProperties.getProperty("password"))
      val stmt = conn.createStatement()
      val sql =
        s"""
           |create table if not exists default.${table}  on cluster default(
           |    `name` String,
           |    `age`  Int32)
           |ENGINE = MergeTree() ORDER BY `name` SETTINGS index_granularity = 8192;
           |""".stripMargin

      stmt.executeQuery(sql)
    } catch {
      case e: Exception => e.printStackTrace()
    } finally {
      if (conn != null)
        conn.close()
    }
  }


  /**
    * 创建分区表：其实就加partition，最后一行加分区信息就可以了
    * Clickhouse> insert into visits(name,age,day)
    * values
    * (100,'10','2020-06-01'),
    * (100,'20','2020-07-02'),
    * (100,'30','2020-08-03');
    * @param table
    * @param day
    */
  def createCKTableByPartition(table: String,day:String ): Unit = {
    Class.forName(ckProperties.getProperty("driver"))
    var conn: Connection = null
    try {
      conn = DriverManager.getConnection(url, ckProperties.getProperty("user"), ckProperties.getProperty("password"))
      val stmt = conn.createStatement()
      val sql =
        s"""
           |create table if not exists default.${table}  on cluster default(
           |    `name` String,
           |    `age`  Int32
           |     day   Date
           |    )
           |ENGINE = MergeTree()  PARTITION BY  ${day}  ORDER BY `name`
           | SETTINGS index_granularity = 8192;
           |""".stripMargin

      stmt.executeQuery(sql)
    } catch {
      case e: Exception => e.printStackTrace()
    } finally {
      if (conn != null)
        conn.close()
    }
  }


  def main(args: Array[String]): Unit = {
    val table = "ck_test"
    //使用jdbc建ClickHouse表
    createCKTable(table)

    val spark = SparkSession.builder().getOrCreate()
    //将csv的数据写入ClickHouse
    val csvDF = spark.read.option("header", "true").csv("oss://<path/to>/ck.csv").toDF("name", "age")
    csvDF.printSchema()
    csvDF.write.mode(SaveMode.Append).option(JDBCOptions.JDBC_BATCH_INSERT_SIZE, 100000).jdbc(url, table, ckProperties)
    //读ck表数据
    val ckDF = spark.read.jdbc(url, table, ckProperties)
    ckDF.show()
  }

}