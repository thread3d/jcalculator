package dev.jcalculator.mcp;

import dev.jcalculator.app.AppInfo;
import dev.jcalculator.engine.CalculatorMode;
import dev.jcalculator.harness.CalculatorSession;
import dev.jcalculator.harness.Json;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpServerTest {

    private McpServer server;

    @BeforeEach
    void setUp() {
        server = new McpServer(
                new CalculatorSession(CalculatorMode.STANDARD, 0L),
                new PrintWriter(new StringWriter(), true));
    }

    @Test
    void initializeNegotiatesTheProtocolVersion() {
        final Map<String, Object> result = resultOf(server.handle(request(1, "initialize",
                Json.object("protocolVersion", "2025-06-18"))));
        assertEquals("2025-06-18", result.get("protocolVersion"));
        final Map<String, Object> info = Json.asObject(result.get("serverInfo"));
        assertEquals(McpServer.SERVER_NAME, info.get("name"));
        assertEquals(AppInfo.VERSION, info.get("version"));
        assertTrue(Json.asObject(result.get("capabilities")).containsKey("tools"));
    }

    @Test
    void unsupportedProtocolFallsBackToThePreferredVersion() {
        final Map<String, Object> result = resultOf(server.handle(request(1, "initialize",
                Json.object("protocolVersion", "1999-01-01"))));
        assertEquals(McpServer.PREFERRED_PROTOCOL_VERSION, result.get("protocolVersion"));
    }

    @Test
    void listsTheCalculatorTools() {
        final Map<String, Object> result = resultOf(server.handle(request(2, "tools/list", Map.of())));
        final List<Object> tools = Json.asArray(result.get("tools"));
        assertTrue(tools.stream()
                .map(Json::asObject)
                .anyMatch(tool -> "calculator_press".equals(tool.get("name"))));
        assertTrue(tools.stream()
                .map(Json::asObject)
                .anyMatch(tool -> "calculator_convert".equals(tool.get("name"))));
        assertEquals(11, tools.size());
    }

    @Test
    void callsAPressTool() {
        final Map<String, Object> result = call("calculator_press", Json.object("tokens", List.of("2", "+", "3", "=")));
        assertEquals(Boolean.FALSE, result.get("isError"));
        final Map<String, Object> body = bodyOf(result);
        assertEquals("5", body.get("display"));
        assertEquals(Boolean.TRUE, body.get("ok"));
    }

    @Test
    void calculatorErrorsAreToolErrors() {
        final Map<String, Object> result = call("calculator_press", Json.object("tokens", List.of("1", "/", "0", "=")));
        assertEquals(Boolean.TRUE, result.get("isError"));
        assertEquals("calculator_error", bodyOf(result).get("errorCode"));
    }

    @Test
    void toolCallsShareSessionState() {
        call("calculator_press", Json.object("tokens", List.of("2", "+", "3", "=")));
        final Map<String, Object> state = call("calculator_state", Json.object());
        assertEquals("5", bodyOf(state).get("display"));
        assertEquals("STANDARD", bodyOf(state).get("mode"));
    }

    @Test
    void convertsUnits() {
        final Map<String, Object> result = call("calculator_convert", Json.object(
                "category", "length", "from", "m", "to", "ft", "value", "2.5"));
        assertEquals("8.2021", bodyOf(result).get("result"));
    }

    @Test
    void computesDates() {
        final Map<String, Object> difference = call("calculator_date_diff", Json.object(
                "from", "2024-01-01", "to", "2024-12-31"));
        assertEquals("11 months 4 weeks 2 days", bodyOf(difference).get("result"));

        final Map<String, Object> added = call("calculator_date_add", Json.object(
                "from", "2024-01-31", "months", 1));
        assertEquals("2024-02-29", bodyOf(added).get("result"));
    }

    @Test
    void unknownToolIsAnInvalidParamsError() {
        final Map<String, Object> response = Json.parseObject(server.handle(request(3, "tools/call",
                Json.object("name", "nope", "arguments", Json.object()))));
        assertEquals(-32602, Json.integer(Json.asObject(response.get("error")), "code", 0));
    }

    @Test
    void unknownMethodIsMethodNotFound() {
        final Map<String, Object> response = Json.parseObject(server.handle(request(4, "nope", Map.of())));
        assertEquals(-32601, Json.integer(Json.asObject(response.get("error")), "code", 0));
    }

    @Test
    void malformedJsonIsAParseError() {
        final Map<String, Object> response = Json.parseObject(server.handle("not json"));
        assertEquals(-32700, Json.integer(Json.asObject(response.get("error")), "code", 0));
    }

    @Test
    void notificationsGetNoResponse() {
        assertNull(server.handle("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"));
    }

    @Test
    void wrongJsonRpcVersionIsInvalidRequest() {
        final Map<String, Object> response = Json.parseObject(
                server.handle("{\"jsonrpc\":\"1.0\",\"id\":1,\"method\":\"ping\"}"));
        assertEquals(-32600, Json.integer(Json.asObject(response.get("error")), "code", 0));
    }

    @Test
    void pingIsAnswered() {
        final Map<String, Object> result = resultOf(server.handle(request(5, "ping", Map.of())));
        assertTrue(result.isEmpty());
    }

    // ---- Helpers ---------------------------------------------------------------------------------

    private Map<String, Object> call(String name, Map<String, Object> arguments) {
        final Map<String, Object> response = Json.parseObject(server.handle(request(7, "tools/call",
                Json.object("name", name, "arguments", arguments))));
        return Json.asObject(response.get("result"));
    }

    private static Map<String, Object> bodyOf(Map<String, Object> result) {
        final List<Object> content = Json.asArray(result.get("content"));
        final Map<String, Object> block = Json.asObject(content.get(0));
        return Json.parseObject((String) block.get("text"));
    }

    private static Map<String, Object> resultOf(String response) {
        return Json.asObject(Json.asObject(Json.parseObject(response)).get("result"));
    }

    private static String request(Object id, String method, Map<String, Object> params) {
        return Json.write(Json.object(
                "jsonrpc", "2.0",
                "id", id,
                "method", method,
                "params", params));
    }
}
