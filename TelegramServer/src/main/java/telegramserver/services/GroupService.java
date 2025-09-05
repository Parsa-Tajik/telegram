package telegramserver.services;

import java.sql.*;
import java.util.*;

/*
 * GroupService
 * - Adds group management layer (create/join/leave/getInfo)
 * - Uses DB where possible (same DB creds as project). If DB ops fail, falls back to in-memory map
 *
 * NOTE for DB team: the SQLs below follow the ERD names:
 *   groups (id, group_name, description, is_public)
 *   group_members (id, group_id, user_id, joined_at, is_admin, is_accepted)
 * Adjust columns if your schema uses different names.
 */
public class GroupService {
    private static final String URL = "jdbc:postgresql://localhost:5432/Telegram";
    private static final String DB_USER = "postgres";
    private static final String DB_PASS = "AmirMahdiImani";

    // in-memory fallback state (runtime only)
    private static int nextGroupId = 1000;
    private static final Map<Integer, Map<String,Object>> groups = new HashMap<>();
    private static final Map<Integer, Set<String>> groupMembers = new HashMap<>();
    private static final Map<Integer, Integer> groupChatMap = new HashMap<>(); // groupId -> chatId

    // Create a group, persist to DB if possible
    public static int createGroup(String name, String description, boolean isPublic, int ownerUserId) {
        // Try DB insert and get generated id
        String sql = "INSERT INTO groups (group_name, description, is_public) VALUES (?, ?, ?) RETURNING id";
        try (Connection conn = DriverManager.getConnection(URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, description);
            ps.setBoolean(3, isPublic);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int groupId = rs.getInt(1);
                // Also insert into group_members as owner/admin
                String minsert = "INSERT INTO group_members (group_id, user_id, joined_at, is_admin, is_accepted) VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement mps = conn.prepareStatement(minsert)) {
                    mps.setInt(1, groupId);
                    mps.setInt(2, ownerUserId);
                    mps.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
                    mps.setBoolean(4, true);
                    mps.setBoolean(5, true);
                    mps.executeUpdate();
                } catch (SQLException e) {
                    // non-fatal, keep going
                    e.printStackTrace();
                }
                // Map to an ephemeral chatId (DB team can decide how to map chats)
                int chatId = 2000 + groupId;
                groupChatMap.put(groupId, chatId);
                return groupId;
            }
        } catch (SQLException e) {
            // fallback to in-memory creation
            // System.err.println("GroupService DB error -> falling back to memory: " + e.getMessage());
        }

        // fallback in-memory
        int id = nextGroupId++;
        Map<String,Object> info = new HashMap<>();
        info.put("id", id);
        info.put("group_name", name);
        info.put("description", description);
        info.put("is_public", isPublic);
        info.put("ownerId", ownerUserId);
        groups.put(id, info);
        groupMembers.put(id, new HashSet<>(Set.of(String.valueOf(ownerUserId))));
        groupChatMap.put(id, 2000 + id);
        return id;
    }

    // Join group by username (resolve user id via DB if needed)
    public static boolean joinGroup(int groupId, String username) {
        // Try to resolve user id
        Integer uid = resolveUserId(username);
        if (uid == null) {
            // fallback: use username as string in runtime map
            groupMembers.putIfAbsent(groupId, new HashSet<>());
            groupMembers.get(groupId).add(username);
            return true;
        }

        // Insert into DB group_members
        String sql = "INSERT INTO group_members (group_id, user_id, joined_at, is_admin, is_accepted) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.setInt(2, uid);
            ps.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
            ps.setBoolean(4, false);
            ps.setBoolean(5, true);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            // fallback to memory
            groupMembers.putIfAbsent(groupId, new HashSet<>());
            groupMembers.get(groupId).add(username);
            return true;
        }
    }

    public static boolean leaveGroup(int groupId, String username) {
        Integer uid = resolveUserId(username);
        if (uid != null) {
            String sql = "DELETE FROM group_members WHERE group_id = ? AND user_id = ?";
            try (Connection conn = DriverManager.getConnection(URL, DB_USER, DB_PASS);
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, groupId);
                ps.setInt(2, uid);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                // fallback
            }
        }
        if (groupMembers.containsKey(groupId)) {
            groupMembers.get(groupId).remove(username);
        }
        return true;
    }

    public static Set<String> getMembers(int groupId) {
        Set<String> set = new HashSet<>();
        String sql = "SELECT u.username FROM group_members gm JOIN users u ON gm.user_id = u.id WHERE gm.group_id = ?";
        try (Connection conn = DriverManager.getConnection(URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) set.add(rs.getString("username"));
            if (!set.isEmpty()) return set;
        } catch (SQLException e) {
            // fallback to memory
        }
        return groupMembers.getOrDefault(groupId, new HashSet<>());
    }

    public static Map<String,Object> getGroupInfo(int groupId) {
        Map<String,Object> info = null;
        String sql = "SELECT id, group_name, description, is_public FROM groups WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                info = new HashMap<>();
                info.put("id", rs.getInt("id"));
                info.put("group_name", rs.getString("group_name"));
                info.put("description", rs.getString("description"));
                info.put("is_public", rs.getBoolean("is_public"));
                info.put("members", new ArrayList<>(getMembers(groupId)));
                return info;
            }
        } catch (SQLException e) {
            // fallback
        }
        // fallback to in-memory
        info = groups.get(groupId);
        if (info != null) {
            info.put("members", new ArrayList<>(getMembers(groupId)));
        }
        return info;
    }

    public static Integer getChatIdForGroup(int groupId) {
        Integer cid = groupChatMap.get(groupId);
        if (cid != null) return cid;
        // optional: try DB lookup if chats table exists
        return null;
    }

    // helper to get user id
    private static Integer resolveUserId(String username) {
        String q = "SELECT id FROM users WHERE username = ?";
        try (Connection conn = DriverManager.getConnection(URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("id");
        } catch (SQLException ignored) {}
        return null;
    }
}
