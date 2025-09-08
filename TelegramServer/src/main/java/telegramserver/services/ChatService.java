package telegramserver.services;

import telegramserver.models.User;

import java.sql.*;
import java.util.*;

import static telegramserver.services.GroupService.resolveUserId;

// Tracks members of each chat
public class ChatService {
    private static final Map<Integer, Set<String>> chatMembers = new HashMap<>();
    private static String url = "jdbc:postgresql://localhost:5432/Telegram";
    private static String user = "postgres";
    private static String password = "AmirMahdiImani";
    public static Scanner scanner = new Scanner(System.in);


    public static boolean joinChat(int chatId, String username) {
        String sqlType = "SELECT type, id FROM chats WHERE chat_id = ?";
        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement ps = conn.prepareStatement(sqlType)) {

            ps.setInt(1, chatId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String type = rs.getString("type");
                    int dbChatId = rs.getInt("id");

                    Integer uid = resolveUserId(username);
                    if (uid == null) return false;

                    String insertSql;
                    if ("group".equalsIgnoreCase(type)) {
                        insertSql = "INSERT INTO group_members (group_id, user_id, joined_at, is_admin, is_accepted) VALUES (?, ?, ?, ?, ?)";
                    } else if ("channel".equalsIgnoreCase(type)) {
                        insertSql = "INSERT INTO channel_members (channel_id, user_id, joined_at, is_admin, is_accepted) VALUES (?, ?, ?, ?, ?)";
                    } else {
                        return false;
                    }

                    try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
                        insertPs.setInt(1, dbChatId);
                        insertPs.setInt(2, uid);
                        insertPs.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
                        insertPs.setBoolean(4, false);
                        insertPs.setBoolean(5, true);
                        insertPs.executeUpdate();
                        return true;
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }


    public static boolean leaveChat(int chatId, String username) {
        Integer uid = resolveUserId(username);
        if (uid == null) return false;

        String sqlType = "SELECT type, id FROM chats WHERE chat_id = ?";
        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement ps = conn.prepareStatement(sqlType)) {

            ps.setInt(1, chatId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String type = rs.getString("type");
                    int dbChatId = rs.getInt("id");

                    String deleteSql;
                    if ("group".equalsIgnoreCase(type)) {
                        deleteSql = "DELETE FROM group_members WHERE group_id = ? AND user_id = ?";
                    } else if ("channel".equalsIgnoreCase(type)) {
                        deleteSql = "DELETE FROM channel_members WHERE channel_id = ? AND user_id = ?";
                    } else {
                        return false;
                    }

                    try (PreparedStatement deletePs = conn.prepareStatement(deleteSql)) {
                        deletePs.setInt(1, dbChatId);
                        deletePs.setInt(2, uid);
                        int rows = deletePs.executeUpdate();
                        if (rows > 0) {
                            chatMembers.putIfAbsent(chatId, new HashSet<>());
                            chatMembers.get(chatId).remove(username);
                            return true;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }


    public static Set<String> getMembers(int chatId) {
        Set<String> members = new HashSet<>();
        String sqlType = "SELECT type, id FROM chats WHERE chat_id = ?";
        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement ps = conn.prepareStatement(sqlType)) {

            ps.setInt(1, chatId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String type = rs.getString("type");
                    int dbChatId = rs.getInt("id");
                    String selectSql;

                    if ("group".equalsIgnoreCase(type)) {
                        selectSql = "SELECT u.username FROM group_members gm JOIN users u ON gm.user_id = u.id WHERE gm.group_id = ?";
                    } else if ("channel".equalsIgnoreCase(type)) {
                        selectSql = "SELECT u.username FROM channel_members cm JOIN users u ON cm.user_id = u.id WHERE cm.channel_id = ?";
                    } else {
                        return members;
                    }

                    try (PreparedStatement selectPs = conn.prepareStatement(selectSql)) {
                        selectPs.setInt(1, dbChatId);
                        try (ResultSet rsMembers = selectPs.executeQuery()) {
                            while (rsMembers.next()) {
                                members.add(rsMembers.getString("username"));
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        chatMembers.put(chatId, members);
        return members;
    }

}