package org.apache.spark.examples.accumulators

import org.apache.spark.{SparkConf, SparkContext}

object CardCount {
  def main(args: Array[String]) {
    val conf = new SparkConf().setAppName("calcCardCountDemo").setMaster("local")
    val sc = new SparkContext(conf)
    val cc = new CalcCardCount
    sc.register(cc) // 注册累加器
    val cardList = sc.parallelize(List[String]("card1 1", "card1 3", "card1 7", "card2 5", "card2 2"), 2)
    val cardMapRDD = cardList.map(card => {
      var cardInfo = new Card(0, 0)
      card.split(" ")(0) match {
        case "card1" => cardInfo = Card(card.split(" ")(1).toInt, 0)
        case "card2" => cardInfo = Card(0, card.split(" ")(1).toInt)
        case _ => Card(0, 0)
      }
      cc.add(cardInfo)
    })
    cardMapRDD.count() //执行action，触发上面的累加操作
    println("card1总数量为:" + cc.result.card1Count + ",card2总数量为:" + cc.result.card2Count)
  }
}