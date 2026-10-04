package com.medicarex.controller;

import com.medicarex.auth.PasswordHasher;
import com.medicarex.model.Appointment;
import com.medicarex.model.Doctor;
import com.medicarex.model.Patient;
import com.medicarex.model.TimelineEvent;
import com.medicarex.model.TreatmentGoal;
import com.medicarex.model.User;
import com.medicarex.repository.AppointmentRepository;
import com.medicarex.repository.DoctorRepository;
import com.medicarex.repository.DoctorWorkspaceRepository;
import com.medicarex.repository.PatientRepository;
import com.medicarex.repository.UserRepository;
import com.medicarex.service.AiClinicalDecisionService;
import com.medicarex.service.PrescriptionSafetyService;
import com.medicarex.util.HttpUtil;
import com.medicarex.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DoctorController implements HttpHandler {
    private final AppointmentRepository apptRepo = new AppointmentRepository();
    private final UserRepository userRepo = new UserRepository();
    private final PatientRepository patientRepo = new PatientRepository();
    private final DoctorRepository doctorRepo = new DoctorRepository();
    private final DoctorWorkspaceRepository workspaceRepo = new DoctorWorkspaceRepository();
    private final PrescriptionSafetyService safetyService = new PrescriptionSafetyService();
    private final AiClinicalDecisionService aiDecisionService = new AiClinicalDecisionService();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpUtil.handleOptions(exchange)) return;

        Map<String, Object> claims = HttpUtil.authenticate(exchange);
        if (claims == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required for doctor access.");
            return;
        }

        String role = (String) claims.get("role");
        if (!"DOCTOR".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role)) {
            HttpUtil.sendError(exchange, 403, "Forbidden: Doctor privileges required.");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if (path.equals("/api/doctor/dashboard") && "GET".equalsIgnoreCase(method)) {
            handleDashboard(exchange, claims);
        } else if (path.equals("/api/doctor/patients") && "GET".equalsIgnoreCase(method)) {
            handleGetPatients(exchange);
        } else if (path.equals("/api/doctor/patients") && "POST".equalsIgnoreCase(method)) {
            handleAddPatient(exchange);
        } else if (path.equals("/api/doctor/appointments") && "GET".equalsIgnoreCase(method)) {
            handleGetAppointments(exchange, claims);
        } else if ((path.equals("/api/doctor/appointments") || path.equals("/api/doctor/appointments/create")) && "POST".equalsIgnoreCase(method)) {
            handleCreateAppointment(exchange, claims);
        } else if (path.equals("/api/doctor/appointments/status") && "POST".equalsIgnoreCase(method)) {
            handleUpdateStatus(exchange);
        } else if (path.equals("/api/doctor/appointments/cancel") && "POST".equalsIgnoreCase(method)) {
            handleCancelAppointment(exchange);
        } else if ((path.matches("^/api/doctor/patients/\\d+/workspace$") || path.matches("^/api/doctor/patients/\\d+/overview$")) && "GET".equalsIgnoreCase(method)) {
            String pStr = path.replaceAll("^/api/doctor/patients/", "").replaceAll("/(workspace|overview)$", "");
            handlePatientWorkspace(exchange, claims, Integer.parseInt(pStr));
        } else if (path.matches("^/api/doctor/patients/\\d+/timeline$") && "GET".equalsIgnoreCase(method)) {
            String pStr = path.replaceAll("^/api/doctor/patients/", "").replaceAll("/timeline$", "");
            handlePatientTimeline(exchange, claims, Integer.parseInt(pStr));
        } else if (path.matches("^/api/doctor/patients/\\d+/treatment-goals$") && "GET".equalsIgnoreCase(method)) {
            String pStr = path.replaceAll("^/api/doctor/patients/", "").replaceAll("/treatment-goals$", "");
            handleGetTreatmentGoals(exchange, claims, Integer.parseInt(pStr));
        } else if (path.matches("^/api/doctor/patients/\\d+/treatment-goals$") && "POST".equalsIgnoreCase(method)) {
            String pStr = path.replaceAll("^/api/doctor/patients/", "").replaceAll("/treatment-goals$", "");
            handleCreateTreatmentGoal(exchange, claims, Integer.parseInt(pStr));
        } else if (path.matches("^/api/doctor/treatment-goals/\\d+/progress$") && "POST".equalsIgnoreCase(method)) {
            String gStr = path.replaceAll("^/api/doctor/treatment-goals/", "").replaceAll("/progress$", "");
            handleAddTreatmentProgress(exchange, claims, Integer.parseInt(gStr));
        } else if (path.matches("^/api/doctor/patients/\\d+/pharmacy-availability/\\d+$") && "GET".equalsIgnoreCase(method)) {
            String[] parts = path.split("/");
            int patId = Integer.parseInt(parts[4]);
            int medId = Integer.parseInt(parts[6]);
            handlePharmacyAvailability(exchange, claims, patId, medId);
        } else if (path.matches("^/api/doctor/pharmacy-availability/\\d+$") && "GET".equalsIgnoreCase(method)) {
            String medStr = path.substring("/api/doctor/pharmacy-availability/".length());
            handlePharmacyAvailability(exchange, claims, 0, Integer.parseInt(medStr));
        } else if (path.equals("/api/doctor/ai/clinical-support") && "POST".equalsIgnoreCase(method)) {
            handleAiClinicalSupport(exchange, claims);
        } else if (path.equals("/api/doctor/prescriptions/safety-check") && "POST".equalsIgnoreCase(method)) {
            handlePrescriptionSafetyCheck(exchange, claims);
        } else {
            HttpUtil.sendError(exchange, 404, "Endpoint not found");
        }
    }

    private void handleDashboard(HttpExchange exchange, Map<String, Object> claims) throws IOException {
        int doctorUserId = ((Number) claims.get("userId")).intValue();
        Map<String, Object> data = new HashMap<>();

        Doctor doc = doctorRepo.findByUserId(doctorUserId);
        data.put("doctor", doc != null ? doc.toMap() : null);

        List<Appointment> appts = apptRepo.findByDoctorUserId(doctorUserId);
        List<Map<String, Object>> apptList = new ArrayList<>();
        int completedToday = 0;
        int todayAppts = 0;

        for (Appointment a : appts) {
            apptList.add(a.toMap());
            if ("Completed".equalsIgnoreCase(a.getStatus())) completedToday++;
            if ("Scheduled".equalsIgnoreCase(a.getStatus()) || "Confirmed".equalsIgnoreCase(a.getStatus())) todayAppts++;
        }

        List<Map<String, Object>> patients = patientRepo.findAllPatientsWithDetails();

        data.put("appointments", apptList);
        data.put("recentPatients", patients);
        data.put("totalPatients", patients.size());
        data.put("totalAppointments", appts.size());
        data.put("todayAppointments", todayAppts);
        data.put("completedToday", completedToday);
        data.put("pendingAppointments", appts.size() - completedToday);

        HttpUtil.sendSuccess(exchange, "Doctor dashboard data", data);
    }

    private void handleGetPatients(HttpExchange exchange) throws IOException {
        List<Map<String, Object>> list = patientRepo.findAllPatientsWithDetails();
        HttpUtil.sendSuccess(exchange, "Patients list", list);
    }

    private void handleAddPatient(HttpExchange exchange) throws IOException {
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);

        String fullName = (String) req.get("fullName");
        String email = (String) req.get("email");
        String phone = (String) req.get("phone");
        String dob = (String) req.get("dateOfBirth");
        if (dob == null) dob = (String) req.get("dob");
        String gender = (String) req.get("gender");
        String bloodGroup = (String) req.get("bloodGroup");
        String address = (String) req.get("address");
        String emergencyContact = (String) req.get("emergencyContact");
        String insurance = (String) req.get("insuranceProvider");

        if (fullName == null || email == null || fullName.trim().isEmpty() || email.trim().isEmpty()) {
            HttpUtil.sendError(exchange, 400, "Full name and email are required");
            return;
        }

        if (userRepo.findByEmail(email) != null) {
            HttpUtil.sendError(exchange, 400, "Patient with this email already exists.");
            return;
        }

        String reqPassword = (String) req.get("password");
        if (reqPassword == null || reqPassword.trim().isEmpty()) {
            reqPassword = UUID.randomUUID().toString();
        }
        String salt = PasswordHasher.generateSalt();
        String passHash = PasswordHasher.hashPassword(reqPassword, salt);

        User newUser = userRepo.createUser(email, passHash, salt, fullName, phone != null ? phone : "+91 90000 00000", "PATIENT");
        if (newUser == null) {
            HttpUtil.sendError(exchange, 500, "Failed to create patient account");
            return;
        }

        patientRepo.create(newUser.getId(), dob != null ? dob : "1995-01-01", gender != null ? gender : "Other", bloodGroup != null ? bloodGroup : "O+", address != null ? address : "Bangalore", emergencyContact != null ? emergencyContact : "+91 90000 00000", insurance != null ? insurance : "Self-insured");

        Map<String, Object> res = newUser.toSafeMap();
        res.put("dob", dob);
        res.put("gender", gender);
        res.put("bloodGroup", bloodGroup);
        HttpUtil.sendSuccess(exchange, "Patient added successfully", res);
    }

    private void handleGetAppointments(HttpExchange exchange, Map<String, Object> claims) throws IOException {
        int doctorUserId = ((Number) claims.get("userId")).intValue();
        List<Appointment> appts = apptRepo.findByDoctorUserId(doctorUserId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Appointment a : appts) {
            result.add(a.toMap());
        }
        HttpUtil.sendSuccess(exchange, "Appointments retrieved", result);
    }

    private void handleCreateAppointment(HttpExchange exchange, Map<String, Object> claims) throws IOException {
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);

        int doctorUserId = ((Number) claims.get("userId")).intValue();
        Doctor doc = doctorRepo.findByUserId(doctorUserId);
        int doctorId = doc != null ? doc.getId() : 1;

        int patientId = 1;
        Number patientIdNum = (Number) req.get("patientId");
        if (patientIdNum != null) {
            patientId = patientIdNum.intValue();
        } else if (req.get("patientEmail") != null) {
            User u = userRepo.findByEmail((String) req.get("patientEmail"));
            if (u != null) {
                Patient p = patientRepo.findByUserId(u.getId());
                if (p != null) patientId = p.getId();
            }
        }

        String date = (String) req.get("appointmentDate");
        String time = (String) req.get("appointmentTime");
        String type = (String) req.get("type");
        String notes = (String) req.get("reason");
        if (notes == null) notes = (String) req.get("notes");

        if (date == null || time == null) {
            HttpUtil.sendError(exchange, 400, "appointmentDate and appointmentTime are required");
            return;
        }

        if (apptRepo.isSlotBooked(doctorId, date, time)) {
            HttpUtil.sendError(exchange, 409, "This appointment slot is no longer available. Please select another time.");
            return;
        }

        int createdId = apptRepo.create(doctorId, patientId, date, time, type != null ? type : "General Consultation", "Scheduled", notes != null ? notes : "");
        if (createdId > 0) {
            Map<String, Object> createdData = new HashMap<>();
            createdData.put("id", createdId);
            createdData.put("status", "Scheduled");
            HttpUtil.sendSuccess(exchange, "Appointment booked successfully", createdData);
        } else {
            HttpUtil.sendError(exchange, 500, "Failed to schedule appointment");
        }
    }

    private void handleUpdateStatus(HttpExchange exchange) throws IOException {
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);

        Number apptId = (Number) req.get("appointmentId");
        String status = (String) req.get("status");

        if (apptId == null || status == null) {
            HttpUtil.sendError(exchange, 400, "appointmentId and status are required");
            return;
        }

        boolean ok = apptRepo.updateStatus(apptId.intValue(), status);
        HttpUtil.sendSuccess(exchange, ok ? "Appointment status updated" : "Failed to update", null);
    }

    private void handleCancelAppointment(HttpExchange exchange) throws IOException {
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);

        Number apptId = (Number) req.get("appointmentId");
        if (apptId == null) {
            HttpUtil.sendError(exchange, 400, "appointmentId is required");
            return;
        }

        boolean ok = apptRepo.updateStatus(apptId.intValue(), "Cancelled");
        HttpUtil.sendSuccess(exchange, ok ? "Appointment cancelled" : "Failed to cancel", null);
    }

    // ==========================================
    // DOCTOR WORKSPACE INTEGRATED ENDPOINTS
    // ==========================================

    private void handlePatientWorkspace(HttpExchange exchange, Map<String, Object> claims, int patientId) throws IOException {
        int doctorUserId = ((Number) claims.get("userId")).intValue();
        String role = (String) claims.get("role");

        if (!workspaceRepo.verifyDoctorPatientAccess(doctorUserId, patientId, role)) {
            HttpUtil.sendError(exchange, 403, "Access denied: You are not authorized to view this patient's medical records.");
            return;
        }

        Map<String, Object> summary = workspaceRepo.getPatientSummary(patientId);
        if (summary == null) {
            HttpUtil.sendError(exchange, 404, "Patient record not found.");
            return;
        }

        List<TimelineEvent> recentTimeline = workspaceRepo.getPatientTimeline(patientId, 10, 0);
        List<Map<String, Object>> timelineMaps = new ArrayList<>();
        for (TimelineEvent event : recentTimeline) {
            timelineMaps.add(event.toMap());
        }

        List<TreatmentGoal> treatmentGoals = workspaceRepo.getTreatmentGoalsWithProgress(patientId);
        List<Map<String, Object>> goalsMaps = new ArrayList<>();
        for (TreatmentGoal g : treatmentGoals) {
            goalsMaps.add(g.toMap());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("patient", summary);
        response.put("timelinePreview", timelineMaps);
        response.put("treatmentGoals", goalsMaps);

        HttpUtil.sendSuccess(exchange, "Patient workspace retrieved", response);
    }

    private void handlePatientTimeline(HttpExchange exchange, Map<String, Object> claims, int patientId) throws IOException {
        int doctorUserId = ((Number) claims.get("userId")).intValue();
        String role = (String) claims.get("role");

        if (!workspaceRepo.verifyDoctorPatientAccess(doctorUserId, patientId, role)) {
            HttpUtil.sendError(exchange, 403, "Access denied: You are not authorized to view this patient's timeline.");
            return;
        }

        int limit = 50;
        int offset = 0;
        String query = exchange.getRequestURI().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] pair = param.split("=");
                if (pair.length == 2) {
                    if ("limit".equalsIgnoreCase(pair[0])) {
                        try { limit = Integer.parseInt(pair[1]); } catch (Exception ignored) {}
                    } else if ("offset".equalsIgnoreCase(pair[0])) {
                        try { offset = Integer.parseInt(pair[1]); } catch (Exception ignored) {}
                    }
                }
            }
        }

        List<TimelineEvent> events = workspaceRepo.getPatientTimeline(patientId, limit, offset);
        List<Map<String, Object>> result = new ArrayList<>();
        for (TimelineEvent e : events) {
            result.add(e.toMap());
        }

        Map<String, Object> data = new HashMap<>();
        data.put("patientId", patientId);
        data.put("count", result.size());
        data.put("limit", limit);
        data.put("offset", offset);
        data.put("events", result);

        HttpUtil.sendSuccess(exchange, "Patient timeline retrieved", data);
    }

    private void handleGetTreatmentGoals(HttpExchange exchange, Map<String, Object> claims, int patientId) throws IOException {
        int doctorUserId = ((Number) claims.get("userId")).intValue();
        String role = (String) claims.get("role");

        if (!workspaceRepo.verifyDoctorPatientAccess(doctorUserId, patientId, role)) {
            HttpUtil.sendError(exchange, 403, "Access denied: Unauthorized patient treatment records access.");
            return;
        }

        List<TreatmentGoal> goals = workspaceRepo.getTreatmentGoalsWithProgress(patientId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (TreatmentGoal g : goals) {
            result.add(g.toMap());
        }

        HttpUtil.sendSuccess(exchange, "Treatment goals retrieved", result);
    }

    private void handleCreateTreatmentGoal(HttpExchange exchange, Map<String, Object> claims, int patientId) throws IOException {
        int doctorUserId = ((Number) claims.get("userId")).intValue();
        String role = (String) claims.get("role");

        if (!workspaceRepo.verifyDoctorPatientAccess(doctorUserId, patientId, role)) {
            HttpUtil.sendError(exchange, 403, "Access denied: Unauthorized to set treatment goals for this patient.");
            return;
        }

        Doctor doc = doctorRepo.findByUserId(doctorUserId);
        int doctorId = doc != null ? doc.getId() : 1;

        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);

        String title = (String) req.get("title");
        String metricName = (String) req.get("metricName");
        String unit = (String) req.get("unit");
        String baselineValue = (String) req.get("baselineValue");
        String targetValue = (String) req.get("targetValue");
        String status = (String) req.get("status");

        if (title == null || title.trim().isEmpty() || metricName == null || metricName.trim().isEmpty()) {
            HttpUtil.sendError(exchange, 400, "Title and metricName are required.");
            return;
        }

        TreatmentGoal createdGoal = workspaceRepo.createTreatmentGoal(patientId, doctorId, title, metricName,
                unit != null ? unit : "",
                baselineValue != null ? baselineValue : "",
                targetValue != null ? targetValue : "",
                status != null ? status : "IN_PROGRESS");

        if (createdGoal != null && createdGoal.getId() > 0) {
            int goalId = createdGoal.getId();
            // If initial baseline was provided with a note, record first baseline entry
            String notes = (String) req.get("notes");
            if (baselineValue != null && !baselineValue.trim().isEmpty()) {
                workspaceRepo.addTreatmentProgressEntry(goalId, baselineValue, null,
                        notes != null ? notes : "Baseline measurement recorded at goal creation", doctorUserId);
            }

            Map<String, Object> res = new HashMap<>();
            res.put("id", goalId);
            res.put("patientId", patientId);
            res.put("title", title);
            res.put("status", status != null ? status : "IN_PROGRESS");
            HttpUtil.sendSuccess(exchange, "Treatment goal created successfully", res);
        } else {
            HttpUtil.sendError(exchange, 500, "Failed to create treatment goal");
        }
    }

    private void handleAddTreatmentProgress(HttpExchange exchange, Map<String, Object> claims, int goalId) throws IOException {
        int doctorUserId = ((Number) claims.get("userId")).intValue();
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);

        String value = (String) req.get("value");
        String recordedDate = (String) req.get("recordedDate");
        String notes = (String) req.get("notes");
        String updateStatus = (String) req.get("status");

        if (value == null || value.trim().isEmpty()) {
            HttpUtil.sendError(exchange, 400, "Value is required for treatment progress entry.");
            return;
        }

        int entryId = workspaceRepo.addTreatmentProgressEntry(goalId, value, recordedDate, notes != null ? notes : "", doctorUserId);
        if (entryId > 0) {
            if (updateStatus != null && !updateStatus.trim().isEmpty()) {
                workspaceRepo.updateTreatmentGoalStatus(goalId, updateStatus);
            }

            Map<String, Object> res = new HashMap<>();
            res.put("id", entryId);
            res.put("goalId", goalId);
            res.put("value", value);
            HttpUtil.sendSuccess(exchange, "Treatment progress recorded successfully", res);
        } else {
            HttpUtil.sendError(exchange, 500, "Failed to record treatment progress");
        }
    }

    private void handlePharmacyAvailability(HttpExchange exchange, Map<String, Object> claims, int patientId, int medicineId) throws IOException {
        if (medicineId <= 0) {
            HttpUtil.sendError(exchange, 400, "Valid medicineId is required.");
            return;
        }

        double patientLat = 12.9716;
        double patientLng = 77.5946;

        if (patientId > 0) {
            Map<String, Object> pSummary = workspaceRepo.getPatientSummary(patientId);
            if (pSummary != null && pSummary.get("latitude") != null && pSummary.get("longitude") != null) {
                try {
                    patientLat = ((Number) pSummary.get("latitude")).doubleValue();
                    patientLng = ((Number) pSummary.get("longitude")).doubleValue();
                } catch (Exception ignored) {}
            }
        }

        List<Map<String, Object>> availability = workspaceRepo.getNearbyPharmacyAvailability(medicineId, patientLat, patientLng, 10);
        HttpUtil.sendSuccess(exchange, "Pharmacy availability retrieved", availability);
    }

    private void handleAiClinicalSupport(HttpExchange exchange, Map<String, Object> claims) throws IOException {
        int doctorUserId = ((Number) claims.get("userId")).intValue();
        String role = (String) claims.get("role");

        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);

        Number patIdNum = (Number) req.get("patientId");
        if (patIdNum == null) {
            HttpUtil.sendError(exchange, 400, "patientId is required for AI clinical decision support.");
            return;
        }

        int patientId = patIdNum.intValue();
        if (!workspaceRepo.verifyDoctorPatientAccess(doctorUserId, patientId, role)) {
            HttpUtil.sendError(exchange, 403, "Access denied: Unauthorized to run clinical analysis for this patient.");
            return;
        }

        String symptoms = (String) req.get("symptoms");
        if (symptoms == null) symptoms = (String) req.get("chiefComplaint");
        if (symptoms == null) symptoms = (String) req.get("complaint");
        String notes = (String) req.get("consultationNotes");
        if (notes == null) notes = (String) req.get("notes");
        if (notes == null) notes = (String) req.get("examNotes");

        if ((symptoms == null || symptoms.trim().isEmpty()) && (notes == null || notes.trim().isEmpty())) {
            HttpUtil.sendError(exchange, 400, "Please enter patient symptoms or consultation notes to generate clinical decision support.");
            return;
        }

        Map<String, Object> vitals = new HashMap<>();
        if (req.get("vitals") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> vMap = (Map<String, Object>) req.get("vitals");
            vitals.putAll(vMap);
        } else {
            if (req.containsKey("bloodPressure")) vitals.put("bloodPressure", req.get("bloodPressure"));
            if (req.containsKey("bp")) vitals.put("bloodPressure", req.get("bp"));
            if (req.containsKey("temperature")) vitals.put("temperature", req.get("temperature"));
            if (req.containsKey("heartRate")) vitals.put("heartRate", req.get("heartRate"));
            if (req.containsKey("pulse")) vitals.put("heartRate", req.get("pulse"));
            if (req.containsKey("oxygenSaturation")) vitals.put("oxygenSaturation", req.get("oxygenSaturation"));
            if (req.containsKey("spo2")) vitals.put("oxygenSaturation", req.get("spo2"));
            if (req.containsKey("respiratoryRate")) vitals.put("respiratoryRate", req.get("respiratoryRate"));
        }

        Map<String, Object> analysis = aiDecisionService.generateClinicalSupport(patientId, doctorUserId, symptoms, notes, vitals);
        HttpUtil.sendSuccess(exchange, "Clinical decision support generated", analysis);
    }

    @SuppressWarnings("unchecked")
    private void handlePrescriptionSafetyCheck(HttpExchange exchange, Map<String, Object> claims) throws IOException {
        int doctorUserId = ((Number) claims.get("userId")).intValue();
        String role = (String) claims.get("role");

        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);

        Number patIdNum = (Number) req.get("patientId");
        if (patIdNum == null) {
            HttpUtil.sendError(exchange, 400, "patientId is required for prescription safety check.");
            return;
        }

        int patientId = patIdNum.intValue();
        if (!workspaceRepo.verifyDoctorPatientAccess(doctorUserId, patientId, role)) {
            HttpUtil.sendError(exchange, 403, "Access denied: Unauthorized for this patient's prescription safety check.");
            return;
        }

        Object itemsObj = req.get("items");
        List<Map<String, Object>> items = new ArrayList<>();
        if (itemsObj instanceof List) {
            for (Object obj : (List<?>) itemsObj) {
                if (obj instanceof Map) {
                    items.add((Map<String, Object>) obj);
                }
            }
        }

        PrescriptionSafetyService.SafetyCheckResult safetyReport = safetyService.checkPrescriptionSafety(patientId, items);
        HttpUtil.sendSuccess(exchange, "Prescription safety analysis complete", safetyReport.toMap());
    }
}
