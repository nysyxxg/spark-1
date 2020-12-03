package hbase;

import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.CommonConfigurationKeysPublic;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.security.UserGroupInformation;
import util.AuthUtil;
import util.KerberosUtils;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
@Slf4j
public class HbaseManager {
    
    private static final String HADOOP_SECURITY_AUTHENTICATION = CommonConfigurationKeysPublic.HADOOP_SECURITY_AUTHENTICATION;
    
    private static final String KERBEROS_NAME = UserGroupInformation.AuthenticationMethod.KERBEROS.name();
    
    public static Map<Connection, Long> connTime = new ConcurrentHashMap<>();
    public static ThreadLocal<Connection> connHolder = new ThreadLocal<>();
    
    //得到连接
    public static Connection getConnection() throws Exception {
        Long currentTime = System.currentTimeMillis() / 1000;
        Iterator<Connection> iter = connTime.keySet().iterator();
        while (iter.hasNext()) {
            Connection con = iter.next();
            Long lastTime = connTime.get(con);// 获取上一次时间
            long minutes = (currentTime - lastTime) / 60;
            if (minutes >= 6) {
                log.info("--------------------删除超过12个小时的hbase的连接------minutes-" + minutes);
                iter.remove();
            }
        }
        log.info("--------------------获取hbase的连接------------------------------------begin-----------------");
        Connection connection = connHolder.get();
        
        if (connection != null) {
            Long lastTime = connTime.get(connection);// 获取上一次时间
            if (lastTime != null) {
                long minutes = (currentTime - lastTime) / 60;
                log.info("--------------------获取hbase的连接时间：----------------------------minutes----" + minutes);
                if (minutes >= 6) {
                    connTime.remove(connection);
                    connHolder.remove();
                    connection = null;
                }
            } else {
                connHolder.remove();
                connection = null;
            }
        }
        
        log.info("--------------------获取hbase的连接connection：----------------------------connection----" + connection);
        if (connection == null) {
            Configuration conf = HBaseConfiguration.create();
            conf.addResource("hbase-site.xml");
            conf.addResource("core-site.xml");
            conf.addResource("hdfs-site.xml");
//            conf.set(HADOOP_SECURITY_AUTHENTICATION,"kerberos");
//            conf.set("hbase.zookeeper.quorum", "localhost:2181,localhost:2181,localhost:2181");
//            conf.set("security.protocol" , "SASL_PLAINTEXT");
//            conf.set("sasl.mechanism", "GSSAPI");
            //设置扫描超时
//            conf.set("hbase.client.scanner.timeout.period", "10");
//            conf.set("hbase.client.operation.timeout","10");
            
            final String kerberosConf = conf.get(HADOOP_SECURITY_AUTHENTICATION, UserGroupInformation.AuthenticationMethod.SIMPLE.name());
            if (KERBEROS_NAME.equalsIgnoreCase(kerberosConf)) {
                KerberosUtils.initKerberos(KerberosUtils.userPrincipal, KerberosUtils.userKeyTableFile);
                connection = AuthUtil.get(() -> ConnectionFactory.createConnection(conf));
                if (connection == null) {
                    KerberosUtils.initKerberos(KerberosUtils.userPrincipal, KerberosUtils.userKeyTableFile);
                    connection = AuthUtil.get(() -> ConnectionFactory.createConnection(conf));
                }
            } else {
                connection = ConnectionFactory.createConnection(conf);
            }
            connTime.put(connection, currentTime);
            connHolder.set(connection);
        }
        return connection;
    }
    
    //关闭连接
    public static void close() throws IOException {
        Connection connection = connHolder.get();
        if (null != connection) {
            //关闭连接。这个连接已完成
            connection.close();
            //释放缓存空间：把线程的存储空间删除掉
            connHolder.remove();
        }
    }
    
    // 关闭连接
    public static void close(Connection connection, Admin admin, Table table) {
        try {
            if (admin != null) {
                admin.close();
            }
            if (null != connection) {
                close();
            }
            if (table != null) {
                table.close();
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }
}
