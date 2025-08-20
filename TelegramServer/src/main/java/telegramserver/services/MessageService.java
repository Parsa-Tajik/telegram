package telegramserver.services;

import telegramserver.models.Message;

import java.util.ArrayList;
import java.util.List;

// Handles saving and retrieving messages (DB-ready)
public class MessageService {
    private static final List<Message> messages = new ArrayList<>();

    public static void saveMessage(Message msg) {
        messages.add(msg);
        System.out.println("💾 Message saved: " + msg.getContent());
        // TODO: connect to DB using msg.handlemessages()
    }

    public static List<Message> getMessagesForChat(int chatId) {
        List<Message> chatMsgs = new ArrayList<>();
        for (Message m : messages) {
            if (m.getChatid() == chatId) {
                chatMsgs.add(m);
            }
        }
        return chatMsgs;
    }
}
