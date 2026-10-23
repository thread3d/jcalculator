package dev.jcalculator.diagnostics;

import java.util.Locale;

/** How much a recorded problem matters. */
public enum IssueSeverity {

    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    CRITICAL("critical");

    private final String wireName;

    IssueSeverity(String wireName) {
        this.wireName = wireName;
    }

    /** The stable name used in the journal and on the wire. */
    public String wireName() {
        return wireName;
    }

    /** The higher of two severities, used when the same problem is seen again. */
    public IssueSeverity max(IssueSeverity other) {
        return ordinal() >= other.ordinal() ? this : other;
    }

    /**
     * Resolves a wire name such as {@code "high"}.
     *
     * @throws IllegalArgumentException when the name is not a severity
     */
    public static IssueSeverity fromWire(String name) {
        final String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        for (IssueSeverity severity : values()) {
            if (severity.wireName.equals(normalized) || severity.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return severity;
            }
        }
        throw new IllegalArgumentException("unknown issue severity: " + name);
    }
}
