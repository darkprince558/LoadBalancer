import java.util.List;
import java.util.Vector;
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

    //Least Connections)
    public int getBestServer() throws Exception {
        if (liveServers.isEmpty()) {
            throw new Exception("No live servers available");
        }

        int selectedPort = -1;
        int minLoad = Integer.MAX_VALUE;

        for (Integer port : liveServers) {
            int load = serverLoad.get(port).get();
            if (load < minLoad) {
                minLoad = load;
                selectedPort = port;
            }
        }
        return (selectedPort != -1) ? selectedPort : liveServers.get(0);
    }

    // State Updates
    public void incrementLoad(int port) {
        serverLoad.get(port).incrementAndGet();
    }

    public void decrementLoad(int port) {
        serverLoad.get(port).decrementAndGet();
    }

    // Health Check Helpers
    public List<Integer> getAllPorts() { return allBackendPorts; }

    public void markServerDown(int port) {
        if (liveServers.contains(port)) {
            liveServers.remove(Integer.valueOf(port));
            System.out.println(">>> Server " + port + " is DOWN.");
        }
    }

    public void markServerUp(int port) {
        if (!liveServers.contains(port)) {
            liveServers.add(port);
            serverLoad.put(port, new AtomicInteger(0)); // Reset load on revive
            System.out.println(">>> Server " + port + " is UP.");
        }
    }

    public String getTotalLoad() {
        StringBuilder stats = new StringBuilder("STATUS REPORT: ");

        for (Integer port : allBackendPorts) {
            int count = serverLoad.get(port).get();
            stats.append("[Port ").append(port).append(": ").append(count).append(" users]  ");
        }
        return stats.toString();
    }



}