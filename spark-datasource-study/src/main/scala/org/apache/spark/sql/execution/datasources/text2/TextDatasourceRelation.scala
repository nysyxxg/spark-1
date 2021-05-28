package org.apache.spark.sql.execution.datasources.text2
import  com.post.spark.bigdata.text.Utils
import org.apache.spark.internal.Logging
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.sources._
import org.apache.spark.sql.types.{LongType, StringType, StructField, StructType}
import org.apache.spark.sql.{DataFrame, Row, SQLContext, SaveMode}

import scala.collection.mutable.ListBuffer

class TextDatasourceRelation(override val sqlContext: SQLContext,
                             path: String,
                             userSchema: StructType = null)
  extends BaseRelation
    with TableScan
    with PrunedScan
    with PrunedFilteredScan
    with InsertableRelation
    with Logging {
  /*
  继承BaseRelation和TableScan
  使用filter 继承后不再进入PrunedScan
  with PrunedFilteredScan    override def buildScan(requiredColumns: Array[String], filters: Array[Filter]): RDD[Row]
  with PrunedScan   override def buildScan(requiredColumns: Array[String]): RDD[Row]   列裁剪
  extends BaseRelation   override def schema: StructType  设置schema
  with TableScan   override def buildScan(): RDD[Row]    对数据进行转换
  with InsertableRelation    override def insert(data: DataFrame, overwrite: Boolean):   对数据进行insert操作
  with Logging   日志
   */
  override def schema: StructType = {
    /**
      * 设置schema
      */
    //外部传递，则使用外部参数，没有传递则使用schema
    if (userSchema != null) {
      userSchema
    } else {
      StructType(
        // true代表不为空
        StructField("id", LongType, false) ::
          StructField("name", StringType, false) ::
          StructField("gender", StringType, false) ::
          StructField("salary", LongType, false) ::
          StructField("comm", LongType, false) :: Nil
      )
    }
  }

  override def buildScan(): RDD[Row] = {
    /**
      * 对数据进行类型转换
      */
    //打印日志
    logWarning("this is bigdata..................")
    // wholeTextFiles  (filepath,content)
    val rdd = sqlContext.sparkContext.wholeTextFiles(path).map(x => x._2)
    // 获取所有字段
    val schemaFields = schema.fields
    /**
      * TODO 把fields作用到rdd上
      *
      * 如何根据schema的field的数据类型以及字段顺序整合到rdd
      */
    val rows = rdd.map(fileContext => {
      val lines = fileContext.split("\n")
      val data = lines.map(x => x.split(",").map(x => x.trim).toSeq)
      //println(data.mkString)  WrappedArray(1, zhangsan, 1, 1000, 20000)WrappedArray(2, lisi, 0, 9999, 20000)
      // x.zipWithIndex (1, zhangsan, 1, 1000, 20000) ==> ((1,0),(zhangsan,1),(1,2),(1000,3),(20000,4))
      // equalsIgnoreCase不管大小写
      val genderMap = Map("0" -> "男", "1" -> "女")
      val typeedValue = data.map(x => x.zipWithIndex.map {
        case (value, index) => {
          //字段名
          val colName = schemaFields(index).name
          //将字段转换成指定的数据类型，如果是性别则转换成对应中文
          val valueTo = Utils.castTo(if (colName.equalsIgnoreCase("gender")) {
            //if(value == "0"){
            //  "男"
            //}else if(value == "1"){
            //  "女"
            //}else{
            //  "未知"
            //}
            genderMap.getOrElse(value, "未知")
          } else {
            value
          }
            , schemaFields(index).dataType)
          // 转换后的的数据即带类型的字段
          //root
          //|-- id: long (nullable = false)
          //|-- name: string (nullable = false)
          //|-- gender: string (nullable = false)
          //|-- salary: long (nullable = false)
          //|-- comm: long (nullable = false)
          valueTo
        }
      })
      //
      typeedValue.map(x => Row.fromSeq(x))
    })
    // 所有的行都在一起，转换成一行一行
    rows.flatMap(x => x)
  }

  override def buildScan(requiredColumns: Array[String]): RDD[Row] = {
    /**
      * 对数据进行列裁剪，传进来的列
      */
    logWarning("override def buildScan(requiredColumns: Array[String]): RDD[Row]")
    //    if(requiredColumns.length == 0){
    //      return sqlContext.sparkContext.makeRDD(List())
    //    }
    val rdd = sqlContext.sparkContext.wholeTextFiles(path).map(x => x._2)
    // 获取所有字段
    val schemaFields = schema.fields
    /**
      * TODO 把fields作用到rdd上
      *
      * 如何根据schema的field的数据类型以及字段顺序整合到rdd
      */
    val schemaFieldsName = schemaFields.map(x => x.name)
    val requiredColumnsIndex = new ListBuffer[Int]
    for (column <- requiredColumns) {
      val index = schemaFieldsName.indexOf(column)
      requiredColumnsIndex.append(index)
    }
    val rows = rdd.map(fileContext => {
      val lines = fileContext.split("\n")
      val data = lines.map(x => x.split(",").map(x => x.trim).toSeq)
      val genderMap = Map("0" -> "男", "1" -> "女")
      val typeedValue = data.map(linnes => {
        requiredColumnsIndex.map(index => {
          val colName = schemaFields(index).name
          val value = lines(index)
          val valueTo = Utils.castTo(if (colName.equalsIgnoreCase("gender")) {
            genderMap.getOrElse(value, "未知")
          } else {
            value
          }
            , schemaFields(index).dataType)
          valueTo
        })
      })
      // 将 Seq的数据转换成Row
      typeedValue.map(x => Row.fromSeq(x))
    })
    // 所有的行都在一起，转换成一行一行
    rows.flatMap(x => x)
  }

  override def buildScan(requiredColumns: Array[String], filters: Array[Filter]): RDD[Row] = {
    logWarning("override def buildScan(requiredColumns: Array[String], filters: Array[Filter]): RDD[Row]")
    //    if(requiredColumns.length == 0){
    //      return sqlContext.emptyDataFrame.rdd
    //    }
    val rdd = sqlContext.sparkContext.wholeTextFiles(path).map(x => x._2)
    // 获取所有字段
    val schemaFields = schema.fields
    //    filters.foreach(x => println(x.toString))
    /**
      * TODO 把fields作用到rdd上
      *
      * 如何根据schema的field的数据类型以及字段顺序整合到rdd
      */
    val schemaFieldsName = schemaFields.map(x => x.name)
    val requiredColumnsIndex = new ListBuffer[Int]
    for (column <- requiredColumns) {
      // 提前做好数据筛选
      val index = schemaFieldsName.indexOf(column)
      requiredColumnsIndex.append(index)
    }
    val rows = rdd.map(fileContext => {
      val lines = fileContext.split("\n")
      val data = lines.map(x => x.split(",").map(x => x.trim).toSeq)
      val genderMap = Map("0" -> "男", "1" -> "女")
      val typeedValue = data.map(dataLines => {
        requiredColumnsIndex.map(index => {
          val colName = schemaFields(index).name
          val value = dataLines(index)
          var data = if (colName.equalsIgnoreCase("gender")) {
            genderMap.getOrElse(value, "未知")
          } else {
            value
          }
          val castedValue = Utils.castTo(data, schemaFields(index).dataType)
          castedValue
          //          if (requiredColumns.contains(colName)){
          //            Some(castedValue)
          //          } else {
          //            None
          //          }
        })
      })
      // 将 Seq的数据转换成Row
      //  typeedValue.map(x => Row.fromSeq(x.filter(_.isDefined).map(x => x.get)))
      typeedValue.map(x => Row.fromSeq(x))
    })
    // 所有的行都在一起，转换成一行一行,
    // 没有进行任何filters的操作，但是自动进行了数据筛选
    rows.flatMap(x => x)
  }

  override def insert(data: DataFrame, overwrite: Boolean): Unit = {
    data.write
      .mode(if (overwrite) SaveMode.Overwrite else SaveMode.Append)
      .save(path)
  }
}