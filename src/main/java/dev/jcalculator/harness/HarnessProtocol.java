package dev.jcalculator.harness;

import dev.jcalculator.app.AppInfo;
import dev.jcalculator.convert.UnitCategory;
import dev.jcalculator.engine.CalculatorMode;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The line-oriented harness protocol.
 *
 * <p>One request produces one JSON response. A request is either a JSON object or a bare line of
 * button tokens ({@code "2 + 3 * 4 ="}); a response always has the same shape:
 *
 * <pre>
 *   {"id":1,"op":"press","ok":true,"errorCode":null,"error":false,"errorMessage":null,
 *    "display":"20","rawDisplay":"20","expression":"","mode":"STANDARD","radix":10,
 *    "width":"QWORD","angle":"DEGREES","result":null,"history":[],"memory":[]}
 * </pre>
 *
 * <p>{@code ok} says whether the operation produced a defined result. {@code error} says whether the
 * calculator is in its error state (see {@code errorMessage}), and {@code errorCode} is a stable
 * machine-readable reason when {@code ok} is false:
 *
 * <ul>
 *   <li>{@code calculator_error} — the calculation failed, for example division by zero;</li>
 *   <li>{@code out_of_range} — a date result falls outside the supported range;</li>
 *   <li>{@code unknown_token}, {@code unknown_op}, {@code bad_request}, {@code parse_error} — the
 *       request itself was wrong.</li>
 * </ul>
 *
 * <p>The protocol is deterministic: a {@link CalculatorSession} created with a seed produces the
 * same responses for the same requests, including {@code rand}.
 */
public final class HarnessProtocol {

    /** The request succeeded. */
    public static final int EXIT_OK = 0;
    /** The request was valid but the calculation failed. */
    public static final int EXIT_CALCULATION_ERROR = 1;
    /** The request was malformed. */
    public static final int EXIT_PROTOCOL_ERROR = 2;
    /** The command line was malformed. */
    public static final int EXIT_USAGE = 64;
    /** An unexpected internal failure. */
    public static final int EXIT_INTERNAL_ERROR = 70;

    /** Every operation the protocol understands. */
    public static List<String> operations() {
        return List.of(
                "press", "evaluate", "state", "reset", "mode", "convert",
                "date_diff", "date_add", "date_subtract", "history", "memory", "capabilities");
    }

    /** A response document and the process exit code it implies. */
    public record Outcome(Map<String, Object> response, int exitCode) {
    }

    private final CalculatorSession session;

    public HarnessProtocol(CalculatorSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    /** The session this protocol drives. */
    public CalculatorSession session() {
        return session;
    }

    /**
     * Handles one input line.
     *
     * @return the response document and exit code, or {@code null} for a blank line
     */
    public Outcome handleLine(String line) {
        final String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.charAt(0) == '{') {
            final Map<String, Object> request;
            try {
                request = Json.parseObject(trimmed);
            } catch (JsonException exception) {
                return failure(null, "", "parse_error", exception.getMessage());
            }
            return execute(request);
        }
        return execute(Json.object("op", "press", "expression", trimmed));
    }

    /** Handles a parsed request object. */
    public Outcome execute(Map<String, Object> request) {
        final Object id = request.get("id");
        final String op = Json.optionalString(request, "op", null);
        if (op == null) {
            return failure(id, "", "bad_request", "missing op");
        }
        try {
            return switch (op) {
                case "press", "tokens" -> press(id, "press", request);
                case "evaluate" -> press(id, "evaluate", request);
                case "state" -> success(id, "state", null, EXIT_OK);
                case "reset" -> {
                    session.reset();
                    yield success(id, "reset", null, EXIT_OK);
                }
                case "mode" -> mode(id, request);
                case "convert" -> convert(id, request);
                case "date_diff", "date_difference" -> dateDiff(id, request);
                case "date_add" -> dateAdd(id, request, false);
                case "date_subtract" -> dateAdd(id, request, true);
                case "history", "memory" -> success(id, op, null, EXIT_OK);
                case "capabilities" -> capabilities(id);
                default -> failure(id, op, "unknown_op", "unknown operation: " + op);
            };
        } catch (UnknownTokenException exception) {
            return failure(id, op, "unknown_token", exception.getMessage());
        } catch (DateTimeException exception) {
            return failure(id, op, "bad_request", exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return failure(id, op, "bad_request", exception.getMessage());
        } catch (RuntimeException exception) {
            return failure(id, op, "internal_error", exception.getMessage());
        }
    }

    // ---- Operations ------------------------------------------------------------------------------

    private Outcome press(Object id, String op, Map<String, Object> request) {
        session.pressAll(tokensOf(request));
        final boolean ok = !session.manager().isError();
        return new Outcome(
                response(id, op, ok, ok ? null : "calculator_error"),
                ok ? EXIT_OK : EXIT_CALCULATION_ERROR);
    }

    private Outcome mode(Object id, Map<String, Object> request) {
        final String name = Json.optionalString(request, "mode",
                Json.optionalString(request, "value", null));
        if (name == null) {
            return failure(id, "mode", "bad_request", "missing mode");
        }
        session.setMode(Tokens.modeFor(name));
        return success(id, "mode", null, EXIT_OK);
    }

    private Outcome convert(Object id, Map<String, Object> request) {
        final String category = Json.string(request, "category");
        final String from = Json.string(request, "from");
        final String to = Json.string(request, "to");
        final String value = Json.string(request, "value");
        return success(id, "convert", session.convert(category, from, to, value), EXIT_OK);
    }

    private Outcome dateDiff(Object id, Map<String, Object> request) {
        final LocalDate from = LocalDate.parse(Json.string(request, "from"));
        final LocalDate to = LocalDate.parse(Json.string(request, "to"));
        final String result = session.dateDifference(from, to);
        if (result == null) {
            return failure(id, "date_diff", "out_of_range", "date difference is not representable");
        }
        return success(id, "date_diff", result, EXIT_OK);
    }

    private Outcome dateAdd(Object id, Map<String, Object> request, boolean subtract) {
        final LocalDate start = LocalDate.parse(Json.string(request, "from"));
        final int years = Json.integer(request, "years", 0);
        final int months = Json.integer(request, "months", 0);
        final int days = Json.integer(request, "days", 0);
        final LocalDate result = session.dateAdd(start, years, months, days, subtract);
        if (result == null) {
            return failure(id, subtract ? "date_subtract" : "date_add",
                    "out_of_range", "date result is out of range");
        }
        return success(id, subtract ? "date_subtract" : "date_add", result.toString(), EXIT_OK);
    }

    private Outcome capabilities(Object id) {
        final Map<String, Object> response = response(id, "capabilities", true, null);
        response.put("version", AppInfo.VERSION);
        response.put("seed", session.randomSeed());
        response.put("modes", Arrays.stream(CalculatorMode.values()).map(Enum::name).toList());
        response.put("categories", Arrays.stream(UnitCategory.values()).map(Enum::name).toList());
        response.put("operations", operations());
        response.put("tokens", Tokens.supportedTokens());
        return new Outcome(response, EXIT_OK);
    }

    // ---- Response construction -------------------------------------------------------------------

    private Outcome success(Object id, String op, String result, int exitCode) {
        final Map<String, Object> response = response(id, op, true, null);
        if (result != null) {
            response.put("result", result);
        }
        return new Outcome(response, exitCode);
    }

    private Outcome failure(Object id, String op, String errorCode, String message) {
        final Map<String, Object> response = response(id, op, false, errorCode);
        response.put("message", message == null ? "" : message);
        final int exitCode = "out_of_range".equals(errorCode) || "calculator_error".equals(errorCode)
                ? EXIT_CALCULATION_ERROR
                : EXIT_PROTOCOL_ERROR;
        return new Outcome(response, exitCode);
    }

    private Map<String, Object> response(Object id, String op, boolean ok, String errorCode) {
        final CalculatorState state = session.state();
        return Json.object(
                "id", id,
                "op", op,
                "ok", ok,
                "errorCode", errorCode,
                "error", state.error(),
                "errorMessage", state.errorMessage(),
                "display", state.display(),
                "rawDisplay", state.rawDisplay(),
                "expression", state.expression(),
                "mode", state.mode().name(),
                "radix", state.radix(),
                "width", state.width().name(),
                "angle", state.angleType().name(),
                "result", null,
                "history", history(state),
                "memory", memory(state));
    }

    private static List<Map<String, Object>> history(CalculatorState state) {
        final List<Map<String, Object>> entries = new ArrayList<>();
        for (var entry : state.history()) {
            entries.add(Json.object("expression", entry.expression(), "result", entry.result()));
        }
        return entries;
    }

    private static List<String> memory(CalculatorState state) {
        final List<String> values = new ArrayList<>();
        final int precision = state.mode().precision();
        for (var value : state.memory()) {
            values.add(value.toDecimalString(precision));
        }
        return values;
    }

    private static List<String> tokensOf(Map<String, Object> request) {
        final Object raw = request.get("tokens");
        if (raw instanceof List<?> items) {
            final List<String> tokens = new ArrayList<>();
            for (Object item : items) {
                if (!(item instanceof String token)) {
                    throw new JsonException("tokens must be strings");
                }
                tokens.add(token);
            }
            if (tokens.isEmpty()) {
                throw new JsonException("tokens must not be empty");
            }
            return tokens;
        }
        final String expression = Json.optionalString(request, "expression",
                Json.optionalString(request, "value", null));
        if (expression == null) {
            throw new JsonException("missing tokens or expression");
        }
        return split(expression);
    }

    private static List<String> split(String expression) {
        final List<String> tokens = new ArrayList<>();
        for (String token : expression.trim().split("\\s+")) {
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return tokens;
    }
}
