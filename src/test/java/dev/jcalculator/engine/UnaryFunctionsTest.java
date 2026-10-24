package dev.jcalculator.engine;

import dev.jcalculator.core.AngleType;
import dev.jcalculator.core.Rational;
import dev.jcalculator.core.RationalMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The unary-function dispatch: every function the pages offer, and the flag that makes a function
 * mean its inverse.
 *
 * <p>The routing assertions compare against {@link RationalMath} directly, because routing is what
 * this class decides; the mathematical accuracy of the target functions is covered by
 * {@code RationalMathTest}. The absolute values below are the ones the calculator displays.
 */
class UnaryFunctionsTest {

    private static final int PRECISION = 16;
    private static final AngleType DEGREES = AngleType.DEGREES;
    private static final AngleType RADIANS = AngleType.RADIANS;
    private static final AngleType GRADIANS = AngleType.GRADIANS;

    private static Rational apply(Command command, Rational value) {
        return UnaryFunctions.apply(command, value, false, DEGREES, PRECISION);
    }

    private static Rational apply(Command command, Rational value, boolean inverse) {
        return UnaryFunctions.apply(command, value, inverse, DEGREES, PRECISION);
    }

    private static String text(Rational value) {
        return value.toDecimalString(PRECISION);
    }

    private static Rational half() {
        return Rational.of(1, 2);
    }

    // ---- Trigonometry ------------------------------------------------------------------------------

    @Test
    void trigonometryUsesTheAngleUnitItIsGiven() {
        assertEquals("0.5", text(apply(Command.SIN, Rational.of(30))));
        assertEquals("1", text(apply(Command.TAN, Rational.of(45))));
        assertEquals("1", text(UnaryFunctions.apply(Command.SIN, Rational.of(100), false, GRADIANS, PRECISION)));
        assertEquals("1", text(UnaryFunctions.apply(Command.COS, Rational.zero(), false, RADIANS, PRECISION)));
    }

    @Test
    void theInverseFlagSelectsTheArcFunction() {
        assertEquals("30", text(apply(Command.SIN, half(), true)));
        assertEquals(RationalMath.acos(half(), DEGREES, PRECISION), apply(Command.COS, half(), true));
        assertEquals(RationalMath.atan(half(), DEGREES, PRECISION), apply(Command.TAN, half(), true));
        assertEquals(RationalMath.asinh(half(), PRECISION), apply(Command.SINH, half(), true));
        assertEquals(RationalMath.acosh(Rational.of(2), PRECISION), apply(Command.COSH, Rational.of(2), true));
        assertEquals(RationalMath.atanh(half(), PRECISION), apply(Command.TANH, half(), true));
    }

    @Test
    void theArcFunctionsAreAlsoTheirOwnCommands() {
        assertEquals(RationalMath.asin(half(), DEGREES, PRECISION), apply(Command.ASIN, half()));
        assertEquals(RationalMath.acos(half(), DEGREES, PRECISION), apply(Command.ACOS, half()));
        assertEquals(RationalMath.atan(half(), DEGREES, PRECISION), apply(Command.ATAN, half()));
        assertEquals(RationalMath.asinh(half(), PRECISION), apply(Command.ASINH, half()));
        assertEquals(RationalMath.acosh(Rational.of(2), PRECISION), apply(Command.ACOSH, Rational.of(2)));
        assertEquals(RationalMath.atanh(half(), PRECISION), apply(Command.ATANH, half()));
    }

    @Test
    void theReciprocalFunctionsInvertTheirResult() {
        assertEquals("1", text(apply(Command.SEC, Rational.zero())));
        assertEquals("2", text(apply(Command.CSC, Rational.of(30))));
        assertEquals("1", text(apply(Command.COT, Rational.of(45))));
        assertEquals("1", text(apply(Command.SECH, Rational.zero())));
        // csch and coth are the reciprocals of sinh and tanh; the core has no separate function.
        assertEquals(Rational.one().divide(RationalMath.sinh(half(), PRECISION)), apply(Command.CSCH, half()));
        assertEquals(Rational.one().divide(RationalMath.tanh(half(), PRECISION)), apply(Command.COTH, half()));
    }

    @Test
    void theReciprocalFunctionsInvertTheirArgumentWhenInverted() {
        final Rational two = Rational.two();
        assertEquals(RationalMath.acos(half(), DEGREES, PRECISION), apply(Command.SEC, two, true));
        assertEquals(RationalMath.asin(half(), DEGREES, PRECISION), apply(Command.CSC, two, true));
        assertEquals(RationalMath.atan(half(), DEGREES, PRECISION), apply(Command.COT, two, true));
        assertEquals(RationalMath.acosh(two, PRECISION), apply(Command.SECH, half(), true));
        assertEquals(RationalMath.asinh(half(), PRECISION), apply(Command.CSCH, two, true));
        assertEquals(RationalMath.atanh(half(), PRECISION), apply(Command.COTH, two, true));

        assertEquals(RationalMath.acos(half(), DEGREES, PRECISION), apply(Command.ASEC, two));
        assertEquals(RationalMath.asin(half(), DEGREES, PRECISION), apply(Command.ACSC, two));
        assertEquals(RationalMath.atan(half(), DEGREES, PRECISION), apply(Command.ACOT, two));
        assertEquals(RationalMath.acosh(two, PRECISION), apply(Command.ASECH, half()));
        assertEquals(RationalMath.asinh(half(), PRECISION), apply(Command.ACSCH, two));
        assertEquals(RationalMath.atanh(half(), PRECISION), apply(Command.ACOTH, two));
    }

    // ---- Logarithms, roots and powers ---------------------------------------------------------------

    @Test
    void logarithmsAreRoutedToTheirBase() {
        assertEquals("2", text(apply(Command.LOG, Rational.hundred())));
        assertEquals(RationalMath.ln(Rational.of(7), PRECISION), apply(Command.LN, Rational.of(7)));
        assertEquals(RationalMath.exp(Rational.of(7), PRECISION), apply(Command.LN, Rational.of(7), true));
    }

    @Test
    void rootsAndPowersAreExactWhenTheyCanBe() {
        assertEquals("3", text(apply(Command.SQRT, Rational.of(9))));
        assertEquals("4", text(apply(Command.SQR, Rational.two())));
        assertEquals("8", text(apply(Command.CUBE, Rational.two())));
        assertEquals("3", text(apply(Command.CUBEROOT, Rational.of(27))));
        assertEquals("100", text(apply(Command.POW10, Rational.two())));
        assertEquals("8", text(apply(Command.POW2, Rational.of(3))));
        assertEquals(RationalMath.exp(Rational.of(3), PRECISION), apply(Command.POW_E, Rational.of(3)));
    }

    @Test
    void factorialsAndReciprocals() {
        assertEquals("120", text(apply(Command.FACTORIAL, Rational.of(5))));
        assertEquals("1", text(apply(Command.FACTORIAL, Rational.zero())));
        assertEquals("0.25", text(apply(Command.RECIPROCAL, Rational.of(4))));
    }

    // ---- Parts, rounding and sign -------------------------------------------------------------------

    @Test
    void integerAndFractionalPartsSplitAValue() {
        assertEquals("1", text(apply(Command.INTEGER_PART, Rational.of(7, 4))));
        assertEquals("0.75", text(apply(Command.INTEGER_PART, Rational.of(7, 4), true)));
        assertEquals("0.25", text(apply(Command.FRACTIONAL_PART, Rational.of(5, 4))));
    }

    @Test
    void roundingFollowsTheSignOfTheValue() {
        assertEquals("3", text(apply(Command.ABS, Rational.of(-3))));
        assertEquals("3", text(apply(Command.CEIL, Rational.of(5, 2))));
        assertEquals("2", text(apply(Command.FLOOR, Rational.of(5, 2))));
        assertEquals("-2", text(apply(Command.CEIL, Rational.of(-5, 2))));
        assertEquals("-3", text(apply(Command.FLOOR, Rational.of(-5, 2))));
    }

    @Test
    void degreesMinutesSecondsConvertInBothDirections() {
        assertEquals("1.3", text(apply(Command.DMS, Rational.of(3, 2))));
        assertEquals(RationalMath.fromDegreesMinutesSeconds(Rational.of(3, 2), PRECISION),
                apply(Command.DMS, Rational.of(3, 2), true));
        assertEquals(RationalMath.fromDegreesMinutesSeconds(Rational.of(3, 2), PRECISION),
                apply(Command.DEGREES, Rational.of(3, 2)));
    }

    // ---- Failures -----------------------------------------------------------------------------------

    @Test
    void aFailureCarriesTheKindTheEngineReports() {
        final CalculationException domain = assertThrows(CalculationException.class,
                () -> apply(Command.SQRT, Rational.zero().subtract(Rational.one())));
        assertEquals(EngineError.DOMAIN, domain.error());

        assertEquals(EngineError.DOMAIN, assertThrows(CalculationException.class,
                () -> apply(Command.ASIN, Rational.two())).error());
        assertEquals(EngineError.DOMAIN, assertThrows(CalculationException.class,
                () -> apply(Command.LN, Rational.zero())).error());
        assertEquals(EngineError.DOMAIN, assertThrows(CalculationException.class,
                () -> apply(Command.FACTORIAL, Rational.of(-1))).error());
        assertEquals(EngineError.DOMAIN, assertThrows(CalculationException.class,
                () -> apply(Command.ACOSH, half())).error());
        assertEquals(EngineError.DOMAIN, assertThrows(CalculationException.class,
                () -> apply(Command.ATANH, Rational.one())).error());
    }

    @Test
    void aReciprocalOfZeroIsADivideByZeroFailure() {
        assertEquals(EngineError.DIVIDE_BY_ZERO, assertThrows(CalculationException.class,
                () -> apply(Command.RECIPROCAL, Rational.zero())).error());
        assertEquals(EngineError.DIVIDE_BY_ZERO, assertThrows(CalculationException.class,
                () -> apply(Command.SEC, Rational.of(90))).error());
    }

    // ---- Non-functions and predicates ---------------------------------------------------------------

    @Test
    void aCommandThatIsNotAFunctionHandsBackItsOperand() {
        assertEquals(Rational.of(7), apply(Command.ADD, Rational.of(7)));
        assertEquals(Rational.of(7), apply(Command.EQUALS, Rational.of(7)));
        assertEquals(Rational.of(7), apply(Command.NONE, Rational.of(7)));
    }

    @Test
    void trigonometricAndInverseFlagPredicates() {
        assertTrue(UnaryFunctions.isTrigonometric(Command.SIN));
        assertTrue(UnaryFunctions.isTrigonometric(Command.ACOTH));
        assertFalse(UnaryFunctions.isTrigonometric(Command.SQRT));
        assertFalse(UnaryFunctions.isTrigonometric(Command.ADD));

        assertTrue(UnaryFunctions.usesInverseFlag(Command.SIN));
        assertTrue(UnaryFunctions.usesInverseFlag(Command.LN));
        assertTrue(UnaryFunctions.usesInverseFlag(Command.INTEGER_PART));
        assertFalse(UnaryFunctions.usesInverseFlag(Command.SQRT));
        assertFalse(UnaryFunctions.usesInverseFlag(Command.ADD));
    }

    @Test
    void theFractionalPartKeepsTheSignOfTheValue() {
        assertEquals(Rational.of(-1, 4), UnaryFunctions.fractionalPart(Rational.of(-5, 4)));
    }
}
