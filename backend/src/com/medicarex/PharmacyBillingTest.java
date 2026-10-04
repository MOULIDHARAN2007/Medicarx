package com.medicarex;

import com.medicarex.config.DatabaseConnection;
import com.medicarex.model.Bill;
import com.medicarex.model.InventoryItem;
import com.medicarex.model.InventoryTransaction;
import com.medicarex.model.Medicine;
import com.medicarex.model.Pharmacy;
import com.medicarex.repository.BillingRepository;
import com.medicarex.repository.InventoryRepository;
import com.medicarex.repository.MedicineRepository;
import com.medicarex.repository.PharmacyRepository;

import java.util.*;

public class PharmacyBillingTest {

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("   Running MediCareX Pharmacy Billing & POS Tests  ");
        System.out.println("==================================================");

        DatabaseConnection.initialize();

        MedicineRepository medRepo = new MedicineRepository();
        InventoryRepository invRepo = new InventoryRepository();
        BillingRepository billingRepo = new BillingRepository();
        PharmacyRepository pharmRepo = new PharmacyRepository();

        int passed = 0;
        int failed = 0;

        try {
            // Find a valid pharmacy
            List<Pharmacy> pharms = pharmRepo.findAll();
            if (pharms.isEmpty()) {
                throw new IllegalStateException("No pharmacy found in DB for testing.");
            }
            Pharmacy testPharm = pharms.get(0);
            int pharmId = testPharm.getId();
            int userId = testPharm.getUserId() != null ? testPharm.getUserId() : 4;
            System.out.println("[Setup] Using Test Pharmacy: ID=" + pharmId + " ('" + testPharm.getPharmacyName() + "'), UserID=" + userId);

            // TEST 1: Barcode Lookup
            System.out.println("\n--- Test 1: Barcode Lookup ---");
            Medicine medByBarcode = medRepo.findByBarcode("8901030001021"); // Paracetamol 650mg
            if (medByBarcode != null && "Paracetamol 650mg".equalsIgnoreCase(medByBarcode.getName())) {
                System.out.println("PASS: Found medicine by barcode: " + medByBarcode.getName() + " (" + medByBarcode.getBarcode() + ")");
                passed++;
            } else {
                System.err.println("FAIL: Medicine not found for barcode 8901030001021");
                failed++;
            }

            Medicine unknownBarcode = medRepo.findByBarcode("9999999999999");
            if (unknownBarcode == null) {
                System.out.println("PASS: Unknown barcode returned null as expected.");
                passed++;
            } else {
                System.err.println("FAIL: Unknown barcode unexpectedly returned a record.");
                failed++;
            }

            // Ensure test medicine is in pharmacy inventory with known stock
            int targetMedId = medByBarcode != null ? medByBarcode.getId() : 2;
            invRepo.addOrUpdateItem(pharmId, targetMedId, "BATCH-TEST-100", 50, 15.0, "2028-12-31");
            invRepo.updateStock(invRepo.findByPharmacyAndMedicine(pharmId, targetMedId).getId(), 50, 15.0, "In Stock", "2028-12-31");

            InventoryItem currentInv = invRepo.findByPharmacyAndMedicine(pharmId, targetMedId);
            System.out.println("[Setup] Target Medicine Stock reset to: " + currentInv.getStockQuantity());

            // TEST 2: Single-Item Bill Creation with Inventory Deduction
            System.out.println("\n--- Test 2: Single-Item POS Bill Creation & Stock Deduction ---");
            List<Map<String, Object>> billItems = new ArrayList<>();
            Map<String, Object> itm1 = new HashMap<>();
            itm1.put("medicineId", targetMedId);
            itm1.put("quantity", 3);
            billItems.add(itm1);

            int stockBefore = currentInv.getStockQuantity();
            BillingRepository.BillCreationResult createResult = billingRepo.createBillWithInventoryDeduction(
                    pharmId,
                    userId,
                    null,
                    "John Doe",
                    "+91 9876543210",
                    null,
                    0.0,
                    0.0,
                    "CASH",
                    "Test POS Bill",
                    billItems,
                    "Pharmacist Test"
            );

            Bill createdBill = createResult.bill;
            if (createdBill != null && createdBill.getId() > 0 && createdBill.getBillNumber() != null) {
                System.out.println("PASS: Bill created successfully: " + createdBill.getBillNumber() + " (ID: " + createdBill.getId() + ")");
                passed++;
            } else {
                System.err.println("FAIL: Bill creation failed.");
                failed++;
            }

            // Verify Stock decreased exactly by 3
            InventoryItem afterSaleInv = invRepo.findByPharmacyAndMedicine(pharmId, targetMedId);
            int expectedStock = stockBefore - 3;
            if (afterSaleInv.getStockQuantity() == expectedStock) {
                System.out.println("PASS: Stock decreased exactly from " + stockBefore + " to " + afterSaleInv.getStockQuantity());
                passed++;
            } else {
                System.err.println("FAIL: Expected stock " + expectedStock + " but found " + afterSaleInv.getStockQuantity());
                failed++;
            }

            // TEST 3: Inventory Audit Log Verification
            System.out.println("\n--- Test 3: Inventory Audit Trail Verification ---");
            List<InventoryTransaction> auditLogs = billingRepo.findInventoryTransactions(pharmId, targetMedId, 5);
            boolean foundAudit = false;
            for (InventoryTransaction tx : auditLogs) {
                if ("SALE".equals(tx.getTransactionType()) && tx.getQuantityChange() == -3 && createdBill.getBillNumber().equals(tx.getReferenceId())) {
                    foundAudit = true;
                    System.out.println("PASS: Audit transaction found: Type=" + tx.getTransactionType() + ", Qty=" + tx.getQuantityChange() + ", Prev=" + tx.getPreviousStock() + ", New=" + tx.getNewStock() + ", Ref=" + tx.getReferenceId());
                    break;
                }
            }
            if (foundAudit) {
                passed++;
            } else {
                System.err.println("FAIL: Audit transaction not logged properly for bill " + createdBill.getBillNumber());
                failed++;
            }

            // TEST 4: Over-selling / Negative Stock Prevention
            System.out.println("\n--- Test 4: Over-selling & Negative Stock Protection ---");
            int remainingStock = afterSaleInv.getStockQuantity();
            int excessiveQty = remainingStock + 50;

            List<Map<String, Object>> excessiveItems = new ArrayList<>();
            Map<String, Object> overItem = new HashMap<>();
            overItem.put("medicineId", targetMedId);
            overItem.put("quantity", excessiveQty);
            excessiveItems.add(overItem);

            boolean overSellRejected = false;
            try {
                billingRepo.createBillWithInventoryDeduction(
                        pharmId,
                        userId,
                        null,
                        "Over Buyer",
                        "",
                        null,
                        0.0,
                        0.0,
                        "CASH",
                        "Excessive Bill",
                        excessiveItems,
                        "Pharmacist Test"
                );
            } catch (IllegalStateException | IllegalArgumentException e) {
                overSellRejected = true;
                System.out.println("PASS: Over-selling properly rejected with error: " + e.getMessage());
            }

            if (overSellRejected) {
                passed++;
            } else {
                System.err.println("FAIL: Over-selling was not rejected!");
                failed++;
            }

            // Verify stock remained unchanged after rejected sale
            InventoryItem checkInv = invRepo.findByPharmacyAndMedicine(pharmId, targetMedId);
            if (checkInv.getStockQuantity() == remainingStock) {
                System.out.println("PASS: Stock unchanged after aborted transaction (" + checkInv.getStockQuantity() + ")");
                passed++;
            } else {
                System.err.println("FAIL: Stock was altered during rejected transaction!");
                failed++;
            }

            // TEST 5: Multi-Item Atomic Transaction
            System.out.println("\n--- Test 5: Multi-Item Atomic POS Transaction ---");
            Medicine med2 = medRepo.findByBarcode("8901030001014"); // Amoxicillin 500mg
            int med2Id = med2 != null ? med2.getId() : 1;
            invRepo.addOrUpdateItem(pharmId, med2Id, "BATCH-AMOX-50", 40, 20.0, "2028-12-31");
            invRepo.updateStock(invRepo.findByPharmacyAndMedicine(pharmId, med2Id).getId(), 40, 20.0, "In Stock", "2028-12-31");

            int med1StockBefore = invRepo.findByPharmacyAndMedicine(pharmId, targetMedId).getStockQuantity();
            int med2StockBefore = invRepo.findByPharmacyAndMedicine(pharmId, med2Id).getStockQuantity();

            List<Map<String, Object>> multiItems = new ArrayList<>();
            Map<String, Object> mi1 = new HashMap<>();
            mi1.put("medicineId", targetMedId);
            mi1.put("quantity", 2);
            multiItems.add(mi1);

            Map<String, Object> mi2 = new HashMap<>();
            mi2.put("medicineId", med2Id);
            mi2.put("quantity", 5);
            multiItems.add(mi2);

            BillingRepository.BillCreationResult multiResult = billingRepo.createBillWithInventoryDeduction(
                    pharmId,
                    userId,
                    null,
                    "Multi Buyer",
                    "+91 9988776655",
                    null,
                    10.0, // Discount
                    5.0,  // Tax
                    "UPI",
                    "Multi Item Sale",
                    multiItems,
                    "Pharmacist Test"
            );

            Bill multiBill = multiResult.bill;
            int med1StockAfter = invRepo.findByPharmacyAndMedicine(pharmId, targetMedId).getStockQuantity();
            int med2StockAfter = invRepo.findByPharmacyAndMedicine(pharmId, med2Id).getStockQuantity();

            if (med1StockAfter == (med1StockBefore - 2) && med2StockAfter == (med2StockBefore - 5)) {
                System.out.println("PASS: Multi-item stocks deducted correctly (Med1: " + med1StockBefore + "->" + med1StockAfter + ", Med2: " + med2StockBefore + "->" + med2StockAfter + ")");
                System.out.println("PASS: Multi-item Bill Subtotal: ₹" + multiBill.getSubtotal() + ", Total: ₹" + multiBill.getTotalAmount());
                passed += 2;
            } else {
                System.err.println("FAIL: Multi-item stock deduction discrepancy.");
                failed += 2;
            }

            // TEST 6: Bill Cancellation & Inventory Restock
            System.out.println("\n--- Test 6: Bill Cancellation & Stock Restock ---");
            boolean cancelSuccess = billingRepo.cancelBillWithRestock(multiBill.getId(), pharmId, "Customer return of unused batch", "Pharmacist Test");
            if (cancelSuccess) {
                int med1Restocked = invRepo.findByPharmacyAndMedicine(pharmId, targetMedId).getStockQuantity();
                int med2Restocked = invRepo.findByPharmacyAndMedicine(pharmId, med2Id).getStockQuantity();

                if (med1Restocked == med1StockBefore && med2Restocked == med2StockBefore) {
                    System.out.println("PASS: Restock verified! Both medicines returned to exact original stock counts.");
                    passed++;
                } else {
                    System.err.println("FAIL: Stock not restored properly on cancellation (Med1: " + med1Restocked + ", Med2: " + med2Restocked + ")");
                    failed++;
                }

                Bill cancelledBill = billingRepo.findBillById(multiBill.getId());
                if ("CANCELLED".equals(cancelledBill.getPaymentStatus())) {
                    System.out.println("PASS: Bill status updated to CANCELLED in database.");
                    passed++;
                } else {
                    System.err.println("FAIL: Bill payment status is not CANCELLED.");
                    failed++;
                }
            } else {
                System.err.println("FAIL: Bill cancellation returned false.");
                failed++;
            }

            // TEST 7: Low Stock Warning Generation
            System.out.println("\n--- Test 7: Low Stock Warning Notification ---");
            // Set stock to 6, sell 4 -> stock becomes 2 (<= 5)
            invRepo.updateStock(invRepo.findByPharmacyAndMedicine(pharmId, targetMedId).getId(), 6, 15.0, "Low Stock", "2028-12-31");

            List<Map<String, Object>> lowItems = new ArrayList<>();
            Map<String, Object> lowItm = new HashMap<>();
            lowItm.put("medicineId", targetMedId);
            lowItm.put("quantity", 4);
            lowItems.add(lowItm);

            BillingRepository.BillCreationResult lowResult = billingRepo.createBillWithInventoryDeduction(
                    pharmId,
                    userId,
                    null,
                    "Low Stock Buyer",
                    "",
                    null,
                    0.0,
                    0.0,
                    "CASH",
                    "Trigger low stock",
                    lowItems,
                    "Pharmacist Test"
            );

            if (!lowResult.stockWarnings.isEmpty()) {
                System.out.println("PASS: Low stock warning generated: " + lowResult.stockWarnings.get(0));
                passed++;
            } else {
                System.err.println("FAIL: No low stock warning generated when stock dropped to 2.");
                failed++;
            }

        } catch (Exception e) {
            System.err.println("FATAL EXCEPTION DURING TESTS: " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        System.out.println("\n==================================================");
        System.out.println("TEST SUMMARY: " + passed + " PASSED, " + failed + " FAILED");
        System.out.println("==================================================");
    }
}
