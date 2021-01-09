package org.apache.spark.examples.catalog

object SparkSqlCataLogTest {

  import org.apache.spark.sql.Dataset
  import org.apache.spark.sql.SparkSession

  @throws[Exception]
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder.config("spark.driver.host", "localhost").appName("CatalogApiTest").master("local").getOrCreate
    spark.sparkContext.setLogLevel("ERROR")
    //1：数据库元数据信息
    //显示所有的数据库
    spark.catalog.listDatabases.show()
    //        +-------+----------------+--------------------+
    //        |   name|     description|         locationUri|
    //        +-------+----------------+--------------------+
    //        |default|default database|/apps/hive/warehouse|
    //        +-------+----------------+--------------------+
    System.out.println("当前库:" + spark.catalog.currentDatabase)
    //当前库:default
    //获取某个数据库的信息
    val aDefault1 = spark.catalog.getDatabase("default")
    System.out.println(aDefault1)
    //Database[name='default', description='default database', path='/apps/hive/warehouse']
    //检查某个数据库是否存在
    System.out.println("twq库是否存在:" + spark.catalog.databaseExists("twq"))
    //twq库是否存在:false
    //创建数据库
    spark.sql("CREATE DATABASE IF NOT EXISTS twq " + "COMMENT 'Test database' LOCATION 'hdfs://kncloud02:8020/user/oozie/oozie_test/dir8/spark-db'")
    //设置当前要操作的数据库
    spark.catalog.setCurrentDatabase("twq")
    System.out.println("当前库:" + spark.catalog.currentDatabase)
    //当前库:twq
    //2：表元数据相关信息
    //查看某个数据库中所有表信息
    spark.catalog.listTables("twq").show()
    //        +----+--------+-----------+---------+-----------+
    //        |name|database|description|tableType|isTemporary|
    //        +----+--------+-----------+---------+-----------+
    //        +----+--------+-----------+---------+-----------+
    val sessionDF = spark.read.parquet("/test" + "/trackerSession")
    //创建一张表
    sessionDF.createTempView("trackerSession")
    spark.catalog.listTables("twq").show()
    //        +--------------+--------+-----------+---------+-----------+
    //        |          name|database|description|tableType|isTemporary|
    //        +--------------+--------+-----------+---------+-----------+
    //        |trackersession|    null|       null|TEMPORARY|       true|
    //        +--------------+--------+-----------+---------+-----------+
    val sessionRecords = spark.sql("select * from trackerSession")
    sessionRecords.show()
    //        +--------------------+-------------------+-------+------------+---------+--------------------+--------------+-----------+---------------+------------+
    //        |          session_id|session_server_time| cookie|cookie_label|       ip|         landing_url|pageview_count|click_count|         domain|domain_label|
    //        +--------------------+-------------------+-------+------------+---------+--------------------+--------------+-----------+---------------+------------+
    //        |520815c9-bdd4-40c...|2017-09-04 12:00:00|cookie1|          固执|127.0.0.3|https://www.baidu...|             1|          2|  www.baidu.com|      level1|
    //        |912a4b47-6984-476...|2017-09-04 12:45:01|cookie1|          固执|127.0.0.3|https://tieba.bai...|             1|          2|tieba.baidu.com|           -|
    //        |79534f7c-b4dc-4bc...|2017-09-04 12:00:01|cookie2|         有偏见|127.0.0.4|https://www.baidu...|             3|          1|  www.baidu.com|      level1|
    //        +--------------------+-------------------+-------+------------+---------+--------------------+--------------+-----------+---------------+------------+
    System.out.println("log表是否存在:" + spark.catalog.tableExists("log"))
    //log表是否存在:false
    System.out.println("trackerSession表是否存在:" + spark.catalog.tableExists("trackerSession"))
    //trackerSession表是否存在:true
    val aDefault = spark.catalog.listTables("default")
    aDefault.show(false)
    //        |name          |database|description|tableType|isTemporary|
    //        |trackersession|null    |null       |TEMPORARY|true       |
    //获取某个表的信息
    val trackerSession = spark.catalog.getTable("trackerSession")
    System.out.println(trackerSession)
    //Table[name='trackerSession', tableType='TEMPORARY', isTemporary='true']
    //表的缓存
    spark.catalog.cacheTable("trackerSession")
    spark.catalog.uncacheTable("trackerSession")
    //3：表的列的元数据信息
    spark.catalog.listColumns("trackerSession").show()
    //        +-------------------+-----------+--------+--------+-----------+--------+
    //        |               name|description|dataType|nullable|isPartition|isBucket|
    //        +-------------------+-----------+--------+--------+-----------+--------+
    //        |         session_id|       null|  string|    true|      false|   false|
    //        |session_server_time|       null|  string|    true|      false|   false|
    //        |             cookie|       null|  string|    true|      false|   false|
    //        |       cookie_label|       null|  string|    true|      false|   false|
    //        |                 ip|       null|  string|    true|      false|   false|
    //        |        landing_url|       null|  string|    true|      false|   false|
    //        |     pageview_count|       null|     int|    true|      false|   false|
    //        |        click_count|       null|     int|    true|      false|   false|
    //        |             domain|       null|  string|    true|      false|   false|
    //        |       domain_label|       null|  string|    true|      false|   false|
    //         +-------------------+-----------+--------+--------+-----------+--------+
    spark.stop()
  }
}
