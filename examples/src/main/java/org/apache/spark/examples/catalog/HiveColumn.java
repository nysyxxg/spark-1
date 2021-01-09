package org.apache.spark.examples.catalog;

import lombok.Data;

import java.io.Serializable;

@Data
public class HiveColumn implements Serializable {
    private String columnName;
    private String columnType;
    private String describe;
    private Integer type;// 多个字段字段进行转化
    
    public String getColumnName() {
        return columnName;
    }
    
    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }
    
    public String getColumnType() {
        return columnType;
    }
    
    public void setColumnType(String columnType) {
        this.columnType = columnType;
    }
    
    public String getDescribe() {
        return describe;
    }
    
    public void setDescribe(String describe) {
        this.describe = describe;
    }
    
    public Integer getType() {
        return type;
    }
    
    public void setType(Integer type) {
        this.type = type;
    }
}
