package com.medicarex;

import com.medicarex.config.DatabaseConnection;
import com.medicarex.model.Ambulance;
import com.medicarex.model.EmergencyRequest;
import com.medicarex.repository.EmergencyRepository;
import com.medicarex.repository.PatientFeatureRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PatientFeaturesAndAmbulanceTest {

    public static void main(String[] args) {
        System.out.println(">>> Running PatientFeaturesAndAmbulanceTest <<<");
        DatabaseConnection.initialize();

        PatientFeatureRepository patientRepo = new PatientFeatureRepository();
        EmergencyRepository emergencyRepo = new EmergencyRepository();

        // 1. Test Emergency Card
        System.out.println("\n--- Testing Emergency Health Card ---");
        Map<String, Object> cardData = new HashMap<>();
        cardData.put("bloodGroup", "O+");
        cardData.put("emergencyContactName", "Jane Doe");
        cardData.put("emergencyContactPhone", "9876543210");
        cardData.put("emergencyContactRelation", "Spouse");
        cardData.put("allergies", "Penicillin, Peanuts");
        cardData.put("chronicConditions", "Hypertension");
        cardData.put("currentMedicationsSummary", "Amlodipine 5mg OD");
        cardData.put("isOrganDonor", true);
        cardData.put("insuranceProvider", "Star Health");
        cardData.put("insurancePolicyNumber", "POL-998877");

        Map<String, Object> savedCard = patientRepo.saveEmergencyCard(1, cardData);
        System.out.println("Saved Emergency Card: " + savedCard);
        assert savedCard != null && "O+".equals(savedCard.get("bloodGroup")) : "Emergency card save failed!";
        String token = (String) savedCard.get("publicToken");
        System.out.println("Generated Public Token: " + token);

        Map<String, Object> publicCard = patientRepo.getPublicEmergencyCardByToken(token);
        System.out.println("Public QR Card Lookup: " + publicCard);
        assert publicCard != null && "O+".equals(publicCard.get("bloodGroup")) : "Public emergency card lookup failed!";

        // 2. Test Data Sharing
        System.out.println("\n--- Testing Data Sharing Grants ---");
        boolean grantOk = patientRepo.createOrUpdateSharingGrant(1, 2, "FULL_HISTORY", "Temporary consultation", "2026-12-31");
        System.out.println("Create Sharing Grant: " + grantOk);
        assert grantOk : "Failed to create sharing grant!";

        List<Map<String, Object>> grants = patientRepo.getSharingGrants(1);
        System.out.println("Grants count: " + grants.size());
        assert !grants.isEmpty() : "Grants list is empty!";

        patientRepo.logDataAccess(1, 2, "PRESCRIPTION", 101, "VIEW", "127.0.0.1");
        List<Map<String, Object>> logs = patientRepo.getDataAccessLogs(1);
        System.out.println("Access Logs count: " + logs.size());
        assert !logs.isEmpty() : "Access logs empty!";

        // 3. Test Visit Prep Notes
        System.out.println("\n--- Testing Doctor Visit Prep ---");
        Map<String, Object> prep = new HashMap<>();
        prep.put("primarySymptoms", "Persistent dry cough and mild fever");
        prep.put("symptomDuration", "4 days");
        prep.put("questionsForDoctor", "Should I take an antibiotic? Can I continue gym?");
        prep.put("currentVitalsNotes", "BP 120/80, Temp 99.1F");
        boolean prepOk = patientRepo.saveVisitPreparation(1, prep);
        System.out.println("Save Visit Prep: " + prepOk);
        assert prepOk : "Failed to save visit preparation!";

        List<Map<String, Object>> preps = patientRepo.getVisitPreparations(1);
        System.out.println("Visit Preps count: " + preps.size());
        assert !preps.isEmpty() : "Visit preps list empty!";

        // 4. Test Medicine Supply & Expenses
        System.out.println("\n--- Testing Medicine Supply & Expenses ---");
        List<Map<String, Object>> supply = patientRepo.getMedicineSupplyStatus(1);
        System.out.println("Medicine Supply items: " + supply.size());

        Map<String, Object> expenses = patientRepo.getMedicalExpensesSummary(1, "2026");
        System.out.println("Medical Expenses Summary: " + expenses.get("totalSpent"));

        List<Map<String, Object>> purchases = patientRepo.getMedicinePurchaseHistory(1);
        System.out.println("Purchase history bills: " + purchases.size());

        // 5. Test Emergency & Ambulance System
        System.out.println("\n--- Testing Emergency & Ambulance Dispatch ---");
        // Ensure at least one available ambulance exists
        List<Ambulance> fleet = emergencyRepo.getAllAmbulances();
        System.out.println("Current Ambulance Fleet: " + fleet.size());
        if (fleet.isEmpty()) {
            Ambulance a = new Ambulance();
            a.setVehicleNumber("TN-01-AM-1008");
            a.setVehicleType("ADVANCED_LIFE_SUPPORT");
            a.setDriverName("Ramesh Kumar");
            a.setDriverPhone("9840123456");
            a.setStatus("AVAILABLE");
            a.setCurrentLatitude(13.0827);
            a.setCurrentLongitude(80.2707);
            a.setHospitalAffiliation("Apollo Main Hospital");
            a.setEquipmentSummary("Ventilator, Defibrillator, Oxygen Cylinder, ECG Monitor");
            emergencyRepo.createAmbulance(a);
            fleet = emergencyRepo.getAllAmbulances();
            System.out.println("Created test ambulance. Fleet size: " + fleet.size());
        }

        // Dispatch emergency request
        EmergencyRequest emReq = new EmergencyRequest();
        emReq.setPatientId(1);
        emReq.setEmergencyType("CARDIAC");
        emReq.setSeverity("CRITICAL");
        emReq.setPickupLatitude(13.0850);
        emReq.setPickupLongitude(80.2720);
        emReq.setPickupAddress("Flat 4B, Green Towers, Anna Nagar, Chennai");
        emReq.setLandmark("Near Tower Park");
        emReq.setContactPhone("9876543210");
        emReq.setPatientNotes("Chest pain radiating to left arm");

        EmergencyRequest created = emergencyRepo.createRequest(emReq);
        System.out.println("Created Emergency Request ID: " + (created != null ? created.getId() : "FAILED"));
        assert created != null : "Emergency dispatch failed!";
        System.out.println("Status: " + created.getStatus() + ", Assigned Ambulance: " + created.getAmbulanceVehicleNumber() + ", ETA: " + created.getEstimatedArrivalMinutes() + " mins");

        // Advance through status state machine
        int reqId = created.getId();
        boolean s1 = emergencyRepo.updateRequestStatus(reqId, "ACCEPTED", null);
        boolean s2 = emergencyRepo.updateRequestStatus(reqId, "EN_ROUTE", null);
        boolean s3 = emergencyRepo.updateRequestStatus(reqId, "ARRIVED", null);
        boolean s4 = emergencyRepo.updateRequestStatus(reqId, "PATIENT_PICKED_UP", null);
        boolean s5 = emergencyRepo.updateRequestStatus(reqId, "TRANSPORTING", null);
        boolean s6 = emergencyRepo.updateRequestStatus(reqId, "ARRIVED_AT_HOSPITAL", null);
        boolean s7 = emergencyRepo.updateRequestStatus(reqId, "COMPLETED", null);

        System.out.println("Status Progression Results: " + s1 + " " + s2 + " " + s3 + " " + s4 + " " + s5 + " " + s6 + " " + s7);
        assert s7 : "Status progression to COMPLETED failed!";

        EmergencyRequest completedReq = emergencyRepo.getRequestById(reqId);
        System.out.println("Final Request Status: " + completedReq.getStatus() + ", Completed At: " + completedReq.getCompletedAt());
        assert "COMPLETED".equals(completedReq.getStatus()) : "Final status not COMPLETED!";

        Map<String, Object> stats = emergencyRepo.getEmergencyStats();
        System.out.println("Emergency Stats: " + stats);

        System.out.println("\n>>> ALL PATIENT FEATURES & EMERGENCY AMBULANCE TESTS PASSED! <<<");
    }
}
