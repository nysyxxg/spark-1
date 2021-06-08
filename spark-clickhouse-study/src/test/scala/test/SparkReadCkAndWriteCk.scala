package test

import jodd.util.PropertiesUtil
import org.apache.spark.SparkConf
import org.apache.spark.sql.{SaveMode, SparkSession}

/**
  * 测试通过
  * @author xxg
  *         主要两种方式的操作：Http 和  TCP
  */
object SparkReadCkAndWriteCk {

  val fileName = "D:\\Spark_Ws\\spark-apache\\examples\\src\\main\\resources\\application.properties"
  val props = PropertiesUtil.createFromFile(fileName)
  val ckDriver = props.getProperty("ck.driver") //"com.github.housepower.jdbc.ClickHouseDriver"
  val ckUrl = props.getProperty("ck.url")
  val ckUserName = props.getProperty("ck.username")
  val ckPassword = props.getProperty("ck.password")
  val table = "jdbc_test_table"

  def main(args: Array[String]): Unit = {
    val sparkConf: SparkConf = new SparkConf().setAppName("SparkReadCkAndWriteCk").setMaster("local[1]")
    val spark: SparkSession = SparkSession.builder().config(sparkConf).getOrCreate()

//    insert(spark)
    select(spark)

    spark.stop()
  }

  /*
  读取 ck
   */
  def select(spark: SparkSession): Unit = {

    val dataDF = spark.read
      .format("jdbc")
      .option("driver", ckDriver)
      .option("url", ckUrl)
      .option("user", ckUserName)
      .option("password", ckPassword)
      .option("dbtable", table)
      .load()

    dataDF.show()

    dataDF.sqlContext.sql("select * from " + table  + " where age>100").show()

  }

  /*
     写入
      */
  def insert(spark: SparkSession): Unit = {
    //clickhouse客户端配置
    val pro = new java.util.Properties
    pro.put("driver", ckDriver)
    pro.put("user", ckUserName)
    pro.put("password", ckPassword)

    import spark.implicits._
    //创建数据
    val df = Seq(Person1("xxx", 19, "hadoop"), Person1("yyy", 29, "JAVA"), Person1("ZZZ", 39, "SPARK")).toDS
    //写入clickhouse
    df.write
      .mode(SaveMode.Append)
      .option("batchsize", "20000")
      .option("isolationLevel", "NONE")
      .option("numPartitions", "2")
      .jdbc(ckUrl, table, pro)
  }

}

case class Person1(name: String, age: Long,job:String)
