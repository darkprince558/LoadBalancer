import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * The main entry point for the Layer 4 Load Balancer.
 * <p>
 * This class handles:
 * <ul>
 * <li>Initializing the {@link BackendRegistry} with server configurations.</li>
 * <li>Starting background daemon threads for health checking and statistics monitoring.</li>
 * <li>Listening on a configured port for incoming client connections.</li>
 * <li>Dispatching client requests to backend servers using a thread-per-connection model.</li>
 * </ul>
 */
public class LoadBalancer {

    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Starts the Load Balancer.
     *
     * @param args Command line arguments (not currently used).
     * @throws IOException If the ServerSocket cannot bind to the port.
     */
    public static void main(String[] args) throws IOException {
        int port = Config.getListenPort();
        String host = Config.getBackendHost();

        // Initialize the registry which maintains the state of all backend servers
        BackendRegistry registry = new BackendRegistry(Config.getBackendPorts());

        // Start background tasks
        startHealthCheck(registry, host);
        startStatsMonitor(registry);
        printInitialInfo(port);

        // Main Loop: Accept incoming connections
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                try {
                    // Blocking call waits for a new client
                    Socket clientSocket = serverSocket.accept();
                    // Hand off the connection to a handler method immediately to avoid blocking the main loop
                    handleSession(clientSocket, registry, host);
                } catch (IOException e) {
                    log("ERROR", "Accept failed: " + e.getMessage());
                }
            }
        }
    }

    private static void printInitialInfo(int port) {
        log("INFO", "Load Balancer engine starting on port " + port);
        log("INFO", "Health Check: Active every " + Config.getHealthCheckInterval() + "ms");
        log("INFO", "Reports: Printing every " + Config.getStatsMonitorInterval() + "ms");
    }

    /**
     * Handles a single client session by establishing a bridge to a backend server.
     *
     * @param clientSocket The socket connected to the client.
     * @param registry     The registry used to select the best available backend.
     * @param backendHost  The host address for backend servers.
     */
    private static void handleSession(Socket clientSocket, BackendRegistry registry, String backendHost) {
        try {
            // Select the best server based on the load balancing algorithm (Least Connections)
            int backendPort = registry.getBestServer();
            Socket backendSocket = new Socket(backendHost, backendPort);

            // Update load metrics
            registry.incrementLoad(backendPort);
            log("TRAFFIC", "Routed Client -> Backend:" + backendPort);

            // Create two threads to pipe data bi-directionally:
            // 1. Backend -> Client (Response) - Decrements load upon completion
            startPipe(backendSocket, clientSocket, () -> {
                registry.decrementLoad(backendPort);
                log("TRAFFIC", "Closed Session -> Backend:" + backendPort);
            });

            // 2. Client -> Backend (Request)
            startPipe(clientSocket, backendSocket, null);

        } catch (Exception e) {
            log("ERROR", "Routing failed: " + e.getMessage());
            closeQuietly(clientSocket);
        }
    }

    /**
     * Starts a thread to pipe data from an input socket to an output socket.
     *
     * @param inputSocket  The source socket.
     * @param outputSocket The destination socket.
     * @param task         Optional Runnable to execute when the pipe closes (e.g., decrementing load).
     */
    private static void startPipe(Socket inputSocket, Socket outputSocket, Runnable task) {
        new Thread(() -> {
            try {
                // Determine the stream direction and run the pipe
                new StreamPipe(inputSocket.getInputStream(), outputSocket.getOutputStream()).run();
            } catch (IOException e) {
                log("ERROR", "Pipe failed: " + e.getMessage());
            } finally {
                // Ensure resources are released and post-actions (like load updates) are fired
                if (task != null) task.run();
                closeQuietly(inputSocket);
                closeQuietly(outputSocket);
            }
        }).start();
    }

    /**
     * Helper to log messages with a timestamp.
     */
    public static void log(String level, String message) {
        System.out.printf("[%s] [%-7s] %s%n", dtf.format(LocalDateTime.now()), level, message);
    }

    /**
     * Null-safe socket closer to suppress IOExceptions during cleanup.
     */
    private static void closeQuietly(Socket s) {
        try { if (s != null) s.close(); } catch (IOException _) {}
    }

    /**
     * Starts a daemon thread that prints system statistics to the console at a set interval.
     */
    private static void startStatsMonitor(BackendRegistry registry) {
        Thread monitor = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(Config.getStatsMonitorInterval());

                    System.out.println("\n");
                    System.out.println("==================================================");
                    System.out.println("                    SYSTEM REPORT                 ");
                    System.out.println("==================================================");
                    System.out.printf("| %-10s | %-10s | %-18s |%n", "PORT", "STATUS", "CONNECTIONS");
                    System.out.println("--------------------------------------------------");

                    for (int p : registry.getAllPorts()) {
                        String status = registry.isLive(p) ? "ONLINE" : "OFFLINE";
                        int count = registry.getActiveConnections(p);
                        System.out.printf("| %-10d | %-10s | %-18d |%n", p, status, count);
                    }
                    System.out.println("==================================================\n");

                } catch (InterruptedException e) { e.printStackTrace(); }
            }
        });
        monitor.setDaemon(true); // Ensure thread dies when main application stops
        monitor.start();
    }

    /**
     * Starts a daemon thread that actively pings backend servers to check for connectivity.
     * If a server is found dead, it is removed from rotation. If it recovers, it is added back.
     */
    private static void startHealthCheck(BackendRegistry registry, String host) {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(Config.getHealthCheckInterval());
                    for (int port : registry.getAllPorts()) {
                        if (isServerAlive(host, port)) registry.markServerUp(port);
                        else registry.markServerDown(port);
                    }
                } catch (InterruptedException e) { e.printStackTrace(); }
            }
        }).start();
    }

    /**
     * Attempts to open a socket connection to a specific port to verify uptime.
     */
    private static boolean isServerAlive(String host, int port) {
        try (Socket s = new Socket(host, port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}