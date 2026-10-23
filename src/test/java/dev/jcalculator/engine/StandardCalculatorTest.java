package dev.jcalculator.engine;

import dev.jcalculator.manager.CalculatorManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StandardCalculatorTest {

    static CalculatorManager calculator() {
        return new CalculatorManager(CalculatorMode.STANDARD);
    }

    static void type(CalculatorManager calculator, String text) {
        for (char c : text.toCharArray()) {
            if (c == '.') {
                calculator.sendCommand(Command.DECIMAL_POINT);
            } else {
                calculator.sendCommand(digit(c));
            }
        }
    }

    public static Command digit(char c) {
        return switch (c) {
            case '0' -> Command.ZERO;
            case '1' -> Command.ONE;
            case '2' -> Command.TWO;
            case '3' -> Command.THREE;
            case '4' -> Command.FOUR;
            case '5' -> Command.FIVE;
            case '6' -> Command.SIX;
            case '7' -> Command.SEVEN;
            case '8' -> Command.EIGHT;
            case '9' -> Command.NINE;
            default -> throw new IllegalArgumentException("not a digit: " + c);
        };
    }

    @Test
    void typingShowsExactlyWhatWasTyped() {
        final CalculatorManager calculator = calculator();
        type(calculator, "123.456");
        assertEquals("123.456", calculator.primaryDisplay());
        assertEquals("", calculator.expression());
    }

    @Test
    void groupsThousandsOnTheDisplay() {
        final CalculatorManager calculator = calculator();
        type(calculator, "1234567");
        assertEquals("1,234,567", calculator.primaryDisplay());
        assertEquals("1234567", calculator.rawDisplay());
    }

    @Test
    void evaluatesImmediatelyWithoutPrecedence() {
        final CalculatorManager calculator = calculator();
        type(calculator, "2");
        calculator.sendCommand(Command.ADD);
        type(calculator, "3");
        calculator.sendCommand(Command.MULTIPLY);
        type(calculator, "4");
        calculator.sendCommand(Command.EQUALS);
        assertEquals("20", calculator.primaryDisplay());
        assertEquals("5 × 4=", calculator.history().get(0).expression());
    }

    @Test
    void subtractionUsesTheSavedOperandFirst() {
        final CalculatorManager calculator = calculator();
        type(calculator, "3");
        calculator.sendCommand(Command.SUBTRACT);
        type(calculator, "6");
        calculator.sendCommand(Command.EQUALS);
        assertEquals("-3", calculator.primaryDisplay());
    }

    @Test
    void repeatedEqualsRepeatsTheLastOperation() {
        final CalculatorManager calculator = calculator();
        type(calculator, "1");
        calculator.sendCommand(Command.ADD);
        type(calculator, "2");
        calculator.sendCommand(Command.EQUALS);
        assertEquals("3", calculator.primaryDisplay());
        calculator.sendCommand(Command.EQUALS);
        assertEquals("5", calculator.primaryDisplay());
    }

    @Test
    void unaryFunctionAppliesToTheDisplayedValue() {
        final CalculatorManager calculator = calculator();
        type(calculator, "4");
        calculator.sendCommand(Command.SQRT);
        assertEquals("2", calculator.primaryDisplay());
        calculator.sendCommand(Command.SUBTRACT);
        type(calculator, "2");
        calculator.sendCommand(Command.ADD);
        assertEquals("0", calculator.primaryDisplay());
        assertEquals("0 +", calculator.expression());
    }

    @Test
    void reciprocalOfNegativeHundred() {
        final CalculatorManager calculator = calculator();
        type(calculator, "100");
        calculator.sendCommand(Command.NEGATE);
        calculator.sendCommand(Command.RECIPROCAL);
        assertEquals("-0.01", calculator.primaryDisplay());
        assertEquals("1/(-100)", calculator.expression());
    }

    @Test
    void percentUsesThePendingOperator() {
        final CalculatorManager addition = calculator();
        type(addition, "50");
        addition.sendCommand(Command.ADD);
        type(addition, "20");
        addition.sendCommand(Command.PERCENT);
        addition.sendCommand(Command.EQUALS);
        assertEquals("60", addition.primaryDisplay());

        final CalculatorManager multiplication = calculator();
        type(multiplication, "6");
        multiplication.sendCommand(Command.MULTIPLY);
        type(multiplication, "6");
        multiplication.sendCommand(Command.PERCENT);
        multiplication.sendCommand(Command.EQUALS);
        assertEquals("0.36", multiplication.primaryDisplay());
    }

    @Test
    void clearEntryKeepsThePendingOperation() {
        final CalculatorManager calculator = calculator();
        type(calculator, "12");
        calculator.sendCommand(Command.ADD);
        type(calculator, "5");
        calculator.sendCommand(Command.CLEAR_ENTRY);
        type(calculator, "2");
        calculator.sendCommand(Command.ADD);
        assertEquals("14", calculator.primaryDisplay());
        assertEquals("14 +", calculator.expression());
    }

    @Test
    void backspaceRemovesTheLastDigit() {
        final CalculatorManager calculator = calculator();
        type(calculator, "123");
        calculator.sendCommand(Command.BACKSPACE);
        calculator.sendCommand(Command.BACKSPACE);
        assertEquals("1", calculator.primaryDisplay());
    }

    @Test
    void divisionByZeroReportsTheRightError() {
        final CalculatorManager zeroOverZero = calculator();
        type(zeroOverZero, "0");
        zeroOverZero.sendCommand(Command.DIVIDE);
        type(zeroOverZero, "0");
        zeroOverZero.sendCommand(Command.EQUALS);
        assertEquals("Result is undefined", zeroOverZero.primaryDisplay());
        assertTrue(zeroOverZero.isError());

        final CalculatorManager oneOverZero = calculator();
        type(oneOverZero, "1");
        oneOverZero.sendCommand(Command.DIVIDE);
        type(oneOverZero, "0");
        oneOverZero.sendCommand(Command.EQUALS);
        assertEquals("Cannot divide by zero", oneOverZero.primaryDisplay());
        assertTrue(oneOverZero.isError());
    }

    @Test
    void errorStateFreezesUntilCleared() {
        final CalculatorManager calculator = calculator();
        type(calculator, "1");
        calculator.sendCommand(Command.DIVIDE);
        type(calculator, "0");
        calculator.sendCommand(Command.EQUALS);
        type(calculator, "9");
        assertEquals("Cannot divide by zero", calculator.primaryDisplay());
        calculator.sendCommand(Command.CLEAR);
        assertFalse(calculator.isError());
        assertEquals("0", calculator.primaryDisplay());
    }

    @Test
    void clearResetsTheWholeExpression() {
        final CalculatorManager calculator = calculator();
        type(calculator, "1234");
        calculator.sendCommand(Command.CLEAR);
        assertEquals("0", calculator.primaryDisplay());
        assertEquals("", calculator.expression());
    }

    @Test
    void enteringTheSeventeenthDigitIsRefused() {
        final CalculatorManager calculator = calculator();
        type(calculator, "12345678901234567");
        assertEquals("1234567890123456", calculator.rawDisplay());
    }

    @Test
    void historyKeepsCompletedEquations() {
        final CalculatorManager calculator = calculator();
        type(calculator, "2");
        calculator.sendCommand(Command.ADD);
        type(calculator, "3");
        calculator.sendCommand(Command.EQUALS);
        final List<HistoryEntry> history = calculator.history();
        assertEquals(1, history.size());
        assertEquals("2 + 3=", history.get(0).expression());
        assertEquals("5", history.get(0).result());
    }
}
