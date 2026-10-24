package dev.jcalculator.date;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DateCalculationEngineTest {

    private final DateCalculationEngine engine = new DateCalculationEngine();

    @Test
    void sameDateHasNoDifference() {
        final LocalDate date = LocalDate.of(2024, 2, 29);
        assertEquals(new DateDifference(0, 0, 0, 0), engine.tryGetDateDifference(date, date, DateUnit.ALL));
    }

    @Test
    void differenceBreaksDownIntoYearsMonthsWeeksAndDays() {
        final DateDifference difference = engine.tryGetDateDifference(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), DateUnit.ALL);
        assertEquals(new DateDifference(0, 11, 4, 2), difference);
    }

    @Test
    void dayOnlyFormatCountsEveryDay() {
        final DateDifference difference = engine.tryGetDateDifference(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), DateUnit.DAY.flag());
        assertEquals(new DateDifference(0, 0, 0, 365), difference);
    }

    @Test
    void weekFormatUsesWholeWeeks() {
        final DateDifference difference = engine.tryGetDateDifference(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 15), DateUnit.mask(DateUnit.WEEK, DateUnit.DAY));
        assertEquals(new DateDifference(0, 0, 2, 0), difference);
    }

    @Test
    void differenceIsSymmetric() {
        final LocalDate earlier = LocalDate.of(2023, 5, 17);
        final LocalDate later = LocalDate.of(2025, 8, 3);
        assertEquals(engine.tryGetDateDifference(earlier, later, DateUnit.ALL),
                engine.tryGetDateDifference(later, earlier, DateUnit.ALL));
    }

    @Test
    void addingOneYearFromLeapDayLandsOnTheLastOfFebruary() {
        assertEquals(LocalDate.of(2021, 2, 28),
                engine.addDuration(LocalDate.of(2020, 2, 29), new DateDifference(1, 0, 0, 0)));
    }

    @Test
    void addingOneMonthFromTheEndOfJanuaryLandsOnTheEndOfFebruary() {
        assertEquals(LocalDate.of(2024, 2, 29),
                engine.addDuration(LocalDate.of(2024, 1, 31), new DateDifference(0, 1, 0, 0)));
    }

    @Test
    void addingAppliesYearsThenMonthsThenDays() {
        assertEquals(LocalDate.of(2025, 3, 3),
                engine.addDuration(LocalDate.of(2024, 2, 1), new DateDifference(1, 1, 0, 2)));
    }

    @Test
    void subtractingAppliesDaysThenMonthsThenYears() {
        assertEquals(LocalDate.of(2024, 2, 1),
                engine.subtractDuration(LocalDate.of(2024, 3, 1), new DateDifference(0, 1, 0, 0)));
    }

    @Test
    void datesOutsideTheSupportedRangeAreRejected() {
        assertNull(engine.subtractDuration(LocalDate.of(1601, 1, 1), new DateDifference(0, 0, 0, 1)));
        assertNull(engine.addDuration(LocalDate.of(9999, 12, 31), new DateDifference(0, 0, 0, 1)));
        assertEquals(LocalDate.of(1601, 1, 1),
                engine.addDuration(LocalDate.of(1601, 1, 1), new DateDifference(0, 0, 0, 0)));
    }

    @Test
    void zeroDurationIsTheIdentity() {
        final LocalDate date = LocalDate.of(2024, 6, 15);
        assertEquals(date, engine.addDuration(date, new DateDifference(0, 0, 0, 0)));
        assertEquals(date, engine.subtractDuration(date, new DateDifference(0, 0, 0, 0)));
    }

    @Test
    void aDifferenceIsDaysOnlyWhenItCarriesNoLargerUnit() {
        assertTrue(new DateDifference(0, 0, 0, 5).isDaysOnly());
        assertTrue(new DateDifference(0, 0, 0, 0).isDaysOnly());
        assertFalse(new DateDifference(1, 0, 0, 5).isDaysOnly());
        assertFalse(new DateDifference(0, 2, 0, 5).isDaysOnly());
        assertFalse(new DateDifference(0, 0, 3, 5).isDaysOnly());
    }
}
