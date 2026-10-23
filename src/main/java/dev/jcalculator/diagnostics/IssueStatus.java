package dev.jcalculator.diagnostics;

import java.util.Locale;

/** Whether a recorded problem still needs work. */
public enum IssueStatus {

    OPEN("open"),
    RESOLVED("resolved");

    private final String wireName;

    IssueStatus(String wireName) {
        this.wireName = wireName;
    }

    /** The stable name used in the journal and on the wire. */
    public String wireName() {
        return wireName;
    }

    /**
     * Resolves a wire name such as {@code "resolved"}.
     *
     * @throws IllegalArgumentException when the name is not a status
     */
    public static IssueStatus fromWire(String name) {
        final String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        for (IssueStatus status : values()) {
            if (status.wireName.equals(normalized) || status.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return status;
            }
        }
        throw new IllegalArgumentException("unknown issue status: " + name);
    }
}
