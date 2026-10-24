package dev.jcalculator.engine;

import java.util.Locale;

/**
 * The failures the engine can report, named as the original's {@code CalcErr.h} codes are.
 *
 * <p>The original ratpack layers raise a 32-bit {@code ResultCode} per failure
 * ({@code CALC_E_DIVIDEBYZERO}, {@code CALC_E_DOMAIN}, {@code CALC_E_OVERFLOW}, …) and the
 * presentation layer turns the code into a localized resource string. This enum is the same split:
 * the code identifies the failure, {@link #message()} is the text the display shows.
 *
 * <p>The engine is in an error state from the moment one of these is raised until the user clears,
 * so a caller can read {@link #code()} to tell "cannot divide by zero" from "invalid input" without
 * matching on display text.
 */
public enum EngineError {

    /** The divisor was zero and the dividend was not: {@code 5 ÷ 0}. */
    DIVIDE_BY_ZERO("Cannot divide by zero"),

    /** Both sides of a quotient were zero: {@code 0 ÷ 0}; the result is indeterminate. */
    UNDEFINED("Result is undefined"),

    /** The result cannot be represented at the mode's precision. */
    OVERFLOW("Overflow"),

    /** The input is outside the function's domain: {@code √-1}, {@code ln 0}, {@code asin 2}. */
    DOMAIN("Invalid input"),

    /** The input is a value the operation cannot accept at all: a shift wider than the word. */
    INVALID_RANGE("Invalid input");

    private final String message;

    EngineError(String message) {
        this.message = message;
    }

    /** The text the primary display shows while this failure is in force. */
    public String message() {
        return message;
    }

    /**
     * The stable machine-readable name, shaped like the original's error codes.
     *
     * <p>Lower case and hyphen-free so it can be used as a JSON value or a resource key.
     */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
