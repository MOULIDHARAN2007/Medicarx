package com.medicarex;

import com.medicarex.config.DatabaseConnection;
import com.medicarex.model.Prescription;
import com.medicarex.model.User;
import com.medicarex.repository.PrescriptionRepository;
import com.medicarex.repository.UserRepository;
import com.medicarex.util.PdfGenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PrescriptionIntegrationTest {

    private static int passed = 0;
    private static int total = 10;

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  MEDICAREX — DOCTOR-ISSUED PRESCRIPTION SUITE");
        System.out.println("==================================================");

        try {
            DatabaseConnection.initialize();

            // Purge any old dummy prescriptions
            try (java.sql.Connection conn = DatabaseConnection.getConnection();
                 java.sql.Statement st = conn.createStatement()) {
                st.execute("DELETE FROM prescription_items");
                st.execute("DELETE FROM prescriptions");
            }

            UserRepository userRepo = new UserRepository();
            PrescriptionRepository rxRepo = new PrescriptionRepository();

            long timestamp = System.currentTimeMillis();

            // 1. Create Patient A
            System.out.println("\n[TEST 1] Register Brand New Patient A");
            String patEmail = "patient_a_" + timestamp + "@medicarex.com";
            User patUser = userRepo.createUser(patEmail, "hash123", "salt123", "Ananya Reddy", "+91 98888 12345", "PATIENT");
            if (patUser != null && patUser.getId() > 0) {
                System.out.println("  -> PASSED: Patient A created (ID: " + patUser.getId() + ")");
                passed++;
            } else {
                System.err.println("  -> FAILED: Could not create Patient A");
            }

            // 2. Query Prescriptions for Patient A -> MUST BE ZERO
            System.out.println("\n[TEST 2] Verify Patient A has ZERO default prescriptions");
            List<Prescription> emptyList = rxRepo.findByPatientUserId(patUser.getId());
            if (emptyList != null && emptyList.isEmpty()) {
                System.out.println("  -> PASSED: Patient A has exactly 0 prescriptions. Zero fake/default prescriptions!");
                passed++;
            } else {
                System.err.println("  -> FAILED: Expected 0 prescriptions, got: " + (emptyList != null ? emptyList.size() : "null"));
            }

            // 3. Authenticate Doctor
            System.out.println("\n[TEST 3] Verify Doctor Account & Profile");
            User docUser = userRepo.findByEmail("arjun@example.com");
            if (docUser == null) {
                docUser = userRepo.createUser("doctor_test_" + timestamp + "@medicarex.com", "hash123", "salt123", "Dr. Arjun Kumar", "+91 98450 11223", "DOCTOR");
            }
            if (docUser != null) {
                System.out.println("  -> PASSED: Doctor account found/verified (" + docUser.getFullName() + ")");
                passed++;
            } else {
                System.err.println("  -> FAILED: Doctor not found in database");
            }

            // 4. Doctor issues prescription to Patient A
            System.out.println("\n[TEST 4] Doctor Issues Real Digital Prescription to Patient A");
            List<Map<String, Object>> meds = new ArrayList<>();
            Map<String, Object> m1 = new HashMap<>();
            m1.put("medicineName", "Azithromycin 500mg");
            m1.put("dosage", "500mg");
            m1.put("frequency", "1-0-0 (Once daily)");
            m1.put("duration", "3 Days");
            m1.put("instructions", "Take after breakfast with plenty of water");
            m1.put("quantity", 3);
            meds.add(m1);

            Map<String, Object> m2 = new HashMap<>();
            m2.put("medicineName", "Montelukast & Levocetirizine 10mg");
            m2.put("dosage", "10mg");
            m2.put("frequency", "0-0-1 (Night)");
            m2.put("duration", "5 Days");
            m2.put("instructions", "Take before bedtime");
            m2.put("quantity", 5);
            meds.add(m2);

            Prescription createdRx = rxRepo.create(docUser.getId(), patUser.getId(), "Acute Bronchitis and Seasonal Allergy", "Steam inhalation twice daily. Complete 3-day course.", meds);
            if (createdRx != null && createdRx.getId() > 0 && createdRx.getItems().size() == 2) {
                System.out.println("  -> PASSED: Created digital prescription #" + createdRx.getPrescriptionNumber() + " with 2 medications");
                System.out.println("     Doctor Signature: " + createdRx.getDoctorSignature());
                System.out.println("     Patient: " + createdRx.getPatientName());
                passed++;
            } else {
                System.err.println("  -> FAILED: Could not create prescription");
            }

            // 5. Patient A retrieves newly issued prescription
            System.out.println("\n[TEST 5] Patient A Retrieves Doctor-Issued Prescription");
            List<Prescription> patList = rxRepo.findByPatientUserId(patUser.getId());
            if (patList != null && patList.size() == 1) {
                Prescription rx = patList.get(0);
                if (rx.getPrescriptionNumber().equals(createdRx.getPrescriptionNumber()) && rx.getItems().size() == 2) {
                    System.out.println("  -> PASSED: Patient A sees real doctor-issued prescription #" + rx.getPrescriptionNumber());
                    passed++;
                } else {
                    System.err.println("  -> FAILED: Prescription data mismatch");
                }
            } else {
                System.err.println("  -> FAILED: Expected 1 prescription, got: " + (patList != null ? patList.size() : "null"));
            }

            // 6. Single Prescription Details by ID
            System.out.println("\n[TEST 6] Find Prescription by ID");
            Prescription singleRx = rxRepo.findById(createdRx.getId());
            if (singleRx != null && singleRx.getId() == createdRx.getId() && singleRx.getItems().size() == 2) {
                System.out.println("  -> PASSED: Prescription details verified (Diagnosis: " + singleRx.getDiagnosis() + ")");
                passed++;
            } else {
                System.err.println("  -> FAILED: findById failed");
            }

            // 7. PDF Generation Test
            System.out.println("\n[TEST 7] PDF Document Generation");
            byte[] pdf = PdfGenerator.generatePrescriptionPdf(createdRx);
            if (pdf != null && pdf.length > 500) {
                System.out.println("  -> PASSED: PDF generated successfully (" + pdf.length + " bytes)");
                passed++;
            } else {
                System.err.println("  -> FAILED: PDF generation failed");
            }

            // 8. Cross-Patient Security Isolation Check
            System.out.println("\n[TEST 8] Cross-Patient Security Isolation");
            String pat2Email = "patient_b_" + timestamp + "@medicarex.com";
            User pat2User = userRepo.createUser(pat2Email, "hash123", "salt123", "Suresh Patel", "+91 97777 54321", "PATIENT");
            List<Prescription> pat2List = rxRepo.findByPatientUserId(pat2User.getId());
            if (pat2List != null && pat2List.isEmpty()) {
                System.out.println("  -> PASSED: Patient B has 0 prescriptions and CANNOT see Patient A's prescription");
                passed++;
            } else {
                System.err.println("  -> FAILED: Patient B saw prescriptions!");
            }

            // 9. Multiple Prescriptions for Patient A
            System.out.println("\n[TEST 9] Multiple Doctor-Issued Prescriptions for Same Patient");
            List<Map<String, Object>> meds2 = new ArrayList<>();
            Map<String, Object> m3 = new HashMap<>();
            m3.put("medicineName", "Telmisartan 40mg");
            m3.put("dosage", "40mg");
            m3.put("frequency", "1-0-0 (Once morning)");
            m3.put("duration", "30 Days");
            m3.put("instructions", "Take after breakfast");
            m3.put("quantity", 30);
            meds2.add(m3);

            rxRepo.create(docUser.getId(), patUser.getId(), "Cardio Consultation & BP Management", "Daily BP monitoring.", meds2);
            List<Prescription> multiList = rxRepo.findByPatientUserId(patUser.getId());
            if (multiList != null && multiList.size() == 2) {
                System.out.println("  -> PASSED: Patient A now has 2 distinct real prescriptions:");
                System.out.println("     Rx 1: #" + multiList.get(0).getPrescriptionNumber() + " (" + multiList.get(0).getDiagnosis() + ")");
                System.out.println("     Rx 2: #" + multiList.get(1).getPrescriptionNumber() + " (" + multiList.get(1).getDiagnosis() + ")");
                passed++;
            } else {
                System.err.println("  -> FAILED: Expected 2 prescriptions, got: " + (multiList != null ? multiList.size() : "null"));
            }

            // 10. Database Re-query & Persistence Check
            System.out.println("\n[TEST 10] Database Direct SQL Verification");
            int totalRxCount = rxRepo.count();
            if (totalRxCount == 2) {
                System.out.println("  -> PASSED: Database row count verified! Exactly 2 doctor-issued prescriptions exist in MySQL/SQLite.");
                passed++;
            } else {
                System.err.println("  -> FAILED: Expected total 2, got: " + totalRxCount);
            }

            System.out.println("\n==================================================");
            System.out.println("  TEST RESULTS: " + passed + " / " + total + " TESTS PASSED");
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
}
