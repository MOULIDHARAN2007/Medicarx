package com.medicarex.repository;

import com.medicarex.config.DatabaseConnection;
import com.medicarex.model.Notification;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class NotificationRepository {

    public List<Notification> findByUserId(int userId, String type) {
        List<Notification> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM notifications WHERE user_id = ? ");
        List<Object> params = new ArrayList<>();
        params.add(userId);

        if (type != null && !type.equalsIgnoreCase("All") && !type.trim().isEmpty()) {
            sql.append("AND LOWER(type) = LOWER(?) ");
            params.add(type.trim());
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
            e.printStackTrace();
        }
        return list;
    }

    public boolean markAsRead(int notificationId, int userId) {
        String sql = "UPDATE notifications SET is_read = 1 WHERE id = ? AND user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, notificationId);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean markAllAsRead(int userId) {
        String sql = "UPDATE notifications SET is_read = 1 WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public int countUnread(int userId) {
        String sql = "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND (is_read = 0 OR is_read = FALSE)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public boolean create(int userId, String type, String title, String message, Integer referenceId, String referenceType) {
        String sql = "INSERT INTO notifications (user_id, type, title, message, reference_id, reference_type, is_read, time_group) VALUES (?, ?, ?, ?, ?, ?, 0, 'Today')";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, type);
            ps.setString(3, title);
            ps.setString(4, message);
            if (referenceId != null) ps.setInt(5, referenceId); else ps.setNull(5, Types.INTEGER);
            ps.setString(6, referenceType);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private Notification mapRow(ResultSet rs) throws SQLException {
        Notification n = new Notification();
        n.setId(rs.getInt("id"));
        n.setUserId(rs.getInt("user_id"));
        n.setType(rs.getString("type"));
        n.setTitle(rs.getString("title"));
        n.setMessage(rs.getString("message"));
        int refId = rs.getInt("reference_id");
        if (!rs.wasNull()) n.setReferenceId(refId);
        n.setReferenceType(rs.getString("reference_type"));
        n.setRead(rs.getBoolean("is_read"));
        n.setTimeGroup(rs.getString("time_group"));
        n.setCreatedAt(rs.getString("created_at"));
        return n;
    }
}
