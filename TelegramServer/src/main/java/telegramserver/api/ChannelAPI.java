package telegramserver.api;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import telegramserver.services.ChannelService;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class ChannelAPI implements HttpHandler {
    private static final Gson gson = new Gson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        String response = "";

        if ("/channels/create".equals(path) && "POST".equalsIgnoreCase(method)) {
            Map<String, String> req = gson.fromJson(new InputStreamReader(exchange.getRequestBody()), Map.class);
            String name = req.get("name");
            int ownerId = Integer.parseInt(req.get("ownerId"));
            int channelId = ChannelService.createChannel(name, ownerId);

            Map<String, Object> res = new HashMap<>();
            res.put("status", "success");
            res.put("channelId", channelId);
            res.put("name", name);
            response = gson.toJson(res);
            sendResponse(exchange, 200, response);

        } else if ("/channels/join".equals(path) && "POST".equalsIgnoreCase(method)) {
            Map<String, String> req = gson.fromJson(new InputStreamReader(exchange.getRequestBody()), Map.class);
            int channelId = Integer.parseInt(req.get("channelId"));
            String username = req.get("username");
            ChannelService.joinChannel(channelId, username);

            response = gson.toJson(Map.of("status", "success", "message", username + " joined channel " + channelId));
            sendResponse(exchange, 200, response);

        } else if (path.startsWith("/channels/") && method.equalsIgnoreCase("GET")) {
            int channelId = Integer.parseInt(path.replace("/channels/", ""));
            response = gson.toJson(ChannelService.getChannelInfo(channelId));
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
    