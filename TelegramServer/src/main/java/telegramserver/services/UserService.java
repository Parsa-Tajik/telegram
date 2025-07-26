package telegramserver.services;

import telegramserver.models.User;

import java.util.HashMap;
import java.util.Map;

// This class will later be connected to the database (AMIR cna handle it)
public class UserService {
    // using an in-memory map as placeholder for now
    private static final Map<String, User> users = new HashMap<>();

    public static void register(User user) {
        users.put(user.getUsername(), user);
    }

    public static User login(String username, String password) {
        User user = users.get(username);
        if (user != null && user.getPassword().equals(password)) {
            return user;
        }
        return null;
    }

    public static boolean exists(String username) {
        return users.containsKey(username);
    }

    // DB person can replace this map with SQL queries later
}
