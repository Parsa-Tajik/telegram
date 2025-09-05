package telegramserver.services;

import java.sql.*;

/*
 * MessageSeenService - record when a user sees a message.
 * DB table expected: message_seens (id, user_id, message_id, seen_at)
 */
public class MessageSeenService {
    private static final String URL = "jdbc:postgresql://localhost:5432/Telegram";
    private static final String DB_USER = "postgres";
    private static final String DB_PASS = "AmirMahdiImani";

    public static boolean markSeen(int messageId, int userId) {
        String sql = "INSERT INTO message_seens (user_id, message_id, seen_at) VALUES (?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, messageId);
            ps.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            // If DB fails just return false (frontend can ignore)
            e.printStackTrace();
            return false;
        }
    }
}
