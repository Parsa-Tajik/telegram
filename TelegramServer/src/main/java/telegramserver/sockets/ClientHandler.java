package telegramserver.sockets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import telegramserver.protocol.CommandProcessor;
import telegramserver.protocol.SocketProtocol;

import java.io.*;
import java.net.Socket;
import java.util.UUID;

/**
 * Handles one client connection. All communication is newline-terminated JSON.
 *
 * - Client requests must include "id" (UUID string), "type" and "payload".
 * - Server responses echo same "id" and use types like LOGIN_OK, LOGIN_FAIL, etc.
 * - Server events (pushes) use type=EVENT and have "event" key; they don't include id.
 *
 * Notes:
 * - On successful LOGIN we register the writer in ClientRegistry so other parties can receive events.
 * - We keep the writer registration responsibility here (ClientHandler) to hold actual BufferedWriter.
 */
public class ClientHandler implements Runnable {
    private final Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private String username; // set after successful login

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                try {
                    JsonElement je = JsonParser.parseString(line);
                    if (!je.isJsonObject()) {
                        sendRaw(SocketProtocol.buildResponse("ERROR", UUID.randomUUID().toString(),
                                java.util.Map.of("message", "invalid json")));
                        continue;
                    }
                    JsonObject req = je.getAsJsonObject();

                    // Expect "id" field in requests
                    if (!req.has("id")) {
                        sendRaw(SocketProtocol.buildResponse("ERROR", UUID.randomUUID().toString(),
                                java.util.Map.of("message", "missing id in request")));
                        continue;
                    }

                    String type = req.has("type") ? req.get("type").getAsString() : "UNKNOWN";

                    // If login succeeds CommandProcessor returns LOGIN_OK and includes username in response payload.
                    // We'll register writer in registry after sending LOGIN_OK.
                    String response = CommandProcessor.processRequest(req, username);
                    sendRaw(response);

                    // If login succeeded, pick username and register writer
                    try {
                        JsonObject respObj = JsonParser.parseString(response).getAsJsonObject();
                        String respType = respObj.has("type") ? respObj.get("type").getAsString() : "";
                        if ("LOGIN_OK".equalsIgnoreCase(respType)) {
                            // username may be returned in response payload or was in the request payload
                            if (respObj.has("username")) {
                                this.username = respObj.get("username").getAsString();
                            } else if (req.has("payload") && req.getAsJsonObject("payload").has("username")) {
                                this.username = req.getAsJsonObject("payload").get("username").getAsString();
                            }
                            if (this.username != null) {
                                ClientRegistry.addClient(this.username, writer);
                            }
                        }
                    } catch (Exception ignore) {
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    sendRaw(SocketProtocol.buildResponse("ERROR", UUID.randomUUID().toString(),
                            java.util.Map.of("message", ex.getMessage())));
                }
            }
        } catch (IOException e) {
            // connection closed or error
        } finally {
            if (username != null) {
                ClientRegistry.removeClient(username);
            }
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void sendRaw(String json) {
        try {
            writer.write(json + "\n");
            writer.flush();
        } catch (IOException e) {
            // writing failed: likely closed socket
            e.printStackTrace();
        }
    }
}
