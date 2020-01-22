package org.apache.spark.examples.sql

import org.apache.spark.SparkContext
import org.apache.spark.sql.SparkSession

// 定义case class
case class Person(name: String, zip: Long)
case class Person2(id:String, name: String, age: Long)
case class Data(a: Int,b: String)
case class Student(name: String, age: Long, major: String)
case class University(name: String, numStudents: Long, yearFounded: Long)
//    创建Major的分类
case class Major(shortName: String, fullName: String)
// xxg
object DataSetTest {

  def main(args: Array[String]): Unit = {

    val spark = SparkSession
      .builder()
      .appName("Spark Hive Example")
      .enableHiveSupport()
      .getOrCreate()
    val sc = new SparkContext()
    import org.apache.spark.sql.functions._
    import spark.implicits._

    val df = spark.read.json(sc.parallelize("""{"zip": 94709, "name": "Michael"}""" :: Nil))
    df.as[Person].collect()
    df.as[Person].show()

    val ds1 = Seq(Data(1, "one"), Data(2, "two")).toDS()
    ds1.collect()
    ds1.show()


    // 创建DataSet
    val ds = spark.read.text("hdfs://node-1.itcast.cn:9000/wc").as[String]
    val result = ds.flatMap(_.split(" "))
      .filter(_ != "")
      .toDF()
      .groupBy($"value")
      .agg(count("*") as "numOccurances")
      .orderBy($"numOccurances" desc)
      .show()

    val wordCount = ds.flatMap(_.split(" ")).filter(_ != "").groupBy().count().show()

    // 创建DataSet
    // val schools = spark.read.json("hdfs://hadoop1:9000/schools.json").as[University]
    // 操作DataSet
    // schools.map(sc => s"${sc.name} is ${2015 - sc.yearFounded} years old").show

    // JSON -> DataFrame
    val df2 = spark.read.json("hdfs://hadoop1:9000/person.json")
    // 使用DataFrame操作
    df2.where($"age" >= 20).show
    df2.where(col("age") >= 20).show
    df2.printSchema

    // DataFrame转DataSet
    //#DataFrame -> Dataset
    val ds2 = df2.as[Person2]
    ds2.filter(_.age >= 20).show

//    DataSet转DataFrame
//    # Dataset -> DataFrame
    val df3 = ds2.toDF

//    DataFrame和DataSet操作对比
    import org.apache.spark.sql.types._

    df.where($"age" > 0)
      .groupBy((($"age" / 10) cast IntegerType) * 10 as "decade")
      .agg(count("*"))
      .orderBy($"decade")
      .show

    ds2.filter(_.age > 0)
     // .groupBy(p => (p.age / 10) * 10)
      .agg(count("name"))
      .toDF()
      .withColumnRenamed("value", "decade")
      .orderBy("decade")
      .show


//    4) 对student.json进行分析
//    把json转DataFrame
    val df4 = spark.read.json("hdfs://hadoop1:9000/student.json")
//    DataFrame转DataSet

    val studentDS = df4.as[Student]
    studentDS.select($"name".as[String], $"age".as[Long]).filter(_._2 > 19).collect()

//    DataSet根据major分组求和
    studentDS.groupBy("major").count().collect()
//    DataSet根据major分组聚合
    import org.apache.spark.sql.functions._
    studentDS.groupBy("major").agg(avg($"age").as[Double]).collect()
    val majors = Seq(Major("CS", "Computer Science"), Major("Math", "Mathematics")).toDS()
//    把studentDS和majors求join
    val joined = studentDS.joinWith(majors, $"major" === $"shortName")
    joined.map(s => (s._1.name, s._2.fullName)).show()
    joined.explain()


  }
}
