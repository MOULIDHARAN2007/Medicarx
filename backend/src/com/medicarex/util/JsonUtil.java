package com.medicarex.util;

import java.util.*;

public class JsonUtil {

    public static String toJson(Object obj) {
        if (obj == null) {
            return "null";
        }
        if (obj instanceof String) {
            return "\"" + escapeJson((String) obj) + "\"";
        }
        if (obj instanceof Number || obj instanceof Boolean) {
            return String.valueOf(obj);
        }
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(escapeJson(String.valueOf(entry.getKey()))).append("\":");
                sb.append(toJson(entry.getValue()));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }
        if (obj instanceof Collection) {
            Collection<?> col = (Collection<?>) obj;
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : col) {
                if (!first) sb.append(",");
                sb.append(toJson(item));
                first = false;
            }
            sb.append("]");
            return sb.toString();
        }
        if (obj instanceof Object[]) {
            Object[] arr = (Object[]) obj;
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : arr) {
                if (!first) sb.append(",");
                sb.append(toJson(item));
                first = false;
            }
            sb.append("]");
            return sb.toString();
        }
        
        // 1. Check if object has toMap() method
        try {
            java.lang.reflect.Method toMapMethod = obj.getClass().getMethod("toMap");
            Object mapRes = toMapMethod.invoke(obj);
            if (mapRes instanceof Map) {
                return toJson(mapRes);
            }
        } catch (NoSuchMethodException ignored) {
        } catch (Exception ignored) {
        }

        // 2. POJO Bean Reflection: serialize getters
        try {
            java.lang.reflect.Method[] methods = obj.getClass().getMethods();
            Map<String, Object> beanMap = new LinkedHashMap<>();
            for (java.lang.reflect.Method m : methods) {
                if (m.getParameterCount() == 0 && !m.getName().equals("getClass")) {
                    String propName = null;
                    if (m.getName().startsWith("get") && m.getName().length() > 3) {
                        propName = Character.toLowerCase(m.getName().charAt(3)) + m.getName().substring(4);
                    } else if (m.getName().startsWith("is") && m.getName().length() > 2) {
                        propName = Character.toLowerCase(m.getName().charAt(2)) + m.getName().substring(3);
                    }
                    if (propName != null) {
                        Object val = m.invoke(obj);
                        beanMap.put(propName, val);
                    }
                }
            }
            if (!beanMap.isEmpty()) {
                return toJson(beanMap);
            }
        } catch (Exception ignored) {
        }

        // Fallback toString
        return "\"" + escapeJson(obj.toString()) + "\"";
    }

    private static String escapeJson(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 32 || c >= 127) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    public static Map<String, Object> fromJson(String json) {
        return parseMap(json);
    }

    public static Map<String, Object> parseMap(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new HashMap<>();
        }
        Object parsed = new SimpleJsonParser(json.trim()).parse();
        if (parsed instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) parsed;
            return map;
        }
        return new HashMap<>();
    }

    public static List<Object> parseList(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        Object parsed = new SimpleJsonParser(json.trim()).parse();
        if (parsed instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) parsed;
            return list;
        }
        return new ArrayList<>();
    }

    private static class SimpleJsonParser {
        private final String src;
        private int idx = 0;

        public SimpleJsonParser(String src) {
            this.src = src;
        }

        public Object parse() {
            skipWhitespace();
            if (idx >= src.length()) return null;
            char c = src.charAt(idx);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == 't' || c == 'f') return parseBoolean();
            if (c == 'n') return parseNull();
            if (Character.isDigit(c) || c == '-') return parseNumber();
            return null;
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> map = new HashMap<>();
            idx++; // skip '{'
            skipWhitespace();
            if (idx < src.length() && src.charAt(idx) == '}') {
                idx++;
                return map;
            }
            while (idx < src.length()) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                if (idx < src.length() && src.charAt(idx) == ':') {
                    idx++;
                }
                skipWhitespace();
                Object val = parse();
                map.put(key, val);
                skipWhitespace();
                if (idx < src.length() && src.charAt(idx) == ',') {
                    idx++;
                } else if (idx < src.length() && src.charAt(idx) == '}') {
                    idx++;
                    break;
                }
            }
            return map;
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            idx++; // skip '['
            skipWhitespace();
            if (idx < src.length() && src.charAt(idx) == ']') {
                idx++;
                return list;
            }
            while (idx < src.length()) {
                skipWhitespace();
                Object val = parse();
                list.add(val);
                skipWhitespace();
                if (idx < src.length() && src.charAt(idx) == ',') {
                    idx++;
                } else if (idx < src.length() && src.charAt(idx) == ']') {
                    idx++;
                    break;
                }
            }
            return list;
        }

        private String parseString() {
            if (idx < src.length() && src.charAt(idx) == '"') {
                idx++;
            }
            StringBuilder sb = new StringBuilder();
            while (idx < src.length()) {
                char c = src.charAt(idx++);
                if (c == '"') {
                    break;
                }
                if (c == '\\' && idx < src.length()) {
                    char esc = src.charAt(idx++);
                    if (esc == '"') sb.append('"');
                    else if (esc == '\\') sb.append('\\');
                    else if (esc == 'n') sb.append('\n');
                    else if (esc == 'r') sb.append('\r');
                    else if (esc == 't') sb.append('\t');
                    else if (esc == 'u' && idx + 4 <= src.length()) {
                        String hex = src.substring(idx, idx + 4);
                        idx += 4;
                        sb.append((char) Integer.parseInt(hex, 16));
                    } else sb.append(esc);
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private Object parseNumber() {
            int start = idx;
            while (idx < src.length()) {
                char c = src.charAt(idx);
                if (Character.isDigit(c) || c == '.' || c == '-' || c == '+' || c == 'e' || c == 'E') {
                    idx++;
                } else {
                    break;
                }
            }
            String numStr = src.substring(start, idx);
            if (numStr.contains(".")) {
                return Double.parseDouble(numStr);
            } else {
                try {
                    return Integer.parseInt(numStr);
                } catch (NumberFormatException e) {
                    return Long.parseLong(numStr);
                }
            }
        }

        private Boolean parseBoolean() {
            if (src.startsWith("true", idx)) {
                idx += 4;
                return true;
            }
            if (src.startsWith("false", idx)) {
                idx += 5;
                return false;
            }
            return null;
        }

        private Object parseNull() {
            if (src.startsWith("null", idx)) {
                idx += 4;
            }
            return null;
        }

        private void skipWhitespace() {
            while (idx < src.length() && Character.isWhitespace(src.charAt(idx))) {
                idx++;
            }
        }
    }
}
