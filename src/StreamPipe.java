import java.io.*;

/**
 * A generic runnable task that transfers data between an InputStream and an OutputStream.
 * <p>
 * This is used to bridge the client's connection to the backend server's connection.
 * It uses a 4KB buffer to chunk data.
 */
public class StreamPipe implements Runnable {
    private final InputStream input;
    private final OutputStream output;

    public StreamPipe(InputStream input, OutputStream output) {
        this.input = input;
        this.output = output;
    }

    @Override
    public void run() {
        try {
            // 4KB buffer to carry packets between streams
            byte[] buffer = new byte[4096];
            int bytesRead;

            // Read from source and write to destination until End of Stream (-1)
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
                output.flush(); // Ensure data is sent immediately
            }
        } catch (IOException _) {
            // IOExceptions usually occur here when the connection closes; we suppress them
            // because the thread will naturally exit, which is the desired behavior.
        }
    }
}