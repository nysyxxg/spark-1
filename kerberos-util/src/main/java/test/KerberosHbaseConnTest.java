package test;

import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;
import hbase.HbaseManager;
import util.KerberosUtils;
import util.PropertiesUtil;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class KerberosHbaseConnTest {
    
    public static void main(String[] args) throws Exception {
        PropertiesUtil propertiesUtil = PropertiesUtil.getInstance();
        String userPrincipal = propertiesUtil.read("config.properties", "kerberos.user.principal.name");
        String userKeyTableFile = propertiesUtil.read("config.properties", "kerberos.user.keytab");
        String krbFile = propertiesUtil.read("config.properties", "java.security.krb5.conf");
        System.setProperty("java.security.krb5.conf", krbFile);
        KerberosUtils.initKerberos(userPrincipal,userKeyTableFile);
        
        Connection connection = HbaseManager.getConnection();
        System.out.println("hbase连接： " + connection);
        
        Table table = connection.getTable(TableName.valueOf("namespace:hbase_user_info"));
        ResultScanner resultScanner = table.getScanner(new Scan());
        for (Result result : resultScanner) {
            String rowKey = Bytes.toString(result.getRow());
            Cell[] cells = result.rawCells();
            Map<String, String> valuesMap = new HashMap<>();
            for (Cell cell : cells) {
                println(cell);
                valuesMap.put(Bytes.toString(CellUtil.cloneQualifier(cell)), Bytes.toString(CellUtil.cloneValue(cell)));
            }
            if (rowKey != null) {
                System.out.println("rowKey: " + rowKey);
            }
        }
    }
    
    private static void println(Cell cell) {
        if (true) {
            log.info("行键:" + Bytes.toString(CellUtil.cloneRow(cell))); //得到rowkey
            log.info("列族" + Bytes.toString(CellUtil.cloneFamily(cell)));   //得到列族
            log.info("列:" + Bytes.toString(CellUtil.cloneQualifier(cell)));// 得到字段
            log.info("值:" + Bytes.toString(CellUtil.cloneValue(cell))); // 得到value
        }
    }
}
