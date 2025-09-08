package com.telegram.telegrampromium.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Minimal NDJSON console client over TCP.
 *
 * Features:
 *  - Reads user-typed JSON objects from STDIN (one object per line) and sends them as NDJSON.
 *  - Prints every incoming NDJSON line (responses/events) from the server.
 *  - Commands:
 *      :help                show help
 *      :quit | :q           exit
 *      :autoid on|off       auto-generate "id" if missing (default: off)
 *      :pretty on|off       pretty-print incoming JSON (default: on)
 *      :raw on|off          print raw incoming line (default: on)
 *      :host <h> :port <p>  (shown only at startup via args; not live-switch)
 *      <<< ... >>>          multi-line JSON block; send as one NDJSON object
 *
 * Usage:
 *   java com.telegram.telegrampromium.tools.NdjsonConsoleClient             // localhost:1688
 *   java com.telegram.telegrampromium.tools.NdjsonConsoleClient 127.0.0.1 1688
 */
public final class NdjsonConsoleClient {

    // Use compact Gson for sending, pretty for optional display
    private static final Gson GSON_COMPACT = new Gson();
    private static final Gson GSON_PRETTY  = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) {
        final String host = args.length > 0 ? args[0] : "localhost";
        final int port    = args.length > 1 ? parsePort(args[1], 9090) : 9090;

        System.out.println("[INFO] Connecting to " + host + ":" + port + " ...");

        try (Socket socket = new Socket(host, port);
             BufferedReader in  = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out    = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
             BufferedReader cin = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {

            final Flags flags = new Flags();

            Thread reader = new Thread(() -> {
                String line;
                try {
                    while ((line = in.readLine()) != null) {
                        handleIncoming(line, flags);
                    }
                    System.out.println("[INFO] Connection closed by server.");
                } catch (IOException e) {
                    System.out.println("[ERROR] Socket read error: " + e.getMessage());
                }
            }, "ndjson-socket-reader");
            reader.setDaemon(true);
            reader.start();

            // REPL loop for user input
            printHelp();
            System.out.println("\nType a JSON object per line and hit Enter. Use :help for commands.");
            for (;;) {
                System.out.print("> ");
                String line = cin.readLine();
                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) continue;

                // Commands (start with ':')
                if (line.startsWith(":")) {
                    if (!handleCommand(line, flags)) {
                        break;                            // :quit
                    }
                }

                // Multiline JSON block: <<< ... >>>
                if (line.equals("<<<")) {
                    StringBuilder sb = new StringBuilder();
                    String blk;
                    while ((blk = cin.readLine()) != null && !blk.equals(">>>")) {
                        sb.append(blk).append('\n');
                    }
                    line = sb.toString();
                }
                // Parse, ensure object, optionally inject id, send as single NDJSON line
                try {
                    JsonElement el = JsonParser.parseString(line);
                    if (!el.isJsonObject()) {
                        System.out.println("[WARN] Input is not a JSON object. NDJSON requires one object per line.");
                        continue;
                    }
                    JsonObject obj = el.getAsJsonObject();

                    if (flags.autoId && !obj.has("id")) {
                        obj.addProperty("id", genId());
                    }

                    String outLine = GSON_COMPACT.toJson(obj);
                    out.println(outLine);                 // newline-delimited
                    System.out.println("[OUT] " + outLine);
                } catch (Exception parseErr) {
                    System.out.println("[WARN] Invalid JSON: " + parseErr.getMessage());
                }
            }

        } catch (IOException e) {
            System.out.println("[ERROR] Failed to connect: " + e.getMessage());
        }
    }

    private static void handleIncoming(String line, Flags flags) {
        String ts = Instant.now().toString();
        if (flags.showRaw) {
            System.out.println("[IN ] " + line);
        }
        if (flags.prettyIn) {
            try {
                JsonElement el = JsonParser.parseString(line);
                System.out.println("[IN◦] " + GSON_PRETTY.toJson(el));
            } catch (Exception ignore) {
                // not valid JSON; still printed raw above
            }
        }
    }

    private static boolean handleCommand(String line, Flags flags) {
        String[] p = line.split("\\s+");
        String cmd = p[0].toLowerCase();

        switch (cmd) {
            case ":q":
            case ":quit":
                System.out.println("[INFO] Bye.");
                return false;

            case ":help":
                printHelp();
                return true;

            case ":autoid":
                if (p.length >= 2) {
                    flags.autoId = "on".equalsIgnoreCase(p[1]);
                }
                System.out.println("[INFO] autoid = " + flags.autoId);
                return true;

            case ":pretty":
                if (p.length >= 2) {
                    flags.prettyIn = "on".equalsIgnoreCase(p[1]);
                }
                System.out.println("[INFO] pretty-in = " + flags.prettyIn);
                return true;

            case ":raw":
                if (p.length >= 2) {
                    flags.showRaw = "on".equalsIgnoreCase(p[1]);
                }
                System.out.println("[INFO] raw-in = " + flags.showRaw);
                return true;

            default:
                System.out.println("[WARN] Unknown command. Type :help");
                return true;
        }
    }

    private static void printHelp() {
        System.out.println("""
                Commands:
                  :help                 Show this help
                  :quit | :q            Exit
                  :autoid on|off        Auto-generate "id" if missing (default: off)
                  :pretty on|off        Pretty-print incoming JSON (default: on)
                  :raw on|off           Print raw incoming line (default: on)
                  <<< ... >>>           Enter multi-line JSON block; finish with >>>
                Examples:
                  {"type":"LOGIN","id":"cli-1","username":"alice","pass_hash":"..."}
                  {"type":"CHATS_LIST","id":"cli-2","limit":20}
                """);
    }

    private static String genId() {
        return "cli-" + Long.toString(System.currentTimeMillis(), 36);
    }

    private static int parsePort(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    /** Runtime flags for console behavior. */
    private static final class Flags {
        boolean autoId   = true;  // default: off (you type your own ids)
        boolean prettyIn = true;   // pretty-print incoming JSON
        boolean showRaw  = true;   // also show raw incoming NDJSON line
    }
}
