package com.medicarex.controller;

import com.medicarex.model.Doctor;
import com.medicarex.model.Patient;
import com.medicarex.model.Prescription;
import com.medicarex.repository.DoctorRepository;
import com.medicarex.repository.PatientRepository;
import com.medicarex.repository.PrescriptionRepository;
import com.medicarex.util.HttpUtil;
import com.medicarex.util.JsonUtil;
import com.medicarex.util.PdfGenerator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PrescriptionController implements HttpHandler {
    private final PrescriptionRepository rxRepo = new PrescriptionRepository();
    private final PatientRepository patientRepo = new PatientRepository();
    private final DoctorRepository doctorRepo = new DoctorRepository();
    private final PrescriptionUploadController uploadController = new PrescriptionUploadController();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpUtil.handleOptions(exchange)) return;

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if (path.startsWith("/api/prescriptions/upload") || path.startsWith("/api/prescriptions/uploads") || path.equals("/api/prescriptions/search-pharmacies")) {
            uploadController.handle(exchange);
            return;
        }

        // Support both /api/prescriptions and /api/patient/prescriptions
        if ((path.equals("/api/prescriptions") || path.equals("/api/patient/prescriptions")) && "GET".equalsIgnoreCase(method)) {
            handleList(exchange);
        } else if ((path.equals("/api/prescriptions/create") || path.equals("/api/prescriptions")) && "POST".equalsIgnoreCase(method)) {
            handleCreate(exchange);
        } else if ((path.equals("/api/prescriptions/download") || path.equals("/api/patient/prescriptions/download") || 
                   (path.startsWith("/api/prescriptions/") && path.endsWith("/download")) ||
                   (path.startsWith("/api/patient/prescriptions/") && path.endsWith("/download"))) && "GET".equalsIgnoreCase(method)) {
            handleDownload(exchange, path);
        } else if (path.startsWith("/api/prescriptions/") && "GET".equalsIgnoreCase(method)) {
            String idStr = path.substring("/api/prescriptions/".length());
            try {
                handleGetById(exchange, Integer.parseInt(idStr));
            } catch (Exception e) {
                HttpUtil.sendError(exchange, 400, "Invalid prescription ID");
            }
        } else if (path.startsWith("/api/patient/prescriptions/") && "GET".equalsIgnoreCase(method)) {
            String idStr = path.substring("/api/patient/prescriptions/".length());
            try {
                handleGetById(exchange, Integer.parseInt(idStr));
            } catch (Exception e) {
                HttpUtil.sendError(exchange, 400, "Invalid prescription ID");
            }
        } else {
            HttpUtil.sendError(exchange, 404, "Endpoint not found");
        }
    }

    private void handleDownload(HttpExchange exchange, String path) throws IOException {
        try {
            Map<String, Object> claims = HttpUtil.authenticate(exchange);
            if (claims == null) {
                HttpUtil.sendError(exchange, 401, "Authentication required to download prescription.");
                return;
            }

            int userId = ((Number) claims.get("userId")).intValue();
            String role = (String) claims.getOrDefault("role", "PATIENT");

            int rxId = 0;
            if (path.contains("/download") && !path.endsWith("/prescriptions/download")) {
                String cleanPath = path.replace("/api/patient/prescriptions/", "").replace("/api/prescriptions/", "").replace("/download", "");
                try {
                    rxId = Integer.parseInt(cleanPath);
                } catch (Exception ignored) {}
            }

            if (rxId == 0) {
                Map<String, String> queryParams = HttpUtil.getQueryParams(exchange);
                if (queryParams.containsKey("id")) {
                    try {
                        rxId = Integer.parseInt(queryParams.get("id"));
                    } catch (Exception ignored) {}
                }
            }

            Prescription rx = null;
            if (rxId > 0) {
                rx = rxRepo.findById(rxId);
            } else if ("PATIENT".equalsIgnoreCase(role)) {
                List<Prescription> list = rxRepo.findByPatientUserId(userId);
                if (!list.isEmpty()) {
                    rx = list.get(0);
                }
            }

            if (rx == null) {
                HttpUtil.sendError(exchange, 404, "Prescription not found.");
                return;
            }

            // Security check: Patients can only download their own prescriptions
            if ("PATIENT".equalsIgnoreCase(role)) {
                Patient currentPat = patientRepo.findByUserId(userId);
                if (currentPat == null || rx.getPatientId() != currentPat.getId()) {
                    HttpUtil.sendError(exchange, 403, "Access denied. You can only download your own prescriptions.");
                    return;
                }
            } else if ("DOCTOR".equalsIgnoreCase(role)) {
                Doctor currentDoc = doctorRepo.findByUserId(userId);
                if (currentDoc == null || rx.getDoctorId() != currentDoc.getId()) {
                    HttpUtil.sendError(exchange, 403, "Access denied. You can only download prescriptions issued by your practice.");
                    return;
                }
            }

            byte[] pdfBytes = PdfGenerator.generatePrescriptionPdf(rx);
            if (pdfBytes == null || pdfBytes.length == 0) {
                HttpUtil.sendError(exchange, 500, "Failed to generate prescription PDF.");
                return;
            }
            String filename = "Prescription_" + (rx.getPrescriptionNumber() != null ? rx.getPrescriptionNumber() : rx.getId()) + ".pdf";
            HttpUtil.sendPdfResponse(exchange, filename, pdfBytes);
        } catch (Exception e) {
            e.printStackTrace();
            HttpUtil.sendError(exchange, 500, "Internal error: " + e.getMessage());
        }
    }

    private void handleList(HttpExchange exchange) throws IOException {
        Map<String, Object> claims = HttpUtil.authenticate(exchange);
        if (claims == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required to view prescriptions.");
            return;
        }

        int userId = ((Number) claims.get("userId")).intValue();
        String role = (String) claims.getOrDefault("role", "PATIENT");

        List<Prescription> list;
        if ("DOCTOR".equalsIgnoreCase(role)) {
            list = rxRepo.findByDoctorUserId(userId);
        } else if ("ADMIN".equalsIgnoreCase(role)) {
            list = rxRepo.findAll();
        } else {
            list = rxRepo.findByPatientUserId(userId);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        if (list != null) {
            for (Prescription rx : list) {
                result.add(rx.toMap());
            }
        }
        HttpUtil.sendSuccess(exchange, "Prescriptions retrieved", result);
    }

    private void handleGetById(HttpExchange exchange, int id) throws IOException {
        Map<String, Object> claims = HttpUtil.authenticate(exchange);
        if (claims == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required to view prescription.");
            return;
        }

        int userId = ((Number) claims.get("userId")).intValue();
        String role = (String) claims.getOrDefault("role", "PATIENT");

        Prescription rx = rxRepo.findById(id);
        if (rx == null) {
            HttpUtil.sendError(exchange, 404, "Prescription not found");
            return;
        }

        // Patient-specific security check
        if ("PATIENT".equalsIgnoreCase(role)) {
            Patient currentPat = patientRepo.findByUserId(userId);
            if (currentPat == null || rx.getPatientId() != currentPat.getId()) {
                HttpUtil.sendError(exchange, 403, "Access denied. You can only view your own prescriptions.");
                return;
            }
        } else if ("DOCTOR".equalsIgnoreCase(role)) {
            Doctor currentDoc = doctorRepo.findByUserId(userId);
            if (currentDoc == null || rx.getDoctorId() != currentDoc.getId()) {
                HttpUtil.sendError(exchange, 403, "Access denied. You can only view prescriptions issued by your practice.");
                return;
            }
        }

        HttpUtil.sendSuccess(exchange, "Prescription details", rx.toMap());
    }

    @SuppressWarnings("unchecked")
    private void handleCreate(HttpExchange exchange) throws IOException {
        Map<String, Object> claims = HttpUtil.authenticate(exchange);
        if (claims == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required.");
            return;
        }

        String role = (String) claims.get("role");
        if (!"DOCTOR".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role)) {
            HttpUtil.sendError(exchange, 403, "Only licensed medical practitioners can issue digital prescriptions.");
            return;
        }

        int doctorUserId = ((Number) claims.get("userId")).intValue();

        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);
        if (req == null) {
            HttpUtil.sendError(exchange, 400, "Invalid JSON payload.");
            return;
        }

        Number patientIdNum = (Number) req.get("patientId");
        if (patientIdNum == null || patientIdNum.intValue() <= 0) {
            HttpUtil.sendError(exchange, 400, "Please select a valid patient to issue the prescription to.");
            return;
        }

        int patientId = patientIdNum.intValue();
        String diagnosis = (String) req.getOrDefault("diagnosis", "Clinical Evaluation & Treatment");
        String notes = (String) req.getOrDefault("notes", "Take medications strictly as directed.");
        
        List<Map<String, Object>> items = new ArrayList<>();
        Object rawItems = req.get("items");
        if (rawItems instanceof List) {
            for (Object itemObj : (List<?>) rawItems) {
                if (itemObj instanceof Map) {
                    items.add((Map<String, Object>) itemObj);
                }
            }
        }

        if (items.isEmpty()) {
            HttpUtil.sendError(exchange, 400, "Prescription must contain at least one medication.");
            return;
        }

        Prescription rx = rxRepo.create(doctorUserId, patientId, diagnosis, notes, items);
        if (rx != null) {
            HttpUtil.sendSuccess(exchange, "Prescription created and signed successfully", rx.toMap());
        } else {
            HttpUtil.sendError(exchange, 400, "Failed to issue prescription. Ensure the patient exists and doctor profile is active.");
        }
    }
}
