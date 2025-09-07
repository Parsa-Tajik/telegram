package telegramserver.services;

import com.google.gson.Gson;
import telegramserver.models.User;

import java.security.MessageDigest;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class UserService {
    private static final Gson gson = new Gson();
    private static final String url = "jdbc:postgresql://localhost:5432/Telegram";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "AmirMahdiImani";
    private static final Map<String, User> users = new HashMap<>();

    public static class RegisterRequest {
        String username;
        String firstName;
        String secondName;
        String bio;
        String phoneNumber;
        String password;
    }

    public static class LoginRequest {
        public String username;
        String password;
    }

    private static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(password.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Hash error", e);
        }
    }

    public static String registerUser(RegisterRequest req) {
        if (req == null || req.username == null || req.password == null) {
            return gson.toJson(Map.of("status", "error", "message", "Invalid payload"));
        }

        if (users.containsKey(req.username)) {
            return gson.toJson(Map.of("status", "error", "message", "User already exists"));
        }

        Timestamp now = new Timestamp(System.currentTimeMillis());
        UUID uuid = UUID.randomUUID();
        String hashedPassword = hashPassword(req.password);

        User user = new User(
                uuid.hashCode(),
                req.firstName,
                req.secondName,
                req.bio,
                req.phoneNumber,
                req.username,
                null,
                now,
                false,
                now,
                hashedPassword
        );

        users.put(req.username, user);

        try (Connection conn = DriverManager.getConnection(url, DB_USER, DB_PASSWORD)) {
            String insertQuery = "INSERT INTO users (id, first_name, second_name, bio, phone_number, username, password, created_at, is_online, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertQuery)) {
                ps.setInt(1, user.getId());
                ps.setString(2, user.getFirstName());
                ps.setString(3, user.getSecondName());
                ps.setString(4, user.getBio());
                ps.setString(5, user.getPhoneNumber());
                ps.setString(6, user.getUsername());
                ps.setString(7, user.getPassword());
                ps.setTimestamp(8, now);
                ps.setBoolean(9, false);
                ps.setTimestamp(10, now);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return gson.toJson(Map.of("status", "error", "message", "Database error"));
        }

        return gson.toJson(Map.of("status", "success", "message", "User registered"));
    }

    public static String loginUser(LoginRequest req) {
        if (req == null || req.username == null || req.password == null) {
            return gson.toJson(Map.of("status", "error", "message", "Invalid payload"));
        }

        String query = "SELECT password FROM users WHERE username = ?";
        try (Connection conn = DriverManager.getConnection(url, DB_USER, DB_PASSWORD);
             PreparedStatement ps = conn.prepareStatement(query)) {

            ps.setString(1, req.username);
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) {
                return gson.toJson(Map.of("status", "error", "message", "User not found"));
            }

            String storedHash = rs.getString("password");
            String inputHash = hashPassword(req.password);

            if (!storedHash.equals(inputHash)) {
                return gson.toJson(Map.of("status", "error", "message", "Invalid password"));
            }

            String updateQuery = "UPDATE users SET is_online = ? WHERE username = ?";
            try (PreparedStatement updatePs = conn.prepareStatement(updateQuery)) {
                updatePs.setBoolean(1, true);
                updatePs.setString(2, req.username);
                updatePs.executeUpdate();
            }

            return gson.toJson(Map.of("status", "success", "message", "Login successful"));

        } catch (SQLException e) {
            e.printStackTrace();
            return gson.toJson(Map.of("status", "error", "message", "Database error"));
        }
    }
}
