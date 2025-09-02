package telegramserver.services;

import telegramserver.models.Message;

import java.util.ArrayList;
import java.util.List;

// Handles storing and retrieving messages
public class MessageService {
    private static final List<Message> messages = new ArrayList<>();

    public static void saveMessage(Message msg) {
        messages.add(msg);
        System.out.println("💾 Message saved: " + msg.getContent());

        // 👉 DB Team: Save into messages table
        // Example: INSERT INTO messages (...) VALUES (...)
    }

    public static List<Message> getMessagesForChat(int chatId) {
        List<Message> chatMsgs = new ArrayList<>();
        for (Message m : messages) {
            if (m.getChatid() == chatId) {
                chatMsgs.add(m);
            }
        }
        // 👉 DB Team: Instead, run SELECT * FROM messages WHERE chatId=?
        return chatMsgs;
    }
}
