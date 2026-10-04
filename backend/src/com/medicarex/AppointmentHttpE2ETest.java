package com.medicarex;

import com.medicarex.auth.JwtUtil;
import com.medicarex.auth.PasswordHasher;
import com.medicarex.config.AppConfig;
import com.medicarex.config.DatabaseConnection;
import com.medicarex.model.Doctor;
import com.medicarex.model.User;
import com.medicarex.repository.DoctorRepository;
import com.medicarex.repository.PatientRepository;
import com.medicarex.repository.UserRepository;
import com.medicarex.util.JsonUtil;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unchecked")
public class AppointmentHttpE2ETest {
    private static final String BASE_URL = "http://localhost:" + AppConfig.PORT;

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("   MediCareX Appointment E2E & Security Test      ");
        System.out.println("==================================================");

        DatabaseConnection.initialize();

        int passed = 0;
        int failed = 0;

        UserRepository userRepo = new UserRepository();
        DoctorRepository docRepo = new DoctorRepository();
        PatientRepository patRepo = new PatientRepository();

        // 1. Setup Test Users
        String salt = PasswordHasher.generateSalt();
        String hash = PasswordHasher.hashPassword("Password@123", salt);

        User patA = userRepo.findByEmail("patient_a_appt@test.com");
        if (patA == null) {
            patA = userRepo.createUser("patient_a_appt@test.com", hash, salt, "Patient A Test", "+91 91111 22222", "PATIENT");
            patRepo.create(patA.getId(), "1994-04-10", "Female", "A+", "Indiranagar, Bangalore", "+91 91111 22222", "Care Health");
        }

        User patB = userRepo.findByEmail("patient_b_appt@test.com");
        if (patB == null) {
            patB = userRepo.createUser("patient_b_appt@test.com", hash, salt, "Patient B Test", "+91 93333 44444", "PATIENT");
            patRepo.create(patB.getId(), "1992-08-15", "Male", "B+", "Koramangala, Bangalore", "+91 93333 44444", "Star Health");
        }

        User docUser = userRepo.findByEmail("arjun@example.com");
        if (docUser == null) {
            docUser = userRepo.createUser("arjun@example.com", hash, salt, "Dr. Arjun Kumar", "+91 98450 11223", "DOCTOR");
            docRepo.create(new Doctor(0, docUser.getId(), "Cardiologist & General Physician", "KMC-88492-MD", "Manipal Hospital, Bangalore", 12, 750.0, 4.9, "09:00 AM - 05:00 PM"));
        }

        Doctor doc = docRepo.findByUserId(docUser.getId());
        int doctorId = doc != null ? doc.getId() : 1;

        // Clean up any previous test appointments for test patients
        try (java.sql.Connection conn = DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement("DELETE FROM appointments WHERE patient_id IN (SELECT id FROM patients WHERE user_id IN (?, ?))")) {
            ps.setInt(1, patA.getId());
            ps.setInt(2, patB.getId());
            ps.executeUpdate();
        } catch (Exception ignored) {}

        String tokenA = JwtUtil.generateToken(patA.getId(), patA.getEmail(), patA.getRole(), patA.getFullName());
        String tokenB = JwtUtil.generateToken(patB.getId(), patB.getEmail(), patB.getRole(), patB.getFullName());
        String tokenDoc = JwtUtil.generateToken(docUser.getId(), docUser.getEmail(), docUser.getRole(), docUser.getFullName());

        String testDate = LocalDate.now().plusDays(7).toString();
        String testTime = "10:30 AM";

        try {
            // TEST 1: Doctor Directory Search
            System.out.print("[TEST 1] GET /api/appointments/doctors -> ");
            HttpResponse docRes = sendHttp("GET", "/api/appointments/doctors", null, tokenA);
            if (docRes.statusCode == 200) {
                Map<String, Object> body = JsonUtil.parseMap(docRes.body);
                List<Map<String, Object>> docs = (List<Map<String, Object>>) body.get("data");
                if (docs != null && !docs.isEmpty()) {
                    System.out.println("PASS (Found " + docs.size() + " real doctors)");
                    passed++;
                } else {
                    System.err.println("FAIL (Doctor list empty)");
                    failed++;
                }
            } else {
                System.err.println("FAIL (HTTP " + docRes.statusCode + "): " + docRes.body);
                failed++;
            }

            // TEST 2: Check Available Slots
            System.out.print("[TEST 2] GET /api/appointments/slots?doctorId=" + doctorId + "&date=" + testDate + " -> ");
            HttpResponse slotRes = sendHttp("GET", "/api/appointments/slots?doctorId=" + doctorId + "&date=" + testDate, null, tokenA);
            if (slotRes.statusCode == 200) {
                Map<String, Object> body = JsonUtil.parseMap(slotRes.body);
                List<Map<String, Object>> slots = (List<Map<String, Object>>) body.get("data");
                if (slots != null && !slots.isEmpty()) {
                    System.out.println("PASS (Found " + slots.size() + " slots)");
                    passed++;
                } else {
                    System.err.println("FAIL (Slots list empty)");
                    failed++;
                }
            } else {
                System.err.println("FAIL (HTTP " + slotRes.statusCode + "): " + slotRes.body);
                failed++;
            }

            // TEST 3: Patient A Books Appointment (Date, Time, Reason)
            System.out.print("[TEST 3] POST /api/appointments/book (Patient A) -> ");
            String bookPayload = "{\"doctorId\":" + doctorId + ",\"appointmentDate\":\"" + testDate + "\",\"appointmentTime\":\"" + testTime + "\",\"type\":\"Cardio Consultation\",\"reason\":\"Chest discomfort evaluation\"}";
            HttpResponse bookResA = sendHttp("POST", "/api/appointments/book", bookPayload, tokenA);
            int apptId = -1;
            if (bookResA.statusCode == 200) {
                Map<String, Object> body = JsonUtil.parseMap(bookResA.body);
                Map<String, Object> data = (Map<String, Object>) body.get("data");
                if (data != null && data.get("id") != null) {
                    apptId = ((Number) data.get("id")).intValue();
                    System.out.println("PASS (Appointment #" + apptId + " confirmed for " + testDate + " at " + testTime + ")");
                    passed++;
                } else {
                    System.err.println("FAIL (Invalid response data: " + bookResA.body + ")");
                    failed++;
                }
            } else {
                System.err.println("FAIL (HTTP " + bookResA.statusCode + "): " + bookResA.body);
                failed++;
            }

            // TEST 4: Patient A Sees Upcoming Appointment in List
            System.out.print("[TEST 4] GET /api/appointments (Patient A List) -> ");
            HttpResponse patListRes = sendHttp("GET", "/api/appointments", null, tokenA);
            if (patListRes.statusCode == 200) {
                Map<String, Object> body = JsonUtil.parseMap(patListRes.body);
                List<Map<String, Object>> list = (List<Map<String, Object>>) body.get("data");
                boolean found = false;
                if (list != null) {
                    for (Map<String, Object> item : list) {
                        if (((Number) item.get("id")).intValue() == apptId) {
                            found = true;
                            break;
                        }
                    }
                }
                if (found) {
                    System.out.println("PASS (Appointment #" + apptId + " present in Patient A appointment queue)");
                    passed++;
                } else {
                    System.err.println("FAIL (Appointment #" + apptId + " missing in list)");
                    failed++;
                }
            } else {
                System.err.println("FAIL (HTTP " + patListRes.statusCode + "): " + patListRes.body);
                failed++;
            }

            // TEST 5: Doctor Sees Patient A's Appointment
            System.out.print("[TEST 5] GET /api/appointments (Doctor Queue) -> ");
            HttpResponse docApptRes = sendHttp("GET", "/api/appointments", null, tokenDoc);
            if (docApptRes.statusCode == 200) {
                Map<String, Object> body = JsonUtil.parseMap(docApptRes.body);
                List<Map<String, Object>> list = (List<Map<String, Object>>) body.get("data");
                boolean found = false;
                if (list != null) {
                    for (Map<String, Object> item : list) {
                        if (((Number) item.get("id")).intValue() == apptId && "Patient A Test".equals(item.get("patientName"))) {
                            found = true;
                            break;
                        }
                    }
                }
                if (found) {
                    System.out.println("PASS (Doctor can view Patient A's booked consultation)");
                    passed++;
                } else {
                    System.err.println("FAIL (Doctor queue did not contain Patient A's appointment)");
                    failed++;
                }
            } else {
                System.err.println("FAIL (HTTP " + docApptRes.statusCode + "): " + docApptRes.body);
                failed++;
            }

            // TEST 6: Double Booking Prevention (Patient B tries same Doctor + Date + Time)
            System.out.print("[TEST 6] POST /api/appointments/book (Patient B - Duplicate Slot Collision) -> ");
            HttpResponse bookResB = sendHttp("POST", "/api/appointments/book", bookPayload, tokenB);
            if (bookResB.statusCode == 409 || bookResB.statusCode == 400) {
                System.out.println("PASS (Correctly rejected double booking: " + bookResB.body + ")");
                passed++;
            } else {
                System.err.println("FAIL (Expected HTTP 409/400 but got " + bookResB.statusCode + "): " + bookResB.body);
                failed++;
            }

            // TEST 7: Past Date Validation (Reject booking for past date)
            System.out.print("[TEST 7] POST /api/appointments/book (Past Date '2020-01-01') -> ");
            String pastPayload = "{\"doctorId\":" + doctorId + ",\"appointmentDate\":\"2020-01-01\",\"appointmentTime\":\"10:00 AM\",\"type\":\"Consultation\",\"reason\":\"Past test\"}";
            HttpResponse pastRes = sendHttp("POST", "/api/appointments/book", pastPayload, tokenB);
            if (pastRes.statusCode == 400) {
                System.out.println("PASS (Correctly rejected past date: " + pastRes.body + ")");
                passed++;
            } else {
                System.err.println("FAIL (Expected HTTP 400 for past date but got " + pastRes.statusCode + "): " + pastRes.body);
                failed++;
            }

            // TEST 8: IDOR Security Prevention (Patient B attempts to cancel Patient A's appointment)
            System.out.print("[TEST 8] POST /api/appointments/cancel (Patient B cancelling Patient A's appt) -> ");
            String cancelPayload = "{\"appointmentId\":" + apptId + "}";
            HttpResponse idorRes = sendHttp("POST", "/api/appointments/cancel", cancelPayload, tokenB);
            if (idorRes.statusCode == 403 || idorRes.statusCode == 404) {
                System.out.println("PASS (IDOR prevented with HTTP " + idorRes.statusCode + ")");
                passed++;
            } else {
                System.err.println("FAIL (Security breach! Expected 403 Forbidden but got " + idorRes.statusCode + ")");
                failed++;
            }

            // TEST 9: Patient A Cancels Their Own Appointment
            System.out.print("[TEST 9] POST /api/appointments/cancel (Patient A legitimate cancellation) -> ");
            HttpResponse cancelResA = sendHttp("POST", "/api/appointments/cancel", cancelPayload, tokenA);
            if (cancelResA.statusCode == 200) {
                System.out.println("PASS (Appointment cancelled successfully)");
                passed++;
            } else {
                System.err.println("FAIL (HTTP " + cancelResA.statusCode + "): " + cancelResA.body);
                failed++;
            }

            // TEST 10: Slot Availability After Cancellation (Patient B can now book freed slot)
            System.out.print("[TEST 10] POST /api/appointments/book (Patient B books freed slot) -> ");
            HttpResponse freedBookRes = sendHttp("POST", "/api/appointments/book", bookPayload, tokenB);
            if (freedBookRes.statusCode == 200) {
                System.out.println("PASS (Patient B successfully booked newly freed slot)");
                passed++;
            } else {
                System.err.println("FAIL (Could not book freed slot: HTTP " + freedBookRes.statusCode + "): " + freedBookRes.body);
                failed++;
            }

        } catch (Exception e) {
            System.err.println("Unexpected exception in E2E tests: " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        System.out.println("==================================================");
        System.out.println("Test Results: " + passed + " PASSED, " + failed + " FAILED");
        System.out.println("==================================================");
    }

    private static HttpResponse sendHttp(String method, String endpoint, String jsonBody, String token) throws Exception {
        URI uri = URI.create(BASE_URL + endpoint);
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        if (token != null) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }

        if (jsonBody != null && ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method))) {
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }
        }

        int code = conn.getResponseCode();
        BufferedReader reader;
        if (code >= 200 && code < 400) {
            reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        } else {
            reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
        }

        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return new HttpResponse(code, sb.toString());
    }

    private static class HttpResponse {
        int statusCode;
        String body;

        HttpResponse(int sc, String b) {
            this.statusCode = sc;
            this.body = b;
        }
    }
}
