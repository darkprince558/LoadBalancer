import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LoadBalancer {

    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static void main(String[] args) throws IOException {
        int port = Config.getListenPort();
        String host = Config.getBackendHost();

        BackendRegistry registry = new BackendRegistry(Config.getBackendPorts());
        startHealthCheck(registry, host);
        startStatsMonitor(registry);

        log("INFO", "Load Balancer engine starting on port " + port);
        log("INFO", "Strategy: Least Connections");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();

                try {
                    int backendPort = registry.getBestServer();
                    Socket backendSocket = new Socket(host, backendPort);

                    registry.incrementLoad(backendPort);
                    log("TRAFFIC", "Routed Client -> Backend:" + backendPort);

                    Thread clientToBackend = new Thread(() -> {
                        try {
                            new StreamPipe(clientSocket.getInputStream(), backendSocket.getOutputStream()).run();
                        } catch (IOException e) {
                        } finally {
                            registry.decrementLoad(backendPort);
                            log("TRAFFIC", "Closed Session -> Backend:" + backendPort);
                            closeQuietly(backendSocket);
                        }
                    });

                    Thread backendToClient = new Thread(() -> {
                        try {
                            new StreamPipe(backendSocket.getInputStream(), clientSocket.getOutputStream()).run();
                        } catch (IOException e) {
                        } finally {
                            closeQuietly(clientSocket);
                        }
                    });

                    clientToBackend.start();
                    backendToClient.start();

                } catch (Exception e) {
                    log("ERROR", "Routing failed: " + e.getMessage());
                    clientSocket.close();
                }
            }
        }
    }

    public static void log(String level, String message) {
        System.out.printf("[%s] [%-7s] %s%n", dtf.format(LocalDateTime.now()), level, message);
    }

    private static void closeQuietly(Socket s) {
        try { if (s != null) s.close(); } catch (IOException e) {}
    }

    private static void startStatsMonitor(BackendRegistry registry) {
        Thread monitor = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(Config.getStatsMonitorInterval());

                    System.out.println("\n");
                    System.out.println("==================================================");
                    System.out.println("                SYSTEM REPORT                     ");
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
        monitor.setDaemon(true);
        monitor.start();
    }

    private static void startHealthCheck(BackendRegistry registry, String host) {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(Config.getHealthCheckInterval());
                    for (int port : registry.getAllPorts()) {
                        if (isServerAlive(host, port)) {
                            registry.markServerUp(port);
                        } else {
                            registry.markServerDown(port);
                        }
                    }
                } catch (InterruptedException e) { e.printStackTrace(); }
            }
        }).start();
    }

    private static boolean isServerAlive(String host, int port) {
        try (Socket s = new Socket(host, port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}