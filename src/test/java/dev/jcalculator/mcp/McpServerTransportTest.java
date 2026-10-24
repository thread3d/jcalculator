package dev.jcalculator.mcp;

import dev.jcalculator.diagnostics.IssueJournal;
import dev.jcalculator.engine.CalculatorMode;
import dev.jcalculator.harness.CalculatorSession;
import dev.jcalculator.harness.Json;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The parts of the MCP transport that a well-behaved client never exercises: malformed envelopes,
 * notifications that fail, the optional listings, and the line loop itself.
 */
class McpServerTransportTest {

    private McpServer server;

    @BeforeEach
    void setUp() {
        server = new McpServer(new CalculatorSession(CalculatorMode.STANDARD, 0L),
                new PrintWriter(new StringWriter(), true), IssueJournal.disabled());
    }

    /** JSON numbers arrive as BigDecimal, so read the code as an int. */
    private static int codeOf(Map<String, Object> error) {
        return ((Number) error.get("code")).intValue();
    }

    private static Map<String, Object> errorOf(String response) {
        final Map<String, Object> document = Json.parseObject(response);
        assertEquals("2.0", document.get("jsonrpc"));
        return Json.asObject(document.get("error"));
    }

    // ---- Envelope validation ----------------------------------------------------------------------

    @Test
    void aBlankLineIsIgnored() {
        assertNull(server.handle(""));
        assertNull(server.handle("   "));
        assertNull(server.handle(null));
    }

    @Test
    void aRequestThatIsNotAnObjectIsInvalid() {
        final Map<String, Object> error = errorOf(server.handle("[1,2,3]"));
        assertEquals(-32600, codeOf(error));
        assertEquals("a request must be a JSON object", error.get("message"));
    }

    @Test
    void aRequestWithoutMethodIsInvalid() {
        final Map<String, Object> error = errorOf(server.handle("{\"jsonrpc\":\"2.0\",\"id\":3}"));
        assertEquals(-32600, codeOf(error));
        assertEquals("missing method", error.get("message"));
    }

    @Test
    void aRequestWithoutTheJsonRpcMemberIsInvalid() {
        final Map<String, Object> error = errorOf(server.handle("{\"id\":4,\"method\":\"ping\"}"));
        assertEquals(-32600, codeOf(error));
        assertEquals("jsonrpc must be \"2.0\"", error.get("message"));
    }

    // ---- Notifications ----------------------------------------------------------------------------

    @Test
    void aNotificationNeverGetsAnAnswerEvenWhenItFails() {
        assertNull(server.handle("{\"jsonrpc\":\"2.0\",\"method\":\"ping\"}"));
        assertNull(server.handle("{\"jsonrpc\":\"2.0\",\"method\":\"no/such/method\"}"));
        assertNull(server.handle("{\"jsonrpc\":\"2.0\",\"method\":\"tools/call\",\"params\":{}}"));
    }

    @Test
    void aNotificationMethodIsAnsweredWithAnEmptyResultWhenItCarriesAnId() {
        final Map<String, Object> document = Json.parseObject(
                server.handle("{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"notifications/initialized\"}"));
        assertTrue(Json.asObject(document.get("result")).isEmpty());
    }

    // ---- Optional listings ------------------------------------------------------------------------

    @Test
    void theOptionalListingsAnswerWithEmptyLists() {
        assertTrue(Json.asArray(resultOf(server.handle(request(1, "resources/list"))).get("resources")).isEmpty());
        assertTrue(Json.asArray(resultOf(server.handle(request(2, "prompts/list"))).get("prompts")).isEmpty());
        assertTrue(resultOf(server.handle(request(3, "logging/setLevel"))).isEmpty());
    }

    @Test
    void initializeWithoutParamsUsesThePreferredVersion() {
        final Map<String, Object> result = resultOf(server.handle("{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"initialize\"}"));
        assertEquals(McpServer.PREFERRED_PROTOCOL_VERSION, result.get("protocolVersion"));
    }

    @Test
    void initializeListsEverySupportedRevision() {
        assertEquals(List.of("2024-11-05", "2025-03-26", "2025-06-18"), McpServer.supportedProtocols());
    }

    // ---- tools/call arguments ---------------------------------------------------------------------

    @Test
    void aToolCallWithoutANameIsInvalidParams() {
        final Map<String, Object> error = errorOf(server.handle(request(6, "tools/call")));
        assertEquals(-32602, codeOf(error));
        assertEquals("tools/call needs a tool name", error.get("message"));
    }

    @Test
    void argumentsThatAreNotAnObjectAreTreatedAsEmpty() {
        final Map<String, Object> result = resultOf(server.handle(
                "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"calculator_state\",\"arguments\":\"not an object\"}}"));
        assertEquals(Boolean.FALSE, result.get("isError"));
        assertNotNull(Json.asObject(result.get("structuredContent")));
    }

    @Test
    void aToolResultCarriesItsContentAndStructuredForm() {
        final Map<String, Object> result = resultOf(server.handle(request(8, "tools/call",
                Json.object("name", "calculator_press", "arguments", Json.object("tokens", List.of("7"))))));
        assertEquals(Boolean.FALSE, result.get("isError"));
        final List<Object> content = Json.asArray(result.get("content"));
        assertEquals(1, content.size());
        assertEquals("text", Json.asObject(content.get(0)).get("type"));
    }

    // ---- The line loop ----------------------------------------------------------------------------

    @Test
    void theLoopAnswersEveryRequestAndSkipsBlankLines() throws Exception {
        try (StringWriter written = new StringWriter();
                PrintWriter sink = new PrintWriter(written, true);
                BufferedReader input = new BufferedReader(new StringReader(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}\n"
                                + "\n"
                                + "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}\n"))) {

            new McpServer(new CalculatorSession(CalculatorMode.STANDARD, 0L), sink, IssueJournal.disabled())
                    .serve(input);

            final List<String> answers = written.toString().lines().filter(line -> !line.isBlank()).toList();
            assertEquals(2, answers.size(), written.toString());
            assertTrue(answers.get(0).contains("\"id\":1"), answers.get(0));
            assertTrue(answers.get(1).contains("\"tools\""), answers.get(1));
        }
    }

    // ---- Helpers ----------------------------------------------------------------------------------

    private static String request(int id, String method) {
        return Json.write(Json.object("jsonrpc", "2.0", "id", id, "method", method));
    }

    private static String request(int id, String method, Map<String, Object> params) {
        return Json.write(Json.object("jsonrpc", "2.0", "id", id, "method", method, "params", params));
    }

    private static Map<String, Object> resultOf(String response) {
        assertNotNull(response, "expected a response");
        return Json.asObject(Json.parseObject(response).get("result"));
    }
}
