package demo

import org.apache.commons.lang3.StringUtils
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.Path
import org.apache.hadoop.security.UserGroupInformation
import org.apache.spark.sql.SparkSession
import org.apache.spark.{SPARK_VERSION, SparkConf}
import util.{KerberosUtils, PropertiesUtil}

/**
  * 统计字符出现次数
  * 测试本地登录Kerbos认证，没问题
  */
object KerberosSparkSqlTest {
  val propertiesUtil: PropertiesUtil = PropertiesUtil.getInstance
  var userPrincipal = propertiesUtil.read("file_config.properties", "kerberos.user.principal.name")
  var userKeyTableFile = propertiesUtil.read("file_config.properties", "kerberos.user.keytab")
  var krbFile = propertiesUtil.read("file_config.properties", "java.security.krb5.conf")

  def main(args: Array[String]) {

    System.setProperty("java.security.krb5.conf", krbFile)
    KerberosUtils.initKerberos(userPrincipal, userKeyTableFile)


    var map = Map[String, String]()
    map += ("inputTableName" -> "test.t1")
    map += ("username" -> userPrincipal)
    map += ("userPrincipal" -> userPrincipal)
    map += ("userKeyTableFile" -> userKeyTableFile)
    map += ("userKrbFile" -> krbFile)

    val sparkConf = getSparkConf(new SparkConf, 0, map)

    sparkConf.set("security.protocol", "SASL_PLAINTEXT")
    sparkConf.set("sasl.mechanism", "GSSAPI")
    System.setProperty("user.name", userPrincipal)
    val builder = SparkSession.builder().config(sparkConf)
    val spark = builder.master("local[2]").appName("SparkTest").enableHiveSupport().getOrCreate()

    spark.sql("show databases").show()
    spark.sql("use default")
    spark.sql("show tables").show()

    spark.sql("use test")
    spark.sql("show tables").show()
    val sql = "select count(1) from test.t1 limit 10"
    spark.sql(sql).show()

    spark.stop()
  }


  def initKerberos(): Unit = {
    //kerberos权限认证
    val path = this.getClass.getClassLoader.getResource("").getPath
    println(path)

    System.setProperty("java.security.krb5.realm", krbFile)
    System.setProperty("java.security.krb5.kdc", krbFile)
    System.setProperty("java.security.krb5.realm", "username@hadoop.CN")
    System.setProperty("java.security.krb5.conf", krbFile)

    val conf = new Configuration()
    System.setProperty("java.security.krb5.conf", path + "krb5.conf")
    System.setProperty("java.security.krb5.realm", path + "krb5.conf")
    System.setProperty("java.security.krb5.kdc", path + "krb5.conf")
    //
    conf.addResource(new Path(path + "CDH583/hbase-site.xml"))
    conf.addResource(new Path(path + "CDH583/hdfs-site.xml"))
    conf.addResource(new Path(path + "CDH583/hive-site.xml"))
    conf.set("hadoop.security.authentication", "Kerberos")
    conf.set("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem")

    UserGroupInformation.setConfiguration(conf)
    UserGroupInformation.loginUserFromKeytab(userPrincipal, userKeyTableFile)
    println("login user: " + UserGroupInformation.getLoginUser())
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
