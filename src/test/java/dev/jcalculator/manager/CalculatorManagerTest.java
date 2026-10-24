package dev.jcalculator.manager;

import dev.jcalculator.core.Rational;
import dev.jcalculator.engine.CalculatorMode;
import dev.jcalculator.engine.Command;
import dev.jcalculator.engine.HistoryEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculatorManagerTest {

    static void type(CalculatorManager calculator, String text) {
        for (char c : text.toCharArray()) {
            if (c == '.') {
                calculator.sendCommand(Command.DECIMAL_POINT);
            } else {
                calculator.sendCommand(dev.jcalculator.engine.StandardCalculatorTest.digit(c));
            }
        }
    }

    @Test
    void switchingModesClearsTheDisplay() {
        final CalculatorManager calculator = new CalculatorManager();
        type(calculator, "123");
        assertEquals("123", calculator.primaryDisplay());
        calculator.setMode(CalculatorMode.SCIENTIFIC);
        assertEquals("0", calculator.primaryDisplay());
        type(calculator, "123");
        calculator.setMode(CalculatorMode.PROGRAMMER);
        assertEquals("0", calculator.primaryDisplay());
        type(calculator, "123");
        calculator.setMode(CalculatorMode.SCIENTIFIC);
        assertEquals("0", calculator.primaryDisplay());
        type(calculator, "67");
        calculator.sendCommand(Command.ADD);
        assertEquals("67", calculator.primaryDisplay());
        assertEquals("67 +", calculator.expression());
        calculator.setMode(CalculatorMode.STANDARD);
        assertEquals("0", calculator.primaryDisplay());
    }

    @Test
    void historyBelongsToTheModeItWasProducedIn() {
        final CalculatorManager calculator = new CalculatorManager();
        type(calculator, "2");
        calculator.sendCommand(Command.ADD);
        type(calculator, "3");
        calculator.sendCommand(Command.EQUALS);

        calculator.setMode(CalculatorMode.SCIENTIFIC);
        type(calculator, "4");
        calculator.sendCommand(Command.MULTIPLY);
        type(calculator, "5");
        calculator.sendCommand(Command.EQUALS);

        final List<HistoryEntry> scientific = calculator.history(CalculatorMode.SCIENTIFIC);
        final List<HistoryEntry> standard = calculator.history(CalculatorMode.STANDARD);
        assertEquals(1, scientific.size());
        assertEquals("4 × 5=", scientific.get(0).expression());
        assertEquals("20", scientific.get(0).result());
        assertEquals(1, standard.size());
        assertEquals("2 + 3=", standard.get(0).expression());
    }

    @Test
    void historyIsCappedAtTwentyEntries() {
        final CalculatorManager calculator = new CalculatorManager();
        for (int i = 0; i < 25; i++) {
            type(calculator, "1");
            calculator.sendCommand(Command.ADD);
            type(calculator, "1");
            calculator.sendCommand(Command.EQUALS);
            calculator.sendCommand(Command.CLEAR);
        }
        assertEquals(CalculatorManager.MAX_HISTORY_SIZE, calculator.history().size());
    }

    @Test
    void memoryStoresRecallsAndAdjusts() {
        final CalculatorManager calculator = new CalculatorManager();
        type(calculator, "5");
        calculator.sendCommand(Command.MEMORY_STORE);
        assertEquals(List.of(Rational.of(5)), calculator.memorizedNumbers());

        calculator.sendCommand(Command.CLEAR);
        calculator.sendCommand(Command.MEMORY_RECALL);
        assertEquals("5", calculator.primaryDisplay());

        calculator.sendCommand(Command.MEMORY_ADD);
        assertEquals(List.of(Rational.of(10)), calculator.memorizedNumbers());

        calculator.sendCommand(Command.MEMORY_SUBTRACT);
        assertEquals(List.of(Rational.of(5)), calculator.memorizedNumbers());

        calculator.sendCommand(Command.MEMORY_CLEAR);
        assertTrue(calculator.memorizedNumbers().isEmpty());
    }

    @Test
    void memoryHoldsSeveralSlotsNewestFirst() {
        final CalculatorManager calculator = new CalculatorManager();
        type(calculator, "5");
        calculator.sendCommand(Command.MEMORY_STORE);
        calculator.sendCommand(Command.CLEAR);
        type(calculator, "7");
        calculator.sendCommand(Command.MEMORY_STORE);
        assertEquals(List.of(Rational.of(7), Rational.of(5)), calculator.memorizedNumbers());

        calculator.recallMemory(1);
        assertEquals("5", calculator.primaryDisplay());
        assertEquals(CalculatorManager.SIGNAL_INPUT_CHANGED, calculator.lastSignal());
    }

    @Test
    void resetClearsEverything() {        final CalculatorManager calculator = new CalculatorManager();
        type(calculator, "5");
        calculator.sendCommand(Command.MEMORY_STORE);
        calculator.sendCommand(Command.CLEAR);
        calculator.sendCommand(Command.CLEAR);
        type(calculator, "2");
        calculator.sendCommand(Command.ADD);
        calculator.sendCommand(Command.EQUALS);
        calculator.reset();
        assertEquals("0", calculator.primaryDisplay());
        assertTrue(calculator.history().isEmpty());
        assertTrue(calculator.memorizedNumbers().isEmpty());
    }

    @Test
    void memorySlotsAreAdjustedIndividually() {
        final CalculatorManager calculator = new CalculatorManager();
        type(calculator, "5");
        calculator.sendCommand(Command.MEMORY_STORE);
        calculator.sendCommand(Command.CLEAR);
        type(calculator, "7");
        calculator.sendCommand(Command.MEMORY_STORE);
        calculator.sendCommand(Command.CLEAR);
        type(calculator, "2");
        assertEquals(List.of(Rational.of(7), Rational.of(5)), calculator.memorizedNumbers());

        calculator.memorizedNumberAdd(1);
        assertEquals(List.of(Rational.of(7), Rational.of(7)), calculator.memorizedNumbers());

        calculator.memorizedNumberSubtract(1);
        assertEquals(List.of(Rational.of(7), Rational.of(5)), calculator.memorizedNumbers());

        calculator.memorizedNumberClear(0);
        assertEquals(List.of(Rational.of(5)), calculator.memorizedNumbers());
    }

    @Test
    void everyMemoryChangeRaisesTheMemorySignal() {
        final CalculatorManager calculator = new CalculatorManager();
        type(calculator, "5");
        calculator.sendCommand(Command.MEMORY_STORE);
        assertEquals(CalculatorManager.SIGNAL_MEMORY_CHANGED, calculator.lastSignal());

        calculator.sendCommand(Command.MULTIPLY);
        assertEquals(CalculatorManager.SIGNAL_BINARY_OPERATOR, calculator.lastSignal());

        calculator.memorizedNumberAdd(0);
        assertEquals(CalculatorManager.SIGNAL_MEMORY_CHANGED, calculator.lastSignal());

        calculator.sendCommand(Command.MULTIPLY);
        calculator.memorizedNumberClear(0);
        assertEquals(CalculatorManager.SIGNAL_MEMORY_CHANGED, calculator.lastSignal());
    }

    @Test
    void memoryAddWithNothingMemorizedStoresTheDisplayedValue() {
        final CalculatorManager calculator = new CalculatorManager();
        type(calculator, "4");
        calculator.memorizedNumberAdd(0);
        assertEquals(List.of(Rational.of(4)), calculator.memorizedNumbers());
    }

    @Test
    void memorySubtractWithNothingMemorizedStoresTheNegatedValue() {
        final CalculatorManager calculator = new CalculatorManager();
        type(calculator, "4");
        calculator.memorizedNumberSubtract(0);
        assertEquals(List.of(Rational.of(-4)), calculator.memorizedNumbers());
    }

    @Test
    void clearingASlotThatDoesNotExistIsHarmless() {
        final CalculatorManager calculator = new CalculatorManager();
        type(calculator, "5");
        calculator.sendCommand(Command.MEMORY_STORE);

        calculator.memorizedNumberClear(3);
        assertEquals(List.of(Rational.of(5)), calculator.memorizedNumbers());
    }
}
