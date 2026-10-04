package com.medicarex.controller;

import com.medicarex.model.InventoryItem;
import com.medicarex.model.Medicine;
import com.medicarex.model.Pharmacy;
import com.medicarex.repository.InventoryRepository;
import com.medicarex.repository.MedicineRepository;
import com.medicarex.repository.PharmacyRepository;
import com.medicarex.service.InventoryService;
import com.medicarex.util.HttpUtil;
import com.medicarex.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InventoryController implements HttpHandler {
    private final InventoryRepository inventoryRepo = new InventoryRepository();
    private final InventoryService inventoryService = new InventoryService();
    private final PharmacyRepository pharmacyRepo = new PharmacyRepository();
    private final MedicineRepository medicineRepo = new MedicineRepository();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpUtil.handleOptions(exchange)) return;

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod().toUpperCase();

        if ((path.equals("/api/inventory") || path.equals("/api/pharmacy/inventory")) && "GET".equals(method)) {
            handleList(exchange);
        } else if ((path.equals("/api/inventory/clear-all-stock") || path.equals("/api/pharmacy/inventory/clear-all-stock")) && "POST".equals(method)) {
            handleClearAllStock(exchange);
        } else if ((path.equals("/api/inventory/remove-all") || path.equals("/api/pharmacy/inventory/remove-all")) && ("DELETE".equals(method) || "POST".equals(method))) {
            handleRemoveAll(exchange);
        } else if ((path.equals("/api/inventory/update") || path.equals("/api/pharmacy/inventory/update")) && "POST".equals(method)) {
            handleUpdateStock(exchange);
        } else if ((path.equals("/api/inventory/add") || path.equals("/api/pharmacy/inventory/add")) && "POST".equals(method)) {
            handleAddStock(exchange);
        } else if ((path.equals("/api/inventory/add-quantity") || path.equals("/api/inventory/restock") || path.equals("/api/inventory/add-stock")) && "POST".equals(method)) {
            handleAddStockQuantity(exchange);
        } else if ((path.equals("/api/inventory/import-csv") || path.equals("/api/pharmacy/inventory/import-csv")) && "POST".equals(method)) {
            handleImportCsv(exchange);
        } else if (path.startsWith("/api/inventory/stats/") && "GET".equals(method)) {
            String idStr = path.substring("/api/inventory/stats/".length());
            try {
                handleStats(exchange, Integer.parseInt(idStr));
            } catch (Exception e) {
                HttpUtil.sendError(exchange, 400, "Invalid pharmacy ID");
            }
        } else if (path.startsWith("/api/inventory/expiring/") && "GET".equals(method)) {
            String idStr = path.substring("/api/inventory/expiring/".length());
            try {
                handleExpiring(exchange, Integer.parseInt(idStr));
            } catch (Exception e) {
                HttpUtil.sendError(exchange, 400, "Invalid pharmacy ID");
            }
        } else if (path.matches("^/api/(inventory|pharmacy/inventory)/\\d+/clear-stock$") && "POST".equals(method)) {
            // e.g. POST /api/pharmacy/inventory/42/clear-stock or /api/inventory/42/clear-stock
            boolean isPharmPrefix = path.startsWith("/api/pharmacy/inventory/");
            String numStr = path.replaceAll("^/api/(inventory|pharmacy/inventory)/", "").replaceAll("/clear-stock$", "");
            try {
                handleClearStock(exchange, Integer.parseInt(numStr), isPharmPrefix);
            } catch (Exception e) {
                HttpUtil.sendError(exchange, 400, "Invalid ID parameter");
            }
        } else if (path.startsWith("/api/pharmacy/inventory/") && "DELETE".equals(method)) {
            String idStr = path.substring("/api/pharmacy/inventory/".length());
            try {
                handleDeleteOrRemove(exchange, Integer.parseInt(idStr), true);
            } catch (Exception e) {
                HttpUtil.sendError(exchange, 400, "Invalid medicine ID");
            }
        } else if (path.startsWith("/api/pharmacy/inventory/") && "PUT".equals(method)) {
            String idStr = path.substring("/api/pharmacy/inventory/".length());
            try {
                handlePutInventory(exchange, Integer.parseInt(idStr), true);
            } catch (Exception e) {
                HttpUtil.sendError(exchange, 400, "Invalid medicine ID");
            }
        } else if (path.startsWith("/api/inventory/") && "PUT".equals(method)) {
            String idStr = path.substring("/api/inventory/".length());
            try {
                handlePutInventory(exchange, Integer.parseInt(idStr), false);
            } catch (Exception e) {
                HttpUtil.sendError(exchange, 400, "Invalid inventory ID");
            }
        } else if (path.startsWith("/api/inventory/") && "GET".equals(method)) {
            String idStr = path.substring("/api/inventory/".length());
            try {
                handleGetById(exchange, Integer.parseInt(idStr));
            } catch (Exception e) {
                HttpUtil.sendError(exchange, 400, "Invalid inventory ID");
            }
        } else if (path.startsWith("/api/inventory/") && "DELETE".equals(method)) {
            String idStr = path.substring("/api/inventory/".length());
            try {
                handleDeleteOrRemove(exchange, Integer.parseInt(idStr), false);
            } catch (Exception e) {
                HttpUtil.sendError(exchange, 400, "Invalid inventory ID");
            }
        } else {
            HttpUtil.sendError(exchange, 404, "Endpoint not found");
        }
    }

    private void handleList(HttpExchange exchange) throws IOException {
        Map<String, String> queryParams = HttpUtil.getQueryParams(exchange);
        String cat = queryParams.get("category");
        String status = queryParams.get("status");
        String q = queryParams.get("q");
        Integer pharmacyId = null;

        Map<String, Object> claims = HttpUtil.authenticate(exchange);
        if (claims != null && "PHARMACY".equalsIgnoreCase((String) claims.get("role"))) {
            Number userId = (Number) claims.get("userId");
            Pharmacy p = pharmacyRepo.findByUserId(userId.intValue());
            if (p != null) {
                pharmacyId = p.getId();
            }
        }

        if (pharmacyId == null && queryParams.containsKey("pharmacyId")) {
            try { pharmacyId = Integer.parseInt(queryParams.get("pharmacyId")); } catch (Exception ignored) {}
        }

        List<InventoryItem> items;
        if (pharmacyId != null) {
            items = inventoryRepo.findByPharmacyId(pharmacyId, cat, status, q);
        } else {
            items = inventoryRepo.findAll(null, status, q);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (InventoryItem item : items) {
            result.add(item.toMap());
        }
        HttpUtil.sendSuccess(exchange, "Inventory retrieved", result);
    }

    private void handleGetById(HttpExchange exchange, int id) throws IOException {
        InventoryItem item = inventoryRepo.findById(id);
        if (item == null) {
            HttpUtil.sendError(exchange, 404, "Inventory item not found");
            return;
        }
        HttpUtil.sendSuccess(exchange, "Inventory item retrieved", item.toMap());
    }

    private void handleUpdateStock(HttpExchange exchange) throws IOException {
        Map<String, Object> claims = HttpUtil.authenticate(exchange);
        if (claims == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required to update inventory.");
            return;
        }

        String role = (String) claims.get("role");
        Number userId = (Number) claims.get("userId");
        Pharmacy authPharm = null;
        if ("PHARMACY".equalsIgnoreCase(role)) {
            authPharm = pharmacyRepo.findByUserId(userId.intValue());
            if (authPharm == null) {
                HttpUtil.sendError(exchange, 403, "No verified pharmacy registered for this account.");
                return;
            }
        } else if (!"ADMIN".equalsIgnoreCase(role)) {
            HttpUtil.sendError(exchange, 403, "Access denied: Only pharmacy accounts or administrators can modify inventory.");
            return;
        }

        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);

        Number idNum = (Number) req.get("id");
        if (idNum == null) idNum = (Number) req.get("inventoryId");

        Number qtyNum = (Number) req.get("stockQuantity");
        if (qtyNum == null) qtyNum = (Number) req.get("quantity");

        if (idNum == null || qtyNum == null) {
            HttpUtil.sendError(exchange, 400, "Inventory ID and stock quantity are required.");
            return;
        }

        if (qtyNum.intValue() < 0) {
            HttpUtil.sendError(exchange, 400, "Stock quantity cannot be negative.");
            return;
        }

        InventoryItem existing = inventoryRepo.findById(idNum.intValue());
        if (existing == null) {
            HttpUtil.sendError(exchange, 404, "Inventory item not found.");
            return;
        }

        // Enforce ownership: Pharmacy A cannot edit Pharmacy B
        if (authPharm != null && existing.getPharmacyId() != authPharm.getId()) {
            HttpUtil.sendError(exchange, 403, "Access denied: You do not own this inventory item.");
            return;
        }

        Number priceNum = (Number) req.get("unitPrice");
        if (priceNum == null) priceNum = (Number) req.get("price");
        if (priceNum != null && priceNum.doubleValue() < 0) {
            HttpUtil.sendError(exchange, 400, "Unit price cannot be negative.");
            return;
        }
        double unitPrice = priceNum != null ? priceNum.doubleValue() : existing.getUnitPrice();

        String status = (String) req.get("status");
        if (status == null || status.trim().isEmpty()) {
            int q = qtyNum.intValue();
            status = q == 0 ? "Out of Stock" : (q < 20 ? "Low Stock" : "In Stock");
        }
        String expiry = (String) req.get("expiryDate");
        if (expiry == null || expiry.trim().isEmpty()) expiry = existing.getExpiryDate();

        boolean ok = inventoryRepo.updateStock(idNum.intValue(), qtyNum.intValue(), unitPrice, status, expiry);
        if (ok) {
            InventoryItem updated = inventoryRepo.findById(idNum.intValue());
            HttpUtil.sendSuccess(exchange, "Stock updated successfully", updated != null ? updated.toMap() : null);
        } else {
            HttpUtil.sendError(exchange, 500, "Failed to update stock in database.");
        }
    }

    private void handleAddStock(HttpExchange exchange) throws IOException {
        Map<String, Object> claims = HttpUtil.authenticate(exchange);
        if (claims == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required to add inventory.");
            return;
        }

        String role = (String) claims.get("role");
        Number userId = (Number) claims.get("userId");
        int targetPharmacyId;

        if ("PHARMACY".equalsIgnoreCase(role)) {
            Pharmacy authPharm = pharmacyRepo.findByUserId(userId.intValue());
            if (authPharm == null) {
                HttpUtil.sendError(exchange, 403, "No verified pharmacy registered for this account.");
                return;
            }
            // Strict security: always use authenticated pharmacy ID
            targetPharmacyId = authPharm.getId();
        } else if ("ADMIN".equalsIgnoreCase(role)) {
            String body = HttpUtil.readRequestBody(exchange);
            Map<String, Object> req = JsonUtil.parseMap(body);
            Number pId = (Number) req.get("pharmacyId");
            targetPharmacyId = pId != null ? pId.intValue() : 1;
            // reprocess body in standard flow
            processAddStock(exchange, req, targetPharmacyId);
            return;
        } else {
            HttpUtil.sendError(exchange, 403, "Access denied: Only pharmacy accounts can add stock to inventory.");
            return;
        }

        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);
        processAddStock(exchange, req, targetPharmacyId);
    }

    private void processAddStock(HttpExchange exchange, Map<String, Object> req, int pharmacyId) throws IOException {
        Number medIdNum = (Number) req.get("medicineId");
        Number qtyNum = (Number) req.get("stockQuantity");
        if (qtyNum == null) qtyNum = (Number) req.get("quantity");
        Number priceNum = (Number) req.get("unitPrice");
        if (priceNum == null) priceNum = (Number) req.get("price");
        String batch = (String) req.get("batchNumber");
        String expiry = (String) req.get("expiryDate");

        if (medIdNum == null || qtyNum == null || priceNum == null) {
            HttpUtil.sendError(exchange, 400, "medicineId, stockQuantity, and unitPrice are required.");
            return;
        }

        if (medIdNum.intValue() <= 0) {
            HttpUtil.sendError(exchange, 400, "Invalid medicine ID.");
            return;
        }

        Medicine med = medicineRepo.findById(medIdNum.intValue());
        if (med == null) {
            HttpUtil.sendError(exchange, 404, "Selected medicine does not exist in the medicine catalog.");
            return;
        }

        if (qtyNum.intValue() < 0) {
            HttpUtil.sendError(exchange, 400, "Stock quantity cannot be negative.");
            return;
        }

        if (priceNum.doubleValue() < 0) {
            HttpUtil.sendError(exchange, 400, "Unit price cannot be negative.");
            return;
        }

        // Duplicate inventory protection: addOrUpdateItem updates existing item or creates new record
        boolean ok = inventoryRepo.addOrUpdateItem(pharmacyId, medIdNum.intValue(), batch, qtyNum.intValue(), priceNum.doubleValue(), expiry);
        if (ok) {
            InventoryItem savedItem = inventoryRepo.findByPharmacyAndMedicine(pharmacyId, medIdNum.intValue());
            HttpUtil.sendSuccess(exchange, "Medicine stock saved to pharmacy inventory successfully", savedItem != null ? savedItem.toMap() : null);
        } else {
            HttpUtil.sendError(exchange, 500, "Failed to save inventory item.");
        }
    }

    private void handleAddStockQuantity(HttpExchange exchange) throws IOException {
        Map<String, Object> claims = HttpUtil.authenticate(exchange);
        if (claims == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required.");
            return;
        }

        String role = (String) claims.get("role");
        Number userId = (Number) claims.get("userId");
        Pharmacy authPharm = null;
        if ("PHARMACY".equalsIgnoreCase(role)) {
            authPharm = pharmacyRepo.findByUserId(userId.intValue());
            if (authPharm == null) {
                HttpUtil.sendError(exchange, 403, "No verified pharmacy registered for this account.");
                return;
            }
        } else if (!"ADMIN".equalsIgnoreCase(role)) {
            HttpUtil.sendError(exchange, 403, "Access denied: Only pharmacy accounts or administrators can modify inventory.");
            return;
        }

        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);

        Number idNum = (Number) req.get("id");
        if (idNum == null) idNum = (Number) req.get("inventoryId");

        Number addQtyNum = (Number) req.get("additionalQuantity");
        if (addQtyNum == null) addQtyNum = (Number) req.get("addQuantity");
        if (addQtyNum == null) addQtyNum = (Number) req.get("quantityToAdd");
        if (addQtyNum == null) addQtyNum = (Number) req.get("quantity");

        if (idNum == null || addQtyNum == null) {
            HttpUtil.sendError(exchange, 400, "Inventory item ID and additional quantity are required.");
            return;
        }

        int addQty = addQtyNum.intValue();
        if (addQty <= 0) {
            HttpUtil.sendError(exchange, 400, "Quantity to add must be greater than zero.");
            return;
        }

        InventoryItem existing = inventoryRepo.findById(idNum.intValue());
        if (existing == null) {
            HttpUtil.sendError(exchange, 404, "Inventory item not found.");
            return;
        }

        // Ownership enforcement
        if (authPharm != null && existing.getPharmacyId() != authPharm.getId()) {
            HttpUtil.sendError(exchange, 403, "Access denied: You do not own this inventory item.");
            return;
        }

        int prevQty = existing.getStockQuantity();
        int newQty = prevQty + addQty;
        boolean ok = inventoryRepo.increaseStock(idNum.intValue(), addQty);

        if (ok) {
            InventoryItem updated = inventoryRepo.findById(idNum.intValue());
            Map<String, Object> respData = new HashMap<>();
            respData.put("previousStock", prevQty);
            respData.put("addedQuantity", addQty);
            respData.put("currentStock", newQty);
            respData.put("item", updated != null ? updated.toMap() : null);
            HttpUtil.sendSuccess(exchange, "Stock quantity increased successfully by " + addQty, respData);
        } else {
            HttpUtil.sendError(exchange, 500, "Failed to update stock quantity in database.");
        }
    }

    @SuppressWarnings("unchecked")
    private void handleImportCsv(HttpExchange exchange) throws IOException {
        Map<String, Object> claims = HttpUtil.authenticate(exchange);
        if (claims == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required.");
            return;
        }

        String role = (String) claims.get("role");
        Number userId = (Number) claims.get("userId");
        int targetPharmacyId;

        if ("PHARMACY".equalsIgnoreCase(role)) {
            Pharmacy authPharm = pharmacyRepo.findByUserId(userId.intValue());
            if (authPharm == null) {
                HttpUtil.sendError(exchange, 403, "No pharmacy registered for this account.");
                return;
            }
            targetPharmacyId = authPharm.getId();
        } else if ("ADMIN".equalsIgnoreCase(role)) {
            targetPharmacyId = 1;
        } else {
            HttpUtil.sendError(exchange, 403, "Access denied: Only pharmacy accounts or administrators can import inventory.");
            return;
        }

        String body = HttpUtil.readRequestBody(exchange);
        List<Map<String, Object>> rows = new ArrayList<>();

        if (body != null && !body.trim().isEmpty()) {
            String trimmedBody = body.trim();
            if (trimmedBody.startsWith("{")) {
                Map<String, Object> req = JsonUtil.parseMap(trimmedBody);
                if (req.get("csv") instanceof String) {
                    rows = parseCsvTextToRows((String) req.get("csv"));
                } else if (req.get("rows") instanceof List) {
                    List<?> rawList = (List<?>) req.get("rows");
                    for (Object item : rawList) {
                        if (item instanceof Map) {
                            rows.add((Map<String, Object>) item);
                        }
                    }
                } else if (req.get("pharmacyId") instanceof Map) {
                    Map<?, ?> pMap = (Map<?, ?>) req.get("pharmacyId");
                    if (pMap.get("csv") instanceof String) {
                        rows = parseCsvTextToRows((String) pMap.get("csv"));
                    }
                }
            } else {
                // Direct CSV text payload
                rows = parseCsvTextToRows(trimmedBody);
            }
        }

        if (rows.isEmpty()) {
            HttpUtil.sendError(exchange, 400, "No valid CSV data rows found.");
            return;
        }

        Map<String, Integer> catalogMap = medicineRepo.getAllNameToIdMap();
        Map<String, Object> summary = inventoryService.importInventoryRows(targetPharmacyId, rows, catalogMap);

        HttpUtil.sendSuccess(exchange, "CSV inventory import completed", summary);
    }

    public static List<Map<String, Object>> parseCsvTextToRows(String csvText) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (csvText == null || csvText.trim().isEmpty()) return rows;

        // Strip UTF-8 BOM if present
        if (csvText.startsWith("\uFEFF")) {
            csvText = csvText.substring(1);
        }

        List<List<String>> parsedCsv = parseRfc4180Csv(csvText);
        if (parsedCsv.size() <= 1) return rows;

        List<String> headers = parsedCsv.get(0);
        int medicineNameIdx = -1;
        int stockQuantityIdx = -1;
        int unitPriceIdx = -1;
        int batchNumberIdx = -1;
        int expiryDateIdx = -1;
        int categoryIdx = -1;
        int genericNameIdx = -1;

        for (int i = 0; i < headers.size(); i++) {
            String raw = headers.get(i).trim();
            if (raw.startsWith("\uFEFF")) {
                raw = raw.substring(1).trim();
            }
            String h = raw.toLowerCase().replaceAll("[\\s_#\\-]", "");

            if (h.equals("genericname") || h.equals("generic") || h.equals("salt")) {
                genericNameIdx = i;
            } else if (h.equals("medicinename") || h.equals("medicine") || h.equals("name") || h.equals("drugname") || h.equals("itemname") || (h.contains("medicine") && !h.contains("id"))) {
                medicineNameIdx = i;
            } else if (h.equals("stockquantity") || h.equals("stockqty") || h.equals("stock") || h.equals("quantity") || h.equals("qty")) {
                stockQuantityIdx = i;
            } else if (h.equals("unitprice") || h.equals("unitcost") || h.equals("price") || h.equals("rate") || h.equals("cost") || h.equals("mrp")) {
                unitPriceIdx = i;
            } else if (h.equals("batchnumber") || h.equals("batchno") || h.equals("batch") || h.equals("lotnumber") || h.equals("lotno") || h.equals("lot")) {
                batchNumberIdx = i;
            } else if (h.equals("expirydate") || h.equals("expiry") || h.equals("expdate") || h.equals("exp")) {
                expiryDateIdx = i;
            } else if (h.equals("category") || h.equals("cat")) {
                categoryIdx = i;
            }
        }

        // Fallback for medicine name column if not matched exactly
        if (medicineNameIdx == -1) {
            for (int i = 0; i < headers.size(); i++) {
                String h = headers.get(i).trim().toLowerCase().replaceAll("[\\s_#\\-]", "");
                if (h.contains("name") && !h.contains("generic") && !h.contains("brand")) {
                    medicineNameIdx = i;
                    break;
                }
            }
        }

        for (int i = 1; i < parsedCsv.size(); i++) {
            List<String> lineValues = parsedCsv.get(i);
            if (lineValues.isEmpty() || !hasNonEmptyField(lineValues)) {
                continue; // ignore blank line
            }

            Map<String, Object> r = new HashMap<>();
            if (medicineNameIdx >= 0 && medicineNameIdx < lineValues.size()) {
                r.put("medicineName", lineValues.get(medicineNameIdx).trim());
            }
            if (stockQuantityIdx >= 0 && stockQuantityIdx < lineValues.size()) {
                r.put("stockQuantity", lineValues.get(stockQuantityIdx).trim());
            }
            if (unitPriceIdx >= 0 && unitPriceIdx < lineValues.size()) {
                r.put("unitPrice", lineValues.get(unitPriceIdx).trim());
            }
            if (batchNumberIdx >= 0 && batchNumberIdx < lineValues.size()) {
                r.put("batchNumber", lineValues.get(batchNumberIdx).trim());
            }
            if (expiryDateIdx >= 0 && expiryDateIdx < lineValues.size()) {
                r.put("expiryDate", lineValues.get(expiryDateIdx).trim());
            }
            if (categoryIdx >= 0 && categoryIdx < lineValues.size()) {
                r.put("category", lineValues.get(categoryIdx).trim());
            }
            if (genericNameIdx >= 0 && genericNameIdx < lineValues.size()) {
                r.put("genericName", lineValues.get(genericNameIdx).trim());
            }

            if (r.containsKey("medicineName") && !((String)r.get("medicineName")).isEmpty()) {
                rows.add(r);
            }
        }
        return rows;
    }

    private static List<List<String>> parseRfc4180Csv(String text) {
        List<List<String>> result = new ArrayList<>();
        List<String> currentRow = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;
        int len = text.length();

        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);

            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < len && text.charAt(i + 1) == '"') {
                        currentField.append('"');
                        i++; // skip escaped quote
                    } else {
                        inQuotes = false;
                    }
                } else {
                    currentField.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    currentRow.add(currentField.toString().trim());
                    currentField.setLength(0);
                } else if (c == '\r') {
                    if (i + 1 < len && text.charAt(i + 1) == '\n') {
                        i++;
                    }
                    currentRow.add(currentField.toString().trim());
                    currentField.setLength(0);
                    if (hasNonEmptyField(currentRow)) {
                        result.add(currentRow);
                    }
                    currentRow = new ArrayList<>();
                } else if (c == '\n') {
                    currentRow.add(currentField.toString().trim());
                    currentField.setLength(0);
                    if (hasNonEmptyField(currentRow)) {
                        result.add(currentRow);
                    }
                    currentRow = new ArrayList<>();
                } else {
                    currentField.append(c);
                }
            }
        }

        if (currentField.length() > 0 || !currentRow.isEmpty()) {
            currentRow.add(currentField.toString().trim());
            if (hasNonEmptyField(currentRow)) {
                result.add(currentRow);
            }
        }

        return result;
    }

    private static boolean hasNonEmptyField(List<String> row) {
        if (row == null || row.isEmpty()) return false;
        for (String f : row) {
            if (f != null && !f.trim().isEmpty()) return true;
        }
        return false;
    }

    private void handleStats(HttpExchange exchange, int pharmacyId) throws IOException {
        Map<String, Object> stats = inventoryService.getPharmacyStats(pharmacyId);
        HttpUtil.sendSuccess(exchange, "Pharmacy stock stats", stats);
    }

    private void handleExpiring(HttpExchange exchange, int pharmacyId) throws IOException {
        List<InventoryItem> items = inventoryService.getExpiringSoon(pharmacyId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (InventoryItem item : items) {
            result.add(item.toMap());
        }
        HttpUtil.sendSuccess(exchange, "Expiring inventory items", result);
    }

    private void handleDeleteOrRemove(HttpExchange exchange, int targetId, boolean isMedicineId) throws IOException {
        Map<String, Object> claims = HttpUtil.authenticate(exchange);
        if (claims == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required.");
            return;
        }

        String role = (String) claims.get("role");
        Number userId = (Number) claims.get("userId");
        int targetPharmacyId;

        if ("PHARMACY".equalsIgnoreCase(role)) {
            Pharmacy authPharm = pharmacyRepo.findByUserId(userId.intValue());
            if (authPharm == null) {
                HttpUtil.sendError(exchange, 403, "No verified pharmacy registered for this account.");
                return;
            }
            targetPharmacyId = authPharm.getId();
        } else if ("ADMIN".equalsIgnoreCase(role)) {
            targetPharmacyId = 1;
        } else {
            HttpUtil.sendError(exchange, 403, "Access denied.");
            return;
        }

        boolean ok;
        if (isMedicineId) {
            ok = inventoryRepo.deleteByPharmacyAndMedicine(targetPharmacyId, targetId);
        } else {
            InventoryItem existing = inventoryRepo.findById(targetId);
            if (existing == null) {
                HttpUtil.sendError(exchange, 404, "Inventory item not found.");
                return;
            }
            if (!"ADMIN".equalsIgnoreCase(role) && existing.getPharmacyId() != targetPharmacyId) {
                HttpUtil.sendError(exchange, 403, "Access denied: You do not own this inventory item.");
                return;
            }
            ok = inventoryRepo.deleteByPharmacyAndId(targetPharmacyId, targetId);
        }

        if (ok) {
            HttpUtil.sendSuccess(exchange, "Medicine removed from store inventory successfully", null);
        } else {
            HttpUtil.sendError(exchange, 500, "Failed to remove item from store inventory.");
        }
    }

    private void handleClearStock(HttpExchange exchange, int targetId, boolean isMedicineId) throws IOException {
        Map<String, Object> claims = HttpUtil.authenticate(exchange);
        if (claims == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required.");
            return;
        }

        String role = (String) claims.get("role");
        Number userId = (Number) claims.get("userId");
        int targetPharmacyId;

        if ("PHARMACY".equalsIgnoreCase(role)) {
            Pharmacy authPharm = pharmacyRepo.findByUserId(userId.intValue());
            if (authPharm == null) {
                HttpUtil.sendError(exchange, 403, "No verified pharmacy registered for this account.");
                return;
            }
            targetPharmacyId = authPharm.getId();
        } else if ("ADMIN".equalsIgnoreCase(role)) {
            targetPharmacyId = 1;
        } else {
            HttpUtil.sendError(exchange, 403, "Access denied.");
            return;
        }

        boolean ok;
        if (isMedicineId) {
            ok = inventoryRepo.clearStockByPharmacyAndMedicine(targetPharmacyId, targetId);
        } else {
            InventoryItem existing = inventoryRepo.findById(targetId);
            if (existing == null) {
                HttpUtil.sendError(exchange, 404, "Inventory item not found.");
                return;
            }
            if (!"ADMIN".equalsIgnoreCase(role) && existing.getPharmacyId() != targetPharmacyId) {
                HttpUtil.sendError(exchange, 403, "Access denied: You do not own this inventory item.");
                return;
            }
            ok = inventoryRepo.clearStockByPharmacyAndId(targetPharmacyId, targetId);
        }

        if (ok) {
            HttpUtil.sendSuccess(exchange, "Medicine stock cleared to 0 (Out of Stock). Inventory record preserved.", null);
        } else {
            HttpUtil.sendError(exchange, 500, "Failed to clear stock in database.");
        }
    }

    private void handleClearAllStock(HttpExchange exchange) throws IOException {
        Map<String, Object> claims = HttpUtil.authenticate(exchange);
        if (claims == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required.");
            return;
        }

        String role = (String) claims.get("role");
        Number userId = (Number) claims.get("userId");
        int targetPharmacyId;

        if ("PHARMACY".equalsIgnoreCase(role)) {
            Pharmacy authPharm = pharmacyRepo.findByUserId(userId.intValue());
            if (authPharm == null) {
                HttpUtil.sendError(exchange, 403, "No verified pharmacy registered for this account.");
                return;
            }
            targetPharmacyId = authPharm.getId();
        } else if ("ADMIN".equalsIgnoreCase(role)) {
            targetPharmacyId = 1;
        } else {
            HttpUtil.sendError(exchange, 403, "Access denied.");
            return;
        }

        int updatedRows = inventoryRepo.clearAllStock(targetPharmacyId);
        Map<String, Object> respData = new HashMap<>();
        respData.put("clearedItems", updatedRows);
        HttpUtil.sendSuccess(exchange, "All store inventory stock levels cleared to 0 (Out of Stock). Records preserved.", respData);
    }

    private void handleRemoveAll(HttpExchange exchange) throws IOException {
        Map<String, Object> claims = HttpUtil.authenticate(exchange);
        if (claims == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required.");
            return;
        }

        String role = (String) claims.get("role");
        Number userId = (Number) claims.get("userId");
        int targetPharmacyId;

        if ("PHARMACY".equalsIgnoreCase(role)) {
            Pharmacy authPharm = pharmacyRepo.findByUserId(userId.intValue());
            if (authPharm == null) {
                HttpUtil.sendError(exchange, 403, "No verified pharmacy registered for this account.");
                return;
            }
            targetPharmacyId = authPharm.getId();
        } else if ("ADMIN".equalsIgnoreCase(role)) {
            targetPharmacyId = 1;
        } else {
            HttpUtil.sendError(exchange, 403, "Access denied.");
            return;
        }

        int removedRows = inventoryRepo.removeAllMedicines(targetPharmacyId);
        Map<String, Object> respData = new HashMap<>();
        respData.put("removedItems", removedRows);
        HttpUtil.sendSuccess(exchange, "All medicines removed from your store inventory. Global medicine catalog records are untouched.", respData);
    }

    private void handlePutInventory(HttpExchange exchange, int targetId, boolean isMedicineId) throws IOException {
        Map<String, Object> claims = HttpUtil.authenticate(exchange);
        if (claims == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required.");
            return;
        }

        String role = (String) claims.get("role");
        Number userId = (Number) claims.get("userId");
        int targetPharmacyId;

        if ("PHARMACY".equalsIgnoreCase(role)) {
            Pharmacy authPharm = pharmacyRepo.findByUserId(userId.intValue());
            if (authPharm == null) {
                HttpUtil.sendError(exchange, 403, "No verified pharmacy registered for this account.");
                return;
            }
            targetPharmacyId = authPharm.getId();
        } else if ("ADMIN".equalsIgnoreCase(role)) {
            targetPharmacyId = 1;
        } else {
            HttpUtil.sendError(exchange, 403, "Access denied.");
            return;
        }

        InventoryItem existing;
        if (isMedicineId) {
            existing = inventoryRepo.findByPharmacyAndMedicine(targetPharmacyId, targetId);
        } else {
            existing = inventoryRepo.findById(targetId);
        }

        if (existing == null) {
            HttpUtil.sendError(exchange, 404, "Inventory item not found.");
            return;
        }

        if (!"ADMIN".equalsIgnoreCase(role) && existing.getPharmacyId() != targetPharmacyId) {
            HttpUtil.sendError(exchange, 403, "Access denied: You do not own this inventory item.");
            return;
        }

        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);

        Number qtyNum = (Number) req.get("stockQuantity");
        if (qtyNum == null) qtyNum = (Number) req.get("quantity");
        int stockQuantity = qtyNum != null ? qtyNum.intValue() : existing.getStockQuantity();

        Number priceNum = (Number) req.get("unitPrice");
        if (priceNum == null) priceNum = (Number) req.get("price");
        double unitPrice = priceNum != null ? priceNum.doubleValue() : existing.getUnitPrice();

        if (stockQuantity < 0) {
            HttpUtil.sendError(exchange, 400, "Stock quantity cannot be negative.");
            return;
        }

        if (unitPrice < 0) {
            HttpUtil.sendError(exchange, 400, "Unit price cannot be negative.");
            return;
        }

        String batch = (String) req.get("batchNumber");
        if (batch == null || batch.trim().isEmpty()) batch = existing.getBatchNumber();

        String expiry = (String) req.get("expiryDate");
        if (expiry == null || expiry.trim().isEmpty()) expiry = existing.getExpiryDate();

        String status = (String) req.get("status");
        if (status == null || status.trim().isEmpty()) {
            status = stockQuantity > 20 ? "In Stock" : (stockQuantity > 0 ? "Low Stock" : "Out of Stock");
        }

        boolean ok = inventoryRepo.updateStock(existing.getId(), stockQuantity, unitPrice, status, expiry);
        if (ok) {
            InventoryItem updated = inventoryRepo.findById(existing.getId());
            HttpUtil.sendSuccess(exchange, "Inventory medicine updated successfully", updated != null ? updated.toMap() : null);
        } else {
            HttpUtil.sendError(exchange, 500, "Failed to update inventory in database.");
        }
    }
}

