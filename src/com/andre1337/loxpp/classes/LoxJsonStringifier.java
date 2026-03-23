package com.andre1337.loxpp.classes;

import java.text.DecimalFormat;
import java.util.Map;

public class LoxJsonStringifier {
    private static String cleanString(String str) {
        if (str == null) return "";
        if (str.startsWith("\"") && str.endsWith("\"") && str.length() >= 2) {
            str = str.substring(1, str.length() - 1);
        }

        return str.replace("\"", "\\\"");
    }

    public static String stringify(Object obj) {
        switch (obj) {
            case null -> {
                return "null";
            }

            case LoxString loxStr -> {
                return "\"" + cleanString(loxStr.value.replace("\"", "\\\"")) + "\"";
            }

            case String str -> {
                return "\"" + cleanString(str.replace("\"", "\\\"")) + "\"";
            }

            case Double d -> {
                if (d == d.longValue()) {
                    return String.valueOf(d.longValue());
                } else {
                    DecimalFormat df = new DecimalFormat("0.0###############");
                    return df.format(d).replace(",", ".");
                }
            }

            case Boolean ignored -> {
                return obj.toString();
            }

            case Map<?, ?> map -> {
                StringBuilder sb = new StringBuilder();
                sb.append("{");
                int count = 0;

                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    String key = entry.getKey() instanceof LoxString ? ((LoxString) entry.getKey()).value : entry.getKey().toString();

                    sb.append("\"").append(cleanString(key)).append("\":");
                    sb.append(stringify(entry.getValue()));

                    if (++count < map.size()) sb.append(",");
                }

                sb.append("}");
                return sb.toString();
            }

            case LoxArray array -> {
                StringBuilder sb = new StringBuilder();
                sb.append("[");

                for (int i = 0; i < array.elements.size(); i++) {
                    sb.append(stringify(array.elements.get(i)));
                    if (i < array.elements.size() - 1) sb.append(",");
                }

                sb.append("]");
                return sb.toString();
            }

            default -> {
                return "\"" + cleanString(obj.toString()) + "\"";
            }
        }
    }
}
