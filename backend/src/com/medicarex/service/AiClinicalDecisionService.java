package com.medicarex.service;

import com.medicarex.repository.DoctorWorkspaceRepository;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AiClinicalDecisionService {

    private final DoctorWorkspaceRepository workspaceRepo = new DoctorWorkspaceRepository();

    public static class ClinicalDecisionResult {
        public String patientName;
        public String patientGender;
        public String patientAge;
        public String patientDob;
        public String bloodGroup;
        public String primaryCategory;
        public String urgencyLevel; // ROUTINE, SEMI_URGENT, URGENT, CRITICAL_CONSIDERATION

        // Analyzed input echo for UI verification
        public Map<String, Object> analyzedInput = new LinkedHashMap<>();

        // 15 Comprehensive Target Categories
        public String clinicalSummary;
        public List<String> keySymptoms = new ArrayList<>();
        public List<String> symptomPattern = new ArrayList<>();
        public List<String> differentialConsiderations = new ArrayList<>();
        public List<String> redFlagScreening = new ArrayList<>();
        public String vitalSignsInterpretation;
        public String relevantPatientHistory;
        public List<String> medicationReview = new ArrayList<>();
        public List<String> allergySafetyConsiderations = new ArrayList<>();
        public List<String> recommendedClinicalEvaluation = new ArrayList<>();
        public List<String> clinicalDecisionPoints = new ArrayList<>();
        public String severityUrgencyAssessment;
        public List<String> followUpConsiderations = new ArrayList<>();
        public List<String> patientSpecificRiskFactors = new ArrayList<>(); // Section 14: Relevant Patient Factors
        public List<String> questionsForDoctor = new ArrayList<>();

        // Backward compatibility mappings
        public String clinicalConsiderations;
        public String medicationConcerns;
        public String drugInteractionConcerns;
        public List<String> missingInformation = new ArrayList<>();

        // Active dynamically ranked sections
        public List<Map<String, Object>> activeSections = new ArrayList<>();

        public String disclaimer = "AI Clinical Decision Support is an assistive analytical tool. It does not make diagnoses, alter records, or prescribe medications. Clinical decisions remain the responsibility of the attending physician.";
        public String generatedAt = new Date().toString();

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("patientName", patientName);
            map.put("patientGender", patientGender);
            map.put("patientAge", patientAge);
            map.put("patientDob", patientDob);
            map.put("bloodGroup", bloodGroup);
            map.put("primaryCategory", primaryCategory);
            map.put("urgencyLevel", urgencyLevel);
            map.put("analyzedInput", analyzedInput);

            // 15 target categories
            map.put("clinicalSummary", clinicalSummary);
            map.put("keySymptoms", keySymptoms);
            map.put("symptomPattern", symptomPattern);
            map.put("differentialConsiderations", differentialConsiderations);
            map.put("redFlagScreening", redFlagScreening);
            map.put("vitalSignsInterpretation", vitalSignsInterpretation);
            map.put("relevantPatientHistory", relevantPatientHistory);
            map.put("medicationReview", medicationReview);
            map.put("allergySafetyConsiderations", allergySafetyConsiderations);
            map.put("recommendedClinicalEvaluation", recommendedClinicalEvaluation);
            map.put("clinicalDecisionPoints", clinicalDecisionPoints);
            map.put("severityUrgencyAssessment", severityUrgencyAssessment);
            map.put("followUpConsiderations", followUpConsiderations);
            map.put("patientSpecificRiskFactors", patientSpecificRiskFactors);
            map.put("relevantPatientFactors", patientSpecificRiskFactors);
            map.put("questionsForDoctor", questionsForDoctor);

            // Backward compatibility aliases
            map.put("clinicalConsiderations", clinicalConsiderations != null ? clinicalConsiderations : String.join("\n", differentialConsiderations));
            map.put("medicationConcerns", medicationConcerns != null ? medicationConcerns : String.join("\n", medicationReview));
            map.put("drugInteractionConcerns", drugInteractionConcerns != null ? drugInteractionConcerns : String.join("\n", allergySafetyConsiderations));
            map.put("missingInformation", !missingInformation.isEmpty() ? missingInformation : questionsForDoctor);
            map.put("importantQuestions", questionsForDoctor);

            map.put("activeSections", activeSections);
            map.put("disclaimer", disclaimer);
            map.put("generatedAt", generatedAt);
            return map;
        }
    }

    public Map<String, Object> generateClinicalSupport(int patientId, int doctorId, String symptoms, String consultationNotes) {
        return generateClinicalSupport(patientId, doctorId, symptoms, consultationNotes, new HashMap<>());
    }

    public Map<String, Object> generateClinicalSupport(int patientId, int doctorId, String symptoms, String consultationNotes, Map<String, Object> vitals) {
        ClinicalDecisionResult res = generateDecisionSupport(doctorId, patientId, symptoms, consultationNotes, vitals);
        return res.toMap();
    }

    public ClinicalDecisionResult generateDecisionSupport(int doctorId, int patientId, String symptoms, String consultationNotes, String provisionalDiagnosis) {
        String combinedNotes = (consultationNotes != null ? consultationNotes : "") + (provisionalDiagnosis != null ? " Provisional diagnosis: " + provisionalDiagnosis : "");
        return generateDecisionSupport(doctorId, patientId, symptoms, combinedNotes, new HashMap<>());
    }

    public ClinicalDecisionResult generateDecisionSupport(int doctorId, int patientId, String symptoms, String consultationNotes, Map<String, Object> vitals) {
        Map<String, Object> summary = workspaceRepo.getPatientWorkspaceSummary(patientId);
        ClinicalDecisionResult res = new ClinicalDecisionResult();

        if (summary == null) {
            res.clinicalSummary = "Patient profile could not be retrieved from electronic health records.";
            return res;
        }

        String patientName = (String) summary.get("fullName");
        String gender = (String) summary.get("gender");
        String dob = (String) summary.get("dob");
        String bloodGroup = (String) summary.get("bloodGroup");
        String knownAllergies = (String) summary.get("allergies");
        String chronicConditions = (String) summary.get("chronicConditions");

        res.patientName = patientName != null ? patientName : "Patient";
        res.patientGender = gender != null ? gender : "Not Specified";
        res.patientDob = dob != null ? dob : "Not Recorded";
        res.bloodGroup = bloodGroup != null ? bloodGroup : "Not Recorded";

        // Calculate approximate age if DOB available
        if (dob != null && dob.length() >= 4) {
            try {
                int birthYear = Integer.parseInt(dob.substring(0, 4));
                int age = 2026 - birthYear;
                res.patientAge = age + " years";
            } catch (Exception e) {
                res.patientAge = "Adult";
            }
        } else {
            res.patientAge = "Adult";
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> activeMeds = summary.containsKey("activeMedications") ? (List<Map<String, Object>>) summary.get("activeMedications") : new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> recentAppts = summary.containsKey("recentAppointments") ? (List<Map<String, Object>>) summary.get("recentAppointments") : new ArrayList<>();

        String symRaw = symptoms != null ? symptoms.trim() : "";
        String notesRaw = consultationNotes != null ? consultationNotes.trim() : "";
        String symLower = symRaw.toLowerCase();
        String notesLower = notesRaw.toLowerCase();
        String combinedText = (symLower + " " + notesLower).trim();

        // Populate analyzedInput echo
        res.analyzedInput.put("patientId", patientId);
        res.analyzedInput.put("patientName", res.patientName);
        res.analyzedInput.put("symptoms", symRaw.isEmpty() ? "Not provided" : symRaw);
        res.analyzedInput.put("consultationNotes", notesRaw.isEmpty() ? "Not provided" : notesRaw);

        Map<String, String> vitalsEcho = new LinkedHashMap<>();
        vitalsEcho.put("bloodPressure", getVitalDisplay(vitals, "bloodPressure", "bp"));
        vitalsEcho.put("temperature", getVitalDisplay(vitals, "temperature", "temp"));
        vitalsEcho.put("heartRate", getVitalDisplay(vitals, "heartRate", "pulse"));
        vitalsEcho.put("oxygenSaturation", getVitalDisplay(vitals, "oxygenSaturation", "spo2"));
        vitalsEcho.put("respiratoryRate", getVitalDisplay(vitals, "respiratoryRate", "rr"));
        res.analyzedInput.put("vitals", vitalsEcho);

        // -------------------------------------------------------------
        // DURATION EXTRACTION FROM INPUT
        // -------------------------------------------------------------
        String extractedDuration = extractDuration(symLower, notesLower);

        // -------------------------------------------------------------
        // GRANULAR SYMPTOM DETECTION (STRICTLY FROM CURRENT INPUT)
        // -------------------------------------------------------------
        boolean hasFever = combinedText.contains("fever") || combinedText.contains("febrile") || combinedText.contains("pyrexia") || hasTemperatureValue(vitals);
        boolean hasCold = combinedText.contains("cold") || combinedText.contains("coryza") || combinedText.contains("rhinorrhea") || combinedText.contains("nasal congestion") || combinedText.contains("runny nose") || combinedText.contains("sneez");
        boolean hasBodyPain = combinedText.contains("body pain") || combinedText.contains("body ache") || combinedText.contains("myalgia") || combinedText.contains("generalized pain") || combinedText.contains("generalized ache");
        boolean hasSoreThroat = combinedText.contains("sore throat") || combinedText.contains("throat pain") || combinedText.contains("pharyngitis") || combinedText.contains("tonsil") || combinedText.contains("pharyngeal");
        boolean hasSwallowingDifficulty = combinedText.contains("swallow") || combinedText.contains("odynophagia") || combinedText.contains("dysphagia");
        boolean hasCough = combinedText.contains("cough") || combinedText.contains("sputum") || combinedText.contains("phlegm");
        boolean hasDyspnea = combinedText.contains("shortness of breath") || combinedText.contains("dyspnea") || combinedText.contains("breathlessness") || combinedText.contains("difficulty breathing") || combinedText.contains("wheez");
        boolean hasHeadache = combinedText.contains("headache") || combinedText.contains("migraine") || combinedText.contains("cephalea") || combinedText.contains("head pain");
        boolean hasPhotophobia = combinedText.contains("photophobia") || combinedText.contains("light sensitive") || combinedText.contains("sensitivity to bright light") || combinedText.contains("sensitivity to light");
        boolean hasNausea = combinedText.contains("nausea") || combinedText.contains("nauseated") || combinedText.contains("feeling sick");
        boolean hasVomiting = combinedText.contains("vomit") || combinedText.contains("emesis") || combinedText.contains("throwing up");
        boolean hasAnkleInjury = combinedText.contains("ankle") || (combinedText.contains("foot") && combinedText.contains("pain"));
        boolean hasTraumaOrSlip = combinedText.contains("slip") || combinedText.contains("fall") || combinedText.contains("twist") || combinedText.contains("sprain") || combinedText.contains("trauma") || combinedText.contains("injury") || combinedText.contains("accident");
        boolean hasSwelling = combinedText.contains("swell") || combinedText.contains("edema") || combinedText.contains("inflamm");
        boolean hasChestPain = combinedText.contains("chest pain") || combinedText.contains("substernal") || combinedText.contains("chest discomfort") || combinedText.contains("chest pressure") || combinedText.contains("chest tight") || combinedText.contains("angina");
        boolean hasPalpitations = combinedText.contains("palpitation") || combinedText.contains("racing heart") || combinedText.contains("flutter");
        boolean hasAbdominalPain = combinedText.contains("abdom") || combinedText.contains("stomach pain") || combinedText.contains("belly pain") || combinedText.contains("epigastr") || combinedText.contains("cramp");
        boolean hasDiarrhea = combinedText.contains("diarrhea") || combinedText.contains("loose motion") || combinedText.contains("loose stool");
        boolean hasBackPain = combinedText.contains("back pain") || combinedText.contains("lumbar") || combinedText.contains("sciatica") || combinedText.contains("low back") || combinedText.contains("spine pain");
        boolean hasSkinRash = combinedText.contains("rash") || combinedText.contains("skin lesion") || combinedText.contains("urticaria") || combinedText.contains("hives") || combinedText.contains("wheal") || combinedText.contains("dermatitis") || combinedText.contains("eruption");
        boolean hasItching = combinedText.contains("itch") || combinedText.contains("prurit");
        boolean hasUrinary = combinedText.contains("urina") || combinedText.contains("dysuria") || combinedText.contains("burning micturition") || combinedText.contains("micturition") || combinedText.contains("bladder") || combinedText.contains("suprapubic") || combinedText.contains("burning sensation while urinating");
        
        // Dizziness specifics
        boolean hasDizziness = combinedText.contains("dizz") || combinedText.contains("lighthead") || combinedText.contains("giddiness");
        boolean hasPositionalStanding = combinedText.contains("standing") || combinedText.contains("sitting to standing") || combinedText.contains("postural") || combinedText.contains("orthostat") || combinedText.contains("standing up");
        boolean hasTrueVertigo = combinedText.contains("vertigo") || combinedText.contains("spinning") || combinedText.contains("turning in bed") || combinedText.contains("head movement") || combinedText.contains("room spinning");
        boolean hasNoLOC = combinedText.contains("no loss of consciousness") || combinedText.contains("no loc") || combinedText.contains("without loss of consciousness");
        boolean hasFatigue = combinedText.contains("fatigue") || combinedText.contains("letharg") || combinedText.contains("tired") || combinedText.contains("weakness") || combinedText.contains("malaise");

        // Allergy screen
        boolean hasPenicillinAllergy = knownAllergies != null && knownAllergies.toLowerCase().contains("penicillin");

        // Category Classification Scoring
        int scoreMusculo = (hasAnkleInjury ? 8 : 0) + (hasTraumaOrSlip ? 6 : 0) + (hasSwelling && hasAnkleInjury ? 4 : 0);
        int scoreNeuro = (hasHeadache ? 8 : 0) + (hasPhotophobia ? 5 : 0) + (hasNausea && hasHeadache ? 4 : 0);
        int scoreRespUpper = (hasCold ? 7 : 0) + ((hasSoreThroat || hasSwallowingDifficulty) ? 7 : 0) + (hasFever && !hasChestPain && !hasAbdominalPain && !hasUrinary && !hasBackPain && !hasAnkleInjury && !hasDizziness ? 6 : 0) + ((hasBodyPain || hasFatigue) && (hasCold || hasFever) ? 4 : 0);
        int scoreRespLower = (hasCough ? 8 : 0) + (hasDyspnea ? 7 : 0);
        int scoreCardiac = (hasChestPain ? 10 : 0) + (hasPalpitations ? 6 : 0);
        int scoreAbdomen = (hasAbdominalPain ? 8 : 0) + (hasDiarrhea ? 5 : 0) + (hasVomiting && !hasHeadache ? 5 : 0);
        int scoreSpine = (hasBackPain ? 9 : 0);
        int scoreDerm = (hasSkinRash ? 9 : 0) + (hasItching ? 4 : 0);
        int scoreUrinary = (hasUrinary ? 9 : 0);
        int scoreDizzy = (hasDizziness ? 8 : 0) + (hasPositionalStanding ? 4 : 0) + (hasTrueVertigo ? 4 : 0) + (hasFatigue ? 2 : 0);

        int maxScore = 0;
        String bestCat = "General Clinical Assessment";

        if (scoreMusculo > maxScore) { maxScore = scoreMusculo; bestCat = "Musculoskeletal & Trauma"; }
        if (scoreCardiac > maxScore) { maxScore = scoreCardiac; bestCat = "Cardiopulmonary & Ischemia Screening"; }
        if (scoreRespLower > maxScore) { maxScore = scoreRespLower; bestCat = "Pulmonary & Lower Respiratory"; }
        if (scoreRespUpper > maxScore) { maxScore = scoreRespUpper; bestCat = "Upper Respiratory & Infectious"; }
        if (scoreNeuro > maxScore) { maxScore = scoreNeuro; bestCat = "Neurology & Cephalea"; }
        if (scoreAbdomen > maxScore) { maxScore = scoreAbdomen; bestCat = "Gastrointestinal & Acute Abdomen"; }
        if (scoreSpine > maxScore) { maxScore = scoreSpine; bestCat = "Lumbosacral & Spine"; }
        if (scoreDerm > maxScore) { maxScore = scoreDerm; bestCat = "Dermatology & Cutaneous"; }
        if (scoreUrinary > maxScore) { maxScore = scoreUrinary; bestCat = "Urology & Nephrology"; }
        if (scoreDizzy > maxScore) { maxScore = scoreDizzy; bestCat = "Clinical Assessment — Dizziness Evaluation"; }

        res.primaryCategory = bestCat;

        boolean isMusculoTrauma = "Musculoskeletal & Trauma".equals(bestCat);
        boolean isNeuroHeadache = "Neurology & Cephalea".equals(bestCat);
        boolean isRespUpper = "Upper Respiratory & Infectious".equals(bestCat);
        boolean isRespLower = "Pulmonary & Lower Respiratory".equals(bestCat);
        boolean isCardiacChest = "Cardiopulmonary & Ischemia Screening".equals(bestCat);
        boolean isAbdomenGI = "Gastrointestinal & Acute Abdomen".equals(bestCat);
        boolean isSpineBack = "Lumbosacral & Spine".equals(bestCat);
        boolean isDermSkin = "Dermatology & Cutaneous".equals(bestCat);
        boolean isUrinaryCat = "Urology & Nephrology".equals(bestCat);
        boolean isDizzyCat = "Clinical Assessment — Dizziness Evaluation".equals(bestCat);

        // =============================================================
        // [1] CLINICAL SUMMARY (Provenance-Aware: Documented Data vs EHR)
        // =============================================================
        StringBuilder cs = new StringBuilder();
        cs.append(res.patientAge).append(" ").append(gender != null ? gender.toLowerCase() : "patient").append(" presenting with ");
        if (!symRaw.isEmpty()) {
            cs.append("\"").append(symRaw).append("\"");
            if (!extractedDuration.isEmpty()) {
                cs.append(" (documented duration: ").append(extractedDuration).append(")");
            }
            cs.append(". ");
        } else {
            cs.append("unspecified chief complaints. ");
        }

        if (!notesRaw.isEmpty()) {
            cs.append("Consultation documentation notes: \"").append(notesRaw).append("\". ");
        }

        // Mention documented vitals if provided
        String tempVal = getVitalValue(vitals, "temperature", "temp");
        if (tempVal != null && !tempVal.isEmpty()) {
            cs.append("Encounter temperature recorded at ").append(tempVal).append("°F. ");
        }
        String bpVal = getVitalValue(vitals, "bloodPressure", "bp");
        if (bpVal != null && !bpVal.isEmpty()) {
            cs.append("Encounter BP recorded at ").append(bpVal).append(" mmHg. ");
        }

        cs.append("Historical EHR indicates ").append(activeMeds.size()).append(" active prescription(s) recorded");
        if (knownAllergies != null && !knownAllergies.trim().isEmpty() && !knownAllergies.equalsIgnoreCase("none")) {
            cs.append(" and documented allergy profile: ").append(knownAllergies.trim());
        }
        cs.append(".");
        res.clinicalSummary = cs.toString();

        // =============================================================
        // [2] KEY SYMPTOMS & FINDINGS (Documented Only — No Invented Exams)
        // =============================================================
        if (!symRaw.isEmpty()) {
            String[] parts = symRaw.split("[,;\\.]+|\\band\\b|\\bwith\\b");
            for (String p : parts) {
                String clean = p.trim();
                if (clean.length() > 2) {
                    res.keySymptoms.add("Documented symptom: " + clean);
                }
            }
        }
        if (!notesRaw.isEmpty()) {
            res.keySymptoms.add("Documented consultation finding: " + notesRaw);
        }
        if (hasNoLOC) {
            res.keySymptoms.add("Documented feature: No loss of consciousness reported.");
        }
        
        // Physical/Neurological exam provenance check
        boolean hasExamNotes = notesLower.contains("exam") || notesLower.contains("palpation") || notesLower.contains("auscultation") || notesLower.contains("tender") || notesLower.contains("rhonchi") || notesLower.contains("bowel sounds");
        if (!hasExamNotes) {
            res.keySymptoms.add("Physical / Neurological examination: Not documented in current encounter.");
        }

        // =============================================================
        // [3] SYMPTOM PATTERN ANALYSIS (Clinical Reasoning)
        // =============================================================
        if (isDizzyCat) {
            if (hasPositionalStanding) {
                res.symptomPattern.add("Reported Trajectory: Intermittent dizziness" + (!extractedDuration.isEmpty() ? " " + extractedDuration : "") + ", with symptoms reported during transition from sitting to standing.");
                res.symptomPattern.add("Clinical Reasoning: The positional relationship with standing may warrant assessment for orthostatic or postural mechanisms.");
            } else if (hasTrueVertigo) {
                res.symptomPattern.add("Reported Trajectory: Episodic spinning dizziness / vertigo" + (!extractedDuration.isEmpty() ? " lasting " + extractedDuration : "") + " triggered by head turns or positional shifts.");
                res.symptomPattern.add("Clinical Reasoning: Symptoms described as rotational spinning may warrant assessment for peripheral vestibular causes.");
            } else {
                res.symptomPattern.add("Reported Trajectory: Documented episodes of dizziness" + (!extractedDuration.isEmpty() ? " " + extractedDuration : "") + ".");
                res.symptomPattern.add("Clinical Reasoning: Trajectory requires characterization of lightheadedness vs. true rotational vertigo.");
            }
            if (hasNoLOC) {
                res.symptomPattern.add("Documented Feature: Absence of syncope or loss of consciousness reported by patient.");
            }
        } else if (isMusculoTrauma) {
            res.symptomPattern.add("Reported Trajectory: Acute mechanical event" + (hasTraumaOrSlip ? " following slip/fall" : "") + (!extractedDuration.isEmpty() ? " (" + extractedDuration + ")" : "") + " with localized joint pain and swelling.");
            res.symptomPattern.add("Clinical Reasoning: Acute trauma with localized edema warrants evaluation of ligamentous integrity versus bony injury.");
        } else if (isNeuroHeadache) {
            res.symptomPattern.add("Reported Trajectory: Acute/subacute cephalea" + (!extractedDuration.isEmpty() ? " of " + extractedDuration + " duration" : "") + ".");
            if (hasPhotophobia) {
                res.symptomPattern.add("Documented Feature: Accompanied by photophobia / light sensitivity.");
            }
            if (hasNausea) {
                res.symptomPattern.add("Documented Feature: Associated nausea reported.");
            }
            res.symptomPattern.add("Clinical Reasoning: Cephalea with nausea and sensory sensitivity suggests evaluation for primary headache disorder (e.g. migraine).");
        } else if (isRespUpper) {
            res.symptomPattern.add("Reported Trajectory: Acute upper respiratory symptoms" + (!extractedDuration.isEmpty() ? " lasting " + extractedDuration : "") + ".");
            if (hasFever) {
                res.symptomPattern.add("Documented Feature: Febrile episodes" + (tempVal != null ? " with measured temperature " + tempVal + "°F" : "") + ".");
            }
            if (hasCold) {
                res.symptomPattern.add("Documented Feature: Nasal congestion / cold symptoms.");
            }
            if (hasBodyPain || hasFatigue) {
                res.symptomPattern.add("Documented Feature: Generalized body pain / myalgia / fatigue.");
            }
            if (hasSwallowingDifficulty) {
                res.symptomPattern.add("Documented Feature: Pharyngeal discomfort / difficulty swallowing.");
            }
            res.symptomPattern.add("Clinical Reasoning: Presentation is consistent with an acute viral upper respiratory prodrome.");
        } else if (isRespLower) {
            res.symptomPattern.add("Reported Trajectory: Lower respiratory tract symptoms with " + (hasCough ? "cough" : "respiratory complaints") + (!extractedDuration.isEmpty() ? " for " + extractedDuration : "") + ".");
            if (hasDyspnea) {
                res.symptomPattern.add("Documented Feature: Exertional shortness of breath / dyspnea reported.");
            }
            res.symptomPattern.add("Clinical Reasoning: Productive or exertional cough warrants physical chest auscultation and pulse oximetry monitoring.");
        } else if (isCardiacChest) {
            res.symptomPattern.add("Reported Trajectory: Acute or exertional chest discomfort" + (!extractedDuration.isEmpty() ? " (" + extractedDuration + ")" : "") + ".");
            res.symptomPattern.add("Clinical Reasoning: Exertional chest pressure requires immediate objective exclusion of acute myocardial ischemia.");
        } else if (isAbdomenGI) {
            res.symptomPattern.add("Reported Trajectory: Acute abdominal / digestive discomfort" + (!extractedDuration.isEmpty() ? " for " + extractedDuration : "") + ".");
            if (hasNausea || hasVomiting) {
                res.symptomPattern.add("Documented Feature: Nausea / emesis reported.");
            }
            if (hasDiarrhea) {
                res.symptomPattern.add("Documented Feature: Loose stools / diarrhea reported.");
            }
            res.symptomPattern.add("Clinical Reasoning: Acute cramping with nausea/diarrhea suggests gastrointestinal evaluation.");
        } else if (isSpineBack) {
            res.symptomPattern.add("Reported Trajectory: Acute lumbosacral pain" + (!extractedDuration.isEmpty() ? " of " + extractedDuration + " duration" : "") + (hasTraumaOrSlip || combinedText.contains("lifting") ? " following mechanical exertion/lifting" : "") + ".");
            res.symptomPattern.add("Clinical Reasoning: Acute back pain requires differentiation of mechanical musculoligamentous strain from radicular nerve root irritation.");
        } else if (isDermSkin) {
            res.symptomPattern.add("Reported Trajectory: Cutaneous eruption" + (!extractedDuration.isEmpty() ? " developing over " + extractedDuration : "") + ".");
            if (hasItching) {
                res.symptomPattern.add("Documented Feature: Associated pruritus / intense itching reported.");
            }
            res.symptomPattern.add("Clinical Reasoning: Pruritic cutaneous wheals or rash suggest histaminergic or contact-mediated dermatitis evaluation.");
        } else if (isUrinaryCat) {
            res.symptomPattern.add("Reported Trajectory: Dysuria and urinary frequency" + (!extractedDuration.isEmpty() ? " for " + extractedDuration : "") + ".");
            res.symptomPattern.add("Clinical Reasoning: Burning micturition with frequency suggests lower urinary tract evaluation.");
        } else {
            res.symptomPattern.add("Reported Trajectory: Presenting complaints as documented in encounter record.");
            res.symptomPattern.add("Clinical Reasoning: Symptom trajectory warrants focused physical evaluation and history correlation.");
        }

        // =============================================================
        // [4] DIFFERENTIAL CONSIDERATIONS (Possibilities to Consider — Not Diagnoses)
        // =============================================================
        if (isDizzyCat) {
            if (hasPositionalStanding) {
                res.differentialConsiderations.add("Possible Consideration: Orthostatic / Postural mechanisms (to assess with positional blood pressure and pulse)");
                res.differentialConsiderations.add("Possible Consideration: Volume depletion or mild dehydration if supported by oral intake history");
                res.differentialConsiderations.add("Possible Consideration: Medication-related or vasovagal effect depending on medication review");
                res.differentialConsiderations.add("Possible Consideration: Vestibular causes if rotational vertigo or head-motion symptoms are elicited on further exam");
                res.differentialConsiderations.add("Other Considerations: Metabolic or systemic factors to consider only if suggested by further assessment");
            } else if (hasTrueVertigo) {
                res.differentialConsiderations.add("Possible Consideration: Benign Paroxysmal Positional Vertigo (BPPV) if positional testing (e.g. Dix-Hallpike) is positive");
                res.differentialConsiderations.add("Possible Consideration: Peripheral Vestibulopathy / Vestibular Neuritis");
                res.differentialConsiderations.add("Possible Consideration: Orthostatic / Postural causes to correlate with positional vitals");
            } else {
                res.differentialConsiderations.add("Possible Consideration: Postural / Orthostatic lightheadedness");
                res.differentialConsiderations.add("Possible Consideration: Peripheral vestibular dysfunction");
                res.differentialConsiderations.add("Possible Consideration: Vasovagal or situational presyncope");
            }
        } else if (isMusculoTrauma) {
            res.differentialConsiderations.add("Possible Consideration: Acute Lateral Ankle Ligamentous Sprain (ATFL / CFL involvement)");
            res.differentialConsiderations.add("Possible Consideration: Bony injury / fracture (to evaluate using Ottawa Ankle Rules criteria)");
            res.differentialConsiderations.add("Possible Consideration: Syndesmotic (High Ankle) sprain or traumatic tenosynovitis");
        } else if (isNeuroHeadache) {
            res.differentialConsiderations.add("Possible Consideration: Acute Episodic Migraine (with nausea and photophobia)");
            res.differentialConsiderations.add("Possible Consideration: Tension-Type Headache with secondary sensory sensitivity");
            res.differentialConsiderations.add("Consideration to Screen: Secondary headache etiologies if warning signs emerge");
        } else if (isRespUpper) {
            if (hasSoreThroat || hasSwallowingDifficulty) {
                res.differentialConsiderations.add("Possible Consideration: Acute Viral Pharyngitis / Upper Respiratory Infection");
                res.differentialConsiderations.add("Possible Consideration: Streptococcal Pharyngotonsillitis (evaluate Centor criteria if exudates present)");
            } else {
                res.differentialConsiderations.add("Possible Consideration: Acute Viral Upper Respiratory Tract Infection (Common Cold / Viral Rhinitis)");
                res.differentialConsiderations.add("Possible Consideration: Influenza or Acute Viral Febrile Illness with Myalgia");
                res.differentialConsiderations.add("Possible Consideration: Early systemic viral prodrome without focal lower tract involvement");
            }
        } else if (isRespLower) {
            res.differentialConsiderations.add("Possible Consideration: Acute Bronchitis / Tracheobronchitis (Viral vs. Atypical Bacterial)");
            res.differentialConsiderations.add("Possible Consideration: Community-Acquired Pneumonia (CAP) if focal findings present on auscultation");
            res.differentialConsiderations.add("Possible Consideration: Reactive Airway Disease or Post-Viral Bronchospasm");
        } else if (isCardiacChest) {
            res.differentialConsiderations.add("Consideration to Rule Out: Acute Coronary Syndrome (ACS) / Angina Pectoris (priority objective evaluation)");
            res.differentialConsiderations.add("Possible Consideration: Musculoskeletal Chest Wall Strain / Costochondritis");
            res.differentialConsiderations.add("Possible Consideration: Gastroesophageal Reflux Disease (GERD) / Esophageal Spasm");
        } else if (isAbdomenGI) {
            res.differentialConsiderations.add("Possible Consideration: Acute Infectious Gastroenteritis (Viral / Food-borne)");
            res.differentialConsiderations.add("Possible Consideration: Functional Dyspepsia or Acute Gastritis");
            res.differentialConsiderations.add("Consideration to Screen: Surgical acute abdomen (e.g. Appendicitis) if localized tenderness develops");
        } else if (isSpineBack) {
            res.differentialConsiderations.add("Possible Consideration: Acute Mechanical Lumbosacral Muscular / Ligamentous Strain");
            res.differentialConsiderations.add("Possible Consideration: Lumbar Disc Herniation with Radiculopathy / Sciatica (if dermatomal radiation present)");
            res.differentialConsiderations.add("Consideration to Screen: Spinal red-flag conditions (e.g. Cauda Equina compression)");
        } else if (isDermSkin) {
            res.differentialConsiderations.add("Possible Consideration: Acute Urticaria / Histaminergic Cutaneous Reaction");
            res.differentialConsiderations.add("Possible Consideration: Acute Contact Dermatitis (Allergic vs. Irritant)");
            res.differentialConsiderations.add("Possible Consideration: Drug-induced or viral exanthematous eruption");
        } else if (isUrinaryCat) {
            res.differentialConsiderations.add("Possible Consideration: Acute Uncomplicated Cystitis (Lower Urinary Tract Infection)");
            res.differentialConsiderations.add("Consideration to Screen: Upper tract involvement (Pyelonephritis) if fever or flank pain is present");
        } else {
            res.differentialConsiderations.add("Possible Consideration: Outpatient presentation requires targeted organ-system examination.");
            res.differentialConsiderations.add("Possible Consideration: Correlate history with baseline clinical evaluation.");
        }

        // =============================================================
        // [5] RED-FLAG SCREENING (Features to Assess — Not Falsely Marked Absent)
        // =============================================================
        if (isDizzyCat) {
            res.redFlagScreening.add("Important features to assess clinically include:");
            res.redFlagScreening.add("• Syncope / True loss of consciousness (" + (hasNoLOC ? "documented as absent by patient report" : "assess history") + ")");
            res.redFlagScreening.add("• Focal neurological deficits (unilateral weakness, sensory loss, facial asymmetry, dysarthria, diplopia)");
            res.redFlagScreening.add("• Central signs on examination (spontaneous vertical nystagmus, severe gait ataxia, dysmetria)");
            res.redFlagScreening.add("• Severe new-onset headache, neck stiffness, or sudden hearing loss");
            res.redFlagScreening.add("• Chest pain, palpitations, or acute shortness of breath");
        } else if (isMusculoTrauma) {
            res.redFlagScreening.add("Important features to assess clinically include:");
            res.redFlagScreening.add("• Inability to bear weight for 4 consecutive steps immediately after injury and at examination");
            res.redFlagScreening.add("• Severe bone point tenderness along the posterior 6cm of malleoli or base of 5th metatarsal (Ottawa Criteria)");
            res.redFlagScreening.add("• Neurovascular compromise: Absent distal pedal pulses, capillary refill >2s, or distal numbness");
            res.redFlagScreening.add("• Gross joint deformity, open wound, or rapidly expanding tense hematoma");
        } else if (isNeuroHeadache) {
            res.redFlagScreening.add("Important features to assess clinically include:");
            res.redFlagScreening.add("• Sudden 'thunderclap' onset reaching peak intensity within 60 seconds (Subarachnoid hemorrhage screen)");
            res.redFlagScreening.add("• New focal neurological deficits (unilateral motor weakness, sensory loss, speech changes)");
            res.redFlagScreening.add("• Systemic signs: High fever accompanied by nuchal rigidity (meningismus) or altered sensorium");
            res.redFlagScreening.add("• Papilledema on fundoscopy or new onset headache in patients aged >50 years");
        } else if (isRespUpper) {
            res.redFlagScreening.add("Important features to assess clinically include:");
            res.redFlagScreening.add("• High-grade unremitting fever (>103°F / 39.5°C) with toxic appearance or neck stiffness");
            res.redFlagScreening.add("• Development of severe shortness of breath, tachypnea, or chest pain");
            res.redFlagScreening.add("• Inability to maintain oral hydration or signs of severe dehydration");
            if (hasSoreThroat) {
                res.redFlagScreening.add("• Airway compromise: Inspiratory stridor, marked drooling, trismus, or uvular deviation");
            }
        } else if (isRespLower) {
            res.redFlagScreening.add("Important features to assess clinically include:");
            res.redFlagScreening.add("• Hypoxemia: SpO2 < 92% or resting tachypnea (>24 breaths/min)");
            res.redFlagScreening.add("• Respiratory distress: Intercostal indrawing, accessory muscle use, inability to complete sentences");
            res.redFlagScreening.add("• Frank hemoptysis or rust-colored sputum");
            res.redFlagScreening.add("• Hemodynamic instability or altered mental status (CURB-65 criteria)");
        } else if (isCardiacChest) {
            res.redFlagScreening.add("Important features to assess clinically include:");
            res.redFlagScreening.add("• Crushing substernal pressure radiating to jaw, left arm, or back with diaphoresis");
            res.redFlagScreening.add("• Hemodynamic instability: Hypotension (Systolic BP < 90 mmHg) or sustained tachycardia/bradycardia");
            res.redFlagScreening.add("• Syncope or presyncope associated with chest discomfort");
            res.redFlagScreening.add("• Acute pulmonary edema signs (bilateral lung crackles, orthopnea)");
        } else if (isAbdomenGI) {
            res.redFlagScreening.add("Important features to assess clinically include:");
            res.redFlagScreening.add("• Peritoneal signs: Involuntary abdominal guarding, rigidity, or marked rebound tenderness");
            res.redFlagScreening.add("• Gastrointestinal bleeding: Hematemesis, coffee-ground emesis, or melena");
            res.redFlagScreening.add("• Hemodynamic instability or signs of severe volume depletion");
            res.redFlagScreening.add("• Intractable vomiting with inability to tolerate oral fluids");
        } else if (isSpineBack) {
            res.redFlagScreening.add("Important features to assess clinically include:");
            res.redFlagScreening.add("• Cauda Equina Syndrome: Saddle anesthesia (perineal numbness), acute urinary retention, or fecal incontinence");
            res.redFlagScreening.add("• Progressive lower extremity motor weakness (e.g. foot drop)");
            res.redFlagScreening.add("• Spinal infection screen: Unexplained fever, history of IV drug use, or spinal procedures");
            res.redFlagScreening.add("• Malignancy screen: Unexplained weight loss, pain worse at night/rest");
        } else if (isDermSkin) {
            res.redFlagScreening.add("Important features to assess clinically include:");
            res.redFlagScreening.add("• Anaphylaxis / Airway: Angioedema of lips, tongue, or pharynx with stridor, wheezing, or dysphagia");
            res.redFlagScreening.add("• Severe cutaneous adverse reactions (SCAR): Mucosal ulceration, blistering, skin detachment");
            res.redFlagScreening.add("• Non-blanching petechial or purpuric rash with high fever");
        } else if (isUrinaryCat) {
            res.redFlagScreening.add("Important features to assess clinically include:");
            res.redFlagScreening.add("• Upper tract sepsis: High-grade fever, rigors, and marked costovertebral angle tenderness");
            res.redFlagScreening.add("• Acute complete urinary retention with painful distended bladder");
            res.redFlagScreening.add("• Gross painless hematuria with blood clots");
        } else {
            res.redFlagScreening.add("Important features to assess clinically include:");
            res.redFlagScreening.add("• Acute hemodynamic instability, unexplained high fever, or focal neurological deficit.");
        }

        // =============================================================
        // [6] VITAL SIGNS INTERPRETATION (Accurate Interpretation & No Assumptions)
        // =============================================================
        StringBuilder vs = new StringBuilder();
        String bp = getVitalValue(vitals, "bloodPressure", "bp");
        String temp = getVitalValue(vitals, "temperature", "temp");
        String hr = getVitalValue(vitals, "heartRate", "pulse");
        String spo2 = getVitalValue(vitals, "oxygenSaturation", "spo2");
        String rr = getVitalValue(vitals, "respiratoryRate", "rr");

        vs.append("• **Blood Pressure:** ").append(bp != null ? bp + " mmHg — " + interpretBP(bp) : "Not provided").append("\n");
        vs.append("• **Temperature:** ").append(temp != null ? temp + "°F — " + interpretTemp(temp) : "Not provided").append("\n");
        vs.append("• **Heart Rate / Pulse:** ").append(hr != null ? hr + " bpm — " + interpretHR(hr) : "Not provided").append("\n");
        vs.append("• **Oxygen Saturation (SpO2):** ").append(spo2 != null ? spo2 + "% — " + interpretSpO2(spo2) : "Not provided").append("\n");
        vs.append("• **Respiratory Rate:** ").append(rr != null ? rr + "/min — " + interpretRR(rr) : "Not provided").append("\n");

        if (bp == null && temp == null && hr == null && spo2 == null && rr == null) {
            vs.append("\n*Clinical Note:* Encounter vital signs were not provided in current input. Obtaining baseline measurements (BP, Heart Rate, Temperature, SpO2, Respiratory Rate) is recommended for formal objective clinical risk stratification.");
        }
        res.vitalSignsInterpretation = vs.toString().trim();

        // =============================================================
        // [7] RELEVANT PATIENT HISTORY (Historical EHR Context from MySQL)
        // =============================================================
        StringBuilder ph = new StringBuilder();
        ph.append("• **Demographic Profile (EHR):** ").append(res.patientName).append(", ").append(res.patientAge).append(" (DOB: ").append(res.patientDob).append("), Gender: ").append(res.patientGender).append(".\n");
        if (chronicConditions != null && !chronicConditions.trim().isEmpty()) {
            ph.append("• **Documented Comorbidities (EHR):** ").append(chronicConditions.trim()).append(".\n");
        } else {
            ph.append("• **Comorbidities (EHR):** No chronic medical conditions documented in available electronic health record.\n");
        }
        if (!recentAppts.isEmpty()) {
            ph.append("• **Recorded Prior Encounters (EHR):** ").append(recentAppts.size()).append(" outpatient encounter(s). Last consultation: ").append(recentAppts.get(0).get("date")).append(" (").append(recentAppts.get(0).get("doctorName")).append(" - ").append(recentAppts.get(0).get("specialization")).append(").\n");
        } else {
            ph.append("• **Recorded Prior Encounters (EHR):** Initial clinical encounter recorded on MediCareX platform.\n");
        }
        res.relevantPatientHistory = ph.toString();

        // =============================================================
        // [8] MEDICATION REVIEW (Historical EHR)
        // =============================================================
        if (!activeMeds.isEmpty()) {
            res.medicationReview.add("Active Prescriptions Recorded in EHR (" + activeMeds.size() + "):");
            for (Map<String, Object> am : activeMeds) {
                res.medicationReview.add("• " + am.get("medicineName") + " — " + am.get("dosage") + " (" + am.get("frequency") + ", " + am.get("duration") + "). Prescribed by " + am.get("doctorName") + ".");
            }
            res.medicationReview.add("• Medication history outside the digital EHR should be verified when clinically relevant.");
        } else {
            res.medicationReview.add("• No active digital prescriptions are recorded in the available EHR.");
            res.medicationReview.add("• Medication history outside the digital EHR (including OTC medications, supplements, or external prescriptions) should be verified directly with the patient when clinically relevant.");
        }

        // =============================================================
        // [9] ALLERGY & SAFETY CONSIDERATIONS (Allergy-Aware Reasoning)
        // =============================================================
        if (knownAllergies != null && !knownAllergies.trim().isEmpty() && !knownAllergies.equalsIgnoreCase("none")) {
            res.allergySafetyConsiderations.add("DOCUMENTED ALLERGY PROFILE: " + knownAllergies.trim() + ".");
            if (hasPenicillinAllergy) {
                res.allergySafetyConsiderations.add("CRITICAL PRESCRIBING SAFETY: Documented Penicillin allergy on file. Strict avoidance of Penicillins, Amoxicillin, Ampicillin, and related Beta-lactams is advised.");
            }
            res.allergySafetyConsiderations.add("Medication selection should account for the documented allergy profile and be determined by the treating clinician.");
        } else {
            res.allergySafetyConsiderations.add("No drug or food allergies formally recorded in available electronic health record.");
            res.allergySafetyConsiderations.add("Explicit verbal allergy verification is advised prior to administering pharmacotherapy.");
        }

        // =============================================================
        // [10] RECOMMENDED CLINICAL EVALUATION (Conditional & Case-Specific)
        // =============================================================
        if (isDizzyCat) {
            res.recommendedClinicalEvaluation.add("Depending on the clinical assessment and clinician judgment, the doctor may consider:");
            res.recommendedClinicalEvaluation.add("• Measurement of orthostatic vital signs (blood pressure and pulse supine, and after 1 and 3 minutes of standing)");
            res.recommendedClinicalEvaluation.add("• Review of current medication regimen, recent dosage changes, and hydration status");
            res.recommendedClinicalEvaluation.add("• Focused neurological examination (cranial nerves, gait, Romberg testing, and coordination)");
            res.recommendedClinicalEvaluation.add("• Positional testing (e.g. Dix-Hallpike maneuver) if rotational vertigo or head-motion triggers are elicited on history");
            res.recommendedClinicalEvaluation.add("• Blood glucose assessment and baseline laboratory evaluation (e.g. CBC, electrolytes) when clinically indicated based on history and physical findings");
        } else if (isMusculoTrauma) {
            res.recommendedClinicalEvaluation.add("Depending on the clinical assessment, the doctor may consider:");
            res.recommendedClinicalEvaluation.add("• Ottawa Ankle & Foot Rules assessment: Palpate posterior 6cm of malleoli, navicular, and base of 5th metatarsal");
            res.recommendedClinicalEvaluation.add("• Assessment of weight-bearing capability (ability to take 4 consecutive steps)");
            res.recommendedClinicalEvaluation.add("• Evaluation of ligamentous stability and distal neurovascular examination (pulses, capillary refill, sensation)");
            res.recommendedClinicalEvaluation.add("• Plain radiography (AP, lateral, mortise views) if indicated by Ottawa criteria");
        } else if (isNeuroHeadache) {
            res.recommendedClinicalEvaluation.add("Depending on the clinical assessment, the doctor may consider:");
            res.recommendedClinicalEvaluation.add("• Comprehensive cranial nerve examination (CN II–XII) and pupillary reflex testing");
            res.recommendedClinicalEvaluation.add("• Fundoscopic examination to screen for papilledema or retinal findings");
            res.recommendedClinicalEvaluation.add("• Screening for meningeal signs (nuchal rigidity, Kernig's sign)");
            res.recommendedClinicalEvaluation.add("• Blood pressure measurement to evaluate for hypertensive contribution");
        } else if (isRespUpper) {
            res.recommendedClinicalEvaluation.add("Depending on the clinical assessment, the doctor may consider:");
            res.recommendedClinicalEvaluation.add("• Physical examination of nasal mucosa, hydration status, and cervical lymph nodes");
            if (hasSoreThroat) {
                res.recommendedClinicalEvaluation.add("• Oropharyngeal inspection (tonsillar size, erythema, exudates) and Centor score evaluation");
            }
            res.recommendedClinicalEvaluation.add("• Bilateral chest auscultation to verify clear breath sounds");
            res.recommendedClinicalEvaluation.add("• Complete Blood Count (CBC) or targeted viral testing if fever persists beyond 3–5 days");
        } else if (isRespLower) {
            res.recommendedClinicalEvaluation.add("Depending on the clinical assessment, the doctor may consider:");
            res.recommendedClinicalEvaluation.add("• Systematic chest auscultation across all lung zones for crackles, wheezes, or rhonchi");
            res.recommendedClinicalEvaluation.add("• Pulse oximetry monitoring and respiratory rate evaluation");
            res.recommendedClinicalEvaluation.add("• Chest radiography (PA/lateral) if focal breath sounds, hypoxemia, or persistent high fever are present");
        } else if (isCardiacChest) {
            res.recommendedClinicalEvaluation.add("Priority clinical evaluation considerations:");
            res.recommendedClinicalEvaluation.add("• Immediate 12-lead ECG to evaluate for ST-T segment deviations or arrhythmias");
            res.recommendedClinicalEvaluation.add("• Serial cardiac biomarkers (high-sensitivity Troponin) based on clinical protocol");
            res.recommendedClinicalEvaluation.add("• Hemodynamic monitoring and chest wall palpation to assess pain reproducibility");
        } else if (isAbdomenGI) {
            res.recommendedClinicalEvaluation.add("Depending on the clinical assessment, the doctor may consider:");
            res.recommendedClinicalEvaluation.add("• Four-quadrant abdominal palpation for focal tenderness, guarding, and rebound signs");
            res.recommendedClinicalEvaluation.add("• Assessment of hydration status, oral tolerance, and bowel sounds");
            res.recommendedClinicalEvaluation.add("• Targeted laboratory or ultrasound evaluation if acute surgical abdomen is suspected");
        } else if (isSpineBack) {
            res.recommendedClinicalEvaluation.add("Depending on the clinical assessment, the doctor may consider:");
            res.recommendedClinicalEvaluation.add("• Straight Leg Raise testing to screen for nerve root tension");
            res.recommendedClinicalEvaluation.add("• Lower extremity neurological assessment (reflexes, motor strength, dermatomal sensation)");
            res.recommendedClinicalEvaluation.add("• Spinous process palpation and screen for saddle sensation / sphincter tone");
        } else if (isDermSkin) {
            res.recommendedClinicalEvaluation.add("Depending on the clinical assessment, the doctor may consider:");
            res.recommendedClinicalEvaluation.add("• Full skin examination including mucosal surfaces to evaluate lesion morphology and distribution");
            res.recommendedClinicalEvaluation.add("• Diascopy (glass slide pressure) to confirm blanching vs. non-blanching lesions");
            res.recommendedClinicalEvaluation.add("• Detailed review of all newly introduced medications, topicals, or allergen exposures");
        } else if (isUrinaryCat) {
            res.recommendedClinicalEvaluation.add("Depending on the clinical assessment, the doctor may consider:");
            res.recommendedClinicalEvaluation.add("• Point-of-care urine dipstick (leukocyte esterase, nitrites, blood, protein)");
            res.recommendedClinicalEvaluation.add("• Urine microscopy and culture with antimicrobial susceptibility profile");
            res.recommendedClinicalEvaluation.add("• Costovertebral angle (CVA) palpation to screen for upper tract involvement");
        } else {
            res.recommendedClinicalEvaluation.add("Depending on clinical findings, consider focused organ-system examination and baseline laboratory screening as indicated.");
        }

        // =============================================================
        // [11] CLINICAL DECISION POINTS (Non-Prescriptive Supportive Triggers)
        // =============================================================
        if (isDizzyCat) {
            res.clinicalDecisionPoints.add("• If orthostatic blood pressure changes are confirmed, assess contributing factors including hydration status and medications that may contribute.");
            res.clinicalDecisionPoints.add("• If rotational vertigo is elicited with positive Dix-Hallpike testing, clinician may consider canalith repositioning maneuvers.");
            res.clinicalDecisionPoints.add("• If new focal neurological deficits or central red flags are present, urgent neuroimaging or neurological evaluation is warranted.");
            res.clinicalDecisionPoints.add("• If symptoms persist or diagnosis remains uncertain, further outpatient cardiovascular or vestibular evaluation may be appropriate.");
        } else if (isMusculoTrauma) {
            res.clinicalDecisionPoints.add("• If Ottawa Ankle Rules criteria are met (bone tenderness or inability to bear weight) ➔ Consider plain radiography series.");
            res.clinicalDecisionPoints.add("• If ligamentous sprain is clinically suspected without fracture ➔ Consider functional support, R.I.C.E measures, and progressive mobilization as appropriate.");
        } else if (isNeuroHeadache) {
            res.clinicalDecisionPoints.add("• If SNOOP red flags (thunderclap onset, focal deficit, papilledema) are present ➔ Urgent neuroimaging is indicated.");
            res.clinicalDecisionPoints.add("• If uncomplicated migraine pattern is clinically diagnosed ➔ Stepwise acute therapy and trigger management by clinician.");
        } else if (isRespUpper) {
            res.clinicalDecisionPoints.add("• If uncomplicated viral upper respiratory presentation ➔ Supportive care (hydration, rest, antipyretics); avoid routine empirical antibiotics.");
            res.clinicalDecisionPoints.add("• If bacterial pharyngitis is confirmed (e.g. Centor/RADT) ➔ Select appropriate antibiotic accounting for documented allergy profile (e.g. avoid beta-lactams in Penicillin-allergic patient).");
        } else if (isRespLower) {
            res.clinicalDecisionPoints.add("• If hypoxemia (SpO2 < 92%) or severe respiratory distress ➔ Urgent hospital evaluation and oxygen support.");
            res.clinicalDecisionPoints.add("• If mild lower tract symptoms ➔ Determine need for chest radiography and antimicrobial therapy based on clinical findings.");
        } else if (isCardiacChest) {
            res.clinicalDecisionPoints.add("• If ECG reveals ischemic ST-T changes or cardiac biomarkers are elevated ➔ Immediate acute coronary syndrome management pathway.");
            res.clinicalDecisionPoints.add("• If initial workup is non-ischemic ➔ Consider outpatient cardiology evaluation and functional testing.");
        } else if (isAbdomenGI) {
            res.clinicalDecisionPoints.add("• If localized peritoneal signs or rebound tenderness develop ➔ Urgent surgical consultation.");
            res.clinicalDecisionPoints.add("• If uncomplicated mild gastroenteritis ➔ Oral rehydration and supportive management.");
        } else if (isSpineBack) {
            res.clinicalDecisionPoints.add("• If saddle anesthesia or sphincter dysfunction is present ➔ Emergent whole-spine MRI for cauda equina compression.");
            res.clinicalDecisionPoints.add("• If uncomplicated mechanical strain ➔ Activity maintenance, short-course analgesia, and physical therapy as appropriate.");
        } else if (isDermSkin) {
            res.clinicalDecisionPoints.add("• If angioedema or airway involvement is present ➔ Immediate emergency airway management and Epinephrine.");
            res.clinicalDecisionPoints.add("• If uncomplicated urticaria ➔ Second-generation H1-antihistamines and allergen avoidance.");
        } else if (isUrinaryCat) {
            res.clinicalDecisionPoints.add("• If uncomplicated lower UTI ➔ First-line empirical antimicrobial therapy accounting for documented allergies.");
            res.clinicalDecisionPoints.add("• If systemic toxicity or flank pain (pyelonephritis) ➔ Urine/blood cultures and systemic therapy.");
        } else {
            res.clinicalDecisionPoints.add("• Clinical response within 48–72 hours determines the need for second-line diagnostic investigations.");
        }

        // =============================================================
        // [12] SEVERITY / URGENCY ASSESSMENT (Conditional & Fact-Based)
        // =============================================================
        if (isCardiacChest) {
            res.urgencyLevel = "URGENT";
            res.severityUrgencyAssessment = "Urgent Assessment: Chest symptoms require immediate objective exclusion of acute coronary syndrome and continuous hemodynamic monitoring. Clinical decisions remain the responsibility of the attending physician.";
        } else if (isRespLower && hasDyspnea) {
            res.urgencyLevel = "SEMI_URGENT";
            res.severityUrgencyAssessment = "Semi-Urgent Consideration: Lower respiratory symptoms with documented shortness of breath warrant timely clinical evaluation and pulse oximetry to confirm adequate oxygenation.";
        } else if (isMusculoTrauma && combinedText.contains("weight bearing")) {
            res.urgencyLevel = "SEMI_URGENT";
            res.severityUrgencyAssessment = "Semi-Urgent / Priority Outpatient: Acute joint trauma with reported weight-bearing limitation requires timely physical examination and fracture rule evaluation.";
        } else {
            res.urgencyLevel = "ROUTINE";
            res.severityUrgencyAssessment = "Standard Outpatient Priority: Based on the clinical information currently provided, no immediate life-threatening instability is documented. Further clinical assessment and physical examination by the attending physician are required to establish definitive urgency.";
        }

        // =============================================================
        // [13] FOLLOW-UP CONSIDERATIONS (Conditional & Case-Specific)
        // =============================================================
        if (isDizzyCat) {
            res.followUpConsiderations.add("• Follow-up timing should depend on symptom progression, examination findings, and initial clinical assessment.");
            res.followUpConsiderations.add("• Advise patient to practice deliberate postural transitions (e.g. pausing before standing) and maintain adequate hydration.");
            res.followUpConsiderations.add("• Emergency Return Precautions: Instruct patient to seek immediate emergency medical care if syncope, speech slurring, facial asymmetry, limb weakness, severe headache, or chest pain occurs.");
        } else if (isMusculoTrauma) {
            res.followUpConsiderations.add("• Follow-up timing should depend on physical examination findings, radiological results, and clinical response over 5–7 days.");
            res.followUpConsiderations.add("• Conservative R.I.C.E measures (Rest, Ice, Compression, Elevation) and protected mobilization as clinically advised.");
            res.followUpConsiderations.add("• Emergency Return Precautions: Return immediately if foot becomes cold, pale, numb, or if pain worsens significantly.");
        } else if (isNeuroHeadache) {
            res.followUpConsiderations.add("• Follow-up timing should depend on response to acute therapy and headache frequency.");
            res.followUpConsiderations.add("• Maintain a headache diary tracking triggers, duration, and medication response.");
            res.followUpConsiderations.add("• Emergency Return Precautions: Return immediately if sudden 'worst headache of life', fever with stiff neck, or focal neurological deficits develop.");
        } else if (isRespUpper) {
            res.followUpConsiderations.add("• Follow-up timing should depend on fever trend and resolution of symptoms over 3–5 days.");
            res.followUpConsiderations.add("• Encourage oral hydration, rest, and appropriate antipyretic relief as indicated.");
            res.followUpConsiderations.add("• Emergency Return Precautions: Return immediately if difficulty breathing, inability to swallow liquids, or high unremitting fever develops.");
        } else if (isRespLower) {
            res.followUpConsiderations.add("• Follow-up evaluation in 3–5 days or sooner if breathing difficulty or fever increases.");
            res.followUpConsiderations.add("• Emergency Return Precautions: Seek immediate emergency care if severe shortness of breath, chest pain, or cyanosis occurs.");
        } else if (isCardiacChest) {
            res.followUpConsiderations.add("• Continuous observation until formal serial cardiac workup is completed.");
            res.followUpConsiderations.add("• Emergency Return Precautions: Dial emergency medical services (108 / 911) immediately if severe chest discomfort recurs.");
        } else if (isAbdomenGI) {
            res.followUpConsiderations.add("• Re-evaluate in 24–48 hours if abdominal symptoms do not progressively improve.");
            res.followUpConsiderations.add("• Oral electrolyte maintenance and bland diet as tolerated.");
            res.followUpConsiderations.add("• Emergency Return Precautions: Return immediately if severe localized pain, persistent vomiting, high fever, or GI bleeding occurs.");
        } else if (isSpineBack) {
            res.followUpConsiderations.add("• Follow-up in 2–3 weeks; structured physical therapy may be considered if symptoms persist.");
            res.followUpConsiderations.add("• Emergency Return Precautions: Go to emergency department immediately if saddle numbness or loss of bowel/bladder control occurs.");
        } else if (isDermSkin) {
            res.followUpConsiderations.add("• Outpatient review in 1–2 weeks if cutaneous eruption fails to resolve.");
            res.followUpConsiderations.add("• Emergency Return Precautions: Immediate ER visit if facial/lip swelling, breathing difficulty, or skin blistering occurs.");
        } else if (isUrinaryCat) {
            res.followUpConsiderations.add("• Clinical review in 3–4 days if dysuria persists or fails to improve.");
            res.followUpConsiderations.add("• Emergency Return Precautions: Return immediately if flank pain, high fever, rigors, or nausea develop.");
        } else {
            res.followUpConsiderations.add("• Follow-up timing should depend on symptom progression, examination findings, and clinical assessment.");
            res.followUpConsiderations.add("• Clear return precautions provided if symptoms worsen or new warning signs develop.");
        }

        // =============================================================
        // [14] RELEVANT PATIENT FACTORS (Clinically Relevant Only)
        // =============================================================
        res.patientSpecificRiskFactors.add("• Patient Demographics: Age " + res.patientAge + ", " + res.patientGender + ".");
        if (chronicConditions != null && !chronicConditions.trim().isEmpty()) {
            res.patientSpecificRiskFactors.add("• Documented Chronic Conditions (EHR): " + chronicConditions.trim() + ".");
        } else {
            res.patientSpecificRiskFactors.add("• Documented Chronic Conditions (EHR): None recorded in digital record.");
        }
        if (knownAllergies != null && !knownAllergies.trim().isEmpty() && !knownAllergies.equalsIgnoreCase("none")) {
            res.patientSpecificRiskFactors.add("• Documented Allergies (EHR): " + knownAllergies.trim() + " (Must be cross-referenced for medication safety).");
        }
        if (!activeMeds.isEmpty()) {
            res.patientSpecificRiskFactors.add("• Documented Regimens (EHR): " + activeMeds.size() + " active digital prescription(s) on file.");
        } else {
            res.patientSpecificRiskFactors.add("• Active Digital Prescriptions (EHR): None recorded in digital hospital record (verify OTC/external regimens).");
        }

        // =============================================================
        // [15] CLINICAL QUESTIONS FOR THE DOCTOR
        // =============================================================
        if (isDizzyCat) {
            res.questionsForDoctor.add("• Does the patient experience a sensation of spinning (vertigo) or lightheadedness/faintness upon standing?");
            res.questionsForDoctor.add("• What are the documented blood pressure and pulse readings in supine versus 1-minute and 3-minute standing positions?");
            res.questionsForDoctor.add("• Has the patient started any new over-the-counter medications, herbal remedies, or experienced reduced fluid intake?");
            res.questionsForDoctor.add("• Are there any accompanying auditory symptoms (tinnitus, ear fullness, hearing changes) or visual changes?");
        } else if (isMusculoTrauma) {
            res.questionsForDoctor.add("• Was there an audible 'pop', snap, or tearing sensation at the exact moment of trauma?");
            res.questionsForDoctor.add("• Can the patient bear weight for 4 consecutive steps both immediately following injury and during examination?");
            res.questionsForDoctor.add("• Is there pinpoint bone tenderness along the posterior 6cm or tips of either malleolus?");
            res.questionsForDoctor.add("• Are distal pedal pulses symmetrical and capillary refill under 2 seconds?");
        } else if (isNeuroHeadache) {
            res.questionsForDoctor.add("• Is this the patient's first or 'worst headache of life' (thunderclap onset within 60 seconds)?");
            res.questionsForDoctor.add("• Are there any accompanying visual scintillations, sensory numbness, or unilateral motor weakness?");
            res.questionsForDoctor.add("• What is the weekly frequency of OTC analgesic use (to screen for medication overuse)?");
            res.questionsForDoctor.add("• Is the headache relieved by resting in dark quiet surroundings, or worsened by coughing/straining?");
        } else if (isRespUpper) {
            res.questionsForDoctor.add("• What is the documented peak core temperature and its response to antipyretics?");
            res.questionsForDoctor.add("• Are household or workplace contacts experiencing similar infectious symptoms?");
            res.questionsForDoctor.add("• Has the patient taken any over-the-counter cold medications or antibiotics within the preceding 30 days?");
            res.questionsForDoctor.add("• Are there any emerging localizing symptoms (such as ear pain, facial pressure, or cough)?");
        } else if (isRespLower) {
            res.questionsForDoctor.add("• What is the exact resting oxygen saturation (SpO2) on ambient room air?");
            res.questionsForDoctor.add("• Is there any focal crackling, bronchial breathing, or localized wheeze on chest auscultation?");
            res.questionsForDoctor.add("• Has the patient noticed any frank blood or rust-colored discoloration in their sputum?");
            res.questionsForDoctor.add("• Is there a history of asthma, COPD, or environmental smoking exposure?");
        } else if (isCardiacChest) {
            res.questionsForDoctor.add("• Does the chest discomfort radiate to the left arm, neck, lower jaw, or interscapular region?");
            res.questionsForDoctor.add("• Is the discomfort triggered or intensified by physical exertion, and relieved by rest or nitrates?");
            res.questionsForDoctor.add("• Are there associated symptoms such as diaphoresis, dyspnea, nausea, or lightheadedness?");
            res.questionsForDoctor.add("• What are the patient's cardiovascular risk factors (hypertension, smoking, diabetes, family history of early CAD)?");
        } else if (isAbdomenGI) {
            res.questionsForDoctor.add("• Where did the pain originally begin, and has it shifted or localized to a specific quadrant?");
            res.questionsForDoctor.add("• Is there involuntary guarding or sharp pain when the abdomen is quickly released (rebound)?");
            res.questionsForDoctor.add("• Has the patient had any black tarry stools (melena) or blood in vomitus?");
            res.questionsForDoctor.add("• When was the patient's last bowel movement and flatulence?");
        } else if (isSpineBack) {
            res.questionsForDoctor.add("• Has the patient experienced any numbness in the groin/perineal area (saddle anesthesia)?");
            res.questionsForDoctor.add("• Is there any new difficulty with bladder control (urinary retention) or bowel incontinence?");
            res.questionsForDoctor.add("• Does the pain shoot down below the knee into the calf or foot (radicular trajectory)?");
            res.questionsForDoctor.add("• Is there a personal history of malignancy or unexplained systemic weight loss?");
        } else if (isDermSkin) {
            res.questionsForDoctor.add("• Has the patient started any new medications, over-the-counter supplements, or herbal remedies in the past 8 weeks?");
            res.questionsForDoctor.add("• Are there any lesions inside the mouth, on the lips, conjunctiva, or genital mucosa?");
            res.questionsForDoctor.add("• Is there any sensation of throat tightness, difficulty swallowing, or wheezing?");
            res.questionsForDoctor.add("• Does the rash blanch completely when direct pressure is applied with a glass slide?");
        } else if (isUrinaryCat) {
            res.questionsForDoctor.add("• Is there any fever, chills, nausea, or sharp flank / back ache (suggestive of pyelonephritis)?");
            res.questionsForDoctor.add("• Has the patient noticed visible red or cola-colored blood in their urine (hematuria)?");
            res.questionsForDoctor.add("• How many previous urinary tract infections has the patient had over the past 12 months?");
            res.questionsForDoctor.add("• Is there any possibility of pregnancy or recent urological instrumentations?");
        } else {
            res.questionsForDoctor.add("• What was the exact timeline and trigger of the presenting symptoms?");
            res.questionsForDoctor.add("• Are there aggravating or alleviating factors identified by the patient?");
            res.questionsForDoctor.add("• Has the patient experienced similar clinical episodes in the past?");
        }

        // =============================================================
        // CONSTRUCT DYNAMIC ACTIVE SECTIONS LIST FOR UI
        // =============================================================
        addSection(res.activeSections, "summary", "Clinical Summary", "Case Synthesis", "📋", "Summary", res.clinicalSummary, null, 100);
        addSection(res.activeSections, "key-symptoms", "Key Symptoms & Findings", "Presentation", "🔍", "Reported", null, res.keySymptoms, 95);
        addSection(res.activeSections, "symptom-pattern", "Symptom Pattern Analysis", "Trajectory", "📈", "Pattern", null, res.symptomPattern, 90);
        addSection(res.activeSections, "differential", "Differential Considerations", "Clinical Possibilities", "🔬", res.primaryCategory, null, res.differentialConsiderations, 92);
        addSection(res.activeSections, "red-flags", "Red-Flag Screening", "Warning Signs", "🚩", "Safety Check", null, res.redFlagScreening, 94);
        addSection(res.activeSections, "vitals", "Vital Signs Interpretation", "Encounter Data", "🩺", "Vitals", res.vitalSignsInterpretation, null, 85);
        addSection(res.activeSections, "history", "Relevant Patient History", "EHR Records", "🗂️", "MySQL Context", res.relevantPatientHistory, null, 88);
        addSection(res.activeSections, "medication-review", "Medication Review", "Pharmacotherapy", "💊", "Prescriptions", null, res.medicationReview, 87);
        addSection(res.activeSections, "allergy-safety", "Allergy / Safety Considerations", "Safety Alert", "🛡️", "Verification", null, res.allergySafetyConsiderations, 89);
        addSection(res.activeSections, "recommended-evaluation", "Recommended Clinical Evaluation", "Actionable Tests", "🩺", "Diagnostic Workup", null, res.recommendedClinicalEvaluation, 91);
        addSection(res.activeSections, "decision-points", "Clinical Decision Points", "Management Triggers", "⚡", "Decision Rules", null, res.clinicalDecisionPoints, 93);
        addSection(res.activeSections, "urgency", "Severity / Urgency Assessment", "Priority Classification", "⏱️", res.urgencyLevel, res.severityUrgencyAssessment, null, 86);
        addSection(res.activeSections, "follow-up", "Follow-Up Considerations", "Care Plan", "📅", "Return Precautions", null, res.followUpConsiderations, 89);
        addSection(res.activeSections, "risk-factors", "Relevant Patient Factors", "EHR Profile", "👤", "Patient Factors", null, res.patientSpecificRiskFactors, 84);
        addSection(res.activeSections, "questions", "Clinical Questions for Doctor", "Diagnostic Inquiries", "❓", "Missing Details", null, res.questionsForDoctor, 90);

        // Sort active sections by relevance score descending
        res.activeSections.sort((a, b) -> ((Integer) b.get("relevanceScore")).compareTo((Integer) a.get("relevanceScore")));

        // Compatibility strings
        res.clinicalConsiderations = String.join("\n", res.differentialConsiderations);
        res.medicationConcerns = String.join("\n", res.medicationReview);
        res.drugInteractionConcerns = String.join("\n", res.allergySafetyConsiderations);
        res.missingInformation = new ArrayList<>(res.questionsForDoctor);

        // Log AI Audit entry in MySQL
        workspaceRepo.logAiAudit(doctorId, patientId, "CLINICAL_DECISION_SUPPORT", "Decision support generated for primary category: " + res.primaryCategory + " (Symptoms: " + (!symRaw.isEmpty() ? symRaw : "General") + ")");

        return res;
    }

    private void addSection(List<Map<String, Object>> list, String id, String title, String subtitle, String icon, String badge, String content, List<String> items, int relevanceScore) {
        Map<String, Object> sec = new LinkedHashMap<>();
        sec.put("id", id);
        sec.put("title", title);
        sec.put("subtitle", subtitle);
        sec.put("icon", icon);
        sec.put("badge", badge);
        sec.put("content", content);
        sec.put("items", items != null ? items : new ArrayList<>());
        sec.put("relevanceScore", relevanceScore);
        list.add(sec);
    }

    private String extractDuration(String s1, String s2) {
        String combined = (s1 + " " + s2).toLowerCase();
        Pattern p = Pattern.compile("\\b(\\d+|one|two|three|four|five|six|seven|eight|nine|ten|a few|several)\\s*(days?|hours?|weeks?|months?|d|h|w|m)\\b");
        Matcher m = p.matcher(combined);
        if (m.find()) {
            return m.group(0);
        }
        if (combined.contains("since yesterday") || combined.contains("yesterday")) return "since yesterday";
        if (combined.contains("since morning")) return "since morning";
        if (combined.contains("today")) return "since today";
        if (combined.contains("sudden") || combined.contains("acute")) return "acute onset";
        return "";
    }

    private boolean hasTemperatureValue(Map<String, Object> vitals) {
        if (vitals == null) return false;
        String val = getVitalValue(vitals, "temperature", "temp");
        if (val == null || val.isEmpty()) return false;
        try {
            double t = Double.parseDouble(val.replaceAll("[^0-9\\.]", ""));
            return t >= 99.5;
        } catch (Exception e) {
            return false;
        }
    }

    private String getVitalValue(Map<String, Object> vitals, String... keys) {
        if (vitals == null) return null;
        for (String k : keys) {
            if (vitals.containsKey(k) && vitals.get(k) != null) {
                String str = String.valueOf(vitals.get(k)).trim();
                if (!str.isEmpty() && !str.equalsIgnoreCase("null") && !str.equalsIgnoreCase("not provided")) {
                    return str;
                }
            }
        }
        return null;
    }

    private String getVitalDisplay(Map<String, Object> vitals, String... keys) {
        String val = getVitalValue(vitals, keys);
        return val != null ? val : "Not provided";
    }

    private String interpretBP(String bp) {
        try {
            String[] parts = bp.split("/");
            int sys = Integer.parseInt(parts[0].replaceAll("[^0-9]", ""));
            int dia = parts.length > 1 ? Integer.parseInt(parts[1].replaceAll("[^0-9]", "")) : 80;
            if (sys >= 180 || dia >= 120) return "within severely elevated / hypertensive crisis range based on the provided measurement — immediate clinician recheck advised";
            if (sys >= 140 || dia >= 90) return "within stage 2 hypertension range based on the provided measurement";
            if (sys >= 130 || dia >= 80) return "within stage 1 hypertension range based on the provided measurement";
            if (sys < 90 || dia < 60) return "within lower / hypotensive range based on the provided measurement";
            return "within typical adult resting blood pressure range based on the provided measurement";
        } catch (Exception e) {
            return "based on the provided measurement";
        }
    }

    private String interpretTemp(String temp) {
        try {
            double t = Double.parseDouble(temp.replaceAll("[^0-9\\.]", ""));
            if (t >= 103.0) return "high-grade fever / pyrexia noted based on the provided measurement";
            if (t >= 100.4) return "fever / pyrexia noted based on the provided measurement";
            if (t >= 99.5) return "low-grade temperature elevation based on the provided measurement";
            if (t < 96.0) return "lower body temperature / hypothermia range based on the provided measurement";
            return "within typical resting body temperature range based on the provided measurement";
        } catch (Exception e) {
            return "based on the provided measurement";
        }
    }

    private String interpretHR(String hr) {
        try {
            int h = Integer.parseInt(hr.replaceAll("[^0-9]", ""));
            if (h > 100) return "elevated pulse / tachycardia range based on the provided measurement";
            if (h < 60) return "lower pulse / bradycardia range based on the provided measurement";
            return "within the typical adult resting range based on the provided measurement";
        } catch (Exception e) {
            return "based on the provided measurement";
        }
    }

    private String interpretSpO2(String spo2) {
        try {
            int s = Integer.parseInt(spo2.replaceAll("[^0-9]", ""));
            if (s < 90) return "significantly reduced oxygen saturation based on the provided measurement — urgent clinician evaluation warranted";
            if (s < 94) return "borderline / mild reduction in oxygen saturation based on the provided measurement";
            return "within typical target range based on the provided measurement";
        } catch (Exception e) {
            return "based on the provided measurement";
        }
    }

    private String interpretRR(String rr) {
        try {
            int r = Integer.parseInt(rr.replaceAll("[^0-9]", ""));
            if (r > 24) return "elevated respiratory rate / tachypnea based on the provided measurement";
            if (r < 12) return "reduced respiratory rate / bradypnea based on the provided measurement";
            return "within typical adult resting respiratory rate range based on the provided measurement";
        } catch (Exception e) {
            return "based on the provided measurement";
        }
    }
}

