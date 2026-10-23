package dev.jcalculator.engine;

import dev.jcalculator.core.AngleType;
import dev.jcalculator.manager.CalculatorManager;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScientificCalculatorTest {

    static CalculatorManager calculator() {
        return new CalculatorManager(CalculatorMode.SCIENTIFIC);
    }

    static void type(CalculatorManager calculator, String text) {
        for (char c : text.toCharArray()) {
            if (c == '.') {
                calculator.sendCommand(Command.DECIMAL_POINT);
            } else {
                calculator.sendCommand(StandardCalculatorTest.digit(c));
            }
        }
    }

    static void press(CalculatorManager calculator, Command... commands) {
        for (Command command : commands) {
            calculator.sendCommand(command);
        }
    }

    static void assertNumeric(String expected, String actual) {
        final BigDecimal expectedValue = new BigDecimal(expected);
        final BigDecimal actualValue = new BigDecimal(actual);
        final MathContext context = new MathContext(24, RoundingMode.HALF_EVEN);
        assertEquals(0, expectedValue.round(context).compareTo(actualValue.round(context)),
                "expected " + expected + " but was " + actual);
    }

    @Test
    void respectsOrderOfOperations() {
        final CalculatorManager calculator = calculator();
        type(calculator, "2");
        press(calculator, Command.ADD);
        type(calculator, "3");
        press(calculator, Command.MULTIPLY);
        type(calculator, "4");
        press(calculator, Command.EQUALS);
        assertEquals("14", calculator.primaryDisplay());
        assertEquals("2 + 3 × 4=", calculator.history().get(0).expression());
    }

    @Test
    void multiplicationBindsBeforeAdditionRegardlessOfEntryOrder() {
        final CalculatorManager calculator = calculator();
        type(calculator, "1");
        press(calculator, Command.ADD);
        type(calculator, "0");
        press(calculator, Command.MULTIPLY);
        type(calculator, "2");
        press(calculator, Command.EQUALS);
        assertEquals("1", calculator.primaryDisplay());
    }

    @Test
    void nestedSquareRoots() {
        final CalculatorManager calculator = calculator();
        type(calculator, "256");
        press(calculator, Command.SQRT, Command.SQRT, Command.SQRT);
        assertEquals("2", calculator.primaryDisplay());
        assertEquals("√(√(√(256)))", calculator.expression());
    }

    @Test
    void squareRootOfNegativeIsInvalid() {
        final CalculatorManager calculator = calculator();
        type(calculator, "6");
        press(calculator, Command.MULTIPLY, Command.NEGATE, Command.SQRT);
        assertEquals("Invalid input", calculator.primaryDisplay());
        assertTrue(calculator.isError());
    }

    @Test
    void reciprocalIsExpressionOneOverOperand() {
        final CalculatorManager calculator = calculator();
        type(calculator, "50");
        press(calculator, Command.ADD);
        type(calculator, "20");
        press(calculator, Command.RECIPROCAL, Command.SUBTRACT);
        assertEquals("50.05", calculator.primaryDisplay());
        assertEquals("50 + 1/(20) -", calculator.expression());
    }

    @Test
    void powerAndRoot() {
        final CalculatorManager power = calculator();
        type(power, "5");
        press(power, Command.POWER);
        type(power, "2");
        press(power, Command.ADD);
        assertEquals("25", power.primaryDisplay());
        assertEquals("5 ^ 2 +", power.expression());

        final CalculatorManager root = calculator();
        type(root, "8");
        press(root, Command.ROOT);
        type(root, "3");
        press(root, Command.MULTIPLY);
        assertEquals("2", root.primaryDisplay());

        final CalculatorManager negativeRoot = calculator();
        type(negativeRoot, "27");
        press(negativeRoot, Command.NEGATE, Command.ROOT);
        type(negativeRoot, "3");
        press(negativeRoot, Command.ADD);
        assertEquals("-3", negativeRoot.primaryDisplay());
    }

    @Test
    void zeroToTheZeroPowerIsOne() {
        final CalculatorManager calculator = calculator();
        type(calculator, "0");
        press(calculator, Command.POWER);
        type(calculator, "0");
        press(calculator, Command.ADD);
        assertEquals("1", calculator.primaryDisplay());
    }

    @Test
    void cubesAndCubeRoots() {
        final CalculatorManager calculator = calculator();
        type(calculator, "8");
        press(calculator, Command.CUBE, Command.CUBEROOT);
        assertEquals("8", calculator.primaryDisplay());
    }

    @Test
    void logarithmsAndConstants() {
        final CalculatorManager calculator = calculator();
        type(calculator, "10");
        press(calculator, Command.LN);
        assertEquals("2.3025850929940456840179914546844", calculator.primaryDisplay());

        final CalculatorManager log = calculator();
        type(log, "10");
        press(log, Command.LOG);
        assertEquals("1", log.primaryDisplay());

        final CalculatorManager powerOfTen = calculator();
        type(powerOfTen, "5");
        press(powerOfTen, Command.POW10);
        assertEquals("100,000", powerOfTen.primaryDisplay());
    }

    @Test
    void trigonometricFunctionsAndInverse() {
        final CalculatorManager calculator = calculator();
        calculator.sendCommand(Command.INVERSE);
        type(calculator, "1");
        press(calculator, Command.SIN);
        assertEquals("90", calculator.primaryDisplay());

        final CalculatorManager sine = calculator();
        type(sine, "90");
        press(sine, Command.SIN);
        assertEquals("1", sine.primaryDisplay());

        final CalculatorManager arcTangent = calculator();
        type(arcTangent, "1");
        press(arcTangent, Command.ATAN);
        assertEquals("45", arcTangent.primaryDisplay());
    }

    @Test
    void radiansAndGradians() {
        final CalculatorManager radians = calculator();
        press(radians, Command.ANGLE_RADIANS, Command.PI, Command.SIN);
        assertNumeric("0", radians.primaryDisplay());
        radians.sendCommand(Command.CLEAR);
        press(radians, Command.PI, Command.COS);
        assertEquals("-1", radians.primaryDisplay());

        final CalculatorManager gradians = calculator();
        press(gradians, Command.ANGLE_GRADIANS);
        type(gradians, "400");
        press(gradians, Command.SIN);
        assertNumeric("0", gradians.primaryDisplay());
        gradians.sendCommand(Command.CLEAR);
        type(gradians, "400");
        press(gradians, Command.COS);
        assertEquals("1", gradians.primaryDisplay());
    }

    @Test
    void factorials() {
        final CalculatorManager calculator = calculator();
        type(calculator, "5");
        press(calculator, Command.FACTORIAL);
        assertEquals("120", calculator.primaryDisplay());

        final CalculatorManager invalid = calculator();
        type(invalid, "3");
        press(invalid, Command.DECIMAL_POINT);
        type(invalid, "5");
        press(invalid, Command.FACTORIAL);
        assertEquals("Invalid input", invalid.primaryDisplay());
    }

    @Test
    void floorAndCeiling() {
        final CalculatorManager floor = calculator();
        type(floor, "3.8");
        floor.sendCommand(Command.FLOOR);
        assertEquals("3", floor.primaryDisplay());

        final CalculatorManager ceiling = calculator();
        type(ceiling, "3.8");
        ceiling.sendCommand(Command.CEIL);
        assertEquals("4", ceiling.primaryDisplay());
    }

    @Test
    void parenthesesAndImplicitMultiplication() {
        final CalculatorManager calculator = calculator();
        press(calculator, Command.OPEN_PAREN, Command.EIGHT, Command.CLOSE_PAREN, Command.TWO, Command.EQUALS);
        assertEquals("16", calculator.primaryDisplay());

        final CalculatorManager sum = calculator();
        type(sum, "1");
        press(sum, Command.ADD, Command.OPEN_PAREN, Command.ADD, Command.THREE, Command.CLOSE_PAREN);
        assertEquals("3", sum.primaryDisplay());
    }

    @Test
    void exponentialInputIsAccepted() {
        final CalculatorManager calculator = calculator();
        type(calculator, "1");
        press(calculator, Command.EXPONENT, Command.NEGATE);
        type(calculator, "3");
        press(calculator, Command.EQUALS);
        assertEquals("0.001", calculator.primaryDisplay());
    }

    @Test
    void angleTypeIsStoredOnTheEngine() {
        final CalculatorManager calculator = calculator();
        press(calculator, Command.ANGLE_GRADIANS);
        assertEquals(AngleType.GRADIANS, calculator.engine().angleType());
        press(calculator, Command.ANGLE_RADIANS);
        assertEquals(AngleType.RADIANS, calculator.engine().angleType());
    }
}
