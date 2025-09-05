package telegramserver.protocol;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;

/**
 * Small helper for building responses and events.
 * - Responses MUST include the original "id" from the request.
 * - Events MUST NOT include "id".
 *
 * Keep payload simple: we return a Map serialized with Gson.
 */
public class SocketProtocol {
    private static final Gson gson = new Gson();

    /**
     * Build a response to a request (echoes id).
     * type: string name of response (e.g. "SEND_OK", "LOGIN_OK")
     * id: the id provided by client (UUID string)
     * body: additional fields (map) to include
     */
    public static String buildResponse(String type, String id, Map<String, Object> body) {
        Map<String, Object> out = new HashMap<>();
        out.put("type", type);
        out.put("id", id);
        if (body != null) out.putAll(body);
        return gson.toJson(out);
    }

    /**
     * Build a server event (no id).
     * eventName: e.g. "message_new", "presence_update"
     * body: event payload as map
     */
    public static String buildEvent(String eventName, Map<String, Object> body) {
        Map<String, Object> out = new HashMap<>();
        out.put("type", "EVENT");
        out.put("event", eventName);
        if (body != null) out.putAll(body);
        return gson.toJson(out);
    }

    public static String toJson(Object o) {
        return gson.toJson(o);
    }
}
