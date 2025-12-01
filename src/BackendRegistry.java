import java.util.List;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages the state of backend servers including their availability and current load.
 * <p>
 * This class is Thread-Safe. It uses {@link ConcurrentHashMap} for load tracking and
 * {@link Vector} for the list of live servers to support concurrent reads/writes
 * from the main thread, health check thread, and stats monitor thread.
 */
public class BackendRegistry {

    // Read-only list of all configured ports (used for health checks)
    private final List<Integer> allBackendPorts;

    // Thread-safe list of currently active (healthy) servers
    private final Vector<Integer> liveServers;

    // Thread-safe map storing the number of active connections per server port
    private final ConcurrentHashMap<Integer, AtomicInteger> serverLoad;

    public BackendRegistry(List<Integer> ports) {
        this.allBackendPorts = ports;
        this.liveServers = new Vector<>(ports);
        this.serverLoad = new ConcurrentHashMap<>();
        // Initialize load counters to 0
        for (int port : ports) serverLoad.put(port, new AtomicInteger(0));
    }

    /**
     * Selects the best available server using the "Least Connections" strategy.
     *
     * @return The port number of the server with the fewest active connections.
     * @throws Exception If no servers are currently available (all down).
     */
    public int getBestServer() throws Exception {
        if (liveServers.isEmpty()) throw new Exception("Service Unavailable: No servers available.");

        int selectedPort = -1;
        int minLoad = Integer.MAX_VALUE;

        // Iterate over live servers to find the one with the lowest load
        for (Integer port : liveServers) {
            int load = serverLoad.get(port).get();
            if (load < minLoad) {
                minLoad = load;
                selectedPort = port;
            }
        }
        // Fallback to the first available server if selection fails logic
        return (selectedPort != -1) ? selectedPort : liveServers.getFirst();
    }

    /**
     * Atomically increments the active connection count for a specific port.
     */
    public void incrementLoad(int port) {
        serverLoad.get(port).incrementAndGet();
    }

    /**
     * Atomically decrements the active connection count for a specific port.
     */
    public void decrementLoad(int port) {
        serverLoad.get(port).decrementAndGet();
    }

    public List<Integer> getAllPorts() {
        return allBackendPorts;
    }

    /**
     * Marks a server as unavailable, removing it from the rotation pool.
     */
    public void markServerDown(int port) {
        // remove() returns true if the element was present
        if (liveServers.remove(Integer.valueOf(port))) {
            LoadBalancer.log("WARN", "Port " + port + " is unresponsive. Removing it from rotation.");
        }
    }

    /**
     * Marks a server as available, adding it back to the rotation pool.
     * Also resets its load counter to ensure it receives traffic immediately.
     */
    public void markServerUp(int port) {
        if (!liveServers.contains(port)) {
            liveServers.add(port);
            serverLoad.get(port).set(0); // Reset load on revive to prevent stale data
            LoadBalancer.log("INFO", "Port " + port + " was recovered. Adding it to rotation.");
        }
    }

    // --- Data Accessors for the Dashboard ---

    public boolean isLive(int port) {
        return liveServers.contains(port);
    }

    public int getActiveConnections(int port) {
        return serverLoad.get(port).get();
    }
}