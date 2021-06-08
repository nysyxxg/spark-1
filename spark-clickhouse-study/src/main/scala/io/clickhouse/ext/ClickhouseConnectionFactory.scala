package io.clickhouse.ext

import java.util.Properties

import cc.blynk.clickhouse.ClickHouseDataSource
import cc.blynk.clickhouse.settings.{ClickHouseConnectionSettings, ClickHouseProperties}
//import ru.yandex.clickhouse.ClickHouseDataSource
//import ru.yandex.clickhouse.settings.ClickHouseProperties

object ClickhouseConnectionFactory extends Serializable {

  private val dataSources = scala.collection.mutable.Map[(String, Int), ClickHouseDataSource]()

  var userName: String = _
  var password: String = _

  def setUserAndPwd(userName: String, password: String): Unit = {
    this.userName = userName
    this.password = password
  }

  def get(host: String, port: Int = 8123): ClickHouseDataSource = {
    dataSources.get((host, port)) match {
      case Some(ds) =>
        ds
      case None =>
        val ds = createDatasource(host, port = port)
        dataSources += ((host, port) -> ds)
        ds
    }
  }

  private def createDatasource(host: String, dbO: Option[String] = None, port: Int = 8123) = {
    val props = new Properties()
    dbO map { db => props.setProperty("database", db) }
    props.put(ClickHouseConnectionSettings.KEEP_ALIVE_TIMEOUT, "30000")
    props.put(ClickHouseConnectionSettings.SOCKET_TIMEOUT, "30000")
    props.put(ClickHouseConnectionSettings.DATA_TRANSFER_TIMEOUT, "300000")
    Class.forName("cc.blynk.clickhouse.ClickHouseDriver")
    val clickHouseProps = new ClickHouseProperties(props)
    //val httpUrl = s"jdbc:clickhouse://$host:$port/dbname?keepAliveTimeout=300000&socket_timeout=300000&dataTransferTimeout=300000"
    val tcpUrl = s"jdbc:clickhouse://$host:$port"
    new ClickHouseDataSource(tcpUrl, clickHouseProps)
  }

  def getByDBName(host: String, port: Int = 8123, dbName: String): ClickHouseDataSource = {
    createDatasourceByDBName(host, port = port, dbName)
  }

  private def createDatasourceByDBName(host: String, port: Int = 8123, dbName: String) = {
    val props = new Properties()
    props.setProperty("database", dbName)
    Class.forName("cc.blynk.clickhouse.ClickHouseDriver")
    val clickHouseProps = new ClickHouseProperties(props)
    val httpUrl = s"jdbc:clickhouse://$host:$port/$dbName?keepAliveTimeout=300000&socket_timeout=300000&dataTransferTimeout=300000"
    //val tcpUrl = s"jdbc:clickhouse://$host:$port"
    new ClickHouseDataSource(httpUrl, clickHouseProps)
  }

}
