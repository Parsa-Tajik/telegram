package dev;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Minimal NDJSON dev server (TCP: 1688).
 * Logs every incoming request line and every response sent.
 * Now supports CHAT_LIST paging with two demo pages.
 */
public final class NdjsonTestServer {

    private static final int PORT = 1688;
    private static final Gson GSON = new Gson();

    public static void main(String[] args) throws Exception {
        try (ServerSocket server = new ServerSocket(PORT)) {
            System.out.println("[Server] Listening on localhost:" + PORT);
            while (true) {
                Socket s = server.accept();
                s.setTcpNoDelay(true);
                new Thread(() -> handleClient(s), "client-" + s.getPort()).start();
            }
        }
    }

    private static void handleClient(Socket socket) {
        final String who = socket.getRemoteSocketAddress().toString();
        System.out.println("[Server] + Client connected: " + who);

        try (socket;
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) continue;

                System.out.printf("[REQ]  %s  %s%n", who, line);

                JsonObject req;
                try {
                    req = GSON.fromJson(line, JsonObject.class);
                } catch (Exception ex) {
                    System.out.printf("[WARN] %s invalid JSON: %s%n", who, ex.getMessage());
                    continue;
                }

                final String type = getString(req, "type");
                final String id   = getString(req,   "id");

                if (type == null || id == null) {
                    JsonObject err = new JsonObject();
                    err.addProperty("type", "ERROR");
                    err.addProperty("id", id == null ? "" : id);
                    err.addProperty("msg", "missing 'type' or 'id'");
                    writeLine(out, err);
                    System.out.printf("[RESP] %s  type=ERROR id=%s%n", who, id);
                    continue;
                }

                JsonObject resp = switch (type) {
                    case "PING" -> respond(id, "PING_OK", req);
                    case "HELLO" -> {
                        JsonObject r = base(id, "HELLO_OK");
                        r.addProperty("server_time", System.currentTimeMillis());
                        yield r;
                    }
                    case "REGISTER" -> {
                        JsonObject r = base(id, "REGISTER_OK");
                        JsonObject user = new JsonObject();
                        user.addProperty("id", "u_1");
                        user.addProperty("displayName", getString(req, "displayName"));
                        user.addProperty("username", getString(req, "username"));
                        user.addProperty("avatar", (String) null);
                        r.add("user", user);
                        r.addProperty("session", "s_1");
                        r.addProperty("server_time", System.currentTimeMillis());
                        yield r;
                    }
                    case "LOGIN" -> {
                        JsonObject r = base(id, "LOGIN_OK");
                        JsonObject user = new JsonObject();
                        user.addProperty("id", "u_1");
                        user.addProperty("displayName", "Demo");
                        user.addProperty("username", getString(req, "username"));
                        user.addProperty("avatar", (String) null);
                        r.add("user", user);
                        r.addProperty("session", "s_2");
                        r.addProperty("server_time", System.currentTimeMillis());
                        yield r;
                    }
                    case "CHAT_LIST" -> chatList(id, getString(req, "cursor"), getInt(req, "limit", 20));
                    default -> base(id, type + "_OK");
                };

                writeLine(out, resp);

                final String respType = getString(resp, "type");
                System.out.printf("[RESP] %s  type=%s id=%s%n", who, respType, id);
            }
        } catch (IOException e) {
            System.out.println("[Server] - Client " + who + " disconnected: " + e.getMessage());
        }
    }

    private static JsonObject chatList(String id, String cursor, int limit) {
        JsonObject r = base(id, "CHAT_LIST_OK");
        JsonArray items = new JsonArray();

        // page 1
        if (cursor == null || cursor.isBlank() || "p1".equals(cursor)) {
            items.add(chat("c_101", "PV", "Alice", "Hi there!", ts(-3), 2, false));
            items.add(chat("c_102", "GROUP", "Team Alpha", "Standup at 10", ts(-120), 0, false));
            items.add(chat("c_103", "CHANNEL", "NewsWire", "Breaking: …", ts(-1800), 23, true));
            r.add("items", items);
            r.addProperty("next_cursor", "p2");
            r.addProperty("has_more", true);
            r.addProperty("total_unread", 25);
            return r;
        }

        // page 2
        if ("p2".equals(cursor)) {
            items.add(chat("c_104", "PV", "Bob", "See you soon", ts(-7200), 0, false));
            items.add(chat("c_105", "GROUP", "Weekend Plans", "Photos uploaded", ts(-9600), 5, false));
            r.add("items", items);
            r.addProperty("has_more", false);
            r.add("next_cursor", null);
            r.addProperty("total_unread", 30);
            return r;
        }

        // empty page (no more)
        r.add("items", items);
        r.addProperty("has_more", false);
        r.add("next_cursor", null);
        r.addProperty("total_unread", 30);
        return r;
    }

    private static long ts(int secondsAgo) {
        return System.currentTimeMillis() + secondsAgo * 1000L;
    }

    private static JsonObject chat(String id, String kind, String title, String preview, long lastTs, int unread, boolean muted) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("kind", kind);
        o.addProperty("title", title);
        o.addProperty("last_preview", preview);
        o.addProperty("last_ts", lastTs);
        o.addProperty("unread", unread);
        o.addProperty("muted", muted);
        return o;
    }

    private static String getString(JsonObject o, String key) {
        return o != null && o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }
    private static int getInt(JsonObject o, String key, int def) {
        try { return o != null && o.has(key) ? o.get(key).getAsInt() : def; } catch (Exception e) { return def; }
    }
    private static JsonObject base(String id, String type) {
        JsonObject o = new JsonObject();
        o.addProperty("type", type);
        o.addProperty("id", id);
        return o;
    }
    private static JsonObject respond(String id, String type, JsonObject echo) {
        JsonObject o = base(id, type);
        o.add("echo", echo);
        return o;
    }
    private static void writeLine(BufferedWriter out, JsonObject obj) throws IOException {
        out.write(GSON.toJson(obj));
        out.write('\n');
        out.flush();
    }
}
