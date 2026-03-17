package com.andre1337.loxpp.classes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LoxJsonParser {
    private final String json;
    private int pos = 0;

    public LoxJsonParser(String json) {
        this.json = json;
    }

    public Object parse() {
        skipWhitespace();
        if (pos >= json.length()) return null;
        return parseValue();
    }

    private Object parseValue() {
        skipWhitespace();
        char c = json.charAt(pos);

        if (c == '{') return parseObject();
        if (c == '[') return parseArray();
        if (c == '"') return parseString();
        if (c == 't') { pos += 4; return true; }
        if (c == 'f') { pos += 5; return false; }
        if (c == 'n') { pos += 4; return null; }
        if (Character.isDigit(c) || c == '-') return parseNumber();

        throw new Error("Unexpected character in JSON: " + c);
    }

    private Map<String, Object> parseObject() {
        Map<String, Object> map = new HashMap<>();
        pos++;
        skipWhitespace();

        if (json.charAt(pos) == '}') {
            pos++;
            return map;
        }

        while (pos < json.length()) {
            skipWhitespace();
            String key = parseRawString();

            skipWhitespace();
            pos++;
            Object value = parseValue();

            map.put(key, value);

            skipWhitespace();

            char c = json.charAt(pos);
            if (c == '}') {
                pos++;
                return map;
            }

            pos++;
        }

        return map;
    }

    private List<Object> parseArray() {
        List<Object> list = new ArrayList<>();
        pos++;
        skipWhitespace();

        if (json.charAt(pos) == ']') {
            pos++;
            return list;
        }

        while (pos < json.length()) {
            list.add(parseValue());
            skipWhitespace();

            char c = json.charAt(pos);
            if (c == ']') {
                pos++;
                return list;
            }

            pos++;
        }

        return list;
    }

    private LoxString parseString() {
        return new LoxString(parseRawString());
    }

    private String parseRawString() {
        pos++;
        StringBuilder sb = new StringBuilder();

        while (pos < json.length() && json.charAt(pos) != '"') {
            if (json.charAt(pos) == '\\') {
                pos++;
            }

            sb.append(json.charAt(pos));
            pos++;
        }

        pos++;
        return sb.toString();
    }

    private Double parseNumber() {
        int start = pos;

        while (pos < json.length() && (Character.isDigit(json.charAt(pos)) || json.charAt(pos) == '.' || json.charAt(pos) == '-')) {
            pos++;
        }

        return Double.parseDouble(json.substring(start, pos));
    }

    private void skipWhitespace() {
        while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) {
            pos++;
        }
    }
}
