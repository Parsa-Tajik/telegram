package com.telegram.telegrampromium.model;

/** Minimal message model for the ListView; will expand in 4.2/4.3. */
public final class Message {
    private final String id;
    private final String chatId;
    private final String from;
    private final long   ts;
    private final MessageKind kind;
    private final String text;
    private final boolean outgoing;
    private final MessageStatus status;

    public Message(String id, String chatId, String from, long ts,
                   MessageKind kind, String text, boolean outgoing, MessageStatus status) {
        this.id = id; this.chatId = chatId; this.from = from; this.ts = ts;
        this.kind = kind; this.text = text; this.outgoing = outgoing; this.status = status;
    }

    public String id() { return id; }
    public String chatId() { return chatId; }
    public String from() { return from; }
    public long ts() { return ts; }
    public MessageKind kind() { return kind; }
    public String text() { return text; }
    public boolean outgoing() { return outgoing; }
    public MessageStatus status() { return status; }
}
