package org.apache.spark.examples.catalog;

import java.sql.Types;

public class HiveTypes {
    
    public static String toHiveType(int sqlType) {

        switch (sqlType) {
            case Types.INTEGER:
                return "INT";
            case Types.SMALLINT:
                return "SMALLINT";
            case Types.VARCHAR:
            case Types.CHAR:
            case Types.LONGVARCHAR:
            case Types.NVARCHAR:
            case Types.NCHAR:
            case Types.LONGNVARCHAR:
            case Types.TIME:
            case Types.CLOB:
                return "STRING";
            case Types.TIMESTAMP:
                return "TIMESTAMP";
            case Types.DATE:
                return "DATE";
            case Types.DECIMAL:
                return "DECIMAL";
            case Types.FLOAT:
                return "FLOAT";
            case Types.NUMERIC:
            case Types.DOUBLE:
            case Types.REAL:
                return "DOUBLE";
            case Types.BIT:
            case Types.BOOLEAN:
                return "BOOLEAN";
            case Types.TINYINT:
                return "TINYINT";
            case Types.BIGINT:
                return "BIGINT";
            default:
                // ARRAY, STRUCT, REF, JAVA_OBJECT.
                return "STRING";
        }
    }

    public static String MysqlTypeToHive(String sqlType) {
        
        switch (sqlType.toUpperCase()) {
            case "INTEGER":
                return "INT";
            case "SMALLINT":
                return "SMALLINT";
            case "VARCHAR":
            case "CHAR":
            case "LONGVARCHAR":
            case "NVARCHAR":
            case "NCHAR":
            case "LONGNVARCHAR":
            case "TIME":
            case "CLOB":
                return "STRING";
            case "TIMESTAMP":
                return "TIMESTAMP";
            case "DATE":
                return "DATE";
            case "DECIMAL":
                return "DECIMAL";
            case "FLOAT":
                return "FLOAT";
            case "NUMERIC":
            case "DOUBLE":
            case "REAL":
                return "DOUBLE";
            case "BIT":
            case "BOOLEAN":
                return "BOOLEAN";
            case "TINYINT":
                return "TINYINT";
            case "BIGINT":
                return "BIGINT";
            default:
                // ARRAY, STRUCT, REF, JAVA_OBJECT.
                return "STRING";
        }
    }
    
    
    
    /**
     * @return true if a sql type can't be translated to a precise match
     * in Hive, and we have to cast it to something more generic.
     */
    public static boolean isHiveTypeImprovised(int sqlType) {
        return sqlType == Types.DATE || sqlType == Types.TIME
                || sqlType == Types.TIMESTAMP
                || sqlType == Types.DECIMAL
                || sqlType == Types.NUMERIC;
    }

    
    
    
    
}
