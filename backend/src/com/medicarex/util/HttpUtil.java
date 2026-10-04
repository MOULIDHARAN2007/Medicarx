package com.medicarex.util;

import com.medicarex.auth.JwtUtil;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class HttpUtil {

    public static void setCorsHeaders(HttpExchange exchange) {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
        headers.set("Access-Control-Max-Age", "86400");
    }

    public static boolean handleOptions(HttpExchange exchange) throws IOException {
        setCorsHeaders(exchange);
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }
        return false;
    }

    public static String readRequestBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int nRead;
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return buffer.toString(StandardCharsets.UTF_8);
    }

    public static Map<String, String> getQueryParams(HttpExchange exchange) {
        Map<String, String> queryParams = new HashMap<>();
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isEmpty()) {
            return queryParams;
        }
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            try {
                if (idx > 0) {
                    String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                    String val = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                    queryParams.put(key, val);
                } else if (!pair.isEmpty()) {
                    queryParams.put(URLDecoder.decode(pair, StandardCharsets.UTF_8), "");
                }
            } catch (Exception ignored) {}
        }
        return queryParams;
    }

    public static Map<String, Object> authenticate(HttpExchange exchange) {
        Headers headers = exchange.getRequestHeaders();
        String authHeader = headers.getFirst("Authorization");
        if (authHeader == null) {
            return null;
        }
        return JwtUtil.validateToken(authHeader);
    }

    public static void sendJsonResponse(HttpExchange exchange, int statusCode, Object data) throws IOException {
        setCorsHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        String json = (data instanceof String && (((String) data).startsWith("{") || ((String) data).startsWith("["))) 
                ? (String) data 
                : JsonUtil.toJson(data);
        byte[] responseBytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    public static void sendSuccess(HttpExchange exchange, String message, Object data) throws IOException {
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", message);
        if (data != null) {
            resp.put("data", data);
        }
        sendJsonResponse(exchange, 200, resp);
    }

    public static void sendPdfResponse(HttpExchange exchange, String filename, byte[] pdfBytes) throws IOException {
        setCorsHeaders(exchange);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/pdf");
        headers.set("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        exchange.sendResponseHeaders(200, pdfBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(pdfBytes);
        }
    }

    public static void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", false);
        resp.put("error", message);
        sendJsonResponse(exchange, statusCode, resp);
    }
}
