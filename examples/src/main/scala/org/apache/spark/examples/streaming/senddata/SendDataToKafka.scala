package org.apache.spark.examples.streaming.senddata

object SendDataToKafka {


  def main(args: Array[String]): Unit = {


  }

  def sendInfo(msg: String, objStr: String) = {
    //获取ip
    val ip = java.net.InetAddress.getLocalHost.getHostAddress
    //得到pid
    val rr = java.lang.management.ManagementFactory.getRuntimeMXBean();
    val pid = rr.getName().split("@")(0);
    //pid
    //线程
    val tname = Thread.currentThread().getName
    //对象id
    val sock = new java.net.Socket("localhost", 8888)
    val out = sock.getOutputStream
    val m = ip + "\t:" + pid + "\t:" + tname + "\t:" + msg + "\t:" + objStr + "\r\n"
    out.write(m.getBytes)
    out.flush()
    out.close()
  }

}
