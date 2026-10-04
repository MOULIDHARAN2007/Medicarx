package com.medicarex.controller;

import com.medicarex.model.Notification;
import com.medicarex.repository.NotificationRepository;
import com.medicarex.util.HttpUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotificationController implements HttpHandler {
    private final NotificationRepository notifRepo = new NotificationRepository();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpUtil.handleOptions(exchange)) return;

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if (path.equals("/api/notifications") && "GET".equalsIgnoreCase(method)) {
            handleList(exchange);
        } else if (path.equals("/api/notifications/unread-count") && "GET".equalsIgnoreCase(method)) {
            handleUnreadCount(exchange);
        } else if (path.equals("/api/notifications/read-all") && "POST".equalsIgnoreCase(method)) {
            handleMarkAllRead(exchange);
        } else if (path.startsWith("/api/notifications/read/") && "POST".equalsIgnoreCase(method)) {
            String idStr = path.substring("/api/notifications/read/".length());
            try {
                handleMarkRead(exchange, Integer.parseInt(idStr));
            } catch (Exception e) {
                HttpUtil.sendError(exchange, 400, "Invalid notification ID");
            }
        } else {
            HttpUtil.sendError(exchange, 404, "Endpoint not found");
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
        String type = queryParams.get("type");

        List<Notification> list = notifRepo.findByUserId(userId, type);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Notification n : list) {
            result.add(n.toMap());
        }
        HttpUtil.sendSuccess(exchange, "Notifications retrieved", result);
    }

    private void handleUnreadCount(HttpExchange exchange) throws IOException {
        Map<String, Object> claims = HttpUtil.authenticate(exchange);
        if (claims == null) {
            HttpUtil.sendError(exchange, 401, "Unauthorized access.");
            return;
        }
        int userId = ((Number) claims.get("userId")).intValue();

        int count = notifRepo.countUnread(userId);
        Map<String, Object> res = new HashMap<>();
        res.put("unreadCount", count);
        HttpUtil.sendSuccess(exchange, "Unread notification count", res);
    }

    private void handleMarkRead(HttpExchange exchange, int id) throws IOException {
        Map<String, Object> claims = HttpUtil.authenticate(exchange);
        int userId = 1;
        if (claims != null) {
            userId = ((Number) claims.get("userId")).intValue();
        }

        boolean ok = notifRepo.markAsRead(id, userId);
        HttpUtil.sendSuccess(exchange, ok ? "Notification marked as read" : "Already read", null);
    }

    private void handleMarkAllRead(HttpExchange exchange) throws IOException {
        Map<String, Object> claims = HttpUtil.authenticate(exchange);
        int userId = 1;
        if (claims != null) {
            userId = ((Number) claims.get("userId")).intValue();
        }

        notifRepo.markAllAsRead(userId);
        HttpUtil.sendSuccess(exchange, "All notifications marked as read", null);
    }
}
