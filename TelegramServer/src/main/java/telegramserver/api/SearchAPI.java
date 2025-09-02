package telegramserver.api;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import telegramserver.models.User;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

public class SearchAPI implements HttpHandler {
    private static final Gson gson = new Gson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getQuery();
        String method = exchange.getRequestMethod();
        String response;

        if ("/search/user".equals(path) && method.equalsIgnoreCase("GET")) {
            String username = query.split("=")[1];

            // 👉 DB Team: Run SELECT query to find user by username
            if (username.equals("ali123")) {
                User u = new User(1, "Ali", "Nadi", "Bio",
                        "0912000000", "ali123", "hash", 0, true, 2025);
                response = gson.toJson(u);
                exchange.sendResponseHeaders(200, response.length());
            } else {
                response = "User not found";
                exchange.sendResponseHeaders(404, response.length());
            }

        } else if ("/search/chat".equals(path) && method.equalsIgnoreCase("GET")) {
            // 👉 DB Team: Fetch chats where user is a member
            List<String> chats = new ArrayList<>(List.of("Group1", "Family"));
            response = gson.toJson(chats);
            exchange.sendResponseHeaders(200, response.length());

        } else {
            response = "Not found";
            exchange.sendResponseHeaders(404, response.length());
        }

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }
}
