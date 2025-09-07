package telegramserver.models;

import java.sql.*;
import java.time.LocalDateTime;

public class User {
    private int id;
    private String firstName;
    private String secondName;
    private String bio;
    private String phoneNumber;
    private String username;
    private String tswHash; // hashed password
    private Timestamp lastSeen;
    private boolean isOnline;
    private Timestamp registeredAt;
    private String password;

    public User(int id, String firstName, String secondName, String bio, String phoneNumber, String username, String tswHash,Timestamp lastSeen, boolean isOnline, Timestamp registeredAt , String password) {
        this.id = id;
        this.firstName = firstName;
        this.secondName = secondName;
        this.bio = bio;
        this.phoneNumber = phoneNumber;
        this.username = username;
        this.tswHash = tswHash;
        this.lastSeen = lastSeen;
        this.isOnline = isOnline;
        this.registeredAt = registeredAt;
        this.password = password;
    }


    public int getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getSecondName() {
        return secondName;
    }

    public String getBio() {
        return bio;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getUsername() {
        return username;
    }

    public String getTswHash() {
        return tswHash;
    }

    public Timestamp getLastSeen() {
        return lastSeen;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public Timestamp getRegisteredAt() {
        return registeredAt;
    }

    public String getPassword() {return password;}

    public void adduser() {
        String url = "jdbc:postgresql://localhost:5432/Telegram";
        String user = "postgres";
        String password = "AmirMahdiImani";

        String sql = "INSERT INTO users (id, first_name, second_name, bio, phone_number, username, tsw_hash, last_seen, is_online, registered_at , password) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? ) ";

        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, this.id);
            stmt.setString(2, this.firstName);
            stmt.setString(3, this.secondName);
            stmt.setString(4, this.bio);
            stmt.setString(5, this.phoneNumber);
            stmt.setString(6, this.username);
            stmt.setString(7, this.tswHash);
            stmt.setTimestamp(8, this.lastSeen);
            stmt.setBoolean(9, this.isOnline);
            stmt.setTimestamp(10, this.registeredAt);
            stmt.setString(11, this.password);
            stmt.executeUpdate();
            System.out.println("User inserted successfully into the database.");

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
        }
    }


    // For DB team
    //finish
}