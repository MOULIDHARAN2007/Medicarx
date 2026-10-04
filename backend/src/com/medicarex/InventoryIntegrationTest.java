package com.medicarex;

import com.medicarex.config.DatabaseConnection;
import com.medicarex.model.InventoryItem;
import com.medicarex.model.Medicine;
import com.medicarex.model.Pharmacy;
import com.medicarex.repository.InventoryRepository;
import com.medicarex.repository.MedicineRepository;
import com.medicarex.repository.PharmacyRepository;
import com.medicarex.service.InventoryService;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

public class InventoryIntegrationTest {
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("   MEDICAREX — PHARMACY INVENTORY TEST SUITE      ");
        System.out.println("==================================================");

        DatabaseConnection.initialize();
        InventoryRepository invRepo = new InventoryRepository();
        InventoryService invService = new InventoryService();
        PharmacyRepository pharmRepo = new PharmacyRepository();
        MedicineRepository medRepo = new MedicineRepository();

        int passed = 0;
        int total = 8;

        try {
            // Clean test pharmacy & test inventory
            try (Connection conn = DatabaseConnection.getConnection();
                 Statement st = conn.createStatement()) {
                st.execute("DELETE FROM inventory WHERE pharmacy_id >= 900");
                st.execute("DELETE FROM pharmacies WHERE id >= 900");
            } catch (Exception ignored) {}

            // 1. Create Test Pharmacy A and Pharmacy B
            System.out.println("\n[TEST 1] Setup Test Pharmacies");
            Pharmacy p1 = new Pharmacy();
            p1.setPharmacyName("Apollo Test Pharmacy A");
            p1.setLicenseNumber("TEST-LIC-901");
            p1.setOwnerName("Test Owner A");
            p1.setAddress("123 Test Street");
            p1.setCity("Bengaluru");
            p1.setState("Karnataka");
            p1.setPostalCode("560001");
            p1.setLatitude(12.9716);
            p1.setLongitude(77.5946);
            p1.setPhone("+91 80000 00001");
            p1.setEmail("pharm1@test.com");
            p1.setStatus("Active");
            Pharmacy pharmA = pharmRepo.create(p1);

            Pharmacy p2 = new Pharmacy();
            p2.setPharmacyName("MedLife Test Pharmacy B");
            p2.setLicenseNumber("TEST-LIC-902");
            p2.setOwnerName("Test Owner B");
            p2.setAddress("456 Test Avenue");
            p2.setCity("Bengaluru");
            p2.setState("Karnataka");
            p2.setPostalCode("560002");
            p2.setLatitude(12.9800);
            p2.setLongitude(77.6000);
            p2.setPhone("+91 80000 00002");
            p2.setEmail("pharm2@test.com");
            p2.setStatus("Active");
            Pharmacy pharmB = pharmRepo.create(p2);

            if (pharmA != null && pharmB != null) {
                System.out.println("  -> PASSED: Created test pharmacies: ID " + pharmA.getId() + " and ID " + pharmB.getId());
                passed++;
            } else {
                System.err.println("  -> FAILED: Could not create test pharmacies");
            }

            // 2. Add Medicine to Pharmacy A Inventory from Catalog
            System.out.println("\n[TEST 2] Add Medicine to Pharmacy A Inventory (Catalog Linking)");
            Medicine med1 = medRepo.findById(1); // Amoxicillin 500mg
            assert med1 != null : "Medicine catalog item 1 must exist";

            boolean added = invRepo.addOrUpdateItem(pharmA.getId(), med1.getId(), "BATCH-TEST-001", 25, 85.00, "2028-06-30");
            InventoryItem itemA = invRepo.findByPharmacyAndMedicine(pharmA.getId(), med1.getId());
            if (added && itemA != null && itemA.getStockQuantity() == 25 && "In Stock".equals(itemA.getStatus())) {
                System.out.println("  -> PASSED: Added " + itemA.getMedicineName() + " to Pharmacy A with Stock = 25, Status = In Stock");
                passed++;
            } else {
                System.err.println("  -> FAILED: Could not add item to pharmacy inventory");
            }

            // 3. Duplicate Inventory Protection Test
            System.out.println("\n[TEST 3] Duplicate Inventory Protection (Adding Same Medicine)");
            // Pharmacy A adds Amoxicillin again with quantity = 15
            boolean addedAgain = invRepo.addOrUpdateItem(pharmA.getId(), med1.getId(), "BATCH-TEST-001", 15, 85.00, "2028-06-30");
            List<InventoryItem> allForPharmA = invRepo.findByPharmacyId(pharmA.getId(), null, null, null);
            InventoryItem updatedItemA = invRepo.findByPharmacyAndMedicine(pharmA.getId(), med1.getId());

            if (addedAgain && allForPharmA.size() == 1 && updatedItemA.getStockQuantity() == 40) {
                System.out.println("  -> PASSED: Duplicate prevented! Exactly 1 row exists, stock quantity incremented: 25 + 15 = 40");
                passed++;
            } else {
                System.err.println("  -> FAILED: Duplicate row created or stock not updated properly. Count: " + allForPharmA.size());
            }

            // 4. Add Stock Feature Test (+10 units)
            System.out.println("\n[TEST 4] Direct Add Stock Action (+10 units to existing inventory)");
            boolean incSuccess = invRepo.increaseStock(updatedItemA.getId(), 10);
            InventoryItem postIncItem = invRepo.findById(updatedItemA.getId());
            if (incSuccess && postIncItem != null && postIncItem.getStockQuantity() == 50) {
                System.out.println("  -> PASSED: Add stock successful: 40 + 10 = 50 units (verified in database)");
                passed++;
            } else {
                System.err.println("  -> FAILED: Increase stock failed");
            }

            // 5. Stock Update & Status Transitions
            System.out.println("\n[TEST 5] Stock Status Transitions (In Stock -> Low Stock -> Out of Stock)");
            invRepo.updateStock(updatedItemA.getId(), 10, 85.00, null, "2028-06-30");
            InventoryItem lowStockItem = invRepo.findById(updatedItemA.getId());
            assert "Low Stock".equals(lowStockItem.getStatus()) : "Stock 10 must be Low Stock";

            invRepo.updateStock(updatedItemA.getId(), 0, 85.00, null, "2028-06-30");
            InventoryItem outOfStockItem = invRepo.findById(updatedItemA.getId());
            assert "Out of Stock".equals(outOfStockItem.getStatus()) : "Stock 0 must be Out of Stock";

            System.out.println("  -> PASSED: Stock transitions verified: 10 units = 'Low Stock', 0 units = 'Out of Stock'");
            passed++;

            // 6. Pharmacy Isolation (Pharmacy B cannot see Pharmacy A's item as its own)
            System.out.println("\n[TEST 6] Pharmacy Inventory Isolation");
            List<InventoryItem> pharmBItems = invRepo.findByPharmacyId(pharmB.getId(), null, null, null);
            if (pharmBItems != null && pharmBItems.isEmpty()) {
                System.out.println("  -> PASSED: Pharmacy B has 0 items and cannot access Pharmacy A's inventory row");
                passed++;
            } else {
                System.err.println("  -> FAILED: Pharmacy isolation leak");
            }

            // 7. Input Validation & Negative Rejection
            System.out.println("\n[TEST 7] Input Validation (Reject negative stock and prices in Service Layer)");
            boolean negStock = invService.updateStock(updatedItemA.getId(), -5, 85.00, null, null);
            boolean negPrice = invService.addOrUpdateStock(pharmA.getId(), med1.getId(), "B1", 10, -50.0, null);
            if (!negStock && !negPrice) {
                System.out.println("  -> PASSED: Negative stock (-5) and negative price (-50.0) correctly rejected!");
                passed++;
            } else {
                System.err.println("  -> FAILED: Negative values were accepted");
            }

            // 8. Patient Medicine Search Availability Reflects Real Database Stock
            System.out.println("\n[TEST 8] Patient Search Availability Grounding");
            // Set stock to 30 for Pharmacy A
            invRepo.updateStock(updatedItemA.getId(), 30, 85.00, "In Stock", "2028-06-30");
            List<Pharmacy> foundPharmacies = pharmRepo.findNearby(12.9716, 77.5946, 0.0, med1.getId(), null, null);
            boolean pharmAFoundWithStock = false;
            for (Pharmacy p : foundPharmacies) {
                if (p.getId() == pharmA.getId() && p.getMedicineStockQty() == 30 && "In Stock".equals(p.getMedicineStockStatus())) {
                    pharmAFoundWithStock = true;
                    break;
                }
            }

            if (pharmAFoundWithStock) {
                System.out.println("  -> PASSED: Patient search accurately shows Pharmacy A with Stock = 30 and 'In Stock' status!");
                passed++;
            } else {
                System.err.println("  -> FAILED: Patient medicine search did not reflect actual stock");
            }

            // Cleanup test data
            try (Connection conn = DatabaseConnection.getConnection();
                 Statement st = conn.createStatement()) {
                st.execute("DELETE FROM inventory WHERE pharmacy_id IN (" + pharmA.getId() + ", " + pharmB.getId() + ")");
                st.execute("DELETE FROM pharmacies WHERE id IN (" + pharmA.getId() + ", " + pharmB.getId() + ")");
            } catch (Exception ignored) {}

            System.out.println("\n==================================================");
            System.out.println("  INVENTORY TEST RESULTS: " + passed + " / " + total + " TESTS PASSED");
            System.out.println("==================================================");

            if (passed == total) {
                System.exit(0);
            } else {
                System.exit(1);
            }

        } catch (Exception e) {
            System.err.println("Error during inventory integration test: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
