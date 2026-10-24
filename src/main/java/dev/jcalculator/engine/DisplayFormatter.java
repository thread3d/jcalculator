package dev.jcalculator.engine;

import dev.jcalculator.core.Rational;
import dev.jcalculator.format.NumberFormatter;

import java.math.BigInteger;
import java.util.Locale;

/**
 * Turns an engine value into the string the primary display shows.
 *
 * <p>The counterpart of the original's {@code scidisp.cpp}. The original keeps a number string and a
 * radix and regenerates that string from the value whenever something changes; this class is the
 * regeneration step, and {@link CalcEngine} owns when it runs.
 *
 * <p>Decimal pages round to the mode's precision and honour the scientific-format toggle. The
 * programmer page renders the word in the current base, signed when the base is decimal and unsigned
 * otherwise, which is what the original shows on its programmer page.
 */
public final class DisplayFormatter {

    private DisplayFormatter() {
    }

    /**
     * Renders a value for the primary display.
     *
     * @param value      the value to render
     * @param mode       the active page, which decides whether the value is a word or a number
     * @param radix      the programmer base (2, 8, 10 or 16); ignored outside programmer mode
     * @param width      the programmer word width; ignored outside programmer mode
     * @param precision  significant digits to round to
     * @param scientific whether the user asked for scientific (FE) notation
     * @throws CalculationException when the value cannot be shown at this precision
     */
    public static String format(Rational value, CalculatorMode mode, int radix, NumberWidth width, int precision,
            boolean scientific) {
        if (mode.isIntegerMode()) {
            if (radix == 10) {
                return BitMath.toSigned(value, width.bits()).toString();
            }
            return BitMath.toBits(value, width.bits()).toString(radix).toUpperCase(Locale.ROOT);
        }
        if (isOverflow(value)) {
            throw new CalculationException(EngineError.OVERFLOW);
        }
        return NumberFormatter.format(value, precision, scientific);
    }

    /**
     * Whether a value is beyond what the display can represent.
     *
     * <p>The engine works with exact rationals, so a value is not bounded by a fixed width the way a
     * double is. What it cannot do is render one: a numerator or denominator with more than ten
     * thousand digits is reported as overflow instead of being formatted.
     */
    public static boolean isOverflow(Rational value) {
        final Rational magnitude = value.abs();
        if (magnitude.isZero()) {
            return false;
        }
        if (magnitude.integerDigitCount() > 10_000) {
            return true;
        }
        if (magnitude.compareTo(Rational.one()) < 0) {
            final BigInteger denominator = magnitude.denominator();
            return denominator.toString().length() > 10_000;
        }
        return false;
    }
}
