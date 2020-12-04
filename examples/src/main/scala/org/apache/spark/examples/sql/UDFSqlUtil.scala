package org.apache.spark.examples.sql

object UDFSqlUtil {

  val password = "1234567890000000000"

  def getLen(str: String, addr: String): Int = {
    str.length + addr.length
  }

  def concatStr(str: String, addr: String): String = {
    str + "<---XXG-->" + addr
  }

  def decryptStr(text: String): String = {
    decryptStr(text, password)
  }

  def decryptStr(text: String, password: String): String = {
    var result = ""
    if (text == null || text.trim.isEmpty || text.equalsIgnoreCase("NULL")) {
      text
    } else {
      result = text.toString.replace("_" + password,"**********" )
    }
    result
  }

  def encryptStr(text: String): String = {
    encryptStr(text, password)
  }

  def encryptStr(text: String, password: String): String = {
    var result = ""
    if (text == null || text.trim.isEmpty || text.equalsIgnoreCase("NULL")) {
      text
    } else {
      result = text.toString + "_" + password
    }
    result
  }


}
