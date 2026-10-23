package dev.jcalculator.core;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RationalTest {

    @Test
    void normalisesAndCompares() {
        final Rational half = Rational.of(2, 4);
        assertEquals(BigInteger.ONE, half.numerator());
        assertEquals(BigInteger.TWO, half.denominator());
        assertTrue(Rational.of(1, 3).compareTo(Rational.of(1, 2)) < 0);
        assertEquals(Rational.of(-1, 3), Rational.of(1, -3));
    }

    @Test
    void arithmeticIsExact() {
        assertEquals(Rational.of(1, 3), Rational.of(1, 6).add(Rational.of(1, 6)));
        assertEquals(Rational.of(1, 3), Rational.one().divide(Rational.of(3)));
        assertEquals(Rational.of(1, 10), Rational.of(1, 5).multiply(Rational.of(1, 2)));
        assertEquals(Rational.of(90, 7), Rational.of(12).subtract(Rational.of(-6, 7)));
    }

    @Test
    void largeIntegerArithmeticKeepsEveryDigit() {
        final Rational value = Rational.of(new BigInteger("123456789012345678901234567890"));
        final Rational result = value.multiply(value);
        assertEquals(new BigInteger("15241578753238836750495351562536198787501905199875019052100"),
                result.numerator());
    }

    @Test
    void modulusAndRemainderDifferForNegativeValues() {
        final Rational minusSeven = Rational.of(-7);
        assertEquals(Rational.of(-3), minusSeven.remainder(Rational.of(4)));
        assertEquals(Rational.of(1), minusSeven.modulo(Rational.of(4)));
        assertEquals(Rational.of(-3), Rational.of(-7).modulo(Rational.of(-4)));
    }

    @Test
    void decimalRenderingRoundsToPrecision() {
        assertEquals("0.3333333333333333", Rational.of(1, 3).toDecimalString(16));
        assertEquals("1.5", Rational.of(3, 2).toDecimalString(16));
        assertEquals("0.36", Rational.of(36, 100).toDecimalString(16));
        assertEquals("2.3025850929940456840179914546844", RationalMath.ln(Rational.ten(), 32).toDecimalString(32));
    }

    @Test
    void divisionByZeroIsRejected() {
        assertThrows(ArithmeticException.class, () -> Rational.one().divide(Rational.zero()));
    }

    @Test
    void bigDecimalRoundTripIsExact() {
        final Rational value = Rational.of(new BigDecimal("123.456"));
        assertEquals("123.456", value.toDecimalString(16));
    }
}
