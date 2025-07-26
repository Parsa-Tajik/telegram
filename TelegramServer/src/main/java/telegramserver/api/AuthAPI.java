package telegramserver.api;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import telegramserver.models.User;
import telegramserver.services.UserService;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;

// Handles /register and /login endpoints
public class AuthAPI implements HttpHandler {
    private static final Gson gson = new Gson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        // Read the body (JSON)
        InputStreamReader isr = new InputStreamReader(exchange.getRequestBody());
        User requestUser = gson.fromJson(isr, User.class);

        String response;

        if ("/register".equals(path) && method.equalsIgnoreCase("POST")) {
            if (UserService.exists(requestUser.getUsername())) {
                response = "Username already exists";
                exchange.sendResponseHeaders(400, response.length());
            } else {
                UserService.register(requestUser);
                response = "Registered successfully";
                exchange.sendResponseHeaders(200, response.length());
            }

        } else if ("/login".equals(path) && method.equalsIgnoreCase("POST")) {
            User user = UserService.login(requestUser.getUsername(), requestUser.getPassword());
            if (user != null) {
                response = "Login successful";
                exchange.sendResponseHeaders(200, response.length());
            } else {
                response = "Invalid credentials";
                exchange.sendResponseHeaders(401, response.length());
            }

        } else {
            response = "Not found";
            exchange.sendResponseHeaders(404, response.length());
        }

        // Send response
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }
}
