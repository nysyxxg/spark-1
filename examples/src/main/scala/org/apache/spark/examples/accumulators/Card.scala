package org.apache.spark.examples.accumulators

import org.apache.spark.util.AccumulatorV2

case class Card(var card1Count: Int, var card2Count: Int)

/**
  * 自定义累加器
  */
class CalcCardCount extends AccumulatorV2[Card, Card] {
  var result = new Card(0, 0)

  /** *
    * 判断，这个要和reset设定值一致
    *
    * @return
    */
  override def isZero: Boolean = {
    result.card1Count == 0 && result.card2Count == 0
  }

  /** *
    * 复制一个新的对象
    *
    * @return
    */
  override def copy(): AccumulatorV2[Card, Card] = {
    val newCalcCardCount = new CalcCardCount()
    newCalcCardCount.result = this.result
    newCalcCardCount
  }

  /** *
    * 重置每个分区的数值
    */
  override def reset(): Unit = {
    result.card1Count = 0
    result.card2Count = 0
  }

  /**
    * 每个分区累加自己的数值
    *
    * @param v
    */
  override def add(v: Card): Unit = {
    result.card1Count += v.card1Count
    result.card2Count += v.card2Count
  }

  /** *
    * 合并分区值，求得总值
    *
    * @param other
    */
  override def merge(other: AccumulatorV2[Card, Card]): Unit =
    other match {
      case o: CalcCardCount => {
        result.card1Count += o.result.card1Count
        result.card2Count += o.result.card2Count
      }
    }

  //返回结果
  override def value: Card = result
}
