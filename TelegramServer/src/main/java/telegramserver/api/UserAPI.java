package telegramserver.api;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import telegramserver.models.User;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.*;

// Handles /users/{id} endpoint
public class UserAPI implements HttpHandler {
    private static final Gson gson = new Gson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String url = "jdbc:postgresql://localhost:5432/Telegram";
        String dbUser = "postgres";
        String dbPass = "AmirMahdiImani";

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        String response;

        if (path.startsWith("/users/") && method.equalsIgnoreCase("GET")) {
            int userId = Integer.parseInt(path.replace("/users/", ""));
            String sql = "SELECT * FROM users WHERE id=?";

            try (Connection conn = DriverManager.getConnection(url, dbUser, dbPass);
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    User user = new User(
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
                    response = gson.toJson(user);
                    sendResponse(exchange, 200, response);
                } else {
                    response = gson.toJson(Map.of("status", "error", "message", "User not found"));
                    sendResponse(exchange, 404, response);
                }

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
