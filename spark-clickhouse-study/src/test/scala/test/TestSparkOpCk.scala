package test

import java.sql.ResultSet

import cc.blynk.clickhouse.ClickHouseConnection
import io.clickhouse.ext.{ClickhouseConnectionFactory, ClusterResultSet}
import io.clickhouse.ext.spark.ClickhouseSparkExt._
import jodd.util.PropertiesUtil
import org.apache.spark.sql.SparkSession

case class Row2(name: String, v: Int, v2: Int)


object TestSparkOpCk {

  def main(args: Array[String]): Unit = {

    val fileName = "D:\\Spark_Ws\\spark-apache\\examples\\src\\main\\resources\\application.properties"
    val props = PropertiesUtil.createFromFile(fileName)


    val sparkSession = SparkSession.builder
      .master("local")
      .appName("local spark")
      .getOrCreate()

    val sc = sparkSession.sparkContext
    val sqlContext = sparkSession.sqlContext

    // test dframe
    val df = sqlContext.createDataFrame(1 to 10 map (i => Row2(s"$i", i, i + 10)))

    // clickhouse params
    val anyHost = props.getProperty("ck.hostname")
    val port: Int = props.getProperty("ck.httpport").toInt
    val userName = props.getProperty("ck.username")
    val password = props.getProperty("ck.password")
    val dbName = props.getProperty("ck.dbName")

    val clusterNa = "cluster_eip"
    val tableName = "t1"
    val partitionColumnName: String = "mock_date"

    //    val clusterName = None: Option[String]
    // start clickhouse docker using config.xml from clickhouse_files
    val clusterName = Some(clusterNa): Option[String]
    // define clickhouse connection
    implicit val clickhouseDataSource = ClickhouseConnectionFactory.get(anyHost, port)
    ClickhouseConnectionFactory.setUserAndPwd(userName, password)
    //implicit val ckConnection: ClickHouseConnection = clickhouseDataSource.getConnection(userName, password)
    // create db  ，需要对用赋予权限
    //        df.createClickhouseDb(dbName, clusterName)

    // drop db
    //    df.dropClickhouseDb(dbName, clusterName)

    // create table
    // df.createClickhouseTable(dbName, tableName, partitionColumnName, Seq("name"), clusterNameO = clusterName)

    // save data
    val res = df.saveToClickhouse(dbName, tableName, (row) => java.sql.Date.valueOf("2000-12-01"), partitionColumnName, clusterNameO = clusterName)
    //assert(res.size == 1)
    //assert(res.get("localhost") == Some(df.count()))

    // count data 统计数据
    //    println("--------统计条数--->" + df.count())
    var querySql = "select *  from " + dbName + "." + tableName;
    println("查询sql： " + querySql)
    //    var tableRdd = df.selectClickhouseBySql(clusterName, querySql)
    //    var clusterResultSet = tableRdd.asInstanceOf[ClusterResultSet]
    //    clusterResultSet.toTab

    df.selectClickhouseBySql(clusterName, querySql)

  }

}
