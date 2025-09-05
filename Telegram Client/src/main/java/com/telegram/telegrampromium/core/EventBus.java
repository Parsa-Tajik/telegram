package com.telegram.telegrampromium.core;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Minimal event bus to deliver server events (type=EVENT) across the UI.
 * Thread-safe: listeners are stored in a CopyOnWriteArrayList.
 */
public final class EventBus {

    private final List<Consumer<JsonObject>> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(Consumer<JsonObject> l) {
        if (l != null) listeners.add(l);
    }

    public void unsubscribe(Consumer<JsonObject> l) {
        listeners.remove(l);
    }

    /** Called by the Client reader thread. Consumers must handle threading on their side. */
    public void post(JsonObject evt) {
        for (Consumer<JsonObject> l : listeners) {
            try { l.accept(evt); } catch (Throwable ignore) {}
        }
    }
}
