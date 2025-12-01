import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class LoadBalancer {

    public static void main(String[] args) throws IOException {
        int port = Config.getListenPort();
        String host = Config.getBackendHost();

        BackendRegistry registry = new BackendRegistry(Config.getBackendPorts());

        startHealthCheck(registry, host);

        startStatsMonitor(registry);

        System.out.println("Load Balancer started on port " + port);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();

                try {
                    int backendPort = registry.getBestServer();
                    Socket backendSocket = new Socket(host, backendPort);
                    registry.incrementLoad(backendPort);

                    System.out.println("Routing to " + backendPort);

                    Thread clientToBackend = new Thread(() -> {
                        try {
                            new StreamPipe(clientSocket.getInputStream(), backendSocket.getOutputStream()).run();
                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            registry.decrementLoad(backendPort); // Clean up load
                            System.out.println("Closed connection to " + backendPort);
                            try { backendSocket.close(); } catch (IOException e) {}
                        }
                    });

                    Thread backendToClient = new Thread(() -> {
                        try {
                            new StreamPipe(backendSocket.getInputStream(), clientSocket.getOutputStream()).run();
                        } catch (IOException e) {
                        } finally {
                            try { clientSocket.close(); } catch (IOException e) {}
                        }
                    });

                    clientToBackend.start();
                    backendToClient.start();

                } catch (Exception e) {
                    System.err.println("Request failed: " + e.getMessage());
                    clientSocket.close();
                }
            }
        }
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
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private static void startStatsMonitor(BackendRegistry registry) {
        Thread monitor = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(Config.getStatsMonitorInterval());
                    System.out.println(registry.getTotalLoad());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        monitor.setDaemon(true);
        monitor.start();
    }

    private static boolean isServerAlive(String host, int port) {
        try (Socket s = new Socket(host, port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}