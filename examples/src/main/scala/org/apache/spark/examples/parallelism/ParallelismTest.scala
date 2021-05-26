package org.apache.spark.examples.parallelism


import org.apache.spark.{SparkConf, SparkContext}

/**
  * 多个Spark配置的优先级】
  *
  * 代码中SparkConf的设置(最高的优先级)  >  spark-submit --选项  > spark-defaults.conf配置 > spark-env.sh配置 > 默认值
  *  说明： 1.  如果conf.set("spark.default.parallelism", "4") 设置，后面对应的shuffle算子，会和设置参数保持一致
  *         2.  如果没有设置conf.set("spark.default.parallelism", "4") ，后面对应的shuffle算子，会和父RDD的并并行度保持一致
  */
class ParallelismTest {

  def f1(sc: SparkContext): Unit = {
    val rdd = sc.parallelize(1 to 100, 10)
    println("[原始RDD] rdd.partitions.length=" + rdd.partitions.length)

    val mapRdd = rdd.map(x => (x, x))
    println("[map映射后的RDD] mapRdd.partitions.length=" + mapRdd.partitions.length)

    // 如果没有设置repartition 分区，后面的RDD，都会和父RDD保持一致
    val rePRdd = mapRdd.repartition(20)
    println("[repartition(20)] rePRdd.partitions.length=" + rePRdd.partitions.length)

    val mapRdd2 = rePRdd.map(x => x)
    println("[再map] mapRdd2.partitions.length=" + mapRdd2.partitions.length)

    //如果conf设置了 spark.default.parallelism 这个属性,
    // 那么在groupByKey操作(这里的groupByKey指shuffle操作的算子) 不指定参数时, 会默认到的读取设置的默认并行度参数 : 4
    val groupRdd = mapRdd2.groupByKey()
    println("[groupByKey] groupRdd.partitions.length=" + groupRdd.partitions.length)

    // 如果conf--没有设置了 spark.default.parallelism 这个属性,那么默认并行度的参数和父RDD的一样，是20
  }

}

object ParallelismTest {

  def main(args: Array[String]): Unit = {
    val conf = new SparkConf().setAppName("day35").setMaster("local")
    conf.set("spark.default.parallelism", "4") //设置默认的并行度

    val sc = new SparkContext(conf)
    val t = new ParallelismTest
    t.f1(sc)
    sc.stop()
  }
}


