package com.medicarex.repository;

import com.medicarex.config.DatabaseConnection;

import java.sql.*;
import java.util.*;

public class PatientFeatureRepository {

    // ==========================================
    // 1. MEDICINE SUPPLY TRACKER
    // ==========================================
    public List<Map<String, Object>> getMedicineSupplyStatus(int patientId) {
        List<Map<String, Object>> list = new ArrayList<>();
        // Fetch active prescriptions with items
        String sql = "SELECT p.id as prescription_id, p.created_at, u.full_name as doctor_name, " +
                "pi.medicine_name, pi.dosage, pi.frequency, pi.duration, pi.quantity, pi.instructions " +
                "FROM prescriptions p " +
                "JOIN prescription_items pi ON p.id = pi.prescription_id " +
                "LEFT JOIN users u ON p.doctor_id = u.id " +
                "WHERE p.patient_id = ? " +
                "ORDER BY p.created_at DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                Set<String> seenMeds = new HashSet<>();
                while (rs.next()) {
                    String medName = rs.getString("medicine_name");
                    if (medName == null || seenMeds.contains(medName.toLowerCase().trim())) {
                        continue; // Focus on latest prescription per medicine
                    }
                    seenMeds.add(medName.toLowerCase().trim());

                    Map<String, Object> item = new HashMap<>();
                    item.put("prescriptionId", rs.getInt("prescription_id"));
                    item.put("doctorName", rs.getString("doctor_name"));
                    item.put("medicineName", medName);
                    item.put("dosage", rs.getString("dosage"));
                    String freq = rs.getString("frequency");
                    item.put("frequency", freq);
                    item.put("duration", rs.getString("duration"));
                    item.put("instructions", rs.getString("instructions"));

                    Timestamp prescribedTime = rs.getTimestamp("created_at");
                    item.put("prescribedDate", prescribedTime != null ? prescribedTime.toString() : "");

                    int qty = rs.getInt("quantity");
                    if (qty <= 0) qty = 30; // sensible default if not explicitly entered

                    // Parse daily consumption from frequency (e.g. 1-0-1 = 2, 1-1-1 = 3, Once daily = 1, BD = 2, TDS = 3)
                    double dailyDose = parseDailyDose(freq);
                    long daysElapsed = 0;
                    if (prescribedTime != null) {
                        long diffMs = System.currentTimeMillis() - prescribedTime.getTime();
                        daysElapsed = Math.max(0, diffMs / (1000L * 60 * 60 * 24));
                    }

                    double consumed = daysElapsed * dailyDose;
                    double remaining = Math.max(0, qty - consumed);
                    int daysLeft = dailyDose > 0 ? (int) Math.floor(remaining / dailyDose) : 30;

                    item.put("totalQuantity", qty);
                    item.put("dailyDose", dailyDose);
                    item.put("daysElapsed", daysElapsed);
                    item.put("remainingQuantity", Math.round(remaining * 10.0) / 10.0);
                    item.put("daysLeft", daysLeft);

                    String status = "OK";
                    if (daysLeft <= 0) {
                        status = "DEPLETED";
                    } else if (daysLeft <= 5) {
                        status = "LOW_STOCK";
                    } else if (daysLeft <= 10) {
                        status = "REFILL_SOON";
                    }
                    item.put("status", status);

                    // Estimated refill date
                    Calendar cal = Calendar.getInstance();
                    cal.add(Calendar.DAY_OF_YEAR, daysLeft);
                    item.put("estimatedRefillDate", String.format("%tF", cal));

                    list.add(item);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    private double parseDailyDose(String freq) {
        if (freq == null || freq.trim().isEmpty()) return 1.0;
        String f = freq.trim().toLowerCase();
        if (f.contains("1-0-1") || f.contains("twice") || f.contains("bid") || f.contains("bd") || f.contains("2 times") || f.contains("2x")) {
            return 2.0;
        } else if (f.contains("1-1-1") || f.contains("thrice") || f.contains("tid") || f.contains("tds") || f.contains("3 times") || f.contains("3x")) {
            return 3.0;
        } else if (f.contains("1-1-1-1") || f.contains("qid") || f.contains("4 times") || f.contains("4x")) {
            return 4.0;
        } else if (f.contains("1-0-0") || f.contains("0-1-0") || f.contains("0-0-1") || f.contains("once") || f.contains("od") || f.contains("daily")) {
            return 1.0;
        } else if (f.contains("alternate") || f.contains("every other")) {
            return 0.5;
        } else if (f.contains("weekly")) {
            return 0.14;
        }
        return 1.0;
    }

    // ==========================================
    // 2. HEALTH DATA SHARING CONTROL
    // ==========================================
    public List<Map<String, Object>> getSharingGrants(int patientId) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT pds.*, u.full_name as doctor_name, u.email as doctor_email " +
                "FROM patient_data_sharing pds " +
                "LEFT JOIN users u ON pds.doctor_id = u.id " +
                "WHERE pds.patient_id = ? " +
                "ORDER BY pds.id DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", rs.getInt("id"));
                    map.put("doctorId", rs.getInt("doctor_id"));
                    map.put("doctorName", rs.getString("doctor_name"));
                    map.put("doctorEmail", rs.getString("doctor_email"));
                    map.put("accessScope", rs.getString("access_scope"));
                    map.put("status", rs.getString("status"));
                    map.put("notes", rs.getString("notes"));
                    map.put("expiresAt", rs.getString("expires_at"));
                    map.put("createdAt", rs.getString("created_at"));
                    map.put("revokedAt", rs.getString("revoked_at"));
                    list.add(map);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public boolean createOrUpdateSharingGrant(int patientId, int doctorId, String scope, String notes, String expiresAt) {
        String checkSql = "SELECT id FROM patient_data_sharing WHERE patient_id = ? AND doctor_id = ?";
        try (Connection conn = DatabaseConnection.getConnection()) {
            Integer existingId = null;
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setInt(1, patientId);
                ps.setInt(2, doctorId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) existingId = rs.getInt("id");
                }
            }

            if (existingId != null) {
                String upd = "UPDATE patient_data_sharing SET access_scope = ?, status = 'ACTIVE', notes = ?, expires_at = ?, revoked_at = NULL, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(upd)) {
                    ps.setString(1, scope);
                    ps.setString(2, notes);
                    if (expiresAt != null && !expiresAt.isEmpty()) ps.setString(3, expiresAt);
                    else ps.setNull(3, Types.VARCHAR);
                    ps.setInt(4, existingId);
                    return ps.executeUpdate() > 0;
                }
            } else {
                String ins = "INSERT INTO patient_data_sharing (patient_id, doctor_id, access_scope, status, notes, expires_at) VALUES (?, ?, ?, 'ACTIVE', ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(ins)) {
                    ps.setInt(1, patientId);
                    ps.setInt(2, doctorId);
                    ps.setString(3, scope);
                    ps.setString(4, notes);
                    if (expiresAt != null && !expiresAt.isEmpty()) ps.setString(5, expiresAt);
                    else ps.setNull(5, Types.VARCHAR);
                    return ps.executeUpdate() > 0;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean revokeSharingGrant(int grantId, int patientId) {
        String sql = "UPDATE patient_data_sharing SET status = 'REVOKED', revoked_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND patient_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, grantId);
            ps.setInt(2, patientId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public List<Map<String, Object>> getDataAccessLogs(int patientId) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT al.*, u.full_name as doctor_name " +
                "FROM patient_data_access_logs al " +
                "LEFT JOIN users u ON al.accessed_by_user_id = u.id " +
                "WHERE al.patient_id = ? " +
                "ORDER BY al.id DESC LIMIT 50";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", rs.getInt("id"));
                    map.put("doctorName", rs.getString("doctor_name"));
                    map.put("recordType", rs.getString("record_type"));
                    map.put("recordId", rs.getInt("record_id"));
                    map.put("action", rs.getString("action"));
                    map.put("accessedAt", rs.getString("accessed_at"));
                    map.put("ipAddress", rs.getString("ip_address"));
                    list.add(map);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public void logDataAccess(int patientId, int doctorUserId, String recordType, int recordId, String action, String ip) {
        String sql = "INSERT INTO patient_data_access_logs (patient_id, accessed_by_user_id, record_type, record_id, action, ip_address) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, patientId);
            ps.setInt(2, doctorUserId);
            ps.setString(3, recordType);
            ps.setInt(4, recordId);
            ps.setString(5, action != null ? action : "VIEW");
            ps.setString(6, ip);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ==========================================
    // 3. EMERGENCY HEALTH CARD
    // ==========================================
    private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();

    public static String generateSecureToken() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder("EMG-");
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public Map<String, Object> getEmergencyCard(int patientId) {
        String sql = "SELECT ec.*, u.full_name as name, u.email, u.phone " +
                "FROM patient_emergency_card ec " +
                "JOIN users u ON ec.patient_id = u.id " +
                "WHERE ec.patient_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapEmergencyCard(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // If no card yet, automatically initialize one with a secure token and user details
        String uSql = "SELECT full_name as name, email, phone FROM users WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(uSql)) {
            ps.setInt(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String initialToken = generateSecureToken();
                    String ins = "INSERT INTO patient_emergency_card (patient_id, blood_group, emergency_contact_name, emergency_contact_phone, " +
                            "emergency_contact_relation, known_allergies, chronic_conditions, current_medications_summary, is_organ_donor, " +
                            "insurance_provider, insurance_policy_number, public_token) VALUES (?, 'O+', '', '', '', '', '', '', 0, '', '', ?)";
                    try (PreparedStatement insPs = conn.prepareStatement(ins)) {
                        insPs.setInt(1, patientId);
                        insPs.setString(2, initialToken);
                        insPs.executeUpdate();
                    } catch (SQLException ex) {
                        // Might already exist due to race condition
                    }
                    return getEmergencyCard(patientId);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Map<String, Object> regenerateEmergencyToken(int patientId) {
        String newToken = generateSecureToken();
        String sql = "UPDATE patient_emergency_card SET public_token = ?, updated_at = CURRENT_TIMESTAMP WHERE patient_id = ?";
        try (Connection conn = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, newToken);
                ps.setInt(2, patientId);
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    // Initialize card if not exists
                    getEmergencyCard(patientId);
                    try (PreparedStatement ps2 = conn.prepareStatement(sql)) {
                        ps2.setString(1, newToken);
                        ps2.setInt(2, patientId);
                        ps2.executeUpdate();
                    }
                }
            }
            return getEmergencyCard(patientId);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Map<String, Object> getPublicEmergencyCardByToken(String token) {
        if (token == null || token.trim().isEmpty()) return null;
        String sql = "SELECT ec.blood_group, ec.emergency_contact_name, ec.emergency_contact_phone, ec.emergency_contact_relation, " +
                "ec.known_allergies, ec.chronic_conditions, ec.current_medications_summary, ec.is_organ_donor, ec.insurance_provider, ec.insurance_policy_number, ec.updated_at, " +
                "u.full_name as name, u.phone " +
                "FROM patient_emergency_card ec " +
                "JOIN users u ON ec.patient_id = u.id " +
                "WHERE ec.public_token = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    String fullName = rs.getString("name");
                    map.put("patientName", fullName != null && !fullName.trim().isEmpty() ? fullName : "Patient Profile");
                    map.put("bloodGroup", rs.getString("blood_group"));
                    map.put("emergencyContactName", rs.getString("emergency_contact_name"));
                    map.put("emergencyContactPhone", rs.getString("emergency_contact_phone"));
                    map.put("emergencyContactRelation", rs.getString("emergency_contact_relation"));
                    map.put("allergies", rs.getString("known_allergies"));
                    map.put("chronicConditions", rs.getString("chronic_conditions"));
                    map.put("currentMedicationsSummary", rs.getString("current_medications_summary"));
                    map.put("isOrganDonor", rs.getBoolean("is_organ_donor"));
                    map.put("insuranceProvider", rs.getString("insurance_provider"));
                    map.put("insurancePolicyNumber", rs.getString("insurance_policy_number"));
                    map.put("lastUpdated", rs.getString("updated_at"));
                    return map;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Map<String, Object> scanEmergencyCard(String token, int requestingUserId, String requestingRole, String ip) {
        if (token == null || token.trim().isEmpty()) return null;
        String sql = "SELECT ec.*, u.full_name as name, u.phone, u.email " +
                "FROM patient_emergency_card ec " +
                "JOIN users u ON ec.patient_id = u.id " +
                "WHERE ec.public_token = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int patientId = rs.getInt("patient_id");
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("patientId", patientId);
                    map.put("patientName", rs.getString("name"));
                    map.put("phone", rs.getString("phone"));
                    map.put("bloodGroup", rs.getString("blood_group"));
                    map.put("emergencyContactName", rs.getString("emergency_contact_name"));
                    map.put("emergencyContactPhone", rs.getString("emergency_contact_phone"));
                    map.put("emergencyContactRelation", rs.getString("emergency_contact_relation"));
                    map.put("allergies", rs.getString("known_allergies"));
                    map.put("chronicConditions", rs.getString("chronic_conditions"));
                    map.put("currentMedicationsSummary", rs.getString("current_medications_summary"));
                    map.put("isOrganDonor", rs.getBoolean("is_organ_donor"));
                    map.put("insuranceProvider", rs.getString("insurance_provider"));
                    map.put("insurancePolicyNumber", rs.getString("insurance_policy_number"));
                    map.put("publicToken", rs.getString("public_token"));
                    map.put("lastUpdated", rs.getString("updated_at"));

                    // Audit Log Access
                    logDataAccess(patientId, requestingUserId, "EMERGENCY_QR_SCAN (" + requestingRole + ")", rs.getInt("id"), "SCAN", ip);

                    return map;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Map<String, Object> saveEmergencyCard(int patientId, Map<String, Object> data) {
        String checkSql = "SELECT id, public_token FROM patient_emergency_card WHERE patient_id = ?";
        try (Connection conn = DatabaseConnection.getConnection()) {
            Integer existingId = null;
            String publicToken = null;
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setInt(1, patientId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        existingId = rs.getInt("id");
                        publicToken = rs.getString("public_token");
                    }
                }
            }

            if (publicToken == null || publicToken.trim().isEmpty()) {
                publicToken = generateSecureToken();
            }

            String bloodGroup = (String) data.getOrDefault("bloodGroup", "O+");
            String cName = (String) data.getOrDefault("emergencyContactName", "");
            String cPhone = (String) data.getOrDefault("emergencyContactPhone", "");
            String cRel = (String) data.getOrDefault("emergencyContactRelation", "");
            String allergies = (String) data.getOrDefault("allergies", "");
            String chronic = (String) data.getOrDefault("chronicConditions", "");
            String meds = (String) data.getOrDefault("currentMedicationsSummary", "");
            boolean organDonor = Boolean.TRUE.equals(data.get("isOrganDonor")) || "true".equalsIgnoreCase(String.valueOf(data.get("isOrganDonor")));
            String insProv = (String) data.getOrDefault("insuranceProvider", "");
            String insPol = (String) data.getOrDefault("insurancePolicyNumber", "");

            if (existingId != null) {
                String upd = "UPDATE patient_emergency_card SET blood_group = ?, emergency_contact_name = ?, emergency_contact_phone = ?, " +
                        "emergency_contact_relation = ?, known_allergies = ?, chronic_conditions = ?, current_medications_summary = ?, " +
                        "is_organ_donor = ?, insurance_provider = ?, insurance_policy_number = ?, public_token = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(upd)) {
                    ps.setString(1, bloodGroup);
                    ps.setString(2, cName);
                    ps.setString(3, cPhone);
                    ps.setString(4, cRel);
                    ps.setString(5, allergies);
                    ps.setString(6, chronic);
                    ps.setString(7, meds);
                    ps.setBoolean(8, organDonor);
                    ps.setString(9, insProv);
                    ps.setString(10, insPol);
                    ps.setString(11, publicToken);
                    ps.setInt(12, existingId);
                    ps.executeUpdate();
                }
            } else {
                String ins = "INSERT INTO patient_emergency_card (patient_id, blood_group, emergency_contact_name, emergency_contact_phone, " +
                        "emergency_contact_relation, known_allergies, chronic_conditions, current_medications_summary, is_organ_donor, " +
                        "insurance_provider, insurance_policy_number, public_token) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(ins)) {
                    ps.setInt(1, patientId);
                    ps.setString(2, bloodGroup);
                    ps.setString(3, cName);
                    ps.setString(4, cPhone);
                    ps.setString(5, cRel);
                    ps.setString(6, allergies);
                    ps.setString(7, chronic);
                    ps.setString(8, meds);
                    ps.setBoolean(9, organDonor);
                    ps.setString(10, insProv);
                    ps.setString(11, insPol);
                    ps.setString(12, publicToken);
                    ps.executeUpdate();
                }
            }

            return getEmergencyCard(patientId);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private Map<String, Object> mapEmergencyCard(ResultSet rs) throws SQLException {
        Map<String, Object> map = new HashMap<>();
        map.put("id", rs.getInt("id"));
        map.put("patientId", rs.getInt("patient_id"));
        map.put("name", rs.getString("name"));
        map.put("phone", rs.getString("phone"));
        map.put("email", rs.getString("email"));
        map.put("bloodGroup", rs.getString("blood_group"));
        map.put("emergencyContactName", rs.getString("emergency_contact_name"));
        map.put("emergencyContactPhone", rs.getString("emergency_contact_phone"));
        map.put("emergencyContactRelation", rs.getString("emergency_contact_relation"));
        map.put("allergies", rs.getString("known_allergies"));
        map.put("chronicConditions", rs.getString("chronic_conditions"));
        map.put("currentMedicationsSummary", rs.getString("current_medications_summary"));
        map.put("isOrganDonor", rs.getBoolean("is_organ_donor"));
        map.put("insuranceProvider", rs.getString("insurance_provider"));
        map.put("insurancePolicyNumber", rs.getString("insurance_policy_number"));
        map.put("publicToken", rs.getString("public_token"));
        map.put("updatedAt", rs.getString("updated_at"));
        return map;
    }

    // ==========================================
    // 4. DOCTOR VISIT PREPARATION
    // ==========================================
    public List<Map<String, Object>> getVisitPreparations(int patientId) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT vp.*, u.full_name as doctor_name, a.appointment_date, a.appointment_time, a.status as appt_status " +
                "FROM visit_preparations vp " +
                "LEFT JOIN appointments a ON vp.appointment_id = a.id " +
                "LEFT JOIN users u ON COALESCE(vp.doctor_id, a.doctor_id) = u.id " +
                "WHERE vp.patient_id = ? " +
                "ORDER BY vp.id DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", rs.getInt("id"));
                    map.put("patientId", rs.getInt("patient_id"));
                    map.put("appointmentId", rs.getObject("appointment_id"));
                    map.put("doctorId", rs.getObject("doctor_id"));
                    map.put("doctorName", rs.getString("doctor_name"));
                    map.put("appointmentDate", rs.getString("appointment_date"));
                    map.put("appointmentTime", rs.getString("appointment_time"));
                    map.put("primarySymptoms", rs.getString("primary_symptoms"));
                    map.put("symptomDuration", rs.getString("symptom_duration"));
                    map.put("questionsForDoctor", rs.getString("questions_for_doctor"));
                    map.put("currentVitalsNotes", rs.getString("current_vitals_notes"));
                    map.put("medicationChanges", rs.getString("medication_changes"));
                    map.put("additionalNotes", rs.getString("additional_notes"));
                    map.put("createdAt", rs.getString("created_at"));
                    map.put("updatedAt", rs.getString("updated_at"));
                    list.add(map);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public boolean saveVisitPreparation(int patientId, Map<String, Object> data) {
        Integer id = data.get("id") != null && !String.valueOf(data.get("id")).isEmpty() ? Integer.parseInt(String.valueOf(data.get("id"))) : null;
        Integer apptId = data.get("appointmentId") != null && !String.valueOf(data.get("appointmentId")).isEmpty() ? Integer.parseInt(String.valueOf(data.get("appointmentId"))) : null;
        Integer docId = data.get("doctorId") != null && !String.valueOf(data.get("doctorId")).isEmpty() ? Integer.parseInt(String.valueOf(data.get("doctorId"))) : null;

        String symptoms = (String) data.getOrDefault("primarySymptoms", "");
        String duration = (String) data.getOrDefault("symptomDuration", "");
        String questions = (String) data.getOrDefault("questionsForDoctor", "");
        String vitals = (String) data.getOrDefault("currentVitalsNotes", "");
        String changes = (String) data.getOrDefault("medicationChanges", "");
        String notes = (String) data.getOrDefault("additionalNotes", "");

        try (Connection conn = DatabaseConnection.getConnection()) {
            if (id != null && id > 0) {
                String upd = "UPDATE visit_preparations SET appointment_id = ?, doctor_id = ?, primary_symptoms = ?, symptom_duration = ?, " +
                        "questions_for_doctor = ?, current_vitals_notes = ?, medication_changes = ?, additional_notes = ?, updated_at = CURRENT_TIMESTAMP " +
                        "WHERE id = ? AND patient_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(upd)) {
                    if (apptId != null) ps.setInt(1, apptId); else ps.setNull(1, Types.INTEGER);
                    if (docId != null) ps.setInt(2, docId); else ps.setNull(2, Types.INTEGER);
                    ps.setString(3, symptoms);
                    ps.setString(4, duration);
                    ps.setString(5, questions);
                    ps.setString(6, vitals);
                    ps.setString(7, changes);
                    ps.setString(8, notes);
                    ps.setInt(9, id);
                    ps.setInt(10, patientId);
                    return ps.executeUpdate() > 0;
                }
            } else {
                String ins = "INSERT INTO visit_preparations (patient_id, appointment_id, doctor_id, primary_symptoms, symptom_duration, " +
                        "questions_for_doctor, current_vitals_notes, medication_changes, additional_notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(ins)) {
                    ps.setInt(1, patientId);
                    if (apptId != null) ps.setInt(2, apptId); else ps.setNull(2, Types.INTEGER);
                    if (docId != null) ps.setInt(3, docId); else ps.setNull(3, Types.INTEGER);
                    ps.setString(4, symptoms);
                    ps.setString(5, duration);
                    ps.setString(6, questions);
                    ps.setString(7, vitals);
                    ps.setString(8, changes);
                    ps.setString(9, notes);
                    return ps.executeUpdate() > 0;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean deleteVisitPreparation(int prepId, int patientId) {
        String sql = "DELETE FROM visit_preparations WHERE id = ? AND patient_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, prepId);
            ps.setInt(2, patientId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    // ==========================================
    // 5. MEDICAL EXPENSE TRACKER & SUMMARY
    // ==========================================
    public Map<String, Object> getMedicalExpensesSummary(int patientId, String yearFilter) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> records = new ArrayList<>();

        double pharmacyTotal = 0.0;
        double consultationTotal = 0.0;

        // Get patient phone to match pharmacy walk-in bills if patient_id was not set
        String patientPhone = "";
        String phoneSql = "SELECT phone FROM users WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(phoneSql)) {
            ps.setInt(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) patientPhone = rs.getString("phone");
            }
        } catch (SQLException ignored) {}

        // Fetch Pharmacy Bills (support both pharmacy_bills and bills tables)
        String billSql = "SELECT b.id, b.bill_number as invoice_number, b.total_amount, b.payment_method, b.created_at, p.pharmacy_name " +
                "FROM bills b " +
                "LEFT JOIN pharmacies p ON b.pharmacy_id = p.id " +
                "WHERE (b.patient_id = ? OR (b.customer_phone IS NOT NULL AND b.customer_phone = ? AND b.customer_phone != '')) " +
                "ORDER BY b.created_at DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(billSql)) {
            ps.setInt(1, patientId);
            ps.setString(2, patientPhone != null ? patientPhone : "");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double amt = rs.getDouble("total_amount");
                    pharmacyTotal += amt;

                    Map<String, Object> rec = new HashMap<>();
                    rec.put("id", "BILL-" + rs.getInt("id"));
                    rec.put("category", "Pharmacy Medicines");
                    String pName = rs.getString("pharmacy_name");
                    rec.put("description", "Invoice #" + rs.getString("invoice_number") + (pName != null ? " (" + pName + ")" : ""));
                    rec.put("amount", amt);
                    rec.put("paymentMethod", rs.getString("payment_method"));
                    rec.put("date", rs.getString("created_at"));
                    records.add(rec);
                }
            }
        } catch (SQLException e) {
            // fallback gracefully
        }

        // Fetch Doctor Consultations (from Appointments with standard consultation fee e.g. 500)
        String apptSql = "SELECT a.id, a.appointment_date, a.appointment_time, a.status, u.full_name as doctor_name, d.specialization " +
                "FROM appointments a " +
                "JOIN users u ON a.doctor_id = u.id " +
                "LEFT JOIN doctors d ON d.user_id = u.id " +
                "WHERE a.patient_id = ? AND a.status IN ('Confirmed', 'COMPLETED', 'CONFIRMED', 'Accepted') " +
                "ORDER BY a.appointment_date DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(apptSql)) {
            ps.setInt(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double fee = 500.0; // Standard nominal consultation fee
                    consultationTotal += fee;

                    Map<String, Object> rec = new HashMap<>();
                    rec.put("id", "APPT-" + rs.getInt("id"));
                    rec.put("category", "Doctor Consultations");
                    String spec = rs.getString("specialization");
                    rec.put("description", "Consultation with Dr. " + rs.getString("doctor_name") + (spec != null ? " (" + spec + ")" : ""));
                    rec.put("amount", fee);
                    rec.put("paymentMethod", "Direct / Online");
                    rec.put("date", rs.getString("appointment_date") + " " + (rs.getString("appointment_time") != null ? rs.getString("appointment_time") : ""));
                    records.add(rec);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        double totalSpent = pharmacyTotal + consultationTotal;
        result.put("totalSpent", totalSpent);
        result.put("pharmacyTotal", pharmacyTotal);
        result.put("consultationTotal", consultationTotal);
        result.put("diagnosticsTotal", 0.0);
        result.put("expensesList", records);

        return result;
    }

    // ==========================================
    // 6. MEDICINE PURCHASE HISTORY (DETAILED)
    // ==========================================
    public List<Map<String, Object>> getMedicinePurchaseHistory(int patientId) {
        List<Map<String, Object>> list = new ArrayList<>();

        String patientPhone = "";
        String phoneSql = "SELECT phone FROM users WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(phoneSql)) {
            ps.setInt(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) patientPhone = rs.getString("phone");
            }
        } catch (SQLException ignored) {}

        String sql = "SELECT b.id as bill_id, b.bill_number as invoice_number, b.subtotal, b.tax as tax_amount, b.discount as discount_amount, " +
                "b.total_amount, b.payment_method, b.created_at, p.pharmacy_name, p.address as pharmacy_address, p.phone as pharmacy_phone " +
                "FROM bills b " +
                "LEFT JOIN pharmacies p ON b.pharmacy_id = p.id " +
                "WHERE (b.patient_id = ? OR (b.customer_phone IS NOT NULL AND b.customer_phone = ? AND b.customer_phone != '')) " +
                "ORDER BY b.created_at DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, patientId);
            ps.setString(2, patientPhone != null ? patientPhone : "");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> bill = new HashMap<>();
                    int billId = rs.getInt("bill_id");
                    bill.put("billId", billId);
                    bill.put("invoiceNumber", rs.getString("invoice_number"));
                    bill.put("subtotal", rs.getDouble("subtotal"));
                    bill.put("taxAmount", rs.getDouble("tax_amount"));
                    bill.put("discountAmount", rs.getDouble("discount_amount"));
                    bill.put("totalAmount", rs.getDouble("total_amount"));
                    bill.put("paymentMethod", rs.getString("payment_method"));
                    bill.put("createdAt", rs.getString("created_at"));
                    bill.put("pharmacyName", rs.getString("pharmacy_name"));
                    bill.put("pharmacyAddress", rs.getString("pharmacy_address"));
                    bill.put("pharmacyPhone", rs.getString("pharmacy_phone"));

                    // Fetch bill items
                    List<Map<String, Object>> items = new ArrayList<>();
                    String itemSql = "SELECT bi.*, m.name as medicine_name, m.generic_name, m.form as dosage_form " +
                            "FROM bill_items bi " +
                            "LEFT JOIN medicines m ON bi.medicine_id = m.id " +
                            "WHERE bi.bill_id = ?";
                    try (PreparedStatement psItem = conn.prepareStatement(itemSql)) {
                        psItem.setInt(1, billId);
                        try (ResultSet rsItem = psItem.executeQuery()) {
                            while (rsItem.next()) {
                                Map<String, Object> it = new HashMap<>();
                                it.put("id", rsItem.getInt("id"));
                                it.put("medicineName", rsItem.getString("medicine_name"));
                                it.put("genericName", rsItem.getString("generic_name"));
                                it.put("dosageForm", rsItem.getString("dosage_form"));
                                it.put("batchNumber", rsItem.getString("batch_number"));
                                it.put("quantity", rsItem.getInt("quantity"));
                                it.put("unitPrice", rsItem.getDouble("unit_price"));
                                it.put("totalPrice", rsItem.getDouble("subtotal"));
                                items.add(it);
                            }
                        }
                    }
                    bill.put("items", items);
                    list.add(bill);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }
}
