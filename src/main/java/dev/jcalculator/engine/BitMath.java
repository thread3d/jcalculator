package dev.jcalculator.engine;

import dev.jcalculator.core.Rational;

import java.math.BigInteger;

/**
 * Two's-complement word arithmetic: the integer view of a value at a given word width.
 *
 * <p>The counterpart of the bit helpers the original spreads over {@code basex.cpp} and
 * {@code logic.cpp}. A value is stored as a {@link Rational} because the engine keeps one number
 * type for every page, and is interpreted as a fixed-width integer only when programmer mode asks
 * for it: {@link #toBits} reads the low {@code bits} bits, and {@link #toSigned} reinterprets the
 * top bit as a sign. Every operation masks its result back into the word, so overflow wraps exactly
 * as it does in the original.
 */
public final class BitMath {

    private BitMath() {
    }

    /** The low {@code bits} bits of the value's integer part, as an unsigned word. */
    public static BigInteger toBits(Rational value, int bits) {
        final BigInteger modulusMask = mask(bits);
        return value.truncate().numerator().and(modulusMask);
    }

    /** The value's integer part reinterpreted as a signed two's-complement word. */
    public static BigInteger toSigned(Rational value, int bits) {
        final BigInteger unsigned = toBits(value, bits);
        if (unsigned.testBit(bits - 1)) {
            return unsigned.subtract(BigInteger.ONE.shiftLeft(bits));
        }
        return unsigned;
    }

    /** A word of {@code bits} set bits: the mask that wraps an operation into the word. */
    public static BigInteger mask(int bits) {
        return BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE);
    }

    /**
     * The integer {@code degree}-th root of {@code value}, truncated toward zero.
     *
     * <p>Newton's method on integers, then a correction pass so the result is the largest integer
     * whose power does not exceed the input. Odd roots of negative numbers are allowed, even roots of
     * them are not.
     *
     * @throws CalculationException when the degree is not positive or an even root of a negative
     */
    public static BigInteger integerRoot(BigInteger value, int degree) {
        if (degree <= 0) {
            throw new CalculationException(EngineError.INVALID_RANGE);
        }
        final boolean negative = value.signum() < 0;
        if (negative && degree % 2 == 0) {
            throw new CalculationException(EngineError.DOMAIN);
        }
        final BigInteger magnitude = value.abs();
        if (magnitude.equals(BigInteger.ZERO) || magnitude.equals(BigInteger.ONE)) {
            return negative ? magnitude.negate() : magnitude;
        }
        BigInteger guess = BigInteger.ONE.shiftLeft((magnitude.bitLength() + degree - 1) / degree);
        BigInteger previous = null;
        while (!guess.equals(previous)) {
            previous = guess;
            guess = magnitude.divide(guess.pow(degree - 1)).add(guess.multiply(BigInteger.valueOf(degree - 1)))
                    .divide(BigInteger.valueOf(degree));
        }
        while (guess.pow(degree).compareTo(magnitude) > 0) {
            guess = guess.subtract(BigInteger.ONE);
        }
        while (guess.add(BigInteger.ONE).pow(degree).compareTo(magnitude) <= 0) {
            guess = guess.add(BigInteger.ONE);
        }
        return negative ? guess.negate() : guess;
    }
}
