package telegramserver.sockets;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Accept loop: each TCP connection gets a ClientHandler thread.
 */
public class SocketServer {
    public static void start(int port) throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("🔌 Socket server running on port " + port);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ClientHandler(clientSocket)).start();
            }
        }
    }
}
