package telegramserver;

import telegramserver.sockets.SocketServer;

import java.io.IOException;

/**
 * MainServer: socket-only JSON server.
 * Starts only the socket listener (no HTTP).
 */
public class MainServer {
    public static void main(String[] args) {
        int port = 9090;
        System.out.println("Starting Telegram socket server on port " + port);
        try {
            SocketServer.start(port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
