package com.medicarex.controller;

import com.medicarex.config.AppConfig;
import com.medicarex.util.HttpUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class StaticFileHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpUtil.handleOptions(exchange)) return;

        String path = exchange.getRequestURI().getPath();
        if (path == null || path.equals("/") || path.isEmpty()) {
            path = "/index.html";
        }

        path = URLDecoder.decode(path, StandardCharsets.UTF_8);

        // Security check for directory traversal
        if (path.contains("..")) {
            HttpUtil.sendError(exchange, 403, "Access denied");
            return;
        }

        File file = new File(AppConfig.FRONTEND_DIR, path.startsWith("/") ? path.substring(1) : path);

        if (!file.exists() || file.isDirectory()) {
            // Check if it was an SPA route or html missing extension
            File htmlFile = new File(AppConfig.FRONTEND_DIR, (path.startsWith("/") ? path.substring(1) : path) + ".html");
            if (htmlFile.exists() && !htmlFile.isDirectory()) {
                file = htmlFile;
            } else {
                // Fallback to index.html for root navigation
                file = new File(AppConfig.FRONTEND_DIR, "index.html");
                if (!file.exists()) {
                    HttpUtil.sendError(exchange, 404, "File not found: " + path);
                    return;
                }
            }
        }

        String mimeType = getMimeType(file.getName());
        exchange.getResponseHeaders().set("Content-Type", mimeType);
        HttpUtil.setCorsHeaders(exchange);

        exchange.sendResponseHeaders(200, file.length());
        try (OutputStream os = exchange.getResponseBody();
             FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = fis.read(buffer)) != -1) {
                os.write(buffer, 0, count);
            }
            os.flush();
        }
    }

    private String getMimeType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=UTF-8";
        if (lower.endsWith(".css")) return "text/css; charset=UTF-8";
        if (lower.endsWith(".js") || lower.endsWith(".mjs")) return "application/javascript; charset=UTF-8";
        if (lower.endsWith(".json")) return "application/json; charset=UTF-8";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".ico")) return "image/x-icon";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".woff2")) return "font/woff2";
        if (lower.endsWith(".woff")) return "font/woff";
        if (lower.endsWith(".ttf")) return "font/ttf";
        return "application/octet-stream";
    }
}
