package com.medicarex.repository;

import com.medicarex.config.DatabaseConnection;
import com.medicarex.model.AccountApplication;
import com.medicarex.util.JsonUtil;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ApplicationRepository {

    public AccountApplication createApplication(int userId, String name, String email, String phone, String role, Map<String, Object> details) {
        String detailsJson = details != null ? JsonUtil.toJson(details) : "{}";
        String sql = "INSERT INTO account_applications (user_id, applicant_name, email, phone, role, status, details_json) VALUES (?, ?, ?, ?, ?, 'PENDING', ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            ps.setString(2, name != null ? name.trim() : "");
            ps.setString(3, email != null ? email.trim().toLowerCase() : "");
            ps.setString(4, phone != null ? phone.trim() : "");
            ps.setString(5, role != null ? role.toUpperCase() : "DOCTOR");
            ps.setString(6, detailsJson);

            int rows = ps.executeUpdate();
            if (rows > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        int id = rs.getInt(1);
                        return findById(id);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[ApplicationRepository] createApplication error: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public List<AccountApplication> findByRole(String role, String status, String query) {
        List<AccountApplication> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM account_applications WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (role != null && !role.equalsIgnoreCase("All") && !role.trim().isEmpty()) {
            sql.append("AND UPPER(role) = UPPER(?) ");
            params.add(role.trim());
        }

        if (status != null && !status.equalsIgnoreCase("All") && !status.trim().isEmpty()) {
            sql.append("AND UPPER(status) = UPPER(?) ");
            params.add(status.trim());
        }

        if (query != null && !query.trim().isEmpty()) {
            sql.append("AND (LOWER(applicant_name) LIKE ? OR LOWER(email) LIKE ? OR LOWER(phone) LIKE ?) ");
            String q = "%" + query.trim().toLowerCase() + "%";
            params.add(q);
            params.add(q);
            params.add(q);
        }

        sql.append("ORDER BY id DESC");

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[ApplicationRepository] findByRole error: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    public AccountApplication findById(int id) {
        String sql = "SELECT * FROM account_applications WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public AccountApplication findLatestByUserId(int userId) {
        String sql = "SELECT * FROM account_applications WHERE user_id = ? ORDER BY id DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean approveApplication(int applicationId, int reviewerAdminId) {
        AccountApplication app = findById(applicationId);
        if (app == null) return false;
        if (!"PENDING".equalsIgnoreCase(app.getStatus())) {
            // Already approved or rejected
            return false;
        }

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            // 1. Update application record to APPROVED
            String appSql = "UPDATE account_applications SET status = 'APPROVED', reviewed_at = CURRENT_TIMESTAMP, reviewed_by = ? WHERE id = ? AND status = 'PENDING'";
            try (PreparedStatement ps = conn.prepareStatement(appSql)) {
                ps.setInt(1, reviewerAdminId);
                ps.setInt(2, applicationId);
                int rows = ps.executeUpdate();
                if (rows <= 0) {
                    conn.rollback();
                    return false;
                }
            }

            // 2. Activate user account in users table
            String userSql = "UPDATE users SET is_active = 1 WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(userSql)) {
                ps.setInt(1, app.getUserId());
                ps.executeUpdate();
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            System.err.println("[ApplicationRepository] approveApplication error: " + e.getMessage());
            e.printStackTrace();
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {}
            }
        }
        return false;
    }

    public boolean rejectApplication(int applicationId, String reason, int reviewerAdminId) {
        AccountApplication app = findById(applicationId);
        if (app == null) return false;
        if (!"PENDING".equalsIgnoreCase(app.getStatus())) {
            return false;
        }

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            // 1. Update application record to REJECTED
            String appSql = "UPDATE account_applications SET status = 'REJECTED', rejection_reason = ?, reviewed_at = CURRENT_TIMESTAMP, reviewed_by = ? WHERE id = ? AND status = 'PENDING'";
            try (PreparedStatement ps = conn.prepareStatement(appSql)) {
                ps.setString(1, reason != null && !reason.trim().isEmpty() ? reason.trim() : "Application criteria not met.");
                ps.setInt(2, reviewerAdminId);
                ps.setInt(3, applicationId);
                int rows = ps.executeUpdate();
                if (rows <= 0) {
                    conn.rollback();
                    return false;
                }
            }

            // 2. Ensure user account remains inactive
            String userSql = "UPDATE users SET is_active = 0 WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(userSql)) {
                ps.setInt(1, app.getUserId());
                ps.executeUpdate();
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            System.err.println("[ApplicationRepository] rejectApplication error: " + e.getMessage());
            e.printStackTrace();
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {}
            }
        }
        return false;
    }

    public int countPending(String role) {
        String sql = "SELECT COUNT(*) FROM account_applications WHERE UPPER(status) = 'PENDING' " +
                (role != null && !role.trim().isEmpty() ? "AND UPPER(role) = UPPER(?)" : "");
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (role != null && !role.trim().isEmpty()) {
                ps.setString(1, role.trim());
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    private AccountApplication mapRow(ResultSet rs) throws SQLException {
        AccountApplication a = new AccountApplication();
        a.setId(rs.getInt("id"));
        a.setUserId(rs.getInt("user_id"));
        a.setApplicantName(rs.getString("applicant_name"));
        a.setEmail(rs.getString("email"));
        a.setPhone(rs.getString("phone"));
        a.setRole(rs.getString("role"));
        a.setStatus(rs.getString("status"));
        a.setRejectionReason(rs.getString("rejection_reason"));
        String dJson = rs.getString("details_json");
        a.setDetailsJson(dJson);
        if (dJson != null && !dJson.trim().isEmpty()) {
            try {
                a.setDetails(JsonUtil.parseMap(dJson));
            } catch (Exception ignored) {}
        }
        a.setSubmittedAt(rs.getString("submitted_at"));
        a.setReviewedAt(rs.getString("reviewed_at"));
        int rb = rs.getInt("reviewed_by");
        if (!rs.wasNull()) {
            a.setReviewedBy(rb);
        }
        return a;
    }
}
