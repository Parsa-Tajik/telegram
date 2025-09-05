package telegramserver.api;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import telegramserver.services.UserService;
import java.io.*;
import java.util.Map;

// REST API handler for register and login
public class AuthAPI implements HttpHandler {
    private static final Gson gson = new Gson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        String response;

        if ("/register".equals(path) && "POST".equalsIgnoreCase(method)) {
            Map<String, String> req = gson.fromJson(new InputStreamReader(exchange.getRequestBody()), Map.class);
            response = UserService.registerUser(req);
            sendResponse(exchange, 200, response);

        } else if ("/login".equals(path) && "POST".equalsIgnoreCase(method)) {
            Map<String, String> req = gson.fromJson(new InputStreamReader(exchange.getRequestBody()), Map.class);
            response = UserService.loginUser(req);
            sendResponse(exchange, 200, response);

        } else {
            sendResponse(exchange, 404, "Not found");
        }
    }

    private void sendResponse(HttpExchange exchange, int status, String response) throws IOException {
        exchange.sendResponseHeaders(status, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }
}
