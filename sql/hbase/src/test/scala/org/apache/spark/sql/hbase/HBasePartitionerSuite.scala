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

import org.scalatest.FunSuite
import org.apache.spark.{SparkConf, LocalSparkContext, SparkContext, Logging}
import org.apache.hadoop.hbase.util.Bytes
import org.apache.spark.rdd.ShuffledRDD

class HBasePartitionerSuite extends FunSuite with LocalSparkContext with Logging {

  val conf = new SparkConf(loadDefaults = false)

  test("test hbase partitioner") {
    sc = new SparkContext("local", "test")
    val data = (1 to 40).map { r =>
      val rowKey = Bytes.toBytes(r)
      val rowKeyWritable = new SparkImmutableBytesWritable(rowKey)
      (rowKeyWritable, r)
    }
    val rdd = sc.parallelize(data, 4)
    val splitKeys = (1 to 40).filter(_ % 5 == 0).filter(_ != 40).map { r =>
      new SparkImmutableBytesWritable(Bytes.toBytes(r))
    }
    val partitioner = new HBasePartitioner(rdd)(splitKeys.toArray)
    val ordering = HBasePartitioner.orderingRowKey
      .asInstanceOf[Ordering[SparkImmutableBytesWritable]]
    val shuffled =
      new ShuffledRDD[SparkImmutableBytesWritable, Int, Int](rdd, partitioner).setKeyOrdering(ordering)

    val groups = shuffled.mapPartitionsWithIndex { (idx, iter) =>
      iter.map(x => (x._2, idx))
    }.collect()
    assert(groups.size == 40)
    assert(groups.map(_._2).toSet.size == 8)
    groups.foreach { r =>
      assert(r._1 > 5 * r._2 && r._1 <= 5 * (1 + r._2))
    }
  }
}
