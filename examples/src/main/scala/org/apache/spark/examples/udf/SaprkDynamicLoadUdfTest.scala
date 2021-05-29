package org.apache.spark.examples.udf

import java.io._
import java.net.{HttpURLConnection, URL, URLClassLoader}

import com.alibaba.fastjson.{JSON, JSONObject}
import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.api.java.UDF1
import org.apache.spark.sql.catalyst.JavaTypeInference

import scala.collection.mutable
import scala.util.Try

/**
  * 参考资料: https://www.jianshu.com/p/4882c9f5472e
  *           https://www.jianshu.com/p/efff975b714f
  */

object SaprkDynamicLoadUdfTest {
  var logger = Logger.getLogger(SaprkDynamicLoadUdfTest.getClass)

  Logger.getLogger("org").setLevel(Level.INFO)
  Logger.getLogger("akka").setLevel(Level.INFO)

  /**
    * 其中加载jar、识别输入、输出、方法体、构造UDF1使用如下代码
    *
    * @param args
    */
  def main(args: Array[String]): Unit = {

    val url = new URL("file:xxx")
    val urls = Array(url)
    var myClassLoader: URLClassLoader = new URLClassLoader(urls, scala.reflect.runtime.universe.getClass.getClassLoader)

//    var IType = method.getParameterTypes.map(JavaTypeInference.inferDataType).map(_._1).head
//    val iputType = method.getParameterTypes.head.getName
//    var Rtype = JavaTypeInference.inferDataType(method.getReturnType)._1

    var className = ""
    var methodName = ""
    val zz = myClassLoader.loadClass(className)
    case class TempUDF() extends UDF1[Object, Any] with Serializable {
      override def call(v1: Object): Any = {
        val m1 = zz.getDeclaredMethods.filter(_.getName.equals(methodName)).head
        m1.invoke(zz.newInstance(), v1)
      }
    }


  }

}
