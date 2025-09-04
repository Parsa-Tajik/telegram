package telegramserver.services;

import telegramserver.models.User;

import java.util.*;

// Tracks members of each chat
public class ChatService {
    private static final Map<Integer, Set<String>> chatMembers = new HashMap<>();
    private static String url = "jdbc:postgresql://localhost:5432/Telegram";
    private static String user = "postgres";
    private static String password = "AmirMahdiImani";
    public static Scanner scanner = new Scanner(System.in);


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