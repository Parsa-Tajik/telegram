package telegramserver.sockets;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// real-time socket server that listens for user connections
public class SocketServer {
    private final int port;
    private final ExecutorService pool;

    public SocketServer(int port) {
        this.port = port;
        this.pool = Executors.newCachedThreadPool(); // Dynamically create threads
    }

    // Start of the socket server
    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("✅ Socket server started on port: " + port);

            while (true) {
                Socket client = serverSocket.accept();
                System.out.println("👉 New client connected: " + client.getInetAddress());

                // Start a thread for this client
                pool.execute(new ClientHandler(client));
            }

        } catch (IOException e) {
            System.err.println("❌ Server failed: " + e.getMessage());
        }
    }
}
