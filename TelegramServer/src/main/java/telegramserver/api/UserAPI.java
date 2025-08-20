package telegramserver.api;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import telegramserver.models.User;

import java.io.IOException;
import java.io.OutputStream;

// Handles /users/{id}
public class UserAPI implements HttpHandler {
    private static final Gson gson = new Gson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        String response;

        if (path.startsWith("/users/") && method.equalsIgnoreCase("GET")) {
            int userId = Integer.parseInt(path.replace("/users/", ""));
            User dummy = new User(userId, "Ali", "Nadi", "This is bio",
                    "0912000000", "ali123", "hashPass", 0, true, 2025);
            response = gson.toJson(dummy);
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
