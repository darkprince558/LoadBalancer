import java.util.List;
import java.util.Vector;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class BackendRegistry {

    private final List<Integer> allBackendPorts;
    private final Vector<Integer> liveServers;
    private final ConcurrentHashMap<Integer, AtomicInteger> serverLoad;

    public BackendRegistry(List<Integer> ports) {
        this.allBackendPorts = ports;
        this.liveServers = new Vector<>(ports);
        this.serverLoad = new ConcurrentHashMap<>();
        for (int port : ports) serverLoad.put(port, new AtomicInteger(0));
    }

    public int getBestServer() throws Exception {
        if (liveServers.isEmpty()) throw new Exception("Service Unavailable: No servers available.");
        int selectedPort = -1;
        int minLoad = Integer.MAX_VALUE;

        // Least Connections Logic
        for (Integer port : liveServers) {
            int load = serverLoad.get(port).get();
            if (load < minLoad) {
                minLoad = load;
                selectedPort = port;
            }
        }
        return (selectedPort != -1) ? selectedPort : liveServers.get(0);
    }

    public void incrementLoad(int port) {
        serverLoad.get(port).incrementAndGet();
    }

    public void decrementLoad(int port) {
        serverLoad.get(port).decrementAndGet();
    }

    public List<Integer> getAllPorts() {
        return allBackendPorts;
    }

    public void markServerDown(int port) {
        if (liveServers.remove(Integer.valueOf(port))) {
            LoadBalancer.log("WARN", "Port " + port + " is unresponsive. Removing it from rotation.");
        }
    }

    public void markServerUp(int port) {
        if (!liveServers.contains(port)) {
            liveServers.add(port);
            serverLoad.get(port).set(0); // Reset load on revive
            LoadBalancer.log("INFO", "Port " + port + " was recovered. Adding it to rotation.");
        }
    }

    // Data Accessors for the Dashboard
    public boolean isLive(int port) {
        return liveServers.contains(port);
    }

    public int getActiveConnections(int port) {
        return serverLoad.get(port).get();
    }
}