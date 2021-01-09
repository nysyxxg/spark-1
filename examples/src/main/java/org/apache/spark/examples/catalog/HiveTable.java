package org.apache.spark.examples.catalog;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class HiveTable {
    private String tableName;
    private String tableDescribe;
    private List<HiveColumn> columnList = new ArrayList<>();
}
