package dev.jcalculator.graph;

import dev.jcalculator.core.Rational;
import dev.jcalculator.engine.CalcEngine;
import dev.jcalculator.engine.CalculatorMode;
import dev.jcalculator.engine.Command;
import dev.jcalculator.harness.Tokens;

import java.util.ArrayList;
import java.util.List;

/**
 * Samples a function of {@code x} by pressing its buttons into a calculator engine.
 *
 * <p>The original's graphing calculator parses equations into its own expression tree and evaluates
 * them against a renderer. This port takes the same route the rest of the application takes: the
 * function is entered as keypad presses, one sample point at a time, and the display is read back.
 * That keeps one evaluation semantics for the whole program — a function plots with exactly the
 * arithmetic, precedence and error rules the scientific page uses.
 *
 * <p>A sample the engine refuses (dividing by zero, an out-of-domain logarithm) is left out of the
 * plot rather than being drawn as zero, so the graph shows the gap.
 */
public final class FunctionPlotter {

    /** One evaluated point. */
    public record Sample(double x, double y) {
    }

    /**
     * A sampled function.
     *
     * @param samples the points that evaluated, in increasing x order
     * @param minX    the left edge of the sampled range
     * @param maxX    the right edge of the sampled range
     * @param minY    the lowest sampled y, or 0 when nothing evaluated
     * @param maxY    the highest sampled y, or 0 when nothing evaluated
     */
    public record Plot(List<Sample> samples, double minX, double maxX, double minY, double maxY) {

        public Plot {
            samples = List.copyOf(samples);
        }

        /** The plotted height of the range, never zero so a caller can scale a drawing by it. */
        public double height() {
            final double height = maxY - minY;
            return height == 0 ? 1 : height;
        }
    }

    private FunctionPlotter() {
    }

    /**
     * Samples a function across a range.
     *
     * @param expression the function text, for example {@code x^2} or {@code sin(x)}
     * @param minX       the left edge, which must be below {@code maxX}
     * @param maxX       the right edge
     * @param samples    how many points to evaluate; at least two
     * @throws IllegalArgumentException when the expression, the range or the count is unusable
     */
    public static Plot plot(String expression, double minX, double maxX, int samples) {
        if (!Double.isFinite(minX) || !Double.isFinite(maxX) || minX >= maxX) {
            throw new IllegalArgumentException("the x range must be finite and increasing");
        }
        if (samples < 2) {
            throw new IllegalArgumentException("a plot needs at least two samples");
        }
        final List<String> tokens = GraphExpression.tokenize(expression);
        final List<Sample> points = new ArrayList<>();
        final double step = (maxX - minX) / (samples - 1);
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < samples; index++) {
            final double x = index == samples - 1 ? maxX : minX + index * step;
            final Double y = evaluate(tokens, x);
            if (y == null) {
                continue;
            }
            points.add(new Sample(x, y));
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        if (points.isEmpty()) {
            return new Plot(points, minX, maxX, 0, 0);
        }
        return new Plot(points, minX, maxX, minY, maxY);
    }

    /**
     * Evaluates the function at one point.
     *
     * @return the value, or {@code null} when the engine refuses the calculation
     */
    static Double evaluate(List<String> expressionTokens, double x) {
        final CalcEngine engine = new CalcEngine(CalculatorMode.SCIENTIFIC);
        for (String token : GraphExpression.buttonsFor(expressionTokens, x)) {
            final Command command = Tokens.commandFor(token, CalculatorMode.SCIENTIFIC);
            if (command == null) {
                throw new IllegalArgumentException("not a button: " + token);
            }
            engine.sendCommand(command);
            if (engine.isError()) {
                return null;
            }
        }
        engine.sendCommand(Command.EQUALS);
        if (engine.isError()) {
            return null;
        }
        try {
            final Rational value = Rational.parse(engine.rawDisplay());
            return value.toDouble();
        } catch (NumberFormatException | ArithmeticException failure) {
            return null;
        }
    }
}
