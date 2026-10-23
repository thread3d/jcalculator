package dev.jcalculator.harness;

import dev.jcalculator.engine.CalculatorMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HarnessProtocolTest {

    private HarnessProtocol protocol;

    @BeforeEach
    void setUp() {
        protocol = new HarnessProtocol(new CalculatorSession(CalculatorMode.STANDARD, 0L));
    }

    @Test
    void bareLineIsATokenSequence() {
        final HarnessProtocol.Outcome outcome = protocol.handleLine("2 + 3 =");
        assertEquals(HarnessProtocol.EXIT_OK, outcome.exitCode());
        assertEquals("5", outcome.response().get("display"));
        assertEquals(Boolean.TRUE, outcome.response().get("ok"));
    }

    @Test
    void jsonRequestIsAnsweredInTheSameShape() {
        final HarnessProtocol.Outcome outcome = protocol.execute(
                Json.object("id", 7, "op", "press", "tokens", List.of("2", "+", "3", "=")));
        assertEquals(7, outcome.response().get("id"));
        assertEquals("press", outcome.response().get("op"));
        assertEquals("5", outcome.response().get("display"));
        assertEquals(Boolean.TRUE, outcome.response().get("ok"));
        assertNull(outcome.response().get("errorCode"));
    }

    @Test
    void calculatorErrorIsNotAProtocolError() {
        final HarnessProtocol.Outcome outcome = protocol.handleLine("1 / 0 =");
        assertEquals(HarnessProtocol.EXIT_CALCULATION_ERROR, outcome.exitCode());
        assertEquals(Boolean.FALSE, outcome.response().get("ok"));
        assertEquals(Boolean.TRUE, outcome.response().get("error"));
        assertEquals("calculator_error", outcome.response().get("errorCode"));
    }

    @Test
    void unknownTokenIsAProtocolError() {
        final HarnessProtocol.Outcome outcome = protocol.handleLine("2 + bogus =");
        assertEquals(HarnessProtocol.EXIT_PROTOCOL_ERROR, outcome.exitCode());
        assertEquals(Boolean.FALSE, outcome.response().get("ok"));
        assertEquals("unknown_token", outcome.response().get("errorCode"));
    }

    @Test
    void unknownOperationIsAProtocolError() {
        final HarnessProtocol.Outcome outcome = protocol.execute(Json.object("op", "fly"));
        assertEquals(HarnessProtocol.EXIT_PROTOCOL_ERROR, outcome.exitCode());
        assertEquals("unknown_op", outcome.response().get("errorCode"));
    }

    @Test
    void malformedJsonIsAParseError() {
        final HarnessProtocol.Outcome outcome = protocol.handleLine("{oops");
        assertEquals(HarnessProtocol.EXIT_PROTOCOL_ERROR, outcome.exitCode());
        assertEquals("parse_error", outcome.response().get("errorCode"));
    }

    @Test
    void blankLinesProduceNoResponse() {
        assertNull(protocol.handleLine(""));
        assertNull(protocol.handleLine("   "));
    }

    @Test
    void conversionUsesTheConverter() {
        final HarnessProtocol.Outcome outcome = protocol.execute(Json.object(
                "id", 1, "op", "convert",
                "category", "length", "from", "m", "to", "ft", "value", "2.5"));
        assertEquals(HarnessProtocol.EXIT_OK, outcome.exitCode());
        assertEquals("8.2021", outcome.response().get("result"));
    }

    @Test
    void unknownConversionCategoryIsRejected() {
        final HarnessProtocol.Outcome outcome = protocol.execute(Json.object(
                "op", "convert", "category", "nonsense", "from", "m", "to", "ft", "value", "1"));
        assertEquals("bad_request", outcome.response().get("errorCode"));
    }

    @Test
    void datesAreComputed() {
        final HarnessProtocol.Outcome difference = protocol.execute(Json.object(
                "op", "date_diff", "from", "2024-01-01", "to", "2024-12-31"));
        assertEquals("11 months 4 weeks 2 days", difference.response().get("result"));

        final HarnessProtocol.Outcome added = protocol.execute(Json.object(
                "op", "date_add", "from", "2024-01-31", "months", 1));
        assertEquals("2024-02-29", added.response().get("result"));
    }

    @Test
    void modeSwitchesThePage() {
        final HarnessProtocol.Outcome outcome = protocol.execute(
                Json.object("op", "mode", "mode", "scientific"));
        assertEquals("SCIENTIFIC", outcome.response().get("mode"));
        assertEquals(CalculatorMode.SCIENTIFIC, protocol.session().manager().mode());
    }

    @Test
    void capabilitiesDescribeTheProtocol() {
        final HarnessProtocol.Outcome outcome = protocol.execute(Json.object("op", "capabilities"));
        assertEquals(Boolean.TRUE, outcome.response().get("ok"));
        assertEquals(dev.jcalculator.app.AppInfo.VERSION, outcome.response().get("version"));
        assertTrue(Json.asArray(outcome.response().get("tokens")).contains("sqrt"));
        assertTrue(Json.asArray(outcome.response().get("categories")).contains("LENGTH"));
    }

    @Test
    void stateIsReportedAfterEveryPress() {
        protocol.handleLine("2 + 3 =");
        final HarnessProtocol.Outcome state = protocol.execute(Json.object("op", "state"));
        assertEquals("5", state.response().get("display"));
        assertEquals("STANDARD", state.response().get("mode"));
        assertEquals(10, state.response().get("radix"));
    }
}
