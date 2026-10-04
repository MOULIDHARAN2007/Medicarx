package com.medicarex.repository;

import com.medicarex.config.DatabaseConnection;
import com.medicarex.model.TimelineEvent;
import com.medicarex.model.TreatmentGoal;
import com.medicarex.model.TreatmentProgressEntry;

import java.sql.*;
import java.util.*;

public class DoctorWorkspaceRepository {

    public boolean verifyDoctorPatientAccess(int doctorUserId, int patientId, String role) {
        if ("ADMIN".equalsIgnoreCase(role)) return true;
        if (!"DOCTOR".equalsIgnoreCase(role)) return false;

        String docSql = "SELECT id FROM doctors WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement docPs = conn.prepareStatement(docSql)) {
            docPs.setInt(1, doctorUserId);
            try (ResultSet rs = docPs.executeQuery()) {
                if (!rs.next()) return false;
                int doctorId = rs.getInt("id");

                // Check 1: Existing appointment relationship
                String apptSql = "SELECT 1 FROM appointments WHERE doctor_id = ? AND patient_id = ? LIMIT 1";
                try (PreparedStatement apptPs = conn.prepareStatement(apptSql)) {
                    apptPs.setInt(1, doctorId);
                    apptPs.setInt(2, patientId);
                    try (ResultSet apptRs = apptPs.executeQuery()) {
                        if (apptRs.next()) return true;
                    }
                }

                // Check 2: Existing prescription relationship
                String rxSql = "SELECT 1 FROM prescriptions WHERE doctor_id = ? AND patient_id = ? LIMIT 1";
                try (PreparedStatement rxPs = conn.prepareStatement(rxSql)) {
                    rxPs.setInt(1, doctorId);
                    rxPs.setInt(2, patientId);
                    try (ResultSet rxRs = rxPs.executeQuery()) {
                        if (rxRs.next()) return true;
                    }
                }

                // Check 3: Active patient in clinical roster
                String patCheckSql = "SELECT 1 FROM patients WHERE id = ? LIMIT 1";
                try (PreparedStatement patPs = conn.prepareStatement(patCheckSql)) {
                    patPs.setInt(1, patientId);
                    try (ResultSet patRs = patPs.executeQuery()) {
                        if (patRs.next()) return true;
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public Map<String, Object> getPatientWorkspaceSummary(int patientId) {
        Map<String, Object> summary = new HashMap<>();

        String sql = "SELECT p.*, u.full_name, u.email, u.phone, u.avatar_url " +
                "FROM patients p " +
                "JOIN users u ON p.user_id = u.id " +
                "WHERE p.id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    summary.put("patientId", rs.getInt("id"));
                    summary.put("userId", rs.getInt("user_id"));
                    summary.put("fullName", rs.getString("full_name"));
                    summary.put("name", rs.getString("full_name"));
                    summary.put("email", rs.getString("email"));
                    summary.put("phone", rs.getString("phone"));
                    summary.put("avatarUrl", rs.getString("avatar_url"));
                    summary.put("dob", rs.getString("dob"));
                    summary.put("gender", rs.getString("gender"));
                    summary.put("bloodGroup", rs.getString("blood_group"));
                    summary.put("address", rs.getString("address"));
                    summary.put("emergencyContact", rs.getString("emergency_contact"));
                    summary.put("insuranceProvider", rs.getString("insurance_provider"));
                }
            }

            if (summary.isEmpty()) return null;

            // Fetch Active Current Medications (from recent prescriptions)
            List<Map<String, Object>> activeMeds = new ArrayList<>();
            String medsSql = "SELECT pi.medicine_name, pi.dosage, pi.frequency, pi.duration, pi.instructions, " +
                    "p.prescription_number, p.issue_date, d.specialization, u.full_name as doctor_name " +
                    "FROM prescription_items pi " +
                    "JOIN prescriptions p ON pi.prescription_id = p.id " +
                    "JOIN doctors d ON p.doctor_id = d.id " +
                    "JOIN users u ON d.user_id = u.id " +
                    "WHERE p.patient_id = ? AND p.status = 'Active' " +
                    "ORDER BY p.issue_date DESC LIMIT 10";

            try (PreparedStatement medsPs = conn.prepareStatement(medsSql)) {
                medsPs.setInt(1, patientId);
                try (ResultSet rs = medsPs.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> m = new HashMap<>();
                        m.put("medicineName", rs.getString("medicine_name"));
                        m.put("dosage", rs.getString("dosage"));
                        m.put("frequency", rs.getString("frequency"));
                        m.put("duration", rs.getString("duration"));
                        m.put("instructions", rs.getString("instructions"));
                        m.put("prescriptionNumber", rs.getString("prescription_number"));
                        m.put("prescribedDate", rs.getString("issue_date"));
                        m.put("doctorName", rs.getString("doctor_name"));
                        activeMeds.add(m);
                    }
                }
            }
            summary.put("activeMedications", activeMeds);

            // Fetch Recent Consultations / Appointments
            List<Map<String, Object>> recentAppts = new ArrayList<>();
            String apptSql = "SELECT a.*, u.full_name as doctor_name, d.specialization " +
                    "FROM appointments a " +
                    "JOIN doctors d ON a.doctor_id = d.id " +
                    "JOIN users u ON d.user_id = u.id " +
                    "WHERE a.patient_id = ? ORDER BY a.appointment_date DESC LIMIT 5";

            try (PreparedStatement apptPs = conn.prepareStatement(apptSql)) {
                apptPs.setInt(1, patientId);
                try (ResultSet rs = apptPs.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> a = new HashMap<>();
                        a.put("id", rs.getInt("id"));
                        a.put("doctorName", rs.getString("doctor_name"));
                        a.put("specialization", rs.getString("specialization"));
                        a.put("date", rs.getString("appointment_date"));
                        a.put("time", rs.getString("appointment_time"));
                        a.put("type", rs.getString("type"));
                        a.put("status", rs.getString("status"));
                        a.put("notes", rs.getString("notes"));
                        recentAppts.add(a);
                    }
                }
            }
            summary.put("recentAppointments", recentAppts);

            // Fetch Allergies & Chronic Conditions from Emergency Profile
            int userId = summary.containsKey("userId") ? ((Number) summary.get("userId")).intValue() : 0;
            String ecSql = "SELECT known_allergies, chronic_conditions, current_medications_summary FROM patient_emergency_card WHERE patient_id = ? OR patient_id = ? LIMIT 1";
            try (PreparedStatement ecPs = conn.prepareStatement(ecSql)) {
                ecPs.setInt(1, patientId);
                ecPs.setInt(2, userId);
                try (ResultSet ecRs = ecPs.executeQuery()) {
                    if (ecRs.next()) {
                        String allergies = ecRs.getString("known_allergies");
                        String conditions = ecRs.getString("chronic_conditions");
                        String medSummary = ecRs.getString("current_medications_summary");
                        if (allergies != null && !allergies.trim().isEmpty()) summary.put("allergies", allergies);
                        if (conditions != null && !conditions.trim().isEmpty()) summary.put("chronicConditions", conditions);
                        if (medSummary != null && !medSummary.trim().isEmpty()) summary.put("medicationsSummary", medSummary);
                    }
                }
            } catch (Exception ignored) {}
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return summary;
    }

    public List<TimelineEvent> getPatientHealthTimeline(int patientId, int limit) {
        List<TimelineEvent> events = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection()) {

            // 1. Prescriptions
            String rxSql = "SELECT p.*, u.full_name as doctor_name, d.specialization " +
                    "FROM prescriptions p " +
                    "JOIN doctors d ON p.doctor_id = d.id " +
                    "JOIN users u ON d.user_id = u.id " +
                    "WHERE p.patient_id = ? ORDER BY p.issue_date DESC";

            try (PreparedStatement ps = conn.prepareStatement(rxSql)) {
                ps.setInt(1, patientId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int rxId = rs.getInt("id");
                        String rxNum = rs.getString("prescription_number");
                        String date = rs.getString("issue_date");
                        String docName = rs.getString("doctor_name");
                        String diagnosis = rs.getString("diagnosis");

                        // Get line items count & names
                        List<String> medNames = new ArrayList<>();
                        String itemSql = "SELECT medicine_name, dosage FROM prescription_items WHERE prescription_id = ?";
                        try (PreparedStatement iPs = conn.prepareStatement(itemSql)) {
                            iPs.setInt(1, rxId);
                            try (ResultSet iRs = iPs.executeQuery()) {
                                while (iRs.next()) {
                                    medNames.add(iRs.getString("medicine_name") + " (" + iRs.getString("dosage") + ")");
                                }
                            }
                        }

                        TimelineEvent ev = new TimelineEvent();
                        ev.setId("RX-" + rxId);
                        ev.setEventType("PRESCRIPTION");
                        ev.setEventDate(date);
                        ev.setDisplayDate(date);
                        ev.setTitle("Digital Prescription #" + rxNum);
                        ev.setSubtitle("Diagnosis: " + diagnosis);
                        ev.setBadge("Prescription (" + medNames.size() + " meds)");
                        ev.setBadgeColor("blue");
                        ev.setDoctorName(docName);
                        ev.setDescription(medNames.isEmpty() ? "No medicines prescribed" : String.join(", ", medNames));
                        ev.setReferenceId(String.valueOf(rxId));
                        ev.getDetails().put("prescriptionId", rxId);
                        ev.getDetails().put("diagnosis", diagnosis);
                        ev.getDetails().put("notes", rs.getString("notes"));
                        ev.getDetails().put("medicines", medNames);
                        events.add(ev);
                    }
                }
            }

            // 2. Appointments / Consultations
            String apptSql = "SELECT a.*, u.full_name as doctor_name, d.specialization " +
                    "FROM appointments a " +
                    "JOIN doctors d ON a.doctor_id = d.id " +
                    "JOIN users u ON d.user_id = u.id " +
                    "WHERE a.patient_id = ? ORDER BY a.appointment_date DESC";

            try (PreparedStatement ps = conn.prepareStatement(apptSql)) {
                ps.setInt(1, patientId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int apptId = rs.getInt("id");
                        String date = rs.getString("appointment_date");
                        String time = rs.getString("appointment_time");
                        String type = rs.getString("type");
                        String status = rs.getString("status");
                        String notes = rs.getString("notes");
                        String docName = rs.getString("doctor_name");

                        TimelineEvent ev = new TimelineEvent();
                        ev.setId("APPT-" + apptId);
                        ev.setEventType("APPOINTMENT");
                        ev.setEventDate(date + " " + (time != null ? time : "09:00"));
                        ev.setDisplayDate(date);
                        ev.setTitle(type != null ? type : "Consultation");
                        ev.setSubtitle(docName + " (" + time + ")");
                        ev.setBadge(status != null ? status : "Scheduled");
                        ev.setBadgeColor("Completed".equalsIgnoreCase(status) ? "green" : "purple");
                        ev.setDoctorName(docName);
                        ev.setDescription(notes != null && !notes.trim().isEmpty() ? notes : "Clinical visit recorded.");
                        ev.setReferenceId(String.valueOf(apptId));
                        events.add(ev);
                    }
                }
            }

            // 3. Health Records & Lab Reports
            String hrSql = "SELECT * FROM health_records WHERE patient_id = ? ORDER BY record_date DESC";
            try (PreparedStatement ps = conn.prepareStatement(hrSql)) {
                ps.setInt(1, patientId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int hrId = rs.getInt("id");
                        String type = rs.getString("record_type");
                        String title = rs.getString("title");
                        String provider = rs.getString("provider_name");
                        String desc = rs.getString("description");
                        String date = rs.getString("record_date");
                        String docUrl = rs.getString("document_url");

                        TimelineEvent ev = new TimelineEvent();
                        ev.setId("HR-" + hrId);
                        ev.setEventType(type != null && type.toLowerCase().contains("lab") ? "LAB_REPORT" : "HEALTH_RECORD");
                        ev.setEventDate(date);
                        ev.setDisplayDate(date);
                        ev.setTitle(title != null ? title : "Medical Record");
                        ev.setSubtitle(provider != null ? provider : "Healthcare Provider");
                        ev.setBadge(type != null ? type : "Report");
                        ev.setBadgeColor(type != null && type.toLowerCase().contains("lab") ? "amber" : "green");
                        ev.setDoctorName(provider);
                        ev.setDescription(desc != null ? desc : "");
                        ev.setReferenceId(String.valueOf(hrId));
                        ev.getDetails().put("documentUrl", docUrl);
                        events.add(ev);
                    }
                }
            }

            // 4. Treatment Progress Entries
            String tpSql = "SELECT tpe.*, tg.title as goal_title, tg.metric_name, tg.unit " +
                    "FROM treatment_progress_entries tpe " +
                    "JOIN treatment_goals tg ON tpe.goal_id = tg.id " +
                    "WHERE tg.patient_id = ? ORDER BY tpe.recorded_date DESC";

            try (PreparedStatement ps = conn.prepareStatement(tpSql)) {
                ps.setInt(1, patientId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int tpeId = rs.getInt("id");
                        String date = rs.getString("recorded_date");
                        String val = rs.getString("value");
                        String goalTitle = rs.getString("goal_title");
                        String metric = rs.getString("metric_name");
                        String unit = rs.getString("unit");
                        String notes = rs.getString("notes");
                        String createdBy = rs.getString("created_by");

                        TimelineEvent ev = new TimelineEvent();
                        ev.setId("TPE-" + tpeId);
                        ev.setEventType("TREATMENT_UPDATE");
                        ev.setEventDate(date);
                        ev.setDisplayDate(date);
                        ev.setTitle("Treatment Milestone: " + goalTitle);
                        ev.setSubtitle("Recorded " + metric + ": " + val + (unit != null ? " " + unit : ""));
                        ev.setBadge("Progress Entry");
                        ev.setBadgeColor("green");
                        ev.setDoctorName(createdBy != null ? createdBy : "Doctor");
                        ev.setDescription(notes != null ? notes : "Milestone recorded.");
                        ev.setReferenceId(String.valueOf(tpeId));
                        events.add(ev);
                    }
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Sort descending by date
        Collections.sort(events);

        if (limit > 0 && events.size() > limit) {
            return events.subList(0, limit);
        }
        return events;
    }

    public List<TreatmentGoal> getTreatmentGoals(int patientId) {
        List<TreatmentGoal> list = new ArrayList<>();

        String sql = "SELECT tg.*, u.full_name as doctor_name " +
                "FROM treatment_goals tg " +
                "JOIN doctors d ON tg.doctor_id = d.id " +
                "JOIN users u ON d.user_id = u.id " +
                "WHERE tg.patient_id = ? " +
                "ORDER BY tg.id DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TreatmentGoal goal = new TreatmentGoal();
                    goal.setId(rs.getInt("id"));
                    goal.setPatientId(rs.getInt("patient_id"));
                    goal.setDoctorId(rs.getInt("doctor_id"));
                    goal.setDoctorName(rs.getString("doctor_name"));
                    goal.setTitle(rs.getString("title"));
                    goal.setMetricName(rs.getString("metric_name"));
                    goal.setUnit(rs.getString("unit"));
                    goal.setBaselineValue(rs.getString("baseline_value"));
                    goal.setTargetValue(rs.getString("target_value"));
                    goal.setStatus(rs.getString("status"));
                    goal.setCreatedAt(rs.getString("created_at"));
                    goal.setUpdatedAt(rs.getString("updated_at"));

                    // Load progress entries
                    goal.setProgressEntries(getGoalProgressEntries(conn, goal.getId()));

                    if (!goal.getProgressEntries().isEmpty()) {
                        TreatmentProgressEntry latest = goal.getProgressEntries().get(goal.getProgressEntries().size() - 1);
                        goal.setLatestValue(latest.getValue());
                    } else {
                        goal.setLatestValue(goal.getBaselineValue());
                    }

                    list.add(goal);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return list;
    }

    private List<TreatmentProgressEntry> getGoalProgressEntries(Connection conn, int goalId) throws SQLException {
        List<TreatmentProgressEntry> entries = new ArrayList<>();
        String sql = "SELECT * FROM treatment_progress_entries WHERE goal_id = ? ORDER BY recorded_date ASC, id ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, goalId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TreatmentProgressEntry entry = new TreatmentProgressEntry();
                    entry.setId(rs.getInt("id"));
                    entry.setGoalId(rs.getInt("goal_id"));
                    entry.setValue(rs.getString("value"));
                    entry.setRecordedDate(rs.getString("recorded_date"));
                    entry.setNotes(rs.getString("notes"));
                    entry.setCreatedBy(rs.getString("created_by"));
                    entry.setCreatedAt(rs.getString("created_at"));
                    entries.add(entry);
                }
            }
        }
        return entries;
    }

    public TreatmentGoal createTreatmentGoal(int patientId, int doctorId, String title, String metricName, String unit, String baselineValue, String targetValue, String status) {
        String sql = "INSERT INTO treatment_goals (patient_id, doctor_id, title, metric_name, unit, baseline_value, target_value, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, patientId);
            ps.setInt(2, doctorId);
            ps.setString(3, title != null ? title.trim() : "Treatment Goal");
            ps.setString(4, metricName != null ? metricName.trim() : "Metric");
            ps.setString(5, unit != null ? unit.trim() : "");
            ps.setString(6, baselineValue != null ? baselineValue.trim() : "0");
            ps.setString(7, targetValue != null ? targetValue.trim() : "0");
            ps.setString(8, status != null ? status.trim().toUpperCase() : "IN_PROGRESS");

            int rows = ps.executeUpdate();
            if (rows > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        int goalId = rs.getInt(1);
                        List<TreatmentGoal> list = getTreatmentGoals(patientId);
                        for (TreatmentGoal g : list) {
                            if (g.getId() == goalId) return g;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean addProgressEntry(int goalId, String value, String recordedDate, String notes, String createdBy, String updatedStatus) {
        String sql = "INSERT INTO treatment_progress_entries (goal_id, value, recorded_date, notes, created_by) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, goalId);
                ps.setString(2, value != null ? value.trim() : "");
                ps.setString(3, recordedDate != null ? recordedDate.trim() : "2026-09-07");
                ps.setString(4, notes != null ? notes.trim() : "");
                ps.setString(5, createdBy != null ? createdBy.trim() : "Doctor");
                ps.executeUpdate();
            }

            if (updatedStatus != null && !updatedStatus.trim().isEmpty()) {
                String statusSql = "UPDATE treatment_goals SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
                try (PreparedStatement statusPs = conn.prepareStatement(statusSql)) {
                    statusPs.setString(1, updatedStatus.trim().toUpperCase());
                    statusPs.setInt(2, goalId);
                    statusPs.executeUpdate();
                }
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public List<Map<String, Object>> getNearbyPharmacyAvailability(int medicineId, double userLat, double userLng, int limit) {
        List<Map<String, Object>> results = new ArrayList<>();
        double refLat = (userLat != 0.0) ? userLat : 12.9716; // Default Bangalore
        double refLng = (userLng != 0.0) ? userLng : 77.5946;

        String sql = "SELECT i.*, p.pharmacy_name, p.address, p.city, p.phone, p.latitude, p.longitude, p.opening_hours, p.rating, " +
                "m.name as medicine_name, m.generic_name, m.brand, m.dosage, m.form " +
                "FROM inventory i " +
                "JOIN pharmacies p ON i.pharmacy_id = p.id " +
                "JOIN medicines m ON i.medicine_id = m.id " +
                "WHERE i.medicine_id = ? " +
                "ORDER BY i.stock_quantity DESC LIMIT ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, medicineId);
            ps.setInt(2, limit > 0 ? limit : 20);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new HashMap<>();
                    double pLat = rs.getDouble("latitude");
                    double pLng = rs.getDouble("longitude");
                    double dist = calculateDistance(refLat, refLng, pLat, pLng);

                    item.put("pharmacyId", rs.getInt("pharmacy_id"));
                    item.put("pharmacyName", rs.getString("pharmacy_name"));
                    item.put("address", rs.getString("address"));
                    item.put("city", rs.getString("city"));
                    item.put("phone", rs.getString("phone"));
                    item.put("openingHours", rs.getString("opening_hours"));
                    item.put("rating", rs.getDouble("rating"));
                    item.put("distanceKm", Math.round(dist * 10.0) / 10.0);
                    item.put("stockQuantity", rs.getInt("stock_quantity"));
                    item.put("unitPrice", rs.getDouble("unit_price"));
                    item.put("batchNumber", rs.getString("batch_number"));
                    item.put("expiryDate", rs.getString("expiry_date"));
                    item.put("status", rs.getString("status"));
                    item.put("lastUpdated", rs.getString("last_updated"));

                    results.add(item);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Sort by distance
        results.sort(Comparator.comparingDouble(a -> (Double) a.get("distanceKm")));
        return results;
    }

    public void logAiAudit(int doctorId, int patientId, String feature, String summaryNotes) {
        String sql = "INSERT INTO ai_decision_audit_logs (doctor_id, patient_id, feature, summary_notes) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, doctorId);
            ps.setInt(2, patientId);
            ps.setString(3, feature != null ? feature : "CLINICAL_DECISION_SUPPORT");
            ps.setString(4, summaryNotes != null ? summaryNotes : "Analysis requested by doctor.");
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Map<String, Object> getPatientSummary(int patientId) {
        return getPatientWorkspaceSummary(patientId);
    }

    public List<TimelineEvent> getPatientTimeline(int patientId, int limit, int offset) {
        List<TimelineEvent> all = getPatientHealthTimeline(patientId, limit + offset);
        if (offset <= 0) {
            return all.size() > limit && limit > 0 ? all.subList(0, limit) : all;
        }
        if (offset >= all.size()) {
            return new ArrayList<>();
        }
        int toIndex = Math.min(all.size(), offset + limit);
        return all.subList(offset, toIndex);
    }

    public List<TreatmentGoal> getTreatmentGoalsWithProgress(int patientId) {
        return getTreatmentGoals(patientId);
    }

    public int addTreatmentProgressEntry(int goalId, String value, String recordedDate, String notes, int doctorUserId) {
        boolean ok = addProgressEntry(goalId, value, recordedDate, notes, "Doctor", null);
        return ok ? 1 : 0;
    }

    public boolean updateTreatmentGoalStatus(int goalId, String status) {
        String sql = "UPDATE treatment_goals SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status != null ? status.trim().toUpperCase() : "IN_PROGRESS");
            ps.setInt(2, goalId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Earth radius in km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
