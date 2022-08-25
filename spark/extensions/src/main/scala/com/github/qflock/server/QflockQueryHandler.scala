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
package com.github.qflock.server

import java.io.OutputStream

import com.github.qflock.datasource.QflockOutputStreamDescriptor
import org.slf4j.LoggerFactory

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.col


object QflockQueryHandler {
  private val logger = LoggerFactory.getLogger(getClass)
  private val spark = getSparkSession()
  private val dbName = "tpcds"
  private val tables = QflockServerTable.getAllTables
  private val tablesMap = tables.map(t => (t.getTableName -> t)).toMap
  def init(): Unit = {
    tables.foreach(t => t.createViews)
  }
  def getSparkSession(): SparkSession = {
    logger.info(s"create new session")
    SparkSession
      .builder
      .master("local[4]")
      .appName("qflock-jdbc")
      .config("spark.local.dir", "/tmp/spark-temp")
      .enableHiveSupport()
      .getOrCreate()
  }
  spark.sql(s"USE ${dbName}")
  spark.sparkContext.setLogLevel("INFO")
  def handleQuery(query: String,
                  tableName: String,
                  offset: Int,
                  count: Int,
                  outStream: OutputStream): String = {
    val writeRequestId = QflockOutputStreamDescriptor.get.fillRequestInfo(outStream)
    val readRequestId = tablesMap(tableName).descriptor.fillRequestInfo(offset, count)


    val newQuery = query.replace(s" ${tableName} ",
                     s" ${tableName}_${readRequestId} ")
    logger.info(s"Start readRequestId: $readRequestId writeRequestId: $writeRequestId " +
      s"query: $newQuery")
    val df = spark.sql(newQuery)

    // Coalesce is slow.  It is temporary until we support double buffering of
    // the results with multiple threads.
    df // .repartition(1)
      // .orderBy((df.columns.toSeq map { x => col(x) }).toArray: _*)
      .write.format("qflockJdbc")
      .mode("overwrite")
      .option("outStreamRequestId", writeRequestId)
      .option("query", newQuery)
      .save()
    QflockOutputStreamDescriptor.get.freeRequest(writeRequestId)
    tablesMap(tableName).descriptor.freeRequest(readRequestId)
    logger.info(s"Done readRequestId: $readRequestId " +
                s"writeRequestId: $writeRequestId query: $newQuery")
    ""
  }
}
