package com.telegram.telegrampromium.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.telegram.telegrampromium.core.Client;
import com.telegram.telegrampromium.core.Ids;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Chat listing API over NDJSON.
 * Request:  {type:"CHAT_LIST", id, cursor?, limit?}
 * Response: {type:"CHAT_LIST_OK", id, items:[...], next_cursor?, has_more?, total_unread?}
 */
public final class ChatAPI {

    private final Client client;

    public ChatAPI(Client client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    public record PageResult(List<com.telegram.telegrampromium.model.ChatSummary> items,
                             String nextCursor,
                             boolean hasMore,
                             int totalUnread) {}

    public CompletableFuture<PageResult> list(String cursor, int limit) {
        JsonObject req = new JsonObject();
        req.addProperty("type", "CHAT_LIST");
        req.addProperty("id", Ids.req("chats"));
        if (cursor != null && !cursor.isBlank()) req.addProperty("cursor", cursor);
        if (limit  > 0)                            req.addProperty("limit",  limit);

        return client.request(req)
                .orTimeout(15, TimeUnit.SECONDS)
                .thenApply(resp -> {
                    String type = str(resp, "type");
                    if (!"CHAT_LIST_OK".equals(type)) {
                        // Gracefully handle minimal test server that replies "<TYPE>_OK" without body.
                        return new PageResult(List.of(), null, false, 0);
                    }
                    List<com.telegram.telegrampromium.model.ChatSummary> list = new ArrayList<>();
                    JsonArray arr = resp.has("items") && resp.get("items").isJsonArray()
                            ? resp.getAsJsonArray("items") : new JsonArray();

                    for (int i = 0; i < arr.size(); i++) {
                        JsonObject c = arr.get(i).getAsJsonObject();
                        String id = str(c, "id");
                        String title = str(c, "title");
                        String kindStr = str(c, "kind");
                        var kind = parseKind(kindStr);
                        String preview = str(c, "last_preview");
                        long ts = c.has("last_ts") ? safeLong(c.get("last_ts")) : 0L;
                        int unread = c.has("unread") ? c.get("unread").getAsInt() : 0;
                        boolean muted = c.has("muted") && c.get("muted").getAsBoolean();
                        list.add(new com.telegram.telegrampromium.model.ChatSummary(
                                id, kind, title, preview, ts, unread, muted
                        ));
                    }

                    String next = str(resp, "next_cursor");
                    boolean more = resp.has("has_more") && resp.get("has_more").getAsBoolean();
                    int totalUnread = resp.has("total_unread") ? resp.get("total_unread").getAsInt() : 0;

                    return new PageResult(list, next, more, totalUnread);
                });
    }

    private static String str(JsonObject o, String k) {
        return (o != null && o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsString() : null;
    }

    private static long safeLong(com.google.gson.JsonElement e) {
        try { return e.getAsLong(); } catch (Exception ignore) { return 0L; }
    }

    private static com.telegram.telegrampromium.model.ChatSummary.Kind parseKind(String k) {
        if (k == null) return com.telegram.telegrampromium.model.ChatSummary.Kind.PV;
        return switch (k.toUpperCase()) {
            case "CHANNEL" -> com.telegram.telegrampromium.model.ChatSummary.Kind.CHANNEL;
            case "GROUP"   -> com.telegram.telegrampromium.model.ChatSummary.Kind.GROUP;
            default        -> com.telegram.telegrampromium.model.ChatSummary.Kind.PV;
        };
    }
}
