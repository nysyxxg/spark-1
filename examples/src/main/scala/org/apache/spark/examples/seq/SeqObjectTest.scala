package org.apache.spark.examples.seq

import scala.collection.immutable.Seq

object SeqObjectTest{
  def main(args:Array[String]){
    var seq:Seq[Int] = Seq(52, 85, 1, 8, 3, 2, 7)
    seq.foreach((element:Int) => print(element+" "))
    println("\nis Empty: "+seq.isEmpty)
    println("Ends with (2, 7): "+ seq.endsWith(Seq(2, 7)))
    println("contains 8: "+ seq.contains(8))
    println("last index of 3 : "+seq.lastIndexOf(3))
    println("Reverse order of sequence: "+seq.reverse)
  }
}
