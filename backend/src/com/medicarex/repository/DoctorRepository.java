package com.medicarex.repository;

import com.medicarex.config.DatabaseConnection;
import com.medicarex.model.Doctor;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DoctorRepository {

    public Doctor findByUserId(int userId) {
        String sql = "SELECT * FROM doctors WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("[DoctorRepository] findByUserId error: " + e.getMessage());
        }
        return null;
    }

    public Doctor findById(int id) {
        String sql = "SELECT * FROM doctors WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("[DoctorRepository] findById error: " + e.getMessage());
        }
        return null;
    }

    public boolean create(Doctor doc) {
        String sql = "INSERT INTO doctors (user_id, specialization, license_number, hospital_affiliation, experience_years, consultation_fee, rating, available_hours) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, doc.getUserId());
            stmt.setString(2, doc.getSpecialization());
            stmt.setString(3, doc.getLicenseNumber());
            stmt.setString(4, doc.getHospitalAffiliation());
            stmt.setInt(5, doc.getExperienceYears());
            stmt.setDouble(6, doc.getConsultationFee());
            stmt.setDouble(7, doc.getRating());
            stmt.setString(8, doc.getAvailableHours());
            int affected = stmt.executeUpdate();
            if (affected > 0) {
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        doc.setId(generatedKeys.getInt(1));
                    }
                }
                return true;
            }
        } catch (SQLException e) {
            System.err.println("[DoctorRepository] create error: " + e.getMessage());
        }
        return false;
    }

    public boolean update(Doctor doc) {
        String sql = "UPDATE doctors SET specialization = ?, license_number = ?, hospital_affiliation = ?, consultation_fee = ?, available_hours = ? WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, doc.getSpecialization());
            stmt.setString(2, doc.getLicenseNumber());
            stmt.setString(3, doc.getHospitalAffiliation());
            stmt.setDouble(4, doc.getConsultationFee());
            stmt.setString(5, doc.getAvailableHours());
            stmt.setInt(6, doc.getUserId());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[DoctorRepository] update error: " + e.getMessage());
        }
        return false;
    }

    public List<Map<String, Object>> findAllDoctorsWithDetails() {
        return searchDoctors(null, null);
    }

    public List<Map<String, Object>> searchDoctors(String query, String specialization) {
        List<Map<String, Object>> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT d.id as doctor_id, d.user_id, u.full_name as doctor_name, u.email, u.phone, u.avatar_url, " +
                "d.specialization, d.license_number, d.hospital_affiliation, d.experience_years, " +
                "d.consultation_fee, d.rating, d.available_hours, d.created_at " +
                "FROM doctors d " +
                "JOIN users u ON d.user_id = u.id " +
                "WHERE u.is_active = 1 "
        );

        List<Object> params = new ArrayList<>();

        if (specialization != null && !specialization.trim().isEmpty() && !"All".equalsIgnoreCase(specialization.trim())) {
            sql.append("AND LOWER(d.specialization) LIKE ? ");
            params.add("%" + specialization.trim().toLowerCase() + "%");
        }

        if (query != null && !query.trim().isEmpty()) {
            String q = "%" + query.trim().toLowerCase() + "%";
            sql.append("AND (LOWER(u.full_name) LIKE ? OR LOWER(d.specialization) LIKE ? OR LOWER(d.hospital_affiliation) LIKE ?) ");
            params.add(q);
            params.add(q);
            params.add(q);
        }

        sql.append("ORDER BY d.rating DESC, d.experience_years DESC");

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", rs.getInt("doctor_id"));
                    map.put("doctorId", rs.getInt("doctor_id"));
                    map.put("userId", rs.getInt("user_id"));
                    map.put("fullName", rs.getString("doctor_name"));
                    map.put("doctorName", rs.getString("doctor_name"));
                    map.put("name", rs.getString("doctor_name"));
                    map.put("email", rs.getString("email"));
                    map.put("phone", rs.getString("phone"));
                    map.put("avatarUrl", rs.getString("avatar_url"));
                    map.put("specialization", rs.getString("specialization"));
                    map.put("licenseNumber", rs.getString("license_number"));
                    map.put("hospitalAffiliation", rs.getString("hospital_affiliation"));
                    map.put("hospitalName", rs.getString("hospital_affiliation"));
                    map.put("experienceYears", rs.getInt("experience_years"));
                    map.put("consultationFee", rs.getDouble("consultation_fee"));
                    map.put("rating", rs.getDouble("rating"));
                    map.put("availableHours", rs.getString("available_hours"));
                    map.put("createdAt", rs.getString("created_at"));
                    list.add(map);
                }
            }
        } catch (SQLException e) {
            System.err.println("[DoctorRepository] searchDoctors error: " + e.getMessage());
        }
        return list;
    }

    private Doctor mapRow(ResultSet rs) throws SQLException {
        Doctor d = new Doctor();
        d.setId(rs.getInt("id"));
        d.setUserId(rs.getInt("user_id"));
        d.setSpecialization(rs.getString("specialization"));
        d.setLicenseNumber(rs.getString("license_number"));
        d.setHospitalAffiliation(rs.getString("hospital_affiliation"));
        d.setExperienceYears(rs.getInt("experience_years"));
        d.setConsultationFee(rs.getDouble("consultation_fee"));
        d.setRating(rs.getDouble("rating"));
        d.setAvailableHours(rs.getString("available_hours"));
        d.setCreatedAt(rs.getString("created_at"));
        return d;
    }
}
