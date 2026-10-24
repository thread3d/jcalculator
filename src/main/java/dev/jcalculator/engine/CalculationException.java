package dev.jcalculator.engine;

import java.util.Objects;

/**
 * Carries an {@link EngineError} out of a calculation that cannot produce a value.
 *
 * <p>The original reports a failure by raising a {@code ResultCode} and letting the engine catch it
 * at the command boundary, which is why arithmetic helpers never paint the display themselves. The
 * Java port keeps that separation: {@link UnaryFunctions} and {@link DisplayFormatter} are pure and
 * throw this, and {@link CalcEngine} is the only place that turns it into display state.
 */
public final class CalculationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final EngineError error;

    /**
     * Creates the exception for one engine failure.
     *
     * @param error the failure kind, which also supplies the exception message
     */
    public CalculationException(EngineError error) {
        super(Objects.requireNonNull(error, "error").message());
        this.error = error;
    }

    /** The failure kind the engine should report. */
    public EngineError error() {
        return error;
    }
}
