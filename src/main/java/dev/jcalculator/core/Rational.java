package dev.jcalculator.core;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Exact rational number, the Java counterpart of the calculator's RatPack value type.
 *
 * <p>Every value is kept as a normalised fraction {@code numerator / denominator} with a positive
 * denominator, so addition, subtraction, multiplication and division never lose precision. That is
 * the property the original calculator advertises for its basic arithmetic and the reason this type
 * exists instead of {@code double}.
 */
public final class Rational implements Comparable<Rational> {

    /**
     * Common values as factory methods rather than shared static instances. Rational is immutable,
     * so either form is safe; returning a fresh value keeps the class free of global state.
     */
    public static Rational zero() {
        return new Rational(BigInteger.ZERO, BigInteger.ONE);
    }

    public static Rational one() {
        return new Rational(BigInteger.ONE, BigInteger.ONE);
    }

    public static Rational two() {
        return new Rational(BigInteger.TWO, BigInteger.ONE);
    }

    public static Rational ten() {
        return new Rational(BigInteger.TEN, BigInteger.ONE);
    }

    public static Rational hundred() {
        return new Rational(BigInteger.valueOf(100), BigInteger.ONE);
    }

    public static Rational thousand() {
        return new Rational(BigInteger.valueOf(1000), BigInteger.ONE);
    }

    private final BigInteger numerator;
    private final BigInteger denominator;

    private Rational(BigInteger numerator, BigInteger denominator) {
        if (denominator.signum() == 0) {
            throw new ArithmeticException("division by zero");
        }
        if (denominator.signum() < 0) {
            numerator = numerator.negate();
            denominator = denominator.negate();
        }
        final BigInteger gcd = numerator.gcd(denominator);
        if (!gcd.equals(BigInteger.ONE)) {
            numerator = numerator.divide(gcd);
            denominator = denominator.divide(gcd);
        }
        this.numerator = numerator;
        this.denominator = denominator;
    }

    public static Rational of(long numerator, long denominator) {
        return new Rational(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
    }

    public static Rational of(BigInteger numerator, BigInteger denominator) {
        return new Rational(numerator, denominator);
    }

    public static Rational of(BigInteger value) {
        return new Rational(value, BigInteger.ONE);
    }

    public static Rational of(long value) {
        return new Rational(BigInteger.valueOf(value), BigInteger.ONE);
    }

    public static Rational of(BigDecimal value) {
        if (value.scale() >= 0) {
            return new Rational(value.unscaledValue(), BigInteger.TEN.pow(value.scale()));
        }
        return new Rational(value.unscaledValue().multiply(BigInteger.TEN.pow(-value.scale())), BigInteger.ONE);
    }

    /**
     * Parses a decimal literal, including an optional sign, fraction and {@code e}/{@code ^}
     * exponent. Returns {@code null} when the text is not a number, which is how the engine treats
     * an unparseable input string.
     */
    public static Rational parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return of(new BigDecimal(text.trim().replace('^', 'e')));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public BigInteger numerator() {
        return numerator;
    }

    public BigInteger denominator() {
        return denominator;
    }

    public int signum() {
        return numerator.signum();
    }

    public boolean isZero() {
        return numerator.signum() == 0;
    }

    public boolean isInteger() {
        return denominator.equals(BigInteger.ONE);
    }

    public Rational add(Rational other) {
        return new Rational(
                numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)),
                denominator.multiply(other.denominator));
    }

    public Rational subtract(Rational other) {
        return add(other.negate());
    }

    public Rational multiply(Rational other) {
        return new Rational(numerator.multiply(other.numerator), denominator.multiply(other.denominator));
    }

    public Rational divide(Rational other) {
        if (other.isZero()) {
            throw new ArithmeticException("division by zero");
        }
        return new Rational(numerator.multiply(other.denominator), denominator.multiply(other.numerator));
    }

    public Rational negate() {
        return new Rational(numerator.negate(), denominator);
    }

    public Rational abs() {
        return numerator.signum() >= 0 ? this : negate();
    }

    /** Exact integer power for a non-negative exponent. */
    public Rational pow(int exponent) {
        if (exponent < 0) {
            return one().divide(pow(-exponent));
        }
        return new Rational(numerator.pow(exponent), denominator.pow(exponent));
    }

    /** Integer division truncated toward zero. */
    public Rational truncate() {
        if (isInteger()) {
            return this;
        }
        // BigInteger division truncates toward zero.
        return of(numerator.divide(denominator));
    }

    public Rational floor() {
        final BigInteger[] division = numerator.divideAndRemainder(denominator);
        BigInteger quotient = division[0];
        if (division[1].signum() < 0) {
            quotient = quotient.subtract(BigInteger.ONE);
        }
        return of(quotient);
    }

    public Rational ceiling() {
        final BigInteger[] division = numerator.divideAndRemainder(denominator);
        BigInteger quotient = division[0];
        if (division[1].signum() > 0) {
            quotient = quotient.add(BigInteger.ONE);
        }
        return of(quotient);
    }

    /** Remainder after truncating division; the programmer-mode {@code MOD} operator. */
    public Rational remainder(Rational divisor) {
        if (divisor.isZero()) {
            throw new ArithmeticException("division by zero");
        }
        final Rational quotient = divide(divisor).truncate();
        return subtract(quotient.multiply(divisor));
    }

    /** Mathematical modulus: the result carries the divisor's sign. */
    public Rational modulo(Rational divisor) {
        if (divisor.isZero()) {
            throw new ArithmeticException("division by zero");
        }
        final Rational quotient = divide(divisor).floor();
        return subtract(quotient.multiply(divisor));
    }

    /** The value rounded to {@code digits} significant digits. */
    public BigDecimal round(int digits, RoundingMode mode) {
        if (digits < 1) {
            throw new IllegalArgumentException("digits must be positive");
        }
        final BigDecimal decimal = toFiniteDecimal(digits + 2, RoundingMode.HALF_EVEN);
        return decimal.round(new MathContext(digits, mode)).stripTrailingZeros();
    }

    /**
     * A decimal approximation with at least {@code digits} significant digits. A fraction whose
     * denominator has only factors 2 and 5 is expanded exactly; every other fraction is divided at
     * the requested precision, which is what makes {@code 1/3} display as a rounded decimal.
     */
    private BigDecimal toFiniteDecimal(int digits, RoundingMode mode) {
        int twos = 0;
        int fives = 0;
        BigInteger remaining = denominator;
        while (remaining.mod(BigInteger.TWO).signum() == 0) {
            remaining = remaining.divide(BigInteger.TWO);
            twos++;
        }
        while (remaining.mod(BigInteger.valueOf(5)).signum() == 0) {
            remaining = remaining.divide(BigInteger.valueOf(5));
            fives++;
        }
        if (remaining.equals(BigInteger.ONE)) {
            final int scale = Math.max(twos, fives);
            final BigInteger scaled = numerator.multiply(BigInteger.TEN.pow(scale)).divide(denominator);
            return new BigDecimal(scaled, scale);
        }
        return new BigDecimal(numerator).divide(new BigDecimal(denominator), new MathContext(digits, mode));
    }

    /** Decimal expansion rounded to {@code digits} significant digits, without grouping. */
    public String toDecimalString(int digits) {
        final BigDecimal rounded = round(digits, RoundingMode.HALF_UP);
        if (rounded.signum() == 0) {
            return "0";
        }
        String plain = rounded.toPlainString();
        if (plain.contains(".")) {
            plain = plain.replaceAll("0+$", "");
            if (plain.endsWith(".")) {
                plain = plain.substring(0, plain.length() - 1);
            }
        }
        return plain;
    }

    /** Exact integer rendering in the radix; the value must be an integer. */
    public String toRadixString(int radix) {
        if (!isInteger()) {
            throw new ArithmeticException("value is not an integer");
        }
        return numerator.abs().toString(radix).toUpperCase();
    }

    public BigDecimal toBigDecimal(MathContext context) {
        return new BigDecimal(numerator).divide(new BigDecimal(denominator), context);
    }

    public double toDouble() {
        return numerator.doubleValue() / denominator.doubleValue();
    }

    /** Number of decimal digits in the integer part, at least one. */
    public int integerDigitCount() {
        final Rational magnitude = abs();
        if (magnitude.isZero()) {
            return 1;
        }
        if (magnitude.compareTo(one()) < 0) {
            return 0;
        }
        final BigInteger truncated = magnitude.truncate().numerator();
        return truncated.toString().length();
    }

    @Override
    public int compareTo(Rational other) {
        return numerator.multiply(other.denominator).compareTo(other.numerator.multiply(denominator));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Rational rational
                && numerator.equals(rational.numerator)
                && denominator.equals(rational.denominator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(numerator, denominator);
    }

    /** Debug form; the calculator's display formatting lives in {@code NumberFormatter}. */
    @Override
    public String toString() {
        return isInteger() ? numerator.toString() : numerator + "/" + denominator;
    }
}
