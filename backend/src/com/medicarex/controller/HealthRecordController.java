package com.medicarex.controller;

import com.medicarex.model.HealthRecord;
import com.medicarex.model.Patient;
import com.medicarex.model.User;
import com.medicarex.repository.HealthRecordRepository;
import com.medicarex.repository.PatientRepository;
import com.medicarex.repository.UserRepository;
import com.medicarex.util.HttpUtil;
import com.medicarex.util.JsonUtil;
import com.medicarex.util.PdfGenerator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HealthRecordController implements HttpHandler {
    private final HealthRecordRepository recordRepo = new HealthRecordRepository();
    private final PatientRepository patientRepo = new PatientRepository();
    private final UserRepository userRepo = new UserRepository();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpUtil.handleOptions(exchange)) return;

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if (path.equals("/api/records") && "GET".equalsIgnoreCase(method)) {
            handleList(exchange);
        } else if (path.equals("/api/records/create") && "POST".equalsIgnoreCase(method)) {
            handleCreate(exchange);
        } else if ((path.equals("/api/records/download") || (path.startsWith("/api/records/") && path.endsWith("/download"))) && "GET".equalsIgnoreCase(method)) {
            handleDownload(exchange, path);
        } else {
            HttpUtil.sendError(exchange, 404, "Endpoint not found");
        }
    }

    private void handleDownload(HttpExchange exchange, String path) throws IOException {
        try {
            Map<String, Object> claims = HttpUtil.authenticate(exchange);
            int userId = 1;
            String role = "PATIENT";
            if (claims != null) {
                userId = ((Number) claims.get("userId")).intValue();
                role = (String) claims.getOrDefault("role", "PATIENT");
            }

            int recordId = 0;
            if (path.startsWith("/api/records/") && path.endsWith("/download") && path.length() > "/api/records/download".length()) {
                String mid = path.substring("/api/records/".length(), path.length() - "/download".length());
                try {
                    recordId = Integer.parseInt(mid);
                } catch (Exception ignored) {}
            }

            if (recordId == 0) {
                Map<String, String> queryParams = HttpUtil.getQueryParams(exchange);
                if (queryParams.containsKey("id")) {
                    try {
                        recordId = Integer.parseInt(queryParams.get("id"));
                    } catch (Exception ignored) {}
                }
            }

            HealthRecord record = null;
            if (recordId > 0) {
                record = recordRepo.findById(recordId);
            } else {
                List<HealthRecord> records = recordRepo.findByPatientUserId(userId, null);
                if (!records.isEmpty()) {
                    record = records.get(0);
                }
            }

            if (record == null) {
                HttpUtil.sendError(exchange, 404, "Health record not found");
                return;
            }

            // Security check: If patient, verify ownership
            Patient patient = patientRepo.findById(record.getPatientId());
            if ("PATIENT".equalsIgnoreCase(role)) {
                Patient currentPat = patientRepo.findByUserId(userId);
                if (currentPat == null || record.getPatientId() != currentPat.getId()) {
                    HttpUtil.sendError(exchange, 403, "Access denied. You can only download your own health records.");
                    return;
                }
            }

            String patientName = "Patient";
            String patientDetails = "ID: #PAT-" + record.getPatientId();
            if (patient != null) {
                User u = userRepo.findById(patient.getUserId());
                if (u != null) {
                    patientName = u.getFullName();
                }
                patientDetails = "ID: #PAT-" + patient.getId() + " • " + (patient.getBloodGroup() != null ? patient.getBloodGroup() : "O+") + " • " + (patient.getGender() != null ? patient.getGender() : "Patient");
            }

            byte[] pdfBytes = PdfGenerator.generateHealthRecordPdf(record, patientName, patientDetails);
            if (pdfBytes == null || pdfBytes.length == 0) {
                HttpUtil.sendError(exchange, 500, "Failed to generate health record PDF.");
                return;
            }
            String filename = "HealthRecord_" + record.getId() + ".pdf";
            HttpUtil.sendPdfResponse(exchange, filename, pdfBytes);
        } catch (Exception e) {
            e.printStackTrace();
            HttpUtil.sendError(exchange, 500, "Internal error: " + e.getMessage());
        }
    }

    private void handleList(HttpExchange exchange) throws IOException {
        Map<String, Object> claims = HttpUtil.authenticate(exchange);
        if (claims == null) {
            HttpUtil.sendError(exchange, 401, "Unauthorized access.");
            return;
        }
        int userId = ((Number) claims.get("userId")).intValue();

        Map<String, String> queryParams = HttpUtil.getQueryParams(exchange);
        String category = queryParams.get("category");

        List<HealthRecord> records = recordRepo.findByPatientUserId(userId, category);
        List<Map<String, Object>> result = new ArrayList<>();
        for (HealthRecord r : records) {
            result.add(r.toMap());
        }
        HttpUtil.sendSuccess(exchange, "Health records retrieved", result);
    }

    private void handleCreate(HttpExchange exchange) throws IOException {
        Map<String, Object> claims = HttpUtil.authenticate(exchange);
        int userId = 1;
        if (claims != null) {
            userId = ((Number) claims.get("userId")).intValue();
        }

        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);

        String title = (String) req.get("title");
        String recordType = (String) req.getOrDefault("recordType", "Documents");
        String providerName = (String) req.getOrDefault("providerName", "Self Uploaded");
        String description = (String) req.get("description");
        String documentUrl = (String) req.getOrDefault("documentUrl", "document.pdf");
        String fileSize = (String) req.getOrDefault("fileSize", "1.2 MB");
        String recordDate = (String) req.getOrDefault("recordDate", "2026-09-01");

        if (title == null || title.trim().isEmpty()) {
            HttpUtil.sendError(exchange, 400, "Title is required.");
            return;
        }

        HealthRecord hr = recordRepo.create(userId, recordType, title, providerName, description, documentUrl, fileSize, recordDate);
        if (hr != null) {
            HttpUtil.sendSuccess(exchange, "Health record uploaded successfully", hr.toMap());
        } else {
            HttpUtil.sendError(exchange, 500, "Failed to create health record");
        }
    }
}
