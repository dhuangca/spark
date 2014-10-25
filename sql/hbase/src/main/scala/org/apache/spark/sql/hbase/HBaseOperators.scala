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

import org.apache.spark.annotation.DeveloperApi
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.catalyst.expressions._
import org.apache.spark.sql.execution.{LeafNode, UnaryNode, SparkPlan}

/**
 * :: DeveloperApi ::
 * The HBase table scan operator.
 */
@DeveloperApi
case class HBaseSQLTableScan(
    relation: HBaseRelation,
    output: Seq[Attribute],
    rowKeyPredicate: Option[Expression],
    valuePredicate: Option[Expression],
    partitionPredicate: Option[Expression],
    coProcessorPlan: Option[SparkPlan])
    (@transient context: HBaseSQLContext)
  extends LeafNode {

  override def execute(): RDD[Row] = {
    new HBaseSQLReaderRDD(
      relation,
      output,
      rowKeyPredicate, // TODO:convert to column pruning preds
      valuePredicate,
      partitionPredicate, // PartitionPred : Option[Expression]
      None, // coprocSubPlan: SparkPlan
      context
    )
  }
}

@DeveloperApi
case class InsertIntoHBaseTable(
    relation: HBaseRelation,
    child: SparkPlan)
    (@transient hbContext: HBaseSQLContext)
  extends UnaryNode {

  override def execute() = {
    val childRdd = child.execute()
    assert(childRdd != null)
    // YZ: to be implemented using sc.runJob() => SparkContext needed here
    childRdd
  }

  override def output = child.output
}