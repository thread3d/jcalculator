package dev.jcalculator.format;

import dev.jcalculator.core.Rational;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How a value becomes a display string: precision, scientific notation and digit grouping. */
class NumberFormatterTest {

    private static final int PRECISION = 16;

    @Test
    void zeroIsJustZero() {
        assertEquals("0", NumberFormatter.format(Rational.zero(), PRECISION, false));
    }

    @Test
    void aValueIsRoundedToTheRequestedPrecision() {
        assertEquals("1.5", NumberFormatter.format(Rational.of(3, 2), PRECISION, false));
        assertEquals("-2.75", NumberFormatter.format(Rational.of(-11, 4), PRECISION, false));
        assertTrue(NumberFormatter.format(Rational.of(1, 3), PRECISION, false).startsWith("0.3333333333"));
    }

    @Test
    void scientificNotationKeepsOneDigitBeforeThePoint() {
        assertEquals("1.5e+3", NumberFormatter.format(Rational.of(1500), PRECISION, true));
        assertEquals("2e-3", NumberFormatter.format(Rational.of(2, 1000), PRECISION, true));
    }

    @Test
    void decimalDigitsAreGroupedInThrees() {
        assertEquals("1,234,567", NumberFormatter.group("1234567", 10));
        assertEquals("-1,234,567", NumberFormatter.group("-1234567", 10));
        assertEquals("1,234.5", NumberFormatter.group("1234.5", 10));
        assertEquals("123", NumberFormatter.group("123", 10));
    }

    @Test
    void otherBasesAreGroupedForTheirWidth() {
        assertEquals("FFFF 0000", NumberFormatter.group("FFFF0000", 16));
        assertEquals("1010 1010", NumberFormatter.group("10101010", 2));
        assertEquals("123 456", NumberFormatter.group("123456", 8));
        assertEquals("123456", NumberFormatter.group("123456", 3));
    }

    @Test
    void anExponentIsNotGrouped() {
        assertEquals("1.5e+10", NumberFormatter.group("1.5e+10", 10));
        assertEquals("1200e+10", NumberFormatter.group("1200e+10", 10).replace(",", ""));
    }

    @Test
    void nothingToGroupIsHandedBackUnchanged() {
        assertNull(NumberFormatter.group(null, 10));
        assertEquals("", NumberFormatter.group("", 10));
    }

    @Test
    void trailingZerosAreTrimmedFromFractionsOnly() {
        assertEquals("1.5", NumberFormatter.trimTrailingZeros("1.500"));
        assertEquals("2", NumberFormatter.trimTrailingZeros("2.000"));
        assertEquals("100", NumberFormatter.trimTrailingZeros("100"));
        assertEquals("0", NumberFormatter.trimTrailingZeros("0.000"));
    }

    @Test
    void significantDigitsIgnoreSignPointAndLeadingZeros() {
        assertEquals(4, NumberFormatter.significantDigits("1234"));
        assertEquals(2, NumberFormatter.significantDigits("0.0012"));
        assertEquals(3, NumberFormatter.significantDigits("-12.30"));
        assertEquals(0, NumberFormatter.significantDigits("0.0"));
    }
}
