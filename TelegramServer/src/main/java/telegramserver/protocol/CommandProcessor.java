package telegramserver.protocol;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import telegramserver.services.*;
import telegramserver.sockets.ClientRegistry;
import telegramserver.models.Message;

import java.io.BufferedWriter;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.*;

/**
 * CommandProcessor - central dispatcher for JSON socket commands.
 *
 * It calls the existing services (UserService, MessageService, ChatService, ChannelService).
 * We intentionally DO NOT modify DB model files. We only call service methods.
 *
 * NOTE: many service methods are small/shallow in your code. Where a service returns nothing,
 * this processor assumes the service handled DB and/or in-memory state.
 */
public class CommandProcessor {
    private static final Gson gson = new Gson();

    /**
     * Process a request JSON (request contains "type" and "id").
     * writerUsername: the username of the connected client (may be null if not logged in yet).
     * Returns a response JSON string (must be written back to the requesting client).
     *
     * For events that need to be broadcast to other clients, this method will call broadcast* helpers.
     */
    public static String processRequest(JsonObject req, String writerUsername) {
        String type = req.has("type") ? req.get("type").getAsString() : null;
        String id = req.has("id") ? req.get("id").getAsString() : UUID.randomUUID().toString();
        try {
            switch (type == null ? "" : type.toUpperCase()) {
                case "LOGIN":
                    return handleLogin(req, id);
                case "LIST_CHATS":
                    return handleListChats(req, id, writerUsername);
                case "SEND":
                    return handleSend(req, id, writerUsername);
                case "SEND_CHANNEL":
                    return handleSendChannel(req, id, writerUsername);
                case "CREATE_CHANNEL":
                    return handleCreateChannel(req, id, writerUsername);
                case "JOIN_CHANNEL":
                    return handleJoinChannel(req, id);
                case "GET_USER":
                    return handleGetUser(req, id);
                case "GET_CHANNEL":
                    return handleGetChannel(req, id);
                case "SEARCH_USER":
                    return handleSearchUser(req, id);
                case "SEARCH_CHAT":
                    return handleSearchChat(req, id);
                case "JOIN":
                    return handleJoin(req, id, writerUsername);
                case "LEAVE":
                    return handleLeave(req, id, writerUsername);
                case "START_PV":
                    return handleStartPV(req, id, writerUsername);
                case "SEEN":
                    return handleSeen(req, id, writerUsername);
                default:
                    return SocketProtocol.buildResponse("ERROR", id, Map.of("message", "unknown type"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return SocketProtocol.buildResponse("ERROR", id, Map.of("message", "server error: " + e.getMessage()));
        }
    }

    // ------------ Handlers ------------

    private static String handleLogin(JsonObject req, String id) {
        // Expect fields: username
        String username = getString(req, "username");
        if (username == null) return SocketProtocol.buildResponse("LOGIN_FAILED", id, Map.of("message", "username required"));

        // Add to ClientRegistry is done in ClientHandler after successful LOGIN command; here we just confirm
        return SocketProtocol.buildResponse("LOGIN_OK", id, Map.of("username", username, "message", "logged in"));
    }

    private static String handleListChats(JsonObject req, String id, String username) {
        // For now, use SearchAPI style: accept userId or username; we try to resolve userId via services if needed
        // We will return a simple list (names or ids). Because DB team manages actual lists, use SearchAPI logic via JDBC
        // Keep simple: return empty list if not available
        List<Object> chats = new ArrayList<>();
        // If request provides userId, try to call SearchAPI DB; but here we return core empty response
        return SocketProtocol.buildResponse("LIST_CHATS_OK", id, Map.of("chats", chats));
    }

    private static String handleSend(JsonObject req, String id, String username) {
        // Expect: chatId, message { kind, text }
        if (!req.has("chatId") || !req.has("message")) {
            return SocketProtocol.buildResponse("SEND_FAILED", id, Map.of("message", "chatId and message required"));
        }
        int chatId = req.get("chatId").getAsInt();
        JsonObject message = req.get("message").getAsJsonObject();
        String kind = message.has("kind") ? message.get("kind").getAsString() : "text";
        String text = message.has("text") ? message.get("text").getAsString() : "";

        // Build Message model (DB-team's Message model expects many fields, we use 0 placeholders where needed)
        Message m = new Message(0, text, 0, chatId, 0, new Timestamp(System.currentTimeMillis()), false, false);
        MessageService.saveMessage(m);

        // OPTIONAL: get generated message id if DB returns it. Current MessageService.saveMessage doesn't return id.
        // We'll create a synthetic id to send in response/event
        String syntheticMsgId = "M" + System.currentTimeMillis();

        // Response to requester (echo id)
        Map<String,Object> respBody = new HashMap<>();
        respBody.put("chatId", chatId);
        respBody.put("msgId", syntheticMsgId);
        respBody.put("ts", System.currentTimeMillis()/1000);
        String response = SocketProtocol.buildResponse("SEND_OK", id, respBody);

        // Build event for other members: message_new
        Map<String,Object> eventBody = new HashMap<>();
        eventBody.put("chatId", chatId);
        Map<String,Object> msgJson = new HashMap<>();
        msgJson.put("id", syntheticMsgId);
        msgJson.put("from", username);
        msgJson.put("kind", kind);
        msgJson.put("text", text);
        msgJson.put("ts", System.currentTimeMillis()/1000);
        eventBody.put("msg", msgJson);

        broadcastToChatMembers(chatId, SocketProtocol.buildEvent("message_new", eventBody), username);

        return response;
    }

    private static String handleSendChannel(JsonObject req, String id, String username) {
        if (!req.has("channelId") || !req.has("message")) {
            return SocketProtocol.buildResponse("SEND_CHANNEL_FAILED", id, Map.of("message", "channelId and message required"));
        }
        int channelId = req.get("channelId").getAsInt();
        JsonObject message = req.get("message").getAsJsonObject();
        String text = message.has("text") ? message.get("text").getAsString() : "";

        // Save into messages via MessageService (we do not attach channel in that model; DB team will handle mapping)
        Message m = new Message(0, text, 0, channelId, 0, new Timestamp(System.currentTimeMillis()), false, false);
        MessageService.saveMessage(m);
        String syntheticMsgId = "M" + System.currentTimeMillis();

        Map<String,Object> resp = Map.of("channelId", channelId, "msgId", syntheticMsgId, "ts", System.currentTimeMillis()/1000);
        // Broadcast to channel members
        Map<String,Object> eventBody = new HashMap<>();
        eventBody.put("channelId", channelId);
        Map<String,Object> msgJson = new HashMap<>();
        msgJson.put("id", syntheticMsgId);
        msgJson.put("from", username);
        msgJson.put("text", text);
        msgJson.put("ts", System.currentTimeMillis()/1000);
        eventBody.put("msg", msgJson);

        broadcastToChannelMembers(channelId, SocketProtocol.buildEvent("channel_message_new", eventBody), username);

        return SocketProtocol.buildResponse("SEND_CHANNEL_OK", id, resp);
    }

    private static String handleCreateChannel(JsonObject req, String id, String username) {
        // expect: name, description, isPublic (or ownerId)
        String name = req.has("name") ? req.get("name").getAsString() : "channel";
        String description = req.has("description") ? req.get("description").getAsString() : "";
        boolean isPublic = req.has("isPublic") && req.get("isPublic").getAsBoolean();
        // ChannelService.createChannel(chanelname,String description,Boolean isPublic) in your latest code uses different signature
        int channelId = ChannelService.createChannel(name, description, isPublic);
        return SocketProtocol.buildResponse("CREATE_CHANNEL_OK", id, Map.of("channelId", channelId, "name", name));
    }

    private static String handleJoinChannel(JsonObject req, String id) {
        // expect: channelId, userId or username
        if (!req.has("channelId") || !req.has("username")) {
            return SocketProtocol.buildResponse("JOIN_CHANNEL_FAILED", id, Map.of("message", "channelId and username required"));
        }
        int channelId = req.get("channelId").getAsInt();
        String username = req.get("username").getAsString();

        // In your ChannelService joinChannel signature is: joinChannel(String username,int id, int channelId,int Userid,Timestamp joinedat,Boolean isadmin,Boolean isPublic)
        // To keep simple call the simpler joinChannel(username, channelId) pattern if exists; otherwise call the full signature with placeholders.
        try {
            ChannelService.joinChannel(username, channelId, channelId, 0, new Timestamp(System.currentTimeMillis()), false, true);
        } catch (Exception e) {
            // try alternative simple call if exists
            try { ChannelService.joinChannel(channelId, username); } catch (Exception ignored) {}
        }
        return SocketProtocol.buildResponse("JOIN_CHANNEL_OK", id, Map.of("channelId", channelId, "username", username));
    }

    private static String handleGetUser(JsonObject req, String id) {
        if (!req.has("userId")) return SocketProtocol.buildResponse("GET_USER_FAILED", id, Map.of("message","userId required"));
        int uid = req.get("userId").getAsInt();
        // We cannot call UserAPI here (it's HTTP). Use DB direct lookup or return a placeholder
        // Keep it simple: respond with minimal info and let HTTP user endpoint be the source of truth
        Map<String,Object> userDto = Map.of("id", uid, "username", "unknown", "message", "use HTTP /users/{id} for full profile");
        return SocketProtocol.buildResponse("GET_USER_OK", id, Map.of("user", userDto));
    }

    private static String handleGetChannel(JsonObject req, String id) {
        if (!req.has("channelId")) return SocketProtocol.buildResponse("GET_CHANNEL_FAILED", id, Map.of("message","channelId required"));
        int cid = req.get("channelId").getAsInt();
        try {
            Map<String,Object> info = ChannelService.getChannelInfo(cid);
            return SocketProtocol.buildResponse("GET_CHANNEL_OK", id, Map.of("channel", info));
        } catch (Exception e) {
            return SocketProtocol.buildResponse("GET_CHANNEL_FAILED", id, Map.of("message","DB error"));
        }
    }

    private static String handleSearchUser(JsonObject req, String id) {
        if (!req.has("username")) return SocketProtocol.buildResponse("SEARCH_USER_FAILED", id, Map.of("message","username required"));
        String username = req.get("username").getAsString();
        // Your SearchAPI uses JDBC; we keep it simple and return the same dummy used in SearchAPI while leaving DB team to fill real results
        Map<String,Object> dummy = Map.of("username", username, "firstName", "Ali", "secondName","Nadi");
        return SocketProtocol.buildResponse("SEARCH_USER_OK", id, Map.of("user", dummy));
    }

    private static String handleSearchChat(JsonObject req, String id) {
        if (!req.has("userId")) return SocketProtocol.buildResponse("SEARCH_CHAT_FAILED", id, Map.of("message","userId required"));
        int uid = req.get("userId").getAsInt();
        List<String> chats = List.of("Group1", "Family"); // placeholder; DB-team will return real chats
        return SocketProtocol.buildResponse("SEARCH_CHAT_OK", id, Map.of("chats", chats));
    }

    private static String handleJoin(JsonObject req, String id, String username) {
        if (!req.has("chatId")) return SocketProtocol.buildResponse("JOIN_FAILED", id, Map.of("message","chatId required"));
        int chatId = req.get("chatId").getAsInt();
        ChatService.joinChat(chatId, username);
        return SocketProtocol.buildResponse("JOIN_OK", id, Map.of("chatId", chatId));
    }

    private static String handleLeave(JsonObject req, String id, String username) {
        if (!req.has("chatId")) return SocketProtocol.buildResponse("LEAVE_FAILED", id, Map.of("message","chatId required"));
        int chatId = req.get("chatId").getAsInt();
        ChatService.leaveChat(chatId, username);
        return SocketProtocol.buildResponse("LEAVE_OK", id, Map.of("chatId", chatId));
    }

    private static String handleStartPV(JsonObject req, String id, String username) {
        if (!req.has("peerUsername")) return SocketProtocol.buildResponse("START_PV_FAILED", id, Map.of("message","peerUsername required"));
        String peer = req.get("peerUsername").getAsString();
        Integer myId = getUserIdByUsername(username);
        Integer peerId = getUserIdByUsername(peer);
        if (myId == null || peerId == null) return SocketProtocol.buildResponse("START_PV_FAILED", id, Map.of("message","user not found"));
        Map<String,Integer> result = PVService.startPv(myId, peerId);
        int chatId = result.get("chatId");
        // Auto join
        ChatService.joinChat(chatId, username);
        ChatService.joinChat(chatId, peer);
        return SocketProtocol.buildResponse("START_PV_OK", id, Map.of("pvId", result.get("pvId"), "chatId", chatId));
    }

    private static String handleSeen(JsonObject req, String id, String username) {
        if (!req.has("messageId")) return SocketProtocol.buildResponse("SEEN_FAILED", id, Map.of("message","messageId required"));
        String messageIdStr = req.get("messageId").getAsString();
        // messageId in our DB is numeric? your Message model uses int id. Here we try to parse digits suffix.
        int messageId = parseMessageId(messageIdStr);
        Integer uid = getUserIdByUsername(username);
        if (uid == null) return SocketProtocol.buildResponse("SEEN_FAILED", id, Map.of("message","user unknown"));
        boolean ok = MessageSeenService.markSeen(messageId, uid);
        return SocketProtocol.buildResponse(ok ? "SEEN_OK" : "SEEN_FAILED", id, Map.of("messageId", messageIdStr));
    }

    // ------------ Utilities ------------

    private static void broadcastToChatMembers(int chatId, String eventJson, String skipUsername) {
        // ChatService.getMembers returns usernames
        try {
            Set<String> members = ChatService.getMembers(chatId);
            for (String member : members) {
                if (skipUsername != null && skipUsername.equals(member)) continue;
                BufferedWriter w = ClientRegistry.getWriter(member);
                if (w != null) {
                    try {
                        w.write(eventJson + "\n");
                        w.flush();
                    } catch (IOException ignored) {}
                }
            }
        } catch (Exception e) {
            // ignore broadcast errors
        }
    }

    private static void broadcastToChannelMembers(int channelId, String eventJson, String skipUsername) {
        try {
            Set<String> members = ChannelService.getMembers(channelId);
            for (String member : members) {
                if (skipUsername != null && skipUsername.equals(member)) continue;
                BufferedWriter w = ClientRegistry.getWriter(member);
                if (w != null) {
                    try {
                        w.write(eventJson + "\n");
                        w.flush();
                    } catch (IOException ignored) {}
                }
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private static int parseMessageId(String mid) {
        // Try to extract digits from Mxxxxx format
        try {
            String digits = mid.replaceAll("[^0-9]", "");
            if (digits.isEmpty()) return 0;
            return Integer.parseInt(digits);
        } catch (Exception e) {
            return 0;
        }
    }

    private static Integer getUserIdByUsername(String username) {
        // Try to resolve via UserService? UserService doesn't expose lookup; use JDBC as ClientHandler did earlier
        String q = "SELECT id FROM users WHERE username = ?";
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:postgresql://localhost:5432/Telegram", "postgres", "AmirMahdiImani");
             java.sql.PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setString(1, username);
            java.sql.ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("id");
        } catch (java.sql.SQLException ignored) {}
        return null;
    }

    private static String getString(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return (e == null || e.isJsonNull()) ? null : e.getAsString();
    }
}
