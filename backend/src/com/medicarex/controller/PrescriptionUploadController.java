package com.medicarex.controller;

import com.medicarex.model.Patient;
import com.medicarex.model.Pharmacy;
import com.medicarex.model.PrescriptionExtractedMedicine;
import com.medicarex.model.PrescriptionUpload;
import com.medicarex.repository.PatientRepository;
import com.medicarex.repository.PharmacyRepository;
import com.medicarex.repository.PrescriptionUploadRepository;
import com.medicarex.service.MedicineMatchingService;
import com.medicarex.service.OcrProcessingService;
import com.medicarex.util.HttpUtil;
import com.medicarex.util.JsonUtil;
import com.medicarex.util.PdfTextExtractor;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class PrescriptionUploadController implements HttpHandler {
    private final PrescriptionUploadRepository uploadRepo = new PrescriptionUploadRepository();
    private final PatientRepository patientRepo = new PatientRepository();
    private final PharmacyRepository pharmacyRepo = new PharmacyRepository();
    private final OcrProcessingService ocrService = new OcrProcessingService();
    private final MedicineMatchingService matchingService = new MedicineMatchingService();

    private static final String UPLOAD_DIR = "uploads/prescriptions";
    private static final int MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB

    public PrescriptionUploadController() {
        // Ensure upload directory exists
        try {
            Files.createDirectories(Paths.get(UPLOAD_DIR));
        } catch (IOException ignored) {}
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpUtil.handleOptions(exchange)) return;

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if (path.equals("/api/prescriptions/upload") && "POST".equalsIgnoreCase(method)) {
            handleUpload(exchange);
        } else if (path.equals("/api/prescriptions/uploads") && "GET".equalsIgnoreCase(method)) {
            handleListUploads(exchange);
        } else if (path.matches("^/api/prescriptions/uploads/\\d+/confirm$") && "POST".equalsIgnoreCase(method)) {
            String idStr = path.split("/")[4];
            handleConfirm(exchange, Integer.parseInt(idStr));
        } else if (path.matches("^/api/prescriptions/uploads/\\d+/file$") && "GET".equalsIgnoreCase(method)) {
            String idStr = path.split("/")[4];
            handleDownloadFile(exchange, Integer.parseInt(idStr));
        } else if (path.matches("^/api/prescriptions/uploads/\\d+$") && "GET".equalsIgnoreCase(method)) {
            String idStr = path.substring("/api/prescriptions/uploads/".length());
            handleGetUpload(exchange, Integer.parseInt(idStr));
        } else if ((path.equals("/api/prescriptions/search-pharmacies") || path.equals("/api/pharmacies/search-medicines")) && "POST".equalsIgnoreCase(method)) {
            handleSearchPharmaciesForMedicines(exchange);
        } else {
            HttpUtil.sendError(exchange, 404, "Endpoint not found");
        }
    }

    private void handleUpload(HttpExchange exchange) throws IOException {
        Map<String, Object> claims = HttpUtil.authenticate(exchange);
        if (claims == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required to upload prescriptions.");
            return;
        }

        int userId = ((Number) claims.get("userId")).intValue();
        Patient patient = patientRepo.findByUserId(userId);
        int patientId = (patient != null) ? patient.getId() : userId;

        String body = HttpUtil.readRequestBody(exchange);
        if (body == null || body.trim().isEmpty()) {
            HttpUtil.sendError(exchange, 400, "Empty request body");
            return;
        }

        Map<String, Object> req = JsonUtil.parseMap(body);
        String fileName = (String) req.get("fileName");
        String base64Data = (String) req.get("fileData");

        if (fileName == null || fileName.trim().isEmpty()) {
            HttpUtil.sendError(exchange, 400, "File name is required");
            return;
        }

        if (base64Data == null || base64Data.trim().isEmpty()) {
            HttpUtil.sendError(exchange, 400, "Uploaded file is empty");
            return;
        }

        // Clean base64 header if present (e.g. data:image/png;base64,...)
        if (base64Data.contains(",")) {
            base64Data = base64Data.substring(base64Data.indexOf(",") + 1);
        }

        byte[] fileBytes;
        try {
            fileBytes = Base64.getDecoder().decode(base64Data.trim());
        } catch (IllegalArgumentException e) {
            HttpUtil.sendError(exchange, 400, "Invalid base64 encoding");
            return;
        }

        if (fileBytes.length == 0) {
            HttpUtil.sendError(exchange, 400, "Uploaded file is empty (0 bytes)");
            return;
        }

        if (fileBytes.length > MAX_FILE_SIZE) {
            HttpUtil.sendError(exchange, 413, "File size exceeds maximum allowed limit (10MB)");
            return;
        }

        // Validate file type & magic bytes
        String detectedExt = validateMagicBytesAndGetExt(fileBytes, fileName);
        if (detectedExt == null) {
            HttpUtil.sendError(exchange, 400, "Please upload a PDF, JPG, JPEG, or PNG prescription.");
            return;
        }

        // Generate safe server-side storage filename (prevent path traversal)
        String safeStoredName = "rx_" + patientId + "_" + UUID.randomUUID().toString() + "." + detectedExt;
        Path targetPath = Paths.get(UPLOAD_DIR, safeStoredName);
        Files.write(targetPath, fileBytes);

        // Run Text Extraction & OCR
        List<String> extractedLines = new ArrayList<>();
        String rawText = "";

        if ("pdf".equalsIgnoreCase(detectedExt)) {
            rawText = PdfTextExtractor.extractText(fileBytes);
            if (!rawText.isEmpty()) {
                String[] split = rawText.split("\\r?\\n");
                for (String s : split) {
                    if (!s.trim().isEmpty()) extractedLines.add(s.trim());
                }
            }
        } else {
            extractedLines = ocrService.extractTextFromImage(targetPath.toFile());
            rawText = String.join("\n", extractedLines);
        }

        // Match medicines against catalog
        List<PrescriptionExtractedMedicine> extractedMedicines = matchingService.processPrescriptionText(extractedLines);

        // Persist upload metadata and extracted items in database
        PrescriptionUpload upload = new PrescriptionUpload();
        upload.setPatientId(patientId);
        upload.setOriginalFilename(sanitizeFilename(fileName));
        upload.setStoredFilename(safeStoredName);
        upload.setFileType(detectedExt.toUpperCase());
        upload.setFileSize(fileBytes.length);
        upload.setExtractionStatus("COMPLETED");
        upload.setRawText(rawText);

        upload = uploadRepo.create(upload);
        if (upload.getId() > 0) {
            uploadRepo.saveExtractedMedicines(upload.getId(), extractedMedicines);
            upload.setExtractedMedicines(uploadRepo.findMedicinesByUploadId(upload.getId()));
        }

        Map<String, Object> respData = upload.toMap();
        List<String> warnings = new ArrayList<>();
        if (extractedMedicines.isEmpty()) {
            warnings.add("No medicines could be confidently identified. Please review the document or search manually.");
        } else {
            boolean hasLowConf = false;
            for (PrescriptionExtractedMedicine m : extractedMedicines) {
                if (m.getMedicineId() == null || m.getConfidence() < 0.70) {
                    hasLowConf = true;
                    break;
                }
            }
            if (hasLowConf) {
                warnings.add("Some prescription text could not be read clearly. Please verify the medicine names or edit as needed.");
            }
        }
        respData.put("warnings", warnings);
        respData.put("disclaimer", "Extracted from prescription — verify with your prescription/doctor before purchasing.");

        HttpUtil.sendSuccess(exchange, "Prescription processed successfully", respData);
    }

    private void handleListUploads(HttpExchange exchange) throws IOException {
        Map<String, Object> claims = HttpUtil.authenticate(exchange);
        if (claims == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required.");
            return;
        }

        int userId = ((Number) claims.get("userId")).intValue();
        Patient patient = patientRepo.findByUserId(userId);
        int patientId = (patient != null) ? patient.getId() : userId;

        List<PrescriptionUpload> list = uploadRepo.findByPatientId(patientId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (PrescriptionUpload u : list) {
            result.add(u.toMap());
        }

        HttpUtil.sendSuccess(exchange, "Uploads retrieved", result);
    }

    private void handleGetUpload(HttpExchange exchange, int id) throws IOException {
        Map<String, Object> claims = HttpUtil.authenticate(exchange);
        if (claims == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required.");
            return;
        }

        int userId = ((Number) claims.get("userId")).intValue();
        String role = (String) claims.getOrDefault("role", "PATIENT");
        Patient patient = patientRepo.findByUserId(userId);
        int patientId = (patient != null) ? patient.getId() : userId;

        PrescriptionUpload upload = uploadRepo.findById(id);
        if (upload == null) {
            HttpUtil.sendError(exchange, 404, "Upload not found");
            return;
        }

        // IDOR protection: only owning patient (or admin) can view
        if ("PATIENT".equalsIgnoreCase(role) && upload.getPatientId() != patientId) {
            HttpUtil.sendError(exchange, 403, "Access denied. You can only view your own uploads.");
            return;
        }

        HttpUtil.sendSuccess(exchange, "Upload details retrieved", upload.toMap());
    }

    private void handleDownloadFile(HttpExchange exchange, int id) throws IOException {
        Map<String, Object> claims = HttpUtil.authenticate(exchange);
        if (claims == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required.");
            return;
        }

        int userId = ((Number) claims.get("userId")).intValue();
        String role = (String) claims.getOrDefault("role", "PATIENT");
        Patient patient = patientRepo.findByUserId(userId);
        int patientId = (patient != null) ? patient.getId() : userId;

        PrescriptionUpload upload = uploadRepo.findById(id);
        if (upload == null) {
            HttpUtil.sendError(exchange, 404, "Upload not found");
            return;
        }

        // IDOR protection: only owning patient (or admin) can download
        if ("PATIENT".equalsIgnoreCase(role) && upload.getPatientId() != patientId) {
            HttpUtil.sendError(exchange, 403, "Access denied. You can only access your own uploaded prescriptions.");
            return;
        }

        Path filePath = Paths.get(UPLOAD_DIR, upload.getStoredFilename());
        if (!Files.exists(filePath)) {
            HttpUtil.sendError(exchange, 404, "Uploaded file not found on server.");
            return;
        }

        byte[] fileBytes = Files.readAllBytes(filePath);
        String mimeType = "application/octet-stream";
        if ("PDF".equalsIgnoreCase(upload.getFileType())) mimeType = "application/pdf";
        else if ("PNG".equalsIgnoreCase(upload.getFileType())) mimeType = "image/png";
        else if ("JPG".equalsIgnoreCase(upload.getFileType()) || "JPEG".equalsIgnoreCase(upload.getFileType())) mimeType = "image/jpeg";

        HttpUtil.setCorsHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", mimeType);
        exchange.getResponseHeaders().set("Content-Disposition", "inline; filename=\"" + upload.getOriginalFilename() + "\"");
        exchange.sendResponseHeaders(200, fileBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(fileBytes);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleConfirm(HttpExchange exchange, int uploadId) throws IOException {
        Map<String, Object> claims = HttpUtil.authenticate(exchange);
        if (claims == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required.");
            return;
        }

        int userId = ((Number) claims.get("userId")).intValue();
        String role = (String) claims.getOrDefault("role", "PATIENT");
        Patient patient = patientRepo.findByUserId(userId);
        int patientId = (patient != null) ? patient.getId() : userId;

        PrescriptionUpload upload = uploadRepo.findById(uploadId);
        if (upload == null) {
            HttpUtil.sendError(exchange, 404, "Upload not found");
            return;
        }

        if ("PATIENT".equalsIgnoreCase(role) && upload.getPatientId() != patientId) {
            HttpUtil.sendError(exchange, 403, "Access denied.");
            return;
        }

        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);
        List<Object> rawList = (List<Object>) req.get("medicines");

        List<PrescriptionExtractedMedicine> confirmedItems = new ArrayList<>();
        if (rawList != null) {
            for (Object obj : rawList) {
                if (obj instanceof Map) {
                    Map<String, Object> m = (Map<String, Object>) obj;
                    PrescriptionExtractedMedicine item = new PrescriptionExtractedMedicine();
                    item.setUploadId(uploadId);
                    item.setExtractedName((String) m.getOrDefault("name", m.get("extractedName")));
                    item.setExtractedDosage((String) m.getOrDefault("dosage", m.get("extractedDosage")));

                    Number medId = (Number) m.get("medicineId");
                    if (medId == null) medId = (Number) m.get("matchedMedicineId");
                    if (medId != null && medId.intValue() > 0) {
                        item.setMedicineId(medId.intValue());
                    }

                    Number conf = (Number) m.getOrDefault("confidence", 1.0);
                    item.setConfidence(conf != null ? conf.doubleValue() : 1.0);
                    item.setConfirmed(true);
                    confirmedItems.add(item);
                }
            }
        }

        uploadRepo.updateConfirmedMedicines(uploadId, confirmedItems);
        List<PrescriptionExtractedMedicine> updated = uploadRepo.findMedicinesByUploadId(uploadId);

        List<Map<String, Object>> respList = new ArrayList<>();
        for (PrescriptionExtractedMedicine m : updated) {
            respList.add(m.toMap());
        }

        HttpUtil.sendSuccess(exchange, "Medicines confirmed successfully", respList);
    }

    @SuppressWarnings("unchecked")
    private void handleSearchPharmaciesForMedicines(HttpExchange exchange) throws IOException {
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);

        List<Object> rawIds = (List<Object>) req.get("medicineIds");
        List<Integer> medicineIds = new ArrayList<>();
        if (rawIds != null) {
            for (Object o : rawIds) {
                if (o instanceof Number) medicineIds.add(((Number) o).intValue());
                else if (o instanceof String) {
                    try { medicineIds.add(Integer.parseInt((String) o)); } catch (Exception ignored) {}
                }
            }
        }

        if (medicineIds.isEmpty()) {
            HttpUtil.sendError(exchange, 400, "At least one medicine ID is required.");
            return;
        }

        double lat = 12.9716;
        double lng = 77.5946;
        double radiusKm = 25.0;

        if (req.containsKey("latitude") && req.get("latitude") != null) {
            try { lat = Double.parseDouble(req.get("latitude").toString()); } catch (Exception ignored) {}
        }
        if (req.containsKey("longitude") && req.get("longitude") != null) {
            try { lng = Double.parseDouble(req.get("longitude").toString()); } catch (Exception ignored) {}
        }
        if (req.containsKey("radius") && req.get("radius") != null) {
            try { radiusKm = Double.parseDouble(req.get("radius").toString()); } catch (Exception ignored) {}
        }

        // Query all nearby pharmacies
        List<Pharmacy> pharmacies = pharmacyRepo.findNearby(lat, lng, radiusKm, null, null, null);
        List<Map<String, Object>> results = new ArrayList<>();

        for (Pharmacy p : pharmacies) {
            Map<String, Object> pMap = p.toMap();
            List<Map<String, Object>> medicineStockStatus = new ArrayList<>();
            int availableCount = 0;

            for (Integer medId : medicineIds) {
                // Check pharmacy inventory for this specific medicine
                Map<String, Object> stock = pharmacyRepo.getMedicineStockInPharmacy(p.getId(), medId);
                medicineStockStatus.add(stock);
                if (Boolean.TRUE.equals(stock.get("inStock"))) {
                    availableCount++;
                }
            }

            pMap.put("medicineStockList", medicineStockStatus);
            pMap.put("availableMedicineCount", availableCount);
            pMap.put("requestedMedicineCount", medicineIds.size());
            pMap.put("allMedicinesAvailable", availableCount == medicineIds.size());
            pMap.put("hasPartialStock", availableCount > 0 && availableCount < medicineIds.size());

            results.add(pMap);
        }

        // Sort: pharmacies with ALL medicines in stock first, then partial, ordered by distance
        results.sort((a, b) -> {
            boolean aAll = Boolean.TRUE.equals(a.get("allMedicinesAvailable"));
            boolean bAll = Boolean.TRUE.equals(b.get("allMedicinesAvailable"));
            if (aAll != bAll) return aAll ? -1 : 1;

            int aCount = (int) a.get("availableMedicineCount");
            int bCount = (int) b.get("availableMedicineCount");
            if (aCount != bCount) return Integer.compare(bCount, aCount);

            double aDist = (double) a.getOrDefault("distanceKm", 999.0);
            double bDist = (double) b.getOrDefault("distanceKm", 999.0);
            return Double.compare(aDist, bDist);
        });

        HttpUtil.sendSuccess(exchange, "Multi-medicine pharmacy availability retrieved", results);
    }

    private String validateMagicBytesAndGetExt(byte[] bytes, String fileName) {
        if (bytes == null || bytes.length < 4) return null;

        // PDF check: %PDF- (0x25, 0x50, 0x44, 0x46, 0x2D)
        if (bytes.length >= 5 && bytes[0] == 0x25 && bytes[1] == 0x50 && bytes[2] == 0x44 && bytes[3] == 0x46 && bytes[4] == 0x2D) {
            return "pdf";
        }

        // PNG check: 89 50 4E 47 0D 0A 1A 0A
        if (bytes.length >= 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) {
            return "png";
        }

        // JPEG check: FF D8 FF
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "jpg";
        }

        // Also allow valid file extensions if header begins within first 100 bytes (e.g. whitespace before %PDF-)
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".pdf")) {
            String head = new String(bytes, 0, Math.min(bytes.length, 128), StandardCharsets.ISO_8859_1);
            if (head.contains("%PDF-")) return "pdf";
        }

        return null;
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) return "prescription";
        return filename.replaceAll("[^a-zA-Z0-9\\.\\-_ ]", "_");
    }
}
