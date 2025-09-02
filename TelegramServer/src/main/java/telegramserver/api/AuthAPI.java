package telegramserver.sockets;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.io.BufferedWriter;

// Tracks online users + their socket connections
public class ClientRegistry {
    private static final Map<String, BufferedWriter> clients = new ConcurrentHashMap<>();

    public static void addClient(String username, BufferedWriter writer) {
        clients.put(username, writer);
        System.out.println("👤 User added to registry: " + username);
    }

    public static void removeClient(String username) {
        clients.remove(username);
        System.out.println("❌ User removed from registry: " + username);
    }

    public static BufferedWriter getWriter(String username) {
        return clients.get(username);
    }

    public static Map<String, BufferedWriter> getClients() {
        return clients;
    }
}
