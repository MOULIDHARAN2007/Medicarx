package com.medicarex;

import com.medicarex.config.AppConfig;
import com.medicarex.config.DatabaseConnection;
import com.medicarex.controller.*;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class Main {
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("   Starting MediCareX Healthcare SaaS Platform    ");
        System.out.println("==================================================");

        // 1. Initialize Database
        DatabaseConnection.initialize();

        // 2. Start HTTP Server
        try {
            int port = AppConfig.PORT;
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

            server.createContext("/api/auth", new AuthController());
            server.createContext("/api/user", new UserController());
            server.createContext("/api/medicines", new MedicineController());
            server.createContext("/api/pharmacies", new PharmacyController());
            server.createContext("/api/inventory", new InventoryController());
            server.createContext("/api/pharmacy/inventory", new InventoryController());
            server.createContext("/api/orders", new OrderController());
            server.createContext("/api/prescriptions", new PrescriptionController());
            server.createContext("/api/patient", new PrescriptionController());
            server.createContext("/api/records", new HealthRecordController());
            server.createContext("/api/notifications", new NotificationController());
            server.createContext("/api/appointments", new AppointmentController());
            server.createContext("/api/doctor", new DoctorController());
            server.createContext("/api/admin", new AdminController());
            server.createContext("/api/ai", new AiAssistantController());

            BillingController billingController = new BillingController();
            server.createContext("/api/pharmacy/bills", billingController);
            server.createContext("/api/pharmacy/billing", billingController);
            server.createContext("/api/pharmacy/medicines", billingController);
            server.createContext("/api/pharmacy/inventory/barcode", billingController);
            server.createContext("/api/pharmacy/barcode", billingController);
            server.createContext("/api/pharmacy/stock-audit", billingController);
            server.createContext("/api/pharmacy/inventory-transactions", billingController);

            // Emergency & Ambulance Operations
            EmergencyController emergencyController = new EmergencyController();
            server.createContext("/api/emergency", emergencyController);
            server.createContext("/api/ambulance", emergencyController);
            server.createContext("/api/admin/ambulances", emergencyController);
            server.createContext("/api/admin/emergency", emergencyController);

            // Patient Enhancements
            PatientFeatureController patientFeatureController = new PatientFeatureController();
            server.createContext("/api/emergency/card", patientFeatureController);
            server.createContext("/api/patient/medicine-supply", patientFeatureController);
            server.createContext("/api/patient/data-sharing", patientFeatureController);
            server.createContext("/api/patient/emergency-card", patientFeatureController);
            server.createContext("/api/patient/visit-prep", patientFeatureController);
            server.createContext("/api/patient/medical-expenses", patientFeatureController);
            server.createContext("/api/patient/purchase-history", patientFeatureController);

            // Static Frontend Handler (Default root)
            server.createContext("/", new StaticFileHandler());

            server.setExecutor(Executors.newFixedThreadPool(30));
            server.start();

            System.out.println("[Server] MediCareX Java Backend started successfully!");
            System.out.println("[Server] Active Database Mode: " + DatabaseConnection.getActiveDbType());
            System.out.println("[Server] Frontend & API accessible at: http://localhost:" + port);
            System.out.println("==================================================");

            // Keep main thread active perpetually
            while (true) {
                try {
                    Thread.sleep(1000000L);
                } catch (InterruptedException ignored) {}
            }

        } catch (Exception e) {
            System.err.println("[Server] Fatal error starting HTTP server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
