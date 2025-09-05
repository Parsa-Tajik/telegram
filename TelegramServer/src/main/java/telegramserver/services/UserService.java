package telegramserver.services;

import com.google.gson.Gson;
import telegramserver.models.User;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

// Business logic for user authentication
public class UserService {
    private static final Gson gson = new Gson();
    private static String url = "jdbc:postgresql://localhost:5432/Telegram";
    private static String USER = "postgres";
    private static String password = "AmirMahdiImani";

    // TEMP in-memory user storage (replace with DB later)
    private static final Map<String, User> users = new HashMap<>();

    public static String registerUser(Map<String, String> req) {
        String username = req.get("username");

        if (users.containsKey(username)) {
            return gson.toJson(Map.of("status", "error", "message", "User already exists"));
        }
        Timestamp now = new Timestamp(System.currentTimeMillis());

        User user = new User(1, req.get("firstName"), req.get("secondName"), req.get("bio"),
                req.get("phoneNumber"), username, req.get("password"), now, false, now);

        users.put(username, user);
        // DB program
        user.adduser();
        // finish


        return gson.toJson(Map.of("status", "success", "message", "User registered"));
    }

    public static String loginUser(Map<String, String> req) {
        String username = req.get("username");
        String Password = req.get("password");
        // DB program
        String query = "SELECT * FROM users WHERE username = ? AND password = ?";
        try{
            Connection conn = DriverManager.getConnection(url, USER, password);
            PreparedStatement ps = conn.prepareStatement(query);
            ps.setString(1, username);
            ps.setString(2, Password);
            ResultSet rs = ps.executeQuery();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        // finish

        if (!users.containsKey(username)) {
            return gson.toJson(Map.of("status", "error", "message", "User not found"));
        }

        User user = users.get(username);
        if (!user.getTswHash().equals(password)) {
            return gson.toJson(Map.of("status", "error", "message", "Invalid password"));
        }
        // DB program
        String updateQuery = "UPDATE users SET is_online = ? WHERE username = ?";

        try (Connection conn = DriverManager.getConnection(url, USER, password);
             PreparedStatement ps = conn.prepareStatement(updateQuery)) {

            boolean isOnline = true;

            ps.setBoolean(1, isOnline);
            ps.setString(2, username);

            int rows = ps.executeUpdate();
            if (rows > 0) {
                System.out.println("User " + username + " status updated → isOnline = " + isOnline);
            } else {
                System.out.println("⚠️ User not found: " + username);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        // finish

        return gson.toJson(Map.of("status", "success", "message", "Login successful"));
    }
}
