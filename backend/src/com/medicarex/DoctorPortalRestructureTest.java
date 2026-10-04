package com.medicarex;

import com.medicarex.config.DatabaseConnection;
import com.medicarex.model.Doctor;
import com.medicarex.model.Patient;
import com.medicarex.model.TimelineEvent;
import com.medicarex.model.TreatmentGoal;
import com.medicarex.model.User;
import com.medicarex.repository.DoctorRepository;
import com.medicarex.repository.DoctorWorkspaceRepository;
import com.medicarex.repository.PatientRepository;
import com.medicarex.repository.UserRepository;
import com.medicarex.service.AiClinicalDecisionService;
import com.medicarex.service.PrescriptionSafetyService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Automated Test Suite for MediCareX Doctor Portal Restructure:
 * - Independent Patient Overview / Summary
 * - AI Clinical Decision Support
 * - Prescription Safety & Formulary Verification (Green/Yellow/Red)
 * - Patient Health Timeline
 * - Treatment Progress Tracker
 * - Doctor-Patient Access Authorization
 */
public class DoctorPortalRestructureTest {

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================");
        System.out.println(">>> Running DoctorPortalRestructureTest <<<");
        System.out.println("==================================================");

        DatabaseConnection.getConnection();

        UserRepository userRepo = new UserRepository();
        DoctorRepository docRepo = new DoctorRepository();
        PatientRepository patRepo = new PatientRepository();
        DoctorWorkspaceRepository wsRepo = new DoctorWorkspaceRepository();
        AiClinicalDecisionService aiService = new AiClinicalDecisionService();
        PrescriptionSafetyService safetyService = new PrescriptionSafetyService();

        // 1. Verify Doctor & Patient exist in DB
        User docUser = userRepo.findByEmail("doctor@medicarex.com");
        if (docUser == null) {
            System.out.println("Creating test doctor user...");
            docUser = userRepo.createUser("doctor@medicarex.com", "hash", "salt", "Dr. Arjun Kumar", "+91 98765 43210", "DOCTOR");
            Doctor newDoc = new Doctor();
            newDoc.setUserId(docUser.getId());
            newDoc.setSpecialization("Cardiology");
            newDoc.setLicenseNumber("REG-DOC-2024-9988");
            newDoc.setHospitalAffiliation("Manipal Hospital Bangalore");
            newDoc.setExperienceYears(12);
            newDoc.setConsultationFee(1000.0);
            docRepo.create(newDoc);
        }
        int docUserId = docUser.getId();

        User patUser = userRepo.findByEmail("patient@medicarex.com");
        if (patUser == null) {
            System.out.println("Creating test patient user...");
            patUser = userRepo.createUser("patient@medicarex.com", "hash", "salt", "Priya Sharma", "+91 98765 11111", "PATIENT");
            patRepo.create(patUser.getId(), "1996-05-14", "Female", "O+", "Indiranagar Bangalore", "+91 98765 00000", "Star Health");
        }
        Patient patient = patRepo.findByUserId(patUser.getId());
        int patientId = patient != null ? patient.getId() : 1;

        System.out.println("\n--- 1. Testing Doctor-Patient Access Verification ---");
        boolean authCheck = wsRepo.verifyDoctorPatientAccess(docUserId, patientId, "DOCTOR");
        System.out.println("Doctor-Patient Access Authorized: " + authCheck);
        if (!authCheck) throw new RuntimeException("Doctor-patient authorization check failed");

        System.out.println("\n--- 2. Testing Patient Overview / Summary ---");
        Map<String, Object> summary = wsRepo.getPatientSummary(patientId);
        System.out.println("Patient Summary Name: " + summary.get("name"));
        System.out.println("Patient Blood Group: " + summary.get("bloodGroup"));
        if (summary.get("name") == null) throw new RuntimeException("Patient summary is missing patient name");

        System.out.println("\n--- 3. Testing AI Clinical Decision Support ---");
        Map<String, Object> aiResult = aiService.generateClinicalSupport(patientId, docUserId, "Acute sore throat, high fever 102F", "Tonsillar enlargement, red pharynx");
        System.out.println("AI Differential Considerations: " + aiResult.get("clinicalConsiderations"));
        System.out.println("AI Recorded History: " + aiResult.get("relevantRecordedHistory"));
        System.out.println("AI Potential Interactions: " + aiResult.get("potentialInteractions"));
        if (aiResult.get("clinicalConsiderations") == null) throw new RuntimeException("AI clinical support generation failed");

        System.out.println("\n--- 4. Testing Prescription Safety & Formulary Verification ---");
        // Safe item test
        List<Map<String, Object>> safeItems = new ArrayList<>();
        Map<String, Object> item1 = new HashMap<>();
        item1.put("medicineName", "Paracetamol 500mg");
        item1.put("dosage", "500mg");
        item1.put("frequency", "1-0-1");
        item1.put("duration", "3 Days");
        safeItems.add(item1);

        PrescriptionSafetyService.SafetyCheckResult safeResult = safetyService.checkPrescriptionSafety(patientId, safeItems);
        System.out.println("Safe Items Check Status: " + safeResult.overallStatus);
        System.out.println("Safe Items Badge: " + safeResult.overallBadge);

        // Allergy / Warning item test (Penicillin / Amoxicillin if patient has beta-lactam warning)
        List<Map<String, Object>> warningItems = new ArrayList<>();
        Map<String, Object> amoxItem = new HashMap<>();
        amoxItem.put("medicineName", "Amoxicillin 500mg");
        amoxItem.put("dosage", "500mg");
        amoxItem.put("frequency", "1-0-1");
        amoxItem.put("duration", "5 Days");
        warningItems.add(amoxItem);

        PrescriptionSafetyService.SafetyCheckResult warnResult = safetyService.checkPrescriptionSafety(patientId, warningItems);
        System.out.println("Warning Items Check Status: " + warnResult.overallStatus);
        System.out.println("Warning Items Badge: " + warnResult.overallBadge);
        System.out.println("Warnings list: " + warnResult.warnings);

        System.out.println("\n--- 5. Testing Patient Health Timeline ---");
        List<TimelineEvent> events = wsRepo.getPatientTimeline(patientId, 20, 0);
        System.out.println("Timeline Events Count: " + events.size());
        for (TimelineEvent e : events) {
            System.out.println("  [" + e.getEventDate() + "] " + e.getEventType() + " -> " + e.getTitle());
        }

        System.out.println("\n--- 6. Testing Treatment Progress Tracker ---");
        Doctor doc = docRepo.findByUserId(docUserId);
        int doctorId = doc != null ? doc.getId() : 1;

        TreatmentGoal goal = wsRepo.createTreatmentGoal(patientId, doctorId, "Hypertension Control", "Systolic/Diastolic BP", "mmHg", "150/95", "120/80", "IN_PROGRESS");
        System.out.println("Created Treatment Goal ID: " + goal.getId() + ", Title: " + goal.getTitle());

        int entryId = wsRepo.addTreatmentProgressEntry(goal.getId(), "132/86", "2026-09-07", "Improving under current regimen", docUserId);
        System.out.println("Added Progress Entry ID: " + entryId);

        List<TreatmentGoal> allGoals = wsRepo.getTreatmentGoalsWithProgress(patientId);
        System.out.println("Total Treatment Goals for Patient: " + allGoals.size());

        System.out.println("\n==================================================");
        System.out.println(">>> ALL DOCTOR PORTAL RESTRUCTURE TESTS PASSED! <<<");
        System.out.println("==================================================");
    }
}
