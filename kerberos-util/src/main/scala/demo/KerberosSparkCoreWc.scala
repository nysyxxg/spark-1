package demo

import org.apache.commons.lang3.StringUtils
import org.apache.hadoop.conf.Configuration
import org.apache.spark.{SPARK_VERSION, SparkConf, SparkContext}
import util.{KerberosUtils, PropertiesUtil}

object KerberosSparkCoreWc {

  val propertiesUtil: PropertiesUtil = PropertiesUtil.getInstance
  var userPrincipal = propertiesUtil.read("file_config.properties", "kerberos.user.principal.name")
  var userKeyTableFile = propertiesUtil.read("file_config.properties", "kerberos.user.keytab")
  var krbFile = propertiesUtil.read("file_config.properties", "java.security.krb5.conf")

  def main(args: Array[String]): Unit = {

    System.setProperty("java.security.krb5.conf", krbFile)
    var  configuration = new Configuration();
    configuration.addResource("core-site.xml")
    configuration.addResource("hbase-site.xml")
    configuration.addResource("hdfs-site.xml")
    configuration.addResource("yarn-site.xml")

    KerberosUtils.initKerberos(userPrincipal, userKeyTableFile,configuration)
    System.setProperty("user.name", userPrincipal)

    var map = Map[String, String]()
    map += ("inputTableName" -> "test.t1")
    map += ("username" -> userPrincipal)
    map += ("userPrincipal" -> userPrincipal)
    map += ("userKeyTableFile" -> userKeyTableFile)
    map += ("userKrbFile" -> krbFile)


    val sparkConf = getSparkConf(new SparkConf,0,map).setAppName("test")
    sparkConf.set("security.protocol", "SASL_PLAINTEXT")
    sparkConf.set("sasl.mechanism", "GSSAPI")

    val sc = new SparkContext(sparkConf)
    val line = sc.textFile("/user/demo/hive_col/202011181146/part-00000")
    line.flatMap(_.split(" ")).map((_, 1)).reduceByKey(_ + _).collect().foreach(println)
    sc.stop()


  }

  def getSparkConf(sparkConf: SparkConf = new SparkConf(), size: Long = 0L, map: Map[String, String]): SparkConf = {
    sparkConf.set("spark.rdd.compress", "true")
    sparkConf.set("spark.sql.parquet.compression.codec", "snappy")
    sparkConf.set("spark.hbase.obtainToken.enabled", "true")
    sparkConf.set("spark.yarn.security.credentials.hbase.enabled", "true")
    sparkConf.set("spark.hadoop.parquet.compression", "snappy")

    val OS = System.getProperty("os.name").toLowerCase
    if (OS.contains("windows")) {
      sparkConf.setMaster("local[1]")
    }
    if (!map.isEmpty) {
      println("kerberosConf enabled.")
      var userName = map.getOrElse("username", "")
      if (!StringUtils.isEmpty(userName)) {
        sparkConf.set("spark.yarn.username", userName)
      }
      var userPrincipal = map.getOrElse("userPrincipal", "")
      var userKeyTableFile = map.getOrElse("userKeyTableFile", "")
      var krbFile = map.getOrElse("userKrbFile", "")

      if (!userPrincipal.isEmpty && !userKeyTableFile.isEmpty && !krbFile.isEmpty) {
        sparkConf.set("spark.yarn.principal", userPrincipal)
        sparkConf.set("spark.yarn.keytab", userKeyTableFile)
        sparkConf.set("spark.yarn.krbFile", krbFile)
        System.setProperty("java.security.krb5.conf", krbFile)
      }
    } else {
      println("kerberosConf disable.")
    }

    // 自动调参模块(通过传参数size和命令行参数启动,命令行里传参数的话本模块将不起作用)
    if (size > 0L && !sparkConf.contains("spark.executor.memory")) {
      println(s"运行自动调参, 估测输入数据大小为 ${size}Byte.")
      val mem = "16384".toDouble
      val minExecutors = "2".toInt
      val reserveTime = math.max("3".toDouble, 1.0D)
      val executorInstances = math.max(minExecutors, math.ceil((size.toDouble / 1024.0D / 1024.0D * reserveTime) / mem).toInt)
      sparkConf.set("spark.executor.instances", executorInstances.toString)
      sparkConf.set("spark.executor.memory", s"${mem.toInt}m")
      sparkConf.set("spark.executor.cores", "2")
      if (StringUtils.substringBeforeLast(SPARK_VERSION, ".").toDouble <= 2.2) {
        sparkConf.set("spark.yarn.executor.memoryOverhead", math.ceil(mem.toDouble / 2).toInt.toString)
      } else {
        sparkConf.set("spark.executor.memoryOverhead", math.ceil(mem.toDouble / 2).toInt.toString)
      }
    } else {
      if (size <= 0L) {
        println(s"$size 小于等于 0, 不运行自动调参.")
      }
      if (sparkConf.contains("spark.executor.instances")) {
        println(s"默认参数包含  EXECUTOR INSTANCE , 不运行自动调参.")
      }
    }
    sparkConf
  }


}
