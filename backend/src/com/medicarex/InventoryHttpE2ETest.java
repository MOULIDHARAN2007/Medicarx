package com.medicarex;

import com.medicarex.config.DatabaseConnection;
import com.medicarex.model.InventoryItem;
import com.medicarex.model.Pharmacy;
import com.medicarex.model.User;
import com.medicarex.repository.InventoryRepository;
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
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InventoryHttpE2ETest {
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
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
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
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("   MEDICAREX — CSV INVENTORY E2E VERIFICATION     ");
        System.out.println("==================================================");

        DatabaseConnection.initialize();
        int passed = 0;
        int totalTests = 10;
        long timestamp = System.currentTimeMillis();

        try {
            // 1. Provision Test Pharmacy User and Authenticate
            System.out.println("\n[E2E 1] Provision and Authenticate Pharmacy Account via REST API");
            String pharmEmail = "pharmacy_e2e_" + timestamp + "@medicarex.test";
            String pharmPass = "SecurePharmPass123!";
            
            Map<String, Object> regReq = new HashMap<>();
            regReq.put("email", pharmEmail);
            regReq.put("password", pharmPass);
            regReq.put("fullName", "E2E Care Pharmacy");
            regReq.put("phone", "+91 98888 11111");
            regReq.put("role", "PHARMACY");
            regReq.put("pharmacyName", "E2E Care Pharmacy Store");
            regReq.put("address", "77 Brigade Road, Indiranagar");
            regReq.put("city", "Bengaluru");
            regReq.put("state", "Karnataka");
            regReq.put("postalCode", "560038");

            sendHttp("POST", "/api/auth/register", JsonUtil.toJson(regReq), null);
            UserRepository userRepo = new UserRepository();
            User u = userRepo.findByEmail(pharmEmail);
            if (u != null) {
                userRepo.updateStatus(u.getId(), true);
            }
            Map<String, Object> pharmLoginReq = new HashMap<>();
            pharmLoginReq.put("email", pharmEmail);
            pharmLoginReq.put("password", pharmPass);
            HttpResponse loginRes = sendHttp("POST", "/api/auth/login", JsonUtil.toJson(pharmLoginReq), null);
            Map<String, Object> regData = loginRes.jsonBody != null ? (Map<String, Object>) loginRes.jsonBody.get("data") : null;
            String pharmToken = regData != null ? (String) regData.get("token") : null;
            Map<String, Object> regUser = regData != null ? (Map<String, Object>) regData.get("user") : null;
            int pharmUserId = regUser != null ? ((Number) regUser.get("id")).intValue() : 0;

            PharmacyRepository pharmRepo = new PharmacyRepository();
            Pharmacy pProfile = pharmRepo.findByUserId(pharmUserId);
            int pharmId = pProfile != null ? pProfile.getId() : 0;

            if (loginRes.statusCode == 200 && pharmToken != null && pharmId > 0) {
                System.out.println("  -> PASSED: Pharmacy registered and authenticated successfully (Pharmacy ID: " + pharmId + ", User ID: " + pharmUserId + ")");
                passed++;
            } else {
                System.err.println("  -> FAILED: Registration / Login failed: " + loginRes.body);
            }

            // 2. Initial Inventory must be empty for new pharmacy
            System.out.println("\n[E2E 2] Check initial empty inventory for new pharmacy");
            HttpResponse initialInvRes = sendHttp("GET", "/api/inventory", null, pharmToken);
            List<?> initialList = (List<?>) initialInvRes.jsonBody.get("data");
            if (initialInvRes.statusCode == 200 && initialList != null && initialList.isEmpty()) {
                System.out.println("  -> PASSED: Initial inventory is empty (0 items)");
                passed++;
            } else {
                System.err.println("  -> FAILED: Expected empty inventory: " + initialInvRes.body);
            }

            // 3. Upload actual 12,000-row CSV file
            System.out.println("\n[E2E 3] Upload medicarex_inventory_12000.csv (12,000 rows) via REST API");
            File csvFile = new File("medicarex_inventory_12000.csv");
            if (!csvFile.exists()) {
                throw new RuntimeException("medicarex_inventory_12000.csv not found in workspace!");
            }
            String csvContent = new String(Files.readAllBytes(Paths.get("medicarex_inventory_12000.csv")), StandardCharsets.UTF_8);
            System.out.println("  -> Loaded CSV file (" + csvContent.length() + " chars)");

            Map<String, Object> uploadPayload = new HashMap<>();
            uploadPayload.put("csv", csvContent);

            long startUpload = System.currentTimeMillis();
            HttpResponse uploadRes = sendHttp("POST", "/api/inventory/import-csv", JsonUtil.toJson(uploadPayload), pharmToken);
            long endUpload = System.currentTimeMillis();

            Map<String, Object> summaryData = (Map<String, Object>) uploadRes.jsonBody.get("data");
            Number totalRowsNum = (Number) summaryData.get("totalProcessed");
            Number importedNum = (Number) summaryData.get("imported");
            Number failedNum = (Number) summaryData.get("failed");
            List<?> errorsList = (List<?>) summaryData.get("errors");

            if (uploadRes.statusCode == 200 && totalRowsNum.intValue() == 12000 && importedNum.intValue() == 12000 && failedNum.intValue() == 0) {
                System.out.println("  -> PASSED: ALL 12,000 rows processed in " + (endUpload - startUpload) + "ms!");
                System.out.println("     Total: " + totalRowsNum + " | Imported: " + importedNum + " | Failed: " + failedNum + " | Errors captured: " + errorsList.size());
                passed++;
            } else {
                System.err.println("  -> FAILED: Import response mismatch: " + uploadRes.body);
            }

            // 4. Verify Database Persistence in MySQL / Embedded DB
            System.out.println("\n[E2E 4] Verify Database Persistence for Pharmacy " + pharmId);
            InventoryRepository invRepo = new InventoryRepository();
            List<InventoryItem> savedItems = invRepo.findByPharmacyId(pharmId, null, null, null);
            if (savedItems.size() == 34) {
                System.out.println("  -> PASSED: All 34 distinct medicines stored in database for pharmacy " + pharmId);
                for (InventoryItem it : savedItems) {
                    System.out.println("     - " + it.getMedicineName() + ": Stock = " + it.getStockQuantity() + ", Price = ₹" + it.getUnitPrice() + ", Status = " + it.getStatus());
                }
                passed++;
            } else {
                System.err.println("  -> FAILED: Expected 34 items in DB, found: " + savedItems.size());
            }

            // 5. Verify Duplicate Protection: Only 1 row per (pharmacy_id, medicine_id)
            System.out.println("\n[E2E 5] Verify Duplicate Protection (Zero duplicate entries in DB)");
            boolean duplicatesFound = false;
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT medicine_id, COUNT(*) as cnt FROM inventory WHERE pharmacy_id = ? GROUP BY medicine_id HAVING cnt > 1")) {
                ps.setInt(1, pharmId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        duplicatesFound = true;
                        System.err.println("  -> Duplicate found for medicine ID: " + rs.getInt("medicine_id") + " (Count: " + rs.getInt("cnt") + ")");
                    }
                }
            }
            if (!duplicatesFound) {
                System.out.println("  -> PASSED: Zero duplicates. Unique constraint (pharmacy_id, medicine_id) respected perfectly.");
                passed++;
            } else {
                System.err.println("  -> FAILED: Duplicate inventory records found in database.");
            }

            // 6. Verify Patient Medicine Availability
            System.out.println("\n[E2E 6] Patient Medicine Availability check for imported stock");
            HttpResponse searchRes = sendHttp("GET", "/api/medicines?q=Paracetamol", null, null);
            List<?> searchData = (List<?>) searchRes.jsonBody.get("data");
            if (searchRes.statusCode == 200 && searchData != null && !searchData.isEmpty()) {
                Map<String, Object> firstMed = (Map<String, Object>) searchData.get(0);
                Number totalStock = (Number) firstMed.get("totalStock");
                System.out.println("  -> PASSED: Patient search returns Paracetamol with updated total stock: " + totalStock);
                passed++;
            } else {
                System.err.println("  -> FAILED: Patient medicine search failed: " + searchRes.body);
            }

            // 7. Verify Pharmacy Medicine Availability endpoint
            System.out.println("\n[E2E 7] Pharmacy lookup by medicine ID (GET /api/pharmacies/medicine/1)");
            HttpResponse pharmMedRes = sendHttp("GET", "/api/pharmacies/medicine/1", null, null);
            List<?> pharmList = (List<?>) pharmMedRes.jsonBody.get("data");
            boolean foundOurPharm = false;
            if (pharmMedRes.statusCode == 200 && pharmList != null) {
                for (Object item : pharmList) {
                    Map<String, Object> pMap = (Map<String, Object>) item;
                    if (pMap.get("id") != null && ((Number)pMap.get("id")).intValue() == pharmId) {
                        foundOurPharm = true;
                        break;
                    }
                }
            }
            if (foundOurPharm) {
                System.out.println("  -> PASSED: Patient sees our pharmacy as an active stock provider for Medicine ID 1");
                passed++;
            } else {
                System.err.println("  -> FAILED: Pharmacy " + pharmId + " not found in medicine stock providers: " + pharmMedRes.body);
            }

            // 8. Verify Manual Add Stock works independently (WORKFLOW A)
            System.out.println("\n[E2E 8] Verify Manual Add Stock [+ Add Medicine Stock] (Workflow A)");
            InventoryItem beforeIt = invRepo.findByPharmacyAndMedicine(pharmId, 1);
            int prevStock = beforeIt != null ? beforeIt.getStockQuantity() : 0;

            Map<String, Object> manualAddReq = new HashMap<>();
            manualAddReq.put("medicineId", 1); // Paracetamol
            manualAddReq.put("stockQuantity", 500);
            manualAddReq.put("unitPrice", 4.50);
            manualAddReq.put("batchNumber", "MANUAL-PCM-999");
            manualAddReq.put("expiryDate", "2029-12-31");

            HttpResponse manualAddRes = sendHttp("POST", "/api/inventory/add", JsonUtil.toJson(manualAddReq), pharmToken);
            if (manualAddRes.statusCode == 200) {
                InventoryItem it = invRepo.findByPharmacyAndMedicine(pharmId, 1);
                if (it != null && it.getStockQuantity() == (prevStock + 500) && it.getUnitPrice() == 4.50) {
                    System.out.println("  -> PASSED: Manual stock update saved directly to DB: Previous = " + prevStock + ", Added = 500, New Stock = " + it.getStockQuantity() + ", Price = " + it.getUnitPrice());
                    passed++;
                } else {
                    System.err.println("  -> FAILED: DB value mismatch. Expected " + (prevStock + 500) + ", got: " + (it != null ? it.getStockQuantity() : "null"));
                }
            } else {
                System.err.println("  -> FAILED: Manual Add Stock request failed: " + manualAddRes.body);
            }

            // 9. Verify Pharmacy Security & Isolation (Pharmacy A cannot modify Pharmacy B)
            System.out.println("\n[E2E 9] Verify Pharmacy Ownership Security Enforcement");
            String unauthorizedPharmEmail = "other_pharm_" + timestamp + "@medicarex.test";
            Map<String, Object> otherRegReq = new HashMap<>();
            otherRegReq.put("email", unauthorizedPharmEmail);
            otherRegReq.put("password", "SecurePass123!");
            otherRegReq.put("fullName", "Other Pharmacy");
            otherRegReq.put("phone", "+91 99999 22222");
            otherRegReq.put("role", "PHARMACY");
            otherRegReq.put("pharmacyName", "Other Pharmacy Store");
            sendHttp("POST", "/api/auth/register", JsonUtil.toJson(otherRegReq), null);
            User otherUser = userRepo.findByEmail(unauthorizedPharmEmail);
            if (otherUser != null) {
                userRepo.updateStatus(otherUser.getId(), true);
            }
            Map<String, Object> otherLoginReq = new HashMap<>();
            otherLoginReq.put("email", unauthorizedPharmEmail);
            otherLoginReq.put("password", "SecurePass123!");
            HttpResponse otherLoginRes = sendHttp("POST", "/api/auth/login", JsonUtil.toJson(otherLoginReq), null);
            Map<String, Object> otherRegData = otherLoginRes.jsonBody != null ? (Map<String, Object>) otherLoginRes.jsonBody.get("data") : null;
            String otherToken = otherRegData != null ? (String) otherRegData.get("token") : null;

            // Attempt to delete / update Pharmacy A's item using Pharmacy B's token
            InventoryItem pharmAItem = invRepo.findByPharmacyAndMedicine(pharmId, 1);
            HttpResponse attackRes = sendHttp("DELETE", "/api/inventory/" + pharmAItem.getId(), null, otherToken);

            if (attackRes.statusCode == 403) {
                System.out.println("  -> PASSED: Access denied (HTTP 403) when Pharmacy B tries to modify Pharmacy A's inventory");
                passed++;
            } else {
                System.err.println("  -> FAILED: Expected 403 Forbidden, got: " + attackRes.statusCode + " -> " + attackRes.body);
            }

            // 10. Verify Persistence across Session / Re-Login
            System.out.println("\n[E2E 10] Verify Session Re-Login and Data Persistence");
            Map<String, Object> loginReq = new HashMap<>();
            loginReq.put("email", pharmEmail);
            loginReq.put("password", pharmPass);
            HttpResponse reLoginRes = sendHttp("POST", "/api/auth/login", JsonUtil.toJson(loginReq), null);
            String reToken = (String) ((Map<String, Object>) reLoginRes.jsonBody.get("data")).get("token");
            HttpResponse reInvRes = sendHttp("GET", "/api/inventory", null, reToken);
            List<?> reList = (List<?>) reInvRes.jsonBody.get("data");

            if (reInvRes.statusCode == 200 && reList != null && reList.size() >= 34) {
                System.out.println("  -> PASSED: Inventory items fully persisted across session re-authentication (" + reList.size() + " items)");
                passed++;
            } else {
                System.err.println("  -> FAILED: Inventory missing after re-login: " + reInvRes.body);
            }

        } catch (Exception e) {
            System.err.println("Unexpected exception in E2E test: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n==================================================");
        System.out.println("   RESULTS: " + passed + " / " + totalTests + " TESTS PASSED");
        System.out.println("==================================================");

        if (passed == totalTests) {
            System.out.println(">>> ALL 10 E2E INVENTORY & CSV IMPORT TESTS PASSED PERFECTLY! <<<");
        } else {
            System.exit(1);
        }
    }
}
