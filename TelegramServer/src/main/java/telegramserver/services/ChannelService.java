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

    public static int createChannel(String channelName, String description, boolean isPublic) {
        int id = -1;
        String sql = "INSERT INTO channels (channel_name, description, is_public) VALUES (?, ?, ?) RETURNING id";
        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, channelName);
            ps.setString(2, description);
            ps.setBoolean(3, isPublic);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                id = rs.getInt("id");
            }
            Map<String, Object> info = new HashMap<>();
            info.put("id", id);
            info.put("name", channelName);
            info.put("description", description);
            info.put("isPublic", isPublic);
            channels.put(id, info);
            System.out.println("📢 Channel created: " + channelName + " (id=" + id + ")");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return id;
    }


    public static void joinChannel(String username, int channelId, int userId, Timestamp joinedAt, boolean isAdmin, boolean isAccepted) {
        channelMembers.putIfAbsent(channelId, new HashSet<>());
        channelMembers.get(channelId).add(username);

        String sql = "INSERT INTO channel_members (channel_id, user_id, joined_at, is_admin, is_accepted) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, channelId);
            ps.setInt(2, userId);
            ps.setTimestamp(3, joinedAt);
            ps.setBoolean(4, isAdmin);
            ps.setBoolean(5, isAccepted);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public static Set<String> getMembers(int channelId) throws SQLException {
        String sql = "SELECT user_id FROM channel_members WHERE channel_id=?";
        Set<String> members = new HashSet<>();

        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, channelId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    members.add(rs.getString("user_id"));
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        // 👉 DB Team: SELECT username FROM channel_members WHERE channelId=?
        //finish
        return channelMembers.getOrDefault(channelId, new HashSet<>());
    }

    public static Map<String, Object> getChannelInfo(int channelId) throws SQLException {
        String sql = "SELECT id, channel_name, description FROM channels WHERE id=?";
        Map<String, Object> channelInfo = null;

        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, channelId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    channelInfo = new HashMap<>();
                    channelInfo.put("id", rs.getInt("id"));
                    channelInfo.put("name", rs.getString("channel_name"));
                    channelInfo.put("description", rs.getString("description"));
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        // 👉 DB Team: SELECT * FROM channels WHERE id=?
        //finish
        Map<String, Object> info = channels.getOrDefault(channelId, null);
        if (info != null) {
            info.put("members", new ArrayList<>(getMembers(channelId)));
        }
        return info;
    }
}
