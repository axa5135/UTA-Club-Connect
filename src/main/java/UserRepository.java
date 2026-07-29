package main.java;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class UserRepository {

    public User findByUsername(String username) throws SQLException {
        String sql = "SELECT username, password_hash, role FROM users WHERE username = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new User(rs.getString("username"), rs.getString("password_hash"), rs.getInt("role"));
                }
            }
        }
        return null;
    }

    public boolean usernameExists(String username) throws SQLException {
        return findByUsername(username) != null;
    }

    public void createUser(String username, String password, int role) throws SQLException {
        String sql = "INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, PasswordUtil.hash(password));
            stmt.setInt(3, role);
            stmt.executeUpdate();
        }
    }

    // Returns the logged-in User if the password matches, otherwise null
    public User checkLogin(String username, String password) throws SQLException {
        User user = findByUsername(username);
        if (user == null) {
            return null;
        }
        if (user.getPasswordHash().equals(PasswordUtil.hash(password))) {
            return user;
        }
        return null;
    }

    // Only promotes if the user is currently a Level 1 student
    public boolean promoteToClubPresident(String username) throws SQLException {
        String sql = "UPDATE users SET role = 2 WHERE username = ? AND role = 1";
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            return stmt.executeUpdate() > 0;
        }
    }

    public List<User> findAllStudents() throws SQLException {
        List<User> results = new ArrayList<>();
        String sql = "SELECT username, password_hash, role FROM users WHERE role = 1 ORDER BY username";
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                results.add(new User(rs.getString("username"), rs.getString("password_hash"), rs.getInt("role")));
            }
        }
        return results;
    }
}
