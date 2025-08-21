package telegramserver;

import com.sun.net.httpserver.HttpServer;
import telegramserver.api.*;
import telegramserver.sockets.SocketServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

// Main entry point: starts REST API server + Socket server
public class MainServer {
    public static void main(String[] args) throws IOException {
        // Start REST API
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(8000), 0);

        httpServer.createContext("/register", new AuthAPI());
        httpServer.createContext("/login", new AuthAPI());
        httpServer.createContext("/users", new UserAPI());
        httpServer.createContext("/search/user", new SearchAPI());
        httpServer.createContext("/search/chat", new SearchAPI());
        httpServer.createContext("/channels/create", new ChannelAPI());
        httpServer.createContext("/channels/join", new ChannelAPI());
        httpServer.createContext("/channels", new ChannelAPI());

        httpServer.setExecutor(Executors.newCachedThreadPool());
        httpServer.start();
        System.out.println("🌍 REST API running at http://localhost:8000");

        // Start Socket server (real-time chat)
        new Thread(() -> {
            try {
                SocketServer.start(9090);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }
}
