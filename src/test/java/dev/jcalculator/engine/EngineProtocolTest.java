package dev.jcalculator.engine;

import dev.jcalculator.harness.CalculatorSession;
import dev.jcalculator.manager.CalculatorManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The display protocol the engine reports through, and the error taxonomy behind it.
 *
 * <p>These are the parts of the original's engine that a presentation layer sees: the expression as
 * tokens, the parenthesis depth, and the signals that are not errors. The engine is driven through
 * {@link CalculatorSession} because that is the surface every front end shares.
 */
class EngineProtocolTest {

    @Test
    void expressionTokensCarryTheCommandBehindEachPart() {
        final CalculatorSession session = new CalculatorSession(CalculatorMode.SCIENTIFIC, 0L);
        session.pressExpression("2 + 3 =");

        final List<ExpressionToken> tokens = session.manager().engine().expressionTokens();
        assertEquals(4, tokens.size());
        assertEquals("2", tokens.get(0).text());
        assertTrue(tokens.get(0).isOperand());
        assertEquals(Command.ADD, tokens.get(1).command());
        assertTrue(tokens.get(1).isBinaryOperator());
        assertEquals("3", tokens.get(2).text());
        assertEquals(Command.EQUALS, tokens.get(3).command());
    }

    @Test
    void aCompletedEquationStaysOnTheExpressionLine() {
        final CalculatorSession session = new CalculatorSession(CalculatorMode.STANDARD, 0L);
        session.pressExpression("2 + 3 =");
        assertEquals("2 + 3=", session.state().expression());

        session.press("4");
        assertEquals("", session.state().expression());
    }

    @Test
    void openingAnOperatorOverAnotherInvertsPrecedenceInTheLine() {
        final CalculatorSession session = new CalculatorSession(CalculatorMode.SCIENTIFIC, 0L);
        session.pressExpression("1 * 2 +");
        assertEquals("1 × 2 +", session.state().expression());

        session.press("^");
        assertEquals("(1 × 2) ^", session.state().expression());
    }

    @Test
    void theParenthesisDepthFollowsTheExpression() {
        final CalculatorSession session = new CalculatorSession(CalculatorMode.SCIENTIFIC, 0L);
        session.pressExpression("( 1 + 2");
        assertEquals(1, session.state().parenthesisCount());

        session.press("=");
        assertEquals(0, session.state().parenthesisCount());
        assertEquals("3", session.state().display());
    }

    @Test
    void aRightParenthesisWithNothingOpenRaisesASignal() {
        final CalculatorSession session = new CalculatorSession(CalculatorMode.SCIENTIFIC, 0L);
        session.pressExpression("9 )");

        assertEquals(CalculatorManager.SIGNAL_NO_RIGHT_PAREN, session.state().signal());
        assertEquals(0, session.state().parenthesisCount());
        assertEquals("9", session.state().display());
    }

    @Test
    void aBinaryOperatorRaisesASignal() {
        final CalculatorSession session = new CalculatorSession(CalculatorMode.SCIENTIFIC, 0L);
        session.pressExpression("2 +");

        assertEquals(CalculatorManager.SIGNAL_BINARY_OPERATOR, session.state().signal());
    }

    @Test
    void aRefusedDigitRaisesTheMaxDigitsSignalWithoutBecomingAnError() {
        final CalculatorSession session = new CalculatorSession(CalculatorMode.STANDARD, 0L);
        for (int i = 0; i < 20; i++) {
            session.press("1");
        }

        assertEquals(CalculatorManager.SIGNAL_MAX_DIGITS, session.state().signal());
        assertEquals(false, session.state().error());
        assertEquals(16, session.state().rawDisplay().length());
    }

    @Test
    void anErrorCarriesAStableCodeAndTheOriginalText() {
        final CalculatorSession session = new CalculatorSession(CalculatorMode.STANDARD, 0L);
        session.pressExpression("1 / 0 =");

        assertEquals(true, session.state().error());
        assertEquals("Cannot divide by zero", session.state().errorMessage());
        assertEquals("divide_by_zero", session.manager().engine().errorCode());
        assertEquals(EngineError.DIVIDE_BY_ZERO, session.manager().engine().error());
    }

    @Test
    void anOutOfDomainFunctionReportsTheDomainCode() {
        final CalculatorSession session = new CalculatorSession(CalculatorMode.SCIENTIFIC, 0L);
        session.pressExpression("0 - 1 =");
        session.press("sqrt");

        assertEquals(true, session.state().error());
        assertEquals("Invalid input", session.state().errorMessage());
        assertEquals("domain", session.manager().engine().errorCode());
    }

    @Test
    void clearingClearsTheErrorCode() {
        final CalculatorSession session = new CalculatorSession(CalculatorMode.STANDARD, 0L);
        session.pressExpression("1 / 0 =");
        session.press("clear");

        assertEquals(false, session.state().error());
        assertNull(session.manager().engine().errorCode());
    }
}
