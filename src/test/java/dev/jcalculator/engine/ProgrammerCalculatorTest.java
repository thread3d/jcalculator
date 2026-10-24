package dev.jcalculator.engine;

import dev.jcalculator.manager.CalculatorManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgrammerCalculatorTest {

    static CalculatorManager calculator() {
        return new CalculatorManager(CalculatorMode.PROGRAMMER);
    }

    static void press(CalculatorManager calculator, Command... commands) {
        for (Command command : commands) {
            calculator.sendCommand(command);
        }
    }

    static void typeDecimal(CalculatorManager calculator, String text) {
        for (char c : text.toCharArray()) {
            calculator.sendCommand(StandardCalculatorTest.digit(c));
        }
    }

    @Test
    void bitwiseOperatorsUseTheWholeWord() {
        final CalculatorManager calculator = calculator();
        press(calculator, Command.FIVE, Command.THREE, Command.NAND,
                Command.EIGHT, Command.THREE, Command.AND);
        assertEquals("-18", calculator.primaryDisplay());

        final CalculatorManager nor = calculator();
        press(nor, Command.FIVE, Command.THREE, Command.NOR,
                Command.EIGHT, Command.THREE, Command.AND);
        assertEquals("-120", nor.primaryDisplay());
    }

    @Test
    void shiftsAndRotation() {
        final CalculatorManager shift = calculator();
        press(shift, Command.FIVE, Command.LEFT_SHIFT, Command.ONE, Command.AND);
        assertEquals("10", shift.primaryDisplay());

        final CalculatorManager right = calculator();
        press(right, Command.FIVE, Command.RIGHT_SHIFT_LOGICAL, Command.ONE, Command.AND);
        assertEquals("2", right.primaryDisplay());

        final CalculatorManager rotateLeft = calculator();
        press(rotateLeft, Command.ONE, Command.ROTATE_LEFT);
        assertEquals("2", rotateLeft.primaryDisplay());

        final CalculatorManager rotateRight = calculator();
        press(rotateRight, Command.ONE, Command.ROTATE_RIGHT);
        assertEquals("-9,223,372,036,854,775,808", rotateRight.primaryDisplay());
    }

    @Test
    void wordWidthTruncatesAndSignExtends() {
        final CalculatorManager calculator = calculator();
        press(calculator, Command.WIDTH_BYTE);
        typeDecimal(calculator, "127");
        press(calculator, Command.ADD, Command.ONE, Command.EQUALS);
        assertEquals("-128", calculator.primaryDisplay());

        final CalculatorManager hex = calculator();
        press(hex, Command.RADIX_16, Command.F, Command.F, Command.RADIX_10);
        assertEquals("255", hex.primaryDisplay());

        final CalculatorManager negativeByte = calculator();
        press(negativeByte, Command.WIDTH_BYTE, Command.RADIX_16, Command.F, Command.F, Command.RADIX_10);
        assertEquals("-1", negativeByte.primaryDisplay());

        final CalculatorManager extended = calculator();
        press(extended, Command.WIDTH_BYTE, Command.RADIX_16, Command.F, Command.F);
        press(extended, Command.WIDTH_QWORD);
        assertEquals("FFFF FFFF FFFF FFFF", extended.primaryDisplay());
    }

    @Test
    void divisionTruncatesTowardsZero() {
        final CalculatorManager calculator = calculator();
        typeDecimal(calculator, "5");
        press(calculator, Command.DIVIDE);
        typeDecimal(calculator, "2");
        press(calculator, Command.EQUALS);
        assertEquals("2", calculator.primaryDisplay());

        press(calculator, Command.MULTIPLY);
        typeDecimal(calculator, "2");
        press(calculator, Command.EQUALS);
        assertEquals("4", calculator.primaryDisplay());

        final CalculatorManager large = calculator();
        typeDecimal(large, "4294967296");
        press(large, Command.DIVIDE);
        typeDecimal(large, "255");
        press(large, Command.EQUALS);
        assertEquals("16,843,009", large.primaryDisplay());
    }

    @Test
    void negativeArithmeticKeepsTheWordRepresentation() {
        final CalculatorManager calculator = calculator();
        typeDecimal(calculator, "5");
        press(calculator, Command.SUBTRACT);
        typeDecimal(calculator, "9");
        press(calculator, Command.EQUALS);
        assertEquals("-4", calculator.primaryDisplay());
        press(calculator, Command.RADIX_16);
        assertEquals("FFFF FFFF FFFF FFFC", calculator.primaryDisplay());
    }

    @Test
    void baseConversionRoundTrips() {
        final CalculatorManager calculator = calculator();
        press(calculator, Command.RADIX_16);
        press(calculator, Command.F, Command.F);
        press(calculator, Command.RADIX_2);
        assertEquals("1111 1111", calculator.primaryDisplay());
        press(calculator, Command.RADIX_8);
        assertEquals("377", calculator.primaryDisplay());
        press(calculator, Command.RADIX_10);
        assertEquals("255", calculator.primaryDisplay());
    }

    @Test
    void complementIsTheOnesComplementWithinTheWord() {
        final CalculatorManager calculator = calculator();
        press(calculator, Command.WIDTH_BYTE);
        typeDecimal(calculator, "5");
        press(calculator, Command.COMPLEMENT);
        assertEquals("-6", calculator.primaryDisplay());
        press(calculator, Command.RADIX_16);
        assertEquals("FA", calculator.primaryDisplay());
    }

    @Test
    void modulusUsesTheSignedOperands() {
        final CalculatorManager calculator = calculator();
        typeDecimal(calculator, "7");
        press(calculator, Command.MOD);
        typeDecimal(calculator, "4");
        press(calculator, Command.EQUALS);
        assertEquals("3", calculator.primaryDisplay());
    }

    @Test
    void binaryOperatorsCannotProduceFractionalResults() {
        final CalculatorManager calculator = calculator();
        typeDecimal(calculator, "7");
        press(calculator, Command.DIVIDE);
        typeDecimal(calculator, "2");
        press(calculator, Command.EQUALS);
        assertEquals("3", calculator.primaryDisplay());
        assertFalse(calculator.isError());
    }

    @Test
    void anEvenRootOfANegativeIsAnErrorNotACrash() {
        final CalculatorManager calculator = calculator();
        typeDecimal(calculator, "8");
        press(calculator, Command.NEGATE, Command.ROOT, Command.TWO, Command.EQUALS);

        assertTrue(calculator.isError());
        assertEquals("Invalid input", calculator.errorMessage());
        assertEquals(EngineError.DOMAIN, calculator.engine().error());
    }

    @Test
    void scientificFunctionsAreUnavailableInIntegerMode() {
        final CalculatorManager calculator = calculator();
        press(calculator, Command.NINE);
        press(calculator, Command.SQRT);
        assertEquals("3", calculator.primaryDisplay());
        assertFalse(calculator.isError());
        assertTrue(calculator.engine().mode().isIntegerMode());
    }
}
