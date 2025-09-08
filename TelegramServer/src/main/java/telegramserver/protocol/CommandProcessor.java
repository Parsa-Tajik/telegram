package telegramserver.protocol;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import telegramserver.models.Message;
import telegramserver.services.*;
import telegramserver.sockets.ClientRegistry;

import java.io.BufferedWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;

import static telegramserver.services.GroupService.resolveUserId;

public class CommandProcessor {
    private static final Gson gson = new Gson();

    public static String processRequest(JsonObject req, String writerUsername) {
        String type = req.has("type") ? req.get("type").getAsString() : null;
        String id = req.has("id") ? req.get("id").getAsString() : UUID.randomUUID().toString();

        try {
            switch (type == null ? "" : type.toUpperCase(Locale.ROOT)) {
                case "REGISTER":
                    return handleRegister(req, id);
                case "LOGIN":
                    return handleLogin(req, id);
                case "GET_HOME":
                    return handleGetHome(req, id, writerUsername);
                case "GET_USER_PROFILE":
                    return handleGetUserProfile(req, id);
                case "SEARCH_ADVANCED":
                    return handleSearchAdvanced(req, id);
                case "LIST_CHATS":
                    return handleListChats(req, id, writerUsername);
                case "SEND":
                case "SEND_MESSAGE":
                    return handleSend(req, id, writerUsername);
                case "SEND_CHANNEL":
                case "SEND_CHANNEL_MESSAGE":
                    return handleSendChannel(req, id, writerUsername);
                case "CREATE_CHANNEL":
                    return handleCreateChannel(req, id, writerUsername);
                case "JOIN_CHANNEL":
                    return handleJoinChannel(req, id);
                case "GET_CHANNEL":
                    return handleGetChannel(req, id);
                case "GET_USER":
                    return handleGetUser(req, id);
                case "SEARCH_USER":
                    return handleSearchUser(req, id);
                case "SEARCH_CHAT":
                    return handleSearchChat(req, id);
                case "JOIN":
                case "JOIN_CHAT":
                    return handleJoin(req, id, writerUsername);
                case "LEAVE":
                case "LEAVE_CHAT":
                    return handleLeave(req, id, writerUsername);
                case "CREATE_GROUP":
                    return handleCreateGroup(req, id, writerUsername);
                case "JOIN_GROUP":
                    return handleJoinGroup(req, id);
                case "LEAVE_GROUP":
                    return handleLeaveGroup(req, id);
                case "START_PV":
                    return handleStartPV(req, id, writerUsername);
                case "SEEN":
                    return handleSeen(req, id, writerUsername);
                case "ADD_REACTION":
                    return handleAddReaction(req, id, writerUsername);
                case "EDIT_MESSAGE":
                    return handleEditMessage(req, id, writerUsername);
                case "DELETE_MESSAGE":
                    return handleDeleteMessage(req, id, writerUsername);
                case "REPLY_MESSAGE":
                    return handleReplyMessage(req, id, writerUsername);
                case "SEND_FILE":
                    return handleSendFile(req, id, writerUsername);
                case "REQ":
                    if (req.has("cmd")) {
                        String cmd = req.get("cmd").getAsString();
                        switch (cmd) {
                            case "contacts_list":
                                return handleContactsList(req, id, writerUsername);
                            case "contacts_add":
                                return handleContactsAdd(req, id, writerUsername);
                        }
                    }
                    break;
                default:
                    return SocketProtocol.buildResponse("ERROR", id, Map.of("message", "unknown type: " + type));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return SocketProtocol.buildResponse("ERROR", id, Map.of("message", "server error: " + e.getMessage()));
        }
        return SocketProtocol.buildResponse("ERROR", id, Map.of("message", "unknown type: " + type));
    }

    private static String handleRegister(JsonObject req, String id) {
        try {
            JsonObject payload = req.has("payload") ? req.getAsJsonObject("payload") : req;
            UserService.RegisterRequest registerReq = gson.fromJson(payload, UserService.RegisterRequest.class);
            String resJson = UserService.registerUser(registerReq);
            Map<String, Object> parsed = gson.fromJson(resJson, new TypeToken<Map<String, Object>>() {}.getType());
            String status = (String) parsed.getOrDefault("status", "error");
            return SocketProtocol.buildResponse(
                    "success".equalsIgnoreCase(status) ? "REGISTER_OK" : "REGISTER_FAIL",
                    id, parsed);
        } catch (Exception e) {
            return SocketProtocol.buildResponse("REGISTER_FAIL", id, Map.of("message", e.getMessage()));
        }
    }

    private static String handleLogin(JsonObject req, String id) {
        try {
            JsonObject payload = req.has("payload") ? req.getAsJsonObject("payload") : req;
            UserService.LoginRequest loginReq = gson.fromJson(payload, UserService.LoginRequest.class);
            String resJson = UserService.loginUser(loginReq);
            Map<String, Object> parsed = gson.fromJson(resJson, new TypeToken<Map<String, Object>>() {}.getType());
            String status = (String) parsed.getOrDefault("status", "error");
            if ("success".equalsIgnoreCase(status)) {
                return SocketProtocol.buildResponse("LOGIN_OK", id, Map.of(
                        "username", loginReq.username,
                        "message", parsed.get("message")
                ));
            } else {
                return SocketProtocol.buildResponse("LOGIN_FAIL", id, Map.of("message", parsed.get("message")));
            }
        } catch (Exception e) {
            return SocketProtocol.buildResponse("LOGIN_FAIL", id, Map.of("message", e.getMessage()));
        }
    }

    private static String handleGetHome(JsonObject req, String id, String username) {
        Map<String, Object> home = new HashMap<>();
        home.put("user", Map.of(
                "username", username != null ? username : "guest",
                "bio", "This is my bio",
                "phone", "0912000000",
                "lastSeen", System.currentTimeMillis(),
                "isOnline", username != null
        ));
        home.put("chats", List.of(Map.of("id", 1, "title", "Family"), Map.of("id", 2, "title", "Friends")));
        home.put("groups", List.of(Map.of("id", 100, "name", "Project Group")));
        home.put("channels", List.of(Map.of("id", 200, "name", "TechNews")));
        return SocketProtocol.buildResponse("GET_HOME_OK", id, Map.of("home", home));
    }

    private static String handleGetUserProfile(JsonObject req, String id) {
        JsonObject payload = req.has("payload") ? req.getAsJsonObject("payload") : req;
        if (!payload.has("userId")) return SocketProtocol.buildResponse("GET_USER_PROFILE_FAILED", id, Map.of("message", "userId required"));
        int uid = payload.get("userId").getAsInt();
        Map<String, Object> profile = new HashMap<>();
        profile.put("id", uid);
        profile.put("username", "user" + uid);
        profile.put("bio", "This is user " + uid);
        profile.put("phone", "09120000" + uid);
        profile.put("lastSeen", System.currentTimeMillis());
        profile.put("isOnline", false);
        return SocketProtocol.buildResponse("GET_USER_PROFILE_OK", id, Map.of("profile", profile));
    }

    private static String handleSearchAdvanced(JsonObject req, String id) {
        JsonObject payload = req.has("payload") ? req.getAsJsonObject("payload") : req;
        List<Map<String, Object>> results = new ArrayList<>();
        results.add(Map.of("id", 1, "username", "ali123", "bio", "hello there", "phone", "0912000000"));
        results.add(Map.of("id", 2, "username", "nadi", "bio", "bio text", "phone", "09123334444"));
        return SocketProtocol.buildResponse("SEARCH_ADVANCED_OK", id, Map.of("results", results));
    }

    private static String handleListChats(JsonObject req, String id, String username) {
        return SocketProtocol.buildResponse("LIST_CHATS_OK", id, Map.of("chats", new ArrayList<>()));
    }

    private static String handleSend(JsonObject req, String id, String senderUsername) {
        JsonObject payload = req.has("payload") ? req.getAsJsonObject("payload") : req;
        int chatId = payload.has("chatId") ? payload.get("chatId").getAsInt() : 0;
        String text = payload.has("text") ? payload.get("text").getAsString() : "";
        Message m = new Message(0, text, 0, chatId, 0, new Timestamp(System.currentTimeMillis()), false, false);
        MessageService.saveMessage(m);
        String syntheticMsgId = "M" + System.currentTimeMillis();
        Map<String, Object> resp = Map.of("chatId", chatId, "msgId", syntheticMsgId, "ts", System.currentTimeMillis() / 1000);
        Map<String, Object> eventBody = Map.of(
                "chatId", chatId,
                "msg", Map.of("id", syntheticMsgId, "from", senderUsername, "text", text, "ts", System.currentTimeMillis() / 1000)
        );
        broadcastToChatMembers(chatId, SocketProtocol.buildEvent("message_new", eventBody), senderUsername);
        return SocketProtocol.buildResponse("SEND_OK", id, resp);
    }

    private static String handleSendChannel(JsonObject req, String id, String senderUsername) {
        JsonObject payload = req.has("payload") ? req.getAsJsonObject("payload") : req;
        int channelId = payload.has("channelId") ? payload.get("channelId").getAsInt() : 0;
        String text = payload.has("text") ? payload.get("text").getAsString() : "";
        Message m = new Message(0, text, 0, channelId, 0, new Timestamp(System.currentTimeMillis()), false, false);
        MessageService.saveMessage(m);
        String syntheticMsgId = "M" + System.currentTimeMillis();
        Map<String, Object> resp = Map.of("channelId", channelId, "msgId", syntheticMsgId, "ts", System.currentTimeMillis() / 1000);
        Map<String, Object> eventBody = Map.of(
                "channelId", channelId,
                "msg", Map.of("id", syntheticMsgId, "from", senderUsername, "text", text, "ts", System.currentTimeMillis() / 1000)
        );
        broadcastToChannelMembers(channelId, SocketProtocol.buildEvent("channel_message_new", eventBody), senderUsername);
        return SocketProtocol.buildResponse("SEND_CHANNEL_OK", id, resp);
    }

    private static String handleCreateChannel(JsonObject req, String id, String username) {
        JsonObject payload = req.has("payload") ? req.getAsJsonObject("payload") : req;
        String name = payload.has("name") ? payload.get("name").getAsString() : "channel";
        String description = payload.has("description") ? payload.get("description").getAsString() : "";
        boolean isPublic = payload.has("isPublic") && payload.get("isPublic").getAsBoolean();
        int channelId = ChannelService.createChannel(name, description, isPublic);
        return SocketProtocol.buildResponse("CREATE_CHANNEL_OK", id, Map.of("channelId", channelId, "name", name));
    }

    private static String handleJoinChannel(JsonObject req, String id) {
        JsonObject payload = req.has("payload") ? req.getAsJsonObject("payload") : req;
        int channelId = payload.get("channelId").getAsInt();
        String username = payload.get("username").getAsString();
        Integer userId = resolveUserId(username);
        if (userId == null) return SocketProtocol.buildResponse("JOIN_CHANNEL_FAILED", id, Map.of("message", "User not found"));
        try { ChannelService.joinChannel(username, userId, userId, new Timestamp(System.currentTimeMillis()), false, true); } catch (Exception ignored) {}
        return SocketProtocol.buildResponse("JOIN_CHANNEL_OK", id, Map.of("channelId", channelId, "username", username));
    }

    private static String handleGetChannel(JsonObject req, String id) {
        JsonObject payload = req.has("payload") ? req.getAsJsonObject("payload") : req;
        if (!payload.has("channelId")) return SocketProtocol.buildResponse("GET_CHANNEL_FAILED", id, Map.of("message", "channelId required"));
        int cid = payload.get("channelId").getAsInt();
        try {
            Map<String, Object> info = ChannelService.getChannelInfo(cid);
            return SocketProtocol.buildResponse("GET_CHANNEL_OK", id, Map.of("channel", info));
        } catch (Exception e) {
            return SocketProtocol.buildResponse("GET_CHANNEL_FAILED", id, Map.of("message", "DB error"));
        }
    }

    private static String handleGetUser(JsonObject req, String id) {
        JsonObject payload = req.has("payload") ? req.getAsJsonObject("payload") : req;
        if (!payload.has("userId")) return SocketProtocol.buildResponse("GET_USER_FAILED", id, Map.of("message", "userId required"));
        int uid = payload.get("userId").getAsInt();
        Map<String, Object> userDto = Map.of("id", uid, "message", "use HTTP /users or DB query for full profile");
        return SocketProtocol.buildResponse("GET_USER_OK", id, Map.of("user", userDto));
    }

    private static String handleSearchUser(JsonObject req, String id) {
        JsonObject payload = req.has("payload") ? req.getAsJsonObject("payload") : req;
        if (!payload.has("username")) return SocketProtocol.buildResponse("SEARCH_USER_FAILED", id, Map.of("message", "username required"));
        String username = payload.get("username").getAsString();
        Map<String, Object> dummy = Map.of("username", username, "firstName", "Ali", "secondName", "Nadi");
        return SocketProtocol.buildResponse("SEARCH_USER_OK", id, Map.of("user", dummy));
    }

    private static String handleSearchChat(JsonObject req, String id) {
        List<String> chats = List.of("Group1", "Family");
        return SocketProtocol.buildResponse("SEARCH_CHAT_OK", id, Map.of("chats", chats));
    }

    private static String handleJoin(JsonObject req, String id, String username) {
        JsonObject payload = req.has("payload") ? req.getAsJsonObject("payload") : req;
        int chatId = payload.get("chatId").getAsInt();
        ChatService.joinChat(chatId, username);
        return SocketProtocol.buildResponse("JOIN_OK", id, Map.of("chatId", chatId));
    }

    private static String handleLeave(JsonObject req, String id, String username) {
        JsonObject payload = req.has("payload") ? req.getAsJsonObject("payload") : req;
        int chatId = payload.get("chatId").getAsInt();
        ChatService.leaveChat(chatId, username);
        return SocketProtocol.buildResponse("LEAVE_OK", id, Map.of("chatId", chatId));
    }

    private static String handleCreateGroup(JsonObject req, String id, String username) {
        JsonObject payload = req.has("payload") ? req.getAsJsonObject("payload") : req;
        String name = payload.has("title") ? payload.get("title").getAsString() : "group";
        String description = payload.has("description") ? payload.get("description").getAsString() : "";
        boolean isPublic = !(payload.has("private") && payload.get("private").getAsBoolean());
        int ownerId = payload.has("ownerId") ? payload.get("ownerId").getAsInt() : 0;
        int groupId = GroupService.createGroup(name, description, isPublic, ownerId);
        List<String> members = new ArrayList<>();
        if (payload.has("members") && payload.get("members").isJsonArray()) {
            payload.getAsJsonArray("members").forEach(e -> members.add(e.getAsString()));
        }
        for (String member : members) GroupService.joinGroup(groupId, member);
        return SocketProtocol.buildResponse("CREATE_GROUP_OK", id, Map.of("groupId", groupId));
    }

    private static String handleJoinGroup(JsonObject req, String id) {
        JsonObject payload = req.has("payload") ? req.getAsJsonObject("payload") : req;
        int groupId = payload.get("groupId").getAsInt();
        String username = payload.get("username").getAsString();
        GroupService.joinGroup(groupId, username);
        return SocketProtocol.buildResponse("JOIN_GROUP_OK", id, Map.of("groupId", groupId));
    }

    private static String handleLeaveGroup(JsonObject req, String id) {
        JsonObject payload = req.has("payload") ? req.getAsJsonObject("payload") : req;
        int groupId = payload.get("groupId").getAsInt();
        String username = payload.get("username").getAsString();
        GroupService.leaveGroup(groupId, username);
        return SocketProtocol.buildResponse("LEAVE_GROUP_OK", id, Map.of("groupId", groupId));
    }

    private static String handleStartPV(JsonObject req, String id, String username) {
        JsonObject payload = req.has("payload") ? req.getAsJsonObject("payload") : req;
        String peer = payload.get("peerUsername").getAsString();
        Integer myId = getUserIdByUsername(username);
        Integer peerId = getUserIdByUsername(peer);
        if (myId == null || peerId == null) return SocketProtocol.buildResponse("START_PV_FAILED", id, Map.of("message", "user not found"));
        Map<String,Integer> result = PVService.startPv(myId, peerId);
        int chatId = result.get("chatId");
        ChatService.joinChat(chatId, username);
        ChatService.joinChat(chatId, peer);
        return SocketProtocol.buildResponse("START_PV_OK", id, Map.of("pvId", result.get("pvId"), "chatId", chatId));
    }

    private static String handleSeen(JsonObject req, String id, String username) {
        JsonObject payload = req.has("payload") ? req.getAsJsonObject("payload") : req;
        String messageIdStr = payload.get("messageId").getAsString();
        int messageId = parseMessageId(messageIdStr);
        Integer uid = getUserIdByUsername(username);
        if (uid == null) return SocketProtocol.buildResponse("SEEN_FAILED", id, Map.of("message", "user unknown"));
        boolean ok = MessageSeenService.markSeen(messageId, uid);
        return SocketProtocol.buildResponse(ok ? "SEEN_OK" : "SEEN_FAILED", id, Map.of("messageId", messageIdStr));
    }

    private static String handleAddReaction(JsonObject req, String id, String username) {
        JsonObject payload = req.has("payload") ? req.getAsJsonObject("payload") : req;
        String msgId = payload.get("messageId").getAsString();
        String reaction = payload.get("reaction").getAsString();
        Map<String,Object> eventBody = Map.of("messageId", msgId, "reaction", reaction, "from", username);
        broadcastToAll(SocketProtocol.buildEvent("reaction_added", eventBody));
        return SocketProtocol.buildResponse("REACTION_OK", id, eventBody);
    }

    private static String handleEditMessage(JsonObject req, String id, String username) {
        JsonObject payload = req.has("payload") ? req.getAsJsonObject("payload") : req;
        String msgId = payload.get("messageId").getAsString();
        String newText = payload.get("newText").getAsString();
        Map<String,Object> eventBody = Map.of("messageId", msgId, "newText", newText, "editedBy", username);
        broadcastToAll(SocketProtocol.buildEvent("message_edited", eventBody));
        return SocketProtocol.buildResponse("EDIT_OK", id, eventBody);
    }

    private static String handleDeleteMessage(JsonObject req, String id, String username) {
        JsonObject payload = req.has("payload") ? req.getAsJsonObject("payload") : req;
        String msgId = payload.get("messageId").getAsString();
        Map<String,Object> eventBody = Map.of("messageId", msgId, "deletedBy", username);
        broadcastToAll(SocketProtocol.buildEvent("message_deleted", eventBody));
        return SocketProtocol.buildResponse("DELETE_OK", id, eventBody);
    }

    private static String handleReplyMessage(JsonObject req, String id, String username) {
        JsonObject payload = req.has("payload") ? req.getAsJsonObject("payload") : req;
        int chatId = payload.get("chatId").getAsInt();
        String replyTo = payload.get("replyTo").getAsString();
        String text = payload.get("text").getAsString();
        String syntheticMsgId = "M" + System.currentTimeMillis();
        Map<String,Object> msg = Map.of("id", syntheticMsgId, "from", username, "text", text, "replyTo", replyTo, "ts", System.currentTimeMillis()/1000);
        broadcastToChatMembers(chatId, SocketProtocol.buildEvent("message_new", Map.of("chatId", chatId, "msg", msg)), username);
        return SocketProtocol.buildResponse("REPLY_OK", id, Map.of("msg", msg));
    }
    // ---- Contacts Handlers ----

    private static String handleContactsList(JsonObject req, String id, String username) {
        Integer userId = getUserIdByUsername(username);
        if (userId == null) {
            return SocketProtocol.buildResponse("ERROR", id, Map.of("message", "User not found"));
        }

        List<Map<String, Object>> contacts = ContactService.getContacts(userId);

        return SocketProtocol.buildResponse("CONTACTS_LIST_OK", id, Map.of(
                "contacts", contacts,
                "server_time", System.currentTimeMillis()
        ));
    }

    private static String handleContactsAdd(JsonObject req, String id, String username) {
        Integer ownerId = getUserIdByUsername(username);
        if (ownerId == null) {
            return SocketProtocol.buildResponse("ERROR", id, Map.of("message", "User not found"));
        }

        JsonObject payload = req.has("contact") ? req.getAsJsonObject("contact") : null;
        if (payload == null || !payload.has("name") || !payload.has("phone")) {
            return SocketProtocol.buildResponse("ERROR", id, Map.of("message", "Invalid payload"));
        }

        String name = payload.get("name").getAsString();
        String phone = payload.get("phone").getAsString();

        Map<String, Object> res = ContactService.addContact(ownerId, name, phone);

        if (res.containsKey("error")) {
            return SocketProtocol.buildResponse("ERROR", id, Map.of(
                    "code", res.get("error"),
                    "userId", res.getOrDefault("userId", ""),
                    "message", res.get("error").equals("CONTACT_ALREADY_EXISTS")
                            ? "Contact already exists" : "Phone is not registered",
                    "server_time", System.currentTimeMillis()
            ));
        }

        return SocketProtocol.buildResponse("CONTACTS_ADD_OK", id, Map.of(
                "userId", res.get("userId"),
                "server_time", System.currentTimeMillis()
        ));
    }

    private static String handleSendFile(JsonObject req, String id, String username) {
        JsonObject payload = req.has("payload") ? req.getAsJsonObject("payload") : req;
        int chatId = payload.get("chatId").getAsInt();
        String fileName = payload.get("fileName").getAsString();
        String fileData = payload.get("fileData").getAsString();
        Map<String,Object> fileMsg = Map.of("id", "F" + System.currentTimeMillis(), "from", username, "fileName", fileName, "size", fileData.length(), "ts", System.currentTimeMillis()/1000);
        broadcastToChatMembers(chatId, SocketProtocol.buildEvent("file_shared", Map.of("chatId", chatId, "file", fileMsg)), username);
        return SocketProtocol.buildResponse("FILE_OK", id, Map.of("file", fileMsg));
    }

    private static void broadcastToChatMembers(int chatId, String eventJson, String skipUsername) {
        try {
            Set<String> members = ChatService.getMembers(chatId);
            for (String member : members) {
                if (skipUsername != null && skipUsername.equals(member)) continue;
                BufferedWriter w = ClientRegistry.getWriter(member);
                if (w != null) { try { w.write(eventJson + "\n"); w.flush(); } catch (IOException ignored) {} }
            }
        } catch (Exception ignored) {}
    }

    private static void broadcastToChannelMembers(int channelId, String eventJson, String skipUsername) {
        try {
            Set<String> members = ChannelService.getMembers(channelId);
            for (String member : members) {
                if (skipUsername != null && skipUsername.equals(member)) continue;
                BufferedWriter w = ClientRegistry.getWriter(member);
                if (w != null) { try { w.write(eventJson + "\n"); w.flush(); } catch (IOException ignored) {} }
            }
        } catch (Exception ignored) {}
    }

    private static void broadcastToAll(String eventJson) {
        for (BufferedWriter w : ClientRegistry.getClients().values()) {
            try { w.write(eventJson + "\n"); w.flush(); } catch (IOException ignored) {}
        }
    }

    private static int parseMessageId(String mid) {
        try {
            String digits = mid.replaceAll("[^0-9]", "");
            if (digits.isEmpty()) return 0;
            return Integer.parseInt(digits);
        } catch (Exception e) {
            return 0;
        }
    }

    private static Integer getUserIdByUsername(String username) {
        String q = "SELECT id FROM users WHERE username = ?";
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:postgresql://localhost:5432/Telegram", "postgres", "AmirMahdiImani");
             java.sql.PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setString(1, username);
            java.sql.ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("id");
        } catch (java.sql.SQLException ignored) {}
        return null;
    }
}
