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

import com.google.gson.JsonElement;
import com.telegram.telegrampromium.model.Message;
import com.telegram.telegrampromium.model.MessageKind;
import com.telegram.telegrampromium.model.MessageStatus;

/**
 * Chat listing & actions (pin) over NDJSON.
 * Requests:
 *  - CHAT_LIST  {id, cursor?, limit?}
 *  - CHAT_PIN   {id, chatId, pinned}
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
                        boolean pinned = c.has("pinned") && c.get("pinned").getAsBoolean();
                        list.add(new com.telegram.telegrampromium.model.ChatSummary(
                                id, kind, title, preview, ts, unread, muted, pinned
                        ));
                    }

                    String next = str(resp, "next_cursor");
                    boolean more = resp.has("has_more") && resp.get("has_more").getAsBoolean();
                    int totalUnread = resp.has("total_unread") ? resp.get("total_unread").getAsInt() : 0;

                    return new PageResult(list, next, more, totalUnread);
                });
    }

    /** Toggle pin status on a chat. Returns true on CHAT_PIN_OK. */
    public CompletableFuture<Boolean> setPinned(String chatId, boolean pinned) {
        JsonObject req = new JsonObject();
        req.addProperty("type", "CHAT_PIN");
        req.addProperty("id",   Ids.req("pin"));
        req.addProperty("chatId", chatId);
        req.addProperty("pinned", pinned);

        return client.request(req)
                .orTimeout(10, TimeUnit.SECONDS)
                .thenApply(resp -> "CHAT_PIN_OK".equals(str(resp, "type")));
    }

    /* helpers */
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

    public static final class HistoryPage {
        public final java.util.List<Message> messages;
        public final String cursorNext;
        public final boolean hasMore;
        public HistoryPage(java.util.List<Message> messages, String cursorNext, boolean hasMore) {
            this.messages = messages; this.cursorNext = cursorNext; this.hasMore = hasMore;
        }
    }

    // Send result for text message
    public static final class SendResult {
        public final String clientTempId;
        public final String messageId;
        public final long ts;
        public SendResult(String clientTempId, String messageId, long ts) {
            this.clientTempId = clientTempId;
            this.messageId = messageId;
            this.ts = ts;
        }
    }

    /** Load chat message history (paged). */
    public java.util.concurrent.CompletableFuture<HistoryPage> history(
            String chatId, String cursor, int limit) {

        Objects.requireNonNull(chatId, "chatId");
        if (limit <= 0) limit = 30;

        com.google.gson.JsonObject req = new com.google.gson.JsonObject();
        req.addProperty("type", "REQ");
        req.addProperty("cmd", "messages_history");
        req.addProperty("id", Ids.req(chatId));
        req.addProperty("chatId", chatId);
        req.addProperty("limit", limit);
        if (cursor != null && !cursor.isBlank()) req.addProperty("cursor", cursor);

        return client.request(req).orTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .thenApply(resp -> {
                    String status = str(resp, "status");
                    if (!"ok".equalsIgnoreCase(status)) {
                        throw new RuntimeException("history failed: " + resp);
                    }
                    java.util.List<Message> list = new java.util.ArrayList<>();
                    com.google.gson.JsonArray arr = resp.has("messages") ? resp.getAsJsonArray("messages") : new com.google.gson.JsonArray();
                    for (JsonElement e : arr) {
                        if (!e.isJsonObject()) continue;
                        com.google.gson.JsonObject m = e.getAsJsonObject();
                        String id     = str(m, "id");
                        String from   = str(m, "from");
                        long   ts     = safeLong(m.get("ts"));
                        String k      = str(m, "kind");
                        String text   = str(m, "text");
                        boolean outgoing = m.has("outgoing") && m.get("outgoing").getAsBoolean();
                        MessageKind kind  = kindOrDefault(k);
                        // status optional in history; default to SENT
                        MessageStatus st = MessageStatus.SENT;
                        list.add(new Message(id, chatId, from, ts, kind, text, outgoing, st));
                    }
                    String next = str(resp, "cursorNext");
                    boolean hasMore = resp.has("hasMore") && resp.get("hasMore").getAsBoolean();
                    return new HistoryPage(list, next, hasMore);
                });
    }

    /** Send a text message to a chat. */
    public CompletableFuture<SendResult> sendText(String chatId, String text, String clientTempId) {
        String reqId = Ids.req("msg");
        JsonObject req = new JsonObject();
        req.addProperty("type", "MESSAGE_SEND");
        req.addProperty("id", reqId);
        req.addProperty("chat_id", chatId);
        JsonObject msg = new JsonObject();
        msg.addProperty("kind", "text");
        msg.addProperty("text", text);
        msg.addProperty("clientTempId", clientTempId);
        req.add("msg", msg);
        return client.request(req).thenApply(resp -> {
            String t = str(resp, "type");
            if (!"MESSAGE_SEND_OK".equals(t)) {
                throw new IllegalStateException("Unexpected response type: " + t);
            }
            String mid = str(resp, "message_id");
            long ts = safeLong(resp.get("ts"));
            String echoedTemp = resp.has("clientTempId")
                    ? resp.get("clientTempId").getAsString()
                    : clientTempId;
            return new SendResult(echoedTemp, mid, ts);
        });
    }

    /** نسخهٔ قبلی برای سازگاری؛ صرفاً tempId تولید می‌کند و به اورلود جدید می‌سپارد. */
    public CompletableFuture<SendResult> sendText(String chatId, String text) {
        String reqId = Ids.req("msg");
        String tmpId = "tmp-" + reqId;
        return sendText(chatId, text, tmpId);
    }
    // --- small helpers at the bottom of ChatAPI (reuse style of existing code) ---
    private static MessageKind kindOrDefault(String k) {
        if (k == null) return MessageKind.TEXT;
        return switch (k.toUpperCase()) {
            case "IMAGE" -> MessageKind.IMAGE;
            case "VIDEO" -> MessageKind.VIDEO;
            case "AUDIO" -> MessageKind.AUDIO;
            case "FILE"  -> MessageKind.FILE;
            default      -> MessageKind.TEXT;
        };
    }
}
