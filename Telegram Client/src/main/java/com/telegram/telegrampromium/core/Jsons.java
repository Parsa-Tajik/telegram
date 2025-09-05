package com.telegram.telegrampromium.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

/**
 * Gson configuration and small helpers for NDJSON use cases.
 */
public final class Jsons {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();

    private Jsons() {}

    public static Gson gson() { return GSON; }

    public static String toLine(JsonObject obj) {
        return GSON.toJson(obj) + "\n";
    }
}
