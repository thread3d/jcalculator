package dev.jcalculator.diagnostics;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One problem as a front end observed it, ready to be written to the journal.
 *
 * <p>A draft is what a surface has at the moment of failure: the kind of problem, its severity, the
 * surface, a one-line summary and whatever context helps a later reader reproduce it. The journal
 * turns the draft into a stable id, so the same problem reported twice does not become two entries.
 *
 * @param kind     what kind of problem this is
 * @param severity how much it matters
 * @param surface  the front end that met it
 * @param summary  a one-line description; it is part of the issue's identity
 * @param detail   a longer description, usually empty for automatic reports
 * @param context  reproduction facts such as the token, operation, mode or stack trace
 */
public record IssueDraft(
        IssueKind kind,
        IssueSeverity severity,
        IssueSurface surface,
        String summary,
        String detail,
        Map<String, Object> context) {

    public IssueDraft {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(summary, "summary");
        if (summary.isBlank()) {
            throw new IllegalArgumentException("an issue summary must not be blank");
        }
        detail = detail == null ? "" : detail;
        context = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(context, "context")));
    }

    /** A problem described by a caller, with no technical context. */
    public static IssueDraft reported(IssueSurface surface, String summary, String detail) {
        return new IssueDraft(IssueKind.USER_REPORT, IssueSeverity.MEDIUM, surface, summary, detail, Map.of());
    }

    /**
     * The extra text that distinguishes two problems with the same summary.
     *
     * <p>Automatic reports put a stable signature in the context (for example
     * {@code "token=e|mode=STANDARD"}), so a crash with a varying message still folds into one
     * issue. A caller-provided report has no signature and is identified by its summary alone.
     */
    String signature() {
        final Object signature = context.get("signature");
        return signature instanceof String text ? text : "";
    }
}
