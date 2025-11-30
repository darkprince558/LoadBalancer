import java.io.*;
import java.net.*;
import java.util.*;


public class LoadBalancer {
    private static final int Listen_PORT = 8080;
    private static final String BACKEND_HOST = "localhost";

    private static final List<Integer> SERVERS_LIST = Arrays.asList(9001, 9002);
    private static final Vector<Integer> LIVE_SERVERS = new Vector<>(SERVERS_LIST);

    private static final int HEALTH_CHECK_TIME = 500;

    static void main() throws IOException {
        ServerSocket serverSocket = new ServerSocket(Listen_PORT);
        startHealthCheck();

        System.out.println("Load Balancer started on port " + Listen_PORT);
        System.out.println("Balancing traffic between ports: " + LIVE_SERVERS);

        int currentServerIndex = 0;


        while (true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("Client connected");

            Socket backendSocket = null;
            int attempts = 0;



            while (true) {
                if (LIVE_SERVERS.isEmpty()) {
                    System.out.println("503 Service Unavailable: No live servers.");
                    clientSocket.close();
                    continue;
                }

                // Fix index if it went out of bounds (because the list shrank)
                if (currentServerIndex >= LIVE_SERVERS.size()) currentServerIndex = 0;


                int backendPort = LIVE_SERVERS.get(currentServerIndex);
                currentServerIndex = (currentServerIndex + 1) % LIVE_SERVERS.size();

                try {
                    backendSocket = new Socket(BACKEND_HOST, backendPort);
                    Thread clientToBackend = new Thread(new StreamPipe(clientSocket.getInputStream(), backendSocket.getOutputStream()));
                    Thread backendToClient = new Thread(new StreamPipe(backendSocket.getInputStream(), clientSocket.getOutputStream()));
                    clientToBackend.start();
                    backendToClient.start();
                    System.out.println("Forwarding to " + backendSocket.getPort());
                } catch (IOException e) {
                    System.err.println("Connection failed to " + backendPort);
                    clientSocket.close();
                }
                attempts++;

            }
        }
    }

    public static void startHealthCheck(){
        Thread healthCheckThread = new Thread(() ->
        {
            while(true){
                try {
                    Thread.sleep(HEALTH_CHECK_TIME);
                    for (int port: SERVERS_LIST){
                        if(isServerAlive(port)){
                            if (!LIVE_SERVERS.contains(port)){
                                System.out.println(">>> Server " + port + " is BACK ONLINE! Adding to rotation.");
                                LIVE_SERVERS.add(port);}
                            else {
                                if (LIVE_SERVERS.contains(port)) {
                                    System.out.println(">>> Server " + port + " is DOWN. Removing from rotation.");
                                    LIVE_SERVERS.remove(Integer.valueOf(port));
                                }
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
