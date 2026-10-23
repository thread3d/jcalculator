package dev.jcalculator.engine;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The digits currently being typed, before they become a rational value.
 *
 * <p>Kept as text because the calculator must show exactly what was typed ({@code 3.} while the
 * fraction is still empty) and because the integer modes impose digit and range limits that are
 * checked at input time rather than after conversion. This mirrors the original {@code CalcInput}.
 */
public final class CalcInput {

    private static final int MAX_DIGIT_COUNT = 32;
    private static final int MAX_EXPONENT_DIGITS = 4;

    private final StringBuilder base = new StringBuilder();
    private boolean baseNegative;
    private int decimalPointIndex;
    private boolean hasDecimal;

    private final StringBuilder exponent = new StringBuilder();
    private boolean exponentNegative;
    private boolean hasExponent;

    private char decimalSymbol = '.';

    public void clear() {
        base.setLength(0);
        baseNegative = false;
        decimalPointIndex = 0;
        hasDecimal = false;
        exponent.setLength(0);
        exponentNegative = false;
        hasExponent = false;
    }

    public boolean isEmpty() {
        return base.isEmpty() && !hasExponent && exponent.isEmpty() && !hasDecimal;
    }

    public boolean hasDecimalPoint() {
        return hasDecimal;
    }

    public boolean isNegative() {
        return baseNegative;
    }

    /**
     * Toggles the sign of the digit sequence being typed.
     *
     * @return {@code false} when the positive counterpart would not fit the integer mode's range
     */
    public boolean tryToggleSign(boolean integerMode, String maxValueText) {
        if (base.isEmpty()) {
            baseNegative = false;
            exponentNegative = false;
        } else if (hasExponent) {
            exponentNegative = !exponentNegative;
        } else {
            if (integerMode && baseNegative) {
                if (base.length() >= maxValueText.length() && base.charAt(base.length() - 1) > maxValueText.charAt(maxValueText.length() - 1)) {
                    return false;
                }
            }
            baseNegative = !baseNegative;
        }
        return true;
    }

    /**
     * Appends a digit.
     *
     * @param value         the digit value (0-15)
     * @param radix         current radix
     * @param integerMode   whether the programmer mode's width limits apply
     * @param maxValueText  largest positive value for the current width, in decimal
     * @param wordBitWidth  width in bits for the integer modes
     * @param maxDigits     digit budget for this mode
     * @return {@code false} when the digit was refused (max digits or out of range)
     */
    public boolean tryAddDigit(int value, int radix, boolean integerMode, String maxValueText,
                               int wordBitWidth, int maxDigits) {
        final char digit = Character.toUpperCase(Character.forDigit(value, radix));
        StringBuilder section;
        int maxCount;
        if (hasExponent) {
            section = exponent;
            maxCount = MAX_EXPONENT_DIGITS;
        } else {
            section = base;
            maxCount = maxDigits;
            if (hasDecimal) {
                maxCount++;
            }
            if (!section.isEmpty() && section.charAt(0) == '0') {
                maxCount++;
            }
        }

        if (section.isEmpty() && value == 0) {
            return true;
        }

        if (section.length() < maxCount) {
            section.append(digit);
            return true;
        }

        if (integerMode && section.length() == maxCount && !hasExponent) {
            boolean allowExtraDigit = false;
            if (radix == 8) {
                switch (wordBitWidth % 3) {
                    case 1 -> allowExtraDigit = section.charAt(0) == '1';
                    case 2 -> allowExtraDigit = section.charAt(0) <= '3';
                    default -> allowExtraDigit = false;
                }
            } else if (radix == 10) {
                if (section.length() < maxValueText.length()) {
                    final int comparison = section.toString().compareTo(maxValueText.substring(0, section.length()));
                    if (comparison < 0) {
                        allowExtraDigit = true;
                    } else if (comparison == 0) {
                        final char limit = maxValueText.charAt(section.length());
                        if (digit <= limit) {
                            allowExtraDigit = true;
                        } else if (baseNegative && digit <= limit + 1) {
                            allowExtraDigit = true;
                        }
                    }
                }
            }
            if (allowExtraDigit) {
                section.append(digit);
                return true;
            }
        }
        return false;
    }

    public boolean tryAddDecimalPoint() {
        if (hasDecimal || hasExponent) {
            return false;
        }
        if (base.isEmpty()) {
            base.append('0');
        }
        decimalPointIndex = base.length();
        base.append(decimalSymbol);
        hasDecimal = true;
        return true;
    }

    public boolean tryBeginExponent() {
        tryAddDecimalPoint();
        if (hasExponent) {
            return false;
        }
        hasExponent = true;
        return true;
    }

    public void backspace() {
        if (hasExponent) {
            if (!exponent.isEmpty()) {
                exponent.deleteCharAt(exponent.length() - 1);
            } else {
                hasExponent = false;
            }
        } else {
            if (!base.isEmpty()) {
                base.deleteCharAt(base.length() - 1);
                if (base.length() == 1 && base.charAt(0) == '0') {
                    base.setLength(0);
                }
            }
            if (base.length() <= decimalPointIndex) {
                hasDecimal = false;
                decimalPointIndex = 0;
            }
            if (base.isEmpty()) {
                baseNegative = false;
            }
        }
    }

    public void setDecimalSymbol(char symbol) {
        if (decimalSymbol != symbol) {
            decimalSymbol = symbol;
            if (hasDecimal) {
                base.setCharAt(decimalPointIndex, symbol);
            }
        }
    }

    /** The text shown while typing, including a pending exponent. */
    public String toDisplayString(int radix) {
        final StringBuilder result = new StringBuilder();
        if (baseNegative) {
            result.append('-');
        }
        if (base.isEmpty()) {
            result.append('0');
        } else {
            result.append(base);
        }
        if (hasExponent) {
            if (!hasDecimal) {
                result.append(decimalSymbol);
            }
            result.append(radix == 10 ? 'e' : '^');
            result.append(exponentNegative ? '-' : '+');
            result.append(exponent.isEmpty() ? "0" : exponent);
        }
        return result.toString();
    }

    /**
     * Converts the typed text into an exact rational.
     *
     * @return {@code null} when the text cannot be interpreted in this radix
     */
    public RationalValue toRationalValue(int radix) {
        final String digits = base.toString();
        final int point = digits.indexOf(decimalSymbol);
        final String whole = point >= 0 ? digits.substring(0, point) : digits;
        final String fraction = point >= 0 ? digits.substring(point + 1) : "";
        if (whole.isEmpty() && fraction.isEmpty()) {
            return new RationalValue(java.math.BigInteger.ZERO, java.math.BigInteger.ONE, false);
        }
        java.math.BigInteger numerator = parseDigits(whole, radix);
        java.math.BigInteger denominator = java.math.BigInteger.ONE;
        if (!fraction.isEmpty()) {
            final java.math.BigInteger fractionValue = parseDigits(fraction, radix);
            final java.math.BigInteger scale = java.math.BigInteger.valueOf(radix).pow(fraction.length());
            numerator = numerator.multiply(scale).add(fractionValue);
            denominator = scale;
        }
        if (hasExponent && !exponent.isEmpty()) {
            final int power = Integer.parseInt(exponent.toString());
            if (exponentNegative) {
                denominator = denominator.multiply(java.math.BigInteger.valueOf(radix).pow(power));
            } else {
                numerator = numerator.multiply(java.math.BigInteger.valueOf(radix).pow(power));
            }
        }
        if (baseNegative) {
            numerator = numerator.negate();
        }
        return new RationalValue(numerator, denominator, true);
    }

    private static java.math.BigInteger parseDigits(String text, int radix) {
        java.math.BigInteger value = java.math.BigInteger.ZERO;
        final java.math.BigInteger baseValue = java.math.BigInteger.valueOf(radix);
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            final int digit = Character.digit(c, radix);
            if (digit < 0) {
                return java.math.BigInteger.ZERO;
            }
            value = value.multiply(baseValue).add(java.math.BigInteger.valueOf(digit));
        }
        return value;
    }

    /** Small carrier for an unnormalised fraction; the engine normalises it into a Rational. */
    public record RationalValue(java.math.BigInteger numerator, java.math.BigInteger denominator, boolean valid) {
    }
}
