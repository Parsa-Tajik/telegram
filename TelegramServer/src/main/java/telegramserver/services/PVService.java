package telegramserver.services;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/*
 * PVService
 * Manage private chats (pv table) and map to chat id for reuse.
 * This class will try to use DB (pv table) and fall back to memory.
 */
public class PVService {
    private static final String URL = "jdbc:postgresql://localhost:5432/Telegram";
    private static final String DB_USER = "postgres";
    private static final String DB_PASS = "AmirMahdiImani";

    private static int nextPvId = 3000;
    private static final Map<Integer, Map<String,Integer>> pvMap = new HashMap<>(); // pvId -> {user1,user2,chatId}
    private static final Map<String, Integer> pairToPv = new HashMap<>(); // "u1:u2" -> pvId

    // Start (or return existing) PV between two user IDs. Returns map { pvId, chatId }
    public static Map<String,Integer> startPv(int u1, int u2) {
        String pairKey = u1 < u2 ? (u1 + ":" + u2) : (u2 + ":" + u1);
        if (pairToPv.containsKey(pairKey)) {
            int pid = pairToPv.get(pairKey);
            int chatId = pvMap.get(pid).get("chatId");
            return Map.of("pvId", pid, "chatId", chatId);
        }

        // Try DB: see if pv table has entry
        String q = "SELECT id, chat_id FROM pv WHERE (user1_id = ? AND user2_id = ?) OR (user1_id = ? AND user2_id = ?)";
        try (Connection conn = DriverManager.getConnection(URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setInt(1, u1); ps.setInt(2, u2); ps.setInt(3, u2); ps.setInt(4, u1);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int pid = rs.getInt("id");
                int chatId = rs.getInt("chat_id"); // if your pv table stores chat_id
                Map<String,Integer> rec = new HashMap<>();
                rec.put("user1", u1);
                rec.put("user2", u2);
                rec.put("chatId", chatId);
                pvMap.put(pid, rec);
                pairToPv.put(pairKey, pid);
                return Map.of("pvId", pid, "chatId", chatId);
            }
        } catch (SQLException ignored) {}

        // Insert new PV (DB)
        String ins = "INSERT INTO pv (user1_id, user2_id, created_at) VALUES (?, ?, ?) RETURNING id";
        try (Connection conn = DriverManager.getConnection(URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(ins)) {
            ps.setInt(1, u1); ps.setInt(2, u2); ps.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int pid = rs.getInt(1);
                int chatId = 5000 + pid; // ephemeral mapping, DB team can define chat id
                Map<String,Integer> rec = new HashMap<>();
                rec.put("user1", u1);
                rec.put("user2", u2);
                rec.put("chatId", chatId);
                pvMap.put(pid, rec);
                pairToPv.put(pairKey, pid);
                return Map.of("pvId", pid, "chatId", chatId);
            }
        } catch (SQLException e) {
            // fallback in-memory
        }

        // final fallback - in-memory
        int pid = nextPvId++;
        int chatId = 5000 + pid;
        Map<String,Integer> rec = new HashMap<>();
        rec.put("user1", u1);
        rec.put("user2", u2);
        rec.put("chatId", chatId);
        pvMap.put(pid, rec);
        pairToPv.put(pairKey, pid);
        return Map.of("pvId", pid, "chatId", chatId);
    }
}
