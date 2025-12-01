import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Integer.parseInt;

public class Config {
    private static final Properties properties = new Properties();
    static {
        try {
            properties.load(new FileInputStream("Config.properties"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static int getListenPort() {
        return parseInt(properties.getProperty("Listen_PORT", "8080"));
    }

    public static String getBackendHost() {
        return properties.getProperty("BACKEND_HOST", "localhost");
    }

    public static int getHealthCheckInterval() {
        return Integer.parseInt(properties.getProperty("health.check.interval", "3000"));
    }

    public static List<Integer> getBackendPorts() {
        String raw = properties.getProperty("backend.servers", "9001,9002");
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .collect(Collectors.toList());
    }


    public static int getStatsMonitorInterval() {
        return Integer.parseInt(properties.getProperty("stats.monitor.interval", "5000"));
    }
}
