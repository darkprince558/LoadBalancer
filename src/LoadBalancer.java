import java.io.*;
import java.net.*;
import java.util.*;


public class LoadBalancer {
    private static final int Listen_PORT = 8080;
    private static final String BACKEND_HOST = "localhost";
    private static final List<Integer> SERVERS = Arrays.asList(9001, 9002);


    static void main() throws IOException {
        ServerSocket serverSocket = new ServerSocket(Listen_PORT);
        System.out.println("Server started on port " + Listen_PORT);
        System.out.println("Balancing traffic between ports: " + SERVERS);

        int currentServerIndex = 0;


        while (true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("Client connected");

            // connected to a server using round robin
            int backendPort = SERVERS.get(currentServerIndex);
            System.out.println("Request received. Forwarding to Server on Port: " + backendPort);
            currentServerIndex = (currentServerIndex + 1) % SERVERS.size();


            try {
                Socket backendSocket = new Socket(BACKEND_HOST, backendPort);

                // Start the Pipes
                Thread clientToBackend = new Thread(new StreamPipe(clientSocket.getInputStream(), backendSocket.getOutputStream()));
                Thread backendToClient = new Thread(new StreamPipe(backendSocket.getInputStream(), clientSocket.getOutputStream()));

                clientToBackend.start();
                backendToClient.start();

            } catch (IOException e) {
                // If a backend server is dead, this prevents the Load Balancer from crashing
                System.err.println("Failed to connect to backend port " + backendPort);
                clientSocket.close();
            }
        }


    }
}
