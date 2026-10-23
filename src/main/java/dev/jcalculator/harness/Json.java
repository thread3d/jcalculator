package dev.jcalculator.harness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A minimal, dependency-free JSON codec.
 *
 * <p>The project has no runtime dependencies on purpose: the build is offline and self-contained.
 * The harness protocol and the MCP transport therefore need their own JSON support. This class is
 * deliberately small and strict:
 *
 * <ul>
 *   <li>{@link #write(Object)} serialises maps, iterables, strings, booleans, integral numbers and
 *       decimals; anything else is refused rather than guessed at;</li>
 *   <li>{@link #parse(String)} returns {@code Map<String, Object>}, {@code List<Object>},
 *       {@code String}, {@code BigDecimal}, {@code Boolean} or {@code null};</li>
 *   <li>parsing rejects trailing content, control characters in strings and excessive nesting.</li>
 * </ul>
 *
 * <p>Maps are written with their iteration order preserved, so a response is byte-stable for a given
 * state. That stability is what lets a harness diff two runs.
 */
public final class Json {

    /** Deepest nesting accepted, so a hostile document cannot exhaust the stack. */
    private static final int MAX_DEPTH = 100;

    private Json() {
    }

    // ---- Writing --------------------------------------------------------------------------------

    /** Serialises a supported value to a single-line JSON document. */
    public static String write(Object value) {
        final StringBuilder out = new StringBuilder(160);
        writeValue(out, value);
        return out.toString();
    }

    /** Builds an insertion-ordered object from alternating names and values. */
    public static Map<String, Object> object(Object... namesAndValues) {
        if (namesAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("object() needs an even number of arguments");
        }
        final Map<String, Object> object = new LinkedHashMap<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            if (!(namesAndValues[i] instanceof String name)) {
                throw new IllegalArgumentException("object() names must be strings");
            }
            object.put(name, namesAndValues[i + 1]);
        }
        return object;
    }

    private static void writeValue(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String text) {
            writeString(out, text);
        } else if (value instanceof Boolean flag) {
            out.append(flag.booleanValue());
        } else if (value instanceof BigDecimal decimal) {
            out.append(decimal.toPlainString());
        } else if (value instanceof Integer || value instanceof Long
                || value instanceof Short || value instanceof Byte) {
            out.append(value);
        } else if (value instanceof Double || value instanceof Float) {
            writeFloating(out, ((Number) value).doubleValue());
        } else if (value instanceof Map<?, ?> map) {
            writeObject(out, map);
        } else if (value instanceof Iterable<?> items) {
            writeArray(out, items);
        } else if (value instanceof Enum<?> constant) {
            writeString(out, constant.name());
        } else {
            throw new JsonException("cannot write JSON for " + value.getClass().getName());
        }
    }

    private static void writeFloating(StringBuilder out, double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new JsonException("JSON has no representation for " + value);
        }
        out.append(BigDecimal.valueOf(value).toPlainString());
    }

    private static void writeObject(StringBuilder out, Map<?, ?> map) {
        out.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            if (!(entry.getKey() instanceof String name)) {
                throw new JsonException("JSON object names must be strings");
            }
            writeString(out, name);
            out.append(':');
            writeValue(out, entry.getValue());
        }
        out.append('}');
    }

    private static void writeArray(StringBuilder out, Iterable<?> items) {
        out.append('[');
        boolean first = true;
        for (Object item : items) {
            if (!first) {
                out.append(',');
            }
            first = false;
            writeValue(out, item);
        }
        out.append(']');
    }

    private static void writeString(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append("\\u00");
                        out.append(Character.forDigit((c >> 4) & 0x0F, 16));
                        out.append(Character.forDigit(c & 0x0F, 16));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    // ---- Parsing --------------------------------------------------------------------------------

    /** Parses one JSON document. */
    public static Object parse(String text) {
        final Parser parser = new Parser(Objects.requireNonNull(text, "text"));
        final Object value = parser.parseValue(0);
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw new JsonException("unexpected trailing content at offset " + parser.position());
        }
        return value;
    }

    /** Parses one JSON document that must be an object. */
    public static Map<String, Object> parseObject(String text) {
        final Object value = parse(text);
        if (value instanceof Map<?, ?> map) {
            return asObject(map);
        }
        throw new JsonException("expected a JSON object");
    }

    /** Narrows a parsed value to an object. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new JsonException("expected a JSON object");
    }

    /** Narrows a parsed value to an array. */
    @SuppressWarnings("unchecked")
    public static List<Object> asArray(Object value) {
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        throw new JsonException("expected a JSON array");
    }

    // ---- Field access ---------------------------------------------------------------------------

    /** Returns a required string field. */
    public static String string(Map<String, Object> object, String key) {
        final Object value = object.get(key);
        if (value instanceof String text) {
            return text;
        }
        throw new JsonException("missing or non-string field: " + key);
    }

    /** Returns a string field, or the fallback when the field is absent. */
    public static String optionalString(Map<String, Object> object, String key, String fallback) {
        final Object value = object.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof String text) {
            return text;
        }
        throw new JsonException("field " + key + " must be a string");
    }

    /** Returns an integer field, or the fallback when the field is absent. */
    public static int integer(Map<String, Object> object, String key, int fallback) {
        final Object value = object.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.intValueExact();
        }
        if (value instanceof Integer integer) {
            return integer.intValue();
        }
        if (value instanceof Long longValue) {
            return Math.toIntExact(longValue.longValue());
        }
        throw new JsonException("field " + key + " must be a number");
    }

    /** Returns a boolean field, or the fallback when the field is absent. */
    public static boolean bool(Map<String, Object> object, String key, boolean fallback) {
        final Object value = object.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean flag) {
            return flag.booleanValue();
        }
        throw new JsonException("field " + key + " must be a boolean");
    }

    // ---- Parser ---------------------------------------------------------------------------------

    private static final class Parser {

        private final String text;
        private int index;

        Parser(String text) {
            this.text = text;
        }

        int position() {
            return index;
        }

        boolean atEnd() {
            return index >= text.length();
        }

        char peek() {
            if (atEnd()) {
                throw new JsonException("unexpected end of JSON input");
            }
            return text.charAt(index);
        }

        void skipWhitespace() {
            while (!atEnd()) {
                final char c = text.charAt(index);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    index++;
                } else {
                    return;
                }
            }
        }

        Object parseValue(int depth) {
            if (depth > MAX_DEPTH) {
                throw new JsonException("JSON nesting is deeper than " + MAX_DEPTH);
            }
            skipWhitespace();
            final char c = peek();
            return switch (c) {
                case '{' -> parseObjectValue(depth);
                case '[' -> parseArrayValue(depth);
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> parseNumber();
            };
        }

        private Object parseObjectValue(int depth) {
            index++;
            skipWhitespace();
            final Map<String, Object> object = new LinkedHashMap<>();
            if (peek() == '}') {
                index++;
                return object;
            }
            while (true) {
                skipWhitespace();
                if (peek() != '"') {
                    throw new JsonException("expected an object name at offset " + index);
                }
                final String name = parseString();
                skipWhitespace();
                expect(':');
                object.put(name, parseValue(depth + 1));
                skipWhitespace();
                final char next = peek();
                if (next == ',') {
                    index++;
                } else if (next == '}') {
                    index++;
                    return object;
                } else {
                    throw new JsonException("expected ',' or '}' at offset " + index);
                }
            }
        }

        private Object parseArrayValue(int depth) {
            index++;
            skipWhitespace();
            final List<Object> items = new ArrayList<>();
            if (peek() == ']') {
                index++;
                return items;
            }
            while (true) {
                items.add(parseValue(depth + 1));
                skipWhitespace();
                final char next = peek();
                if (next == ',') {
                    index++;
                } else if (next == ']') {
                    index++;
                    return items;
                } else {
                    throw new JsonException("expected ',' or ']' at offset " + index);
                }
            }
        }

        private String parseString() {
            expect('"');
            final StringBuilder out = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw new JsonException("unterminated string");
                }
                final char c = text.charAt(index++);
                if (c == '"') {
                    return out.toString();
                }
                if (c == '\\') {
                    appendEscape(out);
                } else if (c < 0x20) {
                    throw new JsonException("control character in string at offset " + (index - 1));
                } else {
                    out.append(c);
                }
            }
        }

        private void appendEscape(StringBuilder out) {
            if (atEnd()) {
                throw new JsonException("unterminated escape");
            }
            final char escape = text.charAt(index++);
            switch (escape) {
                case '"' -> out.append('"');
                case '\\' -> out.append('\\');
                case '/' -> out.append('/');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> out.append(Character.toChars(parseUnicodeValue()));
                default -> throw new JsonException("invalid escape \\" + escape);
            }
        }

        private int parseUnicodeValue() {
            if (index + 4 > text.length()) {
                throw new JsonException("truncated unicode escape");
            }
            int value = 0;
            for (int i = 0; i < 4; i++) {
                final int digit = Character.digit(text.charAt(index++), 16);
                if (digit < 0) {
                    throw new JsonException("invalid unicode escape");
                }
                value = (value << 4) | digit;
            }
            return value;
        }

        private Object parseLiteral(String literal, Object result) {
            if (!text.startsWith(literal, index)) {
                throw new JsonException("invalid JSON literal at offset " + index);
            }
            index += literal.length();
            return result;
        }

        private BigDecimal parseNumber() {
            final int start = index;
            if (!atEnd() && peek() == '-') {
                index++;
            }
            if (atEnd() || !isDigit(text.charAt(index))) {
                throw new JsonException("expected a digit at offset " + index);
            }
            final char first = text.charAt(index);
            index++;
            if (first == '0') {
                if (!atEnd() && isDigit(text.charAt(index))) {
                    throw new JsonException("leading zeros are not allowed at offset " + start);
                }
            } else {
                while (!atEnd() && isDigit(text.charAt(index))) {
                    index++;
                }
            }
            if (!atEnd() && peek() == '.') {
                index++;
                requireDigit();
                while (!atEnd() && isDigit(text.charAt(index))) {
                    index++;
                }
            }
            if (!atEnd() && (peek() == 'e' || peek() == 'E')) {
                index++;
                if (!atEnd() && (peek() == '+' || peek() == '-')) {
                    index++;
                }
                requireDigit();
                while (!atEnd() && isDigit(text.charAt(index))) {
                    index++;
                }
            }
            try {
                return new BigDecimal(text.substring(start, index));
            } catch (NumberFormatException exception) {
                throw new JsonException("invalid number at offset " + start);
            }
        }

        private void requireDigit() {
            if (atEnd() || !isDigit(text.charAt(index))) {
                throw new JsonException("expected a digit at offset " + index);
            }
        }

        private static boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }

        private void expect(char expected) {
            if (atEnd() || text.charAt(index) != expected) {
                throw new JsonException("expected '" + expected + "' at offset " + index);
            }
            index++;
        }
    }
}
