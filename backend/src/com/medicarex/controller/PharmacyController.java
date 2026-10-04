package com.medicarex.controller;

import com.medicarex.model.Pharmacy;
import com.medicarex.repository.PharmacyRepository;
import com.medicarex.util.HttpUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PharmacyController implements HttpHandler {
    private final PharmacyRepository pharmacyRepo = new PharmacyRepository();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpUtil.handleOptions(exchange)) return;

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if (path.equals("/api/pharmacies/search-medicines") && "POST".equalsIgnoreCase(method)) {
            new PrescriptionUploadController().handle(exchange);
            return;
        }

        if ((path.equals("/api/pharmacies") || path.equals("/api/pharmacies/nearby")) && "GET".equalsIgnoreCase(method)) {
            handleSearch(exchange);
        } else if (path.startsWith("/api/pharmacies/medicine/") && "GET".equalsIgnoreCase(method)) {
            String medIdStr = path.substring("/api/pharmacies/medicine/".length());
            try {
                int medId = Integer.parseInt(medIdStr);
                handleGetByMedicine(exchange, medId);
            } catch (NumberFormatException e) {
                HttpUtil.sendError(exchange, 400, "Invalid medicine ID: must be an integer");
            }
        } else if (path.startsWith("/api/pharmacies/") && "GET".equalsIgnoreCase(method)) {
            String idStr = path.substring("/api/pharmacies/".length());
            try {
                int id = Integer.parseInt(idStr);
                handleGetById(exchange, id);
            } catch (NumberFormatException e) {
                HttpUtil.sendError(exchange, 400, "Invalid pharmacy ID: must be an integer");
            }
        } else {
            HttpUtil.sendError(exchange, 404, "Endpoint not found");
        }
    }

    private void handleSearch(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        Map<String, String> queryParams = HttpUtil.getQueryParams(exchange);
        String q = queryParams.getOrDefault("q", queryParams.get("query"));
        String status = queryParams.get("status");

        String latStr = queryParams.getOrDefault("latitude", queryParams.get("lat"));
        String lngStr = queryParams.getOrDefault("longitude", queryParams.getOrDefault("lng", queryParams.get("lon")));

        Double userLat = null;
        Double userLng = null;

        // /api/pharmacies/nearby strictly requires coordinates
        if (path.endsWith("/nearby")) {
            if (latStr == null || lngStr == null || latStr.trim().isEmpty() || lngStr.trim().isEmpty()) {
                HttpUtil.sendError(exchange, 400, "Latitude and longitude are required for nearby pharmacy search.");
                return;
            }
        }

        if (latStr != null && lngStr != null && !latStr.trim().isEmpty() && !lngStr.trim().isEmpty()) {
            try {
                userLat = Double.parseDouble(latStr.trim());
                userLng = Double.parseDouble(lngStr.trim());

                if (userLat < -90.0 || userLat > 90.0 || userLng < -180.0 || userLng > 180.0) {
                    HttpUtil.sendError(exchange, 400, "Invalid coordinates: latitude must be between -90 and 90, longitude between -180 and 180.");
                    return;
                }
            } catch (NumberFormatException e) {
                HttpUtil.sendError(exchange, 400, "Invalid coordinates format: latitude and longitude must be valid decimal numbers.");
                return;
            }
        }

        double radiusKm = 0.0;
        String radiusStr = queryParams.getOrDefault("radius", queryParams.get("radiusKm"));
        if (radiusStr != null && !radiusStr.trim().isEmpty()) {
            try {
                radiusKm = Double.parseDouble(radiusStr.trim());
                if (radiusKm < 0.0 || radiusKm > 500.0) {
                    HttpUtil.sendError(exchange, 400, "Invalid radius: must be between 0 and 500 km.");
                    return;
                }
            } catch (NumberFormatException e) {
                HttpUtil.sendError(exchange, 400, "Invalid radius format: must be a valid number in km.");
                return;
            }
        }

        Integer medicineId = null;
        String medStr = queryParams.getOrDefault("medicineId", queryParams.get("medId"));
        if (medStr != null && !medStr.trim().isEmpty()) {
            try {
                medicineId = Integer.parseInt(medStr.trim());
                if (medicineId <= 0) {
                    HttpUtil.sendError(exchange, 400, "Invalid medicine ID: must be a positive integer.");
                    return;
                }
            } catch (NumberFormatException e) {
                HttpUtil.sendError(exchange, 400, "Invalid medicine ID: must be a valid integer.");
                return;
            }
        }

        double searchLat = userLat != null ? userLat : 12.9716;
        double searchLng = userLng != null ? userLng : 77.5946;

        List<Pharmacy> list = pharmacyRepo.findNearby(searchLat, searchLng, radiusKm, medicineId, q, status);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Pharmacy p : list) {
            result.add(p.toMap());
        }
        HttpUtil.sendSuccess(exchange, "Pharmacies retrieved successfully", result);
    }

    private void handleGetById(HttpExchange exchange, int id) throws IOException {
        if (id <= 0) {
            HttpUtil.sendError(exchange, 400, "Invalid pharmacy ID: must be a positive integer.");
            return;
        }
        Pharmacy p = pharmacyRepo.findById(id);
        if (p == null) {
            HttpUtil.sendError(exchange, 404, "Pharmacy not found with ID: " + id);
            return;
        }
        HttpUtil.sendSuccess(exchange, "Pharmacy retrieved", p.toMap());
    }

    private void handleGetByMedicine(HttpExchange exchange, int medicineId) throws IOException {
        if (medicineId <= 0) {
            HttpUtil.sendError(exchange, 400, "Invalid medicine ID: must be a positive integer.");
            return;
        }

        Map<String, String> queryParams = HttpUtil.getQueryParams(exchange);
        Double userLat = null;
        Double userLng = null;

        String latStr = queryParams.getOrDefault("latitude", queryParams.get("lat"));
        String lngStr = queryParams.getOrDefault("longitude", queryParams.getOrDefault("lng", queryParams.get("lon")));

        if (latStr != null && lngStr != null && !latStr.trim().isEmpty() && !lngStr.trim().isEmpty()) {
            try {
                userLat = Double.parseDouble(latStr.trim());
                userLng = Double.parseDouble(lngStr.trim());

                if (userLat < -90.0 || userLat > 90.0 || userLng < -180.0 || userLng > 180.0) {
                    HttpUtil.sendError(exchange, 400, "Invalid coordinates: latitude must be between -90 and 90, longitude between -180 and 180.");
                    return;
                }
            } catch (NumberFormatException e) {
                HttpUtil.sendError(exchange, 400, "Invalid coordinates format: latitude and longitude must be valid decimal numbers.");
                return;
            }
        }

        double radiusKm = 0.0;
        String radiusStr = queryParams.getOrDefault("radius", queryParams.get("radiusKm"));
        if (radiusStr != null && !radiusStr.trim().isEmpty()) {
            try {
                radiusKm = Double.parseDouble(radiusStr.trim());
                if (radiusKm < 0.0 || radiusKm > 500.0) {
                    HttpUtil.sendError(exchange, 400, "Invalid radius: must be between 0 and 500 km.");
                    return;
                }
            } catch (NumberFormatException e) {
                HttpUtil.sendError(exchange, 400, "Invalid radius format: must be a valid number in km.");
                return;
            }
        }

        double searchLat = userLat != null ? userLat : 12.9716;
        double searchLng = userLng != null ? userLng : 77.5946;

        List<Pharmacy> list = pharmacyRepo.findPharmaciesByMedicine(medicineId, searchLat, searchLng, radiusKm);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Pharmacy p : list) {
            result.add(p.toMap());
        }
        HttpUtil.sendSuccess(exchange, "Pharmacy availability retrieved", result);
    }
}
