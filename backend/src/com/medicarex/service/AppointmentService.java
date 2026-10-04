package com.medicarex.service;

import com.medicarex.model.Appointment;
import com.medicarex.model.Doctor;
import com.medicarex.model.Patient;
import com.medicarex.model.User;
import com.medicarex.repository.AppointmentRepository;
import com.medicarex.repository.DoctorRepository;
import com.medicarex.repository.NotificationRepository;
import com.medicarex.repository.PatientRepository;
import com.medicarex.repository.UserRepository;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppointmentService {
    private final AppointmentRepository apptRepo = new AppointmentRepository();
    private final DoctorRepository doctorRepo = new DoctorRepository();
    private final PatientRepository patientRepo = new PatientRepository();
    private final UserRepository userRepo = new UserRepository();
    private final NotificationRepository notifRepo = new NotificationRepository();

    private static final String[] STANDARD_SLOTS = {
            "09:00 AM", "09:30 AM", "10:00 AM", "10:30 AM", "11:00 AM", "11:30 AM",
            "02:00 PM", "02:30 PM", "03:00 PM", "03:30 PM", "04:00 PM", "04:30 PM", "05:00 PM"
    };

    public List<Map<String, Object>> searchDoctors(String query, String specialization) {
        return doctorRepo.searchDoctors(query, specialization);
    }

    public List<Map<String, Object>> getAllDoctors() {
        return doctorRepo.findAllDoctorsWithDetails();
    }

    public List<Map<String, Object>> getAvailableSlots(int doctorId, String date) {
        List<Map<String, Object>> slots = new ArrayList<>();
        if (date == null || date.trim().isEmpty()) {
            return slots;
        }

        List<String> booked = apptRepo.getBookedSlots(doctorId, date.trim());
        for (String slot : STANDARD_SLOTS) {
            Map<String, Object> item = new HashMap<>();
            item.put("time", slot);
            item.put("available", !booked.contains(slot));
            item.put("isBooked", booked.contains(slot));
            slots.add(item);
        }
        return slots;
    }

    public List<Appointment> getDoctorAppointments(int doctorUserId) {
        return apptRepo.findByDoctorUserId(doctorUserId);
    }

    public List<Appointment> getPatientAppointments(int patientUserId) {
        return apptRepo.findByPatientUserId(patientUserId);
    }

    public List<Appointment> getAllAppointments() {
        return apptRepo.findAllAppointments();
    }

    public Appointment getAppointmentById(int appointmentId, int userId, String role) {
        Appointment a = apptRepo.findById(appointmentId);
        if (a == null) return null;

        if ("ADMIN".equalsIgnoreCase(role)) {
            return a;
        }
        if ("DOCTOR".equalsIgnoreCase(role) && a.getDoctorUserId() == userId) {
            return a;
        }
        if ("PATIENT".equalsIgnoreCase(role) && a.getPatientUserId() == userId) {
            return a;
        }
        return null;
    }

    public synchronized Map<String, Object> bookAppointment(int doctorId, int patientUserId, String date, String time, String type, String notes) throws Exception {
        if (doctorId <= 0) {
            throw new IllegalArgumentException("A valid Doctor must be selected.");
        }

        Doctor doc = doctorRepo.findById(doctorId);
        if (doc == null) {
            throw new IllegalArgumentException("Selected doctor was not found in the directory.");
        }

        if (date == null || date.trim().isEmpty()) {
            throw new IllegalArgumentException("Appointment date is required.");
        }

        String cleanedDate = date.trim();
        try {
            LocalDate chosen = LocalDate.parse(cleanedDate);
            LocalDate today = LocalDate.now();
            if (chosen.isBefore(today)) {
                throw new IllegalArgumentException("Cannot book an appointment for a past date (" + cleanedDate + "). Please select today or a future date.");
            }
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format. Expected YYYY-MM-DD.");
        }

        if (time == null || time.trim().isEmpty()) {
            throw new IllegalArgumentException("Available time slot is required.");
        }
        String cleanedTime = time.trim();

        // 1. Double booking validation check
        if (apptRepo.isSlotBooked(doctorId, cleanedDate, cleanedTime)) {
            throw new IllegalStateException("This appointment slot is no longer available. Please select another time.");
        }

        // 2. Patient lookup / auto-provision
        Patient patient = patientRepo.getOrCreateByUserId(patientUserId);
        if (patient == null) {
            throw new IllegalStateException("Unable to load or provision patient profile.");
        }

        // 3. Persist appointment in DB
        String apptType = (type != null && !type.trim().isEmpty()) ? type.trim() : "General Consultation";
        String apptNotes = (notes != null) ? notes.trim() : "";
        int createdId = apptRepo.create(doctorId, patient.getId(), cleanedDate, cleanedTime, apptType, "Confirmed", apptNotes);

        if (createdId <= 0) {
            throw new IllegalStateException("This appointment slot is no longer available. Please select another time.");
        }

        // 4. Fetch rich appointment entity
        Appointment created = apptRepo.findById(createdId);
        User patientUser = userRepo.findById(patientUserId);
        User doctorUser = userRepo.findById(doc.getUserId());

        String docName = doctorUser != null ? doctorUser.getFullName() : "Doctor";
        String patName = patientUser != null ? patientUser.getFullName() : "Patient";

        // 5. Send automated confirmation notifications
        try {
            notifRepo.create(
                    patientUserId,
                    "Appointment",
                    "Appointment Confirmed with " + docName,
                    "Your " + apptType + " with " + docName + " (" + doc.getSpecialization() + ") is confirmed for " + cleanedDate + " at " + cleanedTime + ".",
                    createdId,
                    "APPOINTMENT"
            );

            notifRepo.create(
                    doc.getUserId(),
                    "Appointment",
                    "New Patient Appointment: " + patName,
                    patName + " booked a " + apptType + " on " + cleanedDate + " at " + cleanedTime + ". Reason: " + (apptNotes.isEmpty() ? "General Consultation" : apptNotes),
                    createdId,
                    "APPOINTMENT"
            );
        } catch (Exception notifErr) {
            System.err.println("[AppointmentService] Notification trigger notice: " + notifErr.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("id", createdId);
        result.put("doctorId", doctorId);
        result.put("doctorName", docName);
        result.put("doctorSpecialization", doc.getSpecialization());
        result.put("hospitalAffiliation", doc.getHospitalAffiliation());
        result.put("patientId", patient.getId());
        result.put("patientName", patName);
        result.put("appointmentDate", cleanedDate);
        result.put("appointmentTime", cleanedTime);
        result.put("type", apptType);
        result.put("status", "Confirmed");
        result.put("notes", apptNotes);
        result.put("reason", apptNotes);
        if (created != null) {
            result.put("appointment", created.toMap());
        }
        return result;
    }

    public boolean updateStatus(int appointmentId, String status) {
        return apptRepo.updateStatus(appointmentId, status);
    }

    public boolean cancelAppointment(int appointmentId, int userId, String role) {
        return apptRepo.cancelAppointment(appointmentId, userId, role);
    }
}
