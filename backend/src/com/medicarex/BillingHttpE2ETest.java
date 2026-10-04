package com.medicarex;

import com.medicarex.auth.JwtUtil;
import com.medicarex.config.AppConfig;
import com.medicarex.config.DatabaseConnection;
import com.medicarex.model.Pharmacy;
import com.medicarex.repository.PharmacyRepository;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class BillingHttpE2ETest {

    private static final String BASE_URL = "http://localhost:" + AppConfig.PORT;

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("   Running Pharmacy POS Billing HTTP E2E Tests    ");
        System.out.println("==================================================");

        DatabaseConnection.initialize();
        PharmacyRepository pharmRepo = new PharmacyRepository();
        com.medicarex.repository.InventoryRepository invRepo = new com.medicarex.repository.InventoryRepository();

        Pharmacy pharm = pharmRepo.findById(1);
        if (pharm == null) {
            pharm = pharmRepo.findAll().get(0);
        }
        int userId = pharm.getUserId() != null ? pharm.getUserId() : 4;
        
        // Ensure medicine 2 (Paracetamol 650mg) has stock in this pharmacy for POS test
        com.medicarex.model.InventoryItem existingItem = invRepo.findByPharmacyAndMedicine(pharm.getId(), 2);
        if (existingItem == null || existingItem.getStockQuantity() < 10) {
            invRepo.addOrUpdateItem(pharm.getId(), 2, "BATCH-POS-TEST", 50, 32.0, "2028-12-31");
        }

        String token = JwtUtil.generateToken(userId, "pharmacy@medicarex.com", "PHARMACY", pharm.getPharmacyName());
        System.out.println("[Auth] Generated token for Pharmacy ID " + pharm.getId() + ", User " + userId + " ('" + pharm.getPharmacyName() + "')");

        int passed = 0;
        int failed = 0;

        try {
            // 1. Test Barcode Scan Endpoint
            System.out.println("\n--- Step 1: GET /api/pharmacy/inventory/barcode/8901030001021 ---");
            HttpResponse res1 = sendRequest("GET", "/api/pharmacy/inventory/barcode/8901030001021", null, token);
            System.out.println("Status: " + res1.statusCode + ", Body: " + res1.body);
            if (res1.statusCode == 200 && res1.body.contains("Paracetamol 650mg")) {
                System.out.println("PASS: Barcode scan endpoint returned correct medicine data.");
                passed++;
            } else {
                System.err.println("FAIL: Barcode scan endpoint failed.");
                failed++;
            }

            // 2. Test POS Bill Creation via HTTP POST
            System.out.println("\n--- Step 2: POST /api/pharmacy/bills ---");
            String billJson = "{\"customerName\":\"Alice Smith\",\"customerPhone\":\"+91 9123456780\",\"discount\":5.0,\"tax\":2.5,\"paymentMethod\":\"UPI\",\"notes\":\"E2E HTTP Test\",\"items\":[{\"medicineId\":2,\"quantity\":2}]}";
            HttpResponse res2 = sendRequest("POST", "/api/pharmacy/bills", billJson, token);
            System.out.println("Status: " + res2.statusCode + ", Body: " + res2.body);
            if (res2.statusCode == 200 && res2.body.contains("\"success\":true") && res2.body.contains("INV-")) {
                System.out.println("PASS: Bill created successfully via HTTP API.");
                passed++;
            } else {
                System.err.println("FAIL: Bill creation failed.");
                failed++;
            }

            // 3. Test List Bills via HTTP GET
            System.out.println("\n--- Step 3: GET /api/pharmacy/bills ---");
            HttpResponse res3 = sendRequest("GET", "/api/pharmacy/bills", null, token);
            System.out.println("Status: " + res3.statusCode + ", Body length: " + res3.body.length());
            if (res3.statusCode == 200 && res3.body.contains("Alice Smith")) {
                System.out.println("PASS: Billing history contains newly created bill.");
                passed++;
            } else {
                System.err.println("FAIL: Bill history listing failed.");
                failed++;
            }

            // 4. Test Stock Audit Trail via HTTP GET
            System.out.println("\n--- Step 4: GET /api/pharmacy/stock-audit ---");
            HttpResponse res4 = sendRequest("GET", "/api/pharmacy/stock-audit", null, token);
            System.out.println("Status: " + res4.statusCode + ", Body length: " + res4.body.length());
            if (res4.statusCode == 200 && res4.body.contains("SALE")) {
                System.out.println("PASS: Stock audit trail returned SALE events.");
                passed++;
            } else {
                System.err.println("FAIL: Stock audit listing failed.");
                failed++;
            }

        } catch (Exception e) {
            System.err.println("FATAL: " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        System.out.println("\n==================================================");
        System.out.println("HTTP E2E TEST SUMMARY: " + passed + " PASSED, " + failed + " FAILED");
        System.out.println("==================================================");
    }

    private static class HttpResponse {
        int statusCode;
        String body;
        HttpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }
    }

    private static HttpResponse sendRequest(String method, String path, String jsonBody, String token) throws Exception {
        URL url = URI.create(BASE_URL + path).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        if (token != null) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }
        conn.setRequestProperty("Content-Type", "application/json");

        if (jsonBody != null && !jsonBody.isEmpty()) {
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }
        }

        int status = conn.getResponseCode();
        InputStream is = (status >= 200 && status < 400) ? conn.getInputStream() : conn.getErrorStream();
        String body = "";
        if (is != null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            while ((n = is.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            body = baos.toString(StandardCharsets.UTF_8);
        }
        return new HttpResponse(status, body);
    }
}
