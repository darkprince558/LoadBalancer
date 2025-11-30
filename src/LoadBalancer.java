import java.io.*;
import java.net.*;
import java.util.*;


public class LoadBalancer {
    private static final int Listen_PORT = 8080;
    private static final String BACKEND_HOST = "localhost";
    private static final Vector<Integer> SERVERS = new Vector<>(Arrays.asList(9001, 9002));

    static void main() throws IOException {
        ServerSocket serverSocket = new ServerSocket(Listen_PORT);
        System.out.println("Load Balancer started on port " + Listen_PORT);
        System.out.println("Balancing traffic between ports: " + SERVERS);

        int currentServerIndex = 0;


        while (true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("Client connected");

            Socket backendSocket = null;
            int attempts = 0;



            while (backendSocket == null && attempts < SERVERS.size()) {
                if (SERVERS.isEmpty()) {
                    System.out.println("No servers available");
                }

                int backendPort = SERVERS.get(currentServerIndex);
                currentServerIndex = (currentServerIndex + 1) % SERVERS.size();

                try {
                    backendSocket = new Socket(BACKEND_HOST, backendPort);
                } catch (IOException e) {
                    System.err.println("Server on port " + backendPort + " is dead. Removing from rotation.");
                    SERVERS.remove(Integer.valueOf(backendPort));

                    if (currentServerIndex >= SERVERS.size()) currentServerIndex = 0;
                }
                attempts++;

            }

            if (backendSocket != null) {
                System.out.println("Forwarding to " + backendSocket.getPort());
                Thread clientToBackend = new Thread(new StreamPipe(clientSocket.getInputStream(), backendSocket.getOutputStream()));
                Thread backendToClient = new Thread(new StreamPipe(backendSocket.getInputStream(), clientSocket.getOutputStream()));
                clientToBackend.start();
                backendToClient.start();
            }
        }
    }
}
