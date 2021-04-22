/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.streaming.scheduler

import java.io.ObjectInputStream
import java.util.concurrent.atomic.AtomicLong

import scala.concurrent.{ExecutionContext, Future}

import org.apache.spark.SparkConf
import org.apache.spark.streaming.scheduler.rate.RateEstimator
import org.apache.spark.util.{ThreadUtils, Utils}

/**
 * A StreamingListener that receives batch completion updates, and maintains
 * an estimate of the speed at which this stream should ingest messages,
 * given an estimate computation from a `RateEstimator`
 */
private[streaming] abstract class RateController(val streamUID: Int, rateEstimator: RateEstimator)
    extends StreamingListener with Serializable {

  init()

  protected def publish(rate: Long): Unit

  @transient
  implicit private var executionContext: ExecutionContext = _

  @transient
  private var rateLimit: AtomicLong = _

  /**
   * An initialization method called both from the constructor and Serialization code.
   */
  private def init() {
    executionContext = ExecutionContext.fromExecutorService(
      ThreadUtils.newDaemonSingleThreadExecutor("stream-rate-update"))
    rateLimit = new AtomicLong(-1L)
  }

  private def readObject(ois: ObjectInputStream): Unit = Utils.tryOrIOException {
    ois.defaultReadObject()
    init()
  }

  /**
   * Compute the new rate limit and publish it asynchronously.
   */
  private def computeAndPublish(time: Long, elems: Long, workDelay: Long, waitDelay: Long): Unit =
    Future[Unit] {
      // 根据处理时间、调度时间、当前Batch记录数，预估新速率
      val newRate = rateEstimator.compute(time, elems, workDelay, waitDelay)
      newRate.foreach { s =>
        rateLimit.set(s.toLong) //   设置新速率
        publish(getLatestRate()) //   发布新速率
      }
    }

  def getLatestRate(): Long = rateLimit.get()

  /**
    * 反压的实现原理
    * Spark Streaming的反压机制中，有以下几个重要的组件：
    *
    * RateController  速率控制器
    * RateEstimator  速率估算器
    * 以上这两个组件都是在Driver端用于更新最大速度的，而RateLimiter是用于接收到Driver的更新通知之后更新Executor的最大处理速率的组件。
    * RateLimiter是一个抽象类，它并不是Spark本身实现的，而是借助了第三方Google的GuavaRateLimiter来产生的。
    *
    * RateLimiter : 限流器
    * 主要是通过 RateController 组件来实现。RateController继承自接口 StreamingListener,
    * 并实现了onBatchCompleted方法。每一个 Batch 处理完成后都会调用此方法,具体如下:
    *
    * @param batchCompleted
    */
  override def onBatchCompleted(batchCompleted: StreamingListenerBatchCompleted) {
    val elements = batchCompleted.batchInfo.streamIdToInputInfo

    for {
      processingEnd <- batchCompleted.batchInfo.processingEndTime      // 处理结束时间
      workDelay <- batchCompleted.batchInfo.processingDelay  // 处理时间,即`processingEndTime` - `processingStartTime`
      waitDelay <- batchCompleted.batchInfo.schedulingDelay  // 在调度队列中的等待时间,即`processingStartTime` - `submissionTime`
      elems <- elements.get(streamUID).map(_.numRecords)    // 当前批次处理的记录数
    } computeAndPublish(processingEnd, elems, workDelay, waitDelay) // 接着又调用的是computeAndPublish方法
  }
}

object RateController {
  def isBackPressureEnabled(conf: SparkConf): Boolean = {
    conf.getBoolean("spark.streaming.backpressure.enabled", false) // false	是否启用反压机制
  }
}
