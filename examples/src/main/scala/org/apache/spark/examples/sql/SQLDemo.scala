package org.apache.spark.examples.sql;

import org.apache.spark.sql.{SQLContext, SparkSession}
import org.apache.spark.{SparkConf, SparkContext}

//case class一定要放到外面
case class  MyPerson(id:Long, name:String, age:Int)

// xxg 通过反射推断Schema
object SQLDemo {

  def main(args: Array[String]): Unit = {
    //本地运行
    val conf = new SparkConf().setAppName("SQLDemo").setMaster("local[2]")
    //val conf = new SparkConf().setAppName("SQLDemo") 要打包到spark集群上运行则不需要后面的setMaster("local[2]")

    //SQLContext要依赖SparkContext
    val sc = new SparkContext(conf)
    //创建SQLContext spark1.6.1以下的写法
    //val sqlContext = new SQLContext(sc)

    //spark2.0 以上的写法
    val sqlContext = SparkSession.builder().config(conf).getOrCreate()

    //提交到spark集群上运行，需要设置用户，否则无权限执行，本地运行则无需
    //System.setProperty("user.name", "bigdata")

    //集群hdfs路径  hdfs://node-1.itcast.cn:9000/person.txt
    //下面由于是本地运行，所以采用本地路径
    //将RDD和case class关联
    val personRdd = sc.textFile("person.txt").map({line =>
      val fields = line.split(",")
      MyPerson(fields(0).toLong, fields(1), fields(2).toInt)
    })

    //导入隐式转换，如果不导入无法将RDD转换成DataFrame
    //将RDD转换成DataFrame
    import sqlContext.implicits._
    val personDf = personRdd.toDF()

    //采用SQL编写风格 注册表
    //personDf.registerTempTable("person") spark 1.6.1以下的写法
    personDf.createOrReplaceTempView("person")

    sqlContext.sql("select * from person where age >= 20 order by age desc limit 2").show()
  }
}
