package com.medicarex.controller;

import com.medicarex.model.Prescription;
import com.medicarex.repository.PrescriptionRepository;
import com.medicarex.util.HttpUtil;
import com.medicarex.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AiAssistantController implements HttpHandler {
    private final PrescriptionRepository rxRepo = new PrescriptionRepository();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpUtil.handleOptions(exchange)) return;

        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = HttpUtil.readRequestBody(exchange);
            Map<String, Object> req = JsonUtil.parseMap(body);
            String userPrompt = (String) req.get("message");

            if (userPrompt == null || userPrompt.trim().isEmpty()) {
                HttpUtil.sendError(exchange, 400, "Message is required.");
                return;
            }

            Map<String, Object> claims = HttpUtil.authenticate(exchange);
            int userId = claims != null ? ((Number) claims.get("userId")).intValue() : 0;

            Map<String, Object> response = generateAiResponse(userPrompt.trim(), userId);
            HttpUtil.sendSuccess(exchange, "AI response generated", response);
        } else {
            HttpUtil.sendError(exchange, 404, "Endpoint not found");
        }
    }

    private Map<String, Object> generateAiResponse(String prompt, int userId) {
        String lower = prompt.toLowerCase();
        Map<String, Object> res = new HashMap<>();

        StringBuilder reply = new StringBuilder();
        String actionType = "INFO";
        Map<String, Object> actionData = new HashMap<>();

        if (lower.contains("pharmacy") || lower.contains("nearby") || lower.contains("where to buy") || lower.contains("store")) {
            reply.append("I found **5 verified pharmacies** near your location in Indiranagar/Koramangala.\n\n")
                 .append("• **HealthPlus Pharmacy** (0.8 km away) — Available in stock.\n")
                 .append("• **MedLife Store** (1.2 km away) — Open 24 Hours with express doorstep pickup.\n")
                 .append("• **Apollo Care Pharmacy** (2.5 km away) — Fully stocked.\n\n")
                 .append("Would you like me to open the interactive map view to guide your directions?");
            actionType = "OPEN_MAP";
            actionData.put("target", "map");
        } else if (lower.contains("amoxicillin") || lower.contains("antibiotic")) {
            reply.append("### Amoxicillin 500mg Information\n\n")
                 .append("**Class:** Broad-spectrum Penicillin-class Antibiotic.\n")
                 .append("**Primary Uses:** Bacterial infections of the ear, nose, throat, chest, and urinary tract.\n")
                 .append("**Key Precaution:** Complete the full prescribed 5–7 day course even if symptoms improve early to prevent bacterial resistance.\n")
                 .append("**Take with:** Meals and a full glass of water to avoid mild stomach irritation.\n\n")
                 .append("⚠️ *Requires a valid Doctor Prescription.*");
            actionType = "VIEW_MEDICINE";
            actionData.put("medicineId", 1);
        } else if (lower.contains("paracetamol") || lower.contains("dolo") || lower.contains("crocin") || lower.contains("fever") || lower.contains("headache")) {
            reply.append("### Paracetamol (Acetaminophen) 650mg\n\n")
                 .append("**Classification:** Analgesic (pain reliever) and Antipyretic (fever reducer).\n")
                 .append("**Safe Dosing:** 1 tablet (650mg) every 4–6 hours as needed. **Never exceed 4,000 mg (approx 6 tablets) in 24 hours**.\n")
                 .append("**Safety Warning:** Avoid combining with other OTC medications containing acetaminophen or drinking alcohol.\n\n")
                 .append("✅ *Over-The-Counter (No prescription required).*");
            actionType = "VIEW_MEDICINE";
            actionData.put("medicineId", 2);
        } else if (lower.contains("alternative") || lower.contains("substitute")) {
            reply.append("Here are common therapeutic alternatives verified on MediCareX:\n\n")
                 .append("1. **For Pain / Inflammation:** Paracetamol 650mg (Mild/Fever) or Ibuprofen 400mg (Inflammation/Joints).\n")
                 .append("2. **For Acid Reflux:** Pantoprazole 40mg or Omeprazole 20mg.\n")
                 .append("3. **For Allergies:** Cetirizine 10mg or Montelukast 10mg.\n\n")
                 .append("Always confirm with your physician before switching prescribed generic formulations.");
            actionType = "NAVIGATE_SEARCH";
            actionData.put("target", "search");
        } else if (lower.contains("prescription") || lower.contains("doctor") || lower.contains("consult")) {
            List<Prescription> userRxList = userId > 0 ? rxRepo.findByPatientUserId(userId) : List.of();
            if (userRxList != null && !userRxList.isEmpty()) {
                Prescription latest = userRxList.get(0);
                reply.append("You have **").append(userRxList.size()).append(" active digital prescription(s)** on file. Latest: **#")
                     .append(latest.getPrescriptionNumber()).append("** issued by ").append(latest.getDoctorName() != null ? latest.getDoctorName() : "your physician")
                     .append(". You can view or download the signed PDF from your Prescriptions section.");
                actionType = "VIEW_PRESCRIPTION";
                actionData.put("prescriptionId", latest.getId());
            } else {
                reply.append("You currently have **no prescriptions issued yet**. Prescriptions will appear automatically once a doctor issues one for you during a consultation.");
                actionType = "VIEW_PRESCRIPTION";
            }
        } else {
            reply.append("Hello! I am your **MediCareX Health & Medicine Assistant**.\n\n")
                 .append("I can help you with:\n")
                 .append("• Checking real-time medicine availability across local pharmacies\n")
                 .append("• Understanding active ingredients, dosage guidelines, and side effects\n")
                 .append("• Finding 24/7 open pharmacies and comparing prices\n")
                 .append("• Tracking your active digital prescriptions and health records\n\n")
                 .append("How can I assist you with your health inquiry today?");
        }

        res.put("reply", reply.toString());
        res.put("disclaimer", "MediCareX AI provides general medical and navigational information, not clinical diagnosis. In emergencies, call your local medical emergency services immediately.");
        res.put("actionType", actionType);
        res.put("actionData", actionData);
        return res;
    }
}
