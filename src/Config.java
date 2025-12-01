import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Static configuration loader.
 * <p>
 * Reads settings from 'Config.properties' on startup. If the file cannot be loaded,
 * hardcoded default values are used to ensure the application still runs.
 */
public class Config {
    private static final Properties properties = new Properties();

    // Static block to load properties once when the class is first accessed
    static {
        try (FileInputStream input = new FileInputStream("Config.properties")) {
            properties.load(input);
        } catch (IOException e) {
            System.err.println("Warning: Could not load Config.properties. Using defaults.");
        }
    }

    /**
     * @return The port the Load Balancer listens on (Default: 8080).
     */
    public static int getListenPort() {
        return Integer.parseInt(properties.getProperty("listen.port", "8080"));
    }

    /**
     * @return The hostname of the backend servers (Default: localhost).
     */
    public static String getBackendHost() {
        return properties.getProperty("backend.host", "localhost");
    }

    /**
     * @return Interval in milliseconds for the health check pings (Default: 3000ms).
     */
    public static int getHealthCheckInterval() {
        return Integer.parseInt(properties.getProperty("health.check.interval", "3000"));
    }

    /**
     * Parses the comma-separated list of backend ports.
     *
     * @return A List of integers representing backend ports (Default: 9001, 9002).
     */
    public static List<Integer> getBackendPorts() {
        String raw = properties.getProperty("backend.servers", "9001,9002");
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .collect(Collectors.toList());
    }

    /**
     * @return Interval in milliseconds for printing the console report (Default: 5000ms).
     */
    public static int getStatsMonitorInterval() {
        return Integer.parseInt(properties.getProperty("stats.monitor.interval", "5000"));
    }
}