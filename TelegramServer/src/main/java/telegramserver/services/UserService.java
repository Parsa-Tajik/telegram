package telegramserver.services;

import com.google.gson.Gson;
import telegramserver.models.User;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

// Business logic for user authentication
public class UserService {
    private static final Gson gson = new Gson();

    // TEMP in-memory storage (fallback)
    private static final Map<String, User> users = new HashMap<>();

    public static String registerUser(Map<String, String> req) {
        String username = req.get("username");

        if (users.containsKey(username)) {
            return gson.toJson(Map.of("status", "error", "message", "User already exists"));
        }

        User user = new User(
                1,
                req.get("firstName"),
                req.get("secondName"),
                req.get("bio"),
                req.get("phoneNumber"),
                username,
                req.get("password"), // will be stored in tswHash
                new Timestamp(System.currentTimeMillis()), // last_seen
                false,
                new Timestamp(System.currentTimeMillis())  // registered_at
        );

        users.put(username, user);

        // Save to DB
        user.handleUser();

        return gson.toJson(Map.of("status", "success", "message", "User registered"));
    }

    public static String loginUser(Map<String, String> req) {
        String username = req.get("username");
        String password = req.get("password");

        String url = "jdbc:postgresql://localhost:5432/Telegram";
        String dbUser = "postgres";
        String dbPass = "AmirMahdiImani";

        try (Connection conn = DriverManager.getConnection(url, dbUser, dbPass)) {
            String query = "SELECT * FROM users WHERE username = ? AND tsw_hash = ?";
            PreparedStatement ps = conn.prepareStatement(query);
            ps.setString(1, username);
            ps.setString(2, password);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                // update online status
                String update = "UPDATE users SET is_online = ? WHERE username = ?";
                PreparedStatement ps2 = conn.prepareStatement(update);
                ps2.setBoolean(1, true);
                ps2.setString(2, username);
                ps2.executeUpdate();

                return gson.toJson(Map.of("status", "success", "message", "Login successful"));
            } else {
                return gson.toJson(Map.of("status", "error", "message", "Invalid username or password"));
            }

        } catch (Exception e) {
            e.printStackTrace();
            return gson.toJson(Map.of("status", "error", "message", "DB error during login"));
        }
    }
}
