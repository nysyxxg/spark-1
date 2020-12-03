package util;

import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;

import static org.apache.hadoop.fs.CommonConfigurationKeysPublic.HADOOP_SECURITY_AUTHENTICATION;

public class KerberosUtils {
    
    public static String userPrincipal;
    public static String userKeyTableFile;
    public static String krbFile;
    public static PropertiesUtil propertiesUtil = PropertiesUtil.getInstance();
    
    static {
        userPrincipal = propertiesUtil.read("config.properties", "kerberos.user.principal.name");
        userKeyTableFile = propertiesUtil.read("config.properties", "kerberos.user.keytab");
        krbFile = propertiesUtil.read("config.properties", "java.security.krb5.conf");
    }
    
    
    public static void initKerberos(String userPrincipal, String userKeyTableFile) {
        System.setProperty("java.security.krb5.conf", krbFile);
        if (!userPrincipal.isEmpty() && !userKeyTableFile.isEmpty()) {
            AuthUtil.init(userPrincipal, userKeyTableFile, StringUtils.EMPTY);
        }
    }
    
    public static void initKerberos(String userPrincipal, String userKeyTableFile, Configuration conf) {
        System.setProperty("java.security.krb5.conf", krbFile);
        if (!userPrincipal.isEmpty() && !userKeyTableFile.isEmpty()) {
            AuthUtil.init(userPrincipal, userKeyTableFile, StringUtils.EMPTY, conf);
        }
    }
    
    public static void main(String[] args) {
        PropertiesUtil propertiesUtil = PropertiesUtil.getInstance();
        String userPrincipal = propertiesUtil.read("config.properties", "kerberos.user.principal.name");
        String userKeyTableFile = propertiesUtil.read("config.properties", "kerberos.user.keytab");
        
        Configuration configuration = new Configuration();
        configuration.set(HADOOP_SECURITY_AUTHENTICATION, "kerberos");
        KerberosUtils.initKerberos(userPrincipal, userKeyTableFile, configuration);
    }
    
}
