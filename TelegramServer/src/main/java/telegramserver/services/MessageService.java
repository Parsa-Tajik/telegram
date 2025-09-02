package telegramserver.services;

import telegramserver.models.Message;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

// Handles storing and retrieving messages
public class MessageService {
    private static final List<Message> messages = new ArrayList<>();

    public static void saveMessage(Message msg) {
        messages.add(msg);
        System.out.println("💾 Message saved (in-memory): " + msg.getContent());

        msg.handleMessage(); // save into DB
    }

    public static List<Message> getMessagesForChat(int chatId) {
        String url = "jdbc:postgresql://localhost:5432/Telegram";
        String dbUser = "postgres";
        String dbPass = "AmirMahdiImani";

        List<Message> chatMsgs = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(url, dbUser, dbPass)) {
            String query = "SELECT * FROM messages WHERE chat_id = ?";
            PreparedStatement ps = conn.prepareStatement(query);
            ps.setInt(1, chatId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                Message m = new Message(
                        rs.getInt("id"),
                        rs.getString("content"),
                        rs.getInt("sender_id"),
                        rs.getInt("chat_id"),
                        rs.getObject("reply_id") != null ? rs.getInt("reply_id") : null,
                        rs.getTimestamp("sent_at"),
                        rs.getBoolean("is_deleted"),
                        rs.getBoolean("is_edited")
                );
                chatMsgs.add(m);
            }

        } catch (Exception e) {
            throw new RuntimeException("⚠️ DB error while fetching messages", e);
        }

        return chatMsgs;
    }
}
