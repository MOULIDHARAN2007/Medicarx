package com.medicarex.controller;

import com.medicarex.model.Appointment;
import com.medicarex.service.AppointmentService;
import com.medicarex.util.HttpUtil;
import com.medicarex.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AppointmentController implements HttpHandler {
    private final AppointmentService appointmentService = new AppointmentService();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpUtil.handleOptions(exchange)) return;

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        // 1. Public or Authenticated Doctor Directory Search
        if (path.equals("/api/appointments/doctors") && "GET".equalsIgnoreCase(method)) {
            handleGetDoctors(exchange);
            return;
        }

        // 2. Public or Authenticated Slot Availability Check
        if (path.equals("/api/appointments/slots") && "GET".equalsIgnoreCase(method)) {
            handleGetSlots(exchange);
            return;
        }

        // All subsequent endpoints require authentication
        Map<String, Object> claims = HttpUtil.authenticate(exchange);
        if (claims == null) {
            HttpUtil.sendError(exchange, 401, "Authentication required to access appointments.");
            return;
        }

        int userId = ((Number) claims.get("userId")).intValue();
        String role = (String) claims.get("role");

        if ((path.equals("/api/appointments") || path.equals("/api/appointments/list")) && "GET".equalsIgnoreCase(method)) {
            handleGetAppointments(exchange, userId, role);
        } else if ((path.equals("/api/appointments") || path.equals("/api/appointments/book") || path.equals("/api/appointments/create")) && "POST".equalsIgnoreCase(method)) {
            handleBookAppointment(exchange, userId, role);
        } else if (path.equals("/api/appointments/cancel") && "POST".equalsIgnoreCase(method)) {
            handleCancelAppointment(exchange, userId, role);
        } else if (path.equals("/api/appointments/status") && "POST".equalsIgnoreCase(method)) {
            handleUpdateStatus(exchange, role);
        } else if (path.startsWith("/api/appointments/") && "GET".equalsIgnoreCase(method)) {
            handleGetSingleAppointment(exchange, path, userId, role);
        } else {
            HttpUtil.sendError(exchange, 404, "Appointment endpoint not found.");
        }
    }

    private void handleGetDoctors(HttpExchange exchange) throws IOException {
        Map<String, String> queryParams = HttpUtil.getQueryParams(exchange);
        String q = queryParams.get("q");
        String specialization = queryParams.get("specialization");

        List<Map<String, Object>> doctors = appointmentService.searchDoctors(q, specialization);
        HttpUtil.sendSuccess(exchange, "Doctor directory retrieved", doctors);
    }

    private void handleGetSlots(HttpExchange exchange) throws IOException {
        Map<String, String> queryParams = HttpUtil.getQueryParams(exchange);
        String docIdStr = queryParams.get("doctorId");
        String date = queryParams.get("date");

        if (docIdStr == null || date == null) {
            HttpUtil.sendError(exchange, 400, "doctorId and date parameters are required.");
            return;
        }

        try {
            int doctorId = Integer.parseInt(docIdStr);
            List<Map<String, Object>> slots = appointmentService.getAvailableSlots(doctorId, date);
            HttpUtil.sendSuccess(exchange, "Available slots retrieved", slots);
        } catch (NumberFormatException e) {
            HttpUtil.sendError(exchange, 400, "Invalid doctorId format.");
        }
    }

    private void handleGetAppointments(HttpExchange exchange, int userId, String role) throws IOException {
        List<Appointment> list;
        if ("DOCTOR".equalsIgnoreCase(role)) {
            list = appointmentService.getDoctorAppointments(userId);
        } else if ("ADMIN".equalsIgnoreCase(role)) {
            list = appointmentService.getAllAppointments();
        } else {
            // PATIENT
            list = appointmentService.getPatientAppointments(userId);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Appointment a : list) {
            result.add(a.toMap());
        }
        HttpUtil.sendSuccess(exchange, "Appointments retrieved", result);
    }

    private void handleBookAppointment(HttpExchange exchange, int userId, String role) throws IOException {
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);

        Number docIdNum = (Number) req.get("doctorId");
        if (docIdNum == null) {
            HttpUtil.sendError(exchange, 400, "doctorId is required.");
            return;
        }

        int doctorId = docIdNum.intValue();
        String date = (String) req.get("appointmentDate");
        if (date == null) date = (String) req.get("date");

        String time = (String) req.get("appointmentTime");
        if (time == null) time = (String) req.get("time");

        String type = (String) req.get("type");
        String reason = (String) req.get("reason");
        if (reason == null) reason = (String) req.get("notes");

        try {
            // Note: Authenticated user ID is passed as patient identity. Frontend patient ID is never trusted.
            Map<String, Object> created = appointmentService.bookAppointment(doctorId, userId, date, time, type, reason);
            HttpUtil.sendSuccess(exchange, "Appointment Booked Successfully", created);
        } catch (IllegalArgumentException e) {
            HttpUtil.sendError(exchange, 400, e.getMessage());
        } catch (IllegalStateException e) {
            HttpUtil.sendError(exchange, 409, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            HttpUtil.sendError(exchange, 500, "Failed to book appointment: " + e.getMessage());
        }
    }

    private void handleCancelAppointment(HttpExchange exchange, int userId, String role) throws IOException {
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);

        Number apptIdNum = (Number) req.get("appointmentId");
        if (apptIdNum == null) {
            apptIdNum = (Number) req.get("id");
        }

        if (apptIdNum == null) {
            HttpUtil.sendError(exchange, 400, "appointmentId is required.");
            return;
        }

        int appointmentId = apptIdNum.intValue();
        boolean ok = appointmentService.cancelAppointment(appointmentId, userId, role);
        if (ok) {
            HttpUtil.sendSuccess(exchange, "Appointment cancelled successfully.", null);
        } else {
            HttpUtil.sendError(exchange, 403, "Unable to cancel appointment. Verification failed or permission denied.");
        }
    }

    private void handleUpdateStatus(HttpExchange exchange, String role) throws IOException {
        if (!"DOCTOR".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role)) {
            HttpUtil.sendError(exchange, 403, "Forbidden: Doctor or Admin role required to update appointment status.");
            return;
        }

        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);

        Number apptIdNum = (Number) req.get("appointmentId");
        String status = (String) req.get("status");

        if (apptIdNum == null || status == null) {
            HttpUtil.sendError(exchange, 400, "appointmentId and status are required.");
            return;
        }

        boolean ok = appointmentService.updateStatus(apptIdNum.intValue(), status);
        if (ok) {
            HttpUtil.sendSuccess(exchange, "Appointment status updated to " + status, null);
        } else {
            HttpUtil.sendError(exchange, 500, "Failed to update appointment status.");
        }
    }

    private void handleGetSingleAppointment(HttpExchange exchange, String path, int userId, String role) throws IOException {
        String idStr = path.substring("/api/appointments/".length()).trim();
        try {
            int appointmentId = Integer.parseInt(idStr);
            Appointment appt = appointmentService.getAppointmentById(appointmentId, userId, role);
            if (appt == null) {
                HttpUtil.sendError(exchange, 404, "Appointment not found or unauthorized.");
                return;
            }
            HttpUtil.sendSuccess(exchange, "Appointment details retrieved", appt.toMap());
        } catch (NumberFormatException e) {
            HttpUtil.sendError(exchange, 400, "Invalid appointment ID in path.");
        }
    }
}
