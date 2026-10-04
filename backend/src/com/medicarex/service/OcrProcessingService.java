package com.medicarex.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Service for OCR text extraction from prescription images (JPG, JPEG, PNG, scanned documents).
 * Uses native Windows Media OCR with fallback support.
 */
public class OcrProcessingService {

    public List<String> extractTextFromImage(File imageFile) {
        List<String> lines = new ArrayList<>();
        if (imageFile == null || !imageFile.exists() || imageFile.length() == 0) {
            return lines;
        }

        try {
            // Find script location
            File scriptFile = new File("scripts/win_ocr_runner.ps1");
            if (!scriptFile.exists()) {
                scriptFile = new File("backend/scripts/win_ocr_runner.ps1");
            }
            if (!scriptFile.exists()) {
                scriptFile = new File("../backend/scripts/win_ocr_runner.ps1");
            }

            if (scriptFile.exists()) {
                ProcessBuilder pb = new ProcessBuilder(
                        "powershell.exe",
                        "-NoProfile",
                        "-NonInteractive",
                        "-ExecutionPolicy", "Bypass",
                        "-File", scriptFile.getAbsolutePath(),
                        "-ImagePath", imageFile.getAbsolutePath()
                );
                pb.redirectErrorStream(true);

                Process process = pb.start();
                boolean finished = process.waitFor(15, TimeUnit.SECONDS);
                if (finished) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        boolean capturing = false;
                        while ((line = reader.readLine()) != null) {
                            line = line.trim();
                            if (line.equals("OCR_LINES_START")) {
                                capturing = true;
                                continue;
                            }
                            if (line.equals("OCR_LINES_END")) {
                                capturing = false;
                                break;
                            }
                            if (capturing && !line.isEmpty()) {
                                lines.add(line);
                            }
                        }
                    }
                } else {
                    process.destroyForcibly();
                    System.err.println("[OCR] Process timed out for file: " + imageFile.getName());
                }
            }
        } catch (Exception e) {
            System.err.println("[OCR] Extraction failed: " + e.getMessage());
            e.printStackTrace();
        }

        return lines;
    }
}
