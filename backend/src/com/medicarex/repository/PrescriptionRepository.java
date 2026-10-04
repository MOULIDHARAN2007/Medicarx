package com.medicarex.repository;

import com.medicarex.config.DatabaseConnection;
import com.medicarex.model.Prescription;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PrescriptionRepository {

    public List<Prescription> findByPatientUserId(int userId) {
        List<Prescription> list = new ArrayList<>();
        String sql = "SELECT pr.*, d.specialization as doc_spec, d.license_number as doc_license, d.hospital_affiliation as doc_hosp, " +
                "u_doc.full_name as doctor_name, u_pat.full_name as patient_name, p.gender as patient_gender, p.dob as patient_dob " +
                "FROM prescriptions pr " +
                "JOIN patients p ON pr.patient_id = p.id " +
                "JOIN users u_pat ON p.user_id = u_pat.id " +
                "JOIN doctors d ON pr.doctor_id = d.id " +
                "JOIN users u_doc ON d.user_id = u_doc.id " +
                "WHERE u_pat.id = ? AND (pr.status IS NULL OR pr.status IN ('Active', 'Issued')) " +
                "ORDER BY pr.id DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Prescription rx = mapRow(rs);
                    rx.setItems(getItemsForPrescription(conn, rx.getId()));
                    list.add(rx);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<Prescription> findByDoctorUserId(int userId) {
        List<Prescription> list = new ArrayList<>();
        String sql = "SELECT pr.*, d.specialization as doc_spec, d.license_number as doc_license, d.hospital_affiliation as doc_hosp, " +
                "u_doc.full_name as doctor_name, u_pat.full_name as patient_name, p.gender as patient_gender, p.dob as patient_dob " +
                "FROM prescriptions pr " +
                "JOIN patients p ON pr.patient_id = p.id " +
                "JOIN users u_pat ON p.user_id = u_pat.id " +
                "JOIN doctors d ON pr.doctor_id = d.id " +
                "JOIN users u_doc ON d.user_id = u_doc.id " +
                "WHERE u_doc.id = ? " +
                "ORDER BY pr.id DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Prescription rx = mapRow(rs);
                    rx.setItems(getItemsForPrescription(conn, rx.getId()));
                    list.add(rx);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<Prescription> findAll() {
        List<Prescription> list = new ArrayList<>();
        String sql = "SELECT pr.*, d.specialization as doc_spec, d.license_number as doc_license, d.hospital_affiliation as doc_hosp, " +
                "u_doc.full_name as doctor_name, u_pat.full_name as patient_name, p.gender as patient_gender, p.dob as patient_dob " +
                "FROM prescriptions pr " +
                "JOIN patients p ON pr.patient_id = p.id " +
                "JOIN users u_pat ON p.user_id = u_pat.id " +
                "JOIN doctors d ON pr.doctor_id = d.id " +
                "JOIN users u_doc ON d.user_id = u_doc.id " +
                "ORDER BY pr.id DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Prescription rx = mapRow(rs);
                rx.setItems(getItemsForPrescription(conn, rx.getId()));
                list.add(rx);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public Prescription findById(int id) {
        String sql = "SELECT pr.*, d.specialization as doc_spec, d.license_number as doc_license, d.hospital_affiliation as doc_hosp, " +
                "u_doc.full_name as doctor_name, u_pat.full_name as patient_name, p.gender as patient_gender, p.dob as patient_dob " +
                "FROM prescriptions pr " +
                "JOIN patients p ON pr.patient_id = p.id " +
                "JOIN users u_pat ON p.user_id = u_pat.id " +
                "JOIN doctors d ON pr.doctor_id = d.id " +
                "JOIN users u_doc ON d.user_id = u_doc.id " +
                "WHERE pr.id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Prescription rx = mapRow(rs);
                    rx.setItems(getItemsForPrescription(conn, rx.getId()));
                    return rx;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Prescription create(int doctorUserId, int patientId, String diagnosis, String notes, List<Map<String, Object>> items) {
        String docSql = "SELECT d.id, u.full_name, d.specialization, d.license_number, d.hospital_affiliation " +
                "FROM doctors d JOIN users u ON d.user_id = u.id WHERE d.user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement docPs = conn.prepareStatement(docSql)) {
            docPs.setInt(1, doctorUserId);
            int doctorId = 0;
            String doctorFullName = null;
            String doctorLicense = null;
            try (ResultSet rs = docPs.executeQuery()) {
                if (rs.next()) {
                    doctorId = rs.getInt("id");
                    doctorFullName = rs.getString("full_name");
                    doctorLicense = rs.getString("license_number");
                }
            }

            if (doctorId == 0) {
                System.err.println("[PrescriptionRepository] Doctor account not found for user_id: " + doctorUserId);
                return null;
            }

            // Verify and resolve valid patient_id
            int resolvedPatientId = 0;
            int patientUserId = 0;
            String checkPatSql = "SELECT p.id, p.user_id FROM patients p WHERE p.id = ?";
            try (PreparedStatement checkPs = conn.prepareStatement(checkPatSql)) {
                checkPs.setInt(1, patientId);
                try (ResultSet checkRs = checkPs.executeQuery()) {
                    if (checkRs.next()) {
                        resolvedPatientId = checkRs.getInt("id");
                        patientUserId = checkRs.getInt("user_id");
                    }
                }
            }

            if (resolvedPatientId == 0) {
                String checkUserSql = "SELECT p.id, p.user_id FROM patients p WHERE p.user_id = ?";
                try (PreparedStatement uPs = conn.prepareStatement(checkUserSql)) {
                    uPs.setInt(1, patientId);
                    try (ResultSet uRs = uPs.executeQuery()) {
                        if (uRs.next()) {
                            resolvedPatientId = uRs.getInt("id");
                            patientUserId = uRs.getInt("user_id");
                        }
                    }
                }
            }

            if (resolvedPatientId == 0) {
                System.err.println("[PrescriptionRepository] Patient not found for id/user_id: " + patientId);
                return null;
            }

            String rxNumber = "RX-" + (int)(100000 + (Math.random() * 900000));
            String signature = (doctorFullName != null ? (doctorFullName.startsWith("Dr.") ? doctorFullName : "Dr. " + doctorFullName) : "Licensed Physician") + 
                    (doctorLicense != null ? " (Reg # " + doctorLicense + ")" : "");

            String insSql = "INSERT INTO prescriptions (prescription_number, doctor_id, patient_id, diagnosis, notes, doctor_signature, status, issue_date, valid_until) " +
                    "VALUES (?, ?, ?, ?, ?, ?, 'Issued', CURRENT_DATE, DATE('now', '+30 days'))";

            if ("MYSQL".equalsIgnoreCase(DatabaseConnection.getActiveDbType())) {
                insSql = "INSERT INTO prescriptions (prescription_number, doctor_id, patient_id, diagnosis, notes, doctor_signature, status, issue_date, valid_until) " +
                        "VALUES (?, ?, ?, ?, ?, ?, 'Issued', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 30 DAY))";
            }

            try (PreparedStatement ps = conn.prepareStatement(insSql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, rxNumber);
                ps.setInt(2, doctorId);
                ps.setInt(3, resolvedPatientId);
                ps.setString(4, diagnosis != null && !diagnosis.trim().isEmpty() ? diagnosis.trim() : "Clinical Consultation");
                ps.setString(5, notes != null && !notes.trim().isEmpty() ? notes.trim() : "Take medications strictly as directed.");
                ps.setString(6, signature);

                int rows = ps.executeUpdate();
                if (rows > 0) {
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) {
                            int rxId = rs.getInt(1);
                            if (items != null && !items.isEmpty()) {
                                String itemSql = "INSERT INTO prescription_items (prescription_id, medicine_id, medicine_name, dosage, frequency, duration, instructions, quantity) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                                for (Map<String, Object> item : items) {
                                    try (PreparedStatement itemPs = conn.prepareStatement(itemSql)) {
                                        itemPs.setInt(1, rxId);
                                        Object medId = item.get("medicineId");
                                        if (medId != null && ((Number) medId).intValue() > 0) {
                                            itemPs.setInt(2, ((Number) medId).intValue());
                                        } else {
                                            itemPs.setNull(2, Types.INTEGER);
                                        }
                                        itemPs.setString(3, String.valueOf(item.getOrDefault("medicineName", "Medicine")));
                                        itemPs.setString(4, String.valueOf(item.getOrDefault("dosage", "500mg")));
                                        itemPs.setString(5, String.valueOf(item.getOrDefault("frequency", "Twice daily")));
                                        itemPs.setString(6, String.valueOf(item.getOrDefault("duration", "5 Days")));
                                        itemPs.setString(7, String.valueOf(item.getOrDefault("instructions", "Take after meals")));
                                        Object qty = item.get("quantity");
                                        itemPs.setInt(8, qty != null ? ((Number) qty).intValue() : 10);
                                        itemPs.executeUpdate();
                                    }
                                }
                            }

                            // Create notification for patient
                            if (patientUserId > 0) {
                                String notifSql = "INSERT INTO notifications (user_id, type, title, message, reference_id, reference_type, is_read, time_group) VALUES (?, 'Prescription', 'New Digital Prescription Created', ?, ?, 'PRESCRIPTION', 0, 'Today')";
                                try (PreparedStatement nPs = conn.prepareStatement(notifSql)) {
                                    nPs.setInt(1, patientUserId);
                                    nPs.setString(2, (doctorFullName != null ? doctorFullName : "Your Doctor") + " has issued digital prescription #" + rxNumber + ".");
                                    nPs.setInt(3, rxId);
                                    nPs.executeUpdate();
                                } catch (Exception ignored) {}
                            }

                            return findById(rxId);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public int count() {
        String sql = "SELECT COUNT(*) FROM prescriptions";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    private List<Map<String, Object>> getItemsForPrescription(Connection conn, int prescriptionId) throws SQLException {
        List<Map<String, Object>> items = new ArrayList<>();
        String sql = "SELECT * FROM prescription_items WHERE prescription_id = ? ORDER BY id ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, prescriptionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", rs.getInt("id"));
                    item.put("medicineId", rs.getInt("medicine_id"));
                    item.put("medicineName", rs.getString("medicine_name"));
                    item.put("dosage", rs.getString("dosage"));
                    item.put("frequency", rs.getString("frequency"));
                    item.put("duration", rs.getString("duration"));
                    item.put("instructions", rs.getString("instructions"));
                    item.put("quantity", rs.getInt("quantity"));
                    items.add(item);
                }
            }
        }
        return items;
    }

    private Prescription mapRow(ResultSet rs) throws SQLException {
        Prescription rx = new Prescription();
        rx.setId(rs.getInt("id"));
        rx.setPrescriptionNumber(rs.getString("prescription_number"));
        rx.setDoctorId(rs.getInt("doctor_id"));
        rx.setDoctorName(rs.getString("doctor_name"));
        rx.setDoctorSpecialization(rs.getString("doc_spec"));
        rx.setDoctorLicense(rs.getString("doc_license"));
        rx.setHospitalAffiliation(rs.getString("doc_hosp"));
        rx.setPatientId(rs.getInt("patient_id"));
        rx.setPatientName(rs.getString("patient_name"));
        rx.setPatientGender(rs.getString("patient_gender"));
        rx.setPatientDob(rs.getString("patient_dob"));
        rx.setDiagnosis(rs.getString("diagnosis"));
        rx.setNotes(rs.getString("notes"));
        rx.setDoctorSignature(rs.getString("doctor_signature"));
        rx.setStatus(rs.getString("status"));
        rx.setIssueDate(rs.getString("issue_date"));
        rx.setValidUntil(rs.getString("valid_until"));
        rx.setCreatedAt(rs.getString("created_at"));
        return rx;
    }
}
