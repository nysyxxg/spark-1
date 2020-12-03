package org.apache.spark.examples.seq

import scala.collection.mutable.ListBuffer

object SeqTest {

  def main(args: Array[String]): Unit = {
    var line: Seq[(String, Map[String, String])] = Seq[(String, Map[String, String])]()

    var map1 = Map[String, String]()
    map1 += ("name" -> "name")
    map1 += ("age" -> "age")
    map1 += ("sex" -> "sex")
    var tuple1 = new Tuple2[String, Map[String, String]]("t1", map1)


    var map2 = Map[String, String]()
    map2 += ("name1" -> "name1")
    map2 += ("age1" -> "age1")
    map2 += ("sex1" -> "sex1")
    var tuple2 = new Tuple2[String, Map[String, String]]("t2", map2)

    var list = new ListBuffer[(String, Map[String, String])];
    list.+=(tuple1)
    list.+=(tuple2)

    line = list.toList

    //    val data = line.map(t=>{
    //      val tname = t._1
    //      val cols = t._2.map(_._1).toList.mkString(",")
    //      (tname,cols)
    //    })
    //    data.foreach(value => {
    //      println(value._1 + "----->" + value._2)
    //    })


    val data0:Seq[List[String]] = line.map(t => {
      val tname = t._1
      val cols = t._2.map(col => {
        tname + "_" + col._1
      }).toList
      cols
    })
    // 这里主要使用两次flatMap
    data0.flatMap(data => data).foreach(data => {
      println(data)
    })


//    val data1:Seq[List[List[String]]] = line.map(t => {
//      val tname = t._1
//      var list = new ListBuffer[List[String]]
//      val cols = t._2.map(col => {
//        tname + "_" + col._1
//      }).toList
//      list.+=(cols)
//      list.+=(cols)
//      list.toList
//    })
//    // 这里主要使用两次flatMap
//    data1.flatMap(data => data).flatMap(data=>data).foreach(data => {
//      println(data)
//    })

  }
}
