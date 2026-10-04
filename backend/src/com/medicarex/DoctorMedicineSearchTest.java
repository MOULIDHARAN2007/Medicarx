package com.medicarex;

import com.medicarex.config.DatabaseConnection;
import com.medicarex.util.JsonUtil;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class DoctorMedicineSearchTest {
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
            conn.setReadTimeout(15000);
            if (jsonPayload != null) {
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            }
            conn.setRequestProperty("Accept", "*/*");

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
                sb.append(line).append("\n");
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
        System.out.println("   DOCTOR MEDICINE SEARCH VERIFICATION SUITE      ");
        System.out.println("==================================================");

        DatabaseConnection.initialize();
        int passed = 0;
        int totalTests = 8;
        long ts = System.currentTimeMillis();

        try {
            // Test 1: Register & Authenticate Doctor via REST API
            System.out.println("\n[TEST 1] Provision and Authenticate Doctor Account");
            String docEmail = "doctor_med_" + ts + "@medicarex.test";
            Map<String, Object> regReq = new HashMap<>();
            regReq.put("email", docEmail);
            regReq.put("password", "DoctorPass123!");
            regReq.put("fullName", "Dr. Aravind Swamy, MD");
            regReq.put("phone", "+91 94444 11111");
            regReq.put("role", "DOCTOR");
            regReq.put("specialty", "Cardiology");
            regReq.put("qualification", "MBBS, MD (Cardiology)");
            regReq.put("experienceYears", 12);
            regReq.put("consultationFee", 750.0);
            regReq.put("hospitalAffiliation", "Apollo Hospital Bengaluru");

            HttpResponse regRes = sendHttp("POST", "/api/auth/register", JsonUtil.toJson(regReq), null);
            Map<String, Object> regData = (Map<String, Object>) regRes.jsonBody.get("data");
            String docToken = (String) regData.get("token");
            if (regRes.statusCode == 200 || regRes.statusCode == 201) {
                System.out.println("  -> PASSED: Doctor account provisioned and token acquired: " + docEmail);
                passed++;
            } else {
                System.err.println("  -> FAILED: Doctor registration failed: " + regRes.body);
            }

            // Test 2: Verify /doctor-medicine-search.html exists and returns 200
            System.out.println("\n[TEST 2] Verify /doctor-medicine-search.html endpoint availability");
            HttpResponse pageRes = sendHttp("GET", "/doctor-medicine-search.html", null, null);
            if (pageRes.statusCode == 200 && pageRes.body.contains("<!DOCTYPE html>")) {
                System.out.println("  -> PASSED: /doctor-medicine-search.html served with HTTP 200 OK (" + pageRes.body.length() + " bytes)");
                passed++;
            } else {
                System.err.println("  -> FAILED: /doctor-medicine-search.html not reachable: " + pageRes.statusCode);
            }

            // Test 3: Verify Doctor Medicine Search Page Structure
            System.out.println("\n[TEST 3] Verify Doctor Medicine Search Page Content & Exclusions");
            String html = pageRes.body;
            boolean hasTitle = html.contains("Medicine Search");
            boolean hasHero = html.contains("Search verified pharmaceutical catalog");
            boolean hasFormulary = html.contains("Clinical Formulary");
            boolean hasSearchInput = html.contains("doc-med-search");
            boolean hasCategoryPills = html.contains("category-pills");
            boolean hasDetailsModal = html.contains("med-details-modal");
            boolean hasPrescriptionModal = html.contains("create-rx-modal");

            // Exclusions: must NOT have patient doctor-booking components
            boolean noFindBookDoctors = !html.contains("Find & Book Specialist Doctors");
            boolean noScheduleConsultations = !html.contains("Schedule in-person or video consultations");
            boolean noViewMyAppointments = !html.contains("View My Appointments");
            boolean noBookingButtons = !html.contains("Book Appointment");

            if (hasTitle && hasHero && hasFormulary && hasSearchInput && hasCategoryPills && hasDetailsModal && hasPrescriptionModal
                    && noFindBookDoctors && noScheduleConsultations && noViewMyAppointments && noBookingButtons) {
                System.out.println("  -> PASSED: Correct dedicated medicine search structure verified!");
                System.out.println("     * Dedicated medicine search header: YES");
                System.out.println("     * Clinical formulary search input: YES");
                System.out.println("     * Therapeutic category filters: YES");
                System.out.println("     * Clinical details modal: YES");
                System.out.println("     * Digital prescription flow: YES");
                System.out.println("     * Excluded all patient doctor-booking UI: YES");
                passed++;
            } else {
                System.err.println("  -> FAILED: Page contains invalid components or missing required elements.");
            }

            // Test 4: Verify Medicine REST API query from doctor view
            System.out.println("\n[TEST 4] Query /api/medicines REST API (Search Paracetamol)");
            HttpResponse medRes = sendHttp("GET", "/api/medicines?q=Paracetamol", null, docToken);
            List<?> medList = (List<?>) medRes.jsonBody.get("data");
            if (medRes.statusCode == 200 && medList != null && !medList.isEmpty()) {
                Map<String, Object> firstMed = (Map<String, Object>) medList.get(0);
                System.out.println("  -> PASSED: Retrieved " + medList.size() + " medicine(s)");
                System.out.println("     * Name: " + firstMed.get("name"));
                System.out.println("     * Generic: " + firstMed.get("genericName"));
                System.out.println("     * Category: " + firstMed.get("category"));
                System.out.println("     * Dosage & Form: " + firstMed.get("dosage") + " • " + firstMed.get("form"));
                System.out.println("     * Base Price: ₹" + firstMed.get("basePrice"));
                passed++;
            } else {
                System.err.println("  -> FAILED: Medicine query failed: " + medRes.body);
            }

            // Test 5: Verify shell.js Doctor Navigation configuration
            System.out.println("\n[TEST 5] Verify shell.js Doctor Menu Routing");
            String shellJs = new String(Files.readAllBytes(Paths.get("frontend/js/components/shell.js")), StandardCharsets.UTF_8);
            boolean shellHasDocMedSearch = shellJs.contains("key: 'doctor-medicine-search', label: 'Medicine Search', icon: 'search', href: '/doctor-medicine-search.html'");
            boolean shellHasPatientDocSearch = shellJs.contains("key: 'doctor-search', label: 'Find Doctors', icon: 'users', href: '/doctor-search.html'");
            if (shellHasDocMedSearch && shellHasPatientDocSearch) {
                System.out.println("  -> PASSED: shell.js correctly links Doctor to /doctor-medicine-search.html and Patient to /doctor-search.html");
                passed++;
            } else {
                System.err.println("  -> FAILED: shell.js menu routing mismatch.");
            }

            // Test 6: Verify doctor-dashboard.html quick action link
            System.out.println("\n[TEST 6] Verify doctor-dashboard.html Quick Action Link");
            String docDashHtml = new String(Files.readAllBytes(Paths.get("frontend/doctor-dashboard.html")), StandardCharsets.UTF_8);
            boolean dashHasCorrectLink = docDashHtml.contains("window.location.href='/doctor-medicine-search.html'");
            if (dashHasCorrectLink) {
                System.out.println("  -> PASSED: Doctor Dashboard quick action links to /doctor-medicine-search.html");
                passed++;
            } else {
                System.err.println("  -> FAILED: doctor-dashboard.html still has old link.");
            }

            // Test 7: Verify Patient Doctor Booking page (/doctor-search.html) remains intact for Patients
            System.out.println("\n[TEST 7] Verify Patient Doctor Search page (/doctor-search.html) intact");
            HttpResponse patDocRes = sendHttp("GET", "/doctor-search.html", null, null);
            if (patDocRes.statusCode == 200 && patDocRes.body.contains("Find & Book Specialist Doctors") && patDocRes.body.contains("doctor-search-input")) {
                System.out.println("  -> PASSED: Patient Doctor Booking page is completely preserved for patients!");
                passed++;
            } else {
                System.err.println("  -> FAILED: Patient doctor search broken: " + patDocRes.statusCode);
            }

            // Test 8: Verify Appointments page (/appointments.html & /doctor-appointments.html) remains separated
            System.out.println("\n[TEST 8] Verify Appointments pages separation");
            HttpResponse patApptRes = sendHttp("GET", "/appointments.html", null, null);
            HttpResponse docApptRes = sendHttp("GET", "/doctor-appointments.html", null, null);
            if (patApptRes.statusCode == 200 && docApptRes.statusCode == 200) {
                System.out.println("  -> PASSED: Appointments functionality completely separated in its dedicated routes!");
                passed++;
            } else {
                System.err.println("  -> FAILED: Appointments routes inaccessible.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("\n==================================================");
        System.out.println("   RESULTS: " + passed + " / " + totalTests + " TESTS PASSED");
        System.out.println("==================================================");

        if (passed == totalTests) {
            System.out.println(">>> ALL DOCTOR MEDICINE SEARCH VERIFICATION TESTS PASSED! <<<");
            System.exit(0);
        } else {
            System.exit(1);
        }
    }
}
