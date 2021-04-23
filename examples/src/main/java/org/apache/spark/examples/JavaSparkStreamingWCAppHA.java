package org.apache.spark.examples;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.function.Function0;
import org.apache.spark.streaming.Duration;
import org.apache.spark.streaming.Seconds;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.spark.streaming.api.java.JavaReceiverInputDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;

/**
 * 第一次在创建和启动StreamingContext的时候，那么将持续不断地将实时计算程序的元数据（比如说，有些dstream或者job执行到了哪个步骤），
 * 如果后面，不幸，因为某些原因导致driver节点挂掉了；那么可以让spark集群帮助我们自动重启driver，
 * 然后继续运行时候计算程序，并且是接着之前的作业继续执行；没有中断，没有数据丢失。
 * 第一次在创建和启动StreamingContext的时候，将元数据写入容错的文件系统（比如hdfs）；
 * spark-submit脚本中加一些参数；保证在driver挂掉之后，spark集群可以自己将driver重新启动起来；
 * 而且driver在启动的时候，不会重新创建一个streaming context，而是从容错文件系统（比如hdfs）中读取之前的元数据信息，
 * 包括job的执行进度，继续接着之前的进度，继续执行。使用这种机制，就必须使用cluster模式提交，
 * 确保driver运行在某个worker上面；
 * ————————————————
 */
public class JavaSparkStreamingWCAppHA {
    public static void main(String[] args) {
        SparkConf conf = new SparkConf();
        conf.setAppName("wc");
        conf.setMaster("local[4]");
        // 开启WAL机制
        conf.set("spark.streaming.receiver.writeAheadLog.enable", "true");
        //创建spark应用上下文，间隔批次是2
        /**
         * 这里为了做到容错，不使用new的方式来创建streamingContext对象，
         * 而是通过工厂的方式JavaStreamingContext.getOrCreate()方式来创建，
         *这种方式创建的好处是：在启动程序的时候回首先去检查点目录检查
         * 如果有数据，就直接从数据中恢复，如果没有数据，就直接new一个新的。
         * 实验：一下代码实现了当driver正常运行的时候，宕机，
         * 重启会自动从以前的checkpoint点拿取数据，重新计算。
         */
        Function0<JavaStreamingContext> contextFunction = new Function0<JavaStreamingContext>() {
            //首次创建context对象调用该方法
            public JavaStreamingContext call() throws Exception {
                JavaStreamingContext jsc = new JavaStreamingContext(conf, Seconds.apply(2));
                JavaReceiverInputDStream<String> socket = jsc.socketTextStream("mini4", 9999);
                /*变化的代码放到处*/
                JavaDStream<Long> dsCount = socket.countByWindow(new Duration(24 * 60 * 60 * 1000), new Duration(2000));
                dsCount.print();
                //切记，这里一定要写检查点
                jsc.checkpoint("g:/log/data/spark/ck/java_spark_streaming_wc");
                return jsc;
            }
        };
        //失败重新连接的时候会经过检查点。
        JavaStreamingContext jsc = JavaStreamingContext.getOrCreate("g:/log/data/spark/ck/java_spark_streaming_wc",contextFunction);

        jsc.start();
        try {
            jsc.awaitTermination();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}