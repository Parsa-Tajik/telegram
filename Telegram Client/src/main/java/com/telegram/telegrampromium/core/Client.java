package com.telegram.telegrampromium.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TCP NDJSON client.
 * Each line is a standalone JSON object. Requests must carry a unique "id".
 * Matching responses are routed via ResponseRouter. Events (type="EVENT") are posted to EventBus.
 *
 * Lifecycle:
 *  - connect(): open socket and start reader thread
 *  - request(): send JSON, return future completed on matching response (same id)
 *  - send(): fire-and-forget (for commands that don't return a response)
 *  - close(): stop reader and close socket
 *
 * Threading:
 *  - readerThread only reads lines and dispatches; it's long-lived.
 *  - send/request are synchronized on writer to keep lines intact.
 */
public final class Client implements Closeable {

    private final String host;
    private final int port;
    private final ResponseRouter router;
    private final EventBus events;

    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;

    private Thread readerThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public Client(String host, int port, ResponseRouter router, EventBus events) {
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
        this.router = Objects.requireNonNull(router, "router");
        this.events = Objects.requireNonNull(events, "events");
    }

    /**
     * Opens the TCP socket and starts the reader thread.
     *
     * @throws IOException if the connection cannot be established
     */
    public void connect() throws IOException {
        if (running.get()) return;

        this.socket = new Socket(host, port);
        this.in  = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

        running.set(true);
        this.readerThread = new Thread(this::readLoop, "ndjson-reader");
        this.readerThread.setDaemon(true);
        this.readerThread.start();
    }

    /**
     * Sends a request and returns a future that completes when a response
     * with the same "id" arrives. The caller is responsible for providing
     * a unique "id" in the payload.
     *
     * @param payload NDJSON request with "type" and unique "id"
     */
    public CompletableFuture<JsonObject> request(JsonObject payload) {
        Objects.requireNonNull(payload, "payload");
        String id = payload.has("id") ? payload.get("id").getAsString() : null;
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("request payload must include a non-empty 'id'");
        }
        CompletableFuture<JsonObject> f = router.register(id);
        send(payload);
        return f;
    }

    /**
     * Sends a NDJSON line without awaiting a response.
     * Use for commands where no response is expected (rare).
     */
    public void send(JsonObject payload) {
        Objects.requireNonNull(payload, "payload");
        String line = Jsons.toLine(payload);
        synchronized (this) {
            try {
                out.write(line);
                out.flush();
            } catch (IOException e) {
                throw new UncheckedIOException("failed to send", e);
            }
        }
    }

    /**
     * Sends a request and awaits the response up to the given timeout.
     * Convenience helper for simple one-shot calls.
     */
    public JsonObject requestBlocking(JsonObject payload, long timeout, TimeUnit unit) throws Exception {
        return request(payload).orTimeout(timeout, unit).get();
    }

    /** Stops the reader and closes the socket. */
    @Override
    public void close() throws IOException {
        if (!running.getAndSet(false)) return;

        IOException first = null;
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) { first = e; }

        if (readerThread != null) {
            try { readerThread.join(500); } catch (InterruptedException ignore) { Thread.currentThread().interrupt(); }
        }

        router.failAll(new IOException("connection closed"));
        if (first != null) throw first;
    }

    // -------------------- reader loop -------------------- //

    private void readLoop() {
        try {
            String line;
            while (running.get() && (line = in.readLine()) != null) {
                if (line.isBlank()) continue;
                JsonObject obj;
                try {
                    obj = JsonParser.parseString(line).getAsJsonObject();
                } catch (Exception parseErr) {
                    System.err.println("[Client] Invalid NDJSON: " + line);
                    continue;
                }

                String type = obj.has("type") ? obj.get("type").getAsString() : null;
                if ("EVENT".equals(type)) {
                    events.post(obj);
                    continue;
                }

                String id = obj.has("id") ? obj.get("id").getAsString() : null;
                if (id != null) {
                    router.complete(id, obj);
                } else {
                    // Unmatched message: log and drop
                    System.err.println("[Client] Unmatched message (no id): " + obj);
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                System.err.println("[Client] Connection error: " + e.getMessage());
            }
        } finally {
            running.set(false);
            try {
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException ignore) {}
            router.failAll(new IOException("connection closed"));
        }
    }
}
