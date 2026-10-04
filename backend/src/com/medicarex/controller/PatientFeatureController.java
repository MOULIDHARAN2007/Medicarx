package com.medicarex.controller;

import com.medicarex.repository.PatientFeatureRepository;
import com.medicarex.util.HttpUtil;
import com.medicarex.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.*;

public class PatientFeatureController implements HttpHandler {

    private final PatientFeatureRepository repo = new PatientFeatureRepository();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpUtil.handleOptions(exchange)) return;

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod().toUpperCase();

        try {
            // Public Emergency Card QR Endpoint: GET /api/emergency/card/public?token=...
            if (path.equals("/api/emergency/card/public") && "GET".equals(method)) {
                handleGetPublicEmergencyCard(exchange);
                return;
            }

            // Authenticated Emergency Card Scan Endpoint: GET /api/emergency/card/scan?token=...
            if (path.equals("/api/emergency/card/scan") && "GET".equals(method)) {
                handleScanEmergencyCard(exchange);
                return;
            }

            // All remaining endpoints require user authentication
            Map<String, Object> auth = HttpUtil.authenticate(exchange);
            if (auth == null) {
                HttpUtil.sendError(exchange, 401, "Authentication required");
                return;
            }
            int patientId = (int) auth.get("userId");

            // 1. Medicine Supply Tracker: GET /api/patient/medicine-supply
            if (path.equals("/api/patient/medicine-supply") && "GET".equals(method)) {
                List<Map<String, Object>> list = repo.getMedicineSupplyStatus(patientId);
                HttpUtil.sendSuccess(exchange, "Medicine supply status retrieved", list);
                return;
            }

            // 2. Health Data Sharing: GET /api/patient/data-sharing or POST /api/patient/data-sharing
            if (path.equals("/api/patient/data-sharing")) {
                if ("GET".equals(method)) {
                    List<Map<String, Object>> grants = repo.getSharingGrants(patientId);
                    List<Map<String, Object>> logs = repo.getDataAccessLogs(patientId);
                    Map<String, Object> result = new HashMap<>();
                    result.put("grants", grants);
                    result.put("accessLogs", logs);
                    HttpUtil.sendSuccess(exchange, "Data sharing retrieved", result);
                } else if ("POST".equals(method)) {
                    handleSaveSharingGrant(exchange, patientId);
                } else {
                    HttpUtil.sendError(exchange, 405, "Method Not Allowed");
                }
                return;
            }

            if (path.startsWith("/api/patient/data-sharing/") && path.endsWith("/revoke") && "POST".equals(method)) {
                String idStr = path.substring("/api/patient/data-sharing/".length(), path.length() - "/revoke".length());
                int grantId = Integer.parseInt(idStr);
                boolean ok = repo.revokeSharingGrant(grantId, patientId);
                if (ok) {
                    HttpUtil.sendSuccess(exchange, "Access revoked successfully", null);
                } else {
                    HttpUtil.sendError(exchange, 400, "Failed to revoke access");
                }
                return;
            }

            // 3. Emergency Health Card: GET or POST /api/patient/emergency-card, or POST /api/patient/emergency-card/regenerate-token
            if (path.equals("/api/patient/emergency-card/regenerate-token") && "POST".equals(method)) {
                Map<String, Object> updated = repo.regenerateEmergencyToken(patientId);
                if (updated != null) {
                    HttpUtil.sendSuccess(exchange, "Emergency token regenerated successfully", updated);
                } else {
                    HttpUtil.sendError(exchange, 500, "Failed to regenerate emergency token");
                }
                return;
            }

            if (path.equals("/api/patient/emergency-card")) {
                if ("GET".equals(method)) {
                    Map<String, Object> card = repo.getEmergencyCard(patientId);
                    HttpUtil.sendSuccess(exchange, "Emergency card retrieved", card);
                } else if ("POST".equals(method)) {
                    String body = HttpUtil.readRequestBody(exchange);
                    Map<String, Object> data = JsonUtil.fromJson(body);
                    Map<String, Object> saved = repo.saveEmergencyCard(patientId, data);
                    if (saved != null) {
                        HttpUtil.sendSuccess(exchange, "Emergency card saved successfully", saved);
                    } else {
                        HttpUtil.sendError(exchange, 400, "Failed to save emergency card");
                    }
                } else {
                    HttpUtil.sendError(exchange, 405, "Method Not Allowed");
                }
                return;
            }

            // 4. Doctor Visit Preparation: GET or POST /api/patient/visit-prep
            if (path.equals("/api/patient/visit-prep")) {
                if ("GET".equals(method)) {
                    List<Map<String, Object>> list = repo.getVisitPreparations(patientId);
                    HttpUtil.sendSuccess(exchange, "Visit preparations retrieved", list);
                } else if ("POST".equals(method)) {
                    String body = HttpUtil.readRequestBody(exchange);
                    Map<String, Object> data = JsonUtil.fromJson(body);
                    boolean ok = repo.saveVisitPreparation(patientId, data);
                    if (ok) {
                        HttpUtil.sendSuccess(exchange, "Visit preparation saved", null);
                    } else {
                        HttpUtil.sendError(exchange, 400, "Failed to save visit preparation");
                    }
                } else {
                    HttpUtil.sendError(exchange, 405, "Method Not Allowed");
                }
                return;
            }

            if (path.startsWith("/api/patient/visit-prep/") && "DELETE".equals(method)) {
                int prepId = Integer.parseInt(path.substring("/api/patient/visit-prep/".length()));
                boolean ok = repo.deleteVisitPreparation(prepId, patientId);
                if (ok) {
                    HttpUtil.sendSuccess(exchange, "Visit preparation deleted", null);
                } else {
                    HttpUtil.sendError(exchange, 400, "Failed to delete preparation note");
                }
                return;
            }

            // 5. Medical Expense Tracker: GET /api/patient/medical-expenses
            if (path.equals("/api/patient/medical-expenses") && "GET".equals(method)) {
                Map<String, String> q = HttpUtil.getQueryParams(exchange);
                String year = q.getOrDefault("year", "");
                Map<String, Object> summary = repo.getMedicalExpensesSummary(patientId, year);
                HttpUtil.sendSuccess(exchange, "Medical expenses summary", summary);
                return;
            }

            // 6. Medicine Purchase History: GET /api/patient/purchase-history
            if (path.equals("/api/patient/purchase-history") && "GET".equals(method)) {
                List<Map<String, Object>> history = repo.getMedicinePurchaseHistory(patientId);
                HttpUtil.sendSuccess(exchange, "Medicine purchase history", history);
                return;
            }

            HttpUtil.sendError(exchange, 404, "Patient feature endpoint not found: " + path);

        } catch (Exception e) {
            e.printStackTrace();
            HttpUtil.sendError(exchange, 500, "Server error: " + e.getMessage());
        }
    }

    private void handleGetPublicEmergencyCard(HttpExchange exchange) throws IOException {
        Map<String, String> q = HttpUtil.getQueryParams(exchange);
        String token = q.get("token");
        if (token == null || token.trim().isEmpty()) {
            HttpUtil.sendError(exchange, 400, "Invalid or expired MediCareX Emergency QR.");
            return;
        }

        Map<String, Object> card = repo.getPublicEmergencyCardByToken(token.trim());
        if (card != null) {
            HttpUtil.sendSuccess(exchange, "Emergency Medical Profile", card);
        } else {
            HttpUtil.sendError(exchange, 404, "Invalid or expired MediCareX Emergency QR.");
        }
    }

    private void handleScanEmergencyCard(HttpExchange exchange) throws IOException {
        Map<String, Object> auth = HttpUtil.authenticate(exchange);
        if (auth == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required to scan emergency card");
            return;
        }

        String role = (String) auth.get("role");
        if (!"DOCTOR".equalsIgnoreCase(role) && !"AMBULANCE".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role)) {
            HttpUtil.sendError(exchange, 403, "Access restricted: Only authorized Doctors and Ambulance personnel can scan emergency cards.");
            return;
        }

        Map<String, String> q = HttpUtil.getQueryParams(exchange);
        String token = q.get("token");
        if (token == null || token.trim().isEmpty()) {
            HttpUtil.sendError(exchange, 400, "Invalid or expired MediCareX Emergency QR.");
            return;
        }

        int userId = (int) auth.get("userId");
        String ip = exchange.getRemoteAddress() != null ? exchange.getRemoteAddress().getAddress().getHostAddress() : "127.0.0.1";
        Map<String, Object> card = repo.scanEmergencyCard(token.trim(), userId, role, ip);
        if (card != null) {
            HttpUtil.sendSuccess(exchange, "Emergency card scanned successfully", card);
        } else {
            HttpUtil.sendError(exchange, 404, "Invalid or expired MediCareX Emergency QR.");
        }
    }

    private void handleSaveSharingGrant(HttpExchange exchange, int patientId) throws IOException {
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> data = JsonUtil.fromJson(body);

        int doctorId = Integer.parseInt(String.valueOf(data.get("doctorId")));
        String scope = (String) data.getOrDefault("accessScope", "FULL_HISTORY");
        String notes = (String) data.getOrDefault("notes", "");
        String expiresAt = (String) data.get("expiresAt");

        boolean ok = repo.createOrUpdateSharingGrant(patientId, doctorId, scope, notes, expiresAt);
        if (ok) {
            HttpUtil.sendSuccess(exchange, "Access permission granted successfully", null);
        } else {
            HttpUtil.sendError(exchange, 400, "Failed to grant access");
        }
    }
}
