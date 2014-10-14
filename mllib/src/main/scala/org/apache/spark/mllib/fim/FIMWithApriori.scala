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
package org.apache.spark.mllib.fim


import org.apache.spark.{Logging, SparkContext}
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD
import org.apache.spark.broadcast._

/**
 * Created by z00143870 on 2014/7/30.
 * calculate frequent item set using Apriori algorithm with dada set and minSupport
 * the apriori algorithm have two step task
 * step one is scaning data db to get L1 by minSuppprt
 * step two is scan data db multiple to get Lk
 */
class FIMWithApriori  extends Logging with Serializable{


  /**
   * create C1 which contains all of items in data Set.
   * @param dataSet For mining frequent itemsets dataset
   * @return all of items in data Set
   */
  def createC1(dataSet: RDD[Array[String]]): Array[Array[String]] =
  {
    //get the items array from data set
    val itemsCollection = dataSet.flatMap(line => line).collect().distinct
    logDebug("itemsCollection:" + itemsCollection)
    //definition new array which item is an array form
    val itemArrCollection = collection.mutable.ArrayBuffer[Array[String]]()

    //change the itemsCollection into itemArrCollection
    for (item <- itemsCollection)
    {
      itemArrCollection += Array[String](item)
    }

    return itemArrCollection.toArray

  }


  /**
   * create Lk from Ck.Lk is generated by Ck when the frequent of Ck bigger than minSupport
   * @param dataSet For mining frequent itemsets dataset
   * @param Ck Candidate set
   * @param minSupport The minimum degree of support
   * @return Lk
   */
  def scanD(dataSet: RDD[Array[String]],
            dataSetLen:Long,
            Ck: Array[Array[String]],
            minSupport: Double,
            sc: SparkContext): Array[(Array[String], Int)] =
  {
    //broadcast Ck
    val broadcastCk = sc.broadcast(Ck)
    //val broadcastCkList: Array[Array[String]] = broadcastCk.value

    val Lk = dataSet.flatMap(line => containCk(line, broadcastCk))
      .filter(_.length > 0)
      .map(v => (v,1))
      .reduceByKey(_+_)
      .filter(_._2 >= (minSupport * dataSetLen))
      .map(v => (v._1.split(" "),v._2))
      .collect()

    return Lk

  }



  /**
   * containCk method.
   * @param line dataset line
   * @param broadcastCk L1
   * @return get Ck array
   */
  def containCk(line: Array[String],
                broadcastCk: Broadcast[Array[Array[String]]]): Array[String] =
  {

    // Ck broadcast value
    val broadcastCkList: Array[Array[String]] = broadcastCk.value
    //
    var dbLineArrayBuffer = collection.mutable.ArrayBuffer[String]()
    // the count number
    var k: Int = 0

    for (broadcastCk <- broadcastCkList)
    {
      val bdArray: Array[String] = broadcastCk.sortWith((s, t) => s.compareTo(t) < 0).array

      if (bdArray.toSet subsetOf (line.toSet))
      {
        val bdString:String = bdArray.mkString(" ")
        dbLineArrayBuffer ++= Array[(String)](bdString)
        k = k + 1
      }

    }

    if (k == 0)
    {
      dbLineArrayBuffer ++= Array[(String)]("")
    }

    return dbLineArrayBuffer.toArray.array

  }

  /**
   * create Ck by Lk
   * @param Lk
   * @param k
   * @return Ck
   */
  def aprioriGen(Lk: Array[(Array[String], Int)],
                 k: Int): Array[Array[String]] =
  {

    val LkLen = Lk.length
    val CkBuffer = collection.mutable.ArrayBuffer[Array[String]]()

    //get Ck from Lk
    for (i <- 0 to LkLen - 1)
      for (j <- i + 1 to LkLen - 1)
      {
        // get Lk：k-2 before k-2 item
        val L1: Array[String] =
          Lk(i)._1.take(k - 2).sortWith((s, t) => s.compareTo(t) < 0)
        val L2: Array[String] =
          Lk(j)._1.take(k - 2).sortWith((s, t) => s.compareTo(t) < 0)

        // merge set while the two set L1 and L2 equals
        if (L1.mkString.equals(L2.mkString))
        {
          CkBuffer.append((Lk(i)._1.toSet ++ Lk(j)._1.toSet).toArray)
        }

      }


    if (CkBuffer.length > 0)
    {
      return CkBuffer.toArray.array
    }
    else
    {
      return null
    }

  }

  /**
   * create L1
   * @param dataSet For mining frequent item sets dataset
   * @param minCount The minimum degree of support
   * @return L1
   */
  def aprioriStepOne(dataSet: RDD[Array[String]],
                     minCount: Double): Array[(Array[String], Int)] =
  {
    dataSet.flatMap(line => line)
      .map(v => (v, 1))
      .reduceByKey(_ + _)
      .filter(_._2 >= minCount)
      .map(v => line2Array(v))
      .collect()
  }

  /**
   * change line type
   * @param line line type (String,Int)
   * @return line tpye (Array[String],Int)
   */
  def line2Array(line:(String,Int)):(Array[String],Int) =
  {
    val arr = Array[String](line._1)
    return (arr,line._2)
  }

  /**
   * apriori algorithm.
   * Solving frequent item sets based on the data set and the minimum degree of support.
   * The first phase, the scan time data sets, computing frequent item sets L1.
   * The second stage, multiple scan data sets, computing frequent item sets Lk.
   * @param dataSet  For mining frequent item sets dataset
   * @param minSupport The minimum degree of support
   * @param sc
   * @return frequent item sets
   */
  def apriori(dataSet: RDD[Array[String]],
              minSupport: Double,
              sc: SparkContext): Array[(String,Int)] = {

    if (dataSet == null)
    {
      logWarning("dadaSet can not be null.")
      return null
    }

    if (sc == null)
    {
      logWarning("sc can not be null.")
      return null
    }
    logDebug("minSupport:" + minSupport)
    //dataSet length
    val dataSetLen:Long = dataSet.count()
    //the count line for minSupport
    val minCount = minSupport * dataSetLen
    logDebug("minCount:" + minCount)
    //definite L collection that using save all of frequent item set
    val L = collection.mutable.ArrayBuffer[Array[(Array[String], Int)]]()
    val FIS = collection.mutable.ArrayBuffer[(String,Int)]()

    //call aprioriStepOne method to get L1
    val L1: Array[(Array[String], Int)] = aprioriStepOne(dataSet,minCount)
    logDebug("L1 length:" + L1.length)
    logDebug("L1:" + L1)

    // L1 assignment to L
    if (L1.length > 0)
    {
      L += L1

      for (arr <- L1)
      {
        FIS += ((arr._1.mkString(" "),arr._2))
      }

      // step counter
      var k: Int = 2
      // do the loop while the k > 0 and L length > 1
      while ((k > 0) && ((L(k - 2).length) > 1)  )
      {
        logDebug("print k:" + k)
        //call createCk method to get Ck
        val Ck: Array[Array[String]] = aprioriGen(L(k-2), k)

        if (Ck != null)
        {
          //call createLk method to get Lk
          val Lk: Array[(Array[String], Int)] =
            scanD(
              dataSet,
              dataSetLen,
              Ck,
              minSupport,
              sc)
          // Lk assignment to L
          L += Lk

          for (arr <- Lk)
          {
            FIS += ((arr._1.mkString(" "),arr._2))
          }

          k = k + 1

        }
        else
        {
          k = -1
        }
      }

      //return L.toArray.array
      return FIS.toArray.array
    }
    else
    {
      //return Array[Array[(Array[String], Int)]]()
      return Array[(String,Int)]()
    }

  }

}

