package com.medicarex;

import com.medicarex.config.DatabaseConnection;
import com.medicarex.model.Pharmacy;
import com.medicarex.model.User;
import com.medicarex.repository.PharmacyRepository;
import com.medicarex.repository.UserRepository;
import com.medicarex.util.JsonUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

public class LargeCsvImportTest {
    private static final String BASE_URL = "http://localhost:8080";

    static class HttpResponse {
        int statusCode;
        String body;
        Map<String, Object> jsonBody;

        HttpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
            try {
                this.jsonBody = JsonUtil.parseMap(body);
            } catch (Exception e) {
                this.jsonBody = new HashMap<>();
            }
        }
    }

    private static HttpResponse sendHttp(String method, String endpoint, String jsonPayload, String token) {
        try {
            URL url = URI.create(BASE_URL + endpoint).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(120000);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");

            if (token != null && !token.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }

            if (jsonPayload != null && !jsonPayload.isEmpty()) {
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
            }

            int code = conn.getResponseCode();
            BufferedReader br;
            if (code >= 200 && code < 400) {
                br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            } else {
                br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
            }

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            br.close();
            return new HttpResponse(code, sb.toString());
        } catch (Exception e) {
            return new HttpResponse(500, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        System.out.println("=================================================================");
        System.out.println("   MEDICAREX — 12,000 ROW CSV IMPORT VERIFICATION SUITE          ");
        System.out.println("=================================================================");

        File csvFile = new File("d:/MedcareX/medicarex_inventory_12000.csv");
        if (!csvFile.exists()) {
            csvFile = new File("medicarex_inventory_12000.csv");
        }
        if (!csvFile.exists()) {
            System.err.println("FATAL: medicarex_inventory_12000.csv not found!");
            System.exit(1);
        }

        String csvContent = Files.readString(csvFile.toPath(), StandardCharsets.UTF_8);
        List<String> rawLines = Files.readAllLines(csvFile.toPath(), StandardCharsets.UTF_8);
        int totalDataRowsInFile = rawLines.size() - 1; // excluding header
        System.out.println("CSV File loaded: " + csvFile.getAbsolutePath());
        System.out.println("Total lines in file: " + rawLines.size() + " (Header + " + totalDataRowsInFile + " data rows)");

        // 1. Provision fresh test pharmacy
        long ts = System.currentTimeMillis();
        String email = "pharmacy_12k_" + ts + "@medicarex.test";
        String pass = "SecurePass123!";
        Map<String, Object> regReq = new HashMap<>();
        regReq.put("email", email);
        regReq.put("password", pass);
        regReq.put("fullName", "Apollo Pharmacy 12K Test");
        regReq.put("phone", "+91 98888 77777");
        regReq.put("role", "PHARMACY");
        regReq.put("pharmacyName", "Apollo Pharmacy Large Stock");
        regReq.put("address", "789 Health Way");
        regReq.put("city", "Bengaluru");
        regReq.put("state", "Karnataka");
        regReq.put("postalCode", "560001");

        sendHttp("POST", "/api/auth/register", JsonUtil.toJson(regReq), null);
        UserRepository userRepo = new UserRepository();
        User u = userRepo.findByEmail(email);
        if (u != null) {
            userRepo.updateStatus(u.getId(), true);
        }
        Map<String, Object> loginReq = new HashMap<>();
        loginReq.put("email", email);
        loginReq.put("password", pass);
        HttpResponse loginRes = sendHttp("POST", "/api/auth/login", JsonUtil.toJson(loginReq), null);
        Map<String, Object> regData = loginRes.jsonBody != null ? (Map<String, Object>) loginRes.jsonBody.get("data") : null;
        String token = regData != null ? (String) regData.get("token") : null;
        Map<String, Object> regUser = regData != null ? (Map<String, Object>) regData.get("user") : null;
        int userId = regUser != null ? ((Number) regUser.get("id")).intValue() : 0;

        PharmacyRepository pharmRepo = new PharmacyRepository();
        Pharmacy pProfile = pharmRepo.findByUserId(userId);
        int pharmacyId = pProfile != null ? pProfile.getId() : 0;
        System.out.println("Registered & Authenticated Pharmacy ID: " + pharmacyId + " (User: " + userId + ")");

        // 2. Inspect DB before import
        int medCountBefore = 0;
        int invCountBefore = 0;
        try (Connection conn = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM medicines");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) medCountBefore = rs.getInt(1);
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM inventory WHERE pharmacy_id = ?")) {
                ps.setInt(1, pharmacyId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) invCountBefore = rs.getInt(1);
                }
            }
        }
        System.out.println("\n[BEFORE IMPORT]");
        System.out.println("  - Medicines in catalog: " + medCountBefore);
        System.out.println("  - Inventory records for Pharmacy " + pharmacyId + ": " + invCountBefore);

        // 3. Post CSV import payload
        System.out.println("\n[EXECUTING CSV IMPORT]");
        System.out.println("  - Sending 12,000 rows CSV to /api/inventory/import-csv...");
        long startMs = System.currentTimeMillis();
        Map<String, Object> importPayload = Collections.singletonMap("csv", csvContent);
        HttpResponse importRes = sendHttp("POST", "/api/inventory/import-csv", JsonUtil.toJson(importPayload), token);
        long durMs = System.currentTimeMillis() - startMs;

        System.out.println("  - HTTP Status: " + importRes.statusCode + " (took " + durMs + " ms)");
        if (importRes.statusCode != 200) {
            System.err.println("  - Import failed: " + importRes.body);
            System.exit(1);
        }

        Map<String, Object> data = (Map<String, Object>) importRes.jsonBody.get("data");
        System.out.println("  - Import Response Summary: " + data);

        int totalProcessed = ((Number) data.getOrDefault("totalProcessed", 0)).intValue();
        int imported = ((Number) data.getOrDefault("imported", 0)).intValue();
        int uniqueMedicines = ((Number) data.getOrDefault("uniqueMedicines", 0)).intValue();
        int medicinesCreated = ((Number) data.getOrDefault("medicinesCreated", 0)).intValue();
        int medicinesMatched = ((Number) data.getOrDefault("medicinesMatched", 0)).intValue();
        int inventoryAdded = ((Number) data.getOrDefault("inventoryAdded", 0)).intValue();
        int inventoryUpdated = ((Number) data.getOrDefault("inventoryUpdated", 0)).intValue();
        int failed = ((Number) data.getOrDefault("failed", 0)).intValue();

        System.out.println("  - Detailed Breakdown:");
        System.out.println("    * Imported rows: " + imported);
        System.out.println("    * Unique medicines: " + uniqueMedicines);
        System.out.println("    * Catalog medicines created: " + medicinesCreated);
        System.out.println("    * Catalog medicines matched: " + medicinesMatched);
        System.out.println("    * Inventory records added: " + inventoryAdded);
        System.out.println("    * Inventory records updated: " + inventoryUpdated);

        // 4. Inspect DB after import
        int medCountAfter = 0;
        int invCountAfter = 0;
        int duplicateInventoryCount = 0;

        try (Connection conn = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM medicines");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) medCountAfter = rs.getInt(1);
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM inventory WHERE pharmacy_id = ?")) {
                ps.setInt(1, pharmacyId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) invCountAfter = rs.getInt(1);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT medicine_id, COUNT(*) as cnt FROM inventory WHERE pharmacy_id = ? GROUP BY medicine_id HAVING COUNT(*) > 1")) {
                ps.setInt(1, pharmacyId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        duplicateInventoryCount++;
                    }
                }
            }
        }

        System.out.println("\n[AFTER IMPORT — DATABASE VERIFICATION]");
        System.out.println("  - Medicines in catalog after: " + medCountAfter + " (Delta: +" + (medCountAfter - medCountBefore) + ")");
        System.out.println("  - Inventory records for Pharmacy " + pharmacyId + " after: " + invCountAfter + " (Delta: +" + (invCountAfter - invCountBefore) + ")");
        System.out.println("  - Duplicate inventory records for pharmacy: " + duplicateInventoryCount);

        // 5. Test Patient Medicine Search to verify stock availability
        System.out.println("\n[PATIENT SEARCH VERIFICATION]");
        HttpResponse patientSearch = sendHttp("GET", "/api/medicines?q=unknown", null, null);
        List<?> searchList = (List<?>) patientSearch.jsonBody.get("data");
        System.out.println("  - Patient search for 'unknown' returned: " + (searchList != null ? searchList.size() : 0) + " medicines");
        if (searchList != null && !searchList.isEmpty()) {
            Map<String, Object> sampleMed = (Map<String, Object>) searchList.get(0);
            System.out.println("  - Sample patient search result: " + sampleMed.get("name") + " | totalStock=" + sampleMed.get("totalStock") + " | pharmacyCount=" + sampleMed.get("pharmacyCount"));
        }

        // 6. Test Pharmacy Inventory List endpoint
        System.out.println("\n[PHARMACY INVENTORY LIST VERIFICATION]");
        HttpResponse invListRes = sendHttp("GET", "/api/inventory", null, token);
        List<?> items = (List<?>) invListRes.jsonBody.get("data");
        System.out.println("  - Pharmacy Inventory items returned: " + (items != null ? items.size() : 0));

        // 7. Verify All Assertions
        boolean ok = true;
        if (totalProcessed != 12000) {
            System.err.println("ASSERTION FAILED: totalProcessed was " + totalProcessed + ", expected 12000");
            ok = false;
        }
        if (imported != 12000) {
            System.err.println("ASSERTION FAILED: imported was " + imported + ", expected 12000");
            ok = false;
        }
        if (failed != 0) {
            System.err.println("ASSERTION FAILED: failed rows was " + failed + ", expected 0");
            ok = false;
        }
        if (uniqueMedicines != 34) {
            System.err.println("ASSERTION FAILED: uniqueMedicines was " + uniqueMedicines + ", expected 34");
            ok = false;
        }
        if (medicinesCreated + medicinesMatched != 34) {
            System.err.println("ASSERTION FAILED: medicinesCreated + medicinesMatched was " + (medicinesCreated + medicinesMatched) + ", expected 34");
            ok = false;
        }
        if (inventoryAdded != 34) {
            System.err.println("ASSERTION FAILED: inventoryAdded was " + inventoryAdded + ", expected 34");
            ok = false;
        }
        if (inventoryUpdated != 0) {
            System.err.println("ASSERTION FAILED: inventoryUpdated was " + inventoryUpdated + ", expected 0");
            ok = false;
        }
        if (invCountAfter != 34) {
            System.err.println("ASSERTION FAILED: MySQL inventory count for pharmacy was " + invCountAfter + ", expected 34");
            ok = false;
        }
        if (duplicateInventoryCount != 0) {
            System.err.println("ASSERTION FAILED: Found duplicate inventory items for pharmacy!");
            ok = false;
        }

        if (ok) {
            System.out.println("\n=================================================================");
            System.out.println("   >>> ALL ROWS PROCESSED AND VERIFIED SUCCESSFULLY! <<<  ");
            System.out.println("=================================================================");
            System.exit(0);
        } else {
            System.err.println("\nVERIFICATION FAILED!");
            System.exit(1);
        }
    }
}
