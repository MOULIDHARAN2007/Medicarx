package com.medicarex.controller;

import com.medicarex.model.Ambulance;
import com.medicarex.model.EmergencyRequest;
import com.medicarex.model.Hospital;
import com.medicarex.repository.EmergencyRepository;
import com.medicarex.repository.HospitalRepository;
import com.medicarex.util.HttpUtil;
import com.medicarex.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.*;

public class EmergencyController implements HttpHandler {

    private final EmergencyRepository emergencyRepo = new EmergencyRepository();
    private final HospitalRepository hospitalRepo = new HospitalRepository();
    private final com.medicarex.repository.PatientFeatureRepository patientFeatureRepo = new com.medicarex.repository.PatientFeatureRepository();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpUtil.handleOptions(exchange)) return;

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod().toUpperCase();

        try {
            // Emergency Health Card Public & Scan Endpoints
            if (path.equals("/api/emergency/card/public") && "GET".equals(method)) {
                handleEmergencyCardPublic(exchange);
                return;
            }
            if (path.equals("/api/emergency/card/scan") && "GET".equals(method)) {
                handleEmergencyCardScan(exchange);
                return;
            }

            // Patient Emergency Requests: POST /api/emergency/requests or GET /api/emergency/requests
            if (path.equals("/api/emergency/requests")) {
                if ("POST".equals(method)) {
                    handleCreateEmergencyRequest(exchange);
                } else if ("GET".equals(method)) {
                    handleGetPatientEmergencyRequests(exchange);
                } else {
                    HttpUtil.sendError(exchange, 405, "Method Not Allowed");
                }
                return;
            }

            // Patient Active Emergency: GET /api/emergency/active
            if (path.equals("/api/emergency/active") && "GET".equals(method)) {
                handleGetActiveEmergency(exchange);
                return;
            }

            // Single Emergency Request Status: GET /api/emergency/requests/{id}
            if (path.startsWith("/api/emergency/requests/") && path.endsWith("/cancel") && "POST".equals(method)) {
                String idStr = path.substring("/api/emergency/requests/".length(), path.length() - "/cancel".length());
                int id = Integer.parseInt(idStr);
                handleCancelEmergencyRequest(exchange, id);
                return;
            }

            if (path.startsWith("/api/emergency/requests/") && "GET".equals(method)) {
                int id = Integer.parseInt(path.substring("/api/emergency/requests/".length()));
                handleGetEmergencyRequestById(exchange, id);
                return;
            }

            // Ambulance Driver Portal Endpoints
            if (path.equals("/api/ambulance/me") && "GET".equals(method)) {
                handleGetDriverAmbulance(exchange);
                return;
            }

            if ((path.equals("/api/ambulance/emergency/active") || path.equals("/api/ambulance/emergencies/active")) && "GET".equals(method)) {
                handleGetDriverActiveEmergency(exchange);
                return;
            }

            if (path.equals("/api/ambulance/requests") && "GET".equals(method)) {
                handleGetDriverRequests(exchange);
                return;
            }

            if (path.startsWith("/api/ambulance/emergencies/") && path.endsWith("/location") && "GET".equals(method)) {
                String idStr = path.substring("/api/ambulance/emergencies/".length(), path.length() - "/location".length());
                int id = Integer.parseInt(idStr);
                handleGetDriverEmergencyLocation(exchange, id);
                return;
            }

            if (path.equals("/api/ambulance/location") && "POST".equals(method)) {
                handleUpdateAmbulanceLocation(exchange);
                return;
            }

            if (path.equals("/api/ambulance/status") && "POST".equals(method)) {
                handleUpdateAmbulanceStatus(exchange);
                return;
            }

            if (path.startsWith("/api/ambulance/requests/") && path.endsWith("/accept") && "POST".equals(method)) {
                String idStr = path.substring("/api/ambulance/requests/".length(), path.length() - "/accept".length());
                int id = Integer.parseInt(idStr);
                handleAcceptEmergencyRequest(exchange, id);
                return;
            }

            if (path.startsWith("/api/ambulance/requests/") && path.endsWith("/status") && "POST".equals(method)) {
                String idStr = path.substring("/api/ambulance/requests/".length(), path.length() - "/status".length());
                int id = Integer.parseInt(idStr);
                handleUpdateRequestStatus(exchange, id);
                return;
            }

            // Ambulance Hospital Selection & Suggestion Endpoints
            if (path.equals("/api/ambulance/hospitals") && "POST".equals(method)) {
                handleAmbulanceAddHospital(exchange);
                return;
            }

            if (path.equals("/api/ambulance/hospitals/nearby") && "GET".equals(method)) {
                handleGetNearbyHospitalsForAmbulance(exchange);
                return;
            }

            if (path.startsWith("/api/ambulance/hospitals/") && "GET".equals(method)) {
                int hospId = Integer.parseInt(path.substring("/api/ambulance/hospitals/".length()));
                handleGetHospitalByIdForAmbulance(exchange, hospId);
                return;
            }

            if (path.matches("^/api/ambulance/(?:emergencies|requests)/\\d+/hospital$")) {
                String idStr = path.replaceAll("^/api/ambulance/(?:emergencies|requests)/(\\d+)/hospital$", "$1");
                int emgId = Integer.parseInt(idStr);
                if ("POST".equals(method)) {
                    handleSetEmergencyHospital(exchange, emgId);
                } else if ("GET".equals(method)) {
                    handleGetEmergencyHospital(exchange, emgId);
                } else {
                    HttpUtil.sendError(exchange, 405, "Method Not Allowed");
                }
                return;
            }

            if (path.matches("^/api/ambulance/(?:emergencies|requests)/\\d+/destination$")) {
                String idStr = path.replaceAll("^/api/ambulance/(?:emergencies|requests)/(\\d+)/destination$", "$1");
                int emgId = Integer.parseInt(idStr);
                if ("POST".equals(method)) {
                    handleSetEmergencyHospital(exchange, emgId);
                } else if ("GET".equals(method)) {
                    handleGetEmergencyHospital(exchange, emgId);
                } else {
                    HttpUtil.sendError(exchange, 405, "Method Not Allowed");
                }
                return;
            }

            // Admin Fleet Management
            if (path.equals("/api/admin/ambulances")) {
                if ("GET".equals(method)) {
                    handleAdminListAmbulances(exchange);
                } else if ("POST".equals(method)) {
                    handleAdminCreateAmbulance(exchange);
                } else {
                    HttpUtil.sendError(exchange, 405, "Method Not Allowed");
                }
                return;
            }

            if (path.startsWith("/api/admin/ambulances/")) {
                int ambId = Integer.parseInt(path.substring("/api/admin/ambulances/".length()));
                if ("PUT".equals(method)) {
                    handleAdminUpdateAmbulance(exchange, ambId);
                } else if ("DELETE".equals(method)) {
                    handleAdminDeleteAmbulance(exchange, ambId);
                } else {
                    HttpUtil.sendError(exchange, 405, "Method Not Allowed");
                }
                return;
            }

            if (path.equals("/api/admin/emergency/requests") && "GET".equals(method)) {
                handleAdminListEmergencyRequests(exchange);
                return;
            }

            if (path.equals("/api/admin/emergency/stats") && "GET".equals(method)) {
                handleAdminGetEmergencyStats(exchange);
                return;
            }

            HttpUtil.sendError(exchange, 404, "Emergency endpoint not found: " + path);
        } catch (Exception e) {
            e.printStackTrace();
            HttpUtil.sendError(exchange, 500, "Emergency server error: " + e.getMessage());
        }
    }

    private void handleCreateEmergencyRequest(HttpExchange exchange) throws IOException {
        Map<String, Object> auth = HttpUtil.authenticate(exchange);
        if (auth == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required");
            return;
        }
        int patientId = (int) auth.get("userId");

        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> data = JsonUtil.fromJson(body);

        EmergencyRequest req = new EmergencyRequest();
        req.setPatientId(patientId);
        req.setEmergencyType((String) data.getOrDefault("emergencyType", "GENERAL"));
        req.setSeverity((String) data.getOrDefault("severity", "CRITICAL"));
        
        try {
            req.setPickupLatitude(Double.parseDouble(String.valueOf(data.get("pickupLatitude"))));
            req.setPickupLongitude(Double.parseDouble(String.valueOf(data.get("pickupLongitude"))));
        } catch (Exception e) {
            req.setPickupLatitude(13.0827); // Default fallback coordinate (Chennai)
            req.setPickupLongitude(80.2707);
        }

        req.setPickupAddress((String) data.getOrDefault("pickupAddress", "Current Location"));
        req.setLandmark((String) data.getOrDefault("landmark", ""));
        req.setContactPhone((String) data.getOrDefault("contactPhone", ""));
        req.setPatientNotes((String) data.getOrDefault("patientNotes", ""));

        EmergencyRequest created = emergencyRepo.createRequest(req);
        if (created != null) {
            HttpUtil.sendSuccess(exchange, "Emergency SOS dispatched successfully", created);
        } else {
            HttpUtil.sendError(exchange, 500, "Failed to dispatch emergency request");
        }
    }

    private void handleGetPatientEmergencyRequests(HttpExchange exchange) throws IOException {
        Map<String, Object> auth = HttpUtil.authenticate(exchange);
        if (auth == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required");
            return;
        }
        int patientId = (int) auth.get("userId");
        List<EmergencyRequest> list = emergencyRepo.getPatientRequests(patientId);
        HttpUtil.sendSuccess(exchange, "Requests retrieved", list);
    }

    private void handleGetActiveEmergency(HttpExchange exchange) throws IOException {
        Map<String, Object> auth = HttpUtil.authenticate(exchange);
        if (auth == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required");
            return;
        }
        int patientId = (int) auth.get("userId");
        EmergencyRequest active = emergencyRepo.getActiveRequestForPatient(patientId);
        HttpUtil.sendSuccess(exchange, "Active emergency request", active);
    }

    private void handleGetEmergencyRequestById(HttpExchange exchange, int id) throws IOException {
        Map<String, Object> auth = HttpUtil.authenticate(exchange);
        if (auth == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required");
            return;
        }
        EmergencyRequest req = emergencyRepo.getRequestById(id);
        if (req != null) {
            HttpUtil.sendSuccess(exchange, "Request retrieved", req);
        } else {
            HttpUtil.sendError(exchange, 404, "Emergency request not found");
        }
    }

    private void handleCancelEmergencyRequest(HttpExchange exchange, int id) throws IOException {
        Map<String, Object> auth = HttpUtil.authenticate(exchange);
        if (auth == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required");
            return;
        }
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> data = JsonUtil.fromJson(body);
        String reason = data != null ? (String) data.getOrDefault("reason", "Cancelled by user") : "Cancelled by user";

        boolean success = emergencyRepo.cancelRequest(id, reason);
        if (success) {
            HttpUtil.sendSuccess(exchange, "Emergency request cancelled", null);
        } else {
            HttpUtil.sendError(exchange, 400, "Could not cancel request");
        }
    }

    private void handleGetDriverAmbulance(HttpExchange exchange) throws IOException {
        Map<String, Object> auth = HttpUtil.authenticate(exchange);
        if (auth == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required");
            return;
        }
        int driverUserId = (int) auth.get("userId");
        Ambulance amb = emergencyRepo.getAmbulanceByDriverUserId(driverUserId);
        HttpUtil.sendSuccess(exchange, "Ambulance profile", amb);
    }

    private void handleGetDriverActiveEmergency(HttpExchange exchange) throws IOException {
        Map<String, Object> auth = HttpUtil.authenticate(exchange);
        if (auth == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required");
            return;
        }
        String role = (String) auth.get("role");
        if (!"AMBULANCE".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role)) {
            HttpUtil.sendError(exchange, 403, "Ambulance access required");
            return;
        }
        int driverUserId = (int) auth.get("userId");
        EmergencyRequest active = emergencyRepo.getActiveRequestForDriver(driverUserId);
        HttpUtil.sendSuccess(exchange, "Active emergency", active != null ? active.toMap() : null);
    }

    private void handleGetDriverEmergencyLocation(HttpExchange exchange, int id) throws IOException {
        Map<String, Object> auth = HttpUtil.authenticate(exchange);
        if (auth == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required");
            return;
        }
        String role = (String) auth.get("role");
        if (!"AMBULANCE".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role)) {
            HttpUtil.sendError(exchange, 403, "Ambulance access required");
            return;
        }
        int driverUserId = (int) auth.get("userId");

        // Strict Privacy & Authorization Check (IDOR prevention)
        if (!"ADMIN".equalsIgnoreCase(role) && !emergencyRepo.verifyEmergencyDriverAccess(id, driverUserId)) {
            HttpUtil.sendError(exchange, 403, "Access denied: Emergency is not assigned to your ambulance unit.");
            return;
        }

        EmergencyRequest req = emergencyRepo.getRequestById(id);
        if (req == null) {
            HttpUtil.sendError(exchange, 404, "Emergency request not found");
            return;
        }

        Map<String, Object> locData = new LinkedHashMap<>();
        locData.put("id", req.getId());
        locData.put("status", req.getStatus());
        locData.put("emergencyType", req.getEmergencyType());
        locData.put("severity", req.getSeverity());
        locData.put("patientName", req.getPatientName());
        locData.put("contactPhone", req.getContactPhone());
        locData.put("patientNotes", req.getPatientNotes());
        locData.put("patientLatitude", req.getPickupLatitude());
        locData.put("patientLongitude", req.getPickupLongitude());
        locData.put("pickupAddress", req.getPickupAddress());
        locData.put("landmark", req.getLandmark());
        locData.put("ambulanceLatitude", req.getAmbulanceLat());
        locData.put("ambulanceLongitude", req.getAmbulanceLng());
        locData.put("hospitalAffiliation", req.getHospitalAffiliation());
        locData.put("hospitalLatitude", req.getHospitalLatitude());
        locData.put("hospitalLongitude", req.getHospitalLongitude());
        locData.put("estimatedArrivalMinutes", req.getEstimatedArrivalMinutes());

        HttpUtil.sendSuccess(exchange, "Emergency location details", locData);
    }

    private void handleGetDriverRequests(HttpExchange exchange) throws IOException {
        Map<String, Object> auth = HttpUtil.authenticate(exchange);
        if (auth == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required");
            return;
        }
        int driverUserId = (int) auth.get("userId");
        List<EmergencyRequest> assigned = emergencyRepo.getRequestsForAmbulanceDriver(driverUserId);
        List<EmergencyRequest> pending = emergencyRepo.getPendingOrAssignedRequests();

        Map<String, Object> res = new HashMap<>();
        res.put("myRequests", assigned);
        res.put("pendingPool", pending);
        HttpUtil.sendSuccess(exchange, "Driver requests", res);
    }

    private void handleAcceptEmergencyRequest(HttpExchange exchange, int id) throws IOException {
        Map<String, Object> auth = HttpUtil.authenticate(exchange);
        if (auth == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required");
            return;
        }
        int driverUserId = (int) auth.get("userId");
        boolean ok = emergencyRepo.acceptRequest(id, driverUserId);
        if (ok) {
            HttpUtil.sendSuccess(exchange, "Emergency dispatch accepted", null);
        } else {
            HttpUtil.sendError(exchange, 400, "Could not accept request (may already be accepted or cancelled)");
        }
    }

    private void handleUpdateRequestStatus(HttpExchange exchange, int id) throws IOException {
        Map<String, Object> auth = HttpUtil.authenticate(exchange);
        if (auth == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required");
            return;
        }
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> data = JsonUtil.fromJson(body);
        String status = (String) data.get("status");
        String notes = (String) data.get("notes");

        if (status == null || status.trim().isEmpty()) {
            HttpUtil.sendError(exchange, 400, "Missing status parameter");
            return;
        }

        boolean ok = emergencyRepo.updateRequestStatus(id, status.toUpperCase().trim(), notes);
        if (ok) {
            HttpUtil.sendSuccess(exchange, "Status updated to " + status, null);
        } else {
            HttpUtil.sendError(exchange, 400, "Failed to update emergency status");
        }
    }

    private void handleUpdateAmbulanceLocation(HttpExchange exchange) throws IOException {
        Map<String, Object> auth = HttpUtil.authenticate(exchange);
        if (auth == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required");
            return;
        }
        int driverUserId = (int) auth.get("userId");
        Ambulance amb = emergencyRepo.getAmbulanceByDriverUserId(driverUserId);
        if (amb == null) {
            HttpUtil.sendError(exchange, 404, "No registered ambulance found for current driver");
            return;
        }

        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> data = JsonUtil.fromJson(body);
        double lat = Double.parseDouble(String.valueOf(data.get("latitude")));
        double lng = Double.parseDouble(String.valueOf(data.get("longitude")));

        boolean ok = emergencyRepo.updateAmbulanceLocation(amb.getId(), lat, lng);
        if (ok) {
            HttpUtil.sendSuccess(exchange, "Location updated", null);
        } else {
            HttpUtil.sendError(exchange, 400, "Failed to update location");
        }
    }

    private void handleUpdateAmbulanceStatus(HttpExchange exchange) throws IOException {
        Map<String, Object> auth = HttpUtil.authenticate(exchange);
        if (auth == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required");
            return;
        }
        int driverUserId = (int) auth.get("userId");
        Ambulance amb = emergencyRepo.getAmbulanceByDriverUserId(driverUserId);
        if (amb == null) {
            HttpUtil.sendError(exchange, 404, "No registered ambulance found for current driver");
            return;
        }

        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> data = JsonUtil.fromJson(body);
        String status = (String) data.get("status");

        boolean ok = emergencyRepo.updateAmbulanceStatus(amb.getId(), status);
        if (ok) {
            HttpUtil.sendSuccess(exchange, "Ambulance status updated to " + status, null);
        } else {
            HttpUtil.sendError(exchange, 400, "Failed to update status");
        }
    }

    // Admin handlers
    private void handleAdminListAmbulances(HttpExchange exchange) throws IOException {
        Map<String, Object> auth = HttpUtil.authenticate(exchange);
        if (auth == null || !"ADMIN".equalsIgnoreCase((String) auth.get("role"))) {
            HttpUtil.sendError(exchange, 403, "Admin access required");
            return;
        }
        List<Ambulance> list = emergencyRepo.getAllAmbulances();
        HttpUtil.sendSuccess(exchange, "Ambulances retrieved", list);
    }

    private void handleAdminCreateAmbulance(HttpExchange exchange) throws IOException {
        Map<String, Object> auth = HttpUtil.authenticate(exchange);
        if (auth == null || !"ADMIN".equalsIgnoreCase((String) auth.get("role"))) {
            HttpUtil.sendError(exchange, 403, "Admin access required");
            return;
        }
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> data = JsonUtil.fromJson(body);

        Ambulance a = new Ambulance();
        a.setVehicleNumber((String) data.get("vehicleNumber"));
        a.setVehicleType((String) data.getOrDefault("vehicleType", "BASIC"));
        a.setDriverName((String) data.get("driverName"));
        a.setDriverPhone((String) data.get("driverPhone"));
        if (data.get("driverUserId") != null && !String.valueOf(data.get("driverUserId")).isEmpty()) {
            a.setDriverUserId(Integer.parseInt(String.valueOf(data.get("driverUserId"))));
        }
        a.setStatus((String) data.getOrDefault("status", "AVAILABLE"));
        try {
            a.setCurrentLatitude(Double.parseDouble(String.valueOf(data.get("currentLatitude"))));
            a.setCurrentLongitude(Double.parseDouble(String.valueOf(data.get("currentLongitude"))));
        } catch (Exception ignored) {
            a.setCurrentLatitude(13.0827);
            a.setCurrentLongitude(80.2707);
        }
        a.setHospitalAffiliation((String) data.get("hospitalAffiliation"));
        a.setEquipmentSummary((String) data.get("equipmentSummary"));

        boolean ok = emergencyRepo.createAmbulance(a);
        if (ok) {
            HttpUtil.sendSuccess(exchange, "Ambulance created successfully", null);
        } else {
            HttpUtil.sendError(exchange, 400, "Failed to create ambulance");
        }
    }

    private void handleAdminUpdateAmbulance(HttpExchange exchange, int id) throws IOException {
        Map<String, Object> auth = HttpUtil.authenticate(exchange);
        if (auth == null || !"ADMIN".equalsIgnoreCase((String) auth.get("role"))) {
            HttpUtil.sendError(exchange, 403, "Admin access required");
            return;
        }
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> data = JsonUtil.fromJson(body);

        Ambulance a = emergencyRepo.getAmbulanceById(id);
        if (a == null) {
            HttpUtil.sendError(exchange, 404, "Ambulance not found");
            return;
        }

        if (data.containsKey("vehicleNumber")) a.setVehicleNumber((String) data.get("vehicleNumber"));
        if (data.containsKey("vehicleType")) a.setVehicleType((String) data.get("vehicleType"));
        if (data.containsKey("driverName")) a.setDriverName((String) data.get("driverName"));
        if (data.containsKey("driverPhone")) a.setDriverPhone((String) data.get("driverPhone"));
        if (data.containsKey("driverUserId") && data.get("driverUserId") != null && !String.valueOf(data.get("driverUserId")).isEmpty()) {
            a.setDriverUserId(Integer.parseInt(String.valueOf(data.get("driverUserId"))));
        }
        if (data.containsKey("status")) a.setStatus((String) data.get("status"));
        if (data.containsKey("hospitalAffiliation")) a.setHospitalAffiliation((String) data.get("hospitalAffiliation"));
        if (data.containsKey("equipmentSummary")) a.setEquipmentSummary((String) data.get("equipmentSummary"));
        if (data.containsKey("isActive")) a.setActive(Boolean.parseBoolean(String.valueOf(data.get("isActive"))));

        boolean ok = emergencyRepo.updateAmbulance(a);
        if (ok) {
            HttpUtil.sendSuccess(exchange, "Ambulance updated", a);
        } else {
            HttpUtil.sendError(exchange, 400, "Failed to update ambulance");
        }
    }

    private void handleAdminDeleteAmbulance(HttpExchange exchange, int id) throws IOException {
        Map<String, Object> auth = HttpUtil.authenticate(exchange);
        if (auth == null || !"ADMIN".equalsIgnoreCase((String) auth.get("role"))) {
            HttpUtil.sendError(exchange, 403, "Admin access required");
            return;
        }
        boolean ok = emergencyRepo.deleteAmbulance(id);
        if (ok) {
            HttpUtil.sendSuccess(exchange, "Ambulance deleted", null);
        } else {
            HttpUtil.sendError(exchange, 400, "Failed to delete ambulance");
        }
    }

    private void handleAdminListEmergencyRequests(HttpExchange exchange) throws IOException {
        Map<String, Object> auth = HttpUtil.authenticate(exchange);
        if (auth == null || !"ADMIN".equalsIgnoreCase((String) auth.get("role"))) {
            HttpUtil.sendError(exchange, 403, "Admin access required");
            return;
        }
        Map<String, String> q = HttpUtil.getQueryParams(exchange);
        String status = q.getOrDefault("status", "ALL");
        List<EmergencyRequest> list = emergencyRepo.getAllEmergencyRequests(status);
        HttpUtil.sendSuccess(exchange, "Requests retrieved", list);
    }

    private void handleAdminGetEmergencyStats(HttpExchange exchange) throws IOException {
        Map<String, Object> auth = HttpUtil.authenticate(exchange);
        if (auth == null || !"ADMIN".equalsIgnoreCase((String) auth.get("role"))) {
            HttpUtil.sendError(exchange, 403, "Admin access required");
            return;
        }
        Map<String, Object> stats = emergencyRepo.getEmergencyStats();
        HttpUtil.sendSuccess(exchange, "Emergency statistics", stats);
    }

    // ==========================================
    // Ambulance Hospital Integration Handlers
    // ==========================================

    private void handleGetNearbyHospitalsForAmbulance(HttpExchange exchange) throws IOException {
        Map<String, Object> auth = HttpUtil.authenticate(exchange);
        if (auth == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required");
            return;
        }

        Map<String, String> q = HttpUtil.getQueryParams(exchange);
        double lat = 12.9716;
        double lng = 77.5946;
        double radius = 30.0; // 30 km radius default

        if (q.containsKey("lat")) {
            try { lat = Double.parseDouble(q.get("lat")); } catch (Exception ignored) {}
        }
        if (q.containsKey("lng")) {
            try { lng = Double.parseDouble(q.get("lng")); } catch (Exception ignored) {}
        }
        if (q.containsKey("radius")) {
            try { radius = Double.parseDouble(q.get("radius")); } catch (Exception ignored) {}
        }

        List<Map<String, Object>> nearby = hospitalRepo.getNearbyActiveHospitals(lat, lng, radius);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("hospitals", nearby);
        data.put("list", nearby);
        HttpUtil.sendSuccess(exchange, "Nearby hospitals retrieved", data);
    }

    private void handleGetHospitalByIdForAmbulance(HttpExchange exchange, int hospId) throws IOException {
        Map<String, Object> auth = HttpUtil.authenticate(exchange);
        if (auth == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required");
            return;
        }

        Hospital h = hospitalRepo.getHospitalById(hospId);
        if (h == null) {
            HttpUtil.sendError(exchange, 404, "Hospital not found");
            return;
        }
        HttpUtil.sendSuccess(exchange, "Hospital retrieved", h.toMap());
    }

    private void handleSetEmergencyHospital(HttpExchange exchange, int emergencyId) throws IOException {
        Map<String, Object> auth = HttpUtil.authenticate(exchange);
        if (auth == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required");
            return;
        }

        String role = (String) auth.get("role");
        int driverUserId = ((Number) auth.get("userId")).intValue();

        if (!"AMBULANCE".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role)) {
            HttpUtil.sendError(exchange, 403, "Ambulance driver or Admin privileges required");
            return;
        }

        EmergencyRequest existing = emergencyRepo.getRequestById(emergencyId);
        if (existing == null) {
            HttpUtil.sendError(exchange, 404, "Emergency request not found");
            return;
        }

        if ("COMPLETED".equalsIgnoreCase(existing.getStatus()) || "CANCELLED".equalsIgnoreCase(existing.getStatus())) {
            HttpUtil.sendError(exchange, 400, "Cannot change destination hospital for a completed or cancelled emergency");
            return;
        }

        if (!"ADMIN".equalsIgnoreCase(role) && !emergencyRepo.verifyEmergencyDriverAccess(emergencyId, driverUserId)) {
            HttpUtil.sendError(exchange, 403, "Access denied: You are not assigned to this emergency mission");
            return;
        }

        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);

        int hospId = 0;
        if (req.containsKey("hospitalId")) {
            try { hospId = ((Number) req.get("hospitalId")).intValue(); } catch (Exception ignored) {}
        } else if (req.containsKey("id")) {
            try { hospId = ((Number) req.get("id")).intValue(); } catch (Exception ignored) {}
        } else if (req.containsKey("destinationHospitalId")) {
            try { hospId = ((Number) req.get("destinationHospitalId")).intValue(); } catch (Exception ignored) {}
        }

        if (hospId <= 0) {
            HttpUtil.sendError(exchange, 400, "Valid hospitalId is required");
            return;
        }

        Hospital hosp = hospitalRepo.getHospitalById(hospId);
        if (hosp == null || !hosp.isActive()) {
            HttpUtil.sendError(exchange, 400, "Selected hospital is inactive or not accepting emergency dispatches");
            return;
        }

        boolean ok = emergencyRepo.setEmergencyDestinationHospital(emergencyId, hospId, driverUserId);
        if (ok) {
            // If status is PATIENT_PICKED_UP, we can also transition/allow TRANSPORTING
            if ("PATIENT_PICKED_UP".equalsIgnoreCase(existing.getStatus())) {
                emergencyRepo.updateRequestStatus(emergencyId, "TRANSPORTING", null);
            }

            EmergencyRequest updated = emergencyRepo.getRequestById(emergencyId);
            Map<String, Object> resData = updated != null ? updated.toMap() : new HashMap<>();
            resData.put("selectedHospital", hosp.toMap());
            HttpUtil.sendSuccess(exchange, "Destination hospital selected and confirmed", resData);
        } else {
            HttpUtil.sendError(exchange, 500, "Failed to persist destination hospital selection");
        }
    }

    private void handleGetEmergencyHospital(HttpExchange exchange, int emergencyId) throws IOException {
        Map<String, Object> auth = HttpUtil.authenticate(exchange);
        if (auth == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required");
            return;
        }

        EmergencyRequest req = emergencyRepo.getRequestById(emergencyId);
        if (req == null) {
            HttpUtil.sendError(exchange, 404, "Emergency request not found");
            return;
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("emergencyId", emergencyId);
        res.put("destinationHospitalId", req.getDestinationHospitalId());
        res.put("destinationHospitalName", req.getDestinationHospitalName());
        res.put("destinationHospitalAddress", req.getDestinationHospitalAddress());
        res.put("destinationHospitalPhone", req.getDestinationHospitalPhone());
        res.put("destinationLatitude", req.getDestinationLatitude());
        res.put("destinationLongitude", req.getDestinationLongitude());

        if (req.getDestinationHospitalId() != null && req.getDestinationHospitalId() > 0) {
            Hospital h = hospitalRepo.getHospitalById(req.getDestinationHospitalId());
            if (h != null) {
                res.put("hospitalDetails", h.toMap());
            }
        }
        HttpUtil.sendSuccess(exchange, "Destination hospital retrieved", res);
    }

    private void handleAmbulanceAddHospital(HttpExchange exchange) throws IOException {
        Map<String, Object> auth = HttpUtil.authenticate(exchange);
        if (auth == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required");
            return;
        }

        String role = (String) auth.get("role");
        if (!"AMBULANCE".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role)) {
            HttpUtil.sendError(exchange, 403, "Ambulance or Admin access required");
            return;
        }
        int userId = ((Number) auth.get("userId")).intValue();

        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);
        if (req == null) {
            HttpUtil.sendError(exchange, 400, "Invalid JSON payload");
            return;
        }

        String name = (String) req.get("name");
        if (name == null || name.trim().isEmpty()) {
            HttpUtil.sendError(exchange, 400, "Hospital Name is required");
            return;
        }

        String address = (String) req.get("address");
        if (address == null || address.trim().isEmpty()) {
            HttpUtil.sendError(exchange, 400, "Address is required");
            return;
        }

        String city = (String) req.get("city");
        if (city == null || city.trim().isEmpty()) {
            HttpUtil.sendError(exchange, 400, "City is required");
            return;
        }

        String state = (String) req.get("state");
        if (state == null || state.trim().isEmpty()) {
            HttpUtil.sendError(exchange, 400, "State is required");
            return;
        }

        String pincode = (String) req.getOrDefault("pincode", "");

        double lat = 0.0;
        double lng = 0.0;
        try {
            lat = ((Number) req.get("latitude")).doubleValue();
            lng = ((Number) req.get("longitude")).doubleValue();
        } catch (Exception e) {
            HttpUtil.sendError(exchange, 400, "Valid latitude and longitude are required");
            return;
        }

        if (lat < -90.0 || lat > 90.0 || lng < -180.0 || lng > 180.0) {
            HttpUtil.sendError(exchange, 400, "Coordinates out of valid range (Latitude: -90 to 90, Longitude: -180 to 180)");
            return;
        }

        int availableBeds = 0;
        int totalBeds = 0;
        if (req.containsKey("availableBeds")) {
            try {
                availableBeds = ((Number) req.get("availableBeds")).intValue();
                if (availableBeds < 0) {
                    HttpUtil.sendError(exchange, 400, "Available beds must not be negative");
                    return;
                }
            } catch (Exception ignored) {}
        }
        if (req.containsKey("totalBeds")) {
            try {
                totalBeds = ((Number) req.get("totalBeds")).intValue();
                if (totalBeds < 0) totalBeds = availableBeds;
            } catch (Exception ignored) {}
        } else {
            totalBeds = Math.max(availableBeds, 10);
        }

        // Duplicate prevention check
        boolean force = Boolean.TRUE.equals(req.get("force"));
        if (!force) {
            Hospital dup = hospitalRepo.findPotentialDuplicate(name, city, address, lat, lng);
            if (dup != null) {
                Map<String, Object> dupData = new LinkedHashMap<>();
                dupData.put("existingHospital", dup.toMap());
                dupData.put("duplicateDetected", true);
                HttpUtil.sendError(exchange, 409, "Similar hospital already exists (" + dup.getName() + " in " + dup.getCity() + "). Please verify or cancel.");
                return;
            }
        }

        Hospital h = new Hospital();
        h.setName(name.trim());
        h.setRegistrationNumber((String) req.getOrDefault("registrationNumber", "HOSP-SUGG-" + (System.currentTimeMillis() % 100000)));
        h.setAddress(address.trim());
        h.setCity(city.trim());
        h.setState(state.trim());
        h.setPincode(pincode != null && !pincode.trim().isEmpty() ? pincode.trim() : "560001");
        h.setLatitude(lat);
        h.setLongitude(lng);
        h.setPhone((String) req.getOrDefault("phone", ""));
        h.setEmergencyContact((String) req.getOrDefault("emergencyContact", (String) req.getOrDefault("phone", "")));
        h.setEmergencyAvailable(req.containsKey("emergencyAvailable") ? Boolean.TRUE.equals(req.get("emergencyAvailable")) : true);
        h.setIcuAvailable(req.containsKey("icuAvailable") ? Boolean.TRUE.equals(req.get("icuAvailable")) : true);
        h.setAvailableBeds(availableBeds);
        h.setTotalBeds(totalBeds);
        h.setAcceptingAmbulances(req.containsKey("acceptingAmbulances") ? Boolean.TRUE.equals(req.get("acceptingAmbulances")) : true);
        
        // Non-admin submissions always start in PENDING_REVIEW and inactive
        h.setActive(false);
        h.setApprovalStatus("PENDING_REVIEW");
        h.setCreatedByUserId(userId);
        h.setCreatedByRole(role.toUpperCase());

        Hospital created = hospitalRepo.createHospital(h);
        if (created != null) {
            HttpUtil.sendSuccess(exchange, "Hospital submitted for admin review.", created.toMap());
        } else {
            HttpUtil.sendError(exchange, 500, "Failed to submit hospital for review");
        }
    }

    private void handleEmergencyCardPublic(HttpExchange exchange) throws IOException {
        Map<String, String> q = HttpUtil.getQueryParams(exchange);
        String token = q.get("token");
        if (token == null || token.trim().isEmpty()) {
            HttpUtil.sendError(exchange, 400, "Invalid or expired MediCareX Emergency QR.");
            return;
        }

        Map<String, Object> card = patientFeatureRepo.getPublicEmergencyCardByToken(token.trim());
        if (card != null) {
            HttpUtil.sendSuccess(exchange, "Emergency Medical Profile", card);
        } else {
            HttpUtil.sendError(exchange, 404, "Invalid or expired MediCareX Emergency QR.");
        }
    }

    private void handleEmergencyCardScan(HttpExchange exchange) throws IOException {
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
        Map<String, Object> card = patientFeatureRepo.scanEmergencyCard(token.trim(), userId, role, ip);
        if (card != null) {
            HttpUtil.sendSuccess(exchange, "Emergency card scanned successfully", card);
        } else {
            HttpUtil.sendError(exchange, 404, "Invalid or expired MediCareX Emergency QR.");
        }
    }
}
