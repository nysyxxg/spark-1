package org.apache.spark.examples.streaming

/**
  * 消费者策略，是控制如何创建和配制消费者对象。
  * 或者对kafka上的消息进行如何消费界定，比如t1主题的分区0和1，
  * 或者消费特定分区上的特定消息段。
  * 该类可扩展，自行实现。
  *
  * 1.ConsumerStrategies.Assign
  * 指定固定的分区集合,指定了特别详细的方范围。
  * def Assign[K, V](
  * topicPartitions: Iterable[TopicPartition],
  * kafkaParams: collection.Map[String, Object],
  * offsets: collection.Map[TopicPartition, Long])
  *
  * 2.ConsumerStrategies.Subscribe
  * 允许消费订阅固定的主题集合。
  *
  * 3.ConsumerStrategies.SubscribePattern
  * 使用正则表达式指定感兴趣的主题集合。
  */

import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.SparkConf
import org.apache.spark.examples.streaming.senddata.SendDataToKafka
import org.apache.spark.streaming.kafka010.{ConsumerStrategies, KafkaUtils, LocationStrategies}
import org.apache.spark.streaming.{Seconds, StreamingContext}


/**
  * Created by Administrator on 2018/3/8.
  */
object SparkStreamingKafkaScala {

  def main(args: Array[String]): Unit = {
    val conf = new SparkConf()
    conf.setAppName("kafka")
    //        conf.setMaster("spark://s101:7077")
    conf.setMaster("local[2]")
    val ssc = new StreamingContext(conf, Seconds(5))

    //kafka参数
    val kafkaParams = Map[String, Object](
      "bootstrap.servers" -> "172.21.1.167:9091,172.21.1.167:9092,172.21.1.167:9093",
      "key.deserializer" -> classOf[StringDeserializer],
      "value.deserializer" -> classOf[StringDeserializer],
      "group.id" -> "g1",
      "auto.offset.reset" -> "latest",
      "enable.auto.commit" -> (false: java.lang.Boolean)
    )

    val map = scala.collection.mutable.Map[TopicPartition, String]()
    map.put(new TopicPartition("test1", 0), "s102")
    map.put(new TopicPartition("test1", 1), "s102")
//    map.put(new TopicPartition("t1", 2), "s102")
//    map.put(new TopicPartition("t1", 3), "s102")
    val locStra = LocationStrategies.PreferFixed(map);
    val consitStra = LocationStrategies.PreferConsistent
    val brokersStra = LocationStrategies.PreferBrokers

    val topics = Array("t1")
    //主题分区集合
    val tps = scala.collection.mutable.ArrayBuffer[TopicPartition]()
    tps.+=(new TopicPartition("test1", 0)) // 指定topic的分区
    //    tps.+=(new TopicPartition("t2", 1))
    //    tps.+=(new TopicPartition("t3", 2))

    //偏移量集合
    val offsets = scala.collection.mutable.Map[TopicPartition, Long]()
    offsets.put(new TopicPartition("test1", 0), 3)
    //        offsets.put(new TopicPartition("t2", 1), 3)
    //        offsets.put(new TopicPartition("t3", 2), 0)

    val conss = ConsumerStrategies.Assign[String, String](tps, kafkaParams, offsets)
              //ConsumerStrategies.Assign[String, String](tps, kafkaParams, offsets)
    //创建kakfa直向流
    val stream = KafkaUtils.createDirectStream[String, String](
      ssc,
      locStra,
      conss
    )

    val ds2 = stream.map(record => {
      val t = Thread.currentThread().getName
      val key = record.key()
      val value = record.value()
      val offset = record.offset()
      val par = record.partition()
      val topic = record.topic()
      val tt = ("k:" + key, "v:" + value, "o:" + offset, "p:" + par, "t:" + topic, "T : " + t)
      // SendDataToKafka.sendInfo(tt.toString(), this.toString)
      tt
    })
    // ds2.print()
    ds2.foreachRDD(rdd=>{
      rdd.foreachPartition(iter=>{
        iter.foreach(line=>{
            println("data-------->" + line._1 + "\t" + line._2 + "\t"+ line._3 + "\t"+ line._4 + "\t"+ line._5 + "\t"+ line._6 + "\t"  +   "]\t")
        })
      })
    })


    ssc.start()
    ssc.awaitTermination()
  }
}
