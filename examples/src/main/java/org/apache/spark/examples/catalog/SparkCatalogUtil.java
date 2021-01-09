package org.apache.spark.examples.catalog;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.hive.metastore.api.FieldSchema;
import org.apache.spark.sql.AnalysisException;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.catalog.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.SparkSession;

import java.util.*;
import java.util.regex.Pattern;

@Slf4j
public class SparkCatalogUtil {
    
    private static final String LOCATION_STR = "location";
    
    private static final Pattern PATTERN = Pattern.compile("^.*Detailed (Table|Partition) Information.*$");
    
    /**
     * 通过版本获取分区字段
     *
     * @param dbName
     * @param tableName
     * @param version
     * @return
     */
    public static List<String> listAllPartitionsColums(SparkSession sparkSession,
                                                       String dbName, String tableName, Short version) {
        final List<String> retList = new ArrayList<>();
        final String showSql = String.format("SHOW PARTITIONS `%s`.`%s`", dbName, tableName);
        log.info("Show Partitions sql : " + showSql);
        try {
            Dataset<Column> dataset = sparkSession.catalog().listColumns(dbName, tableName);
            String finalPartStr = "version_partition=" + version;
            Column columsArrys[] = dataset.collect().clone();
            //  String colums[] = dataset.columns();
            for (Column column : columsArrys) {
                String columnName = column.name();
                boolean isPartition = column.isPartition();
                log.info("--------表---------" + tableName + "--->  字段---" + columnName + "---isPartition--" + isPartition);
                //String parts[] = partStr.split("/");
//                List list = Arrays.asList(parts);
//                if (list.contains(finalPartStr)) {
//                    retList.add(partStr);
//                }
                retList.add(columnName);
            }
        } catch (AnalysisException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
        if (retList.size() == 0) {
            log.info("没有获取到数据分区！");
        }
        return retList;
    }
    
    
    public static List<String> listAllPartitionsByVersion(SparkSession sparkSession,
                                                          String dbName, String tableName, Short version) {
        final List<String> retList = new ArrayList<>();
        final String showSql = String.format("SHOW PARTITIONS `%s`.`%s`", dbName, tableName);
        log.info("Show Partitions sql : " + showSql);
        Row rows[] = sparkSession.sql(showSql).collect().clone();
        try {
            String partStr;
            String finalPartStr = "version_partition=" + version;
            //  String colums[] = dataset.columns();
            for (Row row : rows) {
                partStr = row.getString(0);
                log.info("--------finalPartStr---------" + finalPartStr + "--->  字段---partStr--->" + partStr);
                String parts[] = partStr.split("/");
                List list = Arrays.asList(parts);
                if (list.contains(finalPartStr)) {
                    retList.add(partStr);
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
        if (retList.size() == 0) {
            log.info("没有获取到数据分区！");
        }
        return retList;
    }
    
    public static List<String> getHivePartitionField(org.apache.spark.sql.catalog.Catalog catalog,
                                                     String dbName, String tableName, Set<String> partitionFields) {
        final List<String> fieldSchemaList = new ArrayList<>();
        try {
            Dataset<Column> dataset = catalog.listColumns(dbName, tableName);
            Column columsArrys[] = dataset.collect().clone();
            //String colums[] = dataset.columns();
            for (Column column : columsArrys) {
                String columnName = column.name();
                log.info("--------表---------" + tableName + "--->  字段---" + columnName);
                if (partitionFields.contains(columnName)) {
                    log.info("Hive表中" + tableName + "的分区字段列名：" + columnName);
                    fieldSchemaList.add(columnName);
                }
            }
        } catch (AnalysisException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
        return fieldSchemaList;
    }
    
    
    public static String getAddPartitionSql(String dbName, String tableName, String partition) {
        String alterPartSql = String.format("ALTER TABLE `%s`.`%s` ADD IF NOT EXISTS %s", dbName, tableName, partition);
        log.info("AddPartitionSql: " + alterPartSql);
        return alterPartSql;
    }
    
    
    public static String getDropPartitionSql(String dbName, String tableName, String partition) {
        String dropPartitionSql = String.format("ALTER TABLE `%s`.`%s` DROP IF  EXISTS %s", dbName, tableName, partition);
        log.info("dropPartitionSql: " + dropPartitionSql);
        return dropPartitionSql;
    }
    
    private static String getAttrStr(List<HiveColumn> attrList) {
        final StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < attrList.size(); i++) {
            HiveColumn attr = attrList.get(i);
            if (i > 0) {
                stringBuilder.append(", ");
            }
            stringBuilder.append("`").append(attr.getColumnName()).append("` ").append(attr.getColumnType());
            if (StringUtils.isNotBlank(attr.getDescribe())) {
                stringBuilder.append(" COMMENT '").append(attr.getDescribe()).append("'");
            }
        }
        return stringBuilder.toString();
    }
    
    private static Map<String, String> getNewColNameTypeMap(List<HiveColumn> attrList) {
        Map<String, String> map = new HashMap<>();
        for (HiveColumn column : attrList) {
            map.put(column.getColumnName().toLowerCase(), column.getColumnType().toLowerCase());
        }
        return map;
    }
    
    public static List<FieldSchema> createHiveTableAndGetFieldList(SparkSession sparkSession,
                                                                   String dbName, HiveTable table, List<HiveColumn> attrList,
                                                                   String partitionString, String tableFormat,
                                                                   Set<String> partitionFields) {
        String tableName = table.getTableName();
        if (StringUtils.isBlank(tableFormat)) {
            tableFormat = "STORED AS PARQUET TBLPROPERTIES ('parquet.compression'='SNAPPY')";
        }
        
        final String createNotExistsSql = "CREATE TABLE IF NOT EXISTS `" + dbName + "`.`" + tableName +
                "` (" + getAttrStr(attrList) + ") COMMENT '" + table.getTableDescribe() +
                "' PARTITIONED BY (" + partitionString + ") " + tableFormat;
        
        log.info("createSQL: " + createNotExistsSql);
        
        sparkSession.sql(createNotExistsSql);

//        log.info("create hive table result:" +);
        
        //  得到首次创建tableName表的属性集合
        List<FieldSchema> tableColNames = getHiveAttrListWithOutPartitionKey(sparkSession, dbName, tableName, partitionFields);
        Map<String, String> newColNameType = getNewColNameTypeMap(attrList);
        List<String> outHiveTableColNameList = new ArrayList<>();
        for (FieldSchema fieldSchema : tableColNames) {
            String attrName = fieldSchema.getName().toLowerCase();
            String attrType = fieldSchema.getType();
            outHiveTableColNameList.add(attrName);
            if (newColNameType.containsKey(attrName) && !newColNameType.get(attrName).equalsIgnoreCase(attrType)) {
                // 进行更新字段类型
                updateColumn(sparkSession, dbName, tableName, attrName, attrType, newColNameType);
            }
        }
        log.info("Column name: " + StringUtils.join(outHiveTableColNameList, ", "));
        // attrList 是目标表字段属性的集合
        if (attrList.size() < tableColNames.size()) { // 说明目标表中字段有删除
            final String msg = tableName + " 目标表属性个数: " + attrList.size() + ", hive表属性个数：" + tableColNames.size();
            log.info(msg);
        }
        
        List<HiveColumn> addNewAttr = new ArrayList<HiveColumn>();
        for (int i = 0; i < attrList.size(); i++) { // 找出新增的字段
            HiveColumn attribute = attrList.get(i);
            String addField = attribute.getColumnName();
            if (!outHiveTableColNameList.contains(addField.toLowerCase())) {
                final String msg = tableName + "  新增加的字段column=" + addField;
                addNewAttr.add(attribute);
                log.info(msg);
            }
        }
        
        if (addNewAttr.size() > 0) {
            final String alterSql = "ALTER TABLE `" + dbName + "`.`" + tableName + "` ADD COLUMNS (" + getAttrStr(addNewAttr) + ")";
            try {
                log.info("alterSql: " + alterSql);
                sparkSession.sql(alterSql);
                log.info("result:执行成功！");
            } catch (Exception e) {
                e.printStackTrace();
                log.info("result:执行失败！" + alterSql);
            }
        }
        
        // 得到增加字段  和删除字段之后最新的集合
        List<FieldSchema> tableColNames2 = getHiveAttrListWithOutPartitionKey(sparkSession, dbName, tableName, partitionFields);
        return tableColNames2;
    }
    
    
    private static void updateColumn(SparkSession sparkSession,
                                     String dbName, String tableName, String attrName, String attrType,
                                     Map<String, String> colNameTypeMap) {
        
        HiveColumn oldColumn = new HiveColumn();
        oldColumn.setColumnName(attrName);
        oldColumn.setColumnType(attrType);
        
        HiveColumn newColumn = new HiveColumn();
        String newType = colNameTypeMap.get(attrName);
        newColumn.setColumnName(attrName);
        newColumn.setColumnType(newType);
        execUpdateColumn(sparkSession, dbName + "." + tableName, oldColumn, newColumn);
    }
    
    
    public static void execUpdateColumn(SparkSession sparkSession, String tableName, HiveColumn oldColumn, HiveColumn newColumn) {
        StringBuilder sb = new StringBuilder();
        sb.append("alter table ");
        sb.append(tableName);
        sb.append(" change ");
        sb.append("`");
        sb.append(oldColumn.getColumnName())
                .append("` `")
                .append(newColumn.getColumnName())
                .append("` ");
        if (StringUtils.isNotEmpty(newColumn.getColumnType())) {
            sb.append(newColumn.getColumnType());
        } else if (null != newColumn.getType()) {
            sb.append(HiveTypes.toHiveType(newColumn.getType()));
        }
        
        String alterSql = sb.toString();
        log.info("alter sql:[" + alterSql + "]");
        try {
            sparkSession.sql(alterSql);
            log.info("alter sql:[" + alterSql + "]----> 执行成功！");
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            log.error("操作Hive库失败" + e.getMessage());
            throw new RuntimeException(e);
        }
    }
    
    
    public static List<FieldSchema> getHiveAttrListWithOutPartitionKey(SparkSession sparkSession,
                                                                       String dbName, String tableName, Set<String> partitionFields) {
        final List<FieldSchema> fieldSchemaList = new ArrayList<>();
        try {
            Dataset<Column> dataset = sparkSession.catalog().listColumns(dbName, tableName);
            // String colums[] =   dataset.columns();
            Column columsArrys[] = dataset.collect();
            for (Column column : columsArrys) {
                String columnName = column.name();
                if (!partitionFields.contains(columnName)) {
                    fieldSchemaList.add(new FieldSchema(columnName, column.dataType(), column.description()));
                }
            }
        } catch (AnalysisException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
        return fieldSchemaList;
    }
    
    
    /**
     * getHdfsPath 获取hdfs上Hive Partition路径
     *
     * @param dbName    数据库名
     * @param tableName 表名
     */
    public static String getHdfsPath(SparkSession sparkSession,
                                     String dbName, String tableName, String partition) {
        final String sql = String.format("DESCRIBE FORMATTED `%s`.`%s` %s", dbName, tableName, partition);
        log.info("describe extended sql : " + sql);
        String hdfsPath = StringUtils.EMPTY;
        
        Row rows[] = sparkSession.sql(sql).collect();
        try {
            boolean start = false;
            
            for (Row row : rows) {
                int colNum = row.size();
                for (int i = 0; i < colNum && !start; i++) {
                    String value = StringUtils.trimToEmpty(row.getString(i));
                    start = StringUtils.isNotBlank(value) && PATTERN.matcher(value).matches();
                }
                if (start) {
                    for (int i = 0; i < colNum; i++) {
                        if (StringUtils.containsIgnoreCase(row.getString(i), LOCATION_STR)) {
                            StringBuilder stringBuilder = new StringBuilder();
                            for (int i1 = i; i1 < colNum; i1++) {
                                stringBuilder.append(StringUtils.trimToEmpty(row.getString(i1)));
                            }
                            if (stringBuilder.length() > 0) {
                                String locationStr = StringUtils.remove(stringBuilder.toString(), ' ');
                                log.info(locationStr);
                                // spark2.3和hive全版本对应,可能有Location不带冒号及location:
                                hdfsPath = StringUtils.substringAfter(locationStr, LOCATION_STR.substring(1));
                                hdfsPath = StringUtils.substringBefore(hdfsPath, ",");
                                if (hdfsPath.startsWith(":")) {
                                    hdfsPath = hdfsPath.substring(1);
                                }
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
        log.info(hdfsPath);
        return hdfsPath.trim();
    }
    
}
