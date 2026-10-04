package com.medicarex.repository;

import com.medicarex.config.DatabaseConnection;
import com.medicarex.model.Appointment;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AppointmentRepository {

    public List<Appointment> findByDoctorUserId(int doctorUserId) {
        List<Appointment> list = new ArrayList<>();
        String sql = "SELECT a.*, u_doc.id as doctor_user_id, u_doc.full_name as doctor_name, " +
                "d.specialization as doctor_specialization, d.hospital_affiliation, u_doc.avatar_url as doctor_avatar, " +
                "u_pat.id as patient_user_id, u_pat.full_name as patient_name, u_pat.phone as patient_phone, u_pat.email as patient_email " +
                "FROM appointments a " +
                "JOIN doctors d ON a.doctor_id = d.id " +
                "JOIN users u_doc ON d.user_id = u_doc.id " +
                "JOIN patients p ON a.patient_id = p.id " +
                "JOIN users u_pat ON p.user_id = u_pat.id " +
                "WHERE u_doc.id = ? ORDER BY a.appointment_date ASC, a.appointment_time ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, doctorUserId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[AppointmentRepository] findByDoctorUserId error: " + e.getMessage());
        }
        return list;
    }

    public List<Appointment> findByPatientUserId(int patientUserId) {
        List<Appointment> list = new ArrayList<>();
        String sql = "SELECT a.*, u_doc.id as doctor_user_id, u_doc.full_name as doctor_name, " +
                "d.specialization as doctor_specialization, d.hospital_affiliation, u_doc.avatar_url as doctor_avatar, " +
                "u_pat.id as patient_user_id, u_pat.full_name as patient_name, u_pat.phone as patient_phone, u_pat.email as patient_email " +
                "FROM appointments a " +
                "JOIN doctors d ON a.doctor_id = d.id " +
                "JOIN users u_doc ON d.user_id = u_doc.id " +
                "JOIN patients p ON a.patient_id = p.id " +
                "JOIN users u_pat ON p.user_id = u_pat.id " +
                "WHERE u_pat.id = ? ORDER BY a.appointment_date ASC, a.appointment_time ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, patientUserId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[AppointmentRepository] findByPatientUserId error: " + e.getMessage());
        }
        return list;
    }

    public List<Appointment> findAllAppointments() {
        List<Appointment> list = new ArrayList<>();
        String sql = "SELECT a.*, u_doc.id as doctor_user_id, u_doc.full_name as doctor_name, " +
                "d.specialization as doctor_specialization, d.hospital_affiliation, u_doc.avatar_url as doctor_avatar, " +
                "u_pat.id as patient_user_id, u_pat.full_name as patient_name, u_pat.phone as patient_phone, u_pat.email as patient_email " +
                "FROM appointments a " +
                "JOIN doctors d ON a.doctor_id = d.id " +
                "JOIN users u_doc ON d.user_id = u_doc.id " +
                "JOIN patients p ON a.patient_id = p.id " +
                "JOIN users u_pat ON p.user_id = u_pat.id " +
                "ORDER BY a.appointment_date ASC, a.appointment_time ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            System.err.println("[AppointmentRepository] findAllAppointments error: " + e.getMessage());
        }
        return list;
    }

    public Appointment findById(int id) {
        String sql = "SELECT a.*, u_doc.id as doctor_user_id, u_doc.full_name as doctor_name, " +
                "d.specialization as doctor_specialization, d.hospital_affiliation, u_doc.avatar_url as doctor_avatar, " +
                "u_pat.id as patient_user_id, u_pat.full_name as patient_name, u_pat.phone as patient_phone, u_pat.email as patient_email " +
                "FROM appointments a " +
                "JOIN doctors d ON a.doctor_id = d.id " +
                "JOIN users u_doc ON d.user_id = u_doc.id " +
                "JOIN patients p ON a.patient_id = p.id " +
                "JOIN users u_pat ON p.user_id = u_pat.id " +
                "WHERE a.id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("[AppointmentRepository] findById error: " + e.getMessage());
        }
        return null;
    }

    public synchronized boolean isSlotBooked(int doctorId, String date, String time) {
        String sql = "SELECT COUNT(*) FROM appointments " +
                "WHERE doctor_id = ? AND appointment_date = ? AND appointment_time = ? " +
                "AND status NOT IN ('Cancelled', 'Rejected')";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, doctorId);
            ps.setString(2, date);
            ps.setString(3, time);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            System.err.println("[AppointmentRepository] isSlotBooked error: " + e.getMessage());
        }
        return false;
    }

    public List<String> getBookedSlots(int doctorId, String date) {
        List<String> booked = new ArrayList<>();
        String sql = "SELECT appointment_time FROM appointments " +
                "WHERE doctor_id = ? AND appointment_date = ? " +
                "AND status NOT IN ('Cancelled', 'Rejected')";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, doctorId);
            ps.setString(2, date);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    booked.add(rs.getString("appointment_time"));
                }
            }
        } catch (SQLException e) {
            System.err.println("[AppointmentRepository] getBookedSlots error: " + e.getMessage());
        }
        return booked;
    }

    public synchronized int create(int doctorId, int patientId, String date, String time, String type, String status, String notes) {
        // Enforce double booking check
        if (isSlotBooked(doctorId, date, time)) {
            System.err.println("[AppointmentRepository] Slot already booked for doctor " + doctorId + " on " + date + " at " + time);
            return -1;
        }

        String sql = "INSERT INTO appointments (doctor_id, patient_id, appointment_date, appointment_time, type, status, notes) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, doctorId);
            ps.setInt(2, patientId);
            ps.setString(3, date);
            ps.setString(4, time);
            ps.setString(5, (type != null && !type.trim().isEmpty()) ? type.trim() : "General Consultation");
            ps.setString(6, (status != null && !status.trim().isEmpty()) ? status.trim() : "Confirmed");
            ps.setString(7, notes != null ? notes.trim() : "");

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return generatedKeys.getInt(1);
                    }
                }
                return 1;
            }
        } catch (SQLException e) {
            System.err.println("[AppointmentRepository] create error: " + e.getMessage());
        }
        return 0;
    }

    public boolean create(int doctorId, int patientId, String date, String time, String type, String notes) {
        return create(doctorId, patientId, date, time, type, "Confirmed", notes) > 0;
    }

    public boolean updateStatus(int appointmentId, String status) {
        String sql = "UPDATE appointments SET status = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, appointmentId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[AppointmentRepository] updateStatus error: " + e.getMessage());
        }
        return false;
    }

    public boolean cancelAppointment(int appointmentId, int userId, String role) {
        String checkSql;
        if ("ADMIN".equalsIgnoreCase(role)) {
            checkSql = "SELECT id FROM appointments WHERE id = ?";
        } else if ("DOCTOR".equalsIgnoreCase(role)) {
            checkSql = "SELECT a.id FROM appointments a JOIN doctors d ON a.doctor_id = d.id WHERE a.id = ? AND d.user_id = ?";
        } else {
            // PATIENT
            checkSql = "SELECT a.id FROM appointments a JOIN patients p ON a.patient_id = p.id WHERE a.id = ? AND p.user_id = ?";
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            boolean isAuthorized = false;
            try (PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
                checkPs.setInt(1, appointmentId);
                if (!"ADMIN".equalsIgnoreCase(role)) {
                    checkPs.setInt(2, userId);
                }
                try (ResultSet rs = checkPs.executeQuery()) {
                    isAuthorized = rs.next();
                }
            }

            if (!isAuthorized) {
                System.err.println("[AppointmentRepository] cancelAppointment forbidden for user " + userId + " on appointment " + appointmentId);
                return false;
            }

            String updateSql = "UPDATE appointments SET status = 'Cancelled' WHERE id = ?";
            try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
                updatePs.setInt(1, appointmentId);
                return updatePs.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.err.println("[AppointmentRepository] cancelAppointment error: " + e.getMessage());
        }
        return false;
    }

    private Appointment mapRow(ResultSet rs) throws SQLException {
        Appointment a = new Appointment();
        a.setId(rs.getInt("id"));
        a.setDoctorId(rs.getInt("doctor_id"));
        a.setDoctorName(rs.getString("doctor_name"));

        try {
            a.setDoctorSpecialization(rs.getString("doctor_specialization"));
        } catch (SQLException ignored) {}
        try {
            a.setHospitalAffiliation(rs.getString("hospital_affiliation"));
        } catch (SQLException ignored) {}
        try {
            a.setDoctorAvatarUrl(rs.getString("doctor_avatar"));
        } catch (SQLException ignored) {}
        try {
            a.setDoctorUserId(rs.getInt("doctor_user_id"));
        } catch (SQLException ignored) {}

        a.setPatientId(rs.getInt("patient_id"));
        a.setPatientName(rs.getString("patient_name"));
        a.setPatientPhone(rs.getString("patient_phone"));

        try {
            a.setPatientUserId(rs.getInt("patient_user_id"));
        } catch (SQLException ignored) {}
        try {
            a.setPatientEmail(rs.getString("patient_email"));
        } catch (SQLException ignored) {}

        a.setAppointmentDate(rs.getString("appointment_date"));
        a.setAppointmentTime(rs.getString("appointment_time"));
        a.setType(rs.getString("type"));
        a.setStatus(rs.getString("status"));
        a.setNotes(rs.getString("notes"));
        a.setCreatedAt(rs.getString("created_at"));
        return a;
    }
}
