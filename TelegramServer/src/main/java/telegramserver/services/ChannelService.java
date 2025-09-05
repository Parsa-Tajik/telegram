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

    public static int createChannel(String chanelname,String description,Boolean isPublic) {
        int id = nextId++;

        Map<String, Object> info = new HashMap<>();
        info.put("id", id);
        info.put("name", chanelname);
        info.put("description", description);
        info.put("isPublic", isPublic);

        channels.put(id, info);

        System.out.println("📢 Channel created: " + chanelname + " (id=" + id + ")");

        String sql = "INSERT INTO channels (id, channel_name, description, is_public) VALUES (?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            ps.setString(2, chanelname);
            ps.setString(3, description);
            ps.setBoolean(4, isPublic);
            ps.executeUpdate();

        }
        catch (SQLException e) {
            e.printStackTrace();
        }
        // 👉 DB Team: INSERT INTO channels (id, name, ownerId) VALUES (...)
        //finish

        return id;
    }

    public static void joinChannel(String username,int id, int channelId,int Userid,Timestamp joinedat,Boolean isadmin,Boolean isPublic) {
        channelMembers.putIfAbsent(channelId, new HashSet<>());
        channelMembers.get(channelId).add(username);

        String sql = "INSERT INTO channel_members (id,channel_id,user_id,joined_at,is_admin,is_accepted) VALUES (?, ?,?,?,?,?)";
        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setInt(2, channelId);
            ps.setInt(3, Userid);
            ps.setTimestamp(4, joinedat);
            ps.setBoolean(5, isadmin);
            ps.setBoolean(6, isPublic);
            ps.executeUpdate();
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
        // 👉 DB Team: INSERT INTO channel_members (channelId, username)
        //finish
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
        String sql = "SELECT id, name, description FROM channels WHERE id=?";
        Map<String, Object> channelInfo = null;

        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, channelId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    channelInfo = new HashMap<>();
                    channelInfo.put("id", rs.getInt("id"));
                    channelInfo.put("name", rs.getString("name"));
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
