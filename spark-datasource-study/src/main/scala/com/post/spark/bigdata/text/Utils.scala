package com.post.spark.bigdata.text

import org.apache.spark.sql.types.{DataType, LongType, StringType}

object Utils {

  def castTo(value:String, dataType:DataType) ={
    // 对数据进行转换
    dataType match {
      case _:LongType => value.toLong
      case _:StringType => value
    }
  }
}
