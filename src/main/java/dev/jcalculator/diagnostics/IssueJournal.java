package dev.jcalculator.diagnostics;

import dev.jcalculator.app.AppInfo;
import dev.jcalculator.harness.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A durable, append-only journal of problems met while the calculator is in use.
 *
 * <p>The point of the journal is that a problem does not have to be remembered by the person who
 * met it. Every front end records what went wrong — an unknown token, an unknown operation, an
 * unexpected internal failure — and a caller can add a report in its own words. A later session
 * reads the open entries and fixes them.
 *
 * <h2>Storage</h2>
 *
 * <p>The journal is a JSON Lines file: one event per line, never rewritten. An issue is folded from
 * its events when the file is read, so a repeated problem becomes a count instead of a duplicate,
 * and a problem that happens again after being resolved is open again. A line that cannot be parsed
 * is ignored and counted, so one damaged line does not lose the rest.
 *
 * <p>The location is, in order: the {@value #PATH_PROPERTY} system property, the
 * {@value #PATH_ENVIRONMENT} environment variable, or {@code .jcalculator/issues.jsonl} under the
 * working directory. The value {@code off}, {@code none}, {@code disabled} or {@code -} disables the
 * journal. The {@code bin/} launchers pin the variable to the project root, so the journal does not
 * depend on where a harness starts the process.
 *
 * <h2>Failure policy</h2>
 *
 * <p>Recording is best effort and never changes a calculation: an unwritable journal is silently
 * skipped. Reading reports the failure, because a caller that asked for the journal should know it
 * could not be read.
 */
public final class IssueJournal {

    /** The system property that selects the journal file. */
    public static final String PATH_PROPERTY = "jcalculator.issues.path";

    /** The environment variable that selects the journal file. */
    public static final String PATH_ENVIRONMENT = "JCALC_ISSUES";

    /** After this many occurrences the journal stops appending recurrence events. */
    private static final int MAX_RECORDED_OCCURRENCES = 100;

    private final Path file;
    private final Clock clock;

    /**
     * Opens a journal at an explicit file.
     *
     * @param file  the JSON Lines file; parent directories are created when the first event is written
     * @param clock the clock used to timestamp events, so tests can be deterministic
     */
    public IssueJournal(Path file, Clock clock) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    private IssueJournal(Path file) {
        this.file = file;
        this.clock = Clock.systemUTC();
    }

    // ---- Configuration ----------------------------------------------------------------------------

    /**
     * The journal configured for this process.
     *
     * <p>Resolved on every call rather than cached, so a test or a tool that changes the property
     * sees the change; construction of the surfaces that use it happens once, not per operation.
     */
    public static IssueJournal shared() {
        return fromConfiguration();
    }

    /** Resolves the configured journal, so tests and tools can use it without caching. */
    public static IssueJournal fromConfiguration() {
        final String location = configuredLocation();
        if (isDisabled(location)) {
            return disabled();
        }
        return new IssueJournal(Path.of(location), Clock.systemUTC());
    }

    /** A journal that records nothing and reads as empty. */
    public static IssueJournal disabled() {
        return new IssueJournal(null);
    }

    private static String configuredLocation() {
        final String property = System.getProperty(PATH_PROPERTY);
        if (property != null && !property.isBlank()) {
            return property;
        }
        final String environment = System.getenv(PATH_ENVIRONMENT);
        if (environment != null && !environment.isBlank()) {
            return environment;
        }
        return Path.of(System.getProperty("user.dir"), ".jcalculator", "issues.jsonl").toString();
    }

    private static boolean isDisabled(String location) {
        final String normalized = location == null ? "" : location.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty()
                || "off".equals(normalized)
                || "none".equals(normalized)
                || "disabled".equals(normalized)
                || "-".equals(normalized);
    }

    // ---- Identity ---------------------------------------------------------------------------------

    /** Whether this journal records anything. */
    public boolean isEnabled() {
        return file != null;
    }

    /** The journal path, or {@code "off"} when recording is disabled. */
    public String location() {
        return file == null ? "off" : file.toString();
    }

    /** The journal path, when recording is enabled. */
    public Optional<Path> file() {
        return Optional.ofNullable(file);
    }

    // ---- Writing ----------------------------------------------------------------------------------

    /**
     * Records a problem.
     *
     * <p>A problem seen before is recorded as a recurrence, not as a second entry; its id is stable
     * because it is derived from the kind, the summary and the draft's signature, not from time.
     *
     * @return the issue id, or {@code null} when the journal is disabled
     */
    public String record(IssueDraft draft) {
        Objects.requireNonNull(draft, "draft");
        final String id = identifier(draft);
        if (file == null) {
            return null;
        }
        Optional<Issue> existing;
        try {
            existing = find(id);
        } catch (RuntimeException exception) {
            // An unreadable journal still accepts appends, so record the problem as new.
            existing = Optional.empty();
        }
        if (existing.isPresent()) {
            final Issue issue = existing.get();
            if (issue.occurrences() >= MAX_RECORDED_OCCURRENCES) {
                return id;
            }
            append(event("recurred", id, Instant.now(clock), draft));
            return id;
        }
        append(event("reported", id, Instant.now(clock), draft));
        return id;
    }

    /**
     * Marks an issue resolved.
     *
     * @return whether the issue exists and was marked
     */
    public boolean resolve(String id, IssueDisposition disposition, String note) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(disposition, "disposition");
        if (file == null || find(id).isEmpty()) {
            return false;
        }
        append(Json.object(
                "v", 1,
                "event", "resolved",
                "id", id,
                "at", Instant.now(clock).toString(),
                "disposition", disposition.wireName(),
                "note", note == null ? "" : note));
        return true;
    }

    private Map<String, Object> event(String name, String id, Instant at, IssueDraft draft) {
        return Json.object(
                "v", 1,
                "event", name,
                "id", id,
                "at", at.toString(),
                "surface", draft.surface().wireName(),
                "kind", draft.kind().wireName(),
                "severity", draft.severity().wireName(),
                "summary", draft.summary(),
                "detail", draft.detail(),
                "context", draft.context(),
                "version", AppInfo.VERSION);
    }

    private void append(Map<String, Object> event) {
        if (file == null) {
            return;
        }
        try {
            final Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            // One append call per event: O_APPEND keeps concurrent writers from overwriting each
            // other, and an unfinished line is ignored when the journal is read.
            Files.writeString(file, Json.write(event) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            // Best effort: a calculator must keep working when its journal cannot be written.
        }
    }

    // ---- Reading ----------------------------------------------------------------------------------

    /**
     * Reads every issue in the journal, newest first.
     *
     * @throws UncheckedIOException when the file exists but cannot be read
     */
    public Snapshot read() {
        if (file == null || !Files.exists(file)) {
            return Snapshot.empty();
        }
        final List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("cannot read the issue journal at " + file, exception);
        }
        final Map<String, MutableIssue> folded = new LinkedHashMap<>();
        int events = 0;
        int ignored = 0;
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            try {
                if (apply(folded, Json.parseObject(line))) {
                    events++;
                } else {
                    ignored++;
                }
            } catch (RuntimeException exception) {
                ignored++;
            }
        }
        final List<Issue> issues = new ArrayList<>(folded.size());
        for (MutableIssue issue : folded.values()) {
            issues.add(issue.toIssue());
        }
        issues.sort(Comparator.comparing(Issue::lastSeen).reversed().thenComparing(Issue::id));
        return new Snapshot(issues, events, ignored);
    }

    /** Finds one issue by its id. */
    public Optional<Issue> find(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return read().issues().stream().filter(issue -> issue.id().equals(id)).findFirst();
    }

    /** Applies one event, returning whether it was usable. */
    private static boolean apply(Map<String, MutableIssue> folded, Map<String, Object> event) {
        final String name = Json.optionalString(event, "event", null);
        final String id = Json.optionalString(event, "id", null);
        if (name == null || id == null || id.isBlank()) {
            return false;
        }
        if ("resolved".equals(name)) {
            final MutableIssue issue = folded.get(id);
            if (issue == null) {
                return false;
            }
            issue.resolve(
                    IssueDisposition.fromWire(Json.string(event, "disposition")),
                    Json.optionalString(event, "note", ""),
                    instantOf(event));
            return true;
        }
        if (!"reported".equals(name) && !"recurred".equals(name)) {
            return false;
        }
        final IssueDraft draft = new IssueDraft(
                IssueKind.fromWire(Json.string(event, "kind")),
                IssueSeverity.fromWire(Json.string(event, "severity")),
                IssueSurface.fromWire(Json.string(event, "surface")),
                Json.string(event, "summary"),
                Json.optionalString(event, "detail", ""),
                contextOf(event));
        final Instant at = instantOf(event);
        final MutableIssue issue = folded.get(id);
        if (issue == null) {
            folded.put(id, new MutableIssue(id, draft, at));
            return true;
        }
        // A recurrence reopens a resolved issue: the problem is evidently still there.
        issue.observe(draft, at);
        return true;
    }

    private static Map<String, Object> contextOf(Map<String, Object> event) {
        final Object context = event.get("context");
        if (context instanceof Map<?, ?> map) {
            return Json.asObject(map);
        }
        return Map.of();
    }

    private static Instant instantOf(Map<String, Object> event) {
        final String text = Json.optionalString(event, "at", null);
        if (text == null) {
            return Instant.EPOCH;
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeException exception) {
            return Instant.EPOCH;
        }
    }

    // ---- Identity derivation ----------------------------------------------------------------------

    private static String identifier(IssueDraft draft) {
        final String signature = draft.kind().wireName()
                + '\n' + draft.summary().trim()
                + '\n' + draft.signature();
        final byte[] digest = digest(signature);
        final StringBuilder id = new StringBuilder("JC-");
        for (int index = 0; index < 4; index++) {
            id.append(Character.toUpperCase(Character.forDigit((digest[index] >> 4) & 0x0F, 16)));
            id.append(Character.toUpperCase(Character.forDigit(digest[index] & 0x0F, 16)));
        }
        return id.toString();
    }

    private static byte[] digest(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required to identify issues", exception);
        }
    }

    // ---- Folded issue -----------------------------------------------------------------------------

    /** The mutable accumulator behind one folded issue; never exposed. */
    private static final class MutableIssue {

        private final String id;
        private IssueKind kind;
        private IssueSeverity severity;
        private IssueStatus status;
        private String summary;
        private String detail;
        private final List<String> surfaces = new ArrayList<>();
        private int occurrences;
        private Instant firstSeen;
        private Instant lastSeen;
        private Map<String, Object> context = Map.of();
        private Issue.Resolution resolution;

        MutableIssue(String id, IssueDraft draft, Instant at) {
            this.id = id;
            this.kind = draft.kind();
            this.severity = draft.severity();
            this.status = IssueStatus.OPEN;
            this.summary = draft.summary();
            this.detail = draft.detail();
            this.surfaces.add(draft.surface().wireName());
            this.occurrences = 1;
            this.firstSeen = at;
            this.lastSeen = at;
            this.context = draft.context();
        }

        void observe(IssueDraft draft, Instant at) {
            severity = severity.max(draft.severity());
            detail = draft.detail();
            context = draft.context();
            if (!surfaces.contains(draft.surface().wireName())) {
                surfaces.add(draft.surface().wireName());
                Collections.sort(surfaces);
            }
            occurrences++;
            lastSeen = at;
            status = IssueStatus.OPEN;
            resolution = null;
        }

        void resolve(IssueDisposition disposition, String note, Instant at) {
            status = IssueStatus.RESOLVED;
            resolution = new Issue.Resolution(disposition, note, at);
            if (at.isAfter(lastSeen)) {
                lastSeen = at;
            }
        }

        Issue toIssue() {
            return new Issue(
                    id, kind, severity, status, summary, detail,
                    List.copyOf(surfaces), occurrences, firstSeen, lastSeen, context, resolution);
        }
    }

    /**
     * Every issue in the journal, plus what was skipped while reading it.
     *
     * @param issues       the folded issues, newest first
     * @param events       how many event lines were applied
     * @param ignoredLines how many non-blank lines could not be used
     */
    public record Snapshot(List<Issue> issues, int events, int ignoredLines) {

        public Snapshot {
            issues = List.copyOf(issues);
        }

        static Snapshot empty() {
            return new Snapshot(List.of(), 0, 0);
        }

        /** How many issues still need work. */
        public long openCount() {
            return issues.stream().filter(Issue::isOpen).count();
        }

        /** How many issues have been closed. */
        public long resolvedCount() {
            return issues.stream().filter(issue -> !issue.isOpen()).count();
        }
    }
}
