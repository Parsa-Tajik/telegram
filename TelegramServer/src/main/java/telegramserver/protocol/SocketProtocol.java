package telegramserver.protocol;

import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;

/**
 * Helper for building consistent JSON responses and events.
 *
 * Responses MUST include "type" and "id".
 * Events MUST have "type":"EVENT" and "event" key (no id).
 */
public class SocketProtocol {
    private static final Gson gson = new Gson();

    public static String buildResponse(String type, String id, Map<String, Object> payload) {
        Map<String, Object> out = new HashMap<>();
        out.put("type", type);
        out.put("id", id);
        if (payload != null) out.putAll(payload);
        return gson.toJson(out);
    }

    public static String buildEvent(String eventName, Map<String, Object> payload) {
        Map<String, Object> out = new HashMap<>();
        out.put("type", "EVENT");
        out.put("event", eventName);
        if (payload != null) out.putAll(payload);
        return gson.toJson(out);
    }
}
