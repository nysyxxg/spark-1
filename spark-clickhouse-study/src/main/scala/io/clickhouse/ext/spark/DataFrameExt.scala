package io.clickhouse.ext.spark

import java.sql.ResultSet
import java.util

import cc.blynk.clickhouse.{ClickHouseConnection, ClickHouseDataSource}
import io.clickhouse.ext.{ClickhouseClient, ClickhouseConnectionFactory}
import org.apache.spark.sql.{DataFrame, Row, SparkSession}
//import ru.yandex.clickhouse.ClickHouseDataSource
import io.clickhouse.ext.Utils._
import org.apache.spark.sql.types._

object ClickhouseSparkExt {
  implicit def extraOperations(df: org.apache.spark.sql.DataFrame) = DataFrameExt(df)
}

case class DataFrameExt(df: org.apache.spark.sql.DataFrame) extends Serializable {
  var userName = ClickhouseConnectionFactory.userName
  var password = ClickhouseConnectionFactory.password

  def dropClickhouseDb(dbName: String, clusterNameO: Option[String] = None)
                      (implicit ds: ClickHouseDataSource) {
    val client = ClickhouseClient(clusterNameO)(ds, userName, password)
    clusterNameO match {
      case None => client.dropDb(dbName)
      case Some(x) => client.dropDbCluster(dbName)
    }
  }

  def createClickhouseDb(dbName: String, clusterNameO: Option[String] = None)
                        (implicit ds: ClickHouseDataSource) {
    val client = ClickhouseClient(clusterNameO)(ds, userName, password)
    clusterNameO match {
      case None => client.createDb(dbName)
      case Some(x) => client.createDbCluster(dbName)
    }
  }

  def createClickhouseTable(dbName: String, tableName: String, partitionColumnName: String,
                            indexColumns: Seq[String], clusterNameO: Option[String] = None)
                           (implicit ds: ClickHouseDataSource) {
    val client = ClickhouseClient(clusterNameO)(ds, userName, password)
    val sqlStmt = createClickhouseTableDefinitionSQL(dbName, tableName, partitionColumnName, indexColumns)
    println("建表语句 sqlStmt ： " + sqlStmt)
    clusterNameO match {
      case None => client.query(sqlStmt)
      case Some(clusterName) =>
        // create local table on every node
        client.queryCluster(sqlStmt)
        // create distrib table (view) on every node
        val sqlStmt2 = s"CREATE TABLE IF NOT EXISTS ${dbName}.${tableName}_all AS ${dbName}.${tableName} ENGINE = Distributed($clusterName, $dbName, $tableName, rand());"
        println("建表语句 sqlStmt2 ： " + sqlStmt2)
        client.queryCluster(sqlStmt2)
    }
  }

  def saveToClickhouse(dbName: String, tableName: String, partitionFunc: (org.apache.spark.sql.Row) => java.sql.Date,
                       partitionColumnName: String = "mock_date",
                       clusterNameO: Option[String] = None,
                       batchSize: Int = 100000)
                      (implicit ds: ClickHouseDataSource) = {

    val defaultHost = ds.getHost
    val defaultPort = ds.getPort

    val (clusterTableName, clickHouseHosts) = clusterNameO match {
      case Some(clusterName) =>
        // get nodes from cluster
        val client = ClickhouseClient(clusterNameO)(ds, userName, password)
        (s"${tableName}_all", client.getClusterNodes())
      case None =>
        (tableName, Seq(defaultHost))
    }

    val schema = df.schema

    // following code is going to be run on executors
    val insertResults = df.rdd.mapPartitions((partition: Iterator[org.apache.spark.sql.Row]) => {

      val rnd = scala.util.Random.nextInt(clickHouseHosts.length)
      val targetHost = clickHouseHosts(rnd)
      val targetHostDs = ClickhouseConnectionFactory.get(targetHost, defaultPort)

      // explicit closing
      using(targetHostDs.getConnection(userName, password)) { conn =>
         println("插入表： " +  clusterTableName)
        val insertStatementSql = generateInsertStatment(schema, dbName, clusterTableName, partitionColumnName)
        val statement = conn.prepareStatement(insertStatementSql)

        var totalInsert = 0
        var counter = 0

        while (partition.hasNext) {
          counter += 1
          val row = partition.next()
          // create mock date
          val partitionVal = partitionFunc(row)
          statement.setDate(1, partitionVal)
          // map fields
          schema.foreach { f =>
            val fieldName = f.name
            val fieldIdx = row.fieldIndex(fieldName)
            val fieldVal = row.get(fieldIdx)
            if (fieldVal != null)
              statement.setObject(fieldIdx + 2, fieldVal)
            else {
              val defVal = defaultNullValue(f.dataType, fieldVal)
              statement.setObject(fieldIdx + 2, defVal)
            }
          }
          statement.addBatch()

          if (counter >= batchSize) {
            val r = statement.executeBatch()
            totalInsert += r.sum
            counter = 0
          }

        } // end: while

        if (counter > 0) {
          val r = statement.executeBatch()
          totalInsert += r.sum
          counter = 0
        }

        // return: Seq((host, insertCount))
        List((targetHost, totalInsert)).toIterator
      }

    }).collect()

    // aggr insert results by hosts
    insertResults.groupBy(_._1)
      .map(x => (x._1, x._2.map(_._2).sum))
  }

  def selectClickhouseBySql(clusterNameO: Option[String] = None, querySql: String)
                           (implicit ds: ClickHouseDataSource) = {
    val client = ClickhouseClient(clusterNameO)(ds, userName, password)
    var rs = clusterNameO match {
      case None => client.query(querySql)
      case Some(x) => client.queryCluster(querySql)
    }
    rs
  }

  def selectClickhouseBySqlToDF(clusterNameO: Option[String] = None, querySql: String)
                           (implicit ds: ClickHouseDataSource) = {
    val client = ClickhouseClient(clusterNameO)(ds, userName, password)
    var rs = clusterNameO match {
      case None => client.query(querySql)
      case Some(x) => client.queryCluster(querySql)
    }
    rs
  }


  private def generateInsertStatment(schema: org.apache.spark.sql.types.StructType, dbName: String,
                                     tableName: String, partitionColumnName: String) = {
    val columns = partitionColumnName :: schema.map(f => f.name).toList
    val vals = 1 to (columns.length) map (i => "?")
    s"INSERT INTO $dbName.$tableName (${columns.mkString(",")}) VALUES (${vals.mkString(",")})"
  }

  private def defaultNullValue(sparkType: org.apache.spark.sql.types.DataType, v: Any) = sparkType match {
    case DoubleType => 0
    case LongType => 0
    case FloatType => 0
    case IntegerType => 0
    case StringType => null
    case BooleanType => false
    case _ => null
  }

  private def createClickhouseTableDefinitionSQL(dbName: String, tableName: String, partitionColumnName: String,
                                                 indexColumns: Seq[String]) = {

    val header =
      s"""
          CREATE TABLE IF NOT EXISTS $dbName.$tableName(
          """

    val columns = s"$partitionColumnName Date" :: df.schema.map { f =>
      Seq(f.name, sparkType2ClickhouseType(f.dataType)).mkString(" ")
    }.toList

    val columnsStr = columns.mkString(",\n")
    val footer =
      s"""
          )ENGINE = MergeTree($partitionColumnName, (${indexColumns.mkString(",")}), 8192);
          """

    Seq(header, columnsStr, footer).mkString("\n")
  }

  private def sparkType2ClickhouseType(sparkType: org.apache.spark.sql.types.DataType) =
    sparkType match {
      case LongType => "Int64"
      case DoubleType => "Float64"
      case FloatType => "Float32"
      case IntegerType => "Int32"
      case StringType => "String"
      case BooleanType => "UInt8"
      case _ => "unknown"
    }


  def createStructField(name:String,colType:String):StructField={
    colType match {
      case "java.lang.String" =>{ return StructField(name,StringType,true)}
      case "java.lang.Integer" =>{return StructField(name,IntegerType,true)}
      case "java.lang.Long" =>{return StructField(name,LongType,true)}
      case "java.lang.Boolean" =>{return StructField(name,BooleanType,true)}
      case "java.lang.Double" =>{return StructField(name,DoubleType,true)}
      case "java.lang.Float" =>{return StructField(name,FloatType,true)}
      case "java.sql.Date" =>{return StructField(name,DateType,true)}
      case "java.sql.Time" =>{return StructField(name,TimestampType,true)}
      case "java.sql.Timestamp" =>{return StructField(name,TimestampType,true)}
      case "java.math.BigDecimal" =>{return StructField(name,DecimalType(10,0),true)}
    }
  }
  /**
    * 把查出的ResultSet转换成DataFrame
    */
//  def createResultSetToDF(sparkSession:SparkSession,rs:ResultSet):DataFrame={
//    val rsmd = rs.getMetaData
//    val columnTypeList = new util.ArrayList[String]
//    val rowSchemaList = new util.ArrayList[StructField]
//    for(i <- 1 to rsmd.getColumnCount){
//      var temp = rsmd.getColumnClassName(i)
//      temp=temp.substring(temp.lastIndexOf(".")+1)
//      if("Integer".equals(temp)){
//        temp="Int";
//      }
//      columnTypeList.add(temp)
//      rowSchemaList.add(createStructField(rsmd.getColumnName(i),rsmd.getColumnClassName(i)))
//    }
//    val rowSchema = StructType(Seq(rowSchemaList:_*))
//    //ResultSet反射类对象
//    val rsClass =  rs.getClass
//    var count=1;
//    var resultList = new util.ArrayList[Row]
//    var totalDF = sparkSession.createDataFrame(new util.ArrayList[Row], rowSchema)
//    while (rs.next()) {
//      count=count+1;
//      val temp = new util.ArrayList[Object]
//      for(i <- 0 to columnTypeList.size()-1){
//        val method = rsClass.getMethod("get"+columnTypeList.get(i),"aa".getClass)
//        temp.add(method.invoke(rs, rsmd.getColumnName(i+1)))
//      }
//      resultList.add(Row(temp:_*))
//      if(count%100000==0){
//        val tempDF =  sparkSession.createDataFrame(resultList, rowSchema)
//        totalDF=totalDF.union(tempDF).distinct()
//        resultList.clear()
//      }
//    }
//    val tempDF =  sparkSession.createDataFrame(resultList, rowSchema)
//    totalDF=totalDF.union(tempDF)
//    return totalDF
//  }
}
