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

package org.apache.spark.streaming.scheduler.rate

import org.apache.spark.SparkConf
import org.apache.spark.streaming.Duration

/**
  * A component that estimates the rate at which an `InputDStream` should ingest
  * records, based on updates at every batch completion.
  * RateEstimator 是速率估算器，主要用来估算最大处理速率，
  * 默认的在2.2之前版本中只支持PIDRateEstimator，在以后的版本可能会支持使用其他插件，其源码如下：
  *
  * @see [[org.apache.spark.streaming.scheduler.RateController]]
  */
private[streaming] trait RateEstimator extends Serializable {

  /**
    * Computes the number of records the stream attached to this `RateEstimator`
    * should ingest per second, given an update on the size and completion
    * times of the latest batch.
    *
    * @param time            The timestamp of the current batch interval that just finished
    * @param elements        The number of records that were processed in this batch
    * @param processingDelay The time in ms that took for the job to complete
    * @param schedulingDelay The time in ms that the job spent in the scheduling queue
    */
  def compute(
               time: Long,
               elements: Long,
               processingDelay: Long,
               schedulingDelay: Long): Option[Double]
}

object RateEstimator {

  /**
    * Return a new `RateEstimator` based on the value of
    * `spark.streaming.backpressure.rateEstimator`.
    *
    * The only known and acceptable estimator right now is `pid`.
    *
    * @return An instance of RateEstimator
    * @throws IllegalArgumentException if the configured RateEstimator is not `pid`.
    */
  def create(conf: SparkConf, batchInterval: Duration): RateEstimator =
  // 速率控制器,Spark 默认只支持此控制器，可自定义。
    conf.get("spark.streaming.backpressure.rateEstimator", "pid") match {
      case "pid" => //默认的只支持pid，其他的配置抛出异常
        // 只能为非负值。当前速率与最后一批速率之间的差值对总控制信号贡献的权重。用默认值即可。
        val proportional = conf.getDouble("spark.streaming.backpressure.pid.proportional", 1.0)
        //只能为非负值。比例误差累积对总控制信号贡献的权重。用默认值即可
        val integral = conf.getDouble("spark.streaming.backpressure.pid.integral", 0.2)
        // 只能为非负值。比例误差变化对总控制信号贡献的权重。用默认值即可
        val derived = conf.getDouble("spark.streaming.backpressure.pid.derived", 0.0)
        val minRate = conf.getDouble("spark.streaming.backpressure.pid.minRate", 100)
        new PIDRateEstimator(batchInterval.milliseconds, proportional, integral, derived, minRate)// 只能为正数，最小速率

      case estimator =>
        throw new IllegalArgumentException(s"Unknown rate estimator: $estimator")
    }
}
