package dev.jcalculator.engine;

import java.util.List;

/**
 * One completed calculation, as shown in the history list.
 *
 * <p>The entry keeps the buttons that produced it as well as what they displayed, which is what lets
 * the history list re-run a calculation instead of only describe it — the same thing the original's
 * history list does when an entry is chosen.
 *
 * @param expression the expression line, for example {@code "4 + 3="}
 * @param result     the formatted result
 * @param commands   the buttons pressed to produce it, in order
 */
public record HistoryEntry(String expression, String result, List<Command> commands) {

    public HistoryEntry {
        commands = commands == null ? List.of() : List.copyOf(commands);
    }

    /** An entry that cannot be re-run, for callers that only have the text. */
    public HistoryEntry(String expression, String result) {
        this(expression, result, List.of());
    }

    /** Whether the entry knows the buttons that produced it. */
    public boolean isReplayable() {
        return !commands.isEmpty();
    }
}
