import java.io.*;

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
            // 4kb to carry packets
            byte[] buffer = new byte[4096];
            int bytesRead;

            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
                output.flush();
            }
        } catch (IOException _) {
        }
    }
}