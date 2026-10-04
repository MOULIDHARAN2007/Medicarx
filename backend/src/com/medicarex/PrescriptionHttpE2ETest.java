package com.medicarex;

import com.medicarex.util.JsonUtil;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PrescriptionHttpE2ETest {

    private static final String BASE_URL = "http://localhost:8080";
    private static int passed = 0;
    private static int total = 8;

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  MEDICAREX — HTTP REST API E2E PRESCRIPTION TESTS");
        System.out.println("==================================================");

        try {
            // Wait for server readiness
            boolean ready = false;
            for (int i = 0; i < 20; i++) {
                try {
                    HttpResponse h = sendHttp("GET", "/api/medicines", null, null);
                    if (h.statusCode == 200) {
                        ready = true;
                        break;
                    }
                } catch (Exception ignored) {}
                Thread.sleep(500);
            }

            if (!ready) {
                System.err.println("FATAL: HTTP Server not responding on " + BASE_URL);
                System.exit(1);
            }

            long timestamp = System.currentTimeMillis();

            // 1. Register Brand New Patient via HTTP POST /api/auth/register
            System.out.println("\n[HTTP 1] POST /api/auth/register (Brand new patient)");
            Map<String, Object> patReq = new HashMap<>();
            patReq.put("fullName", "Rhea Sengupta");
            patReq.put("email", "rhea_" + timestamp + "@medicarex.com");
            patReq.put("password", "Pass12345!");
            patReq.put("phone", "+91 91234 56789");
            patReq.put("role", "PATIENT");

            HttpResponse patAuthRes = sendHttp("POST", "/api/auth/register", JsonUtil.toJson(patReq), null);
            if (patAuthRes.jsonBody == null) {
                System.err.println("Register response was: " + patAuthRes.statusCode + " -> " + patAuthRes.body);
            }
            Map<String, Object> patAuthData = (Map<String, Object>) patAuthRes.jsonBody.get("data");
            String patToken = (String) patAuthData.get("token");
            Map<String, Object> patUser = (Map<String, Object>) patAuthData.get("user");
            int patUserId = ((Number) patUser.get("id")).intValue();

            if (patAuthRes.statusCode == 200 && patToken != null) {
                System.out.println("  -> PASSED: Patient registered via REST API (ID: " + patUserId + ")");
                passed++;
            } else {
                System.err.println("  -> FAILED: Register failed: " + patAuthRes.body);
            }

            // 2. GET /api/prescriptions for Brand New Patient -> Must be []
            System.out.println("\n[HTTP 2] GET /api/prescriptions for new patient (Must return empty list)");
            HttpResponse emptyRes = sendHttp("GET", "/api/prescriptions", null, patToken);
            List<?> list = (List<?>) emptyRes.jsonBody.get("data");
            if (emptyRes.statusCode == 200 && list != null && list.isEmpty()) {
                System.out.println("  -> PASSED: Returned status 200 with empty array []. Zero default prescriptions!");
                passed++;
            } else {
                System.err.println("  -> FAILED: Expected empty list, got: " + emptyRes.body);
            }

            // 3. Register or provision dynamic test Doctor and authenticate
            System.out.println("\n[HTTP 3] Provision & Authenticate Doctor for Test");
            com.medicarex.repository.DoctorRepository docRepo = new com.medicarex.repository.DoctorRepository();
            String docEmail = "test_doc_" + timestamp + "@medicarex.test";
            String docPass = "SecureDocPass123!";
            com.medicarex.service.AuthService authService = new com.medicarex.service.AuthService();
            com.medicarex.model.User docUser = authService.register(docEmail, docPass, "Dr. Arjun Kumar", "+91 98765 43210", "DOCTOR");
            new com.medicarex.repository.UserRepository().updateStatus(docUser.getId(), true);
            if (docRepo.findByUserId(docUser.getId()) == null) {
                com.medicarex.model.Doctor dProfile = new com.medicarex.model.Doctor();
                dProfile.setUserId(docUser.getId());
                dProfile.setSpecialization("Cardiology");
                dProfile.setLicenseNumber("DOC-TEST-" + timestamp);
                dProfile.setExperienceYears(12);
                dProfile.setConsultationFee(800.0);
                dProfile.setAvailableHours("09:00 - 17:00");
                docRepo.create(dProfile);
            }

            Map<String, Object> docReq = new HashMap<>();
            docReq.put("email", docEmail);
            docReq.put("password", docPass);

            HttpResponse docAuthRes = sendHttp("POST", "/api/auth/login", JsonUtil.toJson(docReq), null);
            Map<String, Object> docAuthData = docAuthRes.jsonBody != null ? (Map<String, Object>) docAuthRes.jsonBody.get("data") : null;
            String docToken = docAuthData != null ? (String) docAuthData.get("token") : null;

            if (docAuthRes.statusCode == 200 && docToken != null) {
                System.out.println("  -> PASSED: Doctor authenticated via REST API");
                passed++;
            } else {
                System.err.println("  -> FAILED: Doctor login failed: HTTP " + docAuthRes.statusCode + " " + docAuthRes.body);
            }

            // 4. Doctor issues real prescription via POST /api/prescriptions/create
            System.out.println("\n[HTTP 4] POST /api/prescriptions/create (Doctor issues prescription)");
            Map<String, Object> rxPayload = new HashMap<>();
            rxPayload.put("patientId", patUserId);
            rxPayload.put("diagnosis", "Sinusitis & Bacterial Pharyngitis");
            rxPayload.put("notes", "Complete 5-day antibiotic course. Plenty of fluids.");

            List<Map<String, Object>> items = new ArrayList<>();
            Map<String, Object> it1 = new HashMap<>();
            it1.put("medicineName", "Amoxicillin 500mg");
            it1.put("dosage", "500mg");
            it1.put("frequency", "1-0-1 After Food");
            it1.put("duration", "5 Days");
            it1.put("instructions", "Take with water after meals");
            items.add(it1);

            Map<String, Object> it2 = new HashMap<>();
            it2.put("medicineName", "Paracetamol 650mg");
            it2.put("dosage", "650mg");
            it2.put("frequency", "As needed for pain/fever");
            it2.put("duration", "3 Days");
            it2.put("instructions", "Take only if temp > 100 F");
            items.add(it2);

            rxPayload.put("items", items);

            HttpResponse createRes = sendHttp("POST", "/api/prescriptions/create", JsonUtil.toJson(rxPayload), docToken);
            Map<String, Object> createdData = (Map<String, Object>) createRes.jsonBody.get("data");
            int rxId = createdData != null ? ((Number) createdData.get("id")).intValue() : 0;
            String rxNum = createdData != null ? (String) createdData.get("prescriptionNumber") : "";

            if (createRes.statusCode == 200 && rxId > 0) {
                System.out.println("  -> PASSED: Prescription issued via REST API (ID: " + rxId + ", RxNum: " + rxNum + ")");
                passed++;
            } else {
                System.err.println("  -> FAILED: Prescription issuance failed: " + createRes.body);
            }

            // 5. Patient fetches /api/prescriptions & /api/patient/prescriptions
            System.out.println("\n[HTTP 5] GET /api/prescriptions and GET /api/patient/prescriptions");
            HttpResponse patFetchRes = sendHttp("GET", "/api/prescriptions", null, patToken);
            HttpResponse patAltRes = sendHttp("GET", "/api/patient/prescriptions", null, patToken);

            List<?> patRxList = (List<?>) patFetchRes.jsonBody.get("data");
            if (patFetchRes.statusCode == 200 && patAltRes.statusCode == 200 && patRxList != null && patRxList.size() == 1) {
                Map<String, Object> rxObj = (Map<String, Object>) patRxList.get(0);
                if (rxNum.equals(rxObj.get("prescriptionNumber"))) {
                    System.out.println("  -> PASSED: Patient successfully retrieves real doctor-issued prescription #" + rxNum);
                    passed++;
                } else {
                    System.err.println("  -> FAILED: Prescription number mismatch");
                }
            } else {
                System.err.println("  -> FAILED: Patient could not retrieve prescription: " + patFetchRes.body);
            }

            // 6. Patient downloads PDF via GET /api/prescriptions/download?id=
            System.out.println("\n[HTTP 6] GET /api/prescriptions/download?id=" + rxId);
            HttpResponse dlRes = sendHttp("GET", "/api/prescriptions/download?id=" + rxId, null, patToken);
            if (dlRes.statusCode == 200 && "application/pdf".equalsIgnoreCase(dlRes.contentType) && dlRes.binaryLength > 500) {
                System.out.println("  -> PASSED: Official signed prescription PDF downloaded successfully (" + dlRes.binaryLength + " bytes)");
                passed++;
            } else {
                System.err.println("  -> FAILED: Download returned status " + dlRes.statusCode + " / type: " + dlRes.contentType);
            }

            // 7. Security Isolation: Patient B cannot view Patient A's prescription
            System.out.println("\n[HTTP 7] Security Isolation: Patient B cannot access Patient A's prescription");
            Map<String, Object> patBReq = new HashMap<>();
            patBReq.put("fullName", "Vikram Rathore");
            patBReq.put("email", "vikram_" + timestamp + "@medicarex.com");
            patBReq.put("password", "Pass12345!");
            patBReq.put("phone", "+91 99999 11111");
            patBReq.put("role", "PATIENT");

            HttpResponse bAuthRes = sendHttp("POST", "/api/auth/register", JsonUtil.toJson(patBReq), null);
            String bToken = (String) ((Map<String, Object>) bAuthRes.jsonBody.get("data")).get("token");

            HttpResponse bViewRes = sendHttp("GET", "/api/prescriptions/" + rxId, null, bToken);
            HttpResponse bDlRes = sendHttp("GET", "/api/prescriptions/download?id=" + rxId, null, bToken);

            if (bViewRes.statusCode == 403 && bDlRes.statusCode == 403) {
                System.out.println("  -> PASSED: Patient B rejected with HTTP 403 Forbidden for both view and download!");
                passed++;
            } else {
                System.err.println("  -> FAILED: Security check failed (View status: " + bViewRes.statusCode + ", Download status: " + bDlRes.statusCode + ")");
            }

            // 8. Unauthenticated request rejected with 401
            System.out.println("\n[HTTP 8] Unauthenticated Request Rejection");
            HttpResponse unauthRes = sendHttp("GET", "/api/prescriptions", null, null);
            if (unauthRes.statusCode == 401) {
                System.out.println("  -> PASSED: Unauthenticated request rejected with HTTP 401 Unauthorized");
                passed++;
            } else {
                System.err.println("  -> FAILED: Expected 401, got " + unauthRes.statusCode);
            }

            System.out.println("\n==================================================");
            System.out.println("  E2E HTTP RESULTS: " + passed + " / " + total + " TESTS PASSED");
            System.out.println("==================================================");

            if (passed == total) {
                System.exit(0);
            } else {
                System.exit(1);
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static class HttpResponse {
        int statusCode;
        String contentType;
        String body;
        int binaryLength;
        Map<String, Object> jsonBody;
    }

    private static HttpResponse sendHttp(String method, String endpoint, String body, String token) {
        HttpResponse res = new HttpResponse();
        try {
            URI uri = URI.create(BASE_URL + endpoint);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (token != null) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }

            if (body != null) {
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }

            res.statusCode = conn.getResponseCode();
            res.contentType = conn.getContentType();

            InputStream is = res.statusCode >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (is != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[1024];
                int n;
                while ((n = is.read(buf)) != -1) {
                    baos.write(buf, 0, n);
                }
                res.binaryLength = baos.size();
                res.body = new String(baos.toByteArray(), StandardCharsets.UTF_8);
                try {
                    res.jsonBody = JsonUtil.parseMap(res.body);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            res.statusCode = 500;
            res.body = e.getMessage();
        }
        return res;
    }
}
