package org.apache.spark.examples;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import scala.Tuple2;
import java.util.Arrays;

public class SparkWordCountWithJava8  extends  SparkAppCommon{
    public static void main(String[] args) {

        SparkConf conf = new SparkConf();
        conf.setAppName("WortCount");
        conf.setMaster("local");
        JavaSparkContext sc = new JavaSparkContext(conf);

        JavaRDD<String> fileRDD = sc.textFile("D:\\Spark_Ws\\spark-apache\\examples\\src\\main\\resources\\word.txt");
        JavaRDD<String> wordRdd = fileRDD.flatMap(line -> Arrays.asList(line.split(",")).iterator());
        JavaPairRDD<String, Integer> wordOneRDD = wordRdd.mapToPair(word -> new Tuple2<>(word, 1));
        JavaPairRDD<String, Integer> wordCountRDD = wordOneRDD.reduceByKey((x, y) -> x + y);
        JavaPairRDD<Integer, String> count2WordRDD = wordCountRDD.mapToPair(tuple -> new Tuple2<>(tuple._2, tuple._1));
        JavaPairRDD<Integer, String> sortRDD = count2WordRDD.sortByKey(false);
        JavaPairRDD<String, Integer> resultRDD = sortRDD.mapToPair(tuple -> new Tuple2<>(tuple._2, tuple._1));
        resultRDD.saveAsTextFile("D:\\Spark_Ws\\wordcount\\result8");

    }
}