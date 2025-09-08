package telegramserver.services;

import java.sql.*;
import java.util.*;

public class ContactService {

    private static final String URL = "jdbc:postgresql://localhost:5432/Telegram";
    private static final String USER = "postgres";
    private static final String PASSWORD = "AmirMahdiImani";

    public static List<Map<String, Object>> getContacts(int userId) {
        List<Map<String, Object>> contacts = new ArrayList<>();
        String sql = "SELECT * FROM contacts WHERE user_id = ?";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> contact = new HashMap<>();
                contact.put("id", rs.getInt("id"));
                contact.put("userId", rs.getInt("contact_user_id"));
                contact.put("displayName", rs.getString("name"));
                contacts.add(contact);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return contacts;
    }

    public static Map<String, Object> addContact(int ownerId, String name, String phone) {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            int contactUserId = -1;
            String findUserSql = "SELECT id FROM users WHERE phone_number = ?";
            try (PreparedStatement ps = conn.prepareStatement(findUserSql)) {
                ps.setString(1, phone);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    contactUserId = rs.getInt("id");
                } else {
                    return Map.of("error", "PHONE_NOT_REGISTERED");
                }
            }
            String checkSql = "SELECT 1 FROM contacts WHERE user_id = ? AND contact_user_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setInt(1, ownerId);
                ps.setInt(2, contactUserId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return Map.of("error", "CONTACT_ALREADY_EXISTS", "userId", contactUserId);
                }
            }
            String insertSql = "INSERT INTO contacts(user_id, contact_user_id, name) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setInt(1, ownerId);
                ps.setInt(2, contactUserId);
                ps.setString(3, name);
                ps.executeUpdate();
            }
            return Map.of("success", true, "userId", contactUserId);
        } catch (SQLException e) {
            e.printStackTrace();
            return Map.of("error", "DB_ERROR", "message", e.getMessage());
        }
    }
}
