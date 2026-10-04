package com.medicarex.service;

import com.medicarex.model.Medicine;
import com.medicarex.model.PrescriptionExtractedMedicine;
import com.medicarex.repository.MedicineRepository;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for analyzing prescription text lines, identifying medicine candidates,
 * extracting exact dosage expressions, and matching against the MediCareX medicine catalog.
 */
public class MedicineMatchingService {
    private final MedicineRepository medicineRepo = new MedicineRepository();

    // Regex for common medicine line prefixes (numbering, bullet points, Rx symbols)
    private static final Pattern PREFIX_PATTERN = Pattern.compile("^(?:\\d+[\\.\\)]|[-•*•]|Rx:?|Tab(?:let)?\\.?|Cap(?:sule)?\\.?|Syp\\.?|Inj\\.?)\\s*", Pattern.CASE_INSENSITIVE);

    // Regex for dosage patterns (e.g. 500 mg, 650mg, 10 mcg, 20 ml, 5%, 1.5 g)
    private static final Pattern DOSAGE_PATTERN = Pattern.compile("(\\b\\d+(?:\\.\\d+)?\\s*(?:mg|mcg|g|gm|ml|l|%|iu|units?)\\b|\\b\\d+\\s*(?:tab(?:let)?s?|cap(?:sule)?s?|drops?)\\b)", Pattern.CASE_INSENSITIVE);

    // Header keywords to ignore
    private static final Set<String> IGNORED_HEADER_KEYWORDS = new HashSet<>(Arrays.asList(
            "prescription", "hospital", "clinic", "doctor", "dr.", "patient", "name:", "age:",
            "gender:", "date:", "diagnosis:", "notes:", "signature", "phone:", "reg:", "license",
            "clinical", "medicarex", "electronic", "valid", "instructions", "findings", "address",
            "certified", "telemedicine", "compliance", "council", "standards", "network", "issued",
            "consultation", "record", "provider", "details", "hypertension", "infection", "acute"
    ));

    public List<PrescriptionExtractedMedicine> processPrescriptionText(List<String> textLines) {
        List<PrescriptionExtractedMedicine> result = new ArrayList<>();
        if (textLines == null || textLines.isEmpty()) {
            return result;
        }

        // Fetch all active medicines from database catalog for matching
        List<Medicine> catalog = medicineRepo.findAll();
        Map<String, PrescriptionExtractedMedicine> deduplicationMap = new LinkedHashMap<>();

        for (String line : textLines) {
            if (line == null) continue;
            String trimmed = line.trim();
            if (trimmed.length() < 3) continue;

            // Check if this line is an ignored header/metadata line
            if (isHeaderOrMetadataLine(trimmed)) {
                continue;
            }

            // Clean line of list prefixes
            String cleanedLine = PREFIX_PATTERN.matcher(trimmed).replaceFirst("").trim();
            if (cleanedLine.isEmpty()) continue;

            // Extract dosage if present
            String extractedDosage = "";
            Matcher dosageMatcher = DOSAGE_PATTERN.matcher(cleanedLine);
            if (dosageMatcher.find()) {
                extractedDosage = dosageMatcher.group(1).trim();
            }

            // Extract candidate medicine name
            String candidateName = extractCandidateMedicineName(cleanedLine, extractedDosage);
            if (candidateName.length() < 2) continue;

            // Match against database medicine catalog
            MatchResult match = findBestCatalogMatch(candidateName, catalog);

            PrescriptionExtractedMedicine extracted = new PrescriptionExtractedMedicine();
            extracted.setExtractedName(candidateName);
            extracted.setExtractedDosage(extractedDosage);

            if (match.medicine != null && match.confidence >= 0.70) {
                extracted.setMedicineId(match.medicine.getId());
                extracted.setConfidence(match.confidence);
                extracted.setCatalogName(match.medicine.getName());
                extracted.setGenericName(match.medicine.getGenericName());
                extracted.setBrand(match.medicine.getBrand());
                extracted.setCategory(match.medicine.getCategory());
                extracted.setBasePrice(match.medicine.getBasePrice());
                extracted.setTotalStock(match.medicine.getTotalStock());
                extracted.setPharmacyCount(match.medicine.getPharmacyCount());
                extracted.setConfirmed(true);
            } else {
                // If it has neither catalog match nor dosage and is not a clear prescription list line, ignore it
                if (extractedDosage.isEmpty() && !trimmed.matches("^(?:\\d+[\\.\\)]|[-•*•]|Rx:?).*")) {
                    continue;
                }
                // Low confidence / uncertain medicine -> flag for patient review
                extracted.setMedicineId(null);
                extracted.setConfidence(Math.max(0.35, match.confidence));
                extracted.setConfirmed(false);
            }

            // Deduplication key: Medicine ID (if matched) or Candidate Name + Dosage
            String dedupKey = (extracted.getMedicineId() != null)
                    ? "ID_" + extracted.getMedicineId()
                    : "NAME_" + candidateName.toLowerCase() + "_" + extractedDosage.toLowerCase();

            if (!deduplicationMap.containsKey(dedupKey)) {
                deduplicationMap.put(dedupKey, extracted);
            }
        }

        result.addAll(deduplicationMap.values());
        return result;
    }

    private boolean isHeaderOrMetadataLine(String line) {
        String lower = line.toLowerCase();
        for (String kw : IGNORED_HEADER_KEYWORDS) {
            if (lower.startsWith(kw) || (lower.contains(kw) && (lower.contains(":") || lower.contains("hospital") || lower.contains("clinic")))) {
                // Verify it doesn't also contain a clear medicine name
                if (!lower.contains("mg") && !lower.contains("amoxicillin") && !lower.contains("paracetamol")) {
                    return true;
                }
            }
        }
        return false;
    }

    private String extractCandidateMedicineName(String line, String dosage) {
        String name = line;

        // Cut off after dosage or frequency keywords if found
        int dosageIdx = (!dosage.isEmpty()) ? name.indexOf(dosage) : -1;
        if (dosageIdx > 0) {
            name = name.substring(0, dosageIdx);
        } else {
            // Cut off delimiters like '-', '—', '|', ':', '(', '1 tab', 'tid', 'bid', 'od'
            String[] parts = name.split("[-—|:;\\(]|\\b(?:once|twice|thrice|daily|tab|cap|tid|bid|od|qid|prn|for|before|after)\\b", 2);
            if (parts.length > 0) {
                name = parts[0];
            }
        }

        // Clean extra symbols
        name = name.replaceAll("[^a-zA-Z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
        return name;
    }

    private MatchResult findBestCatalogMatch(String candidate, List<Medicine> catalog) {
        if (catalog == null || catalog.isEmpty() || candidate.isEmpty()) {
            return new MatchResult(null, 0.0);
        }

        String candLower = candidate.toLowerCase();
        Medicine bestMatch = null;
        double bestScore = 0.0;

        for (Medicine med : catalog) {
            String medNameLower = med.getName().toLowerCase();
            String genericLower = (med.getGenericName() != null) ? med.getGenericName().toLowerCase() : "";
            String brandLower = (med.getBrand() != null) ? med.getBrand().toLowerCase() : "";

            // 1. Exact match with Brand Name or Full Name
            if (candLower.equals(medNameLower) || candLower.equals(brandLower) || candLower.equals(genericLower)) {
                return new MatchResult(med, 0.98);
            }

            // 2. Substring or Contains match
            if (candLower.contains(medNameLower) || medNameLower.contains(candLower) ||
                (!genericLower.isEmpty() && (candLower.contains(genericLower) || genericLower.contains(candLower)))) {
                double score = 0.92;
                if (score > bestScore) {
                    bestScore = score;
                    bestMatch = med;
                }
            }

            // 3. Word token overlap match (e.g. "Amoxicillin 500" vs "Amoxicillin 500mg")
            String[] candTokens = candLower.split("\\s+");
            for (String token : candTokens) {
                if (token.length() >= 4 && (medNameLower.contains(token) || genericLower.contains(token) || brandLower.contains(token))) {
                    double score = 0.88;
                    if (score > bestScore) {
                        bestScore = score;
                        bestMatch = med;
                    }
                }
            }

            // 4. Fuzzy Levenshtein similarity for OCR character variations (e.g. 'Amoxici11in' or 'Cetirizne')
            double levScore = calculateSimilarity(candLower, medNameLower);
            if (!genericLower.isEmpty()) {
                levScore = Math.max(levScore, calculateSimilarity(candLower, genericLower));
            }
            if (levScore > 0.80 && levScore > bestScore) {
                bestScore = levScore;
                bestMatch = med;
            }
        }

        return new MatchResult(bestMatch, bestScore);
    }

    private double calculateSimilarity(String s1, String s2) {
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) return 1.0;
        int distance = computeLevenshteinDistance(s1, s2);
        return 1.0 - ((double) distance / maxLen);
    }

    private int computeLevenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= s2.length(); j++) dp[0][j] = j;

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[s1.length()][s2.length()];
    }

    private static class MatchResult {
        final Medicine medicine;
        final double confidence;

        MatchResult(Medicine medicine, double confidence) {
            this.medicine = medicine;
            this.confidence = confidence;
        }
    }
}
