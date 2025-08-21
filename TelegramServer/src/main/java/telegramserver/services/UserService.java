package telegramserver.services;

import com.google.gson.Gson;
import telegramserver.models.User;

import java.util.HashMap;
import java.util.Map;

// Business logic for user authentication
public class UserService {
    private static final Gson gson = new Gson();

    // TEMP in-memory user storage (replace with DB later)
    private static final Map<String, User> users = new HashMap<>();

    public static String registerUser(Map<String, String> req) {
        String username = req.get("username");

        if (users.containsKey(username)) {
            return gson.toJson(Map.of("status", "error", "message", "User already exists"));
        }

        User user = new User(1, req.get("firstName"), req.get("secondName"), req.get("bio"),
                req.get("phoneNumber"), username, req.get("password"), 0, false, 2025);

        users.put(username, user);

        // 👉 DB Team: Save this user into `users` table instead of memory
        // Example: INSERT INTO users (...) VALUES (...);

        return gson.toJson(Map.of("status", "success", "message", "User registered"));
    }

    public static String loginUser(Map<String, String> req) {
        String username = req.get("username");
        String password = req.get("password");

        // 👉 DB Team: Instead of using HashMap, fetch user by username from DB
        if (!users.containsKey(username)) {
            return gson.toJson(Map.of("status", "error", "message", "User not found"));
        }

        User user = users.get(username);
        if (!user.getTswHash().equals(password)) {
            return gson.toJson(Map.of("status", "error", "message", "Invalid password"));
        }

        // 👉 DB Team: Update user "isOnline" status in DB
        return gson.toJson(Map.of("status", "success", "message", "Login successful"));
    }
}
