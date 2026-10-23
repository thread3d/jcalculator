package dev.jcalculator.engine;

/**
 * One completed calculation, as shown in the history list.
 *
 * @param expression the expression line, for example {@code "4 + 3="}
 * @param result     the formatted result
 */
public record HistoryEntry(String expression, String result) {
}
