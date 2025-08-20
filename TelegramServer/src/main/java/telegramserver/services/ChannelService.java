package telegramserver.services;

import java.util.*;

public class ChannelService {
    private static int nextId = 1;
    private static final Map<Integer, Map<String, Object>> channels = new HashMap<>();
    private static final Map<Integer, Set<String>> channelMembers = new HashMap<>();

    public static int createChannel(String name, int ownerId) {
        int id = nextId++;
        Map<String, Object> info = new HashMap<>();
        info.put("id", id);
        info.put("name", name);
        info.put("ownerId", ownerId);
        channels.put(id, info);
        channelMembers.put(id, new HashSet<>());
        System.out.println("📢 Channel created: " + name + " (id=" + id + ")");
        return id;
    }

    public static void joinChannel(int channelId, String username) {
        channelMembers.putIfAbsent(channelId, new HashSet<>());
        channelMembers.get(channelId).add(username);
    }

    public static Set<String> getMembers(int channelId) {
        return channelMembers.getOrDefault(channelId, new HashSet<>());
    }

    public static Map<String, Object> getChannelInfo(int channelId) {
        Map<String, Object> info = channels.getOrDefault(channelId, null);
        if (info != null) {
            info.put("members", new ArrayList<>(getMembers(channelId)));
        }
        return info;
    }
}
