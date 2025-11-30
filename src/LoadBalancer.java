import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadBalancer {
    private static final int Listen_PORT = 8080;
    private static final String BACKEND_HOST = "localhost";

    private static final List<Integer> SERVERS_LIST = Arrays.asList(9001, 9002);
    private static final Vector<Integer> LIVE_SERVERS = new Vector<>(SERVERS_LIST);
    private static ConcurrentHashMap<Integer, AtomicInteger> SERVER_LOAD = new ConcurrentHashMap<>();


    private static final int HEALTH_CHECK_TIME = 3000;

    public static void main(String[] args) throws IOException { // FIX 1: Added public
        ServerSocket serverSocket = new ServerSocket(Listen_PORT);

        // Initialize map with 0s
        for (int port: SERVERS_LIST) SERVER_LOAD.put(port, new AtomicInteger(0));

        startHealthCheck();

        System.out.println("Load Balancer started on port " + Listen_PORT);
        System.out.println("Using Least Connections");

        while (true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("Client connected");

            Socket backendSocket = null;
            boolean connected = false;
            int attempts = 0;

            while (!connected && attempts < LIVE_SERVERS.size()) {
                if (LIVE_SERVERS.isEmpty()) {
                    System.out.println("503 Service Unavailable: No live servers.");
                    clientSocket.close();
                    break;
                }

                int backendPort = getServerWithLeastConnections();

                try {
                    // FIX 2: Connect FIRST. If this fails, we go to catch and never increment.
                    backendSocket = new Socket(BACKEND_HOST, backendPort);

                    // Now it's safe to increment
                    SERVER_LOAD.get(backendPort).incrementAndGet();
                    System.out.println("Forwarding to " + backendPort + " | Current Load: " + SERVER_LOAD.get(backendPort));

                    final int portRef = backendPort;

                    Socket finalBackendSocket = backendSocket;
                    Thread clientToBackend = new Thread(() -> {
                        try {
                            new StreamPipe(clientSocket.getInputStream(), finalBackendSocket.getOutputStream()).run();
                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            int currentLoad = SERVER_LOAD.get(portRef).decrementAndGet();
                            System.out.println("Connection closed on " + portRef + " | Current Load: " + currentLoad);
                        }
                    });

                    Thread backendToClient = new Thread(new StreamPipe(backendSocket.getInputStream(), clientSocket.getOutputStream()));

                    clientToBackend.start();
                    backendToClient.start();

                    connected = true;

                } catch (IOException e) {
                    System.err.println("Connection failed to " + backendPort);
                    // We don't need to decrement here because we never incremented!
                }
                attempts++;
            }

            if (!connected) {
                System.out.println("Failed to connect to any backend.");
                clientSocket.close();
            }
        }
    }

    private static int getServerWithLeastConnections() {
        int selectedServer = -1;
        int minConnections = Integer.MAX_VALUE;
        for (Integer port : LIVE_SERVERS) {
            AtomicInteger connections = SERVER_LOAD.get(port);
            int currentConnections = (connections != null)? connections.get() : 0;
            if (currentConnections < minConnections) {
                minConnections = currentConnections;
                selectedServer = port;
            }
        }
        return (selectedServer != -1)? selectedServer : LIVE_SERVERS.get(0) ;
    }

    public static void startHealthCheck(){
        Thread healthCheckThread = new Thread(() ->
        {
            while(true){
                try {
                    Thread.sleep(HEALTH_CHECK_TIME);
                    for (int port: SERVERS_LIST){

                        if(isServerAlive(port)){
                            // CASE: Server is ALIVE
                            if (!LIVE_SERVERS.contains(port)){
                                System.out.println(">>> Server " + port + " is BACK ONLINE! Adding to rotation.");
                                LIVE_SERVERS.add(port);
                                SERVER_LOAD.put(port, new AtomicInteger(0));
                            }
                        } else {
                            // CASE: Server is DEAD (This else belongs to the outer if)
                            if (LIVE_SERVERS.contains(port)) {
                                System.out.println(">>> Server " + port + " is DOWN. Removing from rotation.");
                                LIVE_SERVERS.remove(Integer.valueOf(port));
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        );
        healthCheckThread.start();
        System.out.println("Health check thread started.");
    }

    private static boolean isServerAlive(int port){
        try {
            Socket socket = new Socket(BACKEND_HOST, port);
            socket.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}