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

        // Auth
        httpServer.createContext("/register", new AuthAPI());
        httpServer.createContext("/login", new AuthAPI());

        // User
        httpServer.createContext("/users", new UserAPI());

        // Search
        httpServer.createContext("/search/user", new SearchAPI());
        httpServer.createContext("/search/chat", new SearchAPI());

        // Channels
        httpServer.createContext("/channels/create", new ChannelAPI());
        httpServer.createContext("/channels/join", new ChannelAPI());
        httpServer.createContext("/channels", new ChannelAPI());

        //Groups
        httpServer.createContext("/groups/create", new GroupAPI());
        httpServer.createContext("/groups/join", new GroupAPI());
        httpServer.createContext("/groups/leave", new GroupAPI());
        httpServer.createContext("/groups", new GroupAPI());

        //PV
        httpServer.createContext("/pv/start", new PVAPI());
        httpServer.createContext("/pv", new PVAPI());


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
