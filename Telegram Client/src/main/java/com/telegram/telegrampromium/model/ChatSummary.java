package com.telegram.telegrampromium.model;

/**
 * Lightweight chat summary for list rows.
 * Immutable; include "pinned" to sort pinned chats on top.
 */
public final class ChatSummary {

    public enum Kind { PV, GROUP, CHANNEL }

    private final String  id;
    private final Kind    kind;
    private final String  title;
    private final String  lastPreview;   // last message preview
    private final long    lastTs;        // epoch millis
    private final int     unread;        // unread count
    private final boolean muted;
    private final boolean pinned;

    public ChatSummary(String id, Kind kind, String title,
                       String lastPreview, long lastTs, int unread,
                       boolean muted, boolean pinned) {
        this.id = id;
        this.kind = kind;
        this.title = title;
        this.lastPreview = lastPreview;
        this.lastTs = lastTs;
        this.unread = unread;
        this.muted = muted;
        this.pinned = pinned;
    }

    public String  id()         { return id; }
    public Kind    kind()       { return kind; }
    public String  title()      { return title; }
    public String  lastPreview(){ return lastPreview; }
    public long    lastTs()     { return lastTs; }
    public int     unread()     { return unread; }
    public boolean muted()      { return muted; }
    public boolean pinned()     { return pinned; }
}
