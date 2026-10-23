package dev.jcalculator.app;

import dev.jcalculator.engine.CalculatorMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutomationSurfaceTest {

    @Test
    void standardPageEvaluatesImmediately() {
        final AutomationSurface surface = new AutomationSurface(CalculatorMode.STANDARD);
        surface.click("2");
        surface.click("+");
        surface.click("3");
        surface.click("×");
        surface.click("4");
        surface.click("=");
        assertEquals("20", surface.snapshot().display());
    }

    @Test
    void scientificPageHonoursPrecedence() {
        final AutomationSurface surface = new AutomationSurface(CalculatorMode.SCIENTIFIC);
        surface.click("2");
        surface.click("+");
        surface.click("3");
        surface.click("×");
        surface.click("4");
        surface.click("=");
        assertEquals("14", surface.snapshot().display());
        assertEquals(CalculatorMode.SCIENTIFIC, surface.snapshot().mode());
    }

    @Test
    void programmerPageHasBitwiseKeys() {
        final AutomationSurface surface = new AutomationSurface(CalculatorMode.PROGRAMMER);
        final List<String> labels = surface.keypadKeys().stream().map(AutomationSurface.Key::label).toList();
        assertTrue(labels.contains("AND"));
        assertTrue(labels.contains("Lsh"));
        surface.click("5");
        surface.click("Lsh");
        surface.click("1");
        surface.click("=");
        assertEquals("10", surface.snapshot().display());
    }

    @Test
    void labelsBelongToTheActivePage() {
        final AutomationSurface surface = new AutomationSurface(CalculatorMode.STANDARD);
        assertThrows(IllegalArgumentException.class, () -> surface.click("sin"));
        surface.setMode(CalculatorMode.SCIENTIFIC);
        surface.click("sin");
        assertEquals("0", surface.snapshot().display());
        assertThrows(IllegalArgumentException.class, () -> surface.click("AND"));
    }

    @Test
    void historyIsAnApplicationActionNotACommand() {
        final AutomationSurface surface = new AutomationSurface(CalculatorMode.STANDARD);
        final AutomationSurface.Key history = surface.allKeys().stream()
                .filter(key -> "HIST".equals(key.label()))
                .findFirst()
                .orElseThrow();
        assertFalse(history.isCommand());
        assertThrows(IllegalArgumentException.class, () -> surface.click("HIST"));
    }

    @Test
    void typeEntersDigitsAndPoints() {
        final AutomationSurface surface = new AutomationSurface(CalculatorMode.STANDARD);
        surface.type("123.5");
        assertEquals("123.5", surface.snapshot().display());
        assertThrows(IllegalArgumentException.class, () -> surface.type("+"));
    }

    @Test
    void snapshotReflectsSettings() {
        final AutomationSurface surface = new AutomationSurface(CalculatorMode.PROGRAMMER);
        surface.pressToken("hex");
        surface.type("ff");
        final AutomationSurface.Snapshot snapshot = surface.snapshot();
        assertEquals(16, snapshot.radix());
        assertEquals("FF", snapshot.display());
        assertEquals("QWORD", snapshot.width().name());
    }

    @Test
    void errorsAreVisibleInTheSnapshot() {
        final AutomationSurface surface = new AutomationSurface(CalculatorMode.STANDARD);
        surface.click("1");
        surface.click("÷");
        surface.click("0");
        surface.click("=");
        assertTrue(surface.snapshot().error());
        assertEquals("Cannot divide by zero", surface.snapshot().display());
    }

    @Test
    void everyPageHasTheMemoryColumn() {
        final List<String> side = AutomationSurface.sideKeys().stream()
                .map(AutomationSurface.Key::label)
                .toList();
        assertEquals(List.of("MC", "MR", "M+", "M-", "MS"), side);
    }
}
