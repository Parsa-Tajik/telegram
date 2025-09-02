package telegramserver.api;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import telegramserver.models.User;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.*;
import java.util.*;

public class SearchAPI implements HttpHandler {
    private static final Gson gson = new Gson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String url = "jdbc:postgresql://localhost:5432/Telegram";
        String dbUser = "postgres";
        String dbPass = "AmirMahdiImani";

        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getQuery();
        String method = exchange.getRequestMethod();
        String response;

        if ("/search/user".equals(path) && method.equalsIgnoreCase("GET")) {
            String username = query.split("=")[1];
            String sql = "SELECT * FROM users WHERE username=?";

            try (Connection conn = DriverManager.getConnection(url, dbUser, dbPass);
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, username);
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    User u = new User(
                            rs.getInt("id"),
                            rs.getString("first_name"),
                            rs.getString("second_name"),
                            rs.getString("bio"),
                            rs.getString("phone_number"),
                            rs.getString("username"),
                            rs.getString("tsw_hash"),
                            rs.getTimestamp("last_seen"),
                            rs.getBoolean("is_online"),
                            rs.getTimestamp("registered_at")
                    );
                    response = gson.toJson(u);
                    sendResponse(exchange, 200, response);
                } else {
                    response = gson.toJson(Map.of("status", "error", "message", "User not found"));
                    sendResponse(exchange, 404, response);
                }

            } catch (SQLException e) {
                response = gson.toJson(Map.of("status", "error", "message", "DB error"));
                sendResponse(exchange, 500, response);
            }

        } else if ("/search/chat".equals(path) && method.equalsIgnoreCase("GET")) {
            // This should search group and channel memberships by user_id
            String sql = "SELECT c.name FROM chats c " +
                    "JOIN chat_members cm ON c.id = cm.chat_id " +
                    "WHERE cm.user_id = ?";

            List<String> chats = new ArrayList<>();

            try (Connection conn = DriverManager.getConnection(url, dbUser, dbPass);
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                int userId = Integer.parseInt(query.split("=")[1]); // ?userId=...
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    chats.add(rs.getString("name"));
                }

                response = gson.toJson(chats);
                sendResponse(exchange, 200, response);

            } catch (SQLException e) {
                response = gson.toJson(Map.of("status", "error", "message", "DB error"));
                sendResponse(exchange, 500, response);
            }

        } else {
            response = gson.toJson(Map.of("status", "error", "message", "Not found"));
            sendResponse(exchange, 404, response);
        }
    }

    private void sendResponse(HttpExchange exchange, int status, String response) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }
}
