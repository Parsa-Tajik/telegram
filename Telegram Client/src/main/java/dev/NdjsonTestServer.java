package dev;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * NDJSON dev server with interactive console.
 *
 * Features:
 *  - Accepts TCP clients on :1688 and speaks NDJSON (one JSON object per line).
 *  - Handles a few request types for convenience (PING/HELLO/REGISTER/LOGIN/CHAT_LIST).
 *  - Console thread lets you send arbitrary JSON to all clients or a specific one.
 *
 * Console commands:
 *   /help
 *   /clients                      -> list connected clients
 *   /broadcast <json-or-kv>       -> send to all clients
 *   /send <idx|id> <json-or-kv>   -> send to a specific client by index or client-id
 *   <json-or-kv>                  -> (no slash) broadcast (shorthand)
 *
 * <json-or-kv> can be:
 *   - Raw JSON object, e.g. {"type":"MESSAGE_NEW","chat_id":"c_101","payload":{...}}
 *   - key=value pairs split by spaces, e.g. type=MESSAGE_NEW chat_id=c_101 ok=true n=3
 *     (numbers/booleans/null auto-detected; others treated as strings)
 *
 * Notes:
 *  - For EVENTS, omit "id" so the client routes it to the event bus.
 *  - For responses, include "id" matching the client's request id.
 */
public final class NdjsonTestServer {

    private static final int PORT = 1688;
    private static final Gson GSON = new Gson();

    /** Connected clients (thread-safe). */
    private static final List<ClientSession> CLIENTS = new CopyOnWriteArrayList<>();

    public static void main(String[] args) throws Exception {
        try (ServerSocket server = new ServerSocket(PORT)) {
            System.out.println("[Server] Listening on localhost:" + PORT);

            // Console thread for interactive commands
            Thread console = new Thread(NdjsonTestServer::consoleLoop, "console");
            console.setDaemon(true);
            console.start();

            while (true) {
                Socket s = server.accept();
                s.setTcpNoDelay(true);
                ClientSession session = new ClientSession(s);
                CLIENTS.add(session);
                System.out.printf("[Server] + Client connected [%s] #%d%n", session.id, CLIENTS.indexOf(session));
                new Thread(() -> handleClient(session), "client-" + s.getPort()).start();
            }
        }
    }

    /* ============================== Console ============================== */

    private static void consoleLoop() {
        System.out.println("[Console] Type /help for commands.");
        try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                try {
                    if (line.startsWith("/help")) {
                        printHelp();
                    } else if (line.startsWith("/clients")) {
                        listClients();
                    } else if (line.startsWith("/broadcast")) {
                        String payload = sliceAfter(line, "/broadcast");
                        JsonObject obj = parseJsonOrKv(payload);
                        broadcast(obj);
                    } else if (line.startsWith("/send")) {
                        String rest = sliceAfter(line, "/send");
                        if (rest.isBlank()) {
                            System.out.println("[Console] Usage: /send <idx|id> <json-or-kv>");
                            continue;
                        }
                        // First token is idx or id
                        int sp = rest.indexOf(' ');
                        if (sp == -1) {
                            System.out.println("[Console] Usage: /send <idx|id> <json-or-kv>");
                            continue;
                        }
                        String target = rest.substring(0, sp).trim();
                        String payload = rest.substring(sp + 1).trim();

                        ClientSession session = findClient(target);
                        if (session == null) {
                            System.out.printf("[Console] Client '%s' not found. Use /clients to list.%n", target);
                            continue;
                        }
                        JsonObject obj = parseJsonOrKv(payload);
                        send(session, obj);
                    } else {
                        // Shorthand: line is treated as <json-or-kv>, broadcast
                        JsonObject obj = parseJsonOrKv(line);
                        broadcast(obj);
                    }
                } catch (Exception ex) {
                    System.out.println("[Console] Error: " + ex.getMessage());
                }
            }
        } catch (IOException e) {
            System.out.println("[Console] Stopped: " + e.getMessage());
        }
    }

    private static void printHelp() {
        System.out.println("""
            Commands:
              /help
              /clients
              /broadcast <json-or-kv>
              /send <idx|id> <json-or-kv>

            Examples:
              /broadcast {"type":"MESSAGE_NEW","chat_id":"c_101","message":{"id":"m1","text":"Hi"}}
              /send 0 {"type":"CHAT_UPDATED","chat_id":"c_101","title":"New Title"}
              type=MESSAGE_NEW chat_id=c_101 ok=true count=3           (broadcast)
            """);
    }

    private static void listClients() {
        if (CLIENTS.isEmpty()) {
            System.out.println("[Console] No clients.");
            return;
        }
        for (int i = 0; i < CLIENTS.size(); i++) {
            ClientSession c = CLIENTS.get(i);
            System.out.printf("  #%d  id=%s  addr=%s  open=%s%n", i, c.id, c.socket.getRemoteSocketAddress(), !c.socket.isClosed());
        }
    }

    /** Find client by numeric index or session id. */
    private static ClientSession findClient(String token) {
        // Try index
        try {
            int idx = Integer.parseInt(token);
            if (idx >= 0 && idx < CLIENTS.size()) return CLIENTS.get(idx);
        } catch (NumberFormatException ignore) {}
        // Try id
        for (ClientSession c : CLIENTS) {
            if (c.id.equals(token)) return c;
        }
        return null;
    }

    private static String sliceAfter(String line, String prefix) {
        String s = line.substring(prefix.length()).trim();
        return s;
    }

    /** Parse either raw JSON or key=value pairs into a JsonObject. */
    private static JsonObject parseJsonOrKv(String s) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException("empty payload");
        s = s.trim();
        if (s.startsWith("{")) {
            return GSON.fromJson(s, JsonObject.class);
        }
        // key=value parser (very simple, space-separated)
        JsonObject obj = new JsonObject();
        Scanner sc = new Scanner(s);
        while (sc.hasNext()) {
            String tok = sc.next();
            int eq = tok.indexOf('=');
            if (eq <= 0) {
                // treat as a flag: "foo" -> foo=true
                obj.addProperty(tok, true);
                continue;
            }
            String key = tok.substring(0, eq);
            String val = tok.substring(eq + 1);
            // Try to coerce
            if ("null".equalsIgnoreCase(val)) {
                obj.add(key, null);
            } else if ("true".equalsIgnoreCase(val) || "false".equalsIgnoreCase(val)) {
                obj.addProperty(key, Boolean.parseBoolean(val));
            } else {
                // number?
                try {
                    if (val.contains(".")) obj.addProperty(key, Double.parseDouble(val));
                    else obj.addProperty(key, Long.parseLong(val));
                } catch (NumberFormatException nf) {
                    obj.addProperty(key, val);
                }
            }
        }
        return obj;
    }

    /* ============================== Server Core ============================== */

    private static void handleClient(ClientSession session) {
        final String who = session.socket.getRemoteSocketAddress().toString();

        try (session.socket;
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(session.socket.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) continue;

                // Raw request log
                System.out.printf("[REQ]  %s  %s%n", who, line);

                JsonObject req;
                try {
                    req = GSON.fromJson(line, JsonObject.class);
                } catch (Exception ex) {
                    System.out.printf("[WARN] %s invalid JSON: %s%n", who, ex.getMessage());
                    continue;
                }

                final String type = str(req, "type");
                final String id   = str(req, "id");

                if (type == null) {
                    JsonObject err = base(id, "ERROR");
                    err.addProperty("msg", "missing 'type'");
                    send(session, err);
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
                        user.addProperty("displayName", str(req, "displayName"));
                        user.addProperty("username", str(req, "username"));
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
                        user.addProperty("username", str(req, "username"));
                        user.addProperty("avatar", (String) null);
                        r.add("user", user);
                        r.addProperty("session", "s_2");
                        r.addProperty("server_time", System.currentTimeMillis());
                        yield r;
                    }
                    case "CHAT_LIST" -> chatList(id, str(req, "cursor"), getInt(req, "limit", 20));
                    case "CHAT_PIN" -> {
                        JsonObject r = base(id, "CHAT_PIN_OK");
                        // Optional: echo back the requested state
                        r.addProperty("chatId", str(req, "chatId"));
                        r.addProperty("pinned", req.get("pinned").getAsBoolean());
                        yield r;
                    }
                    default -> {
                        // Generic acknowledge for unknown test commands
                        JsonObject r = base(id, type + "_OK");
                        r.addProperty("server_time", System.currentTimeMillis());
                        yield r;
                    }
                };

                send(session, resp);
            }
        } catch (IOException e) {
            System.out.println("[Server] - Client " + who + " disconnected: " + e.getMessage());
        } finally {
            CLIENTS.remove(session);
            System.out.printf("[Server] - Client removed [%s]%n", session.id);
        }
    }

    /* ============================== Helpers ============================== */

    private static void send(ClientSession session, JsonObject obj) {
        try {
            String json = GSON.toJson(obj);
            session.out.write(json);
            session.out.write('\n');
            session.out.flush();
            System.out.printf("[RESP] -> %s  %s%n", session.id, json);
        } catch (IOException e) {
            System.out.printf("[RESP] ! failed to %s: %s%n", session.id, e.getMessage());
        }
    }

    private static void broadcast(JsonObject obj) {
        if (CLIENTS.isEmpty()) {
            System.out.println("[Console] No clients to broadcast.");
            return;
        }
        for (ClientSession c : CLIENTS) {
            send(c, obj);
        }
    }

    private static JsonObject base(String id, String type) {
        JsonObject o = new JsonObject();
        o.addProperty("type", type);
        if (id != null) o.addProperty("id", id);
        return o;
    }

    private static JsonObject respond(String id, String type, JsonObject echo) {
        JsonObject o = base(id, type);
        o.add("echo", echo);
        return o;
    }

    private static JsonObject chatList(String id, String cursor, int limit) {
        JsonObject r = base(id, "CHAT_LIST_OK");
        var items = new com.google.gson.JsonArray();

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

        if ("p2".equals(cursor)) {
            items.add(chat("c_104", "PV", "Bob", "See you soon", ts(-7200), 0, false));
            items.add(chat("c_105", "GROUP", "Weekend Plans", "Photos uploaded", ts(-9600), 5, false));
            r.add("items", items);
            r.addProperty("has_more", false);
            r.add("next_cursor", null);
            r.addProperty("total_unread", 30);
            return r;
        }

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

    private static String str(JsonObject o, String key) {
        return o != null && o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    private static int getInt(JsonObject o, String key, int def) {
        try { return o != null && o.has(key) ? o.get(key).getAsInt() : def; } catch (Exception e) { return def; }
    }

    /* ============================== Types ============================== */

    private static final class ClientSession {
        final String id = "c-" + UUID.randomUUID();
        final Socket socket;
        final BufferedWriter out;

        ClientSession(Socket socket) throws IOException {
            this.socket = socket;
            this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }
    }
}
