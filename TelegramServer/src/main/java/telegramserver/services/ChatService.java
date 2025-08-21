package telegramserver.services;

import java.util.*;

// Tracks members of each chat
public class ChatService {
    private static final Map<Integer, Set<String>> chatMembers = new HashMap<>();

    public static void joinChat(int chatId, String username) {
        chatMembers.putIfAbsent(chatId, new HashSet<>());
        chatMembers.get(chatId).add(username);

        // 👉 DB Team: Insert into chat_members table
    }

    public static void leaveChat(int chatId, String username) {
        if (chatMembers.containsKey(chatId)) {
            chatMembers.get(chatId).remove(username);

            // 👉 DB Team: DELETE FROM chat_members WHERE chatId=? AND username=?
        }
    }

    public static Set<String> getMembers(int chatId) {
        // 👉 DB Team: SELECT users from chat_members table
        return chatMembers.getOrDefault(chatId, new HashSet<>());
    }
}
