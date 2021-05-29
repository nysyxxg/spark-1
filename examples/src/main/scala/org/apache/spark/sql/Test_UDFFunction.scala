package org.apache.spark.sql

import org.apache.spark.examples.udf.demo.ScalaGenerateFuns
import org.apache.spark.sql.catalyst.FunctionIdentifier
import org.apache.spark.sql.catalyst.expressions.{Expression, ScalaUDF}
import org.apache.spark.sql.types.{StringType, StructField, StructType}

import scala.util.Try

/**
  * 参考资料： https://www.jianshu.com/p/56ff9549d19a
  * 字段---> 规则1，规则2，规则3
  */
object Test_UDFFunction {

  def main(args: Array[String]): Unit = {

    val spark = SparkSession
      .builder()
      .appName("test")
      .master("local[*]")
      .getOrCreate()


    val (fun, argumentTypes, returnType) = ScalaGenerateFuns(
      """
        |def apply(name:String) = name + " => hi"
        |
        |""".stripMargin
    )
    val methodName = "hello"
    val inputTypes = Try(argumentTypes.toList).toOption
    def builder(e: Seq[Expression]) = ScalaUDF(fun, returnType, e, inputTypes.getOrElse(Nil), Some(methodName))
    spark.sessionState.functionRegistry.registerFunction(methodName, builder)
    //    spark.udf.register(methodName, fun, returnType)


    val (fun2, argumentTypes2, returnType2) = ScalaGenerateFuns(
      """
        |def apply(age:Int)=age+ 100
        |""".stripMargin)
    val inputTypes2 = Try(argumentTypes2.toList).toOption
    val methodName2 = "add"
    def builder2(e: Seq[Expression]) = ScalaUDF(fun2, returnType2, e, inputTypes2.getOrElse(Nil), Some(methodName2))
    spark.sessionState.functionRegistry.registerFunction(methodName2, builder2)


    val rdd = spark
      .sparkContext
      .parallelize(Array(("dounine", "20")))
      .map(x => Row.fromSeq(Array(x._1, x._2)))

    val types = StructType(
      Array(
        StructField("name", StringType),
        StructField("age", StringType)
      )
    )
    var tmpTable = "log"
    spark.createDataFrame(rdd, types).createTempView(tmpTable)

    var sql = "select hello(name) as name , hello(add(add(age))) as agestr from " + tmpTable
    spark.sql(sql).show(false)

  }
}
