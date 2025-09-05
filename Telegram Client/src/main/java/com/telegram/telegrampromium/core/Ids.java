package com.telegram.telegrampromium.core;

import java.util.UUID;

/**
 * Utility to generate unique request and client identifiers.
 */
public final class Ids {
    private Ids() {}

    /** Generates a unique request id with a short prefix for readability. */
    public static String req(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    /** Idempotency token for send operations. */
    public static String clientMsgId() {
        return "cli-" + UUID.randomUUID();
    }
}
