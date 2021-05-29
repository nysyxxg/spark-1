package org.apache.spark.examples.udf

import java.io._
import java.net.{HttpURLConnection, URL, URLClassLoader}

import org.apache.spark.sql.types._
import org.apache.spark.sql.catalyst.{JavaTypeInference, ScalaReflection}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._
import scala.collection.JavaConversions._
import scala.collection.mutable
import java.lang.reflect.{Method, ParameterizedType}

import com.alibaba.fastjson.{JSON, JSONObject}
import org.apache.spark.examples.udf.SaprkDynamicLoadUdfTest.logger
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.api.java.UDF1

import scala.util.Try

/**
  * https://www.jianshu.com/p/efff975b714f
  *
  * @author liangwt
  * @create 2020/11/16
  * @since 1.0.0
  * @Description :
  *
  */
case class ClassInfo(clazz: Class[_], instance: Any, function: UDF1[Object, Any]) extends Serializable

object ScalaGenerateFunctions {

  val logger: Logger = LoggerFactory.getLogger(getClass)

  private var myClassLoader: URLClassLoader = null

  var HdfsPrefix = "hdfs://nameservice/"

  def apply(urls: Array[URL]): Unit = {
    //scala.reflect.runtime.universe.getClass.getClassLoader
    //Thread.currentThread().getContextClassLoader()
    myClassLoader = new URLClassLoader(urls, scala.reflect.runtime.universe.getClass.getClassLoader)
  }

  @transient private lazy val classMap = new mutable.HashMap[(String, String, String), ClassInfo]()

  def genetateFunction(methodName: String, className: String, jarName: String) = {

    if (!classMap.contains((className, methodName, jarName))) {
      val zz = myClassLoader.loadClass(className)
      case class TempUDF() extends UDF1[Object, Any] with Serializable {
        override def call(v1: Object): Any = {
          val m1 = zz.getDeclaredMethods.filter(_.getName.equals(methodName)).head
          m1.invoke(zz.newInstance(), v1)
        }
      }

      logger.info("*************************************************************************")
      logger.info(s"动态编译$jarName $className $methodName 成功")
      logger.info("*************************************************************************")
      classMap.put((className, methodName, jarName), ClassInfo(zz, zz.newInstance(), new TempUDF()))
    }

    val (iType, rType) = getDataType(methodName, className, jarName)
    (classMap((className, methodName, jarName)).function, iType, rType)
  }

  private def getDataType(methodName: String, className: String, jarName: String) = {
    val method = classMap((className, methodName, jarName)).clazz.getMethods.filter(_.getName.equals(methodName)).head
    var IType = method.getParameterTypes.map(JavaTypeInference.inferDataType).map(_._1).head
    val iputType = method.getParameterTypes.head.getName
    IType = iputType match {
      //todo
      //这里有点问题，获取不到注册函数中，Seq中的类型，后续有方法可以识别的话，可替换，目前只支持基本类型的转换
      case "scala.collection.Seq" => {
        val inputGenericType = method.getGenericParameterTypes.head
        val contentType = inputGenericType.asInstanceOf[ParameterizedType].getActualTypeArguments.head.getTypeName
        ArrayType(TypeUtil(contentType))
      }

      case "scala.collection.Map" | "scala.collection.immutable.Map" | "scala.collection.mutable.Map" | "scala.Map" | "java.util.Map" => {
        val inputGenericType = method.getGenericParameterTypes.head.getTypeName
        val keyType = inputGenericType.substring(inputGenericType.indexOf("<"))
        val valueType = inputGenericType.substring(inputGenericType.indexOf(","))
        MapType(TypeUtil(keyType), TypeUtil(valueType))
      }
      case _ => IType
    }

    var Rtype = JavaTypeInference.inferDataType(method.getReturnType)._1

    Rtype = method.getReturnType.getName match {
      //spark不支支持java Map,这里支持
      case "scala.collection.Map" | "scala.collection.immutable.Map" | "scala.collection.mutable.Map" | "scala.Map" | "java.util.Map" => {
        val rGenericType = method.getGenericReturnType.getTypeName
        val keyType = rGenericType.substring(rGenericType.indexOf("<"))
        val valueType = rGenericType.substring(rGenericType.indexOf(","))
        MapType(TypeUtil(keyType), TypeUtil(valueType))
      }

      case "scala.collection.Seq" | "scala.Seq" => {
        val rGenericType = method.getGenericReturnType.getTypeName
        ArrayType(TypeUtil(rGenericType.substring(rGenericType.indexOf("<") + 1, rGenericType.indexOf(">"))))
      }
      case _ => Rtype
      //todo
      //后面还有seq，参考 ScalaReflection 支持的类型进行支持，因为在 JavaTypeInference 中无法通过
      //private val mapType = TypeToken.of(classOf[JMap[_, _]]) 来构造DataType，这里还需要再细致得到看下代码，有优化空间
    }
    (IType, Rtype)
  }

  private def TypeUtil(str: String): DataType = {
    str match {
      case f if (f.contains("String")) => StringType
      case f if (f.contains("Double")) => DoubleType
      case f if (f.contains("Float")) => FloatType
      case f if (f.contains("Integer") || f.contains("Int")) => IntegerType
      case f if (f.contains("Char")) => FloatType
      case f if (f.contains("Boolean")) => BooleanType
      case f if (f.contains("Long")) => LongType
      case f if (f.contains("Float")) => FloatType
      case f if (f.contains("Float")) => FloatType
      case f if (f.contains("Object")) => StringType
      case _ => throw new Exception(s"unSupport Type $str")
    }
  }

  /**
    * 根据用户，通过http请求获取对应的udf
    *
    * @param user
    * @param spark
    */
  def LoadUserDefineUDF(user: String, spark: SparkSession): Unit = {
    val brainUrl: String = PropertiesLoader.getProperties("database.properties").getProperty("brain.url")
    val brainPrefix = brainUrl.substring(0, brainUrl.indexOf("feature-panel/online-feature") - 1)
    val udfURL = s"$brainPrefix/udfWarehouse/findInfoByUserId?userId=$user"
    val simpleHttp = new SimpleHttp
    val result = simpleHttp.fetchResponseText(udfURL)
    logger.info("***********************************************************")
    logger.info(s"result:$result")
    try {
      val resultJson = JSON.parseObject(result)
      val flag = resultJson.getInteger("code").toInt
      flag match {
        case 0 => LoadUDF(resultJson, spark)
        case _ => logger.error(s"加载用户自定义离线特征处理udf失败！原因：${resultJson.getString("msg")}")
      }
    } catch {
      case e: Exception =>
        logger.error(s"加载用户自定义离线特征处理udf失败！原因：服务器异常！" + e.getMessage, e)
    }
  }


  def LoadUDF(jsonObj: JSONObject, spark: SparkSession): Unit = {
    val udfArray = jsonObj.getJSONObject("data").getJSONArray("data")
    var array = mutable.ArrayBuilder.make[URL]()
    logger.info("************************************************************")
    val methodMap = new mutable.HashMap[String, (String, String, String)]()
    for (i <- 0 to udfArray.length - 1) {
      val udfJson = udfArray.getJSONObject(i)
      val udfName = udfJson.getString("udfName")
      val downLoadJarUrl = udfJson.getString("downLoadJarUrl")
      val entryClass = udfJson.getString("entryClass")
      val jarName = udfJson.getString("jarName") + ".jar"
      val functionName = udfJson.getString("functionName")
      try {
        downLoadJar(downLoadJarUrl, jarName)
        spark.sparkContext.addJar(HdfsPrefix + jarName)
        val url2 = new URL(s"file:./${jarName}")
        logger.info(s"*********加载udf $udfName 成功**********")
        methodMap.put(udfName, (functionName, entryClass, jarName))
        array += url2
      } catch {
        case e: Exception =>
          logger.error(s"$jarName $functionName $entryClass Exception!!!", e.getMessage)
      }
    }
    ScalaGenerateFunctions(array.result())
    // 开始遍历每个udf函数，进行注册
    methodMap.foreach {
      map =>
        try {
          val (fun, inputType, returnType) = ScalaGenerateFunctions.genetateFunction(map._2._1, map._2._2, map._2._3)
          val inputTypes = Try(List(inputType)).toOption
          spark.udf.register(map._1, fun, returnType)
          logger.info(s"*********spark 注册udf ${map._1} 成功**********")
        } catch {
          case e: Exception =>
            logger.error(s"*********spark 注册udf ${map._1} 失败！！", e.getMessage)
        }
    }
  }

  /**
    * 通过http下载jar
    * @param url
    * @param jarName
    */
  def downLoadJar(url: String, jarName: String): Unit = {
    logger.info("*******************************************")
    logger.info(s"****************url:$url**********************")
    //val path = "E:\\temp\\"
    val path = "./"
    val file = new File(path)
    //val jars = Array("test.jar", "test2.jar")
    if (!file.exists()) {
      //如果文件夹不存在，则创建新的的文件夹
      file.mkdirs()
    }
    var fileOut: FileOutputStream = null
    var conn: HttpURLConnection = null
    var inputStream: InputStream = null
    try {
      val httpUrl = new URL(url)
      conn = httpUrl.openConnection().asInstanceOf[HttpURLConnection]
      conn.setRequestMethod("GET")
      conn.setDoInput(true)
      conn.setDoOutput(true)
      // post方式不能使用缓存
      conn.setUseCaches(false)
      //连接指定的资源
      conn.connect()
      //获取网络输入流
      inputStream = conn.getInputStream();
      val bis = new BufferedInputStream(inputStream)
      fileOut = new FileOutputStream(path + jarName)
      val bos = new BufferedOutputStream(fileOut)
      val buf = new Array[Byte](4096)
      var length = bis.read(buf);
      //保存文件
      while (length != -1) {
        bos.write(buf, 0, length);
        length = bis.read(buf);
      }
      //关闭流
      bos.close()
      bis.close()
      conn.disconnect();
    } catch {
      case e: Exception =>
        logger.error(s"下载jar：$jarName 出错" + e.getMessage, e)
    }
  }

}