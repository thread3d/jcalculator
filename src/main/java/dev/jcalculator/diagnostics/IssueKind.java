package dev.jcalculator.diagnostics;

import java.util.Locale;

/**
 * What kind of problem an issue records.
 *
 * <p>The wire names are stable: they appear in the journal file and in every harness response, so a
 * later session can group and query issues without knowing the Java constants.
 */
public enum IssueKind {

    /** A failure the calculator did not expect, such as a malformed request reaching the model. */
    INTERNAL_ERROR("internal_error"),
    /** A caller pressed a token that is not a button, or that does nothing on the active page. */
    UNSUPPORTED_INPUT("unsupported_input"),
    /** A caller asked for an operation the protocol does not implement. */
    UNSUPPORTED_OPERATION("unsupported_operation"),
    /** A human or an agent described a problem with the tool in its own words. */
    USER_REPORT("user_report");

    private final String wireName;

    IssueKind(String wireName) {
        this.wireName = wireName;
    }

    /** The stable name used in the journal and on the wire. */
    public String wireName() {
        return wireName;
    }

    /**
     * Resolves a wire name such as {@code "user_report"}.
     *
     * @throws IllegalArgumentException when the name is not a kind
     */
    public static IssueKind fromWire(String name) {
        final String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        for (IssueKind kind : values()) {
            if (kind.wireName.equals(normalized) || kind.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("unknown issue kind: " + name);
    }
}
