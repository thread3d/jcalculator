package dev.jcalculator.harness;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonTest {

    @Test
    void writesAndParsesObjects() {
        final Map<String, Object> value = Json.object("a", 1, "b", List.of("x", true), "c", null);
        final String text = Json.write(value);
        assertEquals("{\"a\":1,\"b\":[\"x\",true],\"c\":null}", text);

        final Map<String, Object> parsed = Json.parseObject(text);
        assertEquals(new BigDecimal("1"), parsed.get("a"));
        assertEquals(List.of("x", true), parsed.get("b"));
        assertNull(parsed.get("c"));
    }

    @Test
    void escapesStrings() {
        final String text = Json.write("a\"b\\c\n\t\u0001");
        assertEquals("\"a\\\"b\\\\c\\n\\t\\u0001\"", text);
        assertEquals("a\"b\\c\n\t\u0001", Json.parse(text));
    }

    @Test
    void parsesScientificNotation() {
        final Object value = Json.parse("-1.5e3");
        assertEquals(0, ((BigDecimal) value).compareTo(new BigDecimal("-1500")));
    }

    @Test
    void rejectsTrailingContent() {
        assertThrows(JsonException.class, () -> Json.parse("{} x"));
    }

    @Test
    void rejectsMalformedDocuments() {
        assertThrows(JsonException.class, () -> Json.parse("{"));
        assertThrows(JsonException.class, () -> Json.parse("[1,]"));
        assertThrows(JsonException.class, () -> Json.parse("\"unterminated"));
        assertThrows(JsonException.class, () -> Json.parse("01"));
        assertThrows(JsonException.class, () -> Json.parse("tru"));
    }

    @Test
    void rejectsUnsupportedValues() {
        assertThrows(JsonException.class, () -> Json.write(new Object()));
        assertThrows(JsonException.class, () -> Json.write(Double.NaN));
    }

    @Test
    void buildsObjectsInOrder() {
        assertEquals("{\"one\":1,\"two\":2}", Json.write(Json.object("one", 1, "two", 2)));
    }

    @Test
    void readsTypedFields() {
        final Map<String, Object> object = Json.object("s", "text", "i", 3, "b", true);
        assertEquals("text", Json.string(object, "s"));
        assertEquals(3, Json.integer(object, "i", 0));
        assertEquals(true, Json.bool(object, "b", false));
        assertEquals("fallback", Json.optionalString(object, "missing", "fallback"));
        assertEquals(9, Json.integer(object, "missing", 9));
        assertThrows(JsonException.class, () -> Json.string(object, "i"));
    }
}
