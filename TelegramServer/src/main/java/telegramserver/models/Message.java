package telegramserver.models;

import java.sql.*;
import java.time.LocalDateTime;

// Representing a message between users
public class Message {
    private int id;
    private String content;
    private int senderId;
    private int chatid;
    private int replyid;
    private Timestamp sentat;
    private boolean isdeleted;
    private boolean isedited;

    public Message(int id, String content, int senderId, int chatid, int replyid,Timestamp sentat, boolean isdeleted, boolean isedited) {
        this.id = id;
        this.content = content;
        this.senderId = senderId;
        this.chatid = chatid;
        this.replyid = replyid;
        this.sentat = sentat;
        this.isdeleted = isdeleted;
        this.isedited = isedited;
    }
    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }
    public String getContent() {
        return content;
    }
    public int getSenderId() {
        return senderId;
    }
    public int getChatid() {
        return chatid;
    }
    public int getReplyid() {
        return replyid;
    }
    public Timestamp getSentat() {
        return sentat;
    }
    public boolean isDeleted() {
        return isdeleted;
    }
    public boolean isIsedited() {
        return isedited;
    }
    public void addmessages(){
        String url = "jdbc:postgresql://localhost:5432/Telegram";
        String user = "postgres";
        String password = "AmirMahdiImani";

        String sql = "INSERT INTO messages (id,content,senderid,chatid,replyid,sentat,isdeleted,isedited) VALUES (?,?,?,?,?,?,?,?)";
        try {
            Connection con = DriverManager.getConnection(url, user, password);
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setInt(1, id);
            ps.setString(2, content);
            ps.setInt(3, senderId);
            ps.setInt(4, chatid);
            ps.setInt(5, replyid);
            ps.setTimestamp(6, sentat);
            ps.setBoolean(7, isdeleted);
            ps.setBoolean(8, isedited);
            ps.executeUpdate();
            con.close();
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // DB team will later map this to chat tables
}