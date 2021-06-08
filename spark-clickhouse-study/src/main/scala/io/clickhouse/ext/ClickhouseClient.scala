package io.clickhouse.ext

//import ru.yandex.clickhouse.ClickHouseDataSource
import cc.blynk.clickhouse.{ClickHouseDataSource}
import io.clickhouse.ext.Utils._

case class ClickhouseClient(clusterNameO: Option[String] = None)
                           (implicit ds: ClickHouseDataSource, userName: String, password: String) {

  import io.clickhouse.ext.ClickhouseResultSetExt._

  def createDb(dbName: String) {
    query(s"create database if not exists $dbName")
  }

  def dropDb(dbName: String) {
    query(s"DROP DATABASE IF EXISTS $dbName")
  }

  def query(sql: String) = {
    using(ds.getConnection(userName, password)) { conn =>
      val statement = conn.createStatement()
      val rs = statement.executeQuery(sql)
      println("-----------------开始遍历数据---------------------")
      while (rs.next()) {
        var date = rs.getDate(1)
        var name = rs.getString(2)
        var id = rs.getInt(3)
        var gender = rs.getInt(4);
        println("date : " + date + "  id:" + id + " 姓名：" + name + " 性别：" + gender)
      }
      rs
    }
  }

  def queryCluster(sql: String) = {
    val resultSet = runOnAllNodes(sql)
    ClusterResultSet(resultSet)
  }

  def createDbCluster(dbName: String) = {
    runOnAllNodes(s"create database if not exists $dbName")
      .count(x => x._2 == null)
  }

  def dropDbCluster(dbName: String) = {
    runOnAllNodes(s"DROP DATABASE IF EXISTS $dbName")
      .count(x => x._2 == null)
  }

  def getClusterNodes() = {
    val clusterName = isClusterNameProvided()
    using(ds.getConnection(userName, password)) { conn =>
      val statement = conn.createStatement()
      val rs = statement.executeQuery(s"select host_name, host_address from system.clusters where cluster == '$clusterName'")
      val r = rs.map(x => x.getString("host_name"))
      require(r.nonEmpty, s"cluster $clusterNameO not found")
      r
    }
  }

  private def runOnAllNodes(sql: String) = {
    getClusterNodes().map { nodeIp =>
      println("--------------查询集群的每一个节点：" + nodeIp + "---->执行SQL：" + sql)
      val nodeDs = ClickhouseConnectionFactory.get(nodeIp)
      val client = ClickhouseClient(clusterNameO)(nodeDs, userName, password)
      (nodeIp, client.query(sql))
    }
  }

  private def isClusterNameProvided() = {
    clusterNameO match {
      case None => throw new Exception("cluster name is requires")
      case Some(clusterName) => clusterName
    }
  }
}