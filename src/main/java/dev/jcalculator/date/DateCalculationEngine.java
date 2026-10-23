package dev.jcalculator.date;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Calendar arithmetic for the date calculator: the difference between two dates, and adding or
 * subtracting a years/months/days duration.
 *
 * <p>The arithmetic is calendar-aware rather than a fixed number of days per month or year, because
 * that is what makes "31 January plus one month" land on the end of February instead of 3 March. The
 * supported date range is the original's: 1 January 1601 to 31 December 9999.
 */
public final class DateCalculationEngine {

    public static final LocalDate MIN_SUPPORTED_DATE = LocalDate.of(1601, 1, 1);
    public static final LocalDate MAX_SUPPORTED_DATE = LocalDate.of(9999, 12, 31);

    private final String calendarIdentifier;

    public DateCalculationEngine() {
        this("GregorianCalendar");
    }

    public DateCalculationEngine(String calendarIdentifier) {
        this.calendarIdentifier = calendarIdentifier == null ? "GregorianCalendar" : calendarIdentifier;
    }

    public String calendarIdentifier() {
        return calendarIdentifier;
    }

    /** Adds years, then months, then days; returns {@code null} when the result is out of range. */
    public LocalDate addDuration(LocalDate startDate, DateDifference duration) {
        if (startDate == null || duration == null) {
            return null;
        }
        try {
            LocalDate result = startDate;
            if (duration.year() != 0) {
                result = result.plusYears(duration.year());
            }
            if (duration.month() != 0) {
                result = result.plusMonths(duration.month());
            }
            if (duration.day() != 0) {
                result = result.plusDays(duration.day());
            }
            return inRange(result) ? result : null;
        } catch (DateTimeException exception) {
            return null;
        }
    }

    /**
     * Subtracts days first, then months, then years - the original's order for subtraction, which is
     * not the same as adding a negated duration.
     */
    public LocalDate subtractDuration(LocalDate startDate, DateDifference duration) {
        if (startDate == null || duration == null) {
            return null;
        }
        try {
            LocalDate result = startDate;
            if (duration.day() != 0) {
                result = result.minusDays(duration.day());
            }
            if (duration.month() != 0) {
                result = result.minusMonths(duration.month());
            }
            if (duration.year() != 0) {
                result = result.minusYears(duration.year());
            }
            return inRange(result) ? result : null;
        } catch (DateTimeException exception) {
            return null;
        }
    }

    /**
     * Difference from {@code date1} to {@code date2}, always non-negative.
     *
     * <p>The units selected by {@code outputFormat} are filled greedily from largest to smallest:
     * whole years first, then whole months, then whole weeks; days are always the remainder. A
     * day-only format therefore returns the total number of days.
     */
    public DateDifference tryGetDateDifference(LocalDate date1, LocalDate date2, int outputFormat) {
        if (date1 == null || date2 == null) {
            return null;
        }
        final LocalDate start = date1.isBefore(date2) ? date1 : date2;
        final LocalDate end = date1.isBefore(date2) ? date2 : date1;

        LocalDate pivot = start;
        int years = 0;
        int months = 0;
        int weeks = 0;

        if ((outputFormat & DateUnit.YEAR.flag()) != 0) {
            years = wholeUnitsBetween(pivot, end, ChronoUnit.YEARS);
            pivot = pivot.plusYears(years);
        }
        if ((outputFormat & DateUnit.MONTH.flag()) != 0) {
            months = wholeUnitsBetween(pivot, end, ChronoUnit.MONTHS);
            pivot = pivot.plusMonths(months);
        }
        if ((outputFormat & DateUnit.WEEK.flag()) != 0) {
            final long remainingDays = ChronoUnit.DAYS.between(pivot, end);
            weeks = Math.toIntExact((remainingDays / 7));
            pivot = pivot.plusWeeks(weeks);
        }
        final int days = Math.toIntExact(ChronoUnit.DAYS.between(pivot, end));
        if (days < 0) {
            return null;
        }
        return new DateDifference(years, months, weeks, days);
    }

    /**
     * Largest {@code n} such that {@code start.plus(n, unit) <= end}. Computed from an estimate and
     * then corrected, so month lengths and leap years are respected without assuming a fixed length.
     */
    private static int wholeUnitsBetween(LocalDate start, LocalDate end, ChronoUnit unit) {
        if (start.plus(1, unit).isAfter(end)) {
            return 0;
        }
        long estimate;
        if (unit == ChronoUnit.YEARS) {
            estimate = ChronoUnit.DAYS.between(start, end) / 365;
        } else {
            estimate = ChronoUnit.DAYS.between(start, end) / 28;
        }
        int candidate = Math.toIntExact(Math.max(0, estimate));
        while (start.plus(candidate + 1L, unit).compareTo(end) <= 0) {
            candidate++;
        }
        while (candidate > 0 && start.plus(candidate, unit).isAfter(end)) {
            candidate--;
        }
        return candidate;
    }

    private static boolean inRange(LocalDate date) {
        return !date.isBefore(MIN_SUPPORTED_DATE) && !date.isAfter(MAX_SUPPORTED_DATE);
    }
}
