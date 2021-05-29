package org.apache.spark.examples.udf.demo

import org.apache.spark.sql.{Row, SparkSession}
import org.apache.spark.sql.catalyst.FunctionIdentifier
import org.apache.spark.sql.catalyst.expressions.{Expression, ScalaUDF}
import org.apache.spark.sql.expressions.UserDefinedFunction
import org.apache.spark.sql.internal.SessionState
import org.apache.spark.sql.types.{StringType, StructField, StructType}

import scala.util.Try

/**
  * 参考资料： https://www.jianshu.com/p/56ff9549d19a
  *
  */
object Test_UDFFunction {

  def main(args: Array[String]): Unit = {
    val spark = SparkSession
      .builder()
      .appName("test")
      .master("local[*]")
      .getOrCreate()

    val name = "hello"
    val (fun, argumentTypes, returnType) = ScalaGenerateFuns(
      """
        |def apply(name:String) = name + " => hi"
        |
        |""".stripMargin
    )

    //    val (fun, argumentTypes, returnType) = ScalaSourceUDF(
    //      """
    //        |def apply(name:String)=name+" => hi"
    //        |""".stripMargin)


    val inputTypes = Try(argumentTypes.toList).toOption

    def builder(e: Seq[Expression]) = ScalaUDF(fun, returnType, e, inputTypes.getOrElse(Nil), Some(name))
//            spark.sessionState.functionRegistry.registerFunction(new FunctionIdentifier(name), builder)
    //    spark.udf.register(name, fun, returnType)

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

    var sql = "select hello(name) from " + tmpTable
    spark.sql(sql).show(false)

  }
}
