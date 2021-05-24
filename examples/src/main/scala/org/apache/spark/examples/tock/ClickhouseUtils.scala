package org.apache.spark.examples.tock

import java.sql.{Date, PreparedStatement, Timestamp}
import java.util.Properties

import com.alibaba.fastjson.JSONObject


object ClickhouseUtils {

  /**
    * Clickhouse 连接信息
    */
  def getCHConf: Properties = {
    val clickhouseConf = new Properties()
    clickhouseConf.put("user", "xxxxx")
    clickhouseConf.put("password", "xxxx")
    clickhouseConf.put("database", "xxx")
    clickhouseConf
  }

  /**
    * 初始化 insert SQL
    */
  def initPrepareSQL(userTableSchema: Seq[((String, String), Int)], tableName: String): String = {
    val prepare = List.fill(userTableSchema.size)("?")
    val fields = userTableSchema.map(_._1._1).mkString(",")
    val sql = String.format(
      "INSERT INTO %s (%s) VALUES (%s)",
      tableName,
      fields,
      prepare.mkString(","))
    sql
  }

  /**
    * 校验是否是数值
    */
  def isIntByRegex(s: String) = {
    val pattern = """^(\d+)$""".r
    s match {
      case pattern(_*) => true
      case _ => false
    }
  }

  /**
    * 插入数据
    */
  def newInsert(json: JSONObject,
                statement: PreparedStatement,
                userTableSchema: Seq[((String, String), Int)]): Unit = {

    userTableSchema.foreach(each => {
      val field = each._1._1
      val fieldType = each._1._2
      val i = each._2

      val value = json.get(field)
      fieldType match {
        case "String" =>
          if (null == value) statement.setString(i + 1, "null")
          else statement.setString(i + 1, json.getString(field))

        case "DateTime" =>
          if (null != value) statement.setTimestamp(i + 1, new Timestamp(value.toString.toLong))
          else statement.setTimestamp(i + 1, new Timestamp(System.currentTimeMillis()))

        case "Date" =>
          if (null != value) statement.setDate(i + 1, new Date(value.toString.toLong))
          else statement.setDate(i + 1, new Date(System.currentTimeMillis()))

        case "Int8" | "UInt8" | "Int16" | "UInt16" | "Int32" =>
          if (null == value) statement.setInt(i + 1, 0)
          else statement.setInt(i + 1, json.getIntValue(field))

        case "UInt32" | "UInt64" | "Int64" =>
          if (null != value && isIntByRegex(value.toString.replace(".", ""))) {
            statement.setLong(i + 1, json.getLongValue(field))
          } else {
            statement.setLong(i + 1, 0)
          }

        case "Float32" =>
          if (null != value && isIntByRegex(value.toString.replace(".", ""))) {
            statement.setFloat(i + 1, json.getFloatValue(field))
          } else {
            statement.setFloat(i + 1, 0)
          }

        case "Float64" =>
          if (null != value && isIntByRegex(value.toString.replace(".", ""))) {
            statement.setDouble(i + 1, json.getFloatValue(field))
          } else {
            statement.setDouble(i + 1, 0)
          }

        // TODO: 暂时不考虑Array类型
        // case arrayPattern(_) =>
        // statement.setArray(i + 1, item.getAs[WrappedArray[AnyRef]](field))
        case _ =>
      }
    })
    statement.addBatch()
  }
}