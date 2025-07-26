package telegramserver;

import com.sun.net.httpserver.HttpServer;
import telegramserver.api.AuthAPI;
import telegramserver.sockets.SocketServer;

import java.io.IOException;
import java.net.InetSocketAddress;

public class MainServer {
    public static void main(String[] args) {
        int socketPort = 12345;
        int apiPort = 8000;

        // Start socket server (real-time chat)
        SocketServer socketServer = new SocketServer(socketPort);
        new Thread(socketServer::start).start();

        try {
            // Start HTTP server for REST APIs
            HttpServer httpServer = HttpServer.create(new InetSocketAddress(apiPort), 0);
            httpServer.createContext("/register", new AuthAPI());
            httpServer.createContext("/login", new AuthAPI());

            httpServer.setExecutor(null); // uses default thread pool
            httpServer.start();
            System.out.println("✅ HTTP API server started on port: " + apiPort);
        } catch (IOException e) {
            System.err.println("❌ Failed to start HTTP server: " + e.getMessage());
        }
    }
}
