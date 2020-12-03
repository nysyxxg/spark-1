package util;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.CommonConfigurationKeysPublic;
import org.apache.hadoop.security.UserGroupInformation;

import java.io.File;
import java.io.IOException;
import java.security.PrivilegedExceptionAction;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Slf4j
public class AuthUtil {
    
    public static Map<UserGroupInformation, Long> ugiTime = new ConcurrentHashMap<>();
    
    private static final ConcurrentLinkedDeque<UserGroupInformation> USER_DEQUE = new ConcurrentLinkedDeque<>();
    
    private static final String HADOOP_SECURITY_AUTHENTICATION = CommonConfigurationKeysPublic.HADOOP_SECURITY_AUTHENTICATION;
    
    private static final String KERBEROS_NAME = UserGroupInformation.AuthenticationMethod.KERBEROS.name();
    
    private static final String ZOOKEEPER_DEFAULT_LOGIN_CONTEXT_NAME = "Client";
    
    private static final String ZOOKEEPER_SERVER_PRINCIPAL_KEY = "zookeeper.server.principal";
    
    private static final String ZOOKEEPER_DEFAULT_SERVER_PRINCIPAL = "zookeeper/hadoop.hadoop.com";
    
    private AuthUtil() {
        super();
    }
    
    /**
     * init 初始化Kerberos认证(在跑Spark的Map操作里)
     *
     * @param conf            HadoopConfiguration
     * @param credentialsByte KerberosUser的credentials
     */
//    public static void init(final Configuration conf, final byte[] credentialsByte) throws IOException {
//        final String kerberosConf = conf.get(HADOOP_SECURITY_AUTHENTICATION, UserGroupInformation.AuthenticationMethod.SIMPLE.name());
//        if (USER_DEQUE.isEmpty() && KERBEROS_NAME.equalsIgnoreCase(kerberosConf)) {
//            synchronized (USER_DEQUE) {
//                if (USER_DEQUE.isEmpty()) {
//                    UserGroupInformation.setConfiguration(conf);
//                    UserGroupInformation user = UserGroupInformation.getLoginUser();
//                    USER_DEQUE.addFirst(user);
//                    long time = System.currentTimeMillis()/1000;
//                    ugiTime.put(user,time);
//                    log.info("Kerberos登录成功【{}】.", user);
//                    if (credentialsByte != null && credentialsByte.length != 0) {
//                        Credentials credentials = new Credentials() {{
//                            try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(credentialsByte);
//                                 DataInputStream dataInputStream = new DataInputStream(byteArrayInputStream)) {
//                                readFields(dataInputStream);
//                            }
//                        }};
//                        Set<Text> tokeKindSet = user.getTokens().parallelStream().map(Token::getKind).collect(Collectors.toSet());
//                        for (Token<?> token : credentials.getAllTokens()) {
//                            if (!tokeKindSet.contains(token.getKind())) {
//                                log.info("User{}添加Token【{}】.", user, token);
//                                user.addToken(token);
//                            }
//                        }
//                    }
//                }
//            }
//        } else {
//            log.info("AuthenticationMethod 是 {} 或者已经init完成,无需初始化.", kerberosConf);
//        }
//    }
    
    /**
     * init 初始化Kerberos认证
     * @param user   Kerberos用户
     * @param keytab keytab文件
     * @param krb5   krb5文件
     */
    public static void init(@NonNull final String user,
                            @NonNull final String keytab,
                            final String krb5) {
        init(user, keytab, krb5, new Configuration());
    }
    
    /**
     * init 初始化Kerberos认证
     *
     * @param user   Kerberos用户
     * @param keytab keytab文件
     * @param krb5   krb5文件
     * @param conf   Hadoop的Configuration
     */
    public static void init(@NonNull final String user,
                            @NonNull final String keytab,
                            final String krb5,
                            @NonNull final Configuration conf) {
        
        final String krb5Input = StringUtils.isBlank(krb5) || !new File(krb5).isFile() ? System.getProperty(LoginUtil.JAVA_SECURITY_KRB5_CONF_KEY) : krb5;
        log.info("krb5 file is {} .", krb5Input);
        
        removeGUIByTime();
        
        final String kerberosConf = conf.get(HADOOP_SECURITY_AUTHENTICATION);
        if (!USER_DEQUE.isEmpty()) {
            log.info("Kerberos已经登录【" + USER_DEQUE.peekFirst() + "】.");
        } else if (KERBEROS_NAME.equalsIgnoreCase(kerberosConf)) {
            log.info("Kerberos登录开始.");
            try {
                // 设置客户端的keytab和krb5文件路径
                LoginUtil.setJaasConf(ZOOKEEPER_DEFAULT_LOGIN_CONTEXT_NAME, user, keytab);
                LoginUtil.setConf(ZOOKEEPER_SERVER_PRINCIPAL_KEY, ZOOKEEPER_DEFAULT_SERVER_PRINCIPAL);
                UserGroupInformation userGroupInformation = LoginUtil.login(user, keytab, krb5Input, conf);
                synchronized (USER_DEQUE) {
                    if (USER_DEQUE.isEmpty()) {
                        USER_DEQUE.addFirst(userGroupInformation);
                        long time = System.currentTimeMillis() / 1000;
                        ugiTime.put(userGroupInformation, time);
                    }
                }
            } catch (IOException e) {
                log.error(e.getMessage(), e);
                throw new RuntimeException(e);
            }
            log.info("Kerberos登录成功【" + USER_DEQUE.peekFirst() + "】.");
        } else {
            log.info("【{}】是【{}】, 无需登录.", HADOOP_SECURITY_AUTHENTICATION, kerberosConf);
        }
    }
    
    private static void removeGUIByTime() {
        Long currentTime = System.currentTimeMillis() / 1000;
        Iterator<UserGroupInformation> iter = ugiTime.keySet().iterator();
        while (iter.hasNext()) {
            UserGroupInformation gui = iter.next();
            Long lastTime = ugiTime.get(gui);// 获取上一次时间
            long minutes = (currentTime - lastTime) / 60;
            if (minutes >= 60) {
                log.info("--------------------删除超过12个小时的UserGroupInformation的连接------minutes-" + minutes);
                iter.remove();
                USER_DEQUE.remove(gui);
            }
        }
    }
    
    /**
     * 清除已经获取的UserGroupInformation,为重新连接用
     */
    public static void clear() {
        synchronized (USER_DEQUE) {
            USER_DEQUE.clear();
        }
    }
    
    /**
     * get 获取带Kerberos认证的连接
     *
     * @param supplier 连接提供的函数
     */
    public static <R extends AutoCloseable, E extends Exception> R get(ThrowableSupplier<R, E> supplier) throws E {
        removeGUIByTime();
        if (USER_DEQUE.size() > 0) {
            UserGroupInformation user = USER_DEQUE.peekFirst();
            try {
                log.info("使用Kerberos用户【" + user + "】登录.");
                return user.doAs((PrivilegedExceptionAction<R>) supplier::get);
            } catch (IOException | InterruptedException e) {
                log.error(e.getMessage(), e);
                throw new RuntimeException(e);
            }
        } else {
            return null;
        }
    }
}
