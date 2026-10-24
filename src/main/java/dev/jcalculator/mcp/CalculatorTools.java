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
            case "calculator_memory_edit" -> Json.object(
                    "op", "memory_edit",
                    "action", arguments.get("action"),
                    "index", arguments.get("index"));
            case "calculator_capabilities" -> Json.object("op", "capabilities");
            case "calculator_report_issue" -> Json.object(
                    "op", "report_issue",
                    "summary", arguments.get("summary"),
                    "detail", arguments.get("detail"),
                    "severity", arguments.get("severity"));
            case "calculator_issues" -> Json.object(
                    "op", "issues",
                    "status", arguments.get("status"),
                    "limit", arguments.get("limit"));
            case "calculator_resolve_issue" -> Json.object(
                    "op", "resolve_issue",
                    "id", arguments.get("id"),
                    "disposition", arguments.get("disposition"),
                    "note", arguments.get("note"));
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

        tools.add(tool("calculator_memory_edit",
                "Add, subtract, load or clear one memorized slot. MS stores a new slot; index counts "
                        + "from the newest memorized value.",
                schema(Json.object(
                        "action", Json.object(
                                "type", "string",
                                "description", "add, subtract, load or clear",
                                "enum", List.of("add", "subtract", "load", "clear")),
                        "index", integerProperty("Slot to edit, counting from 0 at the newest (default 0).")),
                        List.of("action"))));

        tools.add(tool("calculator_capabilities",
                "Discover the version, modes, conversion categories, operations and every token.",
                schema(Json.object(), List.of())));

        tools.add(tool("calculator_report_issue",
                "Record a problem with the calculator so a later session can fix it. Use this when a "
                        + "tool refuses input it should accept, or returns a result you believe is wrong.",
                schema(Json.object(
                        "summary", stringProperty("One-line description of the problem; it identifies the issue."),
                        "detail", stringProperty("What you expected and what happened."),
                        "severity", Json.object(
                                "type", "string",
                                "description", "low, medium, high or critical (default medium)",
                                "enum", List.of("low", "medium", "high", "critical"))),
                        List.of("summary"))));

        tools.add(tool("calculator_issues",
                "Read the recorded issues about jCalculator, newest first (by default only the open ones).",
                schema(Json.object(
                        "status", Json.object(
                                "type", "string",
                                "description", "open (default), resolved or all",
                                "enum", List.of("open", "resolved", "all")),
                        "limit", integerProperty("Maximum number of issues to return (default 20).")),
                        List.of())));

        tools.add(tool("calculator_resolve_issue",
                "Mark a recorded issue fixed or won't-fix, with an optional note.",
                schema(Json.object(
                        "id", stringProperty("Issue id such as JC-1A2B3C4D."),
                        "disposition", Json.object(
                                "type", "string",
                                "description", "fixed (default) or wontfix",
                                "enum", List.of("fixed", "wontfix")),
                        "note", stringProperty("How it was fixed, or why it will not be.")),
                        List.of("id"))));

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
