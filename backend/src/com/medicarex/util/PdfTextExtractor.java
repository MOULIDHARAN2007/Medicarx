package com.medicarex.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.InflaterInputStream;

/**
 * Pure Java PDF Text Extractor
 * Extracts selectable text streams from digital PDF documents (both uncompressed & FlateDecode streams).
 */
public class PdfTextExtractor {

    public static String extractText(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return "";
        }

        StringBuilder fullText = new StringBuilder();

        // 1. Extract and decompress all stream objects
        List<byte[]> streams = extractStreams(pdfBytes);
        for (byte[] streamData : streams) {
            String streamText = parseTextFromContentStream(streamData);
            if (!streamText.trim().isEmpty()) {
                fullText.append(streamText).append("\n");
            }
        }

        // 2. If stream parsing returned little or no text, also scan top-level string literals
        if (fullText.toString().trim().length() < 20) {
            String rawPdf = new String(pdfBytes, StandardCharsets.ISO_8859_1);
            String literalText = extractStringLiterals(rawPdf);
            if (!literalText.trim().isEmpty()) {
                fullText.append(literalText).append("\n");
            }
        }

        return fullText.toString().trim();
    }

    private static List<byte[]> extractStreams(byte[] pdfBytes) {
        List<byte[]> result = new ArrayList<>();
        String pdfStr = new String(pdfBytes, StandardCharsets.ISO_8859_1);

        int searchIdx = 0;
        while (searchIdx < pdfBytes.length) {
            int streamStartIdx = pdfStr.indexOf("stream", searchIdx);
            if (streamStartIdx == -1) break;

            // Find preceding dictionary to check for /Filter /FlateDecode
            int dictStart = pdfStr.lastIndexOf("<<", streamStartIdx);
            boolean isFlate = false;
            if (dictStart != -1 && (streamStartIdx - dictStart) < 300) {
                String dict = pdfStr.substring(dictStart, streamStartIdx);
                if (dict.contains("/FlateDecode") || dict.contains("/Fl")) {
                    isFlate = true;
                }
            }

            // Skip newline after 'stream' keyword (\r\n or \n)
            int dataStart = streamStartIdx + 6;
            if (dataStart < pdfBytes.length && pdfBytes[dataStart] == '\r') dataStart++;
            if (dataStart < pdfBytes.length && pdfBytes[dataStart] == '\n') dataStart++;

            int streamEndIdx = pdfStr.indexOf("endstream", dataStart);
            if (streamEndIdx == -1) {
                searchIdx = streamStartIdx + 6;
                continue;
            }

            int length = streamEndIdx - dataStart;
            if (length > 0 && dataStart + length <= pdfBytes.length) {
                byte[] rawStream = new byte[length];
                System.arraycopy(pdfBytes, dataStart, rawStream, 0, length);

                if (isFlate) {
                    byte[] decompressed = decompressFlate(rawStream);
                    if (decompressed != null && decompressed.length > 0) {
                        result.add(decompressed);
                    }
                } else {
                    result.add(rawStream);
                }
            }

            searchIdx = streamEndIdx + 9;
        }

        return result;
    }

    private static byte[] decompressFlate(byte[] compressed) {
        try (InputStream in = new InflaterInputStream(new ByteArrayInputStream(compressed));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int r;
            while ((r = in.read(buf)) != -1) {
                out.write(buf, 0, r);
            }
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private static String parseTextFromContentStream(byte[] streamBytes) {
        String content = new String(streamBytes, StandardCharsets.ISO_8859_1);
        StringBuilder sb = new StringBuilder();

        // Match Tj operators: (text) Tj or (text)'
        Pattern tjPattern = Pattern.compile("\\((.*?)\\)\\s*(?:Tj|'|\")", Pattern.DOTALL);
        Matcher tjMatcher = tjPattern.matcher(content);
        while (tjMatcher.find()) {
            String decoded = decodePdfString(tjMatcher.group(1));
            if (!decoded.trim().isEmpty()) {
                sb.append(decoded).append("\n");
            }
        }

        // Match TJ array operators: [(item1) -120 (item2)] TJ
        Pattern tjArrayPattern = Pattern.compile("\\[(.*?)\\]\\s*TJ", Pattern.DOTALL);
        Matcher tjArrayMatcher = tjArrayPattern.matcher(content);
        while (tjArrayMatcher.find()) {
            String arrayContent = tjArrayMatcher.group(1);
            Matcher innerStringMatcher = Pattern.compile("\\((.*?)\\)").matcher(arrayContent);
            StringBuilder line = new StringBuilder();
            while (innerStringMatcher.find()) {
                line.append(decodePdfString(innerStringMatcher.group(1)));
            }
            if (!line.toString().trim().isEmpty()) {
                sb.append(line.toString().trim()).append("\n");
            }
        }

        return sb.toString();
    }

    private static String extractStringLiterals(String rawPdf) {
        StringBuilder sb = new StringBuilder();
        // Look for text lines following common PDF header/structure patterns
        Pattern linePattern = Pattern.compile("\\(([A-Za-z0-9\\s\\-\\.,/;:()#–—]+)\\)");
        Matcher matcher = linePattern.matcher(rawPdf);
        while (matcher.find()) {
            String text = decodePdfString(matcher.group(1)).trim();
            if (text.length() > 2 && !text.startsWith("Font") && !text.startsWith("PDF-")) {
                sb.append(text).append("\n");
            }
        }
        return sb.toString();
    }

    private static String decodePdfString(String s) {
        if (s == null) return "";
        return s.replace("\\(", "(")
                .replace("\\)", ")")
                .replace("\\\\", "\\")
                .replace("\\r", " ")
                .replace("\\n", " ")
                .replace("\\t", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
