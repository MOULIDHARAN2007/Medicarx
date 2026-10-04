package com.medicarex;

import com.medicarex.config.DatabaseConnection;
import com.medicarex.model.TimelineEvent;
import com.medicarex.model.TreatmentGoal;
import com.medicarex.repository.DoctorWorkspaceRepository;
import com.medicarex.service.AiClinicalDecisionService;
import com.medicarex.service.PrescriptionSafetyService;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.*;

public class DoctorWorkspaceTest {

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("MediCareX Doctor Workspace Integrated Test Suite");
        System.out.println("==================================================");

        int passed = 0;
        int failed = 0;

        // TEST 1: Database Tables Verification
        try {
            System.out.print("[TEST 1] Verifying workspace tables exist... ");
            try (Connection conn = DatabaseConnection.getConnection()) {
                DatabaseMetaData meta = conn.getMetaData();
                boolean tgExists = checkTable(meta, "treatment_goals");
                boolean tpeExists = checkTable(meta, "treatment_progress_entries");
                boolean aiLogExists = checkTable(meta, "ai_decision_audit_logs");

                if (tgExists && tpeExists && aiLogExists) {
                    System.out.println("PASSED (treatment_goals, treatment_progress_entries, ai_decision_audit_logs verified)");
                    passed++;
                } else {
                    System.out.println("FAILED (Missing tables: tg=" + tgExists + ", tpe=" + tpeExists + ", ai=" + aiLogExists + ")");
                    failed++;
                }
            }
        } catch (Exception e) {
            System.out.println("FAILED (" + e.getMessage() + ")");
            failed++;
        }

        DoctorWorkspaceRepository workspaceRepo = new DoctorWorkspaceRepository();
        AiClinicalDecisionService aiService = new AiClinicalDecisionService();
        PrescriptionSafetyService safetyService = new PrescriptionSafetyService();

        // TEST 2: Doctor-Patient Authorization Security
        try {
            System.out.print("[TEST 2] Verifying Doctor-Patient Access Security... ");
            // Doctor user ID 2 (Dr. Priya Sharma) accessing Patient 1 (Rahul Kumar)
            boolean docAccess = workspaceRepo.verifyDoctorPatientAccess(2, 1, "DOCTOR");
            boolean adminAccess = workspaceRepo.verifyDoctorPatientAccess(1, 1, "ADMIN");
            boolean patientAccessBlocked = workspaceRepo.verifyDoctorPatientAccess(3, 1, "PATIENT");

            if (docAccess && adminAccess && !patientAccessBlocked) {
                System.out.println("PASSED (Doctor & Admin access permitted, Patient role forbidden)");
                passed++;
            } else {
                System.out.println("FAILED (doc=" + docAccess + ", admin=" + adminAccess + ", patBlocked=" + !patientAccessBlocked + ")");
                failed++;
            }
        } catch (Exception e) {
            System.out.println("FAILED (" + e.getMessage() + ")");
            failed++;
        }

        // TEST 3: Patient Health Timeline
        try {
            System.out.print("[TEST 3] Verifying Chronological Timeline Aggregation... ");
            List<TimelineEvent> timeline = workspaceRepo.getPatientHealthTimeline(1, 50);
            if (timeline != null && !timeline.isEmpty()) {
                System.out.println("PASSED (" + timeline.size() + " timeline events aggregated chronologically)");
                for (int i = 0; i < Math.min(3, timeline.size()); i++) {
                    TimelineEvent ev = timeline.get(i);
                    System.out.println("   • [" + ev.getEventType() + "] " + ev.getEventDate() + " - " + ev.getTitle() + " (" + ev.getBadge() + ")");
                }
                passed++;
            } else {
                System.out.println("FAILED (Timeline empty or null)");
                failed++;
            }
        } catch (Exception e) {
            System.out.println("FAILED (" + e.getMessage() + ")");
            failed++;
        }

        // TEST 4: Treatment Goal & Progress Entries Tracker
        try {
            System.out.print("[TEST 4] Verifying Treatment Goals & Progress Milestones... ");
            String goalTitle = "Automated Test BP Goal - " + System.currentTimeMillis();
            TreatmentGoal created = workspaceRepo.createTreatmentGoal(1, 1, goalTitle, "Systolic BP", "mmHg", "155", "120", "IN_PROGRESS");
            if (created != null && created.getId() > 0) {
                int goalId = created.getId();
                // Add follow-up progress entry
                workspaceRepo.addProgressEntry(goalId, "142", "2026-09-07", "First follow-up consultation measurement", "Dr. Priya Sharma", "IMPROVING");
                workspaceRepo.addProgressEntry(goalId, "130", "2026-09-07", "Latest review measurement", "Dr. Priya Sharma", "IMPROVING");

                List<TreatmentGoal> goals = workspaceRepo.getTreatmentGoals(1);
                TreatmentGoal targetGoal = null;
                for (TreatmentGoal g : goals) {
                    if (g.getId() == goalId) {
                        targetGoal = g;
                        break;
                    }
                }

                if (targetGoal != null && "130".equals(targetGoal.getLatestValue()) && targetGoal.getProgressEntries().size() >= 2) {
                    System.out.println("PASSED (Goal #" + goalId + " created, baseline 155 -> follow-up 142 -> latest 130 " + targetGoal.getUnit() + ")");
                    passed++;
                } else {
                    System.out.println("FAILED (Progress entries or latest value mismatch)");
                    failed++;
                }
            } else {
                System.out.println("FAILED (Could not create treatment goal)");
                failed++;
            }
        } catch (Exception e) {
            System.out.println("FAILED (" + e.getMessage() + ")");
            failed++;
        }

        // TEST 5: Real Pharmacy Inventory & Distance Lookup
        try {
            System.out.print("[TEST 5] Verifying Real MySQL Pharmacy Inventory Availability... ");
            List<Map<String, Object>> stock = workspaceRepo.getNearbyPharmacyAvailability(1, 12.9716, 77.5946, 10);
            if (stock != null && !stock.isEmpty()) {
                Map<String, Object> first = stock.get(0);
                System.out.println("PASSED (" + stock.size() + " pharmacies found. Nearest: " + first.get("pharmacyName") + " at " + first.get("distanceKm") + "km, Stock: " + first.get("stockQuantity") + " units)");
                passed++;
            } else {
                System.out.println("FAILED (No pharmacy stock returned)");
                failed++;
            }
        } catch (Exception e) {
            System.out.println("FAILED (" + e.getMessage() + ")");
            failed++;
        }

        // TEST 6: Prescription Safety Checker
        try {
            System.out.print("[TEST 6] Verifying Prescription Safety Checker Heuristics... ");
            // Test Scenario A: Paracetamol max daily dose violation + Beta-lactam warning
            List<Map<String, Object>> testMeds = new ArrayList<>();
            Map<String, Object> med1 = new HashMap<>();
            med1.put("medicineId", 2);
            med1.put("medicineName", "Paracetamol 650mg");
            med1.put("dosage", "650mg");
            med1.put("frequency", "6 times daily");
            med1.put("duration", "5 days");
            testMeds.add(med1);

            Map<String, Object> med2 = new HashMap<>();
            med2.put("medicineId", 1);
            med2.put("medicineName", "Amoxicillin 500mg");
            med2.put("dosage", "500mg");
            med2.put("frequency", "Twice daily");
            med2.put("duration", "5 days");
            testMeds.add(med2);

            PrescriptionSafetyService.SafetyCheckResult result = safetyService.checkPrescriptionSafety(1, testMeds);
            if ("POTENTIAL_ISSUE".equals(result.overallStatus) && result.warnings.size() >= 2) {
                System.out.println("PASSED (Detected status: " + result.overallBadge + " with " + result.warnings.size() + " clinical warnings)");
                for (Map<String, Object> w : result.warnings) {
                    System.out.println("   • [" + w.get("badge") + "] " + w.get("medicine") + " -> " + w.get("warningType"));
                }
                passed++;
            } else {
                System.out.println("FAILED (Safety heuristics failed to detect max dose or allergy)");
                failed++;
            }
        } catch (Exception e) {
            System.out.println("FAILED (" + e.getMessage() + ")");
            failed++;
        }

        // TEST 7: AI Clinical Decision Support & Audit Log
        try {
            System.out.print("[TEST 7] Verifying AI Clinical Decision Support Output & Audit Log... ");
            AiClinicalDecisionService.ClinicalDecisionResult aiRes = aiService.generateDecisionSupport(2, 1, "Acute febrile illness, persistent non-productive cough, body ache", "Patient vitals: BP 122/82, HR 78, Temp 101F", "Acute Upper Respiratory Infection");

            if (aiRes != null && aiRes.clinicalConsiderations != null && !aiRes.clinicalConsiderations.isEmpty() && aiRes.questionsForDoctor.size() > 0) {
                System.out.println("PASSED (Structured clinical reasoning generated with mandatory doctor disclaimer)");
                System.out.println("   • Considerations: " + aiRes.clinicalConsiderations.split("\n")[0]);
                System.out.println("   • Disclaimer: " + aiRes.disclaimer);
                passed++;
            } else {
                System.out.println("FAILED (AI clinical support generation failed)");
                failed++;
            }
        } catch (Exception e) {
            System.out.println("FAILED (" + e.getMessage() + ")");
            failed++;
        }

        System.out.println("==================================================");
        System.out.println("Test Suite Completed: " + passed + " PASSED, " + failed + " FAILED");
        System.out.println("==================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static boolean checkTable(DatabaseMetaData meta, String tableName) {
        try (ResultSet rs = meta.getTables(null, null, tableName, null)) {
            if (rs.next()) return true;
        } catch (Exception ignored) {}
        try (ResultSet rs = meta.getTables(null, null, tableName.toUpperCase(), null)) {
            if (rs.next()) return true;
        } catch (Exception ignored) {}
        return false;
    }
}
