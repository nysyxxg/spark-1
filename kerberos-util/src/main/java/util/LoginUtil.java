package util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.authentication.util.KerberosUtil;

import javax.security.auth.login.AppConfigurationEntry;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class LoginUtil {

    public static final String JAVA_SECURITY_KRB5_CONF_KEY = "java.security.krb5.conf";

    private static final String LOGIN_FAILED_CAUSE_PASSWORD_WRONG = "(wrong password) keytab file and user not match, you can kinit -k -t keytab user in client server to check";

    private static final String LOGIN_FAILED_CAUSE_TIME_WRONG = "(clock skew) time of local server and remote server not match, please check ntp to remote server";

    private static final String LOGIN_FAILED_CAUSE_AES256_WRONG = "(aes256 not support) aes256 not support by default jdk/jre, need copy local_policy.jar and US_export_policy.jar from remote server in path /opt/huawei/Bigdata/jdk/jre/lib/security";

    private static final String LOGIN_FAILED_CAUSE_PRINCIPAL_WRONG = "(no rule) principal format not support by default, need add property hadoop.security.auth_to_local(in core-site.xml) value RULE:[1:$1] RULE:[2:$1]";

    private static final String LOGIN_FAILED_CAUSE_TIME_OUT = "(time out) can not connect to kdc server or there is fire wall in the network";

    private static final boolean IS_IBM_JDK = System.getProperty("java.vendor").contains("IBM");

    public synchronized static UserGroupInformation login(String userPrincipal, String userKeytabPath, String krb5ConfPath, Configuration conf) throws IOException {
        // 1.check input parameters
        if (StringUtils.isBlank(userPrincipal)) {
            log.error("input userPrincipal is invalid.");
            throw new IOException("input userPrincipal is invalid.");
        }

        if (StringUtils.isBlank(userKeytabPath)) {
            log.error("input userKeytabPath is invalid.");
            throw new IOException("input userKeytabPath is invalid.");
        }

        if (StringUtils.isBlank(krb5ConfPath)) {
            log.error("input krb5ConfPath is invalid.");
            throw new IOException("input krb5ConfPath is invalid.");
        }

        if (conf == null) {
            log.error("input conf is invalid.");
            throw new IOException("input conf is invalid.");
        }

        // 2.check file exsits
        File userKeytabFile = new File(userKeytabPath);
        if (!userKeytabFile.exists()) {
            log.error("userKeytabFile(" + userKeytabFile.getAbsolutePath() + ") does not exsit.");
            throw new IOException("userKeytabFile(" + userKeytabFile.getAbsolutePath() + ") does not exsit.");
        }
        if (!userKeytabFile.isFile()) {
            log.error("userKeytabFile(" + userKeytabFile.getAbsolutePath() + ") is not a file.");
            throw new IOException("userKeytabFile(" + userKeytabFile.getAbsolutePath() + ") is not a file.");
        }

        File krb5ConfFile = new File(krb5ConfPath);
        if (!krb5ConfFile.exists()) {
            log.error("krb5ConfFile(" + krb5ConfFile.getAbsolutePath() + ") does not exsit.");
            throw new IOException("krb5ConfFile(" + krb5ConfFile.getAbsolutePath() + ") does not exsit.");
        }
        if (!krb5ConfFile.isFile()) {
            log.error("krb5ConfFile(" + krb5ConfFile.getAbsolutePath() + ") is not a file.");
            throw new IOException("krb5ConfFile(" + krb5ConfFile.getAbsolutePath() + ") is not a file.");
        }

        // 3.set and check krb5config
        setConf(JAVA_SECURITY_KRB5_CONF_KEY, krb5ConfFile.getAbsolutePath());
        setConfiguration(conf);

        // 4.login and check for hadoop
        log.info("Login success!!!!!!!!!!!!!!");
        return loginHadoop(userPrincipal, userKeytabFile.getAbsolutePath());
    }

    private static void setConfiguration(Configuration conf) {
        UserGroupInformation.setConfiguration(conf);
    }

    private static boolean checkNeedLogin(String principal) throws IOException {
        if (!UserGroupInformation.isSecurityEnabled()) {
            log.error("UserGroupInformation is not SecurityEnabled, please check if core-site.xml exists in classpath.");
            throw new IOException("UserGroupInformation is not SecurityEnabled, please check if core-site.xml exists in classpath.");
        }
        UserGroupInformation currentUser = UserGroupInformation.getCurrentUser();
        if ((currentUser != null) && (currentUser.hasKerberosCredentials())) {
            if (checkCurrentUserCorrect(principal)) {
                log.info("current user is " + currentUser + "has logined.");
                if (!currentUser.isFromKeytab()) {
                    log.error("current user is not from keytab.");
                    throw new IOException("current user is not from keytab.");
                }
                return false;
            } else {
                log.error("current user is " + currentUser + "has logined. please check your enviroment , especially when it used IBM JDK or kerberos for OS count login!!");
                throw new IOException("current user is " + currentUser + " has logined. And please check your enviroment!!");
            }
        }
        return true;
    }

    public static void setJaasConf(String loginContextName, String principal, String keytabFile) throws IOException {
        if (StringUtils.isBlank(loginContextName)) {
            log.error("input loginContextName is invalid.");
            throw new IOException("input loginContextName is invalid.");
        }

        if (StringUtils.isBlank(principal)) {
            log.error("input principal is invalid.");
            throw new IOException("input principal is invalid.");
        }

        if (StringUtils.isBlank(keytabFile)) {
            log.error("input keytabFile is invalid.");
            throw new IOException("input keytabFile is invalid.");
        }

        File userKeytabFile = new File(keytabFile);
        if (!userKeytabFile.exists()) {
            log.error("userKeytabFile(" + userKeytabFile.getAbsolutePath() + ") does not exsit.");
            throw new IOException("userKeytabFile(" + userKeytabFile.getAbsolutePath() + ") does not exsit.");
        }

        javax.security.auth.login.Configuration.setConfiguration(new JaasConfiguration(loginContextName, principal, userKeytabFile.getAbsolutePath()));

        javax.security.auth.login.Configuration conf = javax.security.auth.login.Configuration.getConfiguration();
        if (!(conf instanceof JaasConfiguration)) {
            log.error("javax.security.auth.login.Configuration is not JaasConfiguration.");
            throw new IOException("javax.security.auth.login.Configuration is not JaasConfiguration.");
        }

        AppConfigurationEntry[] entries = conf.getAppConfigurationEntry(loginContextName);
        if (entries == null) {
            log.error("javax.security.auth.login.Configuration has no AppConfigurationEntry named " + loginContextName + ".");
            throw new IOException("javax.security.auth.login.Configuration has no AppConfigurationEntry named " + loginContextName + ".");
        }

        boolean checkPrincipal = false;
        boolean checkKeytab = false;
        for (AppConfigurationEntry entry : entries) {
            if (entry.getOptions().get("principal").equals(principal)) {
                checkPrincipal = true;
            }
            if (IS_IBM_JDK) {
                if (entry.getOptions().get("useKeytab").equals(keytabFile)) {
                    checkKeytab = true;
                }
            } else {
                if (entry.getOptions().get("keyTab").equals(keytabFile)) {
                    checkKeytab = true;
                }
            }
        }

        if (!checkPrincipal) {
            log.error("AppConfigurationEntry named " + loginContextName + " does not have principal value of " + principal + ".");
            throw new IOException("AppConfigurationEntry named " + loginContextName + " does not have principal value of " + principal + ".");
        }

        if (!checkKeytab) {
            log.error("AppConfigurationEntry named " + loginContextName + " does not have keyTab value of " + keytabFile + ".");
            throw new IOException("AppConfigurationEntry named " + loginContextName + " does not have keyTab value of " + keytabFile + ".");
        }

    }

    public static void setConf(String confKey, String confValue) throws IOException {
        System.setProperty(confKey, confValue);
        String ret = System.getProperty(confKey);
        if (ret == null) {
            log.error(confKey + " is null.");
            throw new IOException(confKey + " is null.");
        }
        if (!ret.equals(confValue)) {
            log.error(confKey + " is " + ret + " is not " + confValue + ".");
            throw new IOException(confKey + " is " + ret + " is not " + confValue + ".");
        }
    }

    private static UserGroupInformation loginHadoop(String principal, String keytabFile) throws IOException {
        try {
            return UserGroupInformation.loginUserFromKeytabAndReturnUGI(principal, keytabFile);
        } catch (IOException e) {
            log.error("login failed with " + principal + " and " + keytabFile + ".");
            log.error("perhaps cause 1 is " + LOGIN_FAILED_CAUSE_PASSWORD_WRONG + ".");
            log.error("perhaps cause 2 is " + LOGIN_FAILED_CAUSE_TIME_WRONG + ".");
            log.error("perhaps cause 3 is " + LOGIN_FAILED_CAUSE_AES256_WRONG + ".");
            log.error("perhaps cause 4 is " + LOGIN_FAILED_CAUSE_PRINCIPAL_WRONG + ".");
            log.error("perhaps cause 5 is " + LOGIN_FAILED_CAUSE_TIME_OUT + ".");
            throw e;
        }
    }

    private static void checkAuthenticateOverKrb() throws IOException {
        UserGroupInformation loginUser = UserGroupInformation.getLoginUser();
        UserGroupInformation currentUser = UserGroupInformation.getCurrentUser();
        if (loginUser == null) {
            log.error("current user is " + currentUser + ", but loginUser is null.");
            throw new IOException("current user is " + currentUser + ", but loginUser is null.");
        }
        if (!loginUser.equals(currentUser)) {
            log.error("current user is " + currentUser + ", but loginUser is " + loginUser + ".");
            throw new IOException("current user is " + currentUser + ", but loginUser is " + loginUser + ".");
        }
        if (!loginUser.hasKerberosCredentials()) {
            log.error("current user is " + currentUser + " has no Kerberos Credentials.");
            throw new IOException("current user is " + currentUser + " has no Kerberos Credentials.");
        }
        if (!UserGroupInformation.isLoginKeytabBased()) {
            log.error("current user is " + currentUser + " is not Login Keytab Based.");
            throw new IOException("current user is " + currentUser + " is not Login Keytab Based.");
        }
    }

    private static boolean checkCurrentUserCorrect(String principal) throws IOException {
        UserGroupInformation ugi = UserGroupInformation.getCurrentUser();
        if (ugi == null) {
            log.error("current user still null.");
            throw new IOException("current user still null.");
        }

        String defaultRealm;
        try {
            defaultRealm = KerberosUtil.getDefaultRealm();
        } catch (Exception e) {
            log.warn("getDefaultRealm failed.");
            throw new IOException(e);
        }

        if (StringUtils.isNotBlank(defaultRealm)) {
            StringBuilder realm = new StringBuilder();
            StringBuilder principalWithRealm = new StringBuilder();
            realm.append("@").append(defaultRealm);
            if (!principal.endsWith(realm.toString())) {
                principalWithRealm.append(principal).append(realm);
                principal = principalWithRealm.toString();
            }
        }

        return principal.equals(ugi.getUserName());
    }

    /**
     * copy from hbase zkutil 0.94&0.98 A JAAS configuration that defines the login modules that we want to use for
     * login.
     */
    private static class JaasConfiguration extends javax.security.auth.login.Configuration {
        private static final Map<String, String> BASIC_JAAS_OPTIONS = new HashMap<>();

        static {
            String jaasEnvVar = System.getenv("HBASE_JAAS_DEBUG");
            if (Boolean.TRUE.toString().equalsIgnoreCase(jaasEnvVar)) {
                BASIC_JAAS_OPTIONS.put("debug", Boolean.TRUE.toString());
            }
        }

        private static final Map<String, String> KEYTAB_KERBEROS_OPTIONS = new HashMap<>();

        static {
            if (IS_IBM_JDK) {
                KEYTAB_KERBEROS_OPTIONS.put("credsType", "both");
            } else {
                KEYTAB_KERBEROS_OPTIONS.put("useKeyTab", Boolean.TRUE.toString());
                KEYTAB_KERBEROS_OPTIONS.put("useTicketCache", "false");
                KEYTAB_KERBEROS_OPTIONS.put("doNotPrompt", Boolean.TRUE.toString());
                KEYTAB_KERBEROS_OPTIONS.put("storeKey", Boolean.TRUE.toString());
            }

            KEYTAB_KERBEROS_OPTIONS.putAll(BASIC_JAAS_OPTIONS);
        }


        private static final AppConfigurationEntry KEYTAB_KERBEROS_LOGIN = new AppConfigurationEntry(KerberosUtil.getKrb5LoginModuleName(), AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, KEYTAB_KERBEROS_OPTIONS);

        private static final AppConfigurationEntry[] KEYTAB_KERBEROS_CONF = new AppConfigurationEntry[]{KEYTAB_KERBEROS_LOGIN};

        private javax.security.auth.login.Configuration baseConfig;

        private final String loginContextName;

        private final boolean useTicketCache;

        private final String keytabFile;

        private final String principal;


        public JaasConfiguration(String loginContextName, String principal, String keytabFile) {
            this(loginContextName, principal, keytabFile, StringUtils.isBlank(keytabFile));
        }

        private JaasConfiguration(String loginContextName, String principal, String keytabFile, boolean useTicketCache) {
            try {
                this.baseConfig = javax.security.auth.login.Configuration.getConfiguration();
            } catch (SecurityException e) {
                this.baseConfig = null;
            }
            this.loginContextName = loginContextName;
            this.useTicketCache = useTicketCache;
            this.keytabFile = keytabFile;
            this.principal = principal;

            initKerberosOption();
            log.info("JaasConfiguration loginContextName=" + loginContextName + " principal=" + principal
                    + " useTicketCache=" + useTicketCache + " keytabFile=" + keytabFile);
        }

        private void initKerberosOption() {
            if (!useTicketCache) {
                if (IS_IBM_JDK) {
                    KEYTAB_KERBEROS_OPTIONS.put("useKeytab", keytabFile);
                } else {
                    KEYTAB_KERBEROS_OPTIONS.put("keyTab", keytabFile);
                    KEYTAB_KERBEROS_OPTIONS.put("useKeyTab", "true");
                    KEYTAB_KERBEROS_OPTIONS.put("useTicketCache", Boolean.FALSE.toString());
                }
            }
            KEYTAB_KERBEROS_OPTIONS.put("principal", principal);
        }

        public AppConfigurationEntry[] getAppConfigurationEntry(String appName) {
            if (loginContextName.equals(appName)) {
                return KEYTAB_KERBEROS_CONF;
            }
            if (baseConfig != null)
                return baseConfig.getAppConfigurationEntry(appName);
            return (null);
        }
    }
}
