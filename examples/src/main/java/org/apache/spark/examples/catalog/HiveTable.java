package org.apache.spark.examples.catalog;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class HiveTable {
    private String tableName;
    private String tableDescribe;
    private List<HiveColumn> columnList = new ArrayList<>();
    
    public String getTableName() {
        return tableName;
    }
    
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }
    
    public String getTableDescribe() {
        return tableDescribe;
    }
    
    public void setTableDescribe(String tableDescribe) {
        this.tableDescribe = tableDescribe;
    }
    
    public List<HiveColumn> getColumnList() {
        return columnList;
    }
    
    public void setColumnList(List<HiveColumn> columnList) {
        this.columnList = columnList;
    }
}
