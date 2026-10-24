package dev.jcalculator.engine;

import dev.jcalculator.core.AngleType;
import dev.jcalculator.core.Rational;
import dev.jcalculator.core.RationalMath;

/**
 * The unary functions the scientific and programmer pages offer.
 *
 * <p>The counterpart of the original's {@code scifunc.cpp}: one place that maps a function command
 * and its operand to a result, independent of where the operand came from. The functions that need
 * pending-operation state ({@code %}, {@code NOT} and the four rotations) stay in {@link CalcEngine},
 * because they read the operator or the word the engine is holding.
 *
 * <p>Every failure is raised as a {@link CalculationException}; nothing here writes to the display.
 * The original's ratpack layer behaves the same way, raising a result code the engine catches at the
 * command boundary.
 */
public final class UnaryFunctions {

    private UnaryFunctions() {
    }

    /**
     * Applies one unary function to a value.
     *
     * @param command   the function; a command that is not one of the pure functions yields the value
     * @param value     the operand
     * @param inverse   whether the inverse flag is set, so {@code sin} means {@code sin⁻¹}
     * @param angleType the angle unit used by the trigonometric functions
     * @param precision significant digits the transcendental functions are computed to
     * @throws CalculationException when the operand is outside the function's domain
     */
    public static Rational apply(Command command, Rational value, boolean inverse, AngleType angleType,
            int precision) {
        try {
            return switch (command) {
                case INTEGER_PART -> inverse ? fractionalPart(value) : value.truncate();
                case FRACTIONAL_PART -> fractionalPart(value);
                case SIN -> inverse
                        ? RationalMath.asin(value, angleType, precision)
                        : RationalMath.sin(value, angleType, precision);
                case COS -> inverse
                        ? RationalMath.acos(value, angleType, precision)
                        : RationalMath.cos(value, angleType, precision);
                case TAN -> inverse
                        ? RationalMath.atan(value, angleType, precision)
                        : RationalMath.tan(value, angleType, precision);
                case SINH -> inverse ? RationalMath.asinh(value, precision) : RationalMath.sinh(value, precision);
                case COSH -> inverse ? RationalMath.acosh(value, precision) : RationalMath.cosh(value, precision);
                case TANH -> inverse ? RationalMath.atanh(value, precision) : RationalMath.tanh(value, precision);
                case SEC -> inverse
                        ? RationalMath.acos(invert(value), angleType, precision)
                        : invert(RationalMath.cos(value, angleType, precision));
                case CSC -> inverse
                        ? RationalMath.asin(invert(value), angleType, precision)
                        : invert(RationalMath.sin(value, angleType, precision));
                case COT -> inverse
                        ? RationalMath.atan(invert(value), angleType, precision)
                        : invert(RationalMath.tan(value, angleType, precision));
                case SECH -> inverse
                        ? RationalMath.acosh(invert(value), precision)
                        : invert(RationalMath.cosh(value, precision));
                case CSCH -> inverse
                        ? RationalMath.asinh(invert(value), precision)
                        : invert(RationalMath.sinh(value, precision));
                case COTH -> inverse
                        ? RationalMath.atanh(invert(value), precision)
                        : invert(RationalMath.tanh(value, precision));
                case ASIN -> RationalMath.asin(value, angleType, precision);
                case ACOS -> RationalMath.acos(value, angleType, precision);
                case ATAN -> RationalMath.atan(value, angleType, precision);
                case ASINH -> RationalMath.asinh(value, precision);
                case ACOSH -> RationalMath.acosh(value, precision);
                case ATANH -> RationalMath.atanh(value, precision);
                case ASEC -> RationalMath.acos(invert(value), angleType, precision);
                case ACSC -> RationalMath.asin(invert(value), angleType, precision);
                case ACOT -> RationalMath.atan(invert(value), angleType, precision);
                case ASECH -> RationalMath.acosh(invert(value), precision);
                case ACSCH -> RationalMath.asinh(invert(value), precision);
                case ACOTH -> RationalMath.atanh(invert(value), precision);
                case LN -> inverse ? RationalMath.exp(value, precision) : RationalMath.ln(value, precision);
                case LOG -> RationalMath.log10(value, precision);
                case SQRT -> RationalMath.sqrt(value, precision);
                case SQR -> RationalMath.pow(value, Rational.two(), precision);
                case CUBE -> RationalMath.pow(value, Rational.of(3), precision);
                case CUBEROOT -> RationalMath.root(value, Rational.of(3), precision);
                case FACTORIAL -> RationalMath.factorial(value, precision);
                case RECIPROCAL -> invert(value);
                case DMS -> inverse
                        ? RationalMath.fromDegreesMinutesSeconds(value, precision)
                        : RationalMath.toDegreesMinutesSeconds(value, precision);
                case DEGREES -> RationalMath.fromDegreesMinutesSeconds(value, precision);
                case POW10 -> RationalMath.pow(Rational.ten(), value, precision);
                case POW2 -> RationalMath.pow(Rational.two(), value, precision);
                case POW_E -> RationalMath.exp(value, precision);
                case ABS -> value.abs();
                case CEIL -> value.ceiling();
                case FLOOR -> value.floor();
                default -> value;
            };
        } catch (ArithmeticException failure) {
            throw new CalculationException(EngineError.DOMAIN);
        }
    }

    /** Whether the command is one of the trigonometric or hyperbolic functions. */
    public static boolean isTrigonometric(Command command) {
        return switch (command) {
            case SIN, COS, TAN, SINH, COSH, TANH, SEC, CSC, COT, SECH, CSCH, COTH,
                    ASIN, ACOS, ATAN, ASINH, ACOSH, ATANH, ASEC, ACSC, ACOT, ASECH, ACSCH, ACOTH -> true;
            default -> false;
        };
    }

    /** Whether pressing the function consumes the inverse flag, as {@code sin} and {@code ln} do. */
    public static boolean usesInverseFlag(Command command) {
        return switch (command) {
            case INTEGER_PART, SIN, COS, TAN, LN, DMS, DEGREES, SINH, COSH, TANH,
                    SEC, CSC, COT, SECH, CSCH, COTH -> true;
            default -> false;
        };
    }

    /** The fractional part of a value, keeping its sign, as {@code frac} does. */
    public static Rational fractionalPart(Rational value) {
        return value.subtract(value.truncate());
    }

    /** The reciprocal of a value. */
    private static Rational invert(Rational value) {
        if (value.isZero()) {
            throw new CalculationException(EngineError.DIVIDE_BY_ZERO);
        }
        return Rational.one().divide(value);
    }
}
