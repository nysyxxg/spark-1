package org.apache.spark.examples.tock

import java.sql.{Connection, Driver, DriverManager, SQLException}
import java.util
import java.util.{Enumeration, Properties, Random}

import com.github.housepower.jdbc.ClickHouseDriver
import org.apache.spark.SparkConf
import org.apache.spark.sql.types.{DoubleType, LongType, StringType}
import org.apache.spark.sql.{SaveMode, SparkSession}

//import ru.yandex.clickhouse.ClickHouseDataSource
/**
  * spark 2.4.0版本，使用4个执行器，每个执行器1 core & 4g，
  * batchsize设置为50000，写入500万条数据，用时76秒
  *
  */
object OfficialJDBCDriver {

  val chDriver = "ru.yandex.clickhouse.ClickHouseDriver"
  val chUrls = Array(
    "jdbc:clickhouse://1.1.1.1:8123/default",
    "jdbc:clickhouse://2.2.2.2:8123/default")

  /**
    * 获取连接第一种方法
    *
    * @param JDBCNativeURL
    * @param username
    * @param password
    * @throws
    * @return
    */
  //  def getConnectionJDBCHTTP(chUrl: String, pro: Properties): ClickHouseConnectionImpl ={
  //    val chDatasource:ClickHouseDataSource = new ClickHouseDataSource(chUrl, pro)
  //    val  connection :ClickHouseConnectionImpl= chDatasource.getConnection("default", "123456")
  //    connection
  //  }

  /**
    * 获取连接第二种
    *
    * @param JDBCNativeURL
    * @param username
    * @param password
    * @throws
    * @return
    */
  @throws[SQLException]
  def getConnectionJDBCNative(JDBCNativeURL: String, username: String, password: String): Connection = {
    val drivers: util.Enumeration[Driver] = DriverManager.getDrivers
    while ( {
      drivers.hasMoreElements
    }) DriverManager.deregisterDriver(drivers.nextElement)
    DriverManager.registerDriver(new ClickHouseDriver)
    val connection: Connection = DriverManager.getConnection(JDBCNativeURL, username, password)
    connection
  }

  def main(args: Array[String]): Unit = {
    if (args.length < 3) {
      System.err.println("Usage: OfficialJDBCDriver <tableName> <partitions> <batchSize>\n" +
        "  <tableName> is the hive table name \n" +
        "  <partitions> is the partitions which want insert into clickhouse, like 20200516,20200517\n" +
        "  <batchSize> is JDBC batch size, may be 1000\n\n")
      System.exit(1)
    }

    val (tableName, partitions, batchSize) = (args(0), args(1).split(","), args(2).toInt)
    val sparkConf: SparkConf =
      new SparkConf()
        .setAppName("OfficialJDBCDriver ")

    val spark: SparkSession =
      SparkSession
        .builder()
        .enableHiveSupport()
        .config(sparkConf)
        .getOrCreate()

    val pro = new java.util.Properties
    pro.put("driver", chDriver)
    pro.setProperty("user", "default")
    pro.setProperty("password", "123456")
    var chShardIndex = 1
    for (partition <- partitions) {
      val chUrl = chUrls((chShardIndex - 1) % chUrls.length)
      val sql = s"select * from tmp.$tableName where dt = $partition"
      val df = spark.sql(sql)
      val (fieldNames, placeholders) = df.schema.fieldNames.foldLeft("", "")(
        (str, name) =>
          if (str._1.nonEmpty && str._2.nonEmpty)
            (str._1 + ", " + name, str._2 + ", " + "?")
          else (name, "?")
      )
      val insertSQL = s"insert into my_table ($fieldNames) values ($placeholders)"

      df.foreachPartition(records => {
        try {
          var count = 0
          val chConn = getConnectionJDBCNative(chUrl, "root", "123456")
          val psmt = chConn.prepareStatement(insertSQL)
          while (records.hasNext) {
            val record = records.next()
            var fieldIndex = 1
            record.schema.fields.foreach(field => {
              field.dataType match {
                case StringType =>
                  psmt.setString(fieldIndex, record.getAs[String](field.name))
                case LongType =>
                  psmt.setLong(fieldIndex, record.getAs[Long](field.name))
                case DoubleType =>
                  psmt.setDouble(fieldIndex, record.getAs[Double](field.name))
                // 这里可以新增自己需要的type
                case _ => println(s" other type: ${field.dataType}")
              }
              fieldIndex += 1
            })
            psmt.addBatch()
            // 批量写入
            if (count % batchSize == 0) {
              psmt.executeBatch()
              psmt.clearBatch()
            }
            count += 1
          }
          psmt.executeBatch()
          psmt.close()
          chConn.close()
        } catch {
          case e: Exception =>
            e.printStackTrace()
        }
      })
      chShardIndex += 1
    }
    spark.close()
  }
}
