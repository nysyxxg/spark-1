package org.apache.spark.examples.udf

import java.net.URL

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.junit.jupiter.api.Test

import scala.util.Try


class TestLoadUdf {

  @Test
  def testStr2VecJson(): Unit = {

    //    System.setProperty("hadoop.home.dir", "D:\\winutils")
    val conf = new SparkConf().setAppName("test").setMaster("local[2]")
    //.set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
    //    val sc = new SparkContext(conf)
    val spark = SparkSession.builder().config(conf).getOrCreate()
    import spark.implicits._
    val data = Array("1", "2")
    val rdd = spark.sparkContext.parallelize(data)
    val df = rdd.toDF("str")
    df.show()


    //这里套用工具类
    val url = new URL("file:F:/ad_codes/data_flow_test/target/data_flow_test-1.0-SNAPSHOT.jar")
    val urls = Array(url)
    ScalaGenerateFunctions(urls)
    // 类名
    val className = "org.apache.spark.examples.udf.method.MyMethod"
    // 方法名称组成的数组
    val methodArray = Array("appendStr")

    val jarName = "autotask-2.0-SNAPSHOT.jar"

    methodArray.foreach {
      methodName =>
        val (fun, inputType, returnType) = ScalaGenerateFunctions.genetateFunction(methodName, className, jarName)
        val inputTypes = Try(List(inputType)).toOption

        //def builder(e: Seq[Expression]) = ScalaUDF(fun, returnType, e, inputTypes.getOrElse(Nil), Some(methodName))
        spark.udf.register(methodName, fun, returnType)
      //        def builder(e: Seq[Expression]) = ScalaUDF(function = fun, dataType = returnType, children = e, Seq(true), inputTypes = inputTypes.getOrElse(Nil), udfName = Some(methodName))
      //        spark.sessionState.functionRegistry.registerFunction(new FunctionIdentifier(methodName), builder)
    }
    df.createTempView("strDF")
    df.show()
    var execSql = "select str2VecJson(str) from strDF"
    spark.sql(execSql).show()
  }
}
  

