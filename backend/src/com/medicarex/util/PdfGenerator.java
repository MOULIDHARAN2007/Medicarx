package com.medicarex.util;

import com.medicarex.model.HealthRecord;
import com.medicarex.model.Prescription;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class PdfGenerator {

    public static byte[] generateHealthRecordPdf(HealthRecord record, String patientName, String patientDetails) {
        List<String> lines = new ArrayList<>();
        lines.add("HEADER:MediCareX — Certified Health Record");
        lines.add("SUBHEADER:Digital Clinical Record & Diagnostic Document");
        lines.add("SEPARATOR:");
        lines.add("LABEL_VAL:Patient Name:" + (patientName != null ? patientName : "Patient"));
        lines.add("LABEL_VAL:Patient Details:" + (patientDetails != null ? patientDetails : "ID: #PAT-1 • O+"));
        lines.add("LABEL_VAL:Record ID:#REC-" + record.getId());
        lines.add("LABEL_VAL:Record Title:" + record.getTitle());
        lines.add("LABEL_VAL:Category:" + record.getRecordType());
        lines.add("LABEL_VAL:Healthcare Provider:" + record.getProviderName());
        lines.add("LABEL_VAL:Document Date:" + record.getRecordDate());
        lines.add("SEPARATOR:");
        lines.add("SECTION:Clinical Findings & Doctor Notes:");
        String desc = record.getDescription() != null ? record.getDescription() : "Verified medical document registered on the MediCareX Digital Health Network.";
        lines.add("TEXT:" + desc);
        lines.add("SEPARATOR:");
        lines.add("FOOTER:Certified Digital Document — MediCareX Health Information System");
        lines.add("FOOTER:In compliance with National Health Data Standards (EHR 2026)");

        return buildSimplePdf(lines);
    }

    public static byte[] generatePrescriptionPdf(Prescription rx) {
        List<String> lines = new ArrayList<>();
        lines.add("HEADER:MediCareX — Official Digital Prescription");
        lines.add("SUBHEADER:Verified Electronic Prescription Network");
        lines.add("SEPARATOR:");
        lines.add("LABEL_VAL:Prescription #:" + rx.getPrescriptionNumber());
        lines.add("LABEL_VAL:Date Issued:" + rx.getIssueDate() + "  |  Valid Until: " + rx.getValidUntil());
        lines.add("LABEL_VAL:Doctor:" + (rx.getDoctorName() != null ? rx.getDoctorName() : "Licensed Medical Practitioner"));
        lines.add("LABEL_VAL:Hospital / Reg:" + (rx.getHospitalAffiliation() != null ? rx.getHospitalAffiliation() : "MediCareX Network") + 
                (rx.getDoctorLicense() != null ? " (Reg: " + rx.getDoctorLicense() + ")" : ""));
        lines.add("LABEL_VAL:Patient Name:" + (rx.getPatientName() != null ? rx.getPatientName() : "Patient"));
        lines.add("LABEL_VAL:Diagnosis:" + (rx.getDiagnosis() != null ? rx.getDiagnosis() : "Clinical Consultation"));
        lines.add("SEPARATOR:");
        lines.add("SECTION:Rx Prescribed Medications:");

        if (rx.getItems() != null && !rx.getItems().isEmpty()) {
            for (int i = 0; i < rx.getItems().size(); i++) {
                java.util.Map<String, Object> item = rx.getItems().get(i);
                String medName = (String) item.getOrDefault("medicineName", "Medicine");
                String dosage = (String) item.getOrDefault("dosage", "As directed");
                String freq = (String) item.getOrDefault("frequency", "As directed");
                String duration = (String) item.getOrDefault("duration", "As directed");
                String instr = (String) item.getOrDefault("instructions", "");

                lines.add("MED_ITEM:" + (i + 1) + ". " + medName + " (" + dosage + ") — " + freq + " for " + duration);
                if (instr != null && !instr.isEmpty()) {
                    lines.add("TEXT:   Instructions: " + instr);
                }
            }
        } else {
            lines.add("TEXT:No medications listed.");
        }

        lines.add("SEPARATOR:");
        lines.add("SECTION:Physician Notes:");
        lines.add("TEXT:" + (rx.getNotes() != null ? rx.getNotes() : "Take medications strictly as instructed by your doctor."));
        lines.add("SEPARATOR:");
        lines.add("SIGNATURE:Signed by: " + (rx.getDoctorSignature() != null ? rx.getDoctorSignature() : "Doctor Verified Signature"));
        lines.add("FOOTER:Generated via MediCareX Telemedicine Platform in compliance with Medical Council Standards.");

        return buildSimplePdf(lines);
    }

    private static byte[] buildSimplePdf(List<String> contentItems) {
        try {
            ByteArrayOutputStream streamContent = new ByteArrayOutputStream();
            streamContent.write("BT\n".getBytes(StandardCharsets.US_ASCII));

            int y = 780; // Starting Y coordinate on A4 page (height 842)

            for (String item : contentItems) {
                if (item.startsWith("HEADER:")) {
                    String text = escapePdf(item.substring("HEADER:".length()));
                    streamContent.write(String.format("/F1 18 Tf\n1 0 0 1 50 %d Tm\n(%s) Tj\n", y, text).getBytes(StandardCharsets.US_ASCII));
                    y -= 25;
                } else if (item.startsWith("SUBHEADER:")) {
                    String text = escapePdf(item.substring("SUBHEADER:".length()));
                    streamContent.write(String.format("/F2 11 Tf\n1 0 0 1 50 %d Tm\n(%s) Tj\n", y, text).getBytes(StandardCharsets.US_ASCII));
                    y -= 20;
                } else if (item.startsWith("SEPARATOR:")) {
                    // Draw a subtle line or spacing
                    y -= 10;
                } else if (item.startsWith("LABEL_VAL:")) {
                    String[] parts = item.substring("LABEL_VAL:".length()).split(":", 2);
                    String label = escapePdf(parts[0]);
                    String val = parts.length > 1 ? escapePdf(parts[1]) : "";
                    streamContent.write(String.format("/F1 10 Tf\n1 0 0 1 50 %d Tm\n(%s:) Tj\n", y, label).getBytes(StandardCharsets.US_ASCII));
                    streamContent.write(String.format("/F2 10 Tf\n1 0 0 1 180 %d Tm\n(%s) Tj\n", y, val).getBytes(StandardCharsets.US_ASCII));
                    y -= 18;
                } else if (item.startsWith("SECTION:")) {
                    String text = escapePdf(item.substring("SECTION:".length()));
                    streamContent.write(String.format("/F1 12 Tf\n1 0 0 1 50 %d Tm\n(%s) Tj\n", y, text).getBytes(StandardCharsets.US_ASCII));
                    y -= 20;
                } else if (item.startsWith("MED_ITEM:")) {
                    String text = escapePdf(item.substring("MED_ITEM:".length()));
                    streamContent.write(String.format("/F1 10 Tf\n1 0 0 1 60 %d Tm\n(%s) Tj\n", y, text).getBytes(StandardCharsets.US_ASCII));
                    y -= 16;
                } else if (item.startsWith("TEXT:")) {
                    String text = escapePdf(item.substring("TEXT:".length()));
                    streamContent.write(String.format("/F2 10 Tf\n1 0 0 1 60 %d Tm\n(%s) Tj\n", y, text).getBytes(StandardCharsets.US_ASCII));
                    y -= 16;
                } else if (item.startsWith("SIGNATURE:")) {
                    String text = escapePdf(item.substring("SIGNATURE:".length()));
                    y -= 10;
                    streamContent.write(String.format("/F1 11 Tf\n1 0 0 1 350 %d Tm\n(%s) Tj\n", y, text).getBytes(StandardCharsets.US_ASCII));
                    y -= 25;
                } else if (item.startsWith("FOOTER:")) {
                    String text = escapePdf(item.substring("FOOTER:".length()));
                    streamContent.write(String.format("/F2 8 Tf\n1 0 0 1 50 %d Tm\n(%s) Tj\n", Math.max(y, 40), text).getBytes(StandardCharsets.US_ASCII));
                    y -= 12;
                }
            }

            streamContent.write("ET\n".getBytes(StandardCharsets.US_ASCII));
            byte[] streamBytes = streamContent.toByteArray();

            // Construct standard PDF objects
            ByteArrayOutputStream pdfOut = new ByteArrayOutputStream();
            List<Integer> offsets = new ArrayList<>();

            pdfOut.write("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));

            // Object 1: Catalog
            offsets.add(pdfOut.size());
            pdfOut.write("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n".getBytes(StandardCharsets.US_ASCII));

            // Object 2: Pages
            offsets.add(pdfOut.size());
            pdfOut.write("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n".getBytes(StandardCharsets.US_ASCII));

            // Object 3: Page (A4: 595 x 842 points)
            offsets.add(pdfOut.size());
            pdfOut.write("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R /F2 5 0 R >> >> /Contents 6 0 R >>\nendobj\n".getBytes(StandardCharsets.US_ASCII));

            // Object 4: Font F1 (Helvetica-Bold)
            offsets.add(pdfOut.size());
            pdfOut.write("4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>\nendobj\n".getBytes(StandardCharsets.US_ASCII));

            // Object 5: Font F2 (Helvetica)
            offsets.add(pdfOut.size());
            pdfOut.write("5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n".getBytes(StandardCharsets.US_ASCII));

            // Object 6: Stream Contents
            offsets.add(pdfOut.size());
            String streamHeader = String.format("6 0 obj\n<< /Length %d >>\nstream\n", streamBytes.length);
            pdfOut.write(streamHeader.getBytes(StandardCharsets.US_ASCII));
            pdfOut.write(streamBytes);
            pdfOut.write("\nendstream\nendobj\n".getBytes(StandardCharsets.US_ASCII));

            // Cross reference table
            int startXref = pdfOut.size();
            pdfOut.write(String.format("xref\n0 %d\n0000000000 65535 f \n", offsets.size() + 1).getBytes(StandardCharsets.US_ASCII));
            for (int offset : offsets) {
                pdfOut.write(String.format("%010d 00000 n \n", offset).getBytes(StandardCharsets.US_ASCII));
            }

            // Trailer
            pdfOut.write(String.format("trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n", offsets.size() + 1, startXref).getBytes(StandardCharsets.US_ASCII));

            return pdfOut.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    private static String escapePdf(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("(", "\\(")
                  .replace(")", "\\)")
                  .replaceAll("[^\\x20-\\x7E]", " ");
    }
}
