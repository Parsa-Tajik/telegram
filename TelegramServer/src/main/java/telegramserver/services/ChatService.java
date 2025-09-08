package telegramserver.services;

import java.sql.*;
import java.util.*;

import static telegramserver.services.GroupService.resolveUserId;

public class ChatService {
    private static String url = "jdbc:postgresql://localhost:5432/Telegram";
    private static String user = "postgres";
    private static String password = "AmirMahdiImani";

    public static boolean joinChat(int chatId, String username) {
        Integer userId = resolveUserId(username);
        if (userId == null) return false;

        String sql = "INSERT INTO user_chats (userid, chatid, pinned_order, archived, last_message_id, unread_count) " +
                "VALUES (?, ?, 0, false, 0, 0) ON CONFLICT (user_id, chat_id) DO NOTHING";

        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ps.setInt(2, chatId);
            ps.executeUpdate();
            return true;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean leaveChat(int chatId, String username) {
        Integer userId = resolveUserId(username);
        if (userId == null) return false;

        String sql = "DELETE FROM user_chats WHERE userid = ? AND chatid = ?";
        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ps.setInt(2, chatId);
            int rows = ps.executeUpdate();
            return rows > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean archiveChat(int chatId, String username, boolean archive) {
        Integer userId = resolveUserId(username);
        if (userId == null) return false;

        String sql = "UPDATE user_chats SET archived = ? WHERE userid = ? AND chatid = ?";
        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setBoolean(1, archive);
            ps.setInt(2, userId);
            ps.setInt(3, chatId);
            int rows = ps.executeUpdate();
            return rows > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean pinChat(int chatId, String username, int pinOrder) {
        Integer userId = resolveUserId(username);
        if (userId == null) return false;

        String sql = "UPDATE user_chats SET pinned_order = ? WHERE userid = ? AND chatid = ?";
        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, pinOrder);
            ps.setInt(2, userId);
            ps.setInt(3, chatId);
            int rows = ps.executeUpdate();
            return rows > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean updateLastMessage(int chatId, int messageId) {
        String sql = "UPDATE user_chats SET last_message_id = ?, unread_count = unread_count + 1 WHERE chat_id = ?";
        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, messageId);
            ps.setInt(2, chatId);
            ps.executeUpdate();
            return true;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static Set<Integer> getUserChats(String username) {
        Integer userId = resolveUserId(username);
        if (userId == null) return Collections.emptySet();

        Set<Integer> chats = new HashSet<>();
        String sql = "SELECT chat_id FROM user_chats WHERE user_id = ? AND archived = false ORDER BY pinned_order DESC, chat_id DESC";

        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    chats.add(rs.getInt("chat_id"));
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return chats;
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
        return members;
    }


}
