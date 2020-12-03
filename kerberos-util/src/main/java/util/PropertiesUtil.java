package util;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

@Slf4j
public class PropertiesUtil {
    private PropertiesUtil() {
    }

    private static class SingletonHolder {
        private final static PropertiesUtil instance = new PropertiesUtil();
    }

    public static PropertiesUtil getInstance() {
        return SingletonHolder.instance;
    }

    /**
     * 读取key字段，配置文件在classes根路径下xx.properties，在子路径下xx/xx.properties
     *
     * @param file
     * @param key
     * @return
     */
    public String read(String file, String key) {
        Properties prop = new Properties();
        String value = "";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(file);
             BufferedReader bf = new BufferedReader(new InputStreamReader(in))) {
            prop.load(bf);
            value = prop.getProperty(key);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return value;
    }

    public Map<String, String> readConfigFile(String file) {
        Map<String, String> map = new HashMap<>();
        Properties prop = new Properties();
        String value = "";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(file);
             BufferedReader bf = new BufferedReader(new InputStreamReader(in))) {
            prop.load(bf);
            Set<Object> set = prop.keySet();
            for (Object obj : set) {
                map.put((String) obj, prop.getProperty((String) obj));
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return map;
    }


    public static void main(String[] args) throws IOException {
        String file = "config.properties";
        String key = "kerberos.user.principal.name";
        // 从配置文件读取key对应的value
        Map<String, String> map = PropertiesUtil.getInstance().readConfigFile(file);
        System.out.println(map.get(key));
    }
}
