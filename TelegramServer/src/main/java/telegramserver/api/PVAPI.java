apackage telegramserver.api;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import telegramserver.services.PVService;

import java.io.*;
import java.util.Map;

/*
 * PV REST API:
 * - POST /pv/start   { user1Id, user2Id }  -> returns pvId + chatId
 * - GET  /pv/{id}
 */
public class PVAPI implements HttpHandler {
    private static final Gson gson = new Gson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if ("/pv/start".equals(path) && "POST".equalsIgnoreCase(method)) {
            Map<String,Object> req = gson.fromJson(new InputStreamReader(exchange.getRequestBody()), Map.class);
            int u1 = ((Number)req.get("user1Id")).intValue();
            int u2 = ((Number)req.get("user2Id")).intValue();
            Map<String,Integer> res = PVService.startPv(u1, u2);
            send(exchange,200,gson.toJson(res));
            return;
        }

        if (path.startsWith("/pv/") && "GET".equalsIgnoreCase(method)) {
            String idS = path.replace("/pv/","");
            try {
                int id = Integer.parseInt(idS);
                // For now return a simple object (PVService holds mapping)
                // DB team: implement SELECT * FROM pv WHERE id=?
                send(exchange,200,gson.toJson(Map.of("pvId", id)));
            } catch (NumberFormatException e) {
                send(exchange,400,gson.toJson(Map.of("status","error","message","bad id")));
            }
            return;
        }

        send(exchange,404,gson.toJson(Map.of("status","error","message","not found")));
    }

    private void send(HttpExchange ex, int status, String body) throws IOException {
        ex.getResponseHeaders().add("Content-Type","application/json");
        ex.sendResponseHeaders(status, body.getBytes().length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body.getBytes()); }
    }
}
