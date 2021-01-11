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

package org.apache.spark.streaming.kafka010

import java.{ util => ju }

import scala.collection.JavaConverters._

import org.apache.kafka.common.TopicPartition

import org.apache.spark.annotation.Experimental


/**
 *  :: Experimental ::
 * Choice of how to schedule consumers for a given TopicPartition on an executor.
 * See [[LocationStrategies]] to obtain instances.
 * Kafka 0.10 consumers prefetch messages, so it's important for performance
 * to keep cached consumers on appropriate executors, not recreate them for every partition.
 * Choice of location is only a preference, not an absolute; partitions may be scheduled elsewhere.
 */
@Experimental
sealed abstract class LocationStrategy

private case object PreferBrokers extends LocationStrategy

private case object PreferConsistent extends LocationStrategy

private case class PreferFixed(hostMap: ju.Map[TopicPartition, String]) extends LocationStrategy

/**
 * :: Experimental :: object to obtain instances of [[LocationStrategy]]
 * 位置策略，
  * 控制特定的主题分区在哪个执行器上消费的。
  * 在executor针对主题分区如何对消费者进行调度。
  * 位置的选择是相对的，位置策略有三种方案：
 */
@Experimental
object LocationStrategies {
  /**
   *  :: Experimental ::  首选kafka服务器，只有在kafka服务器和executor位于同一主机，可以使用该中策略。
   * Use this only if your executors are on the same nodes as your Kafka brokers.
   */
  @Experimental
  def PreferBrokers: LocationStrategy =
    org.apache.spark.streaming.kafka010.PreferBrokers

  /**
   *  :: Experimental ::
   * Use this in most cases, it will consistently distribute partitions across all executors.
    * 首选一致性.
    * 多数时候采用该方式，在所有可用的执行器上均匀分配kakfa的主题的所有分区。
    * 综合利用集群的计算资源。
   */
  @Experimental
  def PreferConsistent: LocationStrategy =
    org.apache.spark.streaming.kafka010.PreferConsistent

  /**
   *  :: Experimental ::
   * Use this to place particular TopicPartitions on particular hosts if your load is uneven.
   * Any TopicPartition not specified in the map will use a consistent location.
    * PreferFixed 首选固定模式。
    * 如果负载不均衡，可以使用该中策略放置在特定节点使用指定的主题分区。手动控制方案。
    * 没有显式指定的分区仍然采用(2)方案。
   */
  @Experimental
  def PreferFixed(hostMap: collection.Map[TopicPartition, String]): LocationStrategy =
    new PreferFixed(new ju.HashMap[TopicPartition, String](hostMap.asJava))

  /**
   *  :: Experimental ::
   * Use this to place particular TopicPartitions on particular hosts if your load is uneven.
   * Any TopicPartition not specified in the map will use a consistent location.
   */
  @Experimental
  def PreferFixed(hostMap: ju.Map[TopicPartition, String]): LocationStrategy =
    new PreferFixed(hostMap)
}
