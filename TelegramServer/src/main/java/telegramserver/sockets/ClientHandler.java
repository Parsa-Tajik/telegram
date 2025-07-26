package telegramserver.sockets;

import telegramserver.models.Message;
import telegramserver.services.UserService;

import java.io.*;
import java.net.Socket;

// Handles communication with one client (on their own thread)
public class ClientHandler implements Runnable {
    private final Socket clientSocket;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    @Override
    public void run() {
        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()))
        ) {
            String inputLine;

            while ((inputLine = reader.readLine()) != null) {
                System.out.println("📩 Client sent: " + inputLine);

                // 🔐 TODO: Parse the command (like LOGIN, SEND, etc.)
                // For now we just echo the message
                writer.write("Server received: " + inputLine + "\n");
                writer.flush();
            }

        } catch (IOException e) {
            System.out.println("⚠️ Client disconnected.");
        }
    }
}
