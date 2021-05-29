package org.apache.spark.examples.udf.demo

object Test_ClassCreateUtils {

  def main(args: Array[String]): Unit = {

    val infos = ClassCreateUtils(
      """
        |def apply(name:String)=name.toUpperCase
      """.stripMargin
    )
    println(infos.defaultMethod.invoke(infos.instance, "dounine 本猪会一点点 spark"))
    //    # 输出结果不用猜也知道是
    //    DOUNINE 本猪会一点点 SPARK
    //    # 也可以手动指定方法
    println(infos.methods("apply").invoke(infos.instance, "dounine 本猪会一点点 spark"))

  }
}
