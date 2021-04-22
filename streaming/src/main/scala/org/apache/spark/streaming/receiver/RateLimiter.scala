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

package org.apache.spark.streaming.receiver

import com.google.common.util.concurrent.{RateLimiter => GuavaRateLimiter}

import org.apache.spark.SparkConf
import org.apache.spark.internal.Logging

/**
 * Provides waitToPush() method to limit the rate at which receivers consume data.
 *
 * waitToPush method will block the thread if too many messages have been pushed too quickly,
 * and only return when a new message has been pushed. It assumes that only one message is
 * pushed at a time.
 *
 * The spark configuration spark.streaming.receiver.maxRate gives the maximum number of messages
 * per second that each receiver will accept.
 * RateLimiter 它实质上是一个限流器，也可以叫做令牌，如果Executor中task每秒计算的速度大于该值则阻塞，
  *            如果小于该值则通过，将流数据加入缓存中进行计算。这种机制也可以叫做令牌桶机制
 * @param conf spark configuration
 */
// 初始最大接收速率。只适用于Receiver Stream，不适用于Direct Stream。
private[receiver] abstract class RateLimiter(conf: SparkConf) extends Logging {

  // treated as an upper limit   从配置参数中获取 maxRate
  private val maxRateLimit = conf.getLong("spark.streaming.receiver.maxRate", Long.MaxValue)
  private lazy val rateLimiter = GuavaRateLimiter.create(getInitialRateLimit().toDouble)

  def waitToPush() {
    rateLimiter.acquire()
  }

  /**
   * Return the current rate limit. If no limit has been set so far, it returns {{{Long.MaxValue}}}.
   */
  def getCurrentLimit: Long = rateLimiter.getRate.toLong

  /**
   * Set the rate limit to `newRate`. The new rate will not exceed the maximum rate configured by
   * {{{spark.streaming.receiver.maxRate}}}, even if `newRate` is higher than that.
   *   接收到的newRate进行比较，取两者中的最小值来作为最大处理速率，如果没有设置，直接设置为newRate
   * @param newRate A new rate in records per second. It has no effect if it's 0 or negative.
   */
  private[receiver] def updateRate(newRate: Long): Unit =
    if (newRate > 0) {
      if (maxRateLimit > 0) {
        //如果设置了maxRateLimit则取两者中的最小值
        rateLimiter.setRate(newRate.min(maxRateLimit))// 取两者中的最小值来作为最大处理速率
      } else {
        rateLimiter.setRate(newRate)
      }
    }

  /**
   * Get the initial rateLimit to initial rateLimiter
   */
  private def getInitialRateLimit(): Long = {
    // 初始最大接收速率。只适用于Receiver Stream，不适用于Direct Stream。
    math.min(conf.getLong("spark.streaming.backpressure.initialRate", maxRateLimit), maxRateLimit)
  }
}
