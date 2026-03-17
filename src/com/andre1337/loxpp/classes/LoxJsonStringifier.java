package com.andre1337.loxpp.classes;

import java.util.List;
import java.util.Map;

public class LoxJsonStringifier {
    public static String stringify(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof Boolean) return obj.toString();

        if (obj instanceof Double) {
            String text = obj.toString();

            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }

            return text;
        }

        if (obj instanceof String) return "\"" + escapeString((String) obj) + "\"";
        if (obj instanceof LoxString) return "\"" + escapeString(((LoxString) obj).value) + "\"";

        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            StringBuilder sb = new StringBuilder();
            sb.append("[");

            for (int i = 0; i < list.size(); i++) {
                sb.append(stringify(list.get(i)));
                if (i < list.size() - 1) sb.append(",");
            }

            sb.append("]");
            return sb.toString();
        }

        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            StringBuilder sb = new StringBuilder();
            sb.append("{");

            int i = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey() instanceof LoxString ? ((LoxString) entry.getKey()).value : entry.getKey().toString();

                sb.append("\"").append(escapeString(key)).append("\":");
                sb.append(stringify(entry.getValue()));

                if (i < map.size() - 1) sb.append(",");
                i++;
            }

            sb.append("}");
            return sb.toString();
        }

        return "\"" + obj + "\"";
    }

    private static String escapeString(String str) {
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
