package dev.jcalculator.harness;

import dev.jcalculator.core.AngleType;
import dev.jcalculator.core.Rational;
import dev.jcalculator.engine.CalculatorMode;
import dev.jcalculator.engine.HistoryEntry;
import dev.jcalculator.engine.NumberWidth;

import java.util.List;

/**
 * A complete, immutable snapshot of calculator state.
 *
 * <p>Every harness surface — the JSON protocol, the MCP tools and the window automation — reports
 * the same fields, so a consumer can parse one shape and rely on it.
 *
 * @param mode         the active calculator page
 * @param display      the primary display, with digit grouping
 * @param rawDisplay   the primary display without grouping
 * @param expression   the expression line
 * @param error        whether the calculator is in its error state
 * @param errorMessage the error text, or {@code null}
 * @param radix        the programmer base (2, 8, 10 or 16)
 * @param width        the programmer word width
 * @param angleType    the trigonometric angle unit
 * @param memory       memorized values, newest first
 * @param history      completed calculations for the active mode, newest first
 */
public record CalculatorState(
        CalculatorMode mode,
        String display,
        String rawDisplay,
        String expression,
        boolean error,
        String errorMessage,
        int radix,
        NumberWidth width,
        AngleType angleType,
        List<Rational> memory,
        List<HistoryEntry> history) {

    public CalculatorState {
        memory = List.copyOf(memory);
        history = List.copyOf(history);
    }
}
