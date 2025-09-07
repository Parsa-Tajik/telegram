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
    private static final Map<String, User> users = new HashMap<>();
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/Telegram";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "AmirMahdiImani";

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
        String hashedPassword = hashPassword(req.password);

        User user = new User(
                Math.abs(UUID.randomUUID().hashCode()),
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
        user.adduser();

        return gson.toJson(Map.of("status", "success", "message", "User registered"));
    }

    public static String loginUser(LoginRequest req) {
        if (req == null || req.username == null || req.password == null) {
            return gson.toJson(Map.of("status", "error", "message", "Invalid payload"));
        }

        User user = users.get(req.username);

        if (user == null) {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                String query = "SELECT * FROM users WHERE username = ?";
                try (PreparedStatement ps = conn.prepareStatement(query)) {
                    ps.setString(1, req.username);
                    ResultSet rs = ps.executeQuery();
                    if (!rs.next()) {
                        return gson.toJson(Map.of("status", "error", "message", "User not found"));
                    }
                    user = new User(
                            rs.getInt("id"),
                            rs.getString("first_name"),
                            rs.getString("second_name"),
                            rs.getString("bio"),
                            rs.getString("phone_number"),
                            rs.getString("username"),
                            null,
                            rs.getTimestamp("created_at"),
                            rs.getBoolean("is_online"),
                            rs.getTimestamp("updated_at"),
                            rs.getString("password")
                    );
                    users.put(req.username, user);
                }
            } catch (SQLException e) {
                e.printStackTrace();
                return gson.toJson(Map.of("status", "error", "message", "Database error: " + e.getMessage()));
            }
        }

        String inputHash = hashPassword(req.password);
        if (!inputHash.equals(user.getPassword())) {
            return gson.toJson(Map.of("status", "error", "message", "Invalid password"));
        }

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String updateQuery = "UPDATE users SET is_online = ? WHERE username = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateQuery)) {
                ps.setBoolean(1, true);
                ps.setString(2, req.username);
                ps.executeUpdate();
            }
        } catch (SQLException ignored) {}

        return gson.toJson(Map.of("status", "success", "message", "Login successful"));
    }
}
