package com.medicarex.repository;

import com.medicarex.config.DatabaseConnection;
import com.medicarex.model.HealthRecord;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class HealthRecordRepository {

    public List<HealthRecord> findByPatientUserId(int userId, String recordType) {
        List<HealthRecord> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT hr.* FROM health_records hr " +
                "JOIN patients p ON hr.patient_id = p.id " +
                "WHERE p.user_id = ? ");

        List<Object> params = new ArrayList<>();
        params.add(userId);

        if (recordType != null && !recordType.equalsIgnoreCase("All") && !recordType.trim().isEmpty()) {
            sql.append("AND LOWER(hr.record_type) = LOWER(?) ");
            params.add(recordType.trim());
        }

        sql.append("ORDER BY hr.record_date DESC");

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

    public HealthRecord findById(int id) {
        String sql = "SELECT hr.* FROM health_records hr WHERE hr.id = ?";
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

    public HealthRecord create(int patientUserId, String recordType, String title, String providerName, String description, String documentUrl, String fileSize, String recordDate) {
        String pSql = "SELECT id FROM patients WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pPs = conn.prepareStatement(pSql)) {
            pPs.setInt(1, patientUserId);
            int patientId = 1;
            try (ResultSet rs = pPs.executeQuery()) {
                if (rs.next()) patientId = rs.getInt("id");
            }

            String insSql = "INSERT INTO health_records (patient_id, record_type, title, provider_name, description, document_url, file_size, record_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insSql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, patientId);
                ps.setString(2, recordType);
                ps.setString(3, title);
                ps.setString(4, providerName);
                ps.setString(5, description);
                ps.setString(6, documentUrl != null ? documentUrl : "document.pdf");
                ps.setString(7, fileSize != null ? fileSize : "1.2 MB");
                ps.setString(8, recordDate != null ? recordDate : "2026-09-01");

                int rows = ps.executeUpdate();
                if (rows > 0) {
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) {
                            HealthRecord hr = new HealthRecord();
                            hr.setId(rs.getInt(1));
                            hr.setPatientId(patientId);
                            hr.setRecordType(recordType);
                            hr.setTitle(title);
                            hr.setProviderName(providerName);
                            hr.setDescription(description);
                            hr.setDocumentUrl(documentUrl);
                            hr.setFileSize(fileSize);
                            hr.setRecordDate(recordDate);
                            return hr;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private HealthRecord mapRow(ResultSet rs) throws SQLException {
        HealthRecord hr = new HealthRecord();
        hr.setId(rs.getInt("id"));
        hr.setPatientId(rs.getInt("patient_id"));
        hr.setRecordType(rs.getString("record_type"));
        hr.setTitle(rs.getString("title"));
        hr.setProviderName(rs.getString("provider_name"));
        hr.setDescription(rs.getString("description"));
        hr.setDocumentUrl(rs.getString("document_url"));
        hr.setFileSize(rs.getString("file_size"));
        hr.setRecordDate(rs.getString("record_date"));
        hr.setCreatedAt(rs.getString("created_at"));
        return hr;
    }
}
