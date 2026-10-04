package com.medicarex.controller;

import com.medicarex.model.Medicine;
import com.medicarex.repository.MedicineRepository;
import com.medicarex.util.HttpUtil;
import com.medicarex.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MedicineController implements HttpHandler {
    private final MedicineRepository medicineRepo = new MedicineRepository();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpUtil.handleOptions(exchange)) return;

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if (path.equals("/api/medicines") && "GET".equalsIgnoreCase(method)) {
            handleSearch(exchange);
        } else if (path.equals("/api/medicines/saved") && "GET".equalsIgnoreCase(method)) {
            handleGetSaved(exchange);
        } else if (path.equals("/api/medicines/toggle-save") && "POST".equalsIgnoreCase(method)) {
            handleToggleSave(exchange);
        } else if (path.startsWith("/api/medicines/") && "GET".equalsIgnoreCase(method)) {
            String idStr = path.substring("/api/medicines/".length());
            try {
                int id = Integer.parseInt(idStr);
                handleGetById(exchange, id);
            } catch (NumberFormatException e) {
                HttpUtil.sendError(exchange, 400, "Invalid medicine ID");
            }
        } else {
            HttpUtil.sendError(exchange, 404, "Endpoint not found");
        }
    }

    private void handleSearch(HttpExchange exchange) throws IOException {
        Map<String, String> queryParams = HttpUtil.getQueryParams(exchange);
        String q = queryParams.get("q");
        if (q == null || q.trim().isEmpty()) {
            q = queryParams.get("query");
        }
        if (q == null || q.trim().isEmpty()) {
            q = queryParams.get("search");
        }
        String category = queryParams.get("category");
        String availability = queryParams.get("availability");
        String sortBy = queryParams.get("sort");
        int limit = 100;
        if (queryParams.containsKey("limit")) {
            try { limit = Integer.parseInt(queryParams.get("limit")); } catch (Exception ignored) {}
        }

        List<Medicine> medicines = medicineRepo.search(q, category, availability, sortBy, limit);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Medicine m : medicines) {
            list.add(m.toMap());
        }

        HttpUtil.sendSuccess(exchange, "Medicines retrieved", list);
    }

    private void handleGetById(HttpExchange exchange, int id) throws IOException {
        Medicine m = medicineRepo.findById(id);
        if (m == null) {
            HttpUtil.sendError(exchange, 404, "Medicine not found");
            return;
        }
        HttpUtil.sendSuccess(exchange, "Medicine details retrieved", m.toMap());
    }

    private void handleGetSaved(HttpExchange exchange) throws IOException {
        Map<String, Object> claims = HttpUtil.authenticate(exchange);
        if (claims == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required to view saved medicines.");
            return;
        }

        Number userIdNum = (Number) claims.get("userId");
        int userId = userIdNum.intValue();

        List<Medicine> saved = medicineRepo.getSavedMedicines(userId);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Medicine m : saved) {
            list.add(m.toMap());
        }
        HttpUtil.sendSuccess(exchange, "Saved medicines retrieved", list);
    }

    private void handleToggleSave(HttpExchange exchange) throws IOException {
        Map<String, Object> claims = HttpUtil.authenticate(exchange);
        if (claims == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required.");
            return;
        }

        Number userIdNum = (Number) claims.get("userId");
        int userId = userIdNum.intValue();

        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);
        Number medIdNum = (Number) req.get("medicineId");
        if (medIdNum == null) {
            HttpUtil.sendError(exchange, 400, "medicineId is required.");
            return;
        }

        boolean isSavedNow = medicineRepo.toggleSave(userId, medIdNum.intValue());
        Map<String, Object> res = new HashMap<>();
        res.put("isSaved", isSavedNow);
        res.put("medicineId", medIdNum.intValue());

        HttpUtil.sendSuccess(exchange, isSavedNow ? "Medicine saved to favorites" : "Medicine removed from favorites", res);
    }
}
