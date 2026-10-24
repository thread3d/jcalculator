package dev.jcalculator.core;

import org.junit.jupiter.api.Test;

import java.math.MathContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mathematical core the engine's functions are built on.
 *
 * <p>Assertions are the values the calculator displays, or a tolerance for the functions that have
 * no exact rational answer. The tolerance is far tighter than the last displayed digit at the
 * precisions used here, so an accuracy regression fails rather than passes.
 */
class RationalMathTest {

    private static final int PRECISION = 16;
    private static final AngleType DEGREES = AngleType.DEGREES;
    private static final AngleType RADIANS = AngleType.RADIANS;
    private static final AngleType GRADIANS = AngleType.GRADIANS;

    private static void assertClose(Rational expected, Rational actual) {
        final Rational difference = actual.subtract(expected).abs();
        assertTrue(difference.compareTo(Rational.of(1, 10_000_000L)) < 0,
                () -> "expected " + expected.toDecimalString(PRECISION)
                        + " but was " + actual.toDecimalString(PRECISION));
    }

    // ---- Roots and powers ---------------------------------------------------------------------------

    @Test
    void squareRootsAreExactWhenTheyCanBe() {
        assertEquals(Rational.of(3), RationalMath.sqrt(Rational.of(9), PRECISION));
        assertEquals(Rational.of(1, 2), RationalMath.sqrt(Rational.of(1, 4), PRECISION));
        assertEquals(Rational.zero(), RationalMath.sqrt(Rational.zero(), PRECISION));
        assertClose(Rational.of(1414213562, 1_000_000_000), RationalMath.sqrt(Rational.two(), PRECISION));
    }

    @Test
    void aSquareRootOfANegativeIsRejected() {
        assertThrows(ArithmeticException.class, () -> RationalMath.sqrt(Rational.of(-1), PRECISION));
    }

    @Test
    void integerPowersAreExact() {
        assertEquals(Rational.of(1024), RationalMath.pow(Rational.two(), Rational.of(10), PRECISION));
        assertEquals(Rational.of(1, 2), RationalMath.pow(Rational.two(), Rational.of(-1), PRECISION));
        assertClose(Rational.of(3), RationalMath.pow(Rational.of(9), Rational.of(1, 2), PRECISION));
    }

    @Test
    void powersOfZeroFollowTheExponent() {
        assertEquals(Rational.zero(), RationalMath.pow(Rational.zero(), Rational.of(1, 2), PRECISION));
        assertEquals(Rational.one(), RationalMath.pow(Rational.zero(), Rational.of(-1, 2), PRECISION));
    }

    @Test
    void aNegativeBaseNeedsAnIntegerExponent() {
        assertThrows(ArithmeticException.class,
                () -> RationalMath.pow(Rational.of(-2), Rational.of(1, 2), PRECISION));
    }

    @Test
    void rootsHandleBothSignsAndFractionalDegrees() {
        assertEquals(Rational.of(2), RationalMath.root(Rational.of(8), Rational.of(3), PRECISION));
        assertEquals(Rational.of(-2), RationalMath.root(Rational.of(-8), Rational.of(3), PRECISION));
        assertEquals(Rational.zero(), RationalMath.root(Rational.zero(), Rational.of(5), PRECISION));
        assertClose(Rational.of(4), RationalMath.root(Rational.two(), Rational.of(1, 2), PRECISION));
    }

    @Test
    void degenerateRootsAreRejected() {
        assertThrows(ArithmeticException.class,
                () -> RationalMath.root(Rational.of(9), Rational.zero(), PRECISION));
        assertThrows(ArithmeticException.class,
                () -> RationalMath.root(Rational.of(-8), Rational.of(1, 2), PRECISION));
    }

    @Test
    void integerRootsTruncateAndKeepTheSign() {
        assertEquals(Rational.of(3), RationalMath.integerRoot(Rational.of(27), 3, PRECISION));
        assertEquals(Rational.of(-2), RationalMath.integerRoot(Rational.of(-8), 3, PRECISION));
        assertEquals(Rational.zero(), RationalMath.integerRoot(Rational.zero(), 2, PRECISION));
        assertClose(Rational.of(1414213562, 1_000_000_000),
                RationalMath.integerRoot(Rational.two(), 2, PRECISION));
    }

    @Test
    void anIntegerRootOfTheWrongDegreeIsRejected() {
        assertThrows(ArithmeticException.class,
                () -> RationalMath.integerRoot(Rational.of(8), 0, PRECISION));
        assertThrows(ArithmeticException.class,
                () -> RationalMath.integerRoot(Rational.of(-8), 2, PRECISION));
    }

    // ---- Exponentials and logarithms ----------------------------------------------------------------

    @Test
    void exponentialsAndLogarithmsAreEachOthersInverse() {
        assertEquals(Rational.one(), RationalMath.exp(Rational.zero(), PRECISION));
        assertEquals(Rational.zero(), RationalMath.ln(Rational.one(), PRECISION));
        assertEquals(Rational.of(3), RationalMath.log10(Rational.thousand(), PRECISION));
        assertClose(Rational.one(), RationalMath.ln(Rational.of(RationalMath.e(PRECISION)), PRECISION));
        assertClose(Rational.of(RationalMath.e(PRECISION)), RationalMath.exp(Rational.one(), PRECISION));
    }

    @Test
    void logarithmsOfNonPositiveValuesAreRejected() {
        assertThrows(ArithmeticException.class, () -> RationalMath.ln(Rational.zero(), PRECISION));
        assertThrows(ArithmeticException.class, () -> RationalMath.ln(Rational.of(-1), PRECISION));
        assertThrows(ArithmeticException.class, () -> RationalMath.log10(Rational.zero(), PRECISION));
    }

    // ---- Trigonometry -------------------------------------------------------------------------------

    @Test
    void trigonometryUsesTheAngleUnit() {
        assertEquals(Rational.zero(), RationalMath.sin(Rational.zero(), DEGREES, PRECISION));
        assertEquals(Rational.one(), RationalMath.cos(Rational.zero(), DEGREES, PRECISION));
        assertEquals(Rational.of(1, 2), RationalMath.sin(Rational.of(30), DEGREES, PRECISION));
        assertEquals(Rational.one().negate(), RationalMath.cos(Rational.of(180), DEGREES, PRECISION));
        assertClose(Rational.one(), RationalMath.sin(Rational.of(100), GRADIANS, PRECISION));
        assertClose(Rational.one(), RationalMath.sin(
                Rational.of(RationalMath.pi(PRECISION)).divide(Rational.two()), RADIANS, PRECISION));
    }

    @Test
    void aTangentIsUndefinedWhereTheCosineVanishes() {
        assertClose(Rational.one(), RationalMath.tan(Rational.of(45), DEGREES, PRECISION));
        assertThrows(ArithmeticException.class,
                () -> RationalMath.tan(Rational.of(90), DEGREES, PRECISION));
    }

    @Test
    void inverseTrigonometryAnswersInTheAngleUnit() {
        assertClose(Rational.of(90), RationalMath.asin(Rational.one(), DEGREES, PRECISION));
        assertClose(Rational.zero(), RationalMath.asin(Rational.zero(), DEGREES, PRECISION));
        assertClose(Rational.zero(), RationalMath.acos(Rational.one(), DEGREES, PRECISION));
        assertClose(Rational.of(90), RationalMath.acos(Rational.zero(), DEGREES, PRECISION));
        assertClose(Rational.of(45), RationalMath.atan(Rational.one(), DEGREES, PRECISION));
        assertClose(Rational.of(100), RationalMath.asin(Rational.one(), GRADIANS, PRECISION));
    }

    @Test
    void inverseTrigonometryRejectsValuesOutsideItsDomain() {
        assertThrows(ArithmeticException.class,
                () -> RationalMath.asin(Rational.of(2), DEGREES, PRECISION));
        assertThrows(ArithmeticException.class,
                () -> RationalMath.acos(Rational.of(-2), DEGREES, PRECISION));
    }

    // ---- Hyperbolic functions -----------------------------------------------------------------------

    @Test
    void hyperbolicFunctionsMatchTheirDefinitionsAtZero() {
        assertEquals(Rational.zero(), RationalMath.sinh(Rational.zero(), PRECISION));
        assertEquals(Rational.one(), RationalMath.cosh(Rational.zero(), PRECISION));
        assertEquals(Rational.zero(), RationalMath.tanh(Rational.zero(), PRECISION));
        assertClose(Rational.of(15430806348L, 10_000_000_000L), RationalMath.cosh(Rational.one(), PRECISION));
    }

    @Test
    void inverseHyperbolicFunctionsMatchTheirValues() {
        assertEquals(Rational.zero(), RationalMath.asinh(Rational.zero(), PRECISION));
        assertEquals(Rational.zero(), RationalMath.acosh(Rational.one(), PRECISION));
        assertClose(Rational.zero(), RationalMath.atanh(Rational.zero(), PRECISION));
    }

    @Test
    void inverseHyperbolicFunctionsRejectValuesOutsideTheirDomain() {
        assertThrows(ArithmeticException.class,
                () -> RationalMath.acosh(Rational.of(1, 2), PRECISION));
        assertThrows(ArithmeticException.class, () -> RationalMath.atanh(Rational.one(), PRECISION));
        assertThrows(ArithmeticException.class,
                () -> RationalMath.atanh(Rational.one().negate(), PRECISION));
    }

    // ---- Factorials and degrees/minutes/seconds ----------------------------------------------------

    @Test
    void factorialsMultiplyDownToTwo() {
        assertEquals(Rational.of(120), RationalMath.factorial(Rational.of(5), PRECISION));
        assertEquals(Rational.one(), RationalMath.factorial(Rational.zero(), PRECISION));
        assertEquals(Rational.one(), RationalMath.factorial(Rational.one(), PRECISION));
    }

    @Test
    void aFactorialOutsideTheDomainIsRejected() {
        assertThrows(ArithmeticException.class, () -> RationalMath.factorial(Rational.of(-1), PRECISION));
        assertThrows(ArithmeticException.class, () -> RationalMath.factorial(Rational.of(5, 2), PRECISION));
        assertThrows(ArithmeticException.class, () -> RationalMath.factorial(Rational.of(10_001), PRECISION));
    }

    @Test
    void degreesMinutesSecondsRoundTrips() {
        assertEquals(Rational.of(13, 10), RationalMath.toDegreesMinutesSeconds(Rational.of(3, 2), PRECISION));
        assertEquals(Rational.of(3, 2), RationalMath.fromDegreesMinutesSeconds(Rational.of(13, 10), PRECISION));
        assertClose(Rational.of(-3, 2), RationalMath.fromDegreesMinutesSeconds(
                RationalMath.toDegreesMinutesSeconds(Rational.of(-3, 2), PRECISION), PRECISION));
    }

    // ---- Constants and precision --------------------------------------------------------------------

    @Test
    void theConstantsAreTheOnesTheDisplayShows() {
        assertTrue(RationalMath.pi(PRECISION).toPlainString().startsWith("3.1415926535"),
                RationalMath.pi(PRECISION).toPlainString());
        assertTrue(RationalMath.e(PRECISION).toPlainString().startsWith("2.7182818284"),
                RationalMath.e(PRECISION).toPlainString());
    }

    @Test
    void theWorkingPrecisionCarriesGuardDigits() {
        final MathContext context = RationalMath.context(4);
        assertEquals(4 + RationalMath.GUARD_DIGITS, context.getPrecision());
        assertEquals(1 + RationalMath.GUARD_DIGITS, RationalMath.context(0).getPrecision());
    }
}
