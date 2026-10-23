package dev.jcalculator.diagnostics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IssueJournalTest {

    private static final Instant FIRST = Instant.parse("2026-10-23T10:00:00Z");
    private static final Instant LATER = Instant.parse("2026-10-23T11:30:00Z");

    @TempDir
    Path directory;

    private Path file() {
        return directory.resolve("issues.jsonl");
    }

    private static IssueDraft draft(IssueSurface surface, String summary, String detail) {
        return new IssueDraft(IssueKind.USER_REPORT, IssueSeverity.MEDIUM, surface, summary, detail, Map.of());
    }

    @Test
    void recordsAnIssueAndReadsItBack() {
        final IssueJournal journal = new IssueJournal(file(), Clock.fixed(FIRST, ZoneOffset.UTC));
        final String id = journal.record(draft(IssueSurface.MCP, "2 + 2 showed 5", "Expected 4"));
        assertNotNull(id);
        assertTrue(id.matches("JC-[0-9A-F]{8}"), id);
        assertTrue(Files.exists(file()));

        final IssueJournal.Snapshot snapshot = journal.read();
        assertEquals(1, snapshot.issues().size());
        assertEquals(1, snapshot.events());
        assertEquals(0, snapshot.ignoredLines());
        final Issue issue = snapshot.issues().get(0);
        assertEquals(id, issue.id());
        assertEquals(IssueKind.USER_REPORT, issue.kind());
        assertEquals(IssueSeverity.MEDIUM, issue.severity());
        assertEquals(IssueStatus.OPEN, issue.status());
        assertEquals("2 + 2 showed 5", issue.summary());
        assertEquals("Expected 4", issue.detail());
        assertEquals(List.of("mcp"), issue.surfaces());
        assertEquals(1, issue.occurrences());
        assertEquals(FIRST, issue.firstSeen());
        assertEquals(FIRST, issue.lastSeen());
        assertNull(issue.resolution());
    }

    @Test
    void theSameProblemIsRecordedAsOneIssue() {
        final IssueJournal first = new IssueJournal(file(), Clock.fixed(FIRST, ZoneOffset.UTC));
        final String id = first.record(draft(IssueSurface.HARNESS, "unknown token: e", "first"));
        final IssueJournal later = new IssueJournal(file(), Clock.fixed(LATER, ZoneOffset.UTC));
        assertEquals(id, later.record(draft(IssueSurface.MCP, "unknown token: e", "again")));

        final IssueJournal.Snapshot snapshot = later.read();
        assertEquals(1, snapshot.issues().size());
        final Issue issue = snapshot.issues().get(0);
        assertEquals(2, issue.occurrences());
        assertEquals(List.of("harness", "mcp"), issue.surfaces());
        assertEquals("again", issue.detail());
        assertEquals(FIRST, issue.firstSeen());
        assertEquals(LATER, issue.lastSeen());
        assertEquals(2, snapshot.events());
    }

    @Test
    void differentProblemsGetDifferentIds() {
        final IssueJournal journal = new IssueJournal(file(), Clock.fixed(FIRST, ZoneOffset.UTC));
        final String first = journal.record(draft(IssueSurface.CLI, "problem one", ""));
        final String second = journal.record(draft(IssueSurface.CLI, "problem two", ""));
        assertFalse(first.equals(second));
        assertEquals(2, journal.read().issues().size());
    }

    @Test
    void resolutionIsRecordedWithItsDispositionAndNote() {
        final IssueJournal journal = new IssueJournal(file(), Clock.fixed(FIRST, ZoneOffset.UTC));
        final String id = journal.record(draft(IssueSurface.CLI, "problem", ""));
        assertTrue(journal.resolve(id, IssueDisposition.FIXED, "covered by a test"));

        final Issue issue = journal.find(id).orElseThrow();
        assertEquals(IssueStatus.RESOLVED, issue.status());
        assertNotNull(issue.resolution());
        assertEquals(IssueDisposition.FIXED, issue.resolution().disposition());
        assertEquals("covered by a test", issue.resolution().note());
        assertEquals(1, journal.read().resolvedCount());
        assertEquals(0, journal.read().openCount());

        assertFalse(journal.resolve("JC-00000000", IssueDisposition.WONTFIX, ""));
    }

    @Test
    void aRecurrenceReopensAResolvedIssue() {
        final IssueJournal journal = new IssueJournal(file(), Clock.fixed(FIRST, ZoneOffset.UTC));
        final String id = journal.record(draft(IssueSurface.CLI, "problem", ""));
        journal.resolve(id, IssueDisposition.FIXED, "done");
        journal.record(draft(IssueSurface.CLI, "problem", "it is back"));

        final Issue issue = journal.find(id).orElseThrow();
        assertEquals(IssueStatus.OPEN, issue.status());
        assertNull(issue.resolution());
        assertEquals(2, issue.occurrences());
    }

    @Test
    void theHighestSeverityWins() {
        final IssueJournal journal = new IssueJournal(file(), Clock.fixed(FIRST, ZoneOffset.UTC));
        journal.record(new IssueDraft(IssueKind.UNSUPPORTED_INPUT, IssueSeverity.LOW,
                IssueSurface.HARNESS, "unknown token: e", "", Map.of()));
        journal.record(new IssueDraft(IssueKind.UNSUPPORTED_INPUT, IssueSeverity.HIGH,
                IssueSurface.MCP, "unknown token: e", "", Map.of()));
        assertEquals(IssueSeverity.HIGH, journal.read().issues().get(0).severity());
    }

    @Test
    void aDamagedLineDoesNotLoseTheRest() throws IOException {
        final IssueJournal journal = new IssueJournal(file(), Clock.fixed(FIRST, ZoneOffset.UTC));
        journal.record(draft(IssueSurface.CLI, "problem", ""));
        Files.writeString(file(), "not json at all\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);

        final IssueJournal.Snapshot snapshot = journal.read();
        assertEquals(1, snapshot.issues().size());
        assertEquals(1, snapshot.events());
        assertEquals(1, snapshot.ignoredLines());
    }

    @Test
    void idsAreStableAcrossJournalInstances() {
        final IssueJournal first = new IssueJournal(directory.resolve("first.jsonl"), Clock.fixed(FIRST, ZoneOffset.UTC));
        final IssueJournal second = new IssueJournal(directory.resolve("second.jsonl"), Clock.fixed(LATER, ZoneOffset.UTC));
        assertEquals(first.record(draft(IssueSurface.CLI, "problem", "")),
                second.record(draft(IssueSurface.CLI, "problem", "different detail")));
    }

    @Test
    void theJournalIsAppendOnly() throws IOException {
        final IssueJournal journal = new IssueJournal(file(), Clock.fixed(FIRST, ZoneOffset.UTC));
        journal.record(draft(IssueSurface.CLI, "problem", ""));
        journal.record(draft(IssueSurface.CLI, "problem", ""));
        assertEquals(2, Files.readAllLines(file(), StandardCharsets.UTF_8).size());
    }

    @Test
    void aDisabledJournalRecordsNothing() {
        final IssueJournal journal = IssueJournal.disabled();
        assertFalse(journal.isEnabled());
        assertEquals("off", journal.location());
        assertNull(journal.record(draft(IssueSurface.CLI, "problem", "")));
        assertTrue(journal.read().issues().isEmpty());
        assertFalse(Files.exists(file()));
    }

    @Test
    void configurationSelectsTheJournalFile() {
        final String previous = System.getProperty(IssueJournal.PATH_PROPERTY);
        try {
            System.setProperty(IssueJournal.PATH_PROPERTY, file().toString());
            final IssueJournal configured = IssueJournal.fromConfiguration();
            assertTrue(configured.isEnabled());
            assertEquals(file().toAbsolutePath(), configured.file().orElseThrow());

            System.setProperty(IssueJournal.PATH_PROPERTY, "off");
            assertFalse(IssueJournal.fromConfiguration().isEnabled());
        } finally {
            if (previous == null) {
                System.clearProperty(IssueJournal.PATH_PROPERTY);
            } else {
                System.setProperty(IssueJournal.PATH_PROPERTY, previous);
            }
        }
    }
}
