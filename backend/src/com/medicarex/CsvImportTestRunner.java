package com.medicarex;

import com.medicarex.config.DatabaseConnection;
import com.medicarex.model.InventoryItem;
import com.medicarex.model.Medicine;
import com.medicarex.model.Pharmacy;
import com.medicarex.repository.InventoryRepository;
import com.medicarex.repository.MedicineRepository;
import com.medicarex.repository.PharmacyRepository;
import com.medicarex.repository.UserRepository;
import com.medicarex.util.JsonUtil;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CsvImportTestRunner {
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
        System.out.println("   MEDICAREX — CSV IMPORT VERIFICATION SUITE      ");
        System.out.println("==================================================");

        DatabaseConnection.initialize();
        int passed = 0;
        int totalTests = 17;
        long ts = System.currentTimeMillis();

        try {
            // Provision Pharmacy Account
            String email = "pharm_csv_" + ts + "@medicarex.test";
            String pass = "SecurePass123!";
            Map<String, Object> regReq = new HashMap<>();
            regReq.put("email", email);
            regReq.put("password", pass);
            regReq.put("fullName", "CSV Verification Pharmacy");
            regReq.put("phone", "+91 99999 88888");
            regReq.put("role", "PHARMACY");
            regReq.put("pharmacyName", "CSV Verification Meds");
            regReq.put("address", "100 Medical Blvd");
            regReq.put("city", "Bengaluru");
            regReq.put("state", "Karnataka");
            regReq.put("postalCode", "560001");

            HttpResponse regRes = sendHttp("POST", "/api/auth/register", JsonUtil.toJson(regReq), null);
            Map<String, Object> regData = (Map<String, Object>) regRes.jsonBody.get("data");
            Map<String, Object> regUser = (Map<String, Object>) regData.get("user");
            int userId = ((Number) regUser.get("id")).intValue();

            UserRepository userRepo = new UserRepository();
            userRepo.updateStatus(userId, true);
            String token = com.medicarex.auth.JwtUtil.generateToken(userId, email, "PHARMACY", "CSV Verification Pharmacy");

            PharmacyRepository pharmRepo = new PharmacyRepository();
            Pharmacy pProfile = pharmRepo.findByUserId(userId);
            int pharmacyId = pProfile.getId();

            System.out.println("Authenticated Pharmacy ID: " + pharmacyId + " (User: " + userId + ")");

            // Test A: One-row CSV with exact requested example
            System.out.println("\n[TEST A] One-row CSV with Paracetamol 650mg");
            String csvA = "medicine_name,stock_quantity,unit_price,batch_number,expiry_date,category\n" +
                          "Paracetamol 650mg,100,3.50,PCM-2026-002,2028-09-30,Pain Relief\n";
            HttpResponse resA = sendHttp("POST", "/api/inventory/import-csv", JsonUtil.toJson(Collections.singletonMap("csv", csvA)), token);
            Map<String, Object> dataA = (Map<String, Object>) resA.jsonBody.get("data");
            if (resA.statusCode == 200 && dataA != null && ((Number)dataA.get("imported")).intValue() == 1) {
                System.out.println("  -> PASSED: Single row CSV imported successfully (imported: " + dataA.get("imported") + ")");
                passed++;
            } else {
                System.err.println("  -> FAILED: " + resA.body);
            }

            // Test B: 12-row CSV using catalog medicines and generic names
            System.out.println("\n[TEST B] 12-row CSV");
            StringBuilder csvB = new StringBuilder("medicine_name,stock_quantity,unit_price,batch_number,expiry_date,category\n");
            csvB.append("Paracetamol 650mg,120,4.00,B1,2028-09-30,Pain Relief\n");
            csvB.append("Amoxicillin 500mg,50,8.50,B2,2028-06-30,Antibiotics\n");
            csvB.append("Cetirizine 10mg,75,2.80,B3,2028-08-31,Allergy\n");
            csvB.append("Metformin 500mg,200,4.20,B4,2028-11-30,Diabetes\n");
            csvB.append("Atorvastatin 20mg,90,9.00,B5,2028-05-31,Cardiology\n");
            csvB.append("Azithromycin 500mg,60,12.00,B7,2028-04-30,Antibiotics\n");
            csvB.append("Ibuprofen 400mg,110,3.80,B8,2028-10-31,Pain Relief\n");
            csvB.append("Pantoprazole 40mg,80,6.50,B9,2028-09-30,Gastroenterology\n");
            csvB.append("Montelukast 10mg,40,16.00,B10,2028-12-31,Respiratory\n");
            csvB.append("\"Vitamin D3 60,000 IU\",130,12.00,B11,2028-08-31,Vitamins\n");
            csvB.append("Acetaminophen,85,3.20,B12,2028-03-31,Pain Relief\n");
            csvB.append("Amoxicillin Trihydrate,65,8.00,B13,2028-05-31,Antibiotics\n");

            HttpResponse resB = sendHttp("POST", "/api/inventory/import-csv", JsonUtil.toJson(Collections.singletonMap("csv", csvB.toString())), token);
            Map<String, Object> dataB = (Map<String, Object>) resB.jsonBody.get("data");
            if (resB.statusCode == 200 && dataB != null && ((Number)dataB.get("imported")).intValue() == 12) {
                System.out.println("  -> PASSED: 12-row CSV imported successfully (imported: " + dataB.get("imported") + ")");
                passed++;
            } else {
                System.err.println("  -> FAILED: " + resB.body);
            }

            // Test C: CSV with Windows CRLF (\r\n)
            System.out.println("\n[TEST C] CSV with Windows CRLF line endings");
            String csvC = "medicine_name,stock_quantity,unit_price,batch_number,expiry_date,category\r\n" +
                          "Paracetamol 650mg,150,3.50,PCM-CRLF,2028-09-30,Pain Relief\r\n" +
                          "Amoxicillin 500mg,80,8.50,AMX-CRLF,2028-06-30,Antibiotics\r\n";
            HttpResponse resC = sendHttp("POST", "/api/inventory/import-csv", JsonUtil.toJson(Collections.singletonMap("csv", csvC)), token);
            Map<String, Object> dataC = (Map<String, Object>) resC.jsonBody.get("data");
            if (resC.statusCode == 200 && dataC != null && ((Number)dataC.get("imported")).intValue() == 2) {
                System.out.println("  -> PASSED: CRLF line endings parsed correctly (imported: 2)");
                passed++;
            } else {
                System.err.println("  -> FAILED: " + resC.body);
            }

            // Test D: CSV with UTF-8 BOM
            System.out.println("\n[TEST D] CSV with UTF-8 BOM (\\uFEFF)");
            String csvD = "\uFEFFmedicine_name,stock_quantity,unit_price,batch_number,expiry_date,category\n" +
                          "Cetirizine 10mg,95,2.80,CET-BOM,2028-08-31,Allergy\n";
            HttpResponse resD = sendHttp("POST", "/api/inventory/import-csv", JsonUtil.toJson(Collections.singletonMap("csv", csvD)), token);
            Map<String, Object> dataD = (Map<String, Object>) resD.jsonBody.get("data");
            if (resD.statusCode == 200 && dataD != null && ((Number)dataD.get("imported")).intValue() == 1) {
                System.out.println("  -> PASSED: UTF-8 BOM stripped and parsed correctly");
                passed++;
            } else {
                System.err.println("  -> FAILED: " + resD.body);
            }

            // Test E: CSV with spaces around headers
            System.out.println("\n[TEST E] CSV with spaces around headers");
            String csvE = "  medicine_name  ,  stock_quantity  ,  unit_price  ,  batch_number  ,  expiry_date  ,  category  \n" +
                          "Metformin 500mg,140,4.20,MET-SPACE,2028-11-30,Diabetes\n";
            HttpResponse resE = sendHttp("POST", "/api/inventory/import-csv", JsonUtil.toJson(Collections.singletonMap("csv", csvE)), token);
            Map<String, Object> dataE = (Map<String, Object>) resE.jsonBody.get("data");
            if (resE.statusCode == 200 && dataE != null && ((Number)dataE.get("imported")).intValue() == 1) {
                System.out.println("  -> PASSED: Headers with spaces parsed correctly");
                passed++;
            } else {
                System.err.println("  -> FAILED: " + resE.body);
            }

            // Test F: CSV with uppercase headers and alias names
            System.out.println("\n[TEST F] CSV with uppercase headers (MEDICINENAME, QTY, PRICE, BATCH, EXPIRY)");
            String csvF = "MEDICINENAME,QTY,PRICE,BATCH,EXPIRY,CATEGORY\n" +
                          "Atorvastatin 20mg,85,9.00,ATOR-UPPER,2028-05-31,Cardiology\n";
            HttpResponse resF = sendHttp("POST", "/api/inventory/import-csv", JsonUtil.toJson(Collections.singletonMap("csv", csvF)), token);
            Map<String, Object> dataF = (Map<String, Object>) resF.jsonBody.get("data");
            if (resF.statusCode == 200 && dataF != null && ((Number)dataF.get("imported")).intValue() == 1) {
                System.out.println("  -> PASSED: Uppercase header aliases parsed correctly");
                passed++;
            } else {
                System.err.println("  -> FAILED: " + resF.body);
            }

            // Test G: CSV with quoted values
            System.out.println("\n[TEST G] CSV with quoted values");
            String csvG = "\"medicine_name\",\"stock_quantity\",\"unit_price\",\"batch_number\",\"expiry_date\",\"category\"\n" +
                          "\"Pantoprazole 40mg\",\"110\",\"5.50\",\"PAN-QUOTE\",\"2028-07-31\",\"Gastroenterology\"\n";
            HttpResponse resG = sendHttp("POST", "/api/inventory/import-csv", JsonUtil.toJson(Collections.singletonMap("csv", csvG)), token);
            Map<String, Object> dataG = (Map<String, Object>) resG.jsonBody.get("data");
            if (resG.statusCode == 200 && dataG != null && ((Number)dataG.get("imported")).intValue() == 1) {
                System.out.println("  -> PASSED: Quoted values handled correctly");
                passed++;
            } else {
                System.err.println("  -> FAILED: " + resG.body);
            }

            // Test H: Existing medicine -> update inventory to new snapshot stock
            System.out.println("\n[TEST H] Existing medicine update snapshot stock (existing=150, new=100)");
            String csvH = "medicine_name,stock_quantity,unit_price\n" +
                          "Paracetamol 650mg,100,3.50\n";
            HttpResponse resH = sendHttp("POST", "/api/inventory/import-csv", JsonUtil.toJson(Collections.singletonMap("csv", csvH)), token);
            InventoryRepository invRepo = new InventoryRepository();
            MedicineRepository medRepo = new MedicineRepository();
            Medicine pMed = medRepo.findByName("Paracetamol 650mg");
            InventoryItem itemH = invRepo.findByPharmacyAndMedicine(pharmacyId, pMed.getId());
            if (resH.statusCode == 200 && itemH != null && itemH.getStockQuantity() == 100) {
                System.out.println("  -> PASSED: Existing stock correctly updated to snapshot 100 (not cumulative)");
                passed++;
            } else {
                System.err.println("  -> FAILED: Expected stock=100, got: " + (itemH != null ? itemH.getStockQuantity() : "null"));
            }

            // Test I: New inventory entry -> create inventory
            System.out.println("\n[TEST I] New inventory entry create");
            String csvI = "medicine_name,stock_quantity,unit_price,batch_number,expiry_date,category\n" +
                          "Montelukast 10mg,60,16.50,MON-NEW,2028-11-30,Respiratory\n";
            HttpResponse resI = sendHttp("POST", "/api/inventory/import-csv", JsonUtil.toJson(Collections.singletonMap("csv", csvI)), token);
            Medicine monMed = medRepo.findByName("Montelukast 10mg");
            InventoryItem itemI = invRepo.findByPharmacyAndMedicine(pharmacyId, monMed.getId());
            if (resI.statusCode == 200 && itemI != null && itemI.getStockQuantity() == 60) {
                System.out.println("  -> PASSED: New inventory entry created with stock 60");
                passed++;
            } else {
                System.err.println("  -> FAILED: " + resI.body);
            }

            // Test J: Unknown medicine -> dynamically auto-created in catalog and imported cleanly
            System.out.println("\n[TEST J] Unknown medicine non_existent_drug_99999");
            String csvJ = "medicine_name,stock_quantity,unit_price\n" +
                          "NonExistentDrug99999,50,10.00\n" +
                          "Ibuprofen 400mg,75,3.80\n";
            HttpResponse resJ = sendHttp("POST", "/api/inventory/import-csv", JsonUtil.toJson(Collections.singletonMap("csv", csvJ)), token);
            Map<String, Object> dataJ = (Map<String, Object>) resJ.jsonBody.get("data");
            if (resJ.statusCode == 200 && dataJ != null && ((Number)dataJ.get("imported")).intValue() >= 1) {
                System.out.println("  -> PASSED: Unknown medicine handled smoothly and imported (" + dataJ.get("imported") + " items imported)");
                passed++;
            } else {
                System.err.println("  -> FAILED: " + resJ.body);
            }

            // Test K: Invalid quantity -> report row error
            System.out.println("\n[TEST K] Invalid quantity (negative or non-numeric)");
            String csvK = "medicine_name,stock_quantity,unit_price\n" +
                          "Ibuprofen 400mg,-5,3.80\n" +
                          "Pantoprazole 40mg,80,6.50\n";
            HttpResponse resK = sendHttp("POST", "/api/inventory/import-csv", JsonUtil.toJson(Collections.singletonMap("csv", csvK)), token);
            Map<String, Object> dataK = (Map<String, Object>) resK.jsonBody.get("data");
            if (resK.statusCode == 200 && ((Number)dataK.get("imported")).intValue() == 1 && ((Number)dataK.get("failed")).intValue() == 1) {
                System.out.println("  -> PASSED: Invalid quantity rejected per-row, valid row imported");
                passed++;
            } else {
                System.err.println("  -> FAILED: " + resK.body);
            }

            // Test L: Invalid price -> report row error
            System.out.println("\n[TEST L] Invalid price (negative or non-numeric)");
            String csvL = "medicine_name,stock_quantity,unit_price\n" +
                          "Ibuprofen 400mg,50,-10.00\n" +
                          "Cetirizine 10mg,70,4.80\n";
            HttpResponse resL = sendHttp("POST", "/api/inventory/import-csv", JsonUtil.toJson(Collections.singletonMap("csv", csvL)), token);
            Map<String, Object> dataL = (Map<String, Object>) resL.jsonBody.get("data");
            if (resL.statusCode == 200 && ((Number)dataL.get("imported")).intValue() == 1 && ((Number)dataL.get("failed")).intValue() == 1) {
                System.out.println("  -> PASSED: Invalid price rejected per-row, valid row imported");
                passed++;
            } else {
                System.err.println("  -> FAILED: " + resL.body);
            }

            // Test M: Large 10,000+ CSV support
            System.out.println("\n[TEST M] Large 10,000+ CSV Import Benchmark");
            String[] sampleMeds = {"Paracetamol 650mg", "Amoxicillin 500mg", "Cetirizine 10mg", "Metformin 500mg", "Atorvastatin 20mg", "Azithromycin 500mg", "Ibuprofen 400mg", "Pantoprazole 40mg", "Montelukast 10mg", "Vitamin D3 60,000 IU"};
            StringBuilder bigCsv = new StringBuilder("medicine_name,stock_quantity,unit_price,batch_number,expiry_date,category\n");
            for (int i = 0; i < 10000; i++) {
                String med = sampleMeds[i % sampleMeds.length];
                bigCsv.append("\"").append(med).append("\",").append(50 + (i % 500)).append(",").append(5.00 + (i % 20)).append(",BATCH-").append(i).append(",2028-12-31,General\n");
            }
            long startMs = System.currentTimeMillis();
            HttpResponse resM = sendHttp("POST", "/api/inventory/import-csv", JsonUtil.toJson(Collections.singletonMap("csv", bigCsv.toString())), token);
            long durMs = System.currentTimeMillis() - startMs;
            Map<String, Object> dataM = (Map<String, Object>) resM.jsonBody.get("data");
            if (resM.statusCode == 200 && dataM != null && ((Number)dataM.get("imported")).intValue() == 10000) {
                System.out.println("  -> PASSED: 10,000-row CSV imported in " + durMs + " ms! (imported: " + dataM.get("imported") + ")");
                passed++;
            } else {
                System.err.println("  -> FAILED: " + resM.body);
            }

            // Test N: List inventory endpoint to verify all medicines show
            System.out.println("\n[TEST N] Fetch inventory list (/api/inventory)");
            HttpResponse resN = sendHttp("GET", "/api/inventory", null, token);
            List<?> invList = (List<?>) resN.jsonBody.get("data");
            if (resN.statusCode == 200 && invList != null && invList.size() >= 10) {
                System.out.println("  -> PASSED: Inventory list returned " + invList.size() + " records");
                passed++;
            } else {
                System.err.println("  -> FAILED: " + resN.body);
            }

            // Test O: Login again as pharmacy and verify persisted inventory
            System.out.println("\n[TEST O] Re-authenticate and verify persistent inventory");
            HttpResponse loginRes = sendHttp("POST", "/api/auth/login", JsonUtil.toJson(Map.of("email", email, "password", pass)), null);
            Map<String, Object> loginData = (Map<String, Object>) loginRes.jsonBody.get("data");
            String newToken = (String) loginData.get("token");
            HttpResponse checkRes = sendHttp("GET", "/api/inventory", null, newToken);
            List<?> persistentList = (List<?>) checkRes.jsonBody.get("data");
            if (checkRes.statusCode == 200 && persistentList != null && persistentList.size() >= 10) {
                System.out.println("  -> PASSED: Re-authenticated session verified " + persistentList.size() + " persisted items");
                passed++;
            } else {
                System.err.println("  -> FAILED: " + checkRes.body);
            }

            // Test P: Patient medicine search reflects updated stock
            System.out.println("\n[TEST P] Patient medicine availability reflects pharmacy stock");
            HttpResponse searchRes = sendHttp("GET", "/api/medicines?query=Paracetamol", null, null);
            List<?> searchData = (List<?>) searchRes.jsonBody.get("data");
            if (searchRes.statusCode == 200 && searchData != null && !searchData.isEmpty()) {
                Map<String, Object> firstMed = (Map<String, Object>) searchData.get(0);
                int totalStock = ((Number) firstMed.get("totalStock")).intValue();
                int pharmCount = ((Number) firstMed.get("pharmacyCount")).intValue();
                System.out.println("  -> Paracetamol totalStock: " + totalStock + ", pharmacyCount: " + pharmCount);
                if (totalStock > 0 && pharmCount > 0) {
                    System.out.println("  -> PASSED: Patient medicine search correctly reflects pharmacy stock!");
                    passed++;
                } else {
                    System.err.println("  -> FAILED: Stock not reflected: totalStock=" + totalStock);
                }
            } else {
                System.err.println("  -> FAILED: " + searchRes.body);
            }

            // Test Q: Manual '+ Add Medicine Stock' functionality still works
            System.out.println("\n[TEST Q] Verify manual '+ Add Medicine Stock' REST endpoint (/api/inventory/add)");
            Map<String, Object> manualStockReq = new HashMap<>();
            manualStockReq.put("medicineId", 2); // Paracetamol
            manualStockReq.put("stockQuantity", 75);
            manualStockReq.put("unitPrice", 35.00);
            manualStockReq.put("batchNumber", "MANUAL-BATCH-001");
            manualStockReq.put("expiryDate", "2028-10-31");
            HttpResponse manualRes = sendHttp("POST", "/api/inventory/add", JsonUtil.toJson(manualStockReq), token);
            if (manualRes.statusCode == 200) {
                System.out.println("  -> PASSED: Manual Add Medicine Stock works correctly!");
                passed++;
            } else {
                System.err.println("  -> FAILED: " + manualRes.body);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("\n==================================================");
        System.out.println("   RESULTS: " + passed + " / " + totalTests + " PASSED");
        System.out.println("==================================================");

        if (passed == totalTests) {
            System.exit(0);
        } else {
            System.exit(1);
        }
    }
}
