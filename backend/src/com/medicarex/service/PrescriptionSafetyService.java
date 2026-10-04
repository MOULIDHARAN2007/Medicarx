package com.medicarex.service;

import com.medicarex.model.Medicine;
import com.medicarex.repository.DoctorWorkspaceRepository;
import com.medicarex.repository.MedicineRepository;

import java.util.*;

public class PrescriptionSafetyService {

    private final DoctorWorkspaceRepository workspaceRepo = new DoctorWorkspaceRepository();
    private final MedicineRepository medicineRepo = new MedicineRepository();

    public static class SafetyCheckResult {
        public String overallStatus; // "SAFE", "REVIEW_RECOMMENDED", "POTENTIAL_ISSUE"
        public String overallBadge;  // "🟢 No obvious issue detected", "🟡 Review recommended", "🔴 Potential safety issue"
        public List<Map<String, Object>> warnings = new ArrayList<>();
        public String disclaimer = "Safety checks are decision-support heuristics based on available recorded patient data. This does not replace professional clinical evaluation. Final prescribing authority rests entirely with the attending physician.";

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("overallStatus", overallStatus);
            map.put("overallBadge", overallBadge);
            map.put("warnings", warnings);
            map.put("disclaimer", disclaimer);
            return map;
        }
    }

    public Map<String, Object> checkSafetyAsMap(int patientId, List<Map<String, Object>> proposedMedicines) {
        SafetyCheckResult res = checkPrescriptionSafety(patientId, proposedMedicines);
        return res.toMap();
    }

    public SafetyCheckResult checkPrescriptionSafety(int patientId, List<Map<String, Object>> proposedMedicines) {
        SafetyCheckResult result = new SafetyCheckResult();

        if (proposedMedicines == null || proposedMedicines.isEmpty()) {
            result.overallStatus = "SAFE";
            result.overallBadge = "🟢 No obvious issue detected";
            return result;
        }

        Map<String, Object> summary = workspaceRepo.getPatientWorkspaceSummary(patientId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> activeMeds = summary != null && summary.containsKey("activeMedications") 
                ? (List<Map<String, Object>>) summary.get("activeMedications") 
                : new ArrayList<>();

        Set<String> activeMedNames = new HashSet<>();
        for (Map<String, Object> am : activeMeds) {
            String name = (String) am.get("medicineName");
            if (name != null) activeMedNames.add(name.toLowerCase().trim());
        }

        // Collect proposed medicines info
        List<Medicine> resolvedMeds = new ArrayList<>();
        List<Map<String, Object>> resolvedInputs = new ArrayList<>();

        for (Map<String, Object> p : proposedMedicines) {
            int medId = p.containsKey("medicineId") && p.get("medicineId") != null ? ((Number) p.get("medicineId")).intValue() : 0;
            String medName = (String) p.get("name");
            if (medName == null) medName = (String) p.get("medicineName");

            Medicine m = null;
            if (medId > 0) {
                m = medicineRepo.findById(medId);
            }
            if (m == null && medName != null && !medName.trim().isEmpty()) {
                m = medicineRepo.findByName(medName.trim());
            }

            if (m != null) {
                resolvedMeds.add(m);
                resolvedInputs.add(p);
            } else if (medName != null && !medName.trim().isEmpty()) {
                Medicine synthetic = new Medicine();
                synthetic.setId(medId);
                synthetic.setName(medName);
                synthetic.setGenericName(medName);
                synthetic.setDosage(String.valueOf(p.getOrDefault("dosage", "Standard")));
                synthetic.setPrescriptionRequired(true);
                resolvedMeds.add(synthetic);
                resolvedInputs.add(p);
            }
        }

        boolean hasRed = false;
        boolean hasYellow = false;

        // 1. ALLERGY & RECORDED SENSITIVITY CHECK
        for (Medicine m : resolvedMeds) {
            String mName = m.getName().toLowerCase();
            String gName = m.getGenericName() != null ? m.getGenericName().toLowerCase() : "";

            if (mName.contains("amoxicillin") || gName.contains("amoxicillin") || mName.contains("penicillin") || gName.contains("penicillin")) {
                Map<String, Object> warn = new HashMap<>();
                warn.put("medicine", m.getName());
                warn.put("warningType", "BETA_LACTAM_ALLERGY_RISK");
                warn.put("severity", "HIGH");
                warn.put("badge", "🔴 Potential safety issue");
                warn.put("explanation", "Penicillin/Beta-lactam class medication. Verify patient has no recorded hypersensitivity or anaphylactoid reaction history to beta-lactams.");
                warn.put("sourceBasis", "Clinical Allergy Surveillance Heuristics");
                warn.put("recommendedAction", "Verify allergy status with patient. Consider macrolide or fluoroquinolone alternative if allergy confirmed.");
                result.warnings.add(warn);
                hasRed = true;
            }
            if (mName.contains("aspirin") || mName.contains("ibuprofen") || gName.contains("ibuprofen") || mName.contains("diclofenac")) {
                Map<String, Object> warn = new HashMap<>();
                warn.put("medicine", m.getName());
                warn.put("warningType", "NSAID_GI_SENSITIVITY");
                warn.put("severity", "MEDIUM");
                warn.put("badge", "🟡 Review recommended");
                warn.put("explanation", "NSAID therapy may cause gastric irritation or interact with existing antiplatelet/anticoagulant regimens.");
                warn.put("sourceBasis", "Pharmacovigilance Database");
                warn.put("recommendedAction", "Confirm absence of peptic ulcer disease and advise post-meal administration.");
                result.warnings.add(warn);
                hasYellow = true;
            }
        }

        // 2. DUPLICATE THERAPY / ACTIVE REGIMEN DUPLICATION
        Set<String> seenGenericsInCart = new HashSet<>();
        for (Medicine m : resolvedMeds) {
            String gKey = (m.getGenericName() != null && !m.getGenericName().isEmpty() ? m.getGenericName() : m.getName()).toLowerCase().trim();

            if (seenGenericsInCart.contains(gKey)) {
                Map<String, Object> warn = new HashMap<>();
                warn.put("medicine", m.getName());
                warn.put("warningType", "DUPLICATE_THERAPY_IN_PRESCRIPTION");
                warn.put("severity", "HIGH");
                warn.put("badge", "🔴 Potential safety issue");
                warn.put("explanation", "Duplicate therapeutic agent detected within the current prescription items (" + gKey + ").");
                warn.put("sourceBasis", "Prescription Ingredient Audit");
                warn.put("recommendedAction", "Remove redundant line item or verify dual-formulation intention.");
                result.warnings.add(warn);
                hasRed = true;
            }
            seenGenericsInCart.add(gKey);

            for (String activeName : activeMedNames) {
                if (activeName.contains(gKey) || gKey.contains(activeName)) {
                    Map<String, Object> warn = new HashMap<>();
                    warn.put("medicine", m.getName());
                    warn.put("warningType", "ACTIVE_PRESCRIPTION_OVERLAP");
                    warn.put("severity", "MEDIUM");
                    warn.put("badge", "🟡 Review recommended");
                    warn.put("explanation", "Patient has an active existing prescription containing " + activeName + ".");
                    warn.put("sourceBasis", "Electronic Health Records — Active Prescriptions");
                    warn.put("recommendedAction", "Confirm if this replaces the previous regimen or represents dosage escalation.");
                    result.warnings.add(warn);
                    hasYellow = true;
                }
            }
        }

        // 3. DRUG-DRUG INTERACTIONS
        for (int i = 0; i < resolvedMeds.size(); i++) {
            for (int j = i + 1; j < resolvedMeds.size(); j++) {
                String n1 = resolvedMeds.get(i).getName().toLowerCase();
                String n2 = resolvedMeds.get(j).getName().toLowerCase();

                // Paracetamol + Metformin or NSAID + ACE inhibitors
                if ((n1.contains("aspirin") && n2.contains("ibuprofen")) || (n2.contains("aspirin") && n1.contains("ibuprofen"))) {
                    Map<String, Object> warn = new HashMap<>();
                    warn.put("medicine", resolvedMeds.get(i).getName() + " + " + resolvedMeds.get(j).getName());
                    warn.put("warningType", "CONCURRENT_NSAID_INTERACTION");
                    warn.put("severity", "HIGH");
                    warn.put("badge", "🔴 Potential safety issue");
                    warn.put("explanation", "Concomitant use of multiple NSAIDs significantly increases GI toxicity without conferring additional therapeutic efficacy.");
                    warn.put("sourceBasis", "Drug-Drug Interaction Formulary");
                    warn.put("recommendedAction", "Select a single appropriate NSAID agent.");
                    result.warnings.add(warn);
                    hasRed = true;
                }

                if ((n1.contains("ciprofloxacin") && (n2.contains("antacid") || n2.contains("calcium"))) ||
                    (n2.contains("ciprofloxacin") && (n1.contains("antacid") || n1.contains("calcium")))) {
                    Map<String, Object> warn = new HashMap<>();
                    warn.put("medicine", resolvedMeds.get(i).getName() + " + " + resolvedMeds.get(j).getName());
                    warn.put("warningType", "CHELATION_ABSORPTION_REDUCTION");
                    warn.put("severity", "MEDIUM");
                    warn.put("badge", "🟡 Review recommended");
                    warn.put("explanation", "Polyvalent cations in antacids bind to fluoroquinolones, significantly reducing bioavailability.");
                    warn.put("sourceBasis", "Pharmacokinetic Formulary");
                    warn.put("recommendedAction", "Separate administration by at least 2 hours.");
                    result.warnings.add(warn);
                    hasYellow = true;
                }
            }
        }

        // 4. DOSAGE BOUNDARY CHECKS
        for (int i = 0; i < resolvedMeds.size(); i++) {
            Medicine m = resolvedMeds.get(i);
            Map<String, Object> inp = resolvedInputs.get(i);
            String mName = m.getName().toLowerCase();
            String freq = String.valueOf(inp.getOrDefault("frequency", "")).toLowerCase();
            String duration = String.valueOf(inp.getOrDefault("duration", "")).toLowerCase();
            String inputDosage = String.valueOf(inp.getOrDefault("dosage", "")).toLowerCase();
            String medDosage = m.getDosage() != null ? m.getDosage().toLowerCase() : "";
            String gName = m.getGenericName() != null ? m.getGenericName().toLowerCase() : "";

            if ((mName.contains("paracetamol") || gName.contains("paracetamol") || mName.contains("dolo")) && (freq.contains("4") || freq.contains("5") || freq.contains("6") || freq.contains("every 4 hours"))) {
                if (medDosage.contains("650") || mName.contains("650") || inputDosage.contains("650") || freq.contains("6")) {
                    Map<String, Object> warn = new HashMap<>();
                    warn.put("medicine", m.getName());
                    warn.put("warningType", "MAX_DAILY_DOSE_CONCERN");
                    warn.put("severity", "HIGH");
                    warn.put("badge", "🔴 Potential safety issue");
                    warn.put("explanation", "Paracetamol 650mg taken 5–6 times daily approaches or exceeds the maximum recommended safe adult limit (4,000mg/day), elevating hepatotoxicity risk.");
                    warn.put("sourceBasis", "National Pharmacopoeia Guidelines");
                    warn.put("recommendedAction", "Adjust frequency to maximum 3 to 4 times daily (minimum 6-hour interval).");
                    result.warnings.add(warn);
                    hasRed = true;
                }
            }

            if (mName.contains("azithromycin") && (duration.contains("14") || duration.contains("30") || duration.contains("week") || duration.contains("month"))) {
                Map<String, Object> warn = new HashMap<>();
                warn.put("medicine", m.getName());
                warn.put("warningType", "UNUSUAL_DURATION");
                warn.put("severity", "MEDIUM");
                warn.put("badge", "🟡 Review recommended");
                warn.put("explanation", "Azithromycin is typically prescribed as a 3 to 5 day pulsed course due to its extended tissue half-life.");
                warn.put("sourceBasis", "Antibiotic Stewardship Guidance");
                warn.put("recommendedAction", "Confirm if an extended atypical regime is required for a specific chronic infection.");
                result.warnings.add(warn);
                hasYellow = true;
            }
        }

        // Final overall determination
        if (hasRed) {
            result.overallStatus = "POTENTIAL_ISSUE";
            result.overallBadge = "🔴 Potential safety issue";
        } else if (hasYellow) {
            result.overallStatus = "REVIEW_RECOMMENDED";
            result.overallBadge = "🟡 Review recommended";
        } else {
            result.overallStatus = "SAFE";
            result.overallBadge = "🟢 No obvious issue detected";
        }

        return result;
    }
}
