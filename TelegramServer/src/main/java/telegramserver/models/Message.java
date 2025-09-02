package telegramserver.models;

import java.sql.*;
import java.time.LocalDateTime;

// Representing a message between users (matches ERD: messages table)
public class Message {
    private int id;
    private String content;
    private int senderId;
    private int chatId;
    private Integer replyId;  // nullable
    private Timestamp sentAt;
    private boolean isDeleted;
    private boolean isEdited;

    public Message(int id, String content, int senderId, int chatId,
                   Integer replyId, Timestamp sentAt, boolean isDeleted, boolean isEdited) {
        this.id = id;
        this.content = content;
        this.senderId = senderId;
        this.chatId = chatId;
        this.replyId = replyId;
        this.sentAt = sentAt;
        this.isDeleted = isDeleted;
        this.isEdited = isEdited;
    }

    // Getters
    public int getId() { return id; }
    public String getContent() { return content; }
    public int getSenderId() { return senderId; }
    public int getChatId() { return chatId; }
    public Integer getReplyId() { return replyId; }
    public Timestamp getSentAt() { return sentAt; }
    public boolean isDeleted() { return isDeleted; }
    public boolean isEdited() { return isEdited; }

    // Save message into DB
    public void handleMessage() {
        String url = "jdbc:postgresql://localhost:5432/Telegram";
        String dbUser = "postgres";
        String dbPass = "AmirMahdiImani";

        String sql = "INSERT INTO messages (id, content, sender_id, chat_id, reply_id, sent_at, is_deleted, is_edited) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection con = DriverManager.getConnection(url, dbUser, dbPass);
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, id);
            ps.setString(2, content);
            ps.setInt(3, senderId);
            ps.setInt(4, chatId);
            if (replyId != null) {
                ps.setInt(5, replyId);
            } else {
                ps.setNull(5, java.sql.Types.INTEGER);
            }
            ps.setTimestamp(6, sentAt);
            ps.setBoolean(7, isDeleted);
            ps.setBoolean(8, isEdited);

            ps.executeUpdate();
            System.out.println("✅ Message inserted successfully into DB.");

        } catch (SQLException e) {
            System.err.println("⚠️ Database error (Message): " + e.getMessage());
        }
    }
}
