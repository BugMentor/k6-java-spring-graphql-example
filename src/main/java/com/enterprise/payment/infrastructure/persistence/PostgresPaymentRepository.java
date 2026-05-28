package com.enterprise.payment.infrastructure.persistence;

import com.enterprise.payment.application.port.outgoing.PaymentRepositoryPort;
import com.enterprise.payment.domain.model.Payment;
import com.enterprise.payment.domain.model.PaymentSummary;

import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public class PostgresPaymentRepository implements PaymentRepositoryPort {
    private final String url;
    private final String username;
    private final String password;

    public PostgresPaymentRepository(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
        initDb();
    }

    private void initDb() {
        try (Connection conn = DriverManager.getConnection(url, username, password);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS payments (" +
                    "id UUID PRIMARY KEY, " +
                    "customer_id VARCHAR(255), " +
                    "amount DECIMAL, " +
                    "type VARCHAR(50), " +
                    "status VARCHAR(50), " +
                    "created_at TIMESTAMP, " +
                    "version BIGINT DEFAULT 0)");
        } catch (SQLException e) {
            // Table might already exist
        }
    }

    @Override
    public Payment save(Payment payment) {
        String sql = "INSERT INTO payments (id, customer_id, amount, type, status, created_at, version) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status, version = EXCLUDED.version";
        UUID id = payment.getId() != null ? payment.getId() : UUID.randomUUID();
        String customerId = payment.getUser() != null ? payment.getUser().getId().toString() : "unknown";
        try (Connection conn = DriverManager.getConnection(url, username, password);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setObject(1, id);
            pstmt.setString(2, customerId);
            pstmt.setBigDecimal(3, payment.getAmount());
            pstmt.setString(4, payment.getType());
            pstmt.setString(5, payment.getStatus());
            pstmt.setTimestamp(6, Timestamp.from(payment.getCreatedAt() != null ? payment.getCreatedAt() : Instant.now()));
            pstmt.setLong(7, payment.getVersion() != null ? payment.getVersion() : 0);
            pstmt.executeUpdate();
            return payment;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void saveAll(List<Payment> payments) {
        for (Payment payment : payments) {
            save(payment);
        }
    }

    @Override
    public Payment update(Payment payment) {
        String sql = "UPDATE payments SET status = ?, version = version + 1 WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(url, username, password);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, payment.getStatus());
            pstmt.setObject(2, payment.getId());
            int updated = pstmt.executeUpdate();
            if (updated == 0) {
                throw new RuntimeException("Payment not found: " + payment.getId());
            }
            return payment;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        String sql = "SELECT * FROM payments WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(url, username, password);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setObject(1, id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return Optional.of(mapRow(rs));
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Payment> findByCustomerIdAndStatus(String customerId, String status, int limit) {
        String sql = "SELECT * FROM payments WHERE customer_id = ? AND status = ? LIMIT ?";
        List<Payment> results = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(url, username, password);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, customerId);
            pstmt.setString(2, status);
            pstmt.setInt(3, limit);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                results.add(mapRow(rs));
            }
            return results;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public PaymentSummary getSummaryReport(Instant startDate, Instant endDate) {
        String sql = "SELECT status, SUM(amount) as total FROM payments WHERE created_at BETWEEN ? AND ? GROUP BY status";
        Map<String, BigDecimal> totals = new HashMap<>();
        try (Connection conn = DriverManager.getConnection(url, username, password);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setTimestamp(1, Timestamp.from(startDate));
            pstmt.setTimestamp(2, Timestamp.from(endDate));
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                totals.put(rs.getString("status"), rs.getBigDecimal("total"));
            }
            return new PaymentSummary(totals);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Payment> search(BigDecimal minAmount, BigDecimal maxAmount, String currency, String status, int page, int size) {
        String sql = "SELECT * FROM payments WHERE 1=1";
        if (minAmount != null) sql += " AND amount >= " + minAmount;
        if (maxAmount != null) sql += " AND amount <= " + maxAmount;
        if (status != null) sql += " AND status = '" + status + "'";
        sql += " LIMIT " + size + " OFFSET " + (page * size);

        List<Payment> results = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(url, username, password);
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                results.add(mapRow(rs));
            }
            return results;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Payment mapRow(ResultSet rs) throws SQLException {
        UUID id = (UUID) rs.getObject("id");
        String customerId = rs.getString("customer_id");
        BigDecimal amount = rs.getBigDecimal("amount");
        String type = rs.getString("type");
        String status = rs.getString("status");
        Instant createdAt = rs.getTimestamp("created_at").toInstant();
        long version = rs.getLong("version");

        // Create a minimal Payment object using reflection-free constructor
        // For search results, we return a basic payment without full domain relationships
        return new Payment(id, null, null, null, amount, type, status, createdAt, version);
    }
}
