package telegramserver.sockets;

import com.google.gson.Gson;
import telegramserver.models.Message;
import telegramserver.services.ChatService;
import telegramserver.services.ChannelService;
import telegramserver.services.MessageService;

import java.io.*;
import java.net.Socket;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private String username;
    private static final Gson gson = new Gson();

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
                handleRequest(line);
            }
        } catch (IOException | SQLException e) {
            e.printStackTrace();
        } finally {
            if (username != null) {
                ClientRegistry.removeClient(username);
            }
        }
    }

    private void handleRequest(String line) throws IOException, SQLException {
        Map<String, Object> request = gson.fromJson(line, Map.class);
        String type = (String) request.get("type");
        String id = (String) request.get("id"); // request id from client
        Map<String, Object> payload = (Map<String, Object>) request.get("payload");

        switch (type) {
            case "LOGIN":
                username = (String) payload.get("username");
                ClientRegistry.addClient(username, writer);
                sendResponse(id, "success", Map.of("message", "Logged in as " + username));
                break;

            case "JOIN_CHAT":
                int chatId = ((Double) payload.get("chatId")).intValue();
                ChatService.joinChat(chatId, username);
                sendResponse(id, "success", Map.of("chatId", chatId));
                break;

            case "LEAVE_CHAT":
                chatId = ((Double) payload.get("chatId")).intValue();
                ChatService.leaveChat(chatId, username);
                sendResponse(id, "success", Map.of("chatId", chatId));
                break;

            case "SEND_MESSAGE":
                chatId = ((Double) payload.get("chatId")).intValue();
                String content = (String) payload.get("content");
                Message msg = new Message(0, content, 0, chatId, 0,
                        new Timestamp(System.currentTimeMillis()), false, false);

                MessageService.saveMessage(msg);

                // Broadcast event to all chat members
                for (String member : ChatService.getMembers(chatId)) {
                    BufferedWriter w = ClientRegistry.getWriter(member);
                    if (w != null) {
                        sendEvent(w, "NEW_MESSAGE", Map.of(
                                "chatId", chatId,
                                "from", username,
                                "content", content
                        ));
                    }
                }
                sendResponse(id, "success", Map.of("message", "Message delivered"));
                break;

            case "CREATE_CHANNEL":
                String channelName = (String) payload.get("name");
                String description = (String) payload.get("description");
                boolean isPublic = (Boolean) payload.get("isPublic");
                int channelId = ChannelService.createChannel(channelName, description, isPublic);
                sendResponse(id, "success", Map.of("channelId", channelId));
                break;

            case "JOIN_CHANNEL":
                int channelIdToJoin = ((Double) payload.get("channelId")).intValue();
                int userId = ((Double) payload.get("userId")).intValue();
                boolean isAdmin = (Boolean) payload.get("isAdmin");
                boolean isPublicJoin = (Boolean) payload.get("isPublicJoin");
                Timestamp joinedAt = new Timestamp(System.currentTimeMillis());
                ChannelService.joinChannel(username, channelIdToJoin, channelIdToJoin, userId, joinedAt, isAdmin, isPublicJoin);
                sendResponse(id, "success", Map.of("channelId", channelIdToJoin));
                break;

            case "SEND_CHANNEL_MESSAGE":
                int chId = ((Double) payload.get("channelId")).intValue();
                String msgContent = (String) payload.get("content");

                for (String member : ChannelService.getMembers(chId)) {
                    BufferedWriter w = ClientRegistry.getWriter(member);
                    if (w != null) {
                        sendEvent(w, "NEW_CHANNEL_MESSAGE", Map.of(
                                "channelId", chId,
                                "from", username,
                                "content", msgContent
                        ));
                    }
                }
                sendResponse(id, "success", Map.of("message", "Channel message delivered"));
                break;

            default:
                sendResponse(id, "error", Map.of("message", "Unknown command: " + type));
        }
    }

    private void sendResponse(String id, String status, Map<String, Object> payload) throws IOException {
        Map<String, Object> response = Map.of(
                "id", id,
                "status", status,
                "payload", payload
        );
        writer.write(gson.toJson(response) + "\n");
        writer.flush();
    }

    private void sendEvent(BufferedWriter w, String eventType, Map<String, Object> payload) throws IOException {
        Map<String, Object> event = Map.of(
                "event", eventType,
                "payload", payload
        );
        w.write(gson.toJson(event) + "\n");
        w.flush();
    }
}
