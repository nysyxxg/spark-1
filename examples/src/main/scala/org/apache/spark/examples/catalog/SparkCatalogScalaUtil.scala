package org.apache.spark.examples.catalog

import java.util
import java.util.regex.Pattern

import org.apache.commons.lang3.StringUtils
import org.apache.hadoop.hive.metastore.api.FieldSchema
import org.apache.log4j.Logger
import org.apache.spark.sql.{AnalysisException, Dataset, Row, SparkSession}
import org.apache.spark.sql.catalog.Column


object SparkCatalogScalaUtil extends Serializable {

  private val log = Logger.getLogger(this.getClass)

  private val LOCATION_STR = "location";

  private val PATTERN = Pattern.compile("^.*Detailed (Table|Partition) Information.*$");

  /**
    * 通过版本获取分区字段
    *
    * @param dbName
    * @param tableName
    * @param version
    * @return
    */


  def listAllPartitionsByVersionV1(sparkSession: SparkSession, dbName: String, tableName: String, version: Short): java.util.List[String] = {
    val retList: java.util.List[String] = new java.util.ArrayList[String]
    val showSql: String = String.format("SHOW PARTITIONS `%s`.`%s`", dbName, tableName)
    log.info("Show Partitions sql : " + showSql)
    try {
      val dataset: Dataset[Column] = sparkSession.catalog.listColumns(dbName, tableName)
      val finalPartStr: String = "version_partition=" + version
      val columsArrys: Array[Column] = dataset.collect.clone
      //  String colums[] = dataset.columns();
      for (column <- columsArrys) {
        val columnName: String = column.name
        val isPartition: Boolean = column.isPartition
        log.info("--------表---------" + tableName + "--->  字段---" + columnName + "---isPartition--" + isPartition)
        //String parts[] = partStr.split("/");
        //                List list = Arrays.asList(parts);
        //                if (list.contains(finalPartStr)) {
        //                    retList.add(partStr);
        //                }
        retList.add(columnName)
      }
    } catch {
      case e: AnalysisException =>
        log.error(e.getMessage, e)
        throw new RuntimeException(e)
    }
    if (retList.size == 0) log.info("没有获取到数据分区！")
    retList
  }


  def listAllPartitionsByVersion(sparkSession: SparkSession, dbName: String, tableName: String, version: Short): java.util.List[String] = {
    val retList: java.util.List[String] = new java.util.ArrayList[String]
    val showSql: String = String.format("SHOW PARTITIONS `%s`.`%s`", dbName, tableName)
    log.info("Show Partitions sql : " + showSql)
    val rows: Array[Row] = sparkSession.sql(showSql).collect.clone
    try {
      var partStr: String = null
      val finalPartStr: String = "version_partition=" + version
      for (row <- rows) {
        val size = row.length
        partStr = row.getString(size - 1)
        log.info(size + "--------finalPartStr---------" + finalPartStr + "--->  字段---partStr--->" + partStr)
        val parts: Array[String] = partStr.split("/")
        val list:List[String] = parts.toList
        if (list.contains(finalPartStr)) {
          retList.add(partStr)
        }
      }
    } catch {
      case e: Exception =>
        log.error(e.getMessage, e)
        throw new RuntimeException(e)
    }
    if (retList.size == 0) {
      log.info("没有获取到数据分区！")
    }
    retList
  }

  def getHivePartitionField(sparkSession: SparkSession, dbName: String, tableName: String, partitionFields: java.util.Set[String]): java.util.List[String] = {
    val fieldSchemaList: java.util.List[String] = new java.util.ArrayList[String]
    try {
      val dataset: Dataset[Column] = sparkSession.catalog.listColumns(dbName, tableName)
      val columsArrys: Array[Column] = dataset.collect.clone
      //String colums[] = dataset.columns();
      for (column <- columsArrys) {
        val columnName: String = column.name
        log.info("--------表---------" + tableName + "--->  字段---" + columnName)
        if (partitionFields.contains(columnName)) {
          log.info("Hive表中" + tableName + "的分区字段列名：" + columnName)
          fieldSchemaList.add(columnName)
        }
      }
    } catch {
      case e: AnalysisException =>
        log.error(e.getMessage, e)
        throw new RuntimeException(e)
    }
    fieldSchemaList
  }


  def getAddPartitionSql(dbName: String, tableName: String, partition: String): String = {
    val alterPartSql: String = String.format("ALTER TABLE `%s`.`%s` ADD IF NOT EXISTS %s", dbName, tableName, partition)
    log.info("AddPartitionSql: " + alterPartSql)
    alterPartSql
  }


  def getDropPartitionSql(dbName: String, tableName: String, partition: String): String = {
    val dropPartitionSql: String = String.format("ALTER TABLE `%s`.`%s` DROP IF  EXISTS %s", dbName, tableName, partition)
    log.info("dropPartitionSql: " + dropPartitionSql)
    dropPartitionSql
  }

  private def getAttrStr(attrList: util.List[HiveColumn]): String = {
    val stringBuilder: StringBuilder = new StringBuilder
    var i: Int = 0
    for (i <- 0 to attrList.size - 1) {
      val attr: HiveColumn = attrList.get(i)
      if (i > 0) stringBuilder.append(", ")
      stringBuilder.append("`").append(attr.getColumnName).append("` ").append(attr.getColumnType)
      if (StringUtils.isNotBlank(attr.getDescribe)) {
        stringBuilder.append(" COMMENT '").append(attr.getDescribe).append("'")
      }
    }
    stringBuilder.toString
  }

  private def getNewColNameTypeMap(attrList: util.List[HiveColumn]): util.Map[String, String] = {
    val map: util.Map[String, String] = new util.HashMap[String, String]
    import scala.collection.JavaConversions._
    for (column <- attrList) {
      map.put(column.getColumnName.toLowerCase, column.getColumnType.toLowerCase)
    }
    map
  }

  def createHiveTableAndGetFieldList(sparkSession: SparkSession, dbName: String,
                                     table: HiveTable, attrList: java.util.List[HiveColumn],
                                     partitionString: String, partitionFields: util.Set[String]): java.util.List[FieldSchema] = {
    var tableName: String = table.getTableName
    var tableFormat: String = ""
    if (StringUtils.isBlank(tableFormat)) {
      tableFormat = "STORED AS PARQUET TBLPROPERTIES ('parquet.compression'='SNAPPY')"
    }
    val createNotExistsSql: String = "CREATE TABLE IF NOT EXISTS `" + dbName + "`.`" + tableName + "` (" + getAttrStr(attrList) + ") COMMENT '" + table.getTableDescribe + "' PARTITIONED BY (" + partitionString + ") " + tableFormat
    log.info("createSQL: " + createNotExistsSql)
    sparkSession.sql(createNotExistsSql)
    //        log.info("create hive table result:" +);
    //  得到首次创建tableName表的属性集合
    val tableColNames: util.List[FieldSchema] = getHiveAttrListWithOutPartitionKey(sparkSession, dbName, tableName, partitionFields)
    val newColNameType: util.Map[String, String] = getNewColNameTypeMap(attrList)
    val outHiveTableColNameList: util.List[String] = new util.ArrayList[String]
    import scala.collection.JavaConversions._
    for (fieldSchema <- tableColNames) {
      val attrName: String = fieldSchema.getName.toLowerCase
      val attrType: String = fieldSchema.getType
      outHiveTableColNameList.add(attrName)
      if (newColNameType.containsKey(attrName) && !newColNameType.get(attrName).equalsIgnoreCase(attrType)) { // 进行更新字段类型
        updateColumn(sparkSession, dbName, tableName, attrName, attrType, newColNameType)
      }
    }
    log.info("Column name: " + StringUtils.join(outHiveTableColNameList, ", "))
    // attrList 是目标表字段属性的集合
    if (attrList.size < tableColNames.size) { // 说明目标表中字段有删除
      val msg: String = tableName + " 目标表属性个数: " + attrList.size + ", hive表属性个数：" + tableColNames.size
      log.info(msg)
    }
    val addNewAttr: util.List[HiveColumn] = new util.ArrayList[HiveColumn]
    var i: Int = 0
    for (i <- 0 to attrList.size - 1) { // 找出新增的字段
      val attribute: HiveColumn = attrList.get(i)
      val addField: String = attribute.getColumnName
      if (!outHiveTableColNameList.contains(addField.toLowerCase)) {
        val msg: String = tableName + "  新增加的字段column=" + addField
        addNewAttr.add(attribute)
        log.info(msg)
      }
    }
    if (addNewAttr.size > 0) {
      val alterSql: String = "ALTER TABLE `" + dbName + "`.`" + tableName + "` ADD COLUMNS (" + getAttrStr(addNewAttr) + ")"
      try {
        log.info("alterSql: " + alterSql)
        sparkSession.sql(alterSql)
        log.info("result:执行成功！")
      } catch {
        case e: Exception =>
          e.printStackTrace()
          log.info("result:执行失败！" + alterSql)
      }
    }
    // 得到增加字段  和删除字段之后最新的集合
    val tableColNames2: util.List[FieldSchema] = getHiveAttrListWithOutPartitionKey(sparkSession, dbName, tableName, partitionFields)
    tableColNames2
  }


  private def updateColumn(sparkSession: SparkSession, dbName: String, tableName: String, attrName: String, attrType: String, colNameTypeMap: util.Map[String, String]): Unit = {
    val oldColumn: HiveColumn = new HiveColumn
    oldColumn.setColumnName(attrName)
    oldColumn.setColumnType(attrType)
    val newColumn: HiveColumn = new HiveColumn
    val newType: String = colNameTypeMap.get(attrName)
    newColumn.setColumnName(attrName)
    newColumn.setColumnType(newType)
    execUpdateColumn(sparkSession, dbName + "." + tableName, oldColumn, newColumn)
  }


  def execUpdateColumn(sparkSession: SparkSession, tableName: String, oldColumn: HiveColumn, newColumn: HiveColumn): Unit = {
    val sb: StringBuilder = new StringBuilder
    sb.append("alter table ")
    sb.append(tableName)
    sb.append(" change ")
    sb.append("`")
    sb.append(oldColumn.getColumnName).append("` `").append(newColumn.getColumnName).append("` ")
    if (StringUtils.isNotEmpty(newColumn.getColumnType)) sb.append(newColumn.getColumnType)
    else if (null != newColumn.getType) sb.append(HiveTypes.toHiveType(newColumn.getType))
    val alterSql: String = sb.toString
    log.info("alter sql:[" + alterSql + "]")
    try {
      sparkSession.sql(alterSql)
      log.info("alter sql:[" + alterSql + "]----> 执行成功！")
    } catch {
      case e: Exception =>
        log.error(e.getMessage, e)
        log.error("操作Hive库失败" + e.getMessage)
        throw new RuntimeException(e)
    }
  }


  def getHiveAttrListWithOutPartitionKey(sparkSession: SparkSession, dbName: String, tableName: String, partitionFields: util.Set[String]): util.List[FieldSchema] = {
    val fieldSchemaList: util.List[FieldSchema] = new util.ArrayList[FieldSchema]
    try {
      val dataset: Dataset[Column] = sparkSession.catalog.listColumns(dbName, tableName)
      // String colums[] =   dataset.columns();
      val columsArrys: Array[Column] = dataset.collect
      for (column <- columsArrys) {
        val columnName: String = column.name
        if (!partitionFields.contains(columnName)) fieldSchemaList.add(new FieldSchema(columnName, column.dataType, column.description))
      }
    } catch {
      case e: AnalysisException =>
        log.error(e.getMessage, e)
        throw new RuntimeException(e)
    }
    fieldSchemaList
  }


  /**
    * getHdfsPath 获取hdfs上Hive Partition路径
    *
    * @param dbName    数据库名
    * @param tableName 表名
    */
  def getHdfsPath(sparkSession: SparkSession, dbName: String, tableName: String, partition: String): String = {
    val sql: String = String.format("DESCRIBE FORMATTED `%s`.`%s` %s", dbName, tableName, partition)
    log.info("describe extended sql : " + sql)
    var hdfsPath: String = StringUtils.EMPTY
    val rows: Array[Row] = sparkSession.sql(sql).collect
    log.info("rows  size  : " + rows.length) //  32
    try {
      var start: Boolean = false
      for (row <- rows) {
        val colNum: Int = row.length
        log.info("row   colNum size  : " + colNum)
        var i: Int = 0
        for (i <- 0 to colNum - 1) {
          if (!start) {
            val value: String = StringUtils.trimToEmpty(row.getString(i))
            start = StringUtils.isNotBlank(value) && PATTERN.matcher(value).matches
            log.info("value   : " + value + "--------start-----> " + start)
          }
        }
        if (start) {
          var i: Int = 0
          for (i <- 0 to colNum - 1) {
            if (StringUtils.containsIgnoreCase(row.getString(i), LOCATION_STR)) {
              val stringBuilder: StringBuilder = new StringBuilder
              var i1: Int = i
              for (i1 <- i to colNum - 1) {
                stringBuilder.append(StringUtils.trimToEmpty(row.getString(i1)))
              }
              if (stringBuilder.length > 0) {
                val locationStr: String = StringUtils.remove(stringBuilder.toString, ' ')
                log.info("locationStr----------------------------------->" + locationStr)
                // spark2.3和hive全版本对应,可能有Location不带冒号及location:
                hdfsPath = StringUtils.substringAfter(locationStr, LOCATION_STR.substring(1))
                hdfsPath = StringUtils.substringBefore(hdfsPath, ",")
                if (hdfsPath.startsWith(":")) {
                  hdfsPath = hdfsPath.substring(1)
                }
                return hdfsPath
              }
            }
          }
        }
      }
    } catch {
      case e: Exception => log.error(e.getMessage, e)
        throw new RuntimeException(e)
    }
    log.info(hdfsPath)
    hdfsPath.trim
  }

}
