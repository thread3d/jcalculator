package dev.jcalculator.mcp;

import dev.jcalculator.harness.HarnessProtocol;
import dev.jcalculator.harness.Json;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The calculator's tools, exposed over MCP.
 *
 * <p>Each tool is a thin adapter over one {@link HarnessProtocol} operation, so the MCP surface and
 * the JSON protocol cannot disagree about what a key press does. Tool results use exactly the
 * response shape documented by {@link HarnessProtocol}.
 */
public final class CalculatorTools {

    private final HarnessProtocol protocol;
    private final List<Map<String, Object>> descriptors;
    private final Set<String> names;

    public CalculatorTools(HarnessProtocol protocol) {
        this.protocol = Objects.requireNonNull(protocol, "protocol");
        this.descriptors = List.copyOf(buildDescriptors());
        final Set<String> discovered = new HashSet<>();
        for (Map<String, Object> descriptor : descriptors) {
            discovered.add((String) descriptor.get("name"));
        }
        this.names = Set.copyOf(discovered);
    }

    /** The MCP {@code tools/list} descriptors. */
    public List<Map<String, Object>> descriptors() {
        return descriptors;
    }

    /** Whether a tool with this name exists. */
    public boolean has(String name) {
        return names.contains(name);
    }

    /**
     * Runs a tool.
     *
     * @param name      the tool name
     * @param arguments the MCP arguments object
     * @return the harness response document
     * @throws IllegalArgumentException when the tool name is unknown
     */
    public Map<String, Object> call(String name, Map<String, Object> arguments) {
        final Map<String, Object> request = switch (name) {
            case "calculator_press" -> Json.object("op", "press", "tokens", arguments.get("tokens"));
            case "calculator_evaluate" -> Json.object("op", "evaluate", "expression", arguments.get("expression"));
            case "calculator_state" -> Json.object("op", "state");
            case "calculator_reset" -> Json.object("op", "reset");
            case "calculator_mode" -> Json.object("op", "mode", "mode", arguments.get("mode"));
            case "calculator_convert" -> Json.object(
                    "op", "convert",
                    "category", arguments.get("category"),
                    "from", arguments.get("from"),
                    "to", arguments.get("to"),
                    "value", arguments.get("value"));
            case "calculator_date_diff" -> Json.object(
                    "op", "date_diff",
                    "from", arguments.get("from"),
                    "to", arguments.get("to"));
            case "calculator_date_add" -> Json.object(
                    "op", "date_add",
                    "from", arguments.get("from"),
                    "years", arguments.get("years"),
                    "months", arguments.get("months"),
                    "days", arguments.get("days"),
                    "subtract", arguments.get("subtract"));
            case "calculator_history" -> Json.object("op", "history");
            case "calculator_memory" -> Json.object("op", "memory");
            case "calculator_capabilities" -> Json.object("op", "capabilities");
            default -> throw new IllegalArgumentException("unknown tool: " + name);
        };
        return protocol.execute(request).response();
    }

    // ---- Descriptors -----------------------------------------------------------------------------

    private static List<Map<String, Object>> buildDescriptors() {
        final List<Map<String, Object>> tools = new ArrayList<>();

        tools.add(tool("calculator_press",
                "Press calculator buttons in order. Tokens are keypad names such as 7, +, sqrt, "
                        + "hex, qword, deg, ms and '=' to evaluate. Returns the display and state.",
                schema(Json.object(
                        "tokens", arrayProperty("Buttons to press, in order, for example "
                                + "[\"2\",\"+\",\"3\",\"*\"] then [\"=\"].", "string")),
                        List.of("tokens"))));

        tools.add(tool("calculator_evaluate",
                "Press a whitespace-separated token sequence and return the result, for example "
                        + "\"2 + 3 * 4 =\".",
                schema(Json.object(
                        "expression", stringProperty("Whitespace-separated button tokens.")),
                        List.of("expression"))));

        tools.add(tool("calculator_state",
                "Read the full calculator state: display, expression, error, mode, base, width, "
                        + "angle unit, memory and history.",
                schema(Json.object(), List.of())));

        tools.add(tool("calculator_reset",
                "Clear the display, memory and every mode's history.",
                schema(Json.object(), List.of())));

        tools.add(tool("calculator_mode",
                "Switch between the standard, scientific and programmer pages.",
                schema(Json.object(
                        "mode", Json.object(
                                "type", "string",
                                "description", "standard, scientific or programmer",
                                "enum", List.of("standard", "scientific", "programmer"))),
                        List.of("mode"))));

        tools.add(tool("calculator_convert",
                "Convert a value between units of one category, for example length m ft 2.5.",
                schema(Json.object(
                        "category", stringProperty("Conversion category, such as length or temperature."),
                        "from", stringProperty("Source unit abbreviation or name."),
                        "to", stringProperty("Target unit abbreviation or name."),
                        "value", stringProperty("Value to convert.")),
                        List.of("category", "from", "to", "value"))));

        tools.add(tool("calculator_date_diff",
                "Describe the calendar difference between two ISO dates.",
                schema(Json.object(
                        "from", stringProperty("Start date, ISO 8601 (YYYY-MM-DD)."),
                        "to", stringProperty("End date, ISO 8601 (YYYY-MM-DD).")),
                        List.of("from", "to"))));

        tools.add(tool("calculator_date_add",
                "Add or subtract a calendar duration from a date.",
                schema(Json.object(
                        "from", stringProperty("Start date, ISO 8601 (YYYY-MM-DD)."),
                        "years", integerProperty("Years to apply first."),
                        "months", integerProperty("Months to apply second."),
                        "days", integerProperty("Days to apply last."),
                        "subtract", booleanProperty("Subtract the duration instead of adding it.")),
                        List.of("from"))));

        tools.add(tool("calculator_history",
                "Read the completed calculations of the active mode, newest first.",
                schema(Json.object(), List.of())));

        tools.add(tool("calculator_memory",
                "Read the memorized values, newest first.",
                schema(Json.object(), List.of())));

        tools.add(tool("calculator_capabilities",
                "Discover the version, modes, conversion categories, operations and every token.",
                schema(Json.object(), List.of())));

        return tools;
    }

    private static Map<String, Object> tool(String name, String description, Map<String, Object> inputSchema) {
        return Json.object(
                "name", name,
                "description", description,
                "inputSchema", inputSchema);
    }

    private static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        return Json.object(
                "type", "object",
                "properties", properties,
                "required", required,
                "additionalProperties", false);
    }

    private static Map<String, Object> stringProperty(String description) {
        return Json.object("type", "string", "description", description);
    }

    private static Map<String, Object> integerProperty(String description) {
        return Json.object("type", "integer", "description", description);
    }

    private static Map<String, Object> booleanProperty(String description) {
        return Json.object("type", "boolean", "description", description);
    }

    private static Map<String, Object> arrayProperty(String description, String itemType) {
        return Json.object(
                "type", "array",
                "description", description,
                "items", Json.object("type", itemType));
    }
}
