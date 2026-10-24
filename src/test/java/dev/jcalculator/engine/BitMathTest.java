package dev.jcalculator.engine;

import dev.jcalculator.core.Rational;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The word arithmetic behind programmer mode: reading a value as a fixed-width integer, and the
 * integer root the {@code yroot} key uses.
 */
class BitMathTest {

    private static final int BYTE = 8;
    private static final int QWORD = 64;

    @Test
    void aValueIsReadAsTheLowBitsOfItsIntegerPart() {
        assertEquals(BigInteger.valueOf(3), BitMath.toBits(Rational.of(7, 2), BYTE));
        assertEquals(BigInteger.valueOf(200), BitMath.toBits(Rational.of(200), BYTE));
        assertEquals(BigInteger.ZERO, BitMath.toBits(Rational.of(256), BYTE));
        assertEquals(BigInteger.valueOf(255), BitMath.toBits(Rational.of(-1), BYTE));
        assertEquals(BigInteger.valueOf(254), BitMath.toBits(Rational.of(-2), BYTE));
    }

    @Test
    void theTopBitIsASignBit() {
        assertEquals(BigInteger.valueOf(-1), BitMath.toSigned(Rational.of(255), BYTE));
        assertEquals(BigInteger.valueOf(127), BitMath.toSigned(Rational.of(127), BYTE));
        assertEquals(BigInteger.valueOf(-128), BitMath.toSigned(Rational.of(128), BYTE));
        assertEquals(BigInteger.valueOf(0), BitMath.toSigned(Rational.zero(), QWORD));
        assertEquals(BigInteger.valueOf(-1), BitMath.toSigned(Rational.of(-1), QWORD));
    }

    @Test
    void theMaskCoversTheWholeWord() {
        assertEquals(BigInteger.valueOf(255), BitMath.mask(BYTE));
        assertEquals(BigInteger.valueOf(65535), BitMath.mask(16));
        assertEquals(BigInteger.ONE.shiftLeft(QWORD).subtract(BigInteger.ONE), BitMath.mask(QWORD));
    }

    @Test
    void anIntegerRootTruncatesTowardZero() {
        assertEquals(BigInteger.valueOf(3), BitMath.integerRoot(BigInteger.valueOf(27), 3));
        assertEquals(BigInteger.valueOf(3), BitMath.integerRoot(BigInteger.valueOf(28), 3));
        assertEquals(BigInteger.valueOf(-3), BitMath.integerRoot(BigInteger.valueOf(-27), 3));
        assertEquals(BigInteger.ZERO, BitMath.integerRoot(BigInteger.ZERO, 5));
        assertEquals(BigInteger.ONE, BitMath.integerRoot(BigInteger.ONE, 7));
        assertEquals(BigInteger.valueOf(1_000_000_000),
                BitMath.integerRoot(BigInteger.valueOf(1_000_000_000_000_000_000L), 2));
    }

    @Test
    void aDegenerateRootIsRefused() {
        final CalculationException zero = assertThrows(CalculationException.class,
                () -> BitMath.integerRoot(BigInteger.TEN, 0));
        assertEquals(EngineError.INVALID_RANGE, zero.error());

        final CalculationException even = assertThrows(CalculationException.class,
                () -> BitMath.integerRoot(BigInteger.valueOf(-8), 2));
        assertEquals(EngineError.DOMAIN, even.error());
    }
}
