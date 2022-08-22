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

import java.io.{BufferedInputStream, BufferedReader, BufferedWriter, DataInputStream, File, InputStreamReader, PrintWriter, StringWriter}
import java.net.{HttpURLConnection, URL}
import javax.json.Json
import javax.json.JsonArrayBuilder
import javax.json.JsonObject
import javax.json.JsonObjectBuilder
import javax.json.JsonWriter

import scala.collection.mutable.ListBuffer

import com.github.qflock.extensions.compact.QflockCompactColVectReader
import org.apache.log4j.BasicConfigurator
import org.slf4j.LoggerFactory

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._

object ClientTest {
  private val logger = LoggerFactory.getLogger(getClass)
  def getJson(query: String): String = {
    val queryBuilder = Json.createObjectBuilder()
    queryBuilder.add("query", query)
    val queryJson = queryBuilder.build
    val stringWriter = new StringWriter
    val writer = Json.createWriter(stringWriter)
    writer.writeObject(queryJson)
    stringWriter.getBuffer().toString()
  }
  def getSparkSession(): SparkSession = {
    logger.info(s"create new session")
    SparkSession
      .builder
      .master("local")
      .appName("qflock-jdbc")
      .config("spark.local.dir", "/tmp/spark-temp")
      .enableHiveSupport()
      .getOrCreate()
  }
  val spark = getSparkSession()
  spark.sparkContext.setLogLevel("INFO")
  def runQuery(query: String, schema: StructType): ListBuffer[String] = {
    val url = new URL("http://192.168.64.3:9860/test")
    val con = url.openConnection.asInstanceOf[HttpURLConnection]
    con.setRequestMethod("POST")
    con.setRequestProperty("Accept", "application/json")
    con.setDoOutput(true)
    con.setReadTimeout(0)
    con.setConnectTimeout(0)
    val jsonString = getJson(query)
    val os = con.getOutputStream
    try {
      val input = jsonString.getBytes("utf-8")
      os.write(input, 0, input.length)
    } finally if (os != null) os.close()
    val inStream = new DataInputStream(new BufferedInputStream(con.getInputStream))
//    val br = new BufferedReader(new InputStreamReader(inStream, "utf-8"))
    var data = ListBuffer.empty[String]
    try {
      data = readData(schema, 4096, inStream)
    } finally if (inStream != null) inStream.close()
    data
  }
  def readData(schema: StructType,
               batchSize: Int,
               stream: DataInputStream): ListBuffer[String] = {
    val data: ListBuffer[String] = ListBuffer.empty[String]
    val reader = new QflockCompactColVectReader(schema, batchSize, stream)
    while (reader.next()) {
      val batch = reader.get()
      val rowIter = batch.rowIterator()
      while (rowIter.hasNext()) {
        val row = rowIter.next()
        val values = schema.fields.zipWithIndex.map(s => s._1.dataType match {
          case LongType => row.getLong(s._2)
          case DoubleType => row.getDouble(s._2)
          case StringType => row.getUTF8String(s._2)
        })
        // scalastyle:off println
       data += values.mkString(",")
        // scalastyle:on println
      }
    }
    reader.close()
    data
  }
  def queryCallCenter: Unit = {
    logger.info("start call_center")
    val schema = StructType(Array(
      StructField("cc_call_center_sk", LongType, true),
      StructField("cc_call_center_id", StringType, true),
      StructField("cc_rec_start_date", StringType, true),
      StructField("cc_rec_end_date", StringType, true),
      StructField("cc_closed_date_sk", LongType, true),
      StructField("cc_open_date_sk", LongType, true),
      StructField("cc_name", StringType, true),
      StructField("cc_class", StringType, true),
      StructField("cc_employees", LongType, true),
      StructField("cc_sq_ft", LongType, true),
      StructField("cc_hours", StringType, true),
      StructField("cc_manager", StringType, true),
      StructField("cc_mkt_id", LongType, true),
      StructField("cc_mkt_class", StringType, true),
      StructField("cc_mkt_desc", StringType, true),
      StructField("cc_market_manager", StringType, true),
      StructField("cc_division", LongType, true),
      StructField("cc_division_name", StringType, true),
      StructField("cc_company", LongType, true),
      StructField("cc_company_name", StringType, true),
      StructField("cc_street_number", StringType, true),
      StructField("cc_street_name", StringType, true),
      StructField("cc_street_type", StringType, true),
      StructField("cc_suite_number", StringType, true),
      StructField("cc_city", StringType, true),
      StructField("cc_county", StringType, true),
      StructField("cc_state", StringType, true),
      StructField("cc_zip", StringType, true),
      StructField("cc_country", StringType, true),
      StructField("cc_gmt_offset", DoubleType, true),
      StructField("cc_tax_percentage", DoubleType, true)
    ))
    val data = runQuery("select * from call_center",
      schema)
    writeToFile(data, schema, "call_center.csv")
  }
  def queryWebReturns: Unit = {
    logger.info("start web_returns")
    val schema = StructType(Array(
      StructField("wr_returned_date_sk", LongType, true),
      StructField("wr_returned_time_sk", LongType, true),
      StructField("wr_item_sk", LongType, true),
      StructField("wr_refunded_customer_sk", LongType, true),
      StructField("wr_refunded_cdemo_sk", LongType, true),
      StructField("wr_refunded_hdemo_sk", LongType, true),
      StructField("wr_refunded_addr_sk", LongType, true),
      StructField("wr_returning_customer_sk", LongType, true),
      StructField("wr_returning_cdemo_sk", LongType, true),
      StructField("wr_returning_hdemo_sk", LongType, true),
      StructField("wr_returning_addr_sk", LongType, true),
      StructField("wr_web_page_sk", LongType, true),
      StructField("wr_reason_sk", LongType, true),
      StructField("wr_order_number", LongType, true),
      StructField("wr_return_quantity", LongType, true),
      StructField("wr_return_amt", DoubleType, true),
      StructField("wr_return_tax", DoubleType, true),
      StructField("wr_return_amt_inc_tax", DoubleType, true),
      StructField("wr_fee", DoubleType, true),
      StructField("wr_return_ship_cost", DoubleType, true),
      StructField("wr_refunded_cash", DoubleType, true),
      StructField("wr_reversed_charge", DoubleType, true),
      StructField("wr_account_credit", DoubleType, true),
      StructField("wr_net_loss", DoubleType, true)
    ))
    val data = runQuery("select * from web_returns",
      schema)
    writeToFile(data, schema, "web_returns.csv")
  }
  def writeToFile(data: ListBuffer[String],
                  schema: StructType,
                  fileName: String): Unit = {
    val tmpFilename = fileName
    val writer = new PrintWriter(new File(tmpFilename))
    data.foreach(x => writer.write(x + "\n"))
    writer.close()
//    val df = spark.read.format("csv")
//      .schema(schema)
//      .load(tmpFilename)
//    val castColumns = (df.schema.fields map { x =>
//      if (x.dataType == DoubleType) {
//        format_number(bround(col(x.name), 3), 2)
//      } else {
//        col(x.name)
//      }
//    }).toArray
//    val columns = (df.schema.fields map { x => col(x.name) }).toArray
//    df.select(castColumns: _*)
//      .repartition(1)
//      .orderBy((df.columns.toSeq map { x => col(x) }).toArray: _*)
//      .repartition(1)
//      .write.mode("overwrite")
//      .format("csv")
//      .option("header", "true")
//      .option("partitions", "1")
//      .save(fileName)
  }
  def queryStoreSales: Unit = {
    logger.info("start store_sales")
    val schema = StructType(Array(
      StructField("ss_sold_date_sk", LongType, true),
      StructField("ss_sold_time_sk", LongType, true),
      StructField("ss_item_sk", LongType, true),
      StructField("ss_customer_sk", LongType, true),
      StructField("ss_cdemo_sk", LongType, true),
      StructField("ss_hdemo_sk", LongType, true),
      StructField("ss_addr_sk", LongType, true),
      StructField("ss_store_sk", LongType, true),
      StructField("ss_promo_sk", LongType, true),
      StructField("ss_ticket_number", LongType, true),
      StructField("ss_quantity", LongType, true),
      StructField("ss_wholesale_cost", DoubleType, true),
      StructField("ss_list_price", DoubleType, true),
      StructField("ss_sales_price", DoubleType, true),
      StructField("ss_ext_discount_amt", DoubleType, true),
      StructField("ss_ext_sales_price", DoubleType, true),
      StructField("ss_ext_wholesale_cost", DoubleType, true),
      StructField("ss_ext_list_price", DoubleType, true),
      StructField("ss_ext_tax", DoubleType, true),
      StructField("ss_coupon_amt", DoubleType, true),
      StructField("ss_net_paid", DoubleType, true),
      StructField("ss_net_paid_inc_tax", DoubleType, true),
      StructField("ss_net_profit", DoubleType, true)
    ))
    val data = runQuery("select * from store_sales",
      schema)

    writeToFile(data, schema, "store_sales.csv")
  }
  def queryItem: Unit = {
    logger.info("start item")
    val schema = StructType(Array(
      StructField("i_item_sk", LongType, true),
      StructField("i_item_id", StringType, true),
      StructField("i_rec_start_date", StringType, true),
      StructField("i_rec_end_date", StringType, true),
      StructField("i_item_desc", StringType, true),
      StructField("i_current_price", DoubleType, true),
      StructField("i_wholesale_cost", DoubleType, true),
      StructField("i_brand_id", LongType, true),
      StructField("i_brand", StringType, true),
      StructField("i_class_id", LongType, true),
      StructField("i_class", StringType, true),
      StructField("i_category_id", LongType, true),
      StructField("i_category", StringType, true),
      StructField("i_manufact_id", LongType, true),
      StructField("i_manufact", StringType, true),
      StructField("i_size", StringType, true),
      StructField("i_formulation", StringType, true),
      StructField("i_color", StringType, true),
      StructField("i_units", StringType, true),
      StructField("i_container", StringType, true),
      StructField("i_manager_id", LongType, true),
      StructField("i_product_name", StringType, true)
    ))
    val data = runQuery("select * from item",
      schema)

    writeToFile(data, schema, "item.csv")
  }

  def main(args: scala.Array[String]): Unit = {
    BasicConfigurator.configure

    queryCallCenter
    queryWebReturns
    queryItem
//    queryStoreSales
  }
}
