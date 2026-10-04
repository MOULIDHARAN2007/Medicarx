package com.medicarex;

import com.medicarex.auth.PasswordHasher;
import com.medicarex.config.DatabaseConnection;
import com.medicarex.util.JsonUtil;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unchecked")
public class ApprovalAndInventoryTest {
    private static final String BASE_URL = "http://localhost:8080";
    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  RUNNING COMPREHENSIVE APPROVAL & INVENTORY TESTS");
        System.out.println("==================================================");

        try {
            // Setup known test user credentials directly in DB
            setupTestCredentials();

            // 1. Admin login
            String adminToken = testAdminLogin();

            // 2. Test Admin Account Creation Restrictions
            testAdminCreateAccountRestrictions(adminToken);

            // 3. Test Doctor Registration & Approval Flow
            testDoctorApprovalFlow(adminToken);

            // 4. Test Pharmacy Registration & Approval Flow
            testPharmacyApprovalFlow(adminToken);

            // 5. Test Pharmacy Inventory Operations
            testPharmacyInventoryOperations();

            // 6. Test Non-Admin Authorization Restrictions
            testSecurityAuthorization(adminToken);

            System.out.println("==================================================");
            System.out.println("TEST RESULTS: " + testsPassed + " PASSED, " + testsFailed + " FAILED");
            System.out.println("==================================================");

            if (testsFailed > 0) {
                System.exit(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void setupTestCredentials() {
        try {
            DatabaseConnection.initialize();
            String salt = PasswordHasher.generateSalt();
            String adminHash = PasswordHasher.hashPassword("Admin@12345", salt);
            String healthHash = PasswordHasher.hashPassword("Health@12345", salt);
            String patientHash = PasswordHasher.hashPassword("Patient@12345", salt);

            try (Connection conn = DatabaseConnection.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement("UPDATE users SET password_hash = ?, salt = ?, is_active = 1 WHERE email = 'admin@example.com'")) {
                    ps.setString(1, adminHash);
                    ps.setString(2, salt);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("UPDATE users SET password_hash = ?, salt = ?, is_active = 1 WHERE email = 'healthplus@example.com'")) {
                    ps.setString(1, healthHash);
                    ps.setString(2, salt);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("UPDATE users SET password_hash = ?, salt = ?, is_active = 1 WHERE email = 'priya@example.com'")) {
                    ps.setString(1, patientHash);
                    ps.setString(2, salt);
                    ps.executeUpdate();
                }
            }
        } catch (Exception e) {
            System.err.println("Setup test credentials error: " + e.getMessage());
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (condition) {
            System.out.println("  [PASS] " + message);
            testsPassed++;
        } else {
            System.err.println("  [FAIL] " + message);
            testsFailed++;
        }
    }

    private static String testAdminLogin() throws Exception {
        System.out.println("\n--- Step 1: Admin Login ---");
        Map<String, Object> req = Map.of("email", "admin@example.com", "password", "Admin@12345");
        ApiResponse res = sendRequest("POST", "/api/auth/login", req, null);
        assertTrue(res.statusCode == 200, "Admin login successful (HTTP 200)");
        Map<String, Object> data = (Map<String, Object>) res.body.get("data");
        String token = (String) data.get("token");
        assertTrue(token != null && !token.isEmpty(), "Admin received valid JWT token");
        return token;
    }

    private static void testAdminCreateAccountRestrictions(String adminToken) throws Exception {
        System.out.println("\n--- Step 2: Admin Create Account Restrictions ---");

        // A. Attempt to create DOCTOR role from Admin Create Account -> must be rejected
        Map<String, Object> docReq = new HashMap<>();
        docReq.put("role", "DOCTOR");
        docReq.put("fullName", "Dr. Illegal Direct");
        docReq.put("email", "illegal.doc" + System.currentTimeMillis() + "@test.com");
        docReq.put("password", "Doctor@123");
        docReq.put("confirmPassword", "Doctor@123");

        ApiResponse docRes = sendRequest("POST", "/api/admin/users", docReq, adminToken);
        assertTrue(docRes.statusCode == 400, "Admin direct creation of DOCTOR rejected with HTTP 400");
        assertTrue(String.valueOf(docRes.body.get("error")).contains("approval workflow"), "Rejection message specifies approval workflow required");

        // B. Attempt to create PHARMACY role from Admin Create Account -> must be rejected
        Map<String, Object> pharmReq = new HashMap<>();
        pharmReq.put("role", "PHARMACY");
        pharmReq.put("fullName", "Direct Pharmacy Store");
        pharmReq.put("email", "illegal.pharm" + System.currentTimeMillis() + "@test.com");
        pharmReq.put("password", "Pharm@123");
        pharmReq.put("confirmPassword", "Pharm@123");

        ApiResponse pharmRes = sendRequest("POST", "/api/admin/users", pharmReq, adminToken);
        assertTrue(pharmRes.statusCode == 400, "Admin direct creation of PHARMACY rejected with HTTP 400");

        // C. Create PATIENT role -> allowed
        String patientEmail = "patient.direct" + System.currentTimeMillis() + "@test.com";
        Map<String, Object> patReq = new HashMap<>();
        patReq.put("role", "PATIENT");
        patReq.put("fullName", "Direct Patient");
        patReq.put("email", patientEmail);
        patReq.put("password", "Patient@123");
        patReq.put("confirmPassword", "Patient@123");

        ApiResponse patRes = sendRequest("POST", "/api/admin/users", patReq, adminToken);
        assertTrue(patRes.statusCode == 200, "Admin direct creation of PATIENT succeeded with HTTP 200");

        // D. Create ADMIN role -> allowed
        String adminEmail = "admin.sub" + System.currentTimeMillis() + "@test.com";
        Map<String, Object> newAdminReq = new HashMap<>();
        newAdminReq.put("role", "ADMIN");
        newAdminReq.put("fullName", "Assistant Admin");
        newAdminReq.put("email", adminEmail);
        newAdminReq.put("password", "Admin@123");
        newAdminReq.put("confirmPassword", "Admin@123");

        ApiResponse newAdminRes = sendRequest("POST", "/api/admin/users", newAdminReq, adminToken);
        assertTrue(newAdminRes.statusCode == 200, "Admin direct creation of ADMIN succeeded with HTTP 200");
    }

    private static void testDoctorApprovalFlow(String adminToken) throws Exception {
        System.out.println("\n--- Step 3: Doctor Registration & Approval Workflow ---");

        String docEmail = "dr.applicant" + System.currentTimeMillis() + "@test.com";
        Map<String, Object> regReq = new HashMap<>();
        regReq.put("role", "DOCTOR");
        regReq.put("fullName", "Dr. Priya Deshmukh");
        regReq.put("email", docEmail);
        regReq.put("phone", "+91 9876543210");
        regReq.put("password", "DoctorPass@123");
        regReq.put("specialization", "Cardiology");
        regReq.put("qualification", "MBBS, MD Cardiology");
        regReq.put("licenseNumber", "KMC-DOC-2026-999");
        regReq.put("experienceYears", 8);

        // A. Public registration
        ApiResponse regRes = sendRequest("POST", "/api/auth/register", regReq, null);
        assertTrue(regRes.statusCode == 200, "Doctor registration submitted successfully");
        Map<String, Object> regData = (Map<String, Object>) regRes.body.get("data");
        assertTrue(Boolean.TRUE.equals(regData.get("pendingApproval")), "Doctor registration marked pendingApproval: true");

        // B. Attempt login while PENDING -> must fail with 403
        ApiResponse loginPendingRes = sendRequest("POST", "/api/auth/login", Map.of("email", docEmail, "password", "DoctorPass@123"), null);
        assertTrue(loginPendingRes.statusCode == 403, "Login blocked for PENDING doctor with HTTP 403");
        assertTrue(String.valueOf(loginPendingRes.body.get("error")).toLowerCase().contains("pending"), "Error message informs user of pending approval");

        // C. Admin fetches pending doctor applications
        ApiResponse listRes = sendRequest("GET", "/api/admin/approvals/doctors?status=PENDING", null, adminToken);
        assertTrue(listRes.statusCode == 200, "Admin can retrieve pending doctor applications list");
        List<Map<String, Object>> apps = (List<Map<String, Object>>) listRes.body.get("data");
        Map<String, Object> targetApp = null;
        for (Map<String, Object> a : apps) {
            if (docEmail.equalsIgnoreCase((String) a.get("email"))) {
                targetApp = a;
                break;
            }
        }
        assertTrue(targetApp != null, "Pending doctor application found in admin list");
        int appId = ((Number) targetApp.get("id")).intValue();

        // D. Admin approves doctor application
        ApiResponse approveRes = sendRequest("POST", "/api/admin/approvals/doctor/" + appId + "/approve", new HashMap<>(), adminToken);
        assertTrue(approveRes.statusCode == 200, "Admin approved doctor application (HTTP 200)");

        // E. Prevent duplicate approval
        ApiResponse dupApproveRes = sendRequest("POST", "/api/admin/approvals/doctor/" + appId + "/approve", new HashMap<>(), adminToken);
        assertTrue(dupApproveRes.statusCode == 400, "Duplicate approval correctly rejected with HTTP 400");

        // F. Doctor can now login normally
        ApiResponse loginActiveRes = sendRequest("POST", "/api/auth/login", Map.of("email", docEmail, "password", "DoctorPass@123"), null);
        assertTrue(loginActiveRes.statusCode == 200, "Approved doctor can now log in successfully (HTTP 200)");
        Map<String, Object> docLoginData = (Map<String, Object>) loginActiveRes.body.get("data");
        assertTrue(docLoginData.get("token") != null, "Doctor received valid active JWT session token");
    }

    private static void testPharmacyApprovalFlow(String adminToken) throws Exception {
        System.out.println("\n--- Step 4: Pharmacy Registration, Rejection & Approval Workflow ---");

        // A. Register Pharmacy 1 (for testing rejection)
        String rejEmail = "reject.pharm" + System.currentTimeMillis() + "@test.com";
        Map<String, Object> rejReq = new HashMap<>();
        rejReq.put("role", "PHARMACY");
        rejReq.put("fullName", "Fake Meds Store");
        rejReq.put("pharmacyName", "Fake Meds Store");
        rejReq.put("ownerName", "Shady Owner");
        rejReq.put("email", rejEmail);
        rejReq.put("phone", "+91 90000 00000");
        rejReq.put("password", "PharmPass@123");
        rejReq.put("licenseNumber", "INVALID-LIC-000");

        ApiResponse regRejRes = sendRequest("POST", "/api/auth/register", rejReq, null);
        assertTrue(regRejRes.statusCode == 200, "Pharmacy rejection test registration submitted");

        // Find application
        ApiResponse listRes1 = sendRequest("GET", "/api/admin/approvals/pharmacies?status=PENDING", null, adminToken);
        List<Map<String, Object>> apps1 = (List<Map<String, Object>>) listRes1.body.get("data");
        Map<String, Object> rejApp = null;
        for (Map<String, Object> a : apps1) {
            if (rejEmail.equalsIgnoreCase((String) a.get("email"))) {
                rejApp = a;
                break;
            }
        }
        assertTrue(rejApp != null, "Found pending pharmacy application for rejection test");
        int rejAppId = ((Number) rejApp.get("id")).intValue();

        // Admin rejects pharmacy with reason
        ApiResponse rejRes = sendRequest("POST", "/api/admin/approvals/pharmacy/" + rejAppId + "/reject", Map.of("reason", "License number could not be verified."), adminToken);
        assertTrue(rejRes.statusCode == 200, "Admin successfully rejected invalid pharmacy application");

        // Login as rejected pharmacy -> must return 403 with reason
        ApiResponse loginRejRes = sendRequest("POST", "/api/auth/login", Map.of("email", rejEmail, "password", "PharmPass@123"), null);
        assertTrue(loginRejRes.statusCode == 403, "Rejected pharmacy login blocked with HTTP 403");
        assertTrue(String.valueOf(loginRejRes.body.get("error")).toLowerCase().contains("rejected"), "Error message specifies application was rejected");

        // B. Register Pharmacy 2 (for approval)
        String pharmEmail = "apollo.city" + System.currentTimeMillis() + "@test.com";
        Map<String, Object> regReq2 = new HashMap<>();
        regReq2.put("role", "PHARMACY");
        regReq2.put("fullName", "Apollo City Pharmacy");
        regReq2.put("pharmacyName", "Apollo City Pharmacy");
        regReq2.put("ownerName", "Ravi Kumar");
        regReq2.put("email", pharmEmail);
        regReq2.put("phone", "+91 91234 56789");
        regReq2.put("password", "ApolloPass@123");
        regReq2.put("licenseNumber", "KA-BLR-2026-DL-8888");
        regReq2.put("address", "100 Feet Road, Indiranagar, Bangalore");

        ApiResponse regAppRes = sendRequest("POST", "/api/auth/register", regReq2, null);
        assertTrue(regAppRes.statusCode == 200, "Pharmacy approval registration submitted");

        // Admin finds and approves
        ApiResponse listRes2 = sendRequest("GET", "/api/admin/approvals/pharmacies?status=PENDING", null, adminToken);
        List<Map<String, Object>> apps2 = (List<Map<String, Object>>) listRes2.body.get("data");
        Map<String, Object> appToApprove = null;
        for (Map<String, Object> a : apps2) {
            if (pharmEmail.equalsIgnoreCase((String) a.get("email"))) {
                appToApprove = a;
                break;
            }
        }
        assertTrue(appToApprove != null, "Found valid pending pharmacy application");
        int appToApproveId = ((Number) appToApprove.get("id")).intValue();

        ApiResponse approveRes = sendRequest("POST", "/api/admin/approvals/pharmacy/" + appToApproveId + "/approve", new HashMap<>(), adminToken);
        assertTrue(approveRes.statusCode == 200, "Pharmacy application approved successfully");

        // Login as approved pharmacy
        ApiResponse loginPharmRes = sendRequest("POST", "/api/auth/login", Map.of("email", pharmEmail, "password", "ApolloPass@123"), null);
        assertTrue(loginPharmRes.statusCode == 200, "Approved pharmacy logged in successfully");
    }

    private static void testPharmacyInventoryOperations() throws Exception {
        System.out.println("\n--- Step 5: Pharmacy Inventory Management (Add, Edit, Clear Stock, Remove, Clear All, Remove All) ---");

        // Login as seeded pharmacy
        ApiResponse loginRes = sendRequest("POST", "/api/auth/login", Map.of("email", "healthplus@example.com", "password", "Health@12345"), null);
        assertTrue(loginRes.statusCode == 200, "Logged in as HealthPlus Pharmacy");
        String pharmToken = (String) ((Map<String, Object>) loginRes.body.get("data")).get("token");

        // A. Add stock for medicine (medicineId: 1 = Paracetamol 650mg)
        Map<String, Object> addReq = new HashMap<>();
        addReq.put("medicineId", 1);
        addReq.put("stockQuantity", 120);
        addReq.put("unitPrice", 3.50);
        addReq.put("batchNumber", "TEST-PCM-999");
        addReq.put("expiryDate", "2028-12-31");

        ApiResponse addRes = sendRequest("POST", "/api/inventory/add", addReq, pharmToken);
        assertTrue(addRes.statusCode == 200, "Added stock for Paracetamol 650mg");

        // B. Update / Edit medicine
        Map<String, Object> updateReq = new HashMap<>();
        updateReq.put("stockQuantity", 85);
        updateReq.put("unitPrice", 4.25);
        updateReq.put("batchNumber", "TEST-PCM-999B");
        updateReq.put("expiryDate", "2029-01-31");

        ApiResponse putRes = sendRequest("PUT", "/api/pharmacy/inventory/1", updateReq, pharmToken);
        assertTrue(putRes.statusCode == 200, "PUT /api/pharmacy/inventory/{medicineId} updated stock and price successfully");

        // C. Clear Stock for medicine
        ApiResponse clearRes = sendRequest("POST", "/api/pharmacy/inventory/1/clear-stock", new HashMap<>(), pharmToken);
        assertTrue(clearRes.statusCode == 200, "POST /api/pharmacy/inventory/{medicineId}/clear-stock cleared stock");

        // Verify stock is now 0 and status is Out of Stock
        ApiResponse invListRes1 = sendRequest("GET", "/api/inventory", null, pharmToken);
        List<Map<String, Object>> invItems1 = (List<Map<String, Object>>) invListRes1.body.get("data");
        Map<String, Object> pcmItem = null;
        for (Map<String, Object> it : invItems1) {
            if (((Number) it.get("medicineId")).intValue() == 1) {
                pcmItem = it;
                break;
            }
        }
        assertTrue(pcmItem != null, "Medicine remains present in pharmacy inventory after Clear Stock");
        assertTrue(((Number) pcmItem.get("stockQuantity")).intValue() == 0, "Stock quantity is 0");
        assertTrue("Out of Stock".equalsIgnoreCase(String.valueOf(pcmItem.get("status"))), "Status is 'Out of Stock'");

        // D. Remove Medicine from Pharmacy Inventory
        ApiResponse removeRes = sendRequest("DELETE", "/api/pharmacy/inventory/1", null, pharmToken);
        assertTrue(removeRes.statusCode == 200, "DELETE /api/pharmacy/inventory/{medicineId} removed medicine from pharmacy");

        // Verify it is removed from this pharmacy's inventory
        ApiResponse invListRes2 = sendRequest("GET", "/api/inventory", null, pharmToken);
        List<Map<String, Object>> invItems2 = (List<Map<String, Object>>) invListRes2.body.get("data");
        boolean foundInPharm = false;
        for (Map<String, Object> it : invItems2) {
            if (((Number) it.get("medicineId")).intValue() == 1) {
                foundInPharm = true;
                break;
            }
        }
        assertTrue(!foundInPharm, "Medicine 1 is no longer in pharmacy's inventory list");

        // Verify global medicine catalog STILL has Medicine 1!
        ApiResponse medCatalogRes = sendRequest("GET", "/api/medicines/1", null, null);
        assertTrue(medCatalogRes.statusCode == 200, "Global medicine catalog entry 1 is preserved and still exists");

        // E. Test Clear All Stock
        ApiResponse clearAllRes = sendRequest("POST", "/api/pharmacy/inventory/clear-all-stock", new HashMap<>(), pharmToken);
        assertTrue(clearAllRes.statusCode == 200, "POST /api/pharmacy/inventory/clear-all-stock succeeded");

        // F. Test CSV Import
        String csvData = "medicine_name,stock_quantity,unit_price,batch_number,expiry_date,category\n" +
                "Paracetamol 650mg,150,3.75,IMP-PCM-101,2028-09-30,Pain Relief\n" +
                "Amoxicillin 500mg,80,8.50,IMP-AMX-102,2028-06-30,Antibiotics\n";
        ApiResponse csvRes = sendRequest("POST", "/api/inventory/import-csv", Map.of("csv", csvData), pharmToken);
        assertTrue(csvRes.statusCode == 200, "CSV Import succeeded and updated database records");

        // G. Test Remove All Medicines
        ApiResponse removeAllRes = sendRequest("DELETE", "/api/pharmacy/inventory/remove-all", null, pharmToken);
        assertTrue(removeAllRes.statusCode == 200, "DELETE /api/pharmacy/inventory/remove-all succeeded");

        ApiResponse invListRes3 = sendRequest("GET", "/api/inventory", null, pharmToken);
        List<Map<String, Object>> invItems3 = (List<Map<String, Object>>) invListRes3.body.get("data");
        assertTrue(invItems3.isEmpty(), "Pharmacy inventory is completely empty after remove-all");

        // Global medicines still exist!
        ApiResponse medListRes = sendRequest("GET", "/api/medicines", null, null);
        List<Map<String, Object>> allMeds = (List<Map<String, Object>>) medListRes.body.get("data");
        assertTrue(allMeds.size() >= 10, "Global medicine catalog is untouched and has all " + allMeds.size() + " medicines");
    }

    private static void testSecurityAuthorization(String adminToken) throws Exception {
        System.out.println("\n--- Step 6: Security & Role-Based Authorization Checks ---");

        // A. Patient login
        ApiResponse patLogin = sendRequest("POST", "/api/auth/login", Map.of("email", "priya@example.com", "password", "Patient@12345"), null);
        assertTrue(patLogin.statusCode == 200, "Logged in as patient Priya");
        String patToken = (String) ((Map<String, Object>) patLogin.body.get("data")).get("token");

        // B. Patient tries to access admin approvals -> 403 Forbidden
        ApiResponse patAppRes = sendRequest("GET", "/api/admin/approvals/doctors", null, patToken);
        assertTrue(patAppRes.statusCode == 403, "Patient access to /api/admin/approvals/doctors rejected with HTTP 403 Forbidden");

        // C. Patient tries to approve doctor -> 403 Forbidden
        ApiResponse patApproveRes = sendRequest("POST", "/api/admin/approvals/doctor/1/approve", new HashMap<>(), patToken);
        assertTrue(patApproveRes.statusCode == 403, "Patient calling approve endpoint rejected with HTTP 403");

        // D. Anonymous request without token -> 401 Unauthorized
        ApiResponse anonRes = sendRequest("GET", "/api/admin/approvals/doctors", null, null);
        assertTrue(anonRes.statusCode == 401, "Anonymous access to admin API rejected with HTTP 401 Unauthorized");
    }

    private static class ApiResponse {
        int statusCode;
        Map<String, Object> body;

        ApiResponse(int statusCode, Map<String, Object> body) {
            this.statusCode = statusCode;
            this.body = body;
        }
    }

    private static ApiResponse sendRequest(String method, String path, Object payload, String token) throws Exception {
        URL url = URI.create(BASE_URL + path).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        if (token != null) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }

        if (payload != null && ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method))) {
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            byte[] bytes = JsonUtil.toJson(payload).getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bytes);
                os.flush();
            }
        }

        int code = conn.getResponseCode();
        BufferedReader reader;
        if (code >= 200 && code < 400) {
            reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        } else {
            reader = new BufferedReader(new InputStreamReader(conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream(), StandardCharsets.UTF_8));
        }

        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();

        Map<String, Object> json = null;
        try {
            json = JsonUtil.parseMap(sb.toString());
        } catch (Exception e) {
            json = new HashMap<>();
            json.put("raw", sb.toString());
        }

        return new ApiResponse(code, json);
    }
}
