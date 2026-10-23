package dev.jcalculator.diagnostics;

import dev.jcalculator.harness.Json;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A problem recorded in the journal, folded from every event that mentions it.
 *
 * <p>The journal is append-only, so an issue is not a stored row: it is the result of replaying its
 * events in order. That is what makes a repeated problem a count instead of a duplicate, and a
 * resolved problem that happens again an open issue again.
 *
 * @param id          the stable identifier, for example {@code JC-1A2B3C4D}
 * @param kind        what kind of problem this is
 * @param severity    the highest severity any occurrence had
 * @param status      whether the problem still needs work
 * @param summary     the one-line description
 * @param detail      the longer description from the most recent occurrence
 * @param surfaces    every surface that met the problem, sorted
 * @param occurrences how many times the problem was met
 * @param firstSeen   when it was first met
 * @param lastSeen    when it was most recently met
 * @param context     reproduction facts from the most recent occurrence
 * @param resolution  how it was closed, or {@code null} while it is open
 */
public record Issue(
        String id,
        IssueKind kind,
        IssueSeverity severity,
        IssueStatus status,
        String summary,
        String detail,
        List<String> surfaces,
        int occurrences,
        Instant firstSeen,
        Instant lastSeen,
        Map<String, Object> context,
        Resolution resolution) {

    public Issue {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(summary, "summary");
        detail = detail == null ? "" : detail;
        surfaces = List.copyOf(surfaces);
        context = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(context, "context")));
        Objects.requireNonNull(firstSeen, "firstSeen");
        Objects.requireNonNull(lastSeen, "lastSeen");
    }

    /** How a resolved issue was closed, and why. */
    public record Resolution(IssueDisposition disposition, String note, Instant resolvedAt) {

        public Resolution {
            Objects.requireNonNull(disposition, "disposition");
            note = note == null ? "" : note;
            Objects.requireNonNull(resolvedAt, "resolvedAt");
        }
    }

    /** Whether this issue still needs work. */
    public boolean isOpen() {
        return status == IssueStatus.OPEN;
    }

    /** The issue as a JSON object, in exactly the shape the protocol and the MCP tools return. */
    public Map<String, Object> asJson() {
        final Map<String, Object> json = Json.object(
                "id", id,
                "kind", kind.wireName(),
                "severity", severity.wireName(),
                "status", status.wireName(),
                "summary", summary,
                "detail", detail,
                "surfaces", surfaces,
                "occurrences", occurrences,
                "firstSeen", firstSeen.toString(),
                "lastSeen", lastSeen.toString(),
                "context", context);
        if (resolution == null) {
            json.put("resolution", null);
        } else {
            json.put("resolution", Json.object(
                    "disposition", resolution.disposition().wireName(),
                    "note", resolution.note(),
                    "at", resolution.resolvedAt().toString()));
        }
        return json;
    }
}
