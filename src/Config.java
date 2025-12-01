import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class Config {
    private static final Properties properties = new Properties();

    static {
        try (FileInputStream input = new FileInputStream("Config.properties")) {
            properties.load(input);
        } catch (IOException e) {
            System.err.println("Warning: Could not load Config.properties. Using defaults.");
        }
    }

    public static int getListenPort() {
        return Integer.parseInt(properties.getProperty("listen.port", "8080"));
    }

    public static String getBackendHost() {
        return properties.getProperty("backend.host", "localhost");
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