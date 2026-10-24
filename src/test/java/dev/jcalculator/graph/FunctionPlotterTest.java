package dev.jcalculator.graph;

import dev.jcalculator.graph.FunctionPlotter.Plot;
import dev.jcalculator.graph.FunctionPlotter.Sample;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The graphing model: how a function is read, and how it is sampled. */
class FunctionPlotterTest {

    private static void assertClose(double expected, double actual) {
        assertTrue(Math.abs(expected - actual) < 1e-9, () -> "expected " + expected + " but was " + actual);
    }

    @Test
    void aPolynomialIsSampledAcrossItsRange() {
        final Plot plot = FunctionPlotter.plot("x^2", -2, 2, 5);
        assertEquals(5, plot.samples().size());
        assertClose(-2, plot.samples().get(0).x());
        assertClose(4, plot.samples().get(0).y());
        assertClose(0, plot.samples().get(2).y());
        assertClose(4, plot.samples().get(4).y());
        assertClose(0, plot.minY());
        assertClose(4, plot.maxY());
        assertClose(4, plot.height());
    }

    @Test
    void aFunctionIsEvaluatedWithThePagesOwnAngleUnit() {
        final Plot plot = FunctionPlotter.plot("sin(x)", 0, 180, 3);
        assertEquals(3, plot.samples().size());
        assertClose(0, plot.samples().get(0).y());
        assertClose(1, plot.samples().get(1).y());
        assertClose(0, plot.samples().get(2).y());
    }

    @Test
    void aPointTheEngineRefusesIsLeftOutRatherThanDrawnAsZero() {
        final Plot plot = FunctionPlotter.plot("1/x", -1, 1, 3);
        assertEquals(2, plot.samples().size());
        assertEquals(List.of(-1.0, 1.0), plot.samples().stream().map(Sample::x).toList());
    }

    @Test
    void nothingCanBeDrawnWhenEveryPointFails() {
        final Plot plot = FunctionPlotter.plot("ln(x)", -2, -1, 3);
        assertTrue(plot.samples().isEmpty());
        assertClose(0, plot.minY());
        assertClose(1, plot.height());
    }

    @Test
    void anImpossibleRangeOrCountIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> FunctionPlotter.plot("x", 2, 2, 5));
        assertThrows(IllegalArgumentException.class, () -> FunctionPlotter.plot("x", 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> FunctionPlotter.plot("x", Double.NaN, 1, 5));
        assertThrows(IllegalArgumentException.class,
                () -> FunctionPlotter.plot("x", 0, Double.POSITIVE_INFINITY, 5));
    }

    @Test
    void aFunctionCallIsRewrittenIntoButtonOrder() {
        assertEquals(List.of("sin"), GraphExpression.tokenize("sin(x)").subList(0, 1));
        assertEquals(List.of("2", "3", "sin"), GraphExpression.buttonsFor(
                GraphExpression.tokenize("sin(23)"), 1));
        assertEquals(List.of("4", "/", "2"), GraphExpression.buttonsFor(
                GraphExpression.tokenize("x/2"), 4));
        assertEquals(List.of("1", "+", "2", "sin"), GraphExpression.buttonsFor(
                GraphExpression.tokenize("1+sin(x)"), 2).subList(0, 4));
    }

    @Test
    void parenthesesThatGroupAreKept() {
        assertEquals(List.of("(", "1", "+", "2", ")", "*", "3"), GraphExpression.buttonsFor(
                GraphExpression.tokenize("(1+2)*3"), 0));
    }

    @Test
    void aNegativeSamplePointIsEnteredAsAKeypadWouldTypeIt() {
        assertEquals(List.of("2", ".", "5", "+/-"),
                GraphExpression.buttonsFor(GraphExpression.tokenize("x"), -2.5));
    }

    @Test
    void aFunctionWithoutAnArgumentOrBalancedParenthesesIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> GraphExpression.buttonsFor(GraphExpression.tokenize("sin()"), 1));
        assertThrows(IllegalArgumentException.class,
                () -> GraphExpression.buttonsFor(GraphExpression.tokenize("(1+2"), 1));
        assertThrows(IllegalArgumentException.class,
                () -> GraphExpression.buttonsFor(GraphExpression.tokenize("1+2)"), 1));
    }

    @Test
    void textThatIsNotAFunctionIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> GraphExpression.tokenize(""));
        assertThrows(IllegalArgumentException.class, () -> GraphExpression.tokenize("   "));
        assertThrows(IllegalArgumentException.class, () -> GraphExpression.tokenize("z+1"));
        assertFalse(GraphExpression.tokenize("2*x").isEmpty());
    }
}
