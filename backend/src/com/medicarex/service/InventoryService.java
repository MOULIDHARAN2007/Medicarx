package com.medicarex.service;

import com.medicarex.model.InventoryItem;
import com.medicarex.model.Medicine;
import com.medicarex.repository.InventoryRepository;
import com.medicarex.repository.MedicineRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InventoryService {
    private final InventoryRepository inventoryRepo = new InventoryRepository();
    private final MedicineRepository medicineRepo = new MedicineRepository();

    public Map<String, Object> importInventoryRows(int pharmacyId, List<Map<String, Object>> rows, Map<String, Integer> medicineCatalogMap) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> validRows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int validationFailed = 0;

        if (medicineCatalogMap == null) {
            medicineCatalogMap = new HashMap<>();
        }

        if (rows == null || rows.isEmpty()) {
            result.put("success", true);
            result.put("totalRows", 0);
            result.put("totalProcessed", 0);
            result.put("imported", 0);
            result.put("uniqueMedicines", 0);
            result.put("distinctMedicines", 0);
            result.put("medicinesCreated", 0);
            result.put("medicinesMatched", 0);
            result.put("inventoryAdded", 0);
            result.put("inventoryUpdated", 0);
            result.put("updated", 0);
            result.put("created", 0);
            result.put("added", 0);
            result.put("failed", 0);
            result.put("errors", errors);
            return result;
        }

        Set<Integer> uniqueMedIds = new HashSet<>();
        Set<Integer> newlyCreatedMedIds = new HashSet<>();

        int rowNum = 1;
        for (Map<String, Object> row : rows) {
            rowNum++;
            String medName = (String) row.get("medicineName");
            if (medName == null || medName.trim().isEmpty()) {
                medName = (String) row.get("medicine_name");
            }
            if (medName == null || medName.trim().isEmpty()) {
                medName = (String) row.get("name");
            }

            if (medName == null || medName.trim().isEmpty()) {
                validationFailed++;
                if (errors.size() < 500) errors.add("Missing medicine name in row " + rowNum);
                continue;
            }

            String trimmedMedName = medName.trim();

            // Quantity
            Object qtyObj = row.get("stockQuantity");
            if (qtyObj == null) qtyObj = row.get("stock_quantity");
            if (qtyObj == null) qtyObj = row.get("quantity");
            if (qtyObj == null) qtyObj = row.get("qty");

            int qty;
            try {
                if (qtyObj instanceof Number) {
                    qty = ((Number) qtyObj).intValue();
                } else if (qtyObj instanceof String) {
                    qty = Integer.parseInt(((String) qtyObj).trim());
                } else {
                    qty = -1;
                }
            } catch (Exception e) {
                qty = -1;
            }

            if (qty < 0) {
                validationFailed++;
                if (errors.size() < 500) errors.add("Invalid stock quantity for " + trimmedMedName);
                continue;
            }

            // Unit Price
            Object priceObj = row.get("unitPrice");
            if (priceObj == null) priceObj = row.get("unit_price");
            if (priceObj == null) priceObj = row.get("price");
            if (priceObj == null) priceObj = row.get("rate");

            double price;
            try {
                if (priceObj instanceof Number) {
                    price = ((Number) priceObj).doubleValue();
                } else if (priceObj instanceof String) {
                    price = Double.parseDouble(((String) priceObj).trim().replace("$", "").replace("₹", ""));
                } else {
                    price = -1.0;
                }
            } catch (Exception e) {
                price = -1.0;
            }

            if (price < 0) {
                validationFailed++;
                if (errors.size() < 500) errors.add("Invalid unit price for " + trimmedMedName);
                continue;
            }

            // Category from CSV if provided
            String category = (String) row.get("category");
            if (category == null || category.trim().isEmpty()) category = (String) row.get("cat");
            if (category != null) category = category.trim();

            // Generic Name from CSV if provided
            String genericName = (String) row.get("genericName");
            if (genericName == null || genericName.trim().isEmpty()) genericName = (String) row.get("generic_name");
            if (genericName == null || genericName.trim().isEmpty()) genericName = (String) row.get("generic");
            if (genericName != null) genericName = genericName.trim();

            // Find or Auto-Create Medicine Catalog Record
            Integer medId = findMedicineId(trimmedMedName, medicineCatalogMap);

            if (medId == null) {
                // Safely derive dosage, form, generic name from medicine name
                String dosage = extractDosage(trimmedMedName);
                String form = extractForm(trimmedMedName);
                String derivedGenName = (genericName != null && !genericName.isEmpty()) ? genericName : extractGenericName(trimmedMedName);
                String derivedCat = (category != null && !category.isEmpty()) ? category : "General";

                Medicine createdMed = medicineRepo.createQuick(trimmedMedName, derivedGenName, derivedCat, dosage, form, price);
                if (createdMed != null) {
                    medId = createdMed.getId();
                    registerMedicineInCatalogMap(createdMed.getName(), createdMed.getGenericName(), medId, medicineCatalogMap);
                    newlyCreatedMedIds.add(medId);
                } else {
                    validationFailed++;
                    if (errors.size() < 500) errors.add("Could not create catalog medicine: " + trimmedMedName);
                    continue;
                }
            }

            uniqueMedIds.add(medId);

            // Batch Number
            String batch = (String) row.get("batchNumber");
            if (batch == null || batch.trim().isEmpty()) batch = (String) row.get("batch_number");
            if (batch == null || batch.trim().isEmpty()) batch = (String) row.get("batch");
            if (batch == null || batch.trim().isEmpty()) batch = "BATCH-" + medId + "-" + (int)(Math.random() * 9000 + 1000);

            // Expiry Date
            String rawExpiry = (String) row.get("expiryDate");
            if (rawExpiry == null || rawExpiry.trim().isEmpty()) rawExpiry = (String) row.get("expiry_date");
            if (rawExpiry == null || rawExpiry.trim().isEmpty()) rawExpiry = (String) row.get("expiry");
            if (rawExpiry == null || rawExpiry.trim().isEmpty()) rawExpiry = (String) row.get("exp");
            
            String expiry = normalizeExpiryDate(rawExpiry);
            if (expiry == null) {
                validationFailed++;
                if (errors.size() < 500) errors.add("Invalid expiry date for " + trimmedMedName + " (" + rawExpiry + ")");
                continue;
            }

            Map<String, Object> validRow = new HashMap<>();
            validRow.put("medicineId", medId);
            validRow.put("medicineName", trimmedMedName);
            validRow.put("stockQuantity", qty);
            validRow.put("unitPrice", price);
            validRow.put("batchNumber", batch.trim());
            validRow.put("expiryDate", expiry);
            validRows.add(validRow);
        }

        Map<String, Object> dbResult = inventoryRepo.importCsvBatch(pharmacyId, validRows);
        int added = (Integer) dbResult.getOrDefault("added", 0);
        int updated = (Integer) dbResult.getOrDefault("updated", 0);
        int validCount = validRows.size();
        int distinctMedicines = (Integer) dbResult.getOrDefault("distinctMedicines", uniqueMedIds.size());
        int medicinesCreated = newlyCreatedMedIds.size();
        int medicinesMatched = Math.max(0, uniqueMedIds.size() - medicinesCreated);

        result.put("success", true);
        result.put("totalRows", rows.size());
        result.put("totalProcessed", rows.size());
        result.put("imported", validCount);
        result.put("uniqueMedicines", distinctMedicines);
        result.put("distinctMedicines", distinctMedicines);
        result.put("medicinesCreated", medicinesCreated);
        result.put("medicinesMatched", medicinesMatched);
        result.put("inventoryAdded", added);
        result.put("inventoryUpdated", updated);
        result.put("updated", updated);
        result.put("created", added);
        result.put("added", added);
        result.put("failed", validationFailed);
        result.put("errors", errors);
        if (dbResult.containsKey("error")) {
            result.put("dbError", dbResult.get("error"));
        }

        return result;
    }

    private void registerMedicineInCatalogMap(String name, String genericName, int medId, Map<String, Integer> map) {
        if (map == null) return;
        if (name != null && !name.trim().isEmpty()) {
            String cleanName = name.trim().toLowerCase();
            map.put(cleanName, medId);
            map.put(cleanName.replaceAll("\\s+", " "), medId);
            map.put(cleanName.replaceAll("[^a-z0-9]", ""), medId);
        }
        if (genericName != null && !genericName.trim().isEmpty()) {
            String cleanGen = genericName.trim().toLowerCase();
            map.putIfAbsent(cleanGen, medId);
            map.putIfAbsent(cleanGen.replaceAll("\\s+", " "), medId);
            map.putIfAbsent(cleanGen.replaceAll("[^a-z0-9]", ""), medId);
        }
    }

    private String extractDosage(String medName) {
        if (medName == null) return "Standard";
        Pattern p = Pattern.compile("(\\d+(?:\\.\\d+)?\\s*(?:mg|g|mcg|ml|iu|IU|%|units|gm|tabs?|caps?))", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(medName);
        if (m.find()) {
            return m.group(1).trim();
        }
        Pattern numPattern = Pattern.compile("\\b(\\d+)\\b");
        Matcher numMatcher = numPattern.matcher(medName);
        if (numMatcher.find()) {
            return numMatcher.group(1) + "mg";
        }
        return "Standard";
    }

    private String extractForm(String medName) {
        if (medName == null) return "Tablet";
        String lower = medName.toLowerCase();
        if (lower.contains("capsule") || lower.contains("cap")) return "Capsule";
        if (lower.contains("syrup") || lower.contains("liquid") || lower.contains("suspension")) return "Syrup";
        if (lower.contains("injection") || lower.contains("inj")) return "Injection";
        if (lower.contains("ointment") || lower.contains("cream") || lower.contains("gel")) return "Ointment";
        if (lower.contains("drop")) return "Drops";
        if (lower.contains("inhaler") || lower.contains("spray")) return "Inhaler";
        if (lower.contains("powder")) return "Powder";
        return "Tablet";
    }

    private String extractGenericName(String medName) {
        if (medName == null || medName.trim().isEmpty()) return "Generic";
        String cleaned = medName.replaceAll("\\([^)]*\\)", "").trim();
        cleaned = cleaned.replaceAll("(?i)\\b\\d+(?:\\.\\d+)?\\s*(?:mg|g|mcg|ml|iu|IU|%|units|gm|tabs?|caps?)\\b", "").trim();
        cleaned = cleaned.replaceAll("\\b\\d+\\b", "").trim();
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        if (cleaned.isEmpty()) return medName.trim();
        return cleaned;
    }

    private String normalizeExpiryDate(String rawDate) {
        if (rawDate == null || rawDate.trim().isEmpty()) return "2028-12-31";
        String d = rawDate.trim();
        if (d.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
            return d;
        }
        if (d.matches("^\\d{4}/\\d{2}/\\d{2}$")) {
            return d.replace('/', '-');
        }
        if (d.matches("^\\d{2}-\\d{2}-\\d{4}$")) {
            String[] parts = d.split("-");
            return parts[2] + "-" + parts[1] + "-" + parts[0];
        }
        if (d.matches("^\\d{2}/\\d{2}/\\d{4}$")) {
            String[] parts = d.split("/");
            return parts[2] + "-" + parts[1] + "-" + parts[0];
        }
        return null;
    }

    private Integer findMedicineId(String medName, Map<String, Integer> catalogMap) {
        if (catalogMap == null || medName == null) return null;
        String lower = medName.toLowerCase().trim();
        Integer id = catalogMap.get(lower);
        if (id != null) return id;

        String normalizedSpace = lower.replaceAll("\\s+", " ");
        id = catalogMap.get(normalizedSpace);
        if (id != null) return id;

        String stripped = lower.replaceAll("[^a-z0-9]", "");
        id = catalogMap.get(stripped);
        if (id != null) return id;

        return null;
    }

    public List<InventoryItem> getInventory(Integer pharmacyId, String category, String status, String query) {
        if (pharmacyId != null) {
            return inventoryRepo.findByPharmacyId(pharmacyId, category, status, query);
        }
        return inventoryRepo.findAll(null, status, query);
    }

    public InventoryItem getById(int id) {
        return inventoryRepo.findById(id);
    }

    public InventoryItem findByPharmacyAndMedicine(int pharmacyId, int medicineId) {
        return inventoryRepo.findByPharmacyAndMedicine(pharmacyId, medicineId);
    }

    public boolean updateStock(int inventoryId, int quantity, double unitPrice, String status, String expiryDate) {
        if (quantity < 0 || unitPrice < 0) return false;
        return inventoryRepo.updateStock(inventoryId, quantity, unitPrice, status, expiryDate);
    }

    public boolean addOrUpdateStock(int pharmacyId, int medicineId, String batchNumber, int quantity, double unitPrice, String expiryDate) {
        if (pharmacyId <= 0 || medicineId <= 0 || quantity < 0 || unitPrice < 0) return false;
        return inventoryRepo.addOrUpdateItem(pharmacyId, medicineId, batchNumber, quantity, unitPrice, expiryDate);
    }

    public boolean increaseStock(int inventoryId, int quantityToAdd) {
        if (quantityToAdd <= 0) return false;
        return inventoryRepo.increaseStock(inventoryId, quantityToAdd);
    }

    public boolean delete(int id) {
        return inventoryRepo.delete(id);
    }

    public Map<String, Object> getPharmacyStats(int pharmacyId) {
        return inventoryRepo.getPharmacyStockStats(pharmacyId);
    }

    public List<InventoryItem> getExpiringSoon(int pharmacyId) {
        return inventoryRepo.getExpiringSoon(pharmacyId);
    }
}
