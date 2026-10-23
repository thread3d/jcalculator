package dev.jcalculator.diagnostics;

import java.util.Locale;

/** How a recorded problem was closed. */
public enum IssueDisposition {

    /** The problem was fixed in the source. */
    FIXED("fixed"),
    /** The problem is real but will not be fixed. */
    WONTFIX("wontfix");

    private final String wireName;

    IssueDisposition(String wireName) {
        this.wireName = wireName;
    }

    /** The stable name used in the journal and on the wire. */
    public String wireName() {
        return wireName;
    }

    /**
     * Resolves a wire name such as {@code "fixed"}.
     *
     * @throws IllegalArgumentException when the name is not a disposition
     */
    public static IssueDisposition fromWire(String name) {
        final String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        for (IssueDisposition disposition : values()) {
            if (disposition.wireName.equals(normalized) || disposition.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return disposition;
            }
        }
        throw new IllegalArgumentException("unknown issue disposition: " + name);
    }
}
