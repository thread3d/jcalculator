package dev.jcalculator.app;

import dev.jcalculator.engine.CalculatorMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What may be pasted on each page, and the buttons an accepted text becomes. */
class ClipboardTextTest {

    @Test
    void eachPageAcceptsItsOwnAlphabet() {
        assertTrue(ClipboardText.alphabet(CalculatorMode.STANDARD).contains("*"));
        assertFalse(ClipboardText.alphabet(CalculatorMode.STANDARD).contains("("));
        assertTrue(ClipboardText.alphabet(CalculatorMode.SCIENTIFIC).contains("("));
        assertTrue(ClipboardText.alphabet(CalculatorMode.PROGRAMMER).contains("a"));
        assertFalse(ClipboardText.alphabet(CalculatorMode.SCIENTIFIC).contains("a"));
    }

    @Test
    void aNumberPastesOnEveryPage() {
        assertTrue(ClipboardText.accepts("1234", CalculatorMode.STANDARD, 10));
        assertTrue(ClipboardText.accepts("-12.5", CalculatorMode.SCIENTIFIC, 10));
        assertTrue(ClipboardText.accepts("1e5", CalculatorMode.STANDARD, 10));
        assertTrue(ClipboardText.accepts("  1 2  ", CalculatorMode.STANDARD, 10));
    }

    @Test
    void anOperatorThePageDoesNotHaveIsRefused() {
        assertTrue(ClipboardText.accepts("2*(3+4)", CalculatorMode.SCIENTIFIC, 10));
        assertFalse(ClipboardText.accepts("2*(3+4)", CalculatorMode.STANDARD, 10));
        assertTrue(ClipboardText.accepts("2*3", CalculatorMode.STANDARD, 10));
        assertFalse(ClipboardText.accepts("2^3", CalculatorMode.STANDARD, 10));
        assertTrue(ClipboardText.accepts("2^3", CalculatorMode.SCIENTIFIC, 10));
        assertFalse(ClipboardText.accepts("sin(1)", CalculatorMode.SCIENTIFIC, 10));
        assertFalse(ClipboardText.accepts("1&2", CalculatorMode.STANDARD, 10));
    }

    @Test
    void theProgrammerPageIsBoundByItsBase() {
        assertTrue(ClipboardText.accepts("FF", CalculatorMode.PROGRAMMER, 16));
        assertFalse(ClipboardText.accepts("FF", CalculatorMode.PROGRAMMER, 10));
        assertTrue(ClipboardText.accepts("101", CalculatorMode.PROGRAMMER, 2));
        assertFalse(ClipboardText.accepts("102", CalculatorMode.PROGRAMMER, 2));
        assertFalse(ClipboardText.accepts("19", CalculatorMode.PROGRAMMER, 8));
    }

    @Test
    void nothingIsAcceptedFromAnEmptyClipboard() {
        assertFalse(ClipboardText.accepts(null, CalculatorMode.STANDARD, 10));
        assertFalse(ClipboardText.accepts("", CalculatorMode.STANDARD, 10));
        assertFalse(ClipboardText.accepts("   ", CalculatorMode.STANDARD, 10));
    }

    @Test
    void anAcceptedTextBecomesButtonTokens() {
        assertEquals(List.of("1", "+", "2"), ClipboardText.tokens("1+2", CalculatorMode.STANDARD, 10));
        assertEquals(List.of("1", "2"), ClipboardText.tokens(" 1 2 ", CalculatorMode.STANDARD, 10));
        assertEquals(List.of("1", "exp", "5"), ClipboardText.tokens("1e5", CalculatorMode.SCIENTIFIC, 10));
        assertEquals(List.of("a", "f"), ClipboardText.tokens("AF", CalculatorMode.PROGRAMMER, 16));
    }

    @Test
    void aRefusedTextIsNotTurnedIntoTokens() {
        assertThrows(IllegalArgumentException.class,
                () -> ClipboardText.tokens("sin(1)", CalculatorMode.SCIENTIFIC, 10));
    }

    @Test
    void everyTokenMustBeAButtonOnThePage() {
        assertTrue(ClipboardText.everyTokenIsAButton(List.of("1", "+", "2"), CalculatorMode.STANDARD));
        assertFalse(ClipboardText.everyTokenIsAButton(List.of("1", "nonsense"), CalculatorMode.STANDARD));
    }
}
