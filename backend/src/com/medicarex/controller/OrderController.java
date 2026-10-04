package com.medicarex.controller;

import com.medicarex.model.Order;
import com.medicarex.model.OrderItem;
import com.medicarex.model.Pharmacy;
import com.medicarex.repository.OrderRepository;
import com.medicarex.repository.PharmacyRepository;
import com.medicarex.util.HttpUtil;
import com.medicarex.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OrderController implements HttpHandler {
    private final OrderRepository orderRepo = new OrderRepository();
    private final PharmacyRepository pharmacyRepo = new PharmacyRepository();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpUtil.handleOptions(exchange)) return;

        Map<String, Object> claims = HttpUtil.authenticate(exchange);
        if (claims == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required to access orders.");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if (path.equals("/api/orders") && "GET".equalsIgnoreCase(method)) {
            handleList(exchange, claims);
        } else if (path.equals("/api/orders/status") && "POST".equalsIgnoreCase(method)) {
            handleUpdateStatus(exchange);
        } else if (path.equals("/api/orders/create") && "POST".equalsIgnoreCase(method)) {
            handleCreate(exchange);
        } else {
            HttpUtil.sendError(exchange, 404, "Endpoint not found");
        }
    }

    private void handleList(HttpExchange exchange, Map<String, Object> claims) throws IOException {
        Map<String, String> queryParams = HttpUtil.getQueryParams(exchange);
        String status = queryParams.get("status");
        String role = (String) claims.get("role");
        Number userId = (Number) claims.get("userId");

        int pharmacyId = 1;
        if (queryParams.containsKey("pharmacyId")) {
            try { pharmacyId = Integer.parseInt(queryParams.get("pharmacyId")); } catch (Exception ignored) {}
        } else if ("PHARMACY".equalsIgnoreCase(role)) {
            Pharmacy p = pharmacyRepo.findByUserId(userId.intValue());
            if (p != null) pharmacyId = p.getId();
        }

        List<Order> orders;
        if ("ADMIN".equalsIgnoreCase(role) && !queryParams.containsKey("pharmacyId")) {
            orders = orderRepo.findAll(status);
        } else {
            orders = orderRepo.findByPharmacyId(pharmacyId, status);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Order o : orders) {
            result.add(o.toMap());
        }
        HttpUtil.sendSuccess(exchange, "Orders retrieved", result);
    }

    private void handleUpdateStatus(HttpExchange exchange) throws IOException {
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);

        Number orderIdNum = (Number) req.get("orderId");
        String status = (String) req.get("status");

        if (orderIdNum == null || status == null || status.trim().isEmpty()) {
            HttpUtil.sendError(exchange, 400, "orderId and status are required");
            return;
        }

        boolean ok = orderRepo.updateStatus(orderIdNum.intValue(), status);
        if (ok) {
            Order updated = orderRepo.findById(orderIdNum.intValue());
            HttpUtil.sendSuccess(exchange, "Order status updated to " + status, updated != null ? updated.toMap() : null);
        } else {
            HttpUtil.sendError(exchange, 500, "Failed to update order status");
        }
    }

    @SuppressWarnings("unchecked")
    private void handleCreate(HttpExchange exchange) throws IOException {
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);

        Number pharmIdNum = (Number) req.get("pharmacyId");
        Number patIdNum = (Number) req.get("patientId");
        String customerName = (String) req.get("customerName");
        String customerPhone = (String) req.get("customerPhone");
        Number totalAmountNum = (Number) req.get("totalAmount");
        String paymentMethod = (String) req.get("paymentMethod");
        String orderDate = (String) req.get("orderDate");

        if (pharmIdNum == null || customerName == null || totalAmountNum == null) {
            HttpUtil.sendError(exchange, 400, "pharmacyId, customerName, and totalAmount are required");
            return;
        }

        List<OrderItem> items = new ArrayList<>();
        Object rawItems = req.get("items");
        if (rawItems instanceof List) {
            for (Object obj : (List<?>) rawItems) {
                if (obj instanceof Map) {
                    Map<String, Object> itemMap = (Map<String, Object>) obj;
                    OrderItem item = new OrderItem();
                    Number medId = (Number) itemMap.get("medicineId");
                    if (medId != null) item.setMedicineId(medId.intValue());
                    item.setMedicineName((String) itemMap.get("medicineName"));
                    Number qty = (Number) itemMap.get("quantity");
                    item.setQuantity(qty != null ? qty.intValue() : 1);
                    Number price = (Number) itemMap.get("unitPrice");
                    item.setUnitPrice(price != null ? price.doubleValue() : 0.0);
                    items.add(item);
                }
            }
        }

        Order created = orderRepo.createOrder(
                pharmIdNum.intValue(),
                patIdNum != null ? patIdNum.intValue() : null,
                customerName,
                customerPhone,
                totalAmountNum.doubleValue(),
                paymentMethod,
                orderDate != null ? orderDate : "2026-09-06",
                items
        );

        if (created != null) {
            HttpUtil.sendSuccess(exchange, "Order placed successfully", created.toMap());
        } else {
            HttpUtil.sendError(exchange, 500, "Failed to create order");
        }
    }
}
