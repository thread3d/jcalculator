package dev.jcalculator.harness;

import dev.jcalculator.convert.UnitCategory;
import dev.jcalculator.convert.UnitConverter;
import dev.jcalculator.date.DateCalculationEngine;
import dev.jcalculator.date.DateDifference;
import dev.jcalculator.date.DateUnit;
import dev.jcalculator.engine.CalculatorMode;
import dev.jcalculator.engine.Command;
import dev.jcalculator.manager.CalculatorManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A scriptable, side-effect-free calculator session.
 *
 * <p>This is the surface every harness uses. Unlike the Swing window and the interactive text
 * interface it never writes to standard output, never blocks and never calls {@code System.exit};
 * it reports state through {@link #state()} and failures through exceptions. Two sessions built with
 * the same seed and driven with the same tokens produce identical state, so a harness can record a
 * run and replay it.
 *
 * <pre>
 *   CalculatorSession session = new CalculatorSession(CalculatorMode.STANDARD, 7L);
 *   session.pressAll(List.of("2", "+", "3", "*", "4", "="));
 *   session.state().display();   // "20"
 * </pre>
 *
 * <p>Tokens are the same button names the text interface accepts; see {@link Tokens}.
 */
public final class CalculatorSession {

    private final CalculatorManager manager;
    private final UnitConverter converter = new UnitConverter();
    private final DateCalculationEngine dates = new DateCalculationEngine();
    private final long randomSeed;

    /** Creates a standard-mode session with seed {@code 0}. */
    public CalculatorSession() {
        this(CalculatorMode.STANDARD, 0L);
    }

    /** Creates a session in the given mode with seed {@code 0}. */
    public CalculatorSession(CalculatorMode mode) {
        this(mode, 0L);
    }

    /**
     * Creates a session in the given mode.
     *
     * @param mode       the calculator page to start on
     * @param randomSeed the seed for the random key; the same seed yields the same sequence
     */
    public CalculatorSession(CalculatorMode mode, long randomSeed) {
        this.manager = new CalculatorManager(Objects.requireNonNull(mode, "mode"), randomSeed);
        this.randomSeed = randomSeed;
    }

    // ---- Identity --------------------------------------------------------------------------------

    /** The underlying manager, for callers that need the model directly. */
    public CalculatorManager manager() {
        return manager;
    }

    /** The seed this session's random key was created with. */
    public long randomSeed() {
        return randomSeed;
    }

    // ---- Commands --------------------------------------------------------------------------------

    /** Presses one keypad command. */
    public void press(Command command) {
        manager.sendCommand(Objects.requireNonNull(command, "command"));
    }

    /**
     * Presses one button by its token name.
     *
     * @throws UnknownTokenException when the token is not a button
     */
    public void press(String token) {
        press(Tokens.require(token, manager.mode()));
    }

    /**
     * Presses a sequence of tokens in order.
     *
     * @throws UnknownTokenException when any token is not a button; earlier tokens have already been
     *                               applied, exactly as a user's earlier presses would have been
     */
    public void pressAll(Iterable<String> tokens) {
        for (String token : tokens) {
            press(token);
        }
    }

    /** Presses a whitespace-separated token sequence such as {@code "2 + 3 * 4 ="}. */
    public void pressExpression(String expression) {
        pressAll(split(expression));
    }

    /** Switches calculator page. */
    public void setMode(CalculatorMode mode) {
        manager.setMode(Objects.requireNonNull(mode, "mode"));
    }

    /** Clears the display, memory and every mode's history. */
    public void reset() {
        manager.reset();
    }

    // ---- State -----------------------------------------------------------------------------------

    /** Captures the full state. The result is a copy that later presses cannot change. */
    public CalculatorState state() {
        return new CalculatorState(
                manager.mode(),
                manager.primaryDisplay(),
                manager.rawDisplay(),
                manager.expression(),
                manager.isError(),
                manager.errorMessage(),
                manager.parenthesisCount(),
                manager.lastSignal(),
                manager.engine().radix(),
                manager.engine().width(),
                manager.engine().angleType(),
                manager.memorizedNumbers(),
                manager.history());
    }

    // ---- Converter and date services -------------------------------------------------------------

    /** Converts a value and renders the result the way the converter page shows it. */
    public String convert(UnitCategory category, String from, String to, String value) {
        return converter.convertAndFormat(
                Objects.requireNonNull(category, "category"),
                Objects.requireNonNull(from, "from"),
                Objects.requireNonNull(to, "to"),
                Objects.requireNonNull(value, "value"));
    }

    /** Converts a value, resolving the category by name (case-insensitive). */
    public String convert(String categoryName, String from, String to, String value) {
        return convert(categoryFor(categoryName), from, to, value);
    }

    /**
     * Describes the calendar difference between two dates.
     *
     * @return the breakdown, or {@code null} when the difference is not representable
     */
    public String dateDifference(LocalDate from, LocalDate to) {
        final DateDifference difference = dates.tryGetDateDifference(from, to, DateUnit.ALL);
        return difference == null ? null : describe(difference);
    }

    /**
     * Adds or subtracts a duration, applying years then months then days.
     *
     * @return the resulting date, or {@code null} when it falls outside the supported range
     */
    public LocalDate dateAdd(LocalDate start, int years, int months, int days, boolean subtract) {
        final DateDifference duration = new DateDifference(years, months, 0, days);
        return subtract ? dates.subtractDuration(start, duration) : dates.addDuration(start, duration);
    }

    /** Resolves a converter category by name. */
    public static UnitCategory categoryFor(String categoryName) {
        try {
            return UnitCategory.valueOf(categoryName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unknown conversion category: " + categoryName, exception);
        }
    }

    /** Renders a date difference the way the date page reads. */
    public static String describe(DateDifference difference) {
        final List<String> parts = new ArrayList<>();
        if (difference.year() != 0) {
            parts.add(difference.year() + (difference.year() == 1 ? " year" : " years"));
        }
        if (difference.month() != 0) {
            parts.add(difference.month() + (difference.month() == 1 ? " month" : " months"));
        }
        if (difference.week() != 0) {
            parts.add(difference.week() + (difference.week() == 1 ? " week" : " weeks"));
        }
        if (difference.day() != 0 || parts.isEmpty()) {
            parts.add(difference.day() + (difference.day() == 1 ? " day" : " days"));
        }
        return String.join(" ", parts);
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
