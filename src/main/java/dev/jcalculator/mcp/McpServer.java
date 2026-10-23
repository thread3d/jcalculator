package dev.jcalculator.mcp;

import dev.jcalculator.app.AppInfo;
import dev.jcalculator.diagnostics.Faults;
import dev.jcalculator.diagnostics.IssueJournal;
import dev.jcalculator.diagnostics.IssueSurface;
import dev.jcalculator.engine.CalculatorMode;
import dev.jcalculator.harness.CalculatorSession;
import dev.jcalculator.harness.HarnessProtocol;
import dev.jcalculator.harness.Json;
import dev.jcalculator.harness.JsonException;
import dev.jcalculator.harness.Tokens;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An MCP (Model Context Protocol) server over standard input and standard output.
 *
 * <p>Messages are newline-delimited JSON-RPC 2.0, as the MCP stdio transport specifies. Nothing else
 * is ever written to standard output: diagnostics go to standard error, so a client can read the
 * stream without filtering.
 *
 * <p>The server implements the lifecycle methods a client needs — {@code initialize}, {@code ping},
 * {@code tools/list} and {@code tools/call} — plus {@code resources/list} and {@code prompts/list}
 * as empty lists. It advertises the {@code tools} capability and nothing else.
 *
 * <pre>
 *   {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18"}}
 *   {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"calculator_press",
 *     "arguments":{"tokens":["2","+","3","="]}}}
 * </pre>
 *
 * <p>Every tool call is deterministic: the server's session is created with {@code --seed}
 * (default {@code 0}), so {@code rand} repeats across runs.
 */
public final class McpServer {

    public static final String SERVER_NAME = "jcalculator";
    public static final String PREFERRED_PROTOCOL_VERSION = "2025-06-18";

    /** Protocol revisions this server accepts; anything else is answered with the preferred one. */
    public static List<String> supportedProtocols() {
        return List.of("2024-11-05", "2025-03-26", "2025-06-18");
    }

    private static final int PARSE_ERROR = -32700;
    private static final int INVALID_REQUEST = -32600;
    private static final int METHOD_NOT_FOUND = -32601;
    private static final int INVALID_PARAMS = -32602;
    private static final int INTERNAL_ERROR = -32603;

    private static final String INSTRUCTIONS = """
            jCalculator is a deterministic calculator. Press buttons with calculator_press
            (tokens such as 7, +, sqrt, hex, qword, deg, ms and '=' to evaluate) or pass a token
            string to calculator_evaluate. State persists between calls: calculator_state reads it,
            calculator_reset clears it, and calculator_mode switches pages. Use calculator_convert
            and the date tools for the converter and date pages.

            When a tool misbehaves, refuses input it should accept, or returns a result you believe
            is wrong, record it with calculator_report_issue. The project keeps a durable issue
            journal so a later session can fix it: calculator_issues lists the entries and
            calculator_resolve_issue closes one with a note.""";

    private final CalculatorTools tools;
    private final PrintWriter out;
    private final IssueJournal journal;

    public McpServer(CalculatorSession session, PrintWriter out) {
        this(session, out, IssueJournal.shared());
    }

    /**
     * Creates a server over one session.
     *
     * @param session the calculator session to expose
     * @param out     where JSON-RPC responses are written
     * @param journal where problems met through this server are recorded
     */
    public McpServer(CalculatorSession session, PrintWriter out, IssueJournal journal) {
        this.journal = Objects.requireNonNull(journal, "journal");
        final HarnessProtocol protocol =
                new HarnessProtocol(Objects.requireNonNull(session, "session"), journal, IssueSurface.MCP);
        this.tools = new CalculatorTools(protocol);
        this.out = Objects.requireNonNull(out, "out");
    }

    /** Runs the server against stdin/stdout. */
    public static void main(String[] args) throws IOException {
        long seed = 0L;
        CalculatorMode mode = CalculatorMode.STANDARD;
        for (int index = 0; index < args.length; index++) {
            switch (args[index]) {
                case "--seed" -> {
                    if (index + 1 >= args.length) {
                        usage("--seed needs a number");
                    }
                    try {
                        seed = Long.parseLong(args[index + 1]);
                    } catch (NumberFormatException exception) {
                        usage("invalid seed: " + args[index + 1]);
                    }
                    index++;
                }
                case "--mode" -> {
                    if (index + 1 >= args.length) {
                        usage("--mode needs standard, scientific or programmer");
                    }
                    try {
                        mode = Tokens.modeFor(args[index + 1]);
                    } catch (IllegalArgumentException exception) {
                        usage(exception.getMessage());
                    }
                    index++;
                }
                case "--version", "-V" -> {
                    System.out.println(AppInfo.displayVersion());
                    return;
                }
                case "--help", "-h" -> {
                    System.out.print(help());
                    return;
                }
                default -> usage("unknown option: " + args[index]);
            }
        }

        try (PrintWriter out = new PrintWriter(
                new OutputStreamWriter(System.out, StandardCharsets.UTF_8), false)) {
            final McpServer server = new McpServer(new CalculatorSession(mode, seed), out);
            server.serve(new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)));
        }
    }

    /** Reads messages until end of input, answering each one. */
    public void serve(BufferedReader in) throws IOException {
        String line;
        while ((line = in.readLine()) != null) {
            final String response = handle(line);
            if (response != null) {
                out.println(response);
                out.flush();
            }
        }
    }

    /**
     * Handles one input line.
     *
     * @return the JSON-RPC response, or {@code null} for a blank line or a notification
     */
    public String handle(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        final Object parsed;
        try {
            parsed = Json.parse(line);
        } catch (JsonException exception) {
            return error(null, PARSE_ERROR, "parse error: " + exception.getMessage());
        }
        if (!(parsed instanceof Map<?, ?>)) {
            return error(null, INVALID_REQUEST, "a request must be a JSON object");
        }
        final Map<String, Object> request = Json.asObject(parsed);
        if (!"2.0".equals(request.get("jsonrpc"))) {
            return error(request.get("id"), INVALID_REQUEST, "jsonrpc must be \"2.0\"");
        }
        final Object methodValue = request.get("method");
        if (!(methodValue instanceof String method)) {
            return error(request.get("id"), INVALID_REQUEST, "missing method");
        }
        final boolean notification = !request.containsKey("id");
        final Map<String, Object> params = request.get("params") instanceof Map<?, ?> map
                ? Json.asObject(map)
                : Map.of();
        try {
            final Map<String, Object> result = dispatch(method, params);
            return notification ? null : success(request.get("id"), result);
        } catch (RpcException exception) {
            return notification ? null : error(request.get("id"), exception.code(), exception.getMessage());
        } catch (RuntimeException exception) {
            recordInternalFailure(exception, method);
            return notification ? null
                    : error(request.get("id"), INTERNAL_ERROR, "internal error: " + exception.getMessage());
        }
    }

    private void recordInternalFailure(RuntimeException exception, String method) {
        try {
            journal.record(Faults.internalError(IssueSurface.MCP, exception,
                    Json.object("method", method, "surface", IssueSurface.MCP.wireName())));
        } catch (RuntimeException ignored) {
            // Best effort: recording a problem must never replace the response to the client.
        }
    }

    private Map<String, Object> dispatch(String method, Map<String, Object> params) {
        return switch (method) {
            case "initialize" -> initialize(params);
            case "ping" -> Map.of();
            case "tools/list" -> Json.object("tools", tools.descriptors());
            case "tools/call" -> callTool(params);
            case "resources/list" -> Json.object("resources", List.of());
            case "prompts/list" -> Json.object("prompts", List.of());
            case "logging/setLevel" -> Map.of();
            default -> {
                if (method.startsWith("notifications/")) {
                    yield Map.of();
                }
                throw new RpcException(METHOD_NOT_FOUND, "method not found: " + method);
            }
        };
    }

    private Map<String, Object> initialize(Map<String, Object> params) {
        final String requested = Json.optionalString(params, "protocolVersion", PREFERRED_PROTOCOL_VERSION);
        final String version = supportedProtocols().contains(requested)
                ? requested
                : PREFERRED_PROTOCOL_VERSION;
        return Json.object(
                "protocolVersion", version,
                "capabilities", Json.object("tools", Json.object("listChanged", false)),
                "serverInfo", Json.object("name", SERVER_NAME, "version", AppInfo.VERSION),
                "instructions", instructions());
    }

    /** The base instructions, plus a nudge when the journal already holds open issues. */
    private String instructions() {
        if (!journal.isEnabled()) {
            return INSTRUCTIONS;
        }
        try {
            final long open = journal.read().openCount();
            if (open == 0) {
                return INSTRUCTIONS;
            }
            return INSTRUCTIONS + "\n\n" + open
                    + " open issue(s) about this calculator are on record;"
                    + " call calculator_issues before relying on the tool.";
        } catch (RuntimeException exception) {
            return INSTRUCTIONS;
        }
    }

    private Map<String, Object> callTool(Map<String, Object> params) {
        final String name;
        try {
            name = Json.string(params, "name");
        } catch (JsonException exception) {
            throw new RpcException(INVALID_PARAMS, "tools/call needs a tool name");
        }
        if (!tools.has(name)) {
            throw new RpcException(INVALID_PARAMS, "unknown tool: " + name);
        }
        final Map<String, Object> arguments = params.get("arguments") instanceof Map<?, ?> map
                ? Json.asObject(map)
                : Map.of();
        final Map<String, Object> response = tools.call(name, arguments);
        final boolean isError = !Boolean.TRUE.equals(response.get("ok"));
        return Json.object(
                "content", List.of(Json.object("type", "text", "text", Json.write(response))),
                "structuredContent", response,
                "isError", isError);
    }

    private static String success(Object id, Map<String, Object> result) {
        return Json.write(Json.object("jsonrpc", "2.0", "id", id, "result", result));
    }

    private static String error(Object id, int code, String message) {
        return Json.write(Json.object(
                "jsonrpc", "2.0",
                "id", id,
                "error", Json.object("code", code, "message", message)));
    }

    private static void usage(String message) {
        System.err.println("jcalculator-mcp: " + message);
        System.err.print(help());
        System.exit(64);
    }

    private static String help() {
        return """
                Usage: McpServer [--seed N] [--mode MODE]

                An MCP server speaking newline-delimited JSON-RPC 2.0 over stdin/stdout.

                  --seed N     seed the random key (default 0)
                  --mode MODE  standard, scientific or programmer (default standard)
                  --version    print the version
                  --help       print this help

                Problems met through the tools are recorded in the issue journal selected by the
                JCALC_ISSUES environment variable (or the jcalculator.issues.path system property);
                the bin/jcalculator-mcp launcher points it at the project's .jcalculator directory.
                """;
    }

    /** A JSON-RPC protocol error carrying its numeric code. */
    private static final class RpcException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final int code;

        RpcException(int code, String message) {
            super(message);
            this.code = code;
        }

        int code() {
            return code;
        }
    }
}
