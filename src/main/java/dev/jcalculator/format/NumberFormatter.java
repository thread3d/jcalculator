package dev.jcalculator.format;

import dev.jcalculator.core.Rational;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Turns calculator values into the strings the display and history show.
 *
 * <p>Values are rounded to the mode's precision and rendered without exponent notation unless the
 * user asked for scientific format. Decimal output is grouped in threes; hexadecimal and binary are
 * grouped in fours and octal in threes, which is what the original calculator does.
 */
public final class NumberFormatter {

    /** How many zeros after the decimal point the original will spend before switching to exponent form. */
    private static final int MAX_ZEROS_AFTER_DECIMAL = 2;

    public static final char DECIMAL_SEPARATOR = '.';
    public static final char GROUP_SEPARATOR = ',';

    private NumberFormatter() {
    }

    /**
     * Formats a value for the primary display, the way the original's {@code NumberToString} does.
     *
     * <p>{@code precision} is the number of digits the display has room for, and the original spends
     * them in a specific order:
     *
     * <ul>
     *   <li>more than {@code precision} digits to the left of the decimal point cannot be shown, so
     *       the value switches to scientific notation;</li>
     *   <li>a value below one spends some of those digits on the leading zeros, which is why
     *       {@code sin(1°)} shows fifteen digits at precision sixteen and not sixteen;</li>
     *   <li>once more than {@link #MAX_ZEROS_AFTER_DECIMAL} zeros would be needed, the value again
     *       switches to scientific notation, so {@code 0.0001} reads {@code 1e-4} rather than
     *       {@code 0.0001}.</li>
     * </ul>
     *
     * <p>The remaining digits are rounded half up, and trailing zeros are dropped.
     *
     * @param value      the value to render
     * @param precision  the digits the display has room for
     * @param scientific whether the user forced scientific (FE) notation
     */
    public static String format(Rational value, int precision, boolean scientific) {
        if (value.isZero()) {
            return "0";
        }
        final int room = Math.max(1, precision);
        // The working value keeps two spare digits, which the original's strip-to-precision does by
        // dropping the rest. It must be truncated, not rounded: rounding here and rounding again
        // below is a double rounding, and a tail like ...849 would carry twice and show ...85.
        final BigDecimal working = value
                .toBigDecimal(new MathContext(room + 2, RoundingMode.DOWN))
                .stripTrailingZeros();
        final int exponent = working.precision() - working.scale();
        final int significant = Math.min(working.precision(), room);
        final boolean useScientific = scientific
                || exponent > room
                || (significant - exponent > room && exponent < -MAX_ZEROS_AFTER_DECIMAL);
        final int digits;
        if (useScientific) {
            digits = room;
        } else if (significant - exponent > room) {
            // The leading zeros are part of the field, so they cost displayed digits.
            digits = Math.max(1, room + exponent);
        } else {
            digits = room;
        }
        final BigDecimal rounded = roundSignificant(working, digits);
        return useScientific ? scientific(rounded) : plain(rounded);
    }

    /** Renders a value in scientific notation with at most {@code precision} significant digits. */
    public static String formatScientific(Rational value, int precision) {
        if (value.isZero()) {
            return "0";
        }
        final int room = Math.max(1, precision);
        final BigDecimal working = value
                .toBigDecimal(new MathContext(room + 2, RoundingMode.DOWN))
                .stripTrailingZeros();
        return scientific(roundSignificant(working, room));
    }

    private static BigDecimal roundSignificant(BigDecimal value, int digits) {
        return value.round(new MathContext(Math.max(1, digits), RoundingMode.HALF_UP))
                .stripTrailingZeros();
    }

    private static String plain(BigDecimal value) {
        return trimDecimal(value.toPlainString());
    }

    private static String scientific(BigDecimal value) {
        final int exponent = value.precision() - value.scale() - 1;
        final BigDecimal mantissa = value.movePointLeft(exponent).stripTrailingZeros();
        final String sign = exponent >= 0 ? "+" : "";
        return trimDecimal(mantissa.toPlainString()) + "e" + sign + exponent;
    }

    private static String trimDecimal(String text) {
        if (text.indexOf('.') < 0) {
            return text;
        }
        String trimmed = text;
        while (trimmed.endsWith("0")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.endsWith(".")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.equals("-0") ? "0" : trimmed;
    }

    /** Groups the integer part of a formatted number according to the radix. */
    public static String group(String display, int radix) {
        if (display == null || display.isEmpty()) {
            return display;
        }
        final char delimiter = radix == 10 ? GROUP_SEPARATOR : ' ';
        final int groupSize = switch (radix) {
            case 10, 8 -> 3;
            case 16, 2 -> 4;
            default -> 0;
        };
        if (groupSize == 0) {
            return display;
        }
        final boolean negative = display.charAt(0) == '-';
        final String body = negative ? display.substring(1) : display;
        final int decimal = body.indexOf(DECIMAL_SEPARATOR);
        final int exponent = body.indexOf('e');
        int end = body.length();
        if (decimal >= 0) {
            end = decimal;
        } else if (exponent >= 0) {
            end = exponent;
        }
        final String integerPart = body.substring(0, end);
        final String suffix = body.substring(end);
        final StringBuilder grouped = new StringBuilder();
        int count = 0;
        for (int i = integerPart.length() - 1; i >= 0; i--) {
            grouped.append(integerPart.charAt(i));
            count++;
            if (count % groupSize == 0 && i > 0) {
                grouped.append(delimiter);
            }
        }
        grouped.reverse();
        return (negative ? "-" : "") + grouped + suffix;
    }

    /**
     * Removes trailing zeros and a trailing decimal point from a fraction, leaving integers alone.
     * Exposed because unit conversion results are trimmed the same way.
     */
    public static String trimTrailingZeros(String number) {
        if (number.indexOf('.') < 0) {
            return number;
        }
        int end = number.length();
        while (end > 0 && number.charAt(end - 1) == '0') {
            end--;
        }
        if (end > 0 && number.charAt(end - 1) == '.') {
            end--;
        }
        return number.substring(0, end);
    }

    /** Number of significant digits in a decimal string, ignoring sign and decimal point. */
    public static int significantDigits(String number) {
        final String trimmed = trimTrailingZeros(number);
        int digits = 0;
        boolean leading = true;
        for (int i = 0; i < trimmed.length(); i++) {
            final char c = trimmed.charAt(i);
            if (c == '-' || c == '.') {
                continue;
            }
            if (leading && c == '0') {
                continue;
            }
            leading = false;
            digits++;
        }
        return digits;
    }
}
