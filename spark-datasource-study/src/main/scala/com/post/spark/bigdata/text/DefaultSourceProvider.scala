package com.post.spark.bigdata.text


import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.execution.datasources.text2.TextDatasourceRelation
import org.apache.spark.sql.sources.{BaseRelation, RelationProvider, SchemaRelationProvider}
import org.apache.spark.sql.types.StructType

/**
  * 当我们随意定义名称时，出现如下错误，所以，
  * 我们必须使用DefaultSource，这个代码中，我们只是将path、sqlContext、schema向下传递
  *
  */
class DefaultSourceProvider extends RelationProvider with SchemaRelationProvider{
  //
  override def createRelation(sqlContext: SQLContext, parameters: Map[String, String], schema: StructType): BaseRelation = {
    //从参数重获取path
    val path = parameters.get("path")
    println("------1---从参数重获取path-------" + path)
    path match {
      case Some(p) => new TextDatasourceRelation(sqlContext, p, schema)
      case _ => throw new IllegalArgumentException("Path is required")
    }
  }
  override def createRelation(sqlContext: SQLContext, parameters: Map[String, String]): BaseRelation = {
    //从参数重获取path
    val path = parameters.get("path")
    println("------2---从参数重获取path-------" + path)
    path match {
      case Some(p) => new TextDatasourceRelation(sqlContext, p)
    }
  }
}