package org.apache.spark.examples.udf.method


import com.alibaba.fastjson.JSON

import scala.collection.JavaConverters._
import scala.collection.mutable

/**
  * @author liangwt
  * @create 2020/11/16
  * @Description :
  * @since 1.0.0
  */
class MyMethod {

  def toLowerCase(value: String) = {
    value.toLowerCase
  }

  def appendStr(value: String): String = {
    //用户自定义处理方法
    value + "--->xuxiaoguang"
  }

  def method2(value: Int): String = {
    (value + 100).toString
  }

  def testMap(value: Int): scala.collection.Map[String, String] = {
    scala.collection.Map("1" -> "1")
  }

  def testJMap(value: Int): java.util.Map[String, String] = {
    scala.collection.Map("1" -> "1").asJava
  }

  def testMap2(value: Int): Map[String, String] = {
    Map("1" -> "1")
  }

  def testSet(value: Int) = {
    val set = mutable.Set("1")
    set.asJava
  }

  def testSeq(value: Int) = {
    Seq("1")
  }

  def inputSeq(seq: Seq[Int]): String = {
    seq.toList.mkString(",")
  }

  def inputMap(map: Map[String, Integer]): String = {
    "1"
  }

//  def   map2Json(map: Map[String, Integer]): String = {
//    JSON.toJSONString(map).toString
//  }

}
