package dev.jcalculator.date;

/**
 * A calendar difference: whole years, months and weeks, plus the remaining days.
 *
 * @param year  whole years
 * @param month whole months
 * @param week  whole weeks
 * @param day   remaining days
 */
public record DateDifference(int year, int month, int week, int day) {

    /** True when the difference is expressed only in days, as the UI's "difference in days" line. */
    public boolean isDaysOnly() {
        return year == 0 && month == 0 && week == 0;
    }
}
