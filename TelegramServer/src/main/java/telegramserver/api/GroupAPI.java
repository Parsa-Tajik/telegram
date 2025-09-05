package telegramserver.api;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import telegramserver.services.GroupService;

import java.io.*;
import java.util.Map;

/*
 * GroupAPI: endpoints for groups
 * - POST /groups/create  { name, description, isPublic, ownerId }
 * - POST /groups/join    { groupId, username }
 * - POST /groups/leave   { groupId, username }
 * - GET  /groups/{id}
 */
public class GroupAPI implements HttpHandler {
    private static final Gson gson = new Gson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        String response = "";

        if ("/groups/create".equals(path) && "POST".equalsIgnoreCase(method)) {
            Map<String,Object> req = gson.fromJson(new InputStreamReader(exchange.getRequestBody()), Map.class);
            String name = (String) req.getOrDefault("name", "group");
            String description = (String) req.getOrDefault("description", "");
            boolean isPublic = Boolean.parseBoolean(String.valueOf(req.getOrDefault("isPublic", "true")));
            int ownerId = ((Number)req.get("ownerId")).intValue();

            int groupId = GroupService.createGroup(name, description, isPublic, ownerId);
            response = gson.toJson(Map.of("status","success","groupId",groupId));
            send(exchange,200,response);
            return;
        }

        if ("/groups/join".equals(path) && "POST".equalsIgnoreCase(method)) {
            Map<String,Object> req = gson.fromJson(new InputStreamReader(exchange.getRequestBody()), Map.class);
            int groupId = ((Number)req.get("groupId")).intValue();
            String username = (String)req.get("username");
            GroupService.joinGroup(groupId, username);
            response = gson.toJson(Map.of("status","success","message","joined"));
            send(exchange,200,response);
            return;
        }

        if ("/groups/leave".equals(path) && "POST".equalsIgnoreCase(method)) {
            Map<String,Object> req = gson.fromJson(new InputStreamReader(exchange.getRequestBody()), Map.class);
            int groupId = ((Number)req.get("groupId")).intValue();
            String username = (String)req.get("username");
            GroupService.leaveGroup(groupId, username);
            response = gson.toJson(Map.of("status","success","message","left"));
            send(exchange,200,response);
            return;
        }

        if (path.startsWith("/groups/") && "GET".equalsIgnoreCase(method)) {
            try {
                int id = Integer.parseInt(path.replace("/groups/",""));
                Object info = GroupService.getGroupInfo(id);
                if (info == null) send(exchange,404,gson.toJson(Map.of("status","error","message","not found")));
                else send(exchange,200,gson.toJson(info));
            } catch (NumberFormatException e) {
                send(exchange,400,gson.toJson(Map.of("status","error","message","bad id")));
            }
            return;
        }

        send(exchange,404,gson.toJson(Map.of("status","error","message","not found")));
    }

    private void send(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type","application/json");
        exchange.sendResponseHeaders(status, body.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(body.getBytes()); }
    }
}
