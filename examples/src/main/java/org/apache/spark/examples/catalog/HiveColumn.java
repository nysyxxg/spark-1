package org.apache.spark.examples.catalog;

import lombok.Data;

import java.io.Serializable;

@Data
public class HiveColumn implements Serializable {
    private String columnName;
    private String columnType;
    private String describe;
    private Integer type;// 多个字段字段进行转化
    
}
