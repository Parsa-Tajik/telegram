package telegramserver.services;

import java.sql.*;
import java.util.*;

public class ChannelService {
    private static int nextId = 1;
    private static final Map<Integer, Map<String, Object>> channels = new HashMap<>();
    private static final Map<Integer, Set<String>> channelMembers = new HashMap<>();
    private static String url = "jdbc:postgresql://localhost:5432/Telegram";
    private static String user = "postgres";
    private static String password = "AmirMahdiImani";

    public static int createChannel(String name, int ownerId) {
        int id = nextId++;

        Map<String, Object> info = new HashMap<>();
        info.put("id", id);
        info.put("name", name);
        info.put("ownerId", ownerId);

        channels.put(id, info);
        channelMembers.put(id, new HashSet<>());

        System.out.println("📢 Channel created: " + name + " (id=" + id + ")");

        // 👉 DB Team: INSERT INTO channels (id, name, ownerId) VALUES (...)
        //  backendteam: rewrite the variables as relate to database
        return id;
    }

    public static void joinChannel(int channelId, String username) {
        channelMembers.putIfAbsent(channelId, new HashSet<>());
        channelMembers.get(channelId).add(username);

        // 👉 DB Team: INSERT INTO channel_members (channelId, username)
        //  backendteam: rewrite the variables as relate to database
    }

    public static Set<String> getMembers(int channelId) {
        String sql = "SELECT username FROM channel_members WHERE channel_id";
        try {
            Connection con = DriverManager.getConnection(url, user, password);
            PreparedStatement ps = con.prepareStatement(sql);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        // 👉 DB Team: SELECT username FROM channel_members WHERE channelId=?
        //   complete /:
        return channelMembers.getOrDefault(channelId, new HashSet<>());
    }

    public static boolean getChannelInfo(int channelId) {
        String sql = "SELECT 1 FROM channels WHERE id = ? LIMIT 1";

        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, channelId);
            ResultSet rs = stmt.executeQuery();

            return rs.next();

        } catch (SQLException e) {
            e.printStackTrace();
        }
        // 👉 DB Team: SELECT * FROM channels WHERE id=?
        //  completed /:
        Map<String, Object> info = channels.getOrDefault(channelId, null);
        if (info != null) {
            info.put("members", new ArrayList<>(getMembers(channelId)));
        }
        return info;
    }
}
