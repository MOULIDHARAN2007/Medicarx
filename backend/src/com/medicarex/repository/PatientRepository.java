package com.medicarex.repository;

import com.medicarex.config.DatabaseConnection;
import com.medicarex.model.Patient;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PatientRepository {

    public List<Map<String, Object>> findAllPatientsWithDetails() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT p.id as patient_id, p.user_id, u.email, u.full_name, u.phone, u.avatar_url, " +
                "p.dob, p.gender, p.blood_group, p.address, p.emergency_contact, p.insurance_provider, p.created_at " +
                "FROM patients p " +
                "JOIN users u ON p.user_id = u.id " +
                "WHERE u.role = 'PATIENT' " +
                "ORDER BY p.id ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> map = new HashMap<>();
                int patId = rs.getInt("patient_id");
                map.put("id", patId);
                map.put("patientId", patId);
                map.put("userId", rs.getInt("user_id"));
                map.put("email", rs.getString("email"));
                map.put("fullName", rs.getString("full_name"));
                map.put("name", rs.getString("full_name"));
                map.put("phone", rs.getString("phone"));
                map.put("avatarUrl", rs.getString("avatar_url"));
                map.put("dob", rs.getString("dob"));
                map.put("gender", rs.getString("gender"));
                map.put("bloodGroup", rs.getString("blood_group"));
                map.put("address", rs.getString("address"));
                map.put("emergencyContact", rs.getString("emergency_contact"));
                map.put("insuranceProvider", rs.getString("insurance_provider"));
                map.put("createdAt", rs.getString("created_at"));
                list.add(map);
            }
        } catch (SQLException e) {
            System.err.println("[PatientRepository] findAllPatientsWithDetails error: " + e.getMessage());
        }
        return list;
    }

    public Patient findByUserId(int userId) {
        String sql = "SELECT * FROM patients WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("[PatientRepository] findByUserId error: " + e.getMessage());
        }
        return null;
    }

    public Patient findById(int id) {
        String sql = "SELECT * FROM patients WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("[PatientRepository] findById error: " + e.getMessage());
        }
        return null;
    }

    public boolean create(Patient patient) {
        String sql = "INSERT OR REPLACE INTO patients (user_id, dob, gender, blood_group, address, emergency_contact, insurance_provider) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, patient.getUserId());
            stmt.setString(2, patient.getDob());
            stmt.setString(3, patient.getGender());
            stmt.setString(4, patient.getBloodGroup());
            stmt.setString(5, patient.getAddress());
            stmt.setString(6, patient.getEmergencyContact());
            stmt.setString(7, patient.getInsuranceProvider());
            int affected = stmt.executeUpdate();
            if (affected > 0) {
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        patient.setId(generatedKeys.getInt(1));
                    }
                }
                return true;
            }
        } catch (SQLException e) {
            System.err.println("[PatientRepository] create error: " + e.getMessage());
        }
        return false;
    }

    public boolean create(int userId, String dob, String gender, String bloodGroup, String address, String emergencyContact, String insuranceProvider) {
        return create(new Patient(0, userId, dob, gender, bloodGroup, address, emergencyContact, insuranceProvider));
    }

    public Patient getOrCreateByUserId(int userId) {
        Patient p = findByUserId(userId);
        if (p != null) return p;
        boolean ok = create(userId, "1995-01-01", "Other", "O+", "Bangalore, India", "+91 90000 00000", "Self-insured");
        if (ok) {
            return findByUserId(userId);
        }
        return null;
    }

    public boolean update(Patient patient) {
        String sql = "UPDATE patients SET dob = ?, gender = ?, blood_group = ?, address = ?, emergency_contact = ?, insurance_provider = ? WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, patient.getDob());
            stmt.setString(2, patient.getGender());
            stmt.setString(3, patient.getBloodGroup());
            stmt.setString(4, patient.getAddress());
            stmt.setString(5, patient.getEmergencyContact());
            stmt.setString(6, patient.getInsuranceProvider());
            stmt.setInt(7, patient.getUserId());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[PatientRepository] update error: " + e.getMessage());
        }
        return false;
    }

    private Patient mapRow(ResultSet rs) throws SQLException {
        Patient p = new Patient();
        p.setId(rs.getInt("id"));
        p.setUserId(rs.getInt("user_id"));
        p.setDob(rs.getString("dob"));
        p.setGender(rs.getString("gender"));
        p.setBloodGroup(rs.getString("blood_group"));
        p.setAddress(rs.getString("address"));
        p.setEmergencyContact(rs.getString("emergency_contact"));
        p.setInsuranceProvider(rs.getString("insurance_provider"));
        p.setCreatedAt(rs.getString("created_at"));
        return p;
    }
}
