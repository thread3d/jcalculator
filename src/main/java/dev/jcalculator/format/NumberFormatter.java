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

    public static final char DECIMAL_SEPARATOR = '.';
    public static final char GROUP_SEPARATOR = ',';

    private NumberFormatter() {
    }

    /**
     * Formats a value for the primary display.
     *
     * @param value      the value to render
     * @param precision  significant digits to round to
     * @param scientific whether the user toggled scientific (FE) notation
     */
    public static String format(Rational value, int precision, boolean scientific) {
        if (value.isZero()) {
            return "0";
        }
        if (scientific) {
            return formatScientific(value, precision);
        }
        return value.toDecimalString(precision);
    }

    public static String formatScientific(Rational value, int precision) {
        final MathContext context = new MathContext(precision, RoundingMode.HALF_UP);
        final BigDecimal rounded = value.toBigDecimal(context).stripTrailingZeros();
        final int exponent = rounded.precision() - rounded.scale() - 1;
        final BigDecimal mantissa = rounded.movePointLeft(exponent).stripTrailingZeros();
        final String sign = exponent >= 0 ? "+" : "";
        return mantissa.toPlainString() + "e" + sign + exponent;
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
