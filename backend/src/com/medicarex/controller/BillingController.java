package com.medicarex.controller;

import com.medicarex.model.Bill;
import com.medicarex.model.InventoryItem;
import com.medicarex.model.InventoryTransaction;
import com.medicarex.model.Medicine;
import com.medicarex.model.Pharmacy;
import com.medicarex.repository.BillingRepository;
import com.medicarex.repository.InventoryRepository;
import com.medicarex.repository.MedicineRepository;
import com.medicarex.repository.PharmacyRepository;
import com.medicarex.util.HttpUtil;
import com.medicarex.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class BillingController implements HttpHandler {

    private final BillingRepository billingRepo = new BillingRepository();
    private final PharmacyRepository pharmacyRepo = new PharmacyRepository();
    private final MedicineRepository medicineRepo = new MedicineRepository();
    private final InventoryRepository inventoryRepo = new InventoryRepository();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpUtil.handleOptions(exchange)) return;

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod().toUpperCase();

        try {
            // Barcode lookup: GET /api/pharmacy/inventory/barcode/{barcode} or /api/pharmacy/barcode/{barcode}
            if ((path.startsWith("/api/pharmacy/inventory/barcode/") || path.startsWith("/api/pharmacy/barcode/")) && "GET".equals(method)) {
                String prefix = path.startsWith("/api/pharmacy/inventory/barcode/") ? "/api/pharmacy/inventory/barcode/" : "/api/pharmacy/barcode/";
                String rawBarcode = path.substring(prefix.length());
                String barcode = URLDecoder.decode(rawBarcode, StandardCharsets.UTF_8.name());
                handleBarcodeScan(exchange, barcode);
                return;
            }

            // Medicine search for POS Billing: GET /api/pharmacy/medicines/search or /api/pharmacy/medicines or /api/pharmacy/inventory/search
            if ((path.equals("/api/pharmacy/medicines/search") || path.equals("/api/pharmacy/medicines") || path.equals("/api/pharmacy/inventory/search")) && "GET".equals(method)) {
                handleSearchMedicines(exchange);
                return;
            }

            // Create POS Bill: POST /api/pharmacy/bills or /api/pharmacy/billing
            if ((path.equals("/api/pharmacy/bills") || path.equals("/api/pharmacy/billing")) && "POST".equals(method)) {
                handleCreateBill(exchange);
                return;
            }

            // List Bills: GET /api/pharmacy/bills or /api/pharmacy/billing
            if ((path.equals("/api/pharmacy/bills") || path.equals("/api/pharmacy/billing")) && "GET".equals(method)) {
                handleListBills(exchange);
                return;
            }

            // Stock audit transactions: GET /api/pharmacy/stock-audit or /api/pharmacy/inventory-transactions
            if ((path.equals("/api/pharmacy/stock-audit") || path.equals("/api/pharmacy/inventory-transactions")) && "GET".equals(method)) {
                handleStockAudit(exchange);
                return;
            }

            // Cancel Bill: POST /api/pharmacy/bills/{id}/cancel
            if (path.matches("^/api/pharmacy/bills/\\d+/cancel$") && "POST".equals(method)) {
                String idStr = path.replaceAll("^/api/pharmacy/bills/", "").replaceAll("/cancel$", "");
                int billId = Integer.parseInt(idStr);
                handleCancelBill(exchange, billId);
                return;
            }

            // Get Single Bill: GET /api/pharmacy/bills/{id}
            if (path.matches("^/api/pharmacy/bills/\\d+$") && "GET".equals(method)) {
                String idStr = path.substring("/api/pharmacy/bills/".length());
                int billId = Integer.parseInt(idStr);
                handleGetBill(exchange, billId);
                return;
            }

            // Fallback 404
            HttpUtil.sendError(exchange, 404, "Endpoint not found: " + path);

        } catch (Exception e) {
            e.printStackTrace();
            HttpUtil.sendError(exchange, 500, "Internal Server Error: " + e.getMessage());
        }
    }

    private void handleSearchMedicines(HttpExchange exchange) throws IOException {
        Map<String, Object> claims = HttpUtil.authenticate(exchange);
        if (claims == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required to search medicines.");
            return;
        }

        String role = (String) claims.get("role");
        if (!"PHARMACY".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role)) {
            HttpUtil.sendError(exchange, 403, "Access restricted to pharmacy staff.");
            return;
        }

        Number userIdNum = (Number) claims.get("userId");
        Pharmacy pharmacy = pharmacyRepo.findByUserId(userIdNum.intValue());
        if (pharmacy == null && !"ADMIN".equalsIgnoreCase(role)) {
            HttpUtil.sendError(exchange, 403, "No pharmacy record found for this user account.");
            return;
        }

        int pharmacyId = pharmacy != null ? pharmacy.getId() : 1;

        Map<String, String> queryParams = HttpUtil.getQueryParams(exchange);
        String query = queryParams.getOrDefault("q", queryParams.getOrDefault("search", queryParams.get("query")));
        int limit = 50;
        if (queryParams.containsKey("limit")) {
            try {
                limit = Integer.parseInt(queryParams.get("limit"));
            } catch (Exception ignored) {}
        }

        List<Map<String, Object>> list = inventoryRepo.searchPharmacyMedicines(pharmacyId, query, limit);
        HttpUtil.sendSuccess(exchange, "Medicines retrieved successfully", list);
    }

    private void handleBarcodeScan(HttpExchange exchange, String barcode) throws IOException {
        Map<String, Object> claims = HttpUtil.authenticate(exchange);
        if (claims == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required to scan medicines.");
            return;
        }

        String role = (String) claims.get("role");
        if (!"PHARMACY".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role)) {
            HttpUtil.sendError(exchange, 403, "Access restricted to pharmacy staff.");
            return;
        }

        Number userIdNum = (Number) claims.get("userId");
        Pharmacy pharmacy = pharmacyRepo.findByUserId(userIdNum.intValue());
        if (pharmacy == null && !"ADMIN".equalsIgnoreCase(role)) {
            HttpUtil.sendError(exchange, 403, "No pharmacy record found for this user account.");
            return;
        }

        int pharmacyId = pharmacy != null ? pharmacy.getId() : 1;

        if (barcode == null || barcode.trim().isEmpty()) {
            HttpUtil.sendError(exchange, 400, "Barcode is required.");
            return;
        }

        // 1. Check if medicine exists globally
        Medicine medicine = medicineRepo.findByBarcode(barcode.trim());
        if (medicine == null) {
            HttpUtil.sendError(exchange, 404, "Medicine not found.");
            return;
        }

        // 2. Check if this pharmacy stocks this medicine
        InventoryItem inv = inventoryRepo.findByPharmacyAndMedicine(pharmacyId, medicine.getId());
        if (inv == null) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("medicineId", medicine.getId());
            resp.put("medicineName", medicine.getName());
            resp.put("barcode", barcode);
            resp.put("availableStock", 0);
            resp.put("status", "Not In Pharmacy");
            HttpUtil.sendError(exchange, 404, "Medicine is not available in your inventory.");
            return;
        }

        // 3. Check stock level
        if (inv.getStockQuantity() <= 0) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("medicineId", medicine.getId());
            resp.put("medicineName", medicine.getName());
            resp.put("barcode", barcode);
            resp.put("availableStock", 0);
            resp.put("status", "Out of Stock");
            HttpUtil.sendError(exchange, 400, "Out of stock.");
            return;
        }

        // 4. Return item ready for billing cart
        Map<String, Object> data = new HashMap<>();
        data.put("medicineId", medicine.getId());
        data.put("name", medicine.getName());
        data.put("brand", medicine.getBrand());
        data.put("dosage", medicine.getDosage());
        data.put("form", medicine.getForm());
        data.put("category", medicine.getCategory());
        data.put("barcode", barcode.trim());
        data.put("prescriptionRequired", medicine.isPrescriptionRequired());
        data.put("unitPrice", inv.getUnitPrice() > 0 ? inv.getUnitPrice() : medicine.getBasePrice());
        data.put("availableStock", inv.getStockQuantity());
        data.put("batchNumber", inv.getBatchNumber());
        data.put("status", inv.getStatus());

        HttpUtil.sendSuccess(exchange, "Medicine found in inventory", data);
    }

    private void handleCreateBill(HttpExchange exchange) throws IOException {
        Map<String, Object> claims = HttpUtil.authenticate(exchange);
        if (claims == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required to create a bill.");
            return;
        }

        String role = (String) claims.get("role");
        if (!"PHARMACY".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role)) {
            HttpUtil.sendError(exchange, 403, "Access denied. Pharmacy role required.");
            return;
        }

        Number userIdNum = (Number) claims.get("userId");
        int userId = userIdNum.intValue();
        Pharmacy pharmacy = pharmacyRepo.findByUserId(userId);
        if (pharmacy == null && !"ADMIN".equalsIgnoreCase(role)) {
            HttpUtil.sendError(exchange, 403, "Pharmacy profile not found for this account.");
            return;
        }

        int pharmacyId = pharmacy != null ? pharmacy.getId() : 1;
        String userName = (String) claims.get("name");
        if (userName == null || userName.isEmpty()) userName = "Staff";

        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);
        if (req == null || req.isEmpty()) {
            HttpUtil.sendError(exchange, 400, "Invalid JSON payload.");
            return;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) req.get("items");
        if (items == null || items.isEmpty()) {
            HttpUtil.sendError(exchange, 400, "Billing cart cannot be empty.");
            return;
        }

        Integer patientId = req.get("patientId") != null ? ((Number) req.get("patientId")).intValue() : null;
        String customerName = (String) req.get("customerName");
        String customerPhone = (String) req.get("customerPhone");
        Integer prescriptionId = req.get("prescriptionId") != null ? ((Number) req.get("prescriptionId")).intValue() : null;
        double discount = req.get("discount") != null ? ((Number) req.get("discount")).doubleValue() : 0.0;
        double tax = req.get("tax") != null ? ((Number) req.get("tax")).doubleValue() : 0.0;
        String paymentMethod = (String) req.get("paymentMethod");
        String notes = (String) req.get("notes");

        try {
            BillingRepository.BillCreationResult result = billingRepo.createBillWithInventoryDeduction(
                    pharmacyId,
                    userId,
                    patientId,
                    customerName,
                    customerPhone,
                    prescriptionId,
                    discount,
                    tax,
                    paymentMethod,
                    notes,
                    items,
                    userName
            );

            Map<String, Object> resp = new HashMap<>();
            resp.put("bill", result.bill != null ? result.bill.toMap() : null);
            resp.put("warnings", result.stockWarnings);
            HttpUtil.sendSuccess(exchange, "Bill completed successfully. Inventory deducted.", resp);

        } catch (IllegalArgumentException e) {
            HttpUtil.sendError(exchange, 400, e.getMessage());
        } catch (IllegalStateException e) {
            HttpUtil.sendError(exchange, 400, e.getMessage());
        } catch (ConcurrentModificationException e) {
            HttpUtil.sendError(exchange, 409, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            HttpUtil.sendError(exchange, 500, "Transaction failed: " + e.getMessage());
        }
    }

    private void handleListBills(HttpExchange exchange) throws IOException {
        Map<String, Object> claims = HttpUtil.authenticate(exchange);
        if (claims == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required.");
            return;
        }

        String role = (String) claims.get("role");
        if (!"PHARMACY".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role)) {
            HttpUtil.sendError(exchange, 403, "Access restricted to pharmacy.");
            return;
        }

        Number userIdNum = (Number) claims.get("userId");
        Pharmacy pharmacy = pharmacyRepo.findByUserId(userIdNum.intValue());
        int pharmacyId = pharmacy != null ? pharmacy.getId() : 1;

        Map<String, String> qp = HttpUtil.getQueryParams(exchange);
        String q = qp.get("q");
        String status = qp.get("status");
        int limit = 50;
        int offset = 0;
        try { if (qp.containsKey("limit")) limit = Integer.parseInt(qp.get("limit")); } catch (Exception ignored) {}
        try { if (qp.containsKey("offset")) offset = Integer.parseInt(qp.get("offset")); } catch (Exception ignored) {}

        List<Bill> bills = billingRepo.findBillsByPharmacy(pharmacyId, q, status, limit, offset);
        List<Map<String, Object>> billList = new ArrayList<>();
        for (Bill b : bills) {
            billList.add(b.toMap());
        }

        HttpUtil.sendSuccess(exchange, "Bills retrieved successfully", billList);
    }

    private void handleGetBill(HttpExchange exchange, int billId) throws IOException {
        Map<String, Object> claims = HttpUtil.authenticate(exchange);
        if (claims == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required.");
            return;
        }

        Bill bill = billingRepo.findBillById(billId);
        if (bill == null) {
            HttpUtil.sendError(exchange, 404, "Bill not found.");
            return;
        }

        String role = (String) claims.get("role");
        if ("PHARMACY".equalsIgnoreCase(role)) {
            Number userIdNum = (Number) claims.get("userId");
            Pharmacy pharmacy = pharmacyRepo.findByUserId(userIdNum.intValue());
            if (pharmacy != null && bill.getPharmacyId() != pharmacy.getId()) {
                HttpUtil.sendError(exchange, 403, "Unauthorized: Bill belongs to another pharmacy.");
                return;
            }
        }

        HttpUtil.sendSuccess(exchange, "Bill retrieved successfully", bill.toMap());
    }

    private void handleCancelBill(HttpExchange exchange, int billId) throws IOException {
        Map<String, Object> claims = HttpUtil.authenticate(exchange);
        if (claims == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required.");
            return;
        }

        String role = (String) claims.get("role");
        if (!"PHARMACY".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role)) {
            HttpUtil.sendError(exchange, 403, "Access restricted to pharmacy staff.");
            return;
        }

        Number userIdNum = (Number) claims.get("userId");
        int userId = userIdNum.intValue();
        Pharmacy pharmacy = pharmacyRepo.findByUserId(userId);
        if (pharmacy == null && !"ADMIN".equalsIgnoreCase(role)) {
            HttpUtil.sendError(exchange, 403, "Pharmacy profile not found.");
            return;
        }

        int pharmacyId = pharmacy != null ? pharmacy.getId() : 1;
        String userName = (String) claims.get("name");

        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);
        String reason = req != null && req.get("reason") != null ? (String) req.get("reason") : "Customer return / cancellation";

        try {
            boolean success = billingRepo.cancelBillWithRestock(billId, pharmacyId, reason, userName != null ? userName : "Staff");
            if (success) {
                HttpUtil.sendSuccess(exchange, "Bill cancelled successfully and inventory restored to stock.", null);
            } else {
                HttpUtil.sendError(exchange, 400, "Could not cancel bill.");
            }
        } catch (SecurityException e) {
            HttpUtil.sendError(exchange, 403, e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException e) {
            HttpUtil.sendError(exchange, 400, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            HttpUtil.sendError(exchange, 500, "Error cancelling bill: " + e.getMessage());
        }
    }

    private void handleStockAudit(HttpExchange exchange) throws IOException {
        Map<String, Object> claims = HttpUtil.authenticate(exchange);
        if (claims == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required.");
            return;
        }

        String role = (String) claims.get("role");
        if (!"PHARMACY".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role)) {
            HttpUtil.sendError(exchange, 403, "Access restricted to pharmacy staff.");
            return;
        }

        Number userIdNum = (Number) claims.get("userId");
        Pharmacy pharmacy = pharmacyRepo.findByUserId(userIdNum.intValue());
        int pharmacyId = pharmacy != null ? pharmacy.getId() : 1;

        Map<String, String> qp = HttpUtil.getQueryParams(exchange);
        Integer medicineId = null;
        try { if (qp.containsKey("medicineId")) medicineId = Integer.parseInt(qp.get("medicineId")); } catch (Exception ignored) {}
        int limit = 50;
        try { if (qp.containsKey("limit")) limit = Integer.parseInt(qp.get("limit")); } catch (Exception ignored) {}

        List<InventoryTransaction> list = billingRepo.findInventoryTransactions(pharmacyId, medicineId, limit);
        List<Map<String, Object>> result = new ArrayList<>();
        for (InventoryTransaction it : list) {
            result.add(it.toMap());
        }

        HttpUtil.sendSuccess(exchange, "Stock audit log retrieved", result);
    }
}
