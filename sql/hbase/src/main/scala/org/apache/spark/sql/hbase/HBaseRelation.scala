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

package org.apache.spark.sql.hbase

import org.apache.hadoop.hbase.client.HTableInterface
import org.apache.log4j.Logger
import org.apache.spark.{Partition, Partitioner}
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.catalyst.expressions.{AttributeReference, Attribute}
import org.apache.spark.sql.catalyst.plans.logical.LeafNode
import org.apache.hadoop.hbase.regionserver.HRegion

import scala.collection.JavaConverters

/**
 * HBaseRelation
 *
 * Created by stephen.boesch@huawei.com on 9/8/14
 */


private[hbase] case class HBaseRelation(tableName: String, alias: Option[String])
                                       (val table: HTableInterface,
                                        val partitions: Seq[Partition])
                                       (@transient hbaseContext: HBaseSQLContext)
  extends LeafNode {

  self: Product =>

  val logger = Logger.getLogger(getClass.getName)

  def partitionKeys: Seq[Attribute] = ???

  override def output: Seq[Attribute] = ???

}