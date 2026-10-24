package dev.jcalculator.harness;

import dev.jcalculator.engine.CalculatorMode;
import dev.jcalculator.engine.Command;
import dev.jcalculator.engine.HistoryEntry;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculatorSessionTest {

    @Test
    void pressesTokensInOrder() {
        final CalculatorSession session = new CalculatorSession(CalculatorMode.STANDARD);
        session.pressAll(List.of("2", "+", "3", "*", "4", "="));
        assertEquals("20", session.state().display());
        assertFalse(session.state().error());
    }

    @Test
    void scientificModeHonoursPrecedence() {
        final CalculatorSession session = new CalculatorSession(CalculatorMode.SCIENTIFIC);
        session.pressExpression("2 + 3 * 4 =");
        assertEquals("14", session.state().display());
    }

    @Test
    void seededRandomRepeats() {
        final CalculatorSession first = new CalculatorSession(CalculatorMode.SCIENTIFIC, 42L);
        final CalculatorSession second = new CalculatorSession(CalculatorMode.SCIENTIFIC, 42L);
        final CalculatorSession other = new CalculatorSession(CalculatorMode.SCIENTIFIC, 43L);
        first.press("rand");
        second.press("rand");
        other.press("rand");
        assertEquals(first.state().display(), second.state().display());
        assertFalse(first.state().display().equals(other.state().display()));
    }

    @Test
    void clearAliasFollowsTheActivePage() {
        final CalculatorSession standard = new CalculatorSession(CalculatorMode.STANDARD);
        standard.press("7");
        standard.press("c");
        assertEquals("0", standard.state().display());

        final CalculatorSession programmer = new CalculatorSession(CalculatorMode.PROGRAMMER);
        programmer.press("hex");
        programmer.press("c");
        assertEquals("C", programmer.state().display());
    }

    @Test
    void unknownTokensAreReportedNotIgnored() {
        final CalculatorSession session = new CalculatorSession();
        assertThrows(UnknownTokenException.class, () -> session.press("bogus"));
        assertEquals("bogus", assertThrows(UnknownTokenException.class, () -> session.press("bogus")).token());
    }

    @Test
    void divisionByZeroIsAnErrorState() {
        final CalculatorSession session = new CalculatorSession();
        session.pressExpression("1 / 0 =");
        assertTrue(session.state().error());
        assertEquals("Cannot divide by zero", session.state().display());
        assertEquals("Cannot divide by zero", session.state().errorMessage());
    }

    @Test
    void convertsUnits() {
        final CalculatorSession session = new CalculatorSession();
        assertEquals("8.2021", session.convert("length", "m", "ft", "2.5"));
        assertEquals("12", session.convert("length", "ft", "in", "1"));
    }

    @Test
    void computesDateDifferencesAndDurations() {
        final CalculatorSession session = new CalculatorSession();
        assertEquals("11 months 4 weeks 2 days",
                session.dateDifference(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)));
        assertEquals(LocalDate.of(2024, 2, 29),
                session.dateAdd(LocalDate.of(2024, 1, 31), 0, 1, 0, false));
        assertEquals(LocalDate.of(2021, 2, 28),
                session.dateAdd(LocalDate.of(2020, 2, 29), 1, 0, 0, false));
    }

    @Test
    void resetClearsTheSession() {
        final CalculatorSession session = new CalculatorSession();
        session.pressExpression("2 + 3 =");
        session.reset();
        assertEquals("0", session.state().display());
        assertTrue(session.state().history().isEmpty());
    }

    @Test
    void stateIsAnImmutableCopy() {
        final CalculatorSession session = new CalculatorSession();
        session.pressExpression("2 + 3 =");
        final CalculatorState snapshot = session.state();
        session.pressExpression("4 + 4 =");
        assertEquals("5", snapshot.display());
        assertEquals(1, snapshot.history().size());
        assertNotNull(snapshot.mode());
    }

    @Test
    void aHistoryEntryKeepsTheButtonsThatProducedIt() {
        final CalculatorSession session = new CalculatorSession(CalculatorMode.STANDARD, 0L);
        session.pressExpression("2 + 3 =");

        final HistoryEntry entry = session.state().history().get(0);
        assertTrue(entry.isReplayable());
        assertEquals(List.of(Command.TWO, Command.ADD, Command.THREE, Command.EQUALS),
                entry.commands());
    }

    @Test
    void aHistoryEntryCanBeReRun() {
        final CalculatorSession session = new CalculatorSession(CalculatorMode.STANDARD, 0L);
        session.pressExpression("2 + 3 =");
        final HistoryEntry entry = session.state().history().get(0);

        session.press("clear");
        assertEquals("0", session.state().display());

        session.replay(entry);
        assertEquals("5", session.state().display());
        assertEquals("2 + 3=", session.state().expression());
    }

    @Test
    void anEntryWithoutButtonsIsNotReplayableAndReplaysToNothing() {
        final CalculatorSession session = new CalculatorSession(CalculatorMode.STANDARD, 0L);
        final HistoryEntry bare = new HistoryEntry("9 + 1=", "10");

        assertFalse(bare.isReplayable());
        session.replay(bare);
        assertEquals("0", session.state().display());
    }
}
