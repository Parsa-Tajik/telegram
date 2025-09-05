package com.telegram.telegrampromium.core;

import com.google.gson.JsonObject;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Routes NDJSON responses to the waiting request futures by matching the "id".
 * Thread-safe via ConcurrentHashMap.
 *
 * A lightweight onComplete hook allows tapping every completed response
 * for diagnostics or logging. The callback runs on the reader thread.
 */
public final class ResponseRouter {

    private final Map<String, CompletableFuture<JsonObject>> inflight = new ConcurrentHashMap<>();

    /** Optional wiretap invoked after a response is delivered to a waiter. */
    private volatile Consumer<JsonObject> onComplete;

    /**
     * Registers a future for the given request id.
     * Any existing mapping is replaced to avoid leaks on id reuse.
     *
     * @param id unique request id
     * @return a new future that will complete when a matching response arrives
     */
    public CompletableFuture<JsonObject> register(String id) {
        Objects.requireNonNull(id, "id");
        CompletableFuture<JsonObject> f = new CompletableFuture<>();
        inflight.put(id, f);
        return f;
    }

    /**
     * Completes and removes a pending future if one exists.
     * Unknown ids are ignored (may happen on timeouts).
     *
     * @param id      response id
     * @param payload response object
     */
    public void complete(String id, JsonObject payload) {
        if (id == null) return;
        CompletableFuture<JsonObject> f = inflight.remove(id);
        if (f != null) {
            f.complete(payload);
        }
        Consumer<JsonObject> tap = onComplete;
        if (tap != null && payload != null) {
            try { tap.accept(payload); } catch (Throwable ignore) {}
        }
    }

    /**
     * Fails and clears all pending futures, e.g., on connection drop.
     *
     * @param cause failure propagated to all inflight requests
     */
    public void failAll(Throwable cause) {
        inflight.forEach((k, f) -> f.completeExceptionally(cause));
        inflight.clear();
    }

    /**
     * Sets a diagnostic consumer that is invoked for every completed response.
     * The consumer executes on the reader/background thread; avoid heavy work.
     *
     * @param consumer a non-blocking consumer or null to disable
     */
    public void setOnComplete(Consumer<JsonObject> consumer) {
        this.onComplete = consumer;
    }
}
