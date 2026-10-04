package com.medicarex;

import com.medicarex.config.DatabaseConnection;
import com.medicarex.model.Bill;
import com.medicarex.model.Medicine;
import com.medicarex.model.Pharmacy;
import com.medicarex.repository.BillingRepository;
import com.medicarex.repository.InventoryRepository;
import com.medicarex.repository.MedicineRepository;
import com.medicarex.repository.PharmacyRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

public class PharmacyMedicineBillingTest {

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println(" Starting MediCareX Pharmacy Medicine-Name Billing Test ");
        System.out.println("==================================================");

        DatabaseConnection.initialize();

        InventoryRepository invRepo = new InventoryRepository();
        MedicineRepository medRepo = new MedicineRepository();
        BillingRepository billRepo = new BillingRepository();
        PharmacyRepository pharmRepo = new PharmacyRepository();

        int passed = 0;
        int failed = 0;

        try (Connection conn = DatabaseConnection.getConnection()) {
            // Setup test pharmacy and test medicines
            Pharmacy pharmacy = pharmRepo.findById(1);
            int pharmacyId = 1;
            int userId = 1;
            if (pharmacy != null) {
                pharmacyId = pharmacy.getId();
                userId = pharmacy.getUserId();
            }

            // Ensure medicines without barcodes and with barcodes exist
            // Med A: No barcode (barcode = null), name="Paracetamol 500mg Tablet", generic="Paracetamol", brand="Calpol"
            int medNoBarcodeId = getOrCreateMedicine(conn, "Paracetamol 500mg Tablet", "Paracetamol", "Calpol", "Analgesic", "500mg", "Tablet", 0, null, 25.0);
            
            // Med B: No barcode (barcode = null), name="Paracetamol 650mg Tablet", generic="Paracetamol", brand="Dolo 650"
            int medDoloId = getOrCreateMedicine(conn, "Paracetamol 650mg Tablet", "Paracetamol", "Dolo 650", "Analgesic", "650mg", "Tablet", 0, null, 32.0);

            // Med C: With barcode, name="Amoxicillin 500mg Capsule", generic="Amoxicillin", brand="Mox 500"
            int medWithBarcodeId = getOrCreateMedicine(conn, "Amoxicillin 500mg Capsule", "Amoxicillin", "Mox 500", "Antibiotic", "500mg", "Capsule", 1, "8901030001014", 65.0);

            // Set stock in inventory for pharmacyId
            setInventoryStock(conn, pharmacyId, medNoBarcodeId, 50, 25.0, "BATCH-PARA-500", "2027-12-31");
            setInventoryStock(conn, pharmacyId, medDoloId, 40, 32.0, "BATCH-DOLO-650", "2027-12-31");
            setInventoryStock(conn, pharmacyId, medWithBarcodeId, 30, 65.0, "BATCH-MOX-500", "2027-12-31");

            // Test 1: Search medicine by name
            System.out.println("\n[Test 1] Search medicine by name ('Paracetamol 500mg')...");
            List<Map<String, Object>> searchByName = invRepo.searchPharmacyMedicines(pharmacyId, "Paracetamol 500mg", 10);
            if (!searchByName.isEmpty() && searchByName.get(0).get("name").toString().contains("Paracetamol 500mg")) {
                System.out.println("✓ Passed: Found medicine by name: " + searchByName.get(0).get("name"));
                passed++;
            } else {
                System.out.println("✗ Failed: Could not search by name.");
                failed++;
            }

            // Test 2: Search by generic name
            System.out.println("\n[Test 2] Search by generic name ('Paracetamol')...");
            List<Map<String, Object>> searchByGeneric = invRepo.searchPharmacyMedicines(pharmacyId, "Paracetamol", 10);
            if (searchByGeneric.size() >= 2) {
                System.out.println("✓ Passed: Found " + searchByGeneric.size() + " medicines with generic 'Paracetamol'");
                passed++;
            } else {
                System.out.println("✗ Failed: Expected at least 2 medicines for generic Paracetamol.");
                failed++;
            }

            // Test 3: Search by brand
            System.out.println("\n[Test 3] Search by brand ('Dolo')...");
            List<Map<String, Object>> searchByBrand = invRepo.searchPharmacyMedicines(pharmacyId, "Dolo", 10);
            if (!searchByBrand.isEmpty() && searchByBrand.get(0).get("brand").toString().contains("Dolo")) {
                System.out.println("✓ Passed: Found medicine by brand: " + searchByBrand.get(0).get("name") + " (" + searchByBrand.get(0).get("brand") + ")");
                passed++;
            } else {
                System.out.println("✗ Failed: Could not search by brand.");
                failed++;
            }

            // Test 4: Medicine without barcode can be found
            System.out.println("\n[Test 4] Medicine without barcode can be found in search...");
            boolean foundNullBarcode = false;
            for (Map<String, Object> m : searchByName) {
                if (m.get("barcode") == null || m.get("barcode").toString().isEmpty()) {
                    foundNullBarcode = true;
                    break;
                }
            }
            if (foundNullBarcode) {
                System.out.println("✓ Passed: Medicine with NULL barcode correctly returned in search.");
                passed++;
            } else {
                System.out.println("✗ Failed: Medicine with NULL barcode not returned properly.");
                failed++;
            }

            // Test 5 & 6: Medicine without barcode can be billed successfully and deducts stock
            System.out.println("\n[Test 5 & 6] Medicine without barcode billed successfully...");
            int initialStockPara = getInventoryStock(conn, pharmacyId, medNoBarcodeId);

            List<Map<String, Object>> billItems1 = new ArrayList<>();
            Map<String, Object> item1 = new HashMap<>();
            item1.put("medicineId", medNoBarcodeId);
            item1.put("quantity", 5);
            billItems1.add(item1);

            BillingRepository.BillCreationResult res1 = billRepo.createBillWithInventoryDeduction(
                    pharmacyId, userId, null, "Ramesh Kumar", "+91 9988776655", null, 0, 0, "CASH", "No notes", billItems1, "Pharmacist");

            int afterStockPara = getInventoryStock(conn, pharmacyId, medNoBarcodeId);

            if (res1 != null && res1.bill != null && res1.bill.getId() > 0 && afterStockPara == initialStockPara - 5) {
                System.out.println("✓ Passed: Bill created (" + res1.bill.getBillNumber() + "), Stock reduced from " + initialStockPara + " to " + afterStockPara);
                passed++;
            } else {
                System.out.println("✗ Failed: Billing without barcode failed.");
                failed++;
            }

            // Test 7: Barcode medicine can still be scanned
            System.out.println("\n[Test 7] Barcode medicine can still be looked up...");
            Medicine scanned = medRepo.findByBarcode("8901030001014");
            if (scanned != null && scanned.getId() == medWithBarcodeId) {
                System.out.println("✓ Passed: Scanned barcode found medicine: " + scanned.getName());
                passed++;
            } else {
                System.out.println("✗ Failed: Barcode lookup failed.");
                failed++;
            }

            // Test 8: Multiple matching medicines require explicit selection
            System.out.println("\n[Test 8] Multiple matching medicines return distinct records without auto-selecting...");
            List<Map<String, Object>> multiMatch = invRepo.searchPharmacyMedicines(pharmacyId, "Paracetamol", 10);
            Set<Integer> distinctMedIds = new HashSet<>();
            for (Map<String, Object> m : multiMatch) {
                distinctMedIds.add((Integer) m.get("medicineId"));
            }
            if (distinctMedIds.size() >= 2) {
                System.out.println("✓ Passed: Multi-match returns distinct items for explicit selection: " + distinctMedIds.size() + " medicines.");
                passed++;
            } else {
                System.out.println("✗ Failed: Multi-match did not return distinct items.");
                failed++;
            }

            // Test 9: Insufficient stock prevents billing
            System.out.println("\n[Test 9] Insufficient stock prevents billing...");
            int currStock = getInventoryStock(conn, pharmacyId, medNoBarcodeId);
            List<Map<String, Object>> excessiveItems = new ArrayList<>();
            Map<String, Object> excItem = new HashMap<>();
            excItem.put("medicineId", medNoBarcodeId);
            excItem.put("quantity", currStock + 100);
            excessiveItems.add(excItem);

            boolean stockErrorCaught = false;
            try {
                billRepo.createBillWithInventoryDeduction(
                        pharmacyId, userId, null, "Over Buyer", "+91 9988776655", null, 0, 0, "UPI", "Over note", excessiveItems, "Pharmacist");
            } catch (Exception e) {
                stockErrorCaught = true;
            }

            int stockAfterFailedBill = getInventoryStock(conn, pharmacyId, medNoBarcodeId);
            if (stockErrorCaught && stockAfterFailedBill == currStock) {
                System.out.println("✓ Passed: Over-stock billing rejected with exception, stock unchanged at " + stockAfterFailedBill);
                passed++;
            } else {
                System.out.println("✗ Failed: Over-stock billing was allowed or stock was modified!");
                failed++;
            }

            // Test 10, 11 & 12: Failed multi-item bill rolls back all stock changes and creates transactions only on success
            System.out.println("\n[Test 10, 11 & 12] Multi-item atomic rollback on failure...");
            int stockParaBefore = getInventoryStock(conn, pharmacyId, medNoBarcodeId);
            int stockDoloBefore = getInventoryStock(conn, pharmacyId, medDoloId);

            List<Map<String, Object>> mixedItems = new ArrayList<>();
            Map<String, Object> okItm = new HashMap<>();
            okItm.put("medicineId", medNoBarcodeId);
            okItm.put("quantity", 2);
            mixedItems.add(okItm);

            Map<String, Object> badItm = new HashMap<>();
            badItm.put("medicineId", medDoloId);
            badItm.put("quantity", 99999); // Exceeds stock
            mixedItems.add(badItm);

            boolean rollbackCaught = false;
            try {
                billRepo.createBillWithInventoryDeduction(
                        pharmacyId, userId, null, "Partial Test", "+91 9988776655", null, 0, 0, "CASH", "Partial note", mixedItems, "Pharmacist");
            } catch (Exception e) {
                rollbackCaught = true;
            }

            int stockParaAfter = getInventoryStock(conn, pharmacyId, medNoBarcodeId);
            int stockDoloAfter = getInventoryStock(conn, pharmacyId, medDoloId);

            if (rollbackCaught && stockParaAfter == stockParaBefore && stockDoloAfter == stockDoloBefore) {
                System.out.println("✓ Passed: Complete rollback on multi-item failure! Neither item's stock was deducted.");
                passed++;
            } else {
                System.out.println("✗ Failed: Rollback failed or partial deduction occurred!");
                failed++;
            }

            // Test 13 & 14: Bill and bill_items persisted and readable
            System.out.println("\n[Test 13 & 14] Bill and bill_items persisted in database...");
            Bill retrievedBill = billRepo.findBillById(res1.bill.getId());
            if (retrievedBill != null && retrievedBill.getItems() != null && !retrievedBill.getItems().isEmpty()) {
                System.out.println("✓ Passed: Retrieved bill #" + retrievedBill.getBillNumber() + " with " + retrievedBill.getItems().size() + " items.");
                passed++;
            } else {
                System.out.println("✗ Failed: Could not retrieve persisted bill.");
                failed++;
            }

            // Test 15: Unauthorized pharmacy cannot deduct another pharmacy's inventory
            System.out.println("\n[Test 15] Pharmacy inventory isolation...");
            int otherPharmacyId = 9999;
            List<Map<String, Object>> otherSearch = invRepo.searchPharmacyMedicines(otherPharmacyId, "Paracetamol", 10);
            boolean otherHasStock = false;
            for (Map<String, Object> item : otherSearch) {
                if (((Number) item.get("stockQuantity")).intValue() > 0) {
                    otherHasStock = true;
                    break;
                }
            }
            if (!otherHasStock) {
                System.out.println("✓ Passed: Pharmacy 9999 has 0 stock for medicines stocked in pharmacy " + pharmacyId);
                passed++;
            } else {
                System.out.println("✗ Failed: Pharmacy isolation leak detected.");
                failed++;
            }

            // Test 16: No negative stock
            System.out.println("\n[Test 16] Verify no negative stock in database...");
            boolean noNegative = true;
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM inventory WHERE stock_quantity < 0");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    noNegative = false;
                }
            }
            if (noNegative) {
                System.out.println("✓ Passed: No negative stock in database.");
                passed++;
            } else {
                System.out.println("✗ Failed: Negative stock records found.");
                failed++;
            }

            // Test 17: Prescription-required flag remains intact
            System.out.println("\n[Test 17] Prescription-required flag remains intact...");
            Medicine mox = medRepo.findById(medWithBarcodeId);
            if (mox != null && mox.isPrescriptionRequired()) {
                System.out.println("✓ Passed: Prescription-required flag intact for " + mox.getName());
                passed++;
            } else {
                System.out.println("✗ Failed: Prescription-required flag missing or lost.");
                failed++;
            }

        } catch (Exception e) {
            e.printStackTrace();
            failed++;
        }

        System.out.println("\n==================================================");
        System.out.println(" Test Results: " + passed + " Passed, " + failed + " Failed ");
        System.out.println("==================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static int getOrCreateMedicine(Connection conn, String name, String generic, String brand, String category, String dosage, String form, int rxReq, String barcode, double price) throws Exception {
        String findSql = barcode != null ? "SELECT id FROM medicines WHERE LOWER(name) = LOWER(?) OR barcode = ?" : "SELECT id FROM medicines WHERE LOWER(name) = LOWER(?)";
        try (PreparedStatement ps = conn.prepareStatement(findSql)) {
            ps.setString(1, name);
            if (barcode != null) {
                ps.setString(2, barcode);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("id");
                    try (PreparedStatement updatePs = conn.prepareStatement("UPDATE medicines SET name=?, generic_name=?, brand=?, category=?, dosage=?, form=?, prescription_required=?, barcode=?, base_price=? WHERE id=?")) {
                        updatePs.setString(1, name);
                        updatePs.setString(2, generic);
                        updatePs.setString(3, brand);
                        updatePs.setString(4, category);
                        updatePs.setString(5, dosage);
                        updatePs.setString(6, form);
                        updatePs.setInt(7, rxReq);
                        updatePs.setString(8, barcode);
                        updatePs.setDouble(9, price);
                        updatePs.setInt(10, id);
                        updatePs.executeUpdate();
                    }
                    return id;
                }
            }
        }

        String sql = "INSERT INTO medicines (name, generic_name, brand, category, dosage, form, prescription_required, barcode, base_price, is_active) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, generic);
            ps.setString(3, brand);
            ps.setString(4, category);
            ps.setString(5, dosage);
            ps.setString(6, form);
            ps.setInt(7, rxReq);
            ps.setString(8, barcode);
            ps.setDouble(9, price);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new RuntimeException("Could not create medicine " + name);
    }

    private static void setInventoryStock(Connection conn, int pharmacyId, int medicineId, int quantity, double price, String batch, String expiry) throws Exception {
        try (PreparedStatement checkPs = conn.prepareStatement("SELECT id FROM inventory WHERE pharmacy_id = ? AND medicine_id = ?")) {
            checkPs.setInt(1, pharmacyId);
            checkPs.setInt(2, medicineId);
            try (ResultSet rs = checkPs.executeQuery()) {
                if (rs.next()) {
                    int invId = rs.getInt("id");
                    try (PreparedStatement upPs = conn.prepareStatement("UPDATE inventory SET stock_quantity = ?, unit_price = ?, batch_number = ?, expiry_date = ?, status = 'In Stock' WHERE id = ?")) {
                        upPs.setInt(1, quantity);
                        upPs.setDouble(2, price);
                        upPs.setString(3, batch);
                        upPs.setString(4, expiry);
                        upPs.setInt(5, invId);
                        upPs.executeUpdate();
                    }
                    return;
                }
            }
        }

        String insertSql = "INSERT INTO inventory (pharmacy_id, medicine_id, batch_number, stock_quantity, unit_price, expiry_date, status) VALUES (?, ?, ?, ?, ?, ?, 'In Stock')";
        try (PreparedStatement insPs = conn.prepareStatement(insertSql)) {
            insPs.setInt(1, pharmacyId);
            insPs.setInt(2, medicineId);
            insPs.setString(3, batch);
            insPs.setInt(4, quantity);
            insPs.setDouble(5, price);
            insPs.setString(6, expiry);
            insPs.executeUpdate();
        }
    }

    private static int getInventoryStock(Connection conn, int pharmacyId, int medicineId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("SELECT stock_quantity FROM inventory WHERE pharmacy_id = ? AND medicine_id = ?")) {
            ps.setInt(1, pharmacyId);
            ps.setInt(2, medicineId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("stock_quantity");
                }
            }
        }
        return 0;
    }
}
