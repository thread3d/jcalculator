package dev.jcalculator.harness;

import dev.jcalculator.engine.CalculatorMode;
import dev.jcalculator.engine.Command;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokensTest {

    @Test
    void digitsAndNamedKeysResolveOnEveryPage() {
        for (CalculatorMode mode : CalculatorMode.values()) {
            assertEquals(Command.SEVEN, Tokens.require("7", mode));
            assertEquals(Command.ADD, Tokens.require("+", mode));
            assertEquals(Command.SQRT, Tokens.require("sqrt", mode));
        }
    }

    @Test
    void eIsEulersConstantOutsideTheProgrammerPage() {
        assertEquals(Command.EULER, Tokens.commandFor("e"));
        for (CalculatorMode mode : List.of(CalculatorMode.STANDARD, CalculatorMode.SCIENTIFIC)) {
            assertEquals(Command.EULER, Tokens.require("e", mode));
            assertEquals(Command.EULER, Tokens.require("E", mode));
            assertEquals(Command.EULER, Tokens.require("euler", mode));
        }
    }

    @Test
    void eIsHexadecimalEOnTheProgrammerPage() {
        assertEquals(Command.E, Tokens.require("e", CalculatorMode.PROGRAMMER));
        assertEquals(Command.E, Tokens.require("E", CalculatorMode.PROGRAMMER));
    }

    @Test
    void cClearsOutsideTheProgrammerPageAndIsHexadecimalCInside() {
        assertEquals(Command.CLEAR, Tokens.require("c", CalculatorMode.STANDARD));
        assertEquals(Command.CLEAR, Tokens.require("c", CalculatorMode.SCIENTIFIC));
        assertEquals(Command.C, Tokens.require("c", CalculatorMode.PROGRAMMER));
    }

    @Test
    void theOtherHexLettersAreButtonsOnlyOnTheProgrammerPage() {
        final char[] letters = {'a', 'b', 'd', 'f'};
        for (CalculatorMode mode : List.of(CalculatorMode.STANDARD, CalculatorMode.SCIENTIFIC)) {
            for (char letter : letters) {
                assertNull(Tokens.commandFor(String.valueOf(letter), mode),
                        letter + " must not be a button on the " + mode + " page");
                final UnknownTokenException refusal = assertThrows(UnknownTokenException.class,
                        () -> Tokens.require(String.valueOf(letter), mode));
                assertEquals(String.valueOf(letter), refusal.token());
                assertTrue(refusal.getMessage().contains("programmer page"), refusal.getMessage());
            }
        }
        assertEquals(Command.A, Tokens.require("a", CalculatorMode.PROGRAMMER));
        assertEquals(Command.B, Tokens.require("b", CalculatorMode.PROGRAMMER));
        assertEquals(Command.D, Tokens.require("d", CalculatorMode.PROGRAMMER));
        assertEquals(Command.F, Tokens.require("f", CalculatorMode.PROGRAMMER));
    }

    @Test
    void theModeBlindFormReadsTheStandardPage() {
        assertEquals(Command.CLEAR, Tokens.commandFor("c"));
        assertEquals(Command.EULER, Tokens.commandFor("e"));
        assertNull(Tokens.commandFor("a"));
        assertEquals(Command.DECIMAL_POINT, Tokens.commandFor("."));
        assertNull(Tokens.commandFor("bogus"));
    }

    @Test
    void aRefusalNamesThePageAndTheToken() {
        final UnknownTokenException refusal = assertThrows(UnknownTokenException.class,
                () -> Tokens.require("bogus", CalculatorMode.SCIENTIFIC));
        assertEquals("bogus", refusal.token());
        assertTrue(refusal.getMessage().contains("SCIENTIFIC"), refusal.getMessage());
    }

    @Test
    void capabilitiesListTheWholeVocabularyOnce() {
        final List<String> tokens = Tokens.supportedTokens();
        assertTrue(tokens.contains("e"));
        assertTrue(tokens.contains("euler"));
        assertTrue(tokens.contains("pi"));
        for (char letter = 'a'; letter <= 'f'; letter++) {
            assertTrue(tokens.contains(String.valueOf(letter)), String.valueOf(letter));
        }
        assertEquals(tokens.size(), tokens.stream().distinct().count());
        assertEquals(tokens.stream().sorted().toList(), tokens);
    }
}
