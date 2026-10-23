package dev.jcalculator.engine;

/** Calculator modes, matching the three calculator pages of the original application. */
public enum CalculatorMode {
    /** Immediate-execution calculator with a 16-digit display. */
    STANDARD(false, false, 16),
    /** Order-of-operations calculator with a 32-digit display. */
    SCIENTIFIC(true, false, 32),
    /** Integer calculator with configurable base and word width. */
    PROGRAMMER(true, true, 64);

    private final boolean precedence;
    private final boolean integerMode;
    private final int precision;

    CalculatorMode(boolean precedence, boolean integerMode, int precision) {
        this.precedence = precedence;
        this.integerMode = integerMode;
        this.precision = precision;
    }

    public boolean hasPrecedence() {
        return precedence;
    }

    public boolean isIntegerMode() {
        return integerMode;
    }

    public int precision() {
        return precision;
    }
}
