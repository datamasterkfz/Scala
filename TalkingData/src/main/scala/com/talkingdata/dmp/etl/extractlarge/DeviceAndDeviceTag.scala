package com.talkingdata.dmp.etl.extractlarge

import com.talkingdata.utils.PathUtil
import org.apache.spark.sql.{Row, SQLContext}
import org.apache.spark.{SparkConf, SparkContext}

/**
  * Created by chris on 7/25/17.
  */
object DeviceAndDeviceTag {
  def main(args: Array[String]): Unit = {
    val input = args(0)
    val output = args(1)
    val entrySetPath = args(2)
    val modelTag = args(3)
    val dim = args(4).toInt
    val conf = new SparkConf()
    val sc = new SparkContext(conf)
    val sqlContext = new SQLContext(sc)
    val modelMapper = sc.broadcast(sc.textFile(modelTag).map{line=>
      line.split("\t")(0) -> line.split("\t").last.split(" ").map(_.toInt + dim + 1)
    }.collect.toMap)

    val mapper = sc.broadcast(sc.textFile(entrySetPath).flatMap(line => {
      try {
        val tmp = line.split(",")
        Some((tmp(0).stripMargin, Integer.parseInt(tmp.last)))
      } catch {
        case e: Exception => None
      }
    }).collect.slice(0,dim).toMap)
    if(PathUtil.isExisted(sc,output)){
      PathUtil.deleteExistedPath(sc, output)
    }

    val data = sqlContext.read.parquet(input).select("tdid", "idBox").flatMap{
      f=>
        val map = mapper.value
        val map2 = modelMapper.value
        val tdid = f.getAs[String]("tdid")
        val envs = f.getAs[Seq[Row]]("idBox").flatMap{env =>
          val key =  env.getAs[String]("model")
          val index = map.getOrElse(key, 0)
          val attributes = map2.getOrElse(key,Array[Int]())
          if(index != 0){
            attributes.toBuffer.+=(index)
          } else {
            attributes.toBuffer
          }
        }.map{f=>
          s"$f:1"
        }.distinct
        if(envs.nonEmpty){
          Some(s"$tdid ${envs.mkString(" ")}")
        } else {
          None
        }
    }.repartition(1000).saveAsTextFile(output)
  }

}
