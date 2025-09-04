package telegramserver.services;

import telegramserver.models.Message;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

// Handles storing and retrieving messages
public class MessageService {
    private static final List<Message> messages = new ArrayList<>();
    private static String url = "jdbc:postgresql://localhost:5432/Telegram";
    private static String user = "postgres";
    private static String password = "AmirMahdiImani";

    public static void saveMessage(Message msg) {
        messages.add(msg);
        System.out.println("💾 Message saved: " + msg.getContent());
        //DB program
        Message handle = new Message(msg.getId(),msg.getContent(),msg.getSenderId(),msg.getChatid(),msg.getReplyid(),msg.getSentat(),msg.isDeleted(),msg.isIsedited());
        handle.addmessages();
        // finish
    }

    public static List<Message> getMessagesForChat(int chatId) {
        List<Message> chatMsgs = new ArrayList<>();
        for (Message m : messages) {
            if (m.getChatid() == chatId) {
                chatMsgs.add(m);
            }
        }
        // DB program
        String query = "select * from messages where chatid = ?";
        try {
            Connection conn = DriverManager.getConnection(url, user, password);
            PreparedStatement ps = conn.prepareStatement(query);
            ps.setInt(1, chatId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int id = rs.getInt("id");
                String text = rs.getString("text");
                Timestamp createdAt = rs.getTimestamp("created_at");

                System.out.println("Message ID: " + id +
                        " | Text: " + text +
                        " | Date: " + createdAt);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        //finish
        return chatMsgs;
    }
}
