package telegramserver.services;

import java.util.*;

// Tracks chat members
public class ChatService {
    private static final Map<Integer, Set<String>> chatMembers = new HashMap<>();

    public static void joinChat(int chatId, String username) {
        chatMembers.putIfAbsent(chatId, new HashSet<>());
        chatMembers.get(chatId).add(username);
    }

    public static void leaveChat(int chatId, String username) {
        if (chatMembers.containsKey(chatId)) {
            chatMembers.get(chatId).remove(username);
        }
    }

    public static Set<String> getMembers(int chatId) {
        return chatMembers.getOrDefault(chatId, new HashSet<>());
    }
}
