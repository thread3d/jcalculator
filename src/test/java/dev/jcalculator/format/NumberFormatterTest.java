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

    // ---- The original's rules, measured against RatPack ----

    @Test
    void aValueBelowOneSpendsDisplayDigitsOnItsLeadingZero() {
        // 0.0174... needs one digit before the significant part, so at precision 16 the original
        // shows fifteen significant digits, not sixteen.
        assertEquals("0.0174524064372835", NumberFormatter.format(sinOfOneDegree(), 16, false));
        assertEquals("0.3333333333333333", NumberFormatter.format(Rational.of(1, 3), 16, false));
    }

    @Test
    void fixedNotationSurvivesUntilTheDigitsAndZerosOverflowTheField() {
        // 0.0043... needs two zeros and fourteen digits: fixed, with the zeros spending digits.
        assertEquals("0.0043633092847466",
                NumberFormatter.format(Rational.of(43633092847466L, 10_000_000_000_000_000L), 16, false));
        // A short value stays fixed however small it is: there is room for its zeros.
        assertEquals("0.0001", NumberFormatter.format(Rational.of(1, 10_000), 16, false));
        assertEquals("0.000001", NumberFormatter.format(Rational.of(1, 1_000_000), 16, false));
        assertEquals("0.0000017", NumberFormatter.format(Rational.of(17, 10_000_000), 16, false));
        // A full-precision value that small cannot fit, so it switches to exponent form.
        assertEquals("1.428571428571429e-7", NumberFormatter.format(Rational.of(1, 7_000_000), 16, false));
    }

    @Test
    void tooManyDigitsBeforeThePointSwitchToScientificNotation() {
        // 20! has nineteen digits before the point, which precision sixteen cannot show.
        assertEquals("2.43290200817664e+18",
                NumberFormatter.format(Rational.of(2_432_902_008_176_640_000L), 16, false));
        assertEquals("1234567890123456", NumberFormatter.format(Rational.of(1_234_567_890_123_456L), 16, false));
        assertEquals("1.234567890123457e+16",
                NumberFormatter.format(Rational.of(12_345_678_901_234_567L), 16, false));
    }

    @Test
    void theForcedScientificFormatAlwaysUsesAnExponent() {
        assertEquals("1.5e+0", NumberFormatter.format(Rational.of(3, 2), 16, true));
        assertEquals("1e+3", NumberFormatter.format(Rational.of(1000), 16, true));
        assertEquals("1.234567890123457e+16",
                NumberFormatter.format(Rational.of(12_345_678_901_234_567L), 16, true));
    }

    @Test
    void roundingIsHalfUpAndTrailingZerosGo() {
        assertEquals("0.078866399790675",
                NumberFormatter.format(Rational.of(788_663_997_906_749_5L, 100_000_000_000_000_000L), 16, false));
        assertEquals("2", NumberFormatter.format(Rational.of(2), 16, false));
        assertEquals("100", NumberFormatter.format(Rational.of(100), 16, false));
    }

    private static Rational sinOfOneDegree() {
        return dev.jcalculator.core.RationalMath.sin(Rational.one(), dev.jcalculator.core.AngleType.DEGREES, 16);
    }
}
