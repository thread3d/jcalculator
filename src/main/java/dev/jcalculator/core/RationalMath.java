package dev.jcalculator.core;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * The mathematical functions the calculator engine exposes, computed with arbitrary precision
 * decimal arithmetic.
 *
 * <p>Exact rationals cover the four basic operations; the transcendental functions cannot be represented
 * exactly, so each one is evaluated with {@code precision + guard} decimal digits and the result is
 * rounded back to the requested precision. That mirrors the RatPack implementation's contract: results
 * are correct to the displayed precision, not symbolic.
 */
public final class RationalMath {

    /** Extra digits carried through intermediate steps to protect the last displayed digit. */
    public static final int GUARD_DIGITS = 14;

    private static final BigDecimal PI = new BigDecimal(
            "3.141592653589793238462643383279502884197169399375105820974944592307816406286208998628034825342117068");
    private static final BigDecimal TWO_PI = PI.multiply(BigDecimal.valueOf(2));
    private static final BigDecimal E = new BigDecimal(
            "2.718281828459045235360287471352662497757247093699959574966967627724076630353547594571382178525166427");
    private static final BigDecimal LN_2 = new BigDecimal(
            "0.693147180559945309417232121458176568075500134360255254120680009493393621969694715605863326996418688");
    private static final BigDecimal LN_10 = new BigDecimal(
            "2.302585092994045684017991454684364207601101488628772976033327900967572609677352480235997205089598298");

    private RationalMath() {
    }

    public static MathContext context(int precision) {
        return new MathContext(Math.max(1, precision) + GUARD_DIGITS, RoundingMode.HALF_EVEN);
    }

    public static BigDecimal pi(int precision) {
        return PI.round(context(precision));
    }

    public static BigDecimal e(int precision) {
        return E.round(context(precision));
    }

    public static Rational sqrt(Rational value, int precision) {
        if (value.signum() < 0) {
            throw new ArithmeticException("square root of a negative number");
        }
        if (value.isZero()) {
            return Rational.zero();
        }
        final MathContext context = context(precision);
        return rational(new BigDecimal(value.numerator()).divide(new BigDecimal(value.denominator()), context)
                .sqrt(context), precision);
    }

    /**
     * {@code base ^ exponent}. An integer exponent is exact; any other exponent is computed as
     * {@code exp(exponent * ln(base))} and requires a positive base.
     */
    public static Rational pow(Rational base, Rational exponent, int precision) {
        if (exponent.isInteger() && exponent.numerator().bitLength() < 20) {
            final int power = exponent.numerator().intValue();
            if (Math.abs((long) power) <= 100_000) {
                return base.pow(power);
            }
        }
        if (base.signum() == 0) {
            return exponent.signum() > 0 ? Rational.zero() : Rational.one();
        }
        if (base.signum() < 0) {
            throw new ArithmeticException("negative base with a non-integer exponent");
        }
        final MathContext context = context(precision);
        final BigDecimal product = exponent.toBigDecimal(context).multiply(ln(base.toBigDecimal(context), context), context);
        return rational(exp(product, context), precision);
    }

    /** The {@code y}-th root of {@code value}, with odd roots of negative numbers defined. */
    public static Rational root(Rational value, Rational degree, int precision) {
        if (degree.isZero()) {
            throw new ArithmeticException("zero-th root");
        }
        if (degree.isInteger() && degree.numerator().bitLength() < 31) {
            final int n = degree.numerator().intValue();
            if (n > 0 && n <= 100_000) {
                return integerRoot(value, n, precision);
            }
        }
        if (value.signum() < 0) {
            throw new ArithmeticException("even root of a negative number");
        }
        if (value.isZero()) {
            return Rational.zero();
        }
        final MathContext context = context(precision);
        final BigDecimal logarithm = ln(value.toBigDecimal(context), context).divide(degree.toBigDecimal(context), context);
        return rational(exp(logarithm, context), precision);
    }

    public static Rational integerRoot(Rational value, int degree, int precision) {
        if (degree <= 0) {
            throw new ArithmeticException("root degree must be positive");
        }
        if (value.signum() < 0) {
            if (degree % 2 == 0) {
                throw new ArithmeticException("even root of a negative number");
            }
            return integerRoot(value.negate(), degree, precision).negate();
        }
        if (value.isZero()) {
            return Rational.zero();
        }
        final MathContext context = context(precision);
        final BigDecimal magnitude = value.toBigDecimal(context);
        // Newton iteration: x <- ((n - 1) * x + value / x^(n - 1)) / n
        BigDecimal guess = magnitude.pow(1).divide(BigDecimal.valueOf(degree), context);
        if (guess.signum() == 0) {
            guess = BigDecimal.ONE;
        }
        BigDecimal previous = null;
        for (int iteration = 0; iteration < 200; iteration++) {
            final BigDecimal powered = guess.pow(degree - 1, context);
            if (powered.signum() == 0) {
                break;
            }
            final BigDecimal next = guess.multiply(BigDecimal.valueOf(degree - 1), context)
                    .add(magnitude.divide(powered, context), context)
                    .divide(BigDecimal.valueOf(degree), context);
            if (previous != null && next.compareTo(previous) == 0) {
                return rational(next, precision);
            }
            previous = guess;
            guess = next;
        }
        return rational(guess, precision);
    }

    public static Rational exp(Rational value, int precision) {
        final MathContext context = context(precision);
        return rational(exp(value.toBigDecimal(context), context), precision);
    }

    public static Rational ln(Rational value, int precision) {
        if (value.signum() <= 0) {
            throw new ArithmeticException("logarithm of a non-positive number");
        }
        final MathContext context = context(precision);
        return rational(snapToZero(ln(value.toBigDecimal(context), context), value, precision), precision);
    }

    public static Rational log10(Rational value, int precision) {
        if (value.signum() <= 0) {
            throw new ArithmeticException("logarithm of a non-positive number");
        }
        final MathContext context = context(precision);
        final BigDecimal logged =
                snapToZero(ln(value.toBigDecimal(context), context), value, precision);
        return rational(logged.divide(LN_10, context), precision);
    }

    public static Rational sin(Rational value, AngleType angleType, int precision) {
        final MathContext context = context(precision);
        return rational(snapToZero(sin(toRadians(value, angleType, context), context), precision), precision);
    }

    public static Rational cos(Rational value, AngleType angleType, int precision) {
        final MathContext context = context(precision);
        return rational(snapToZero(cos(toRadians(value, angleType, context), context), precision), precision);
    }

    public static Rational tan(Rational value, AngleType angleType, int precision) {
        final MathContext context = context(precision);
        final BigDecimal radians = toRadians(value, angleType, context);
        final BigDecimal cosine = cos(radians, context);
        if (cosine.abs().compareTo(BigDecimal.ONE.scaleByPowerOfTen(-(precision + GUARD_DIGITS - 2))) < 0) {
            throw new ArithmeticException("tangent is undefined at this angle");
        }
        return rational(snapToZero(sin(radians, context).divide(cosine, context), precision), precision);
    }

    public static Rational asin(Rational value, AngleType angleType, int precision) {
        if (value.compareTo(Rational.one()) > 0 || value.compareTo(Rational.one().negate()) < 0) {
            throw new ArithmeticException("arc sine domain");
        }
        final MathContext context = context(precision);
        final BigDecimal radians = asin(value.toBigDecimal(context), context);
        return rational(fromRadians(radians, angleType), precision);
    }

    public static Rational acos(Rational value, AngleType angleType, int precision) {
        if (value.compareTo(Rational.one()) > 0 || value.compareTo(Rational.one().negate()) < 0) {
            throw new ArithmeticException("arc cosine domain");
        }
        final MathContext context = context(precision);
        final BigDecimal radians = PI.divide(BigDecimal.valueOf(2), context).subtract(asin(value.toBigDecimal(context), context));
        return rational(fromRadians(radians, angleType), precision);
    }

    public static Rational atan(Rational value, AngleType angleType, int precision) {
        final MathContext context = context(precision);
        final BigDecimal radians = atan(value.toBigDecimal(context), context);
        return rational(fromRadians(radians, angleType), precision);
    }

    public static Rational sinh(Rational value, int precision) {
        final MathContext context = context(precision);
        final BigDecimal x = value.toBigDecimal(context);
        final BigDecimal positive = exp(x, context);
        final BigDecimal negative = exp(x.negate(), context);
        return rational(positive.subtract(negative, context).divide(BigDecimal.valueOf(2), context), precision);
    }

    public static Rational cosh(Rational value, int precision) {
        final MathContext context = context(precision);
        final BigDecimal x = value.toBigDecimal(context);
        final BigDecimal positive = exp(x, context);
        final BigDecimal negative = exp(x.negate(), context);
        return rational(positive.add(negative, context).divide(BigDecimal.valueOf(2), context), precision);
    }

    public static Rational tanh(Rational value, int precision) {
        final MathContext context = context(precision);
        return rational(tanh(value.toBigDecimal(context), context), precision);
    }

    public static Rational asinh(Rational value, int precision) {
        final MathContext context = context(precision);
        final BigDecimal x = value.toBigDecimal(context);
        final BigDecimal root = x.multiply(x, context).add(BigDecimal.ONE, context).sqrt(context);
        return rational(ln(x.add(root, context), context), precision);
    }

    public static Rational acosh(Rational value, int precision) {
        if (value.compareTo(Rational.one()) < 0) {
            throw new ArithmeticException("arc hyperbolic cosine domain");
        }
        final MathContext context = context(precision);
        final BigDecimal x = value.toBigDecimal(context);
        final BigDecimal root = x.multiply(x, context).subtract(BigDecimal.ONE, context).sqrt(context);
        return rational(ln(x.add(root, context), context), precision);
    }

    public static Rational atanh(Rational value, int precision) {
        if (value.compareTo(Rational.one()) >= 0 || value.compareTo(Rational.one().negate()) <= 0) {
            throw new ArithmeticException("arc hyperbolic tangent domain");
        }
        final MathContext context = context(precision);
        final BigDecimal x = value.toBigDecimal(context);
        final BigDecimal ratio = BigDecimal.ONE.add(x, context).divide(BigDecimal.ONE.subtract(x, context), context);
        return rational(ln(ratio, context).divide(BigDecimal.valueOf(2), context), precision);
    }

    /** {@code value!} for a non-negative integer. */
    public static Rational factorial(Rational value, int precision) {
        final Rational truncated = value.truncate();
        if (!truncated.equals(value) || value.signum() < 0) {
            throw new ArithmeticException("factorial requires a non-negative integer");
        }
        if (truncated.compareTo(Rational.of(10_000)) > 0) {
            throw new ArithmeticException("factorial overflow");
        }
        final int n = truncated.numerator().intValue();
        BigInteger result = BigInteger.ONE;
        for (int i = 2; i <= n; i++) {
            result = result.multiply(BigInteger.valueOf(i));
        }
        return Rational.of(result);
    }

    /** Decimal degrees to degrees/minutes/seconds notation: {@code 1.5} becomes {@code 1.3}. */
    public static Rational toDegreesMinutesSeconds(Rational value, int precision) {
        final MathContext context = context(precision);
        final BigDecimal magnitude = value.abs().toBigDecimal(context);
        final BigDecimal degrees = magnitude.setScale(0, RoundingMode.DOWN);
        final BigDecimal fraction = magnitude.subtract(degrees, context).multiply(BigDecimal.valueOf(60), context);
        final BigDecimal minutes = fraction.setScale(0, RoundingMode.DOWN);
        final BigDecimal seconds = fraction.subtract(minutes, context).multiply(BigDecimal.valueOf(60), context);
        final BigDecimal result = degrees
                .add(minutes.divide(BigDecimal.valueOf(100), context), context)
                .add(seconds.divide(BigDecimal.valueOf(10_000), context), context)
                .multiply(BigDecimal.valueOf(value.signum() < 0 ? -1 : 1), context);
        return rational(result, precision);
    }

    /** Degrees/minutes/seconds notation back to decimal degrees: {@code 1.3} becomes {@code 1.5}. */
    public static Rational fromDegreesMinutesSeconds(Rational value, int precision) {
        final MathContext context = context(precision);
        final BigDecimal magnitude = value.abs().toBigDecimal(context);
        final BigDecimal degrees = magnitude.setScale(0, RoundingMode.DOWN);
        final BigDecimal fraction = magnitude.subtract(degrees, context).multiply(BigDecimal.valueOf(100), context);
        final BigDecimal minutes = fraction.setScale(0, RoundingMode.DOWN);
        final BigDecimal seconds = fraction.subtract(minutes, context).multiply(BigDecimal.valueOf(100), context);
        final BigDecimal result = degrees
                .add(minutes.divide(BigDecimal.valueOf(60), context), context)
                .add(seconds.divide(BigDecimal.valueOf(3600), context), context)
                .multiply(BigDecimal.valueOf(value.signum() < 0 ? -1 : 1), context);
        return rational(result, precision);
    }

    // ---- BigDecimal internals -------------------------------------------------------------------

    private static BigDecimal exp(BigDecimal value, MathContext context) {
        if (value.signum() == 0) {
            return BigDecimal.ONE;
        }
        final boolean negative = value.signum() < 0;
        BigDecimal x = negative ? value.negate() : value;
        // Reduce the argument until |x| < 0.5, then square the series result back up.
        int halvings = 0;
        while (x.abs().compareTo(new BigDecimal("0.5")) > 0) {
            x = x.divide(BigDecimal.valueOf(2), context);
            halvings++;
            if (halvings > 100_000) {
                throw new ArithmeticException("exponential overflow");
            }
        }
        BigDecimal sum = BigDecimal.ONE;
        BigDecimal term = BigDecimal.ONE;
        final BigDecimal tolerance = BigDecimal.ONE.scaleByPowerOfTen(-(context.getPrecision() + 2));
        for (int i = 1; i < context.getPrecision() * 4 + 100; i++) {
            term = term.multiply(x, context).divide(BigDecimal.valueOf(i), context);
            sum = sum.add(term, context);
            if (term.abs().compareTo(tolerance) < 0) {
                break;
            }
        }
        for (int i = 0; i < halvings; i++) {
            sum = sum.multiply(sum, context);
        }
        return negative ? BigDecimal.ONE.divide(sum, context) : sum;
    }

    private static BigDecimal ln(BigDecimal value, MathContext context) {
        if (value.signum() <= 0) {
            throw new ArithmeticException("logarithm of a non-positive number");
        }
        BigDecimal x = value;
        int scale = 0;
        final BigDecimal two = BigDecimal.valueOf(2);
        while (x.compareTo(two) >= 0) {
            x = x.divide(two, context);
            scale++;
        }
        while (x.compareTo(new BigDecimal("0.5")) < 0) {
            x = x.multiply(two, context);
            scale--;
        }
        // ln(x) = 2 * atanh((x - 1) / (x + 1))
        final BigDecimal z = x.subtract(BigDecimal.ONE, context).divide(x.add(BigDecimal.ONE, context), context);
        final BigDecimal zSquared = z.multiply(z, context);
        BigDecimal term = z;
        BigDecimal sum = term;
        final BigDecimal tolerance = BigDecimal.ONE.scaleByPowerOfTen(-(context.getPrecision() + 2));
        for (int k = 3; k < context.getPrecision() * 4 + 100; k += 2) {
            term = term.multiply(zSquared, context);
            final BigDecimal addend = term.divide(BigDecimal.valueOf(k), context);
            sum = sum.add(addend, context);
            if (addend.abs().compareTo(tolerance) < 0) {
                break;
            }
        }
        BigDecimal result = sum.multiply(two, context);
        if (scale != 0) {
            result = result.add(LN_2.multiply(BigDecimal.valueOf(scale), context), context);
        }
        return result;
    }

    private static BigDecimal sin(BigDecimal radians, MathContext context) {
        final BigDecimal x = reduceAngle(radians, context);
        final BigDecimal xSquared = x.multiply(x, context);
        BigDecimal term = x;
        BigDecimal sum = x;
        final BigDecimal tolerance = BigDecimal.ONE.scaleByPowerOfTen(-(context.getPrecision() + 2));
        for (int k = 1; k < context.getPrecision() * 2 + 100; k++) {
            term = term.multiply(xSquared, context)
                    .divide(BigDecimal.valueOf((long) (2 * k) * (2 * k + 1)), context)
                    .negate();
            sum = sum.add(term, context);
            if (term.abs().compareTo(tolerance) < 0) {
                break;
            }
        }
        return sum;
    }

    private static BigDecimal cos(BigDecimal radians, MathContext context) {
        final BigDecimal x = reduceAngle(radians, context);
        final BigDecimal xSquared = x.multiply(x, context);
        BigDecimal term = BigDecimal.ONE;
        BigDecimal sum = BigDecimal.ONE;
        final BigDecimal tolerance = BigDecimal.ONE.scaleByPowerOfTen(-(context.getPrecision() + 2));
        for (int k = 1; k < context.getPrecision() * 2 + 100; k++) {
            term = term.multiply(xSquared, context)
                    .divide(BigDecimal.valueOf((long) (2 * k - 1) * (2 * k)), context)
                    .negate();
            sum = sum.add(term, context);
            if (term.abs().compareTo(tolerance) < 0) {
                break;
            }
        }
        return sum;
    }

    private static BigDecimal tanh(BigDecimal x, MathContext context) {
        final BigDecimal doubled = exp(x.multiply(BigDecimal.valueOf(2), context), context);
        return doubled.subtract(BigDecimal.ONE, context).divide(doubled.add(BigDecimal.ONE, context), context);
    }

    private static BigDecimal asin(BigDecimal value, MathContext context) {
        if (value.compareTo(BigDecimal.ONE) == 0) {
            return PI.divide(BigDecimal.valueOf(2), context);
        }
        if (value.compareTo(BigDecimal.ONE.negate()) == 0) {
            return PI.divide(BigDecimal.valueOf(2), context).negate();
        }
        final BigDecimal denominator = BigDecimal.ONE.subtract(value.multiply(value, context), context).sqrt(context);
        return atan(value.divide(denominator, context), context);
    }

    private static BigDecimal atan(BigDecimal value, MathContext context) {
        if (value.signum() == 0) {
            return BigDecimal.ZERO;
        }
        final boolean negative = value.signum() < 0;
        BigDecimal x = value.abs();
        final boolean inverted = x.compareTo(BigDecimal.ONE) > 0;
        if (inverted) {
            x = BigDecimal.ONE.divide(x, context);
        }
        // atan(x) = 2 * atan(x / (1 + sqrt(1 + x^2))) halves the argument repeatedly.
        int doublings = 0;
        final BigDecimal limit = new BigDecimal("0.1");
        while (x.compareTo(limit) > 0) {
            final BigDecimal root = BigDecimal.ONE.add(x.multiply(x, context), context).sqrt(context);
            x = x.divide(BigDecimal.ONE.add(root, context), context);
            doublings++;
            if (doublings > 100) {
                break;
            }
        }
        final BigDecimal xSquared = x.multiply(x, context);
        BigDecimal term = x;
        BigDecimal sum = x;
        final BigDecimal tolerance = BigDecimal.ONE.scaleByPowerOfTen(-(context.getPrecision() + 2));
        for (int k = 1; k < context.getPrecision() * 2 + 100; k++) {
            term = term.multiply(xSquared, context).negate();
            final BigDecimal addend = term.divide(BigDecimal.valueOf(2L * k + 1), context);
            sum = sum.add(addend, context);
            if (addend.abs().compareTo(tolerance) < 0) {
                break;
            }
        }
        sum = sum.multiply(BigDecimal.valueOf(1L << doublings), context);
        if (inverted) {
            sum = PI.divide(BigDecimal.valueOf(2), context).subtract(sum, context);
        }
        return negative ? sum.negate() : sum;
    }

    private static BigDecimal reduceAngle(BigDecimal radians, MathContext context) {
        final BigDecimal quotient = radians.divide(TWO_PI, context).setScale(0, RoundingMode.HALF_EVEN);
        return radians.subtract(quotient.multiply(TWO_PI, context), context);
    }

    private static BigDecimal toRadians(Rational value, AngleType angleType, MathContext context) {
        final BigDecimal x = value.toBigDecimal(context);
        return switch (angleType) {
            case DEGREES -> x.multiply(PI, context).divide(BigDecimal.valueOf(180), context);
            case GRADIANS -> x.multiply(PI, context).divide(BigDecimal.valueOf(200), context);
            case RADIANS -> x;
        };
    }

    private static BigDecimal fromRadians(BigDecimal radians, AngleType angleType) {
        final MathContext context = context(radians.precision());
        return switch (angleType) {
            case DEGREES -> radians.multiply(BigDecimal.valueOf(180), context).divide(PI, context);
            case GRADIANS -> radians.multiply(BigDecimal.valueOf(200), context).divide(PI, context);
            case RADIANS -> radians;
        };
    }

    /** Snaps a trigonometric residual to zero when it is below the displayed precision. */
    /**
     * Snaps a logarithm to zero when its result is below the working precision of its input.
     *
     * <p>The original's {@code lograt} ends with {@code _snaprat(px, a, nullptr, precision)}, whose
     * threshold is {@code |a| * radix^-precision}: a logarithm of a value a hair away from one reads
     * zero instead of a rounding artefact of the last guard digit.
     */
    private static BigDecimal snapToZero(BigDecimal result, Rational input, int precision) {
        final MathContext context = context(precision);
        final BigDecimal threshold = input.abs().toBigDecimal(context)
                .multiply(BigDecimal.ONE.scaleByPowerOfTen(-precision), context);
        return result.abs().compareTo(threshold) < 0 ? BigDecimal.ZERO : result;
    }

    private static BigDecimal snapToZero(BigDecimal value, int precision) {
        final BigDecimal threshold = BigDecimal.ONE.scaleByPowerOfTen(-(precision + 2));
        return value.abs().compareTo(threshold) < 0 ? BigDecimal.ZERO : value;
    }

    private static Rational rational(BigDecimal value, int precision) {
        final BigDecimal rounded = value.round(new MathContext(precision, RoundingMode.HALF_UP)).stripTrailingZeros();
        return Rational.of(rounded);
    }
}
