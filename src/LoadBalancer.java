import java.io.*;
import java.net.*;
import java.nio.channels.Pipe;

public class LoadBalancer {
    private static final int Listen_PORT = 8080;
    private static final String BACKEND_HOST = "localhost";
    private static final int BACKEND_PORT = 9001;

    static void main() throws IOException {
        ServerSocket serverSocket = new ServerSocket(Listen_PORT);
        System.out.println("Server started on port " + Listen_PORT);

        while (true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("Client connected");

            // connected to a backend server
            Socket backendSocket = new Socket(BACKEND_HOST, BACKEND_PORT);

            // Client to backend
            Thread clientToBackend = new Thread(new StreamPipe(clientSocket.getInputStream(), backendSocket.getOutputStream()));

            // Backend to client
            Thread backendToClient = new Thread(new StreamPipe(backendSocket.getInputStream(), clientSocket.getOutputStream()));

            clientToBackend.start();
            backendToClient.start();

        }
        }



    }
