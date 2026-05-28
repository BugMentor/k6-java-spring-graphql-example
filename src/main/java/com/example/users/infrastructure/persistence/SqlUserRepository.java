package com.example.users.infrastructure.persistence;

import com.example.users.application.port.outgoing.UserRepositoryPort;
import com.example.users.domain.model.User;

import java.sql.*;
import java.util.Optional;

public class SqlUserRepository implements UserRepositoryPort {
    private final String url;
    private final String username;
    private final String password;

    public SqlUserRepository(String url, String username, String password) throws SQLException {
        this.url = url;
        this.username = username;
        this.password = password;
        initDatabase();
    }

    private void initDatabase() throws SQLException {
        try (Connection conn = DriverManager.getConnection(url, username, password);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS users (id SERIAL PRIMARY KEY, name VARCHAR(255), email VARCHAR(255))");
        }
    }

    @Override
    public User save(User user) {
        String sql = "INSERT INTO users (name, email) VALUES (?, ?) RETURNING id";
        try (Connection conn = DriverManager.getConnection(url, username, password);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, user.getName());
            pstmt.setString(2, user.getEmail());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new User(rs.getLong(1), user.getName(), user.getEmail());
            }
            throw new RuntimeException("Failed to save user");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<User> findById(Long id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(url, username, password);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return Optional.of(new User(rs.getLong("id"), rs.getString("name"), rs.getString("email")));
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
