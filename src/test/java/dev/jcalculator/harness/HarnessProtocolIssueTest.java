package dev.jcalculator.harness;

import dev.jcalculator.diagnostics.Issue;
import dev.jcalculator.diagnostics.IssueJournal;
import dev.jcalculator.diagnostics.IssueKind;
import dev.jcalculator.diagnostics.IssueSeverity;
import dev.jcalculator.engine.CalculatorMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HarnessProtocolIssueTest {

    @TempDir
    Path directory;

    private IssueJournal journal() {
        return new IssueJournal(directory.resolve("issues.jsonl"), java.time.Clock.systemUTC());
    }

    private static HarnessProtocol protocol(IssueJournal journal) {
        return new HarnessProtocol(new CalculatorSession(CalculatorMode.STANDARD, 0L), journal);
    }

    @Test
    void anUnknownTokenIsRecordedAutomatically() {
        final IssueJournal journal = journal();
        final HarnessProtocol.Outcome outcome = protocol(journal).execute(
                Json.object("op", "press", "tokens", List.of("2", "+", "bogus")));
        assertEquals("unknown_token", outcome.response().get("errorCode"));

        final List<Issue> issues = journal.read().issues();
        assertEquals(1, issues.size());
        assertEquals(IssueKind.UNSUPPORTED_INPUT, issues.get(0).kind());
        assertEquals(IssueSeverity.LOW, issues.get(0).severity());
        assertEquals(List.of("harness"), issues.get(0).surfaces());
        assertEquals("bogus", issues.get(0).context().get("token"));
        assertEquals("STANDARD", issues.get(0).context().get("mode"));
    }

    @Test
    void repeatedProblemsFoldIntoOneIssue() {
        final IssueJournal journal = journal();
        final HarnessProtocol protocol = protocol(journal);
        for (int attempt = 0; attempt < 3; attempt++) {
            protocol.execute(Json.object("op", "press", "tokens", List.of("bogus")));
        }
        assertEquals(1, journal.read().issues().size());
        assertEquals(3, journal.read().issues().get(0).occurrences());
    }

    @Test
    void anUnknownOperationIsRecordedAutomatically() {
        final IssueJournal journal = journal();
        protocol(journal).execute(Json.object("op", "fly"));
        final Issue issue = journal.read().issues().get(0);
        assertEquals(IssueKind.UNSUPPORTED_OPERATION, issue.kind());
        assertEquals("fly", issue.context().get("operation"));
    }

    @Test
    void anUnexpectedFailureIsRecordedWithItsContext() {
        final IssueJournal journal = journal();
        final Map<String, Object> request = Json.object("op", "press", "tokens", List.of("2"));
        final HarnessProtocol.Outcome outcome = protocol(journal).unexpectedFailure(
                7, "press", new IllegalStateException("the model blew up"), request);

        assertEquals("internal_error", outcome.response().get("errorCode"));
        assertEquals(7, outcome.response().get("id"));
        assertEquals("the model blew up", outcome.response().get("message"));

        final Issue issue = journal.read().issues().get(0);
        assertEquals(IssueKind.INTERNAL_ERROR, issue.kind());
        assertEquals(IssueSeverity.HIGH, issue.severity());
        assertEquals("java.lang.IllegalStateException", issue.context().get("exception"));
        assertNotNull(issue.context().get("request"));
        assertFalse(issue.detail().isBlank());
    }

    @Test
    void aMalformedFieldIsRefusedWithoutBeingRecorded() {
        final IssueJournal journal = journal();
        final HarnessProtocol protocol = protocol(journal);
        final List<Map<String, Object>> requests = List.of(
                Json.object("op", "date_add", "from", "2024-01-01",
                        "days", new java.math.BigDecimal("1.5")),
                Json.object("op", "date_add", "from", "2024-01-01",
                        "days", new java.math.BigDecimal("1e10")),
                Json.object("op", "press", "tokens", "2 + 3 ="),
                Json.object("op", "press", "tokens", List.of(2)));

        for (Map<String, Object> request : requests) {
            final HarnessProtocol.Outcome outcome = protocol.execute(request);
            assertEquals("bad_request", outcome.response().get("errorCode"), request.toString());
            assertEquals(HarnessProtocol.EXIT_PROTOCOL_ERROR, outcome.exitCode());
        }
        assertTrue(journal.read().issues().isEmpty());
    }

    @Test
    void reportsCanBeListedAndResolvedThroughTheProtocol() {
        final IssueJournal journal = journal();
        final HarnessProtocol protocol = protocol(journal);
        final Map<String, Object> reported = protocol.execute(Json.object(
                "op", "report_issue", "summary", "2 + 2 displayed 5",
                "detail", "Expected 4", "severity", "high")).response();
        assertEquals(Boolean.TRUE, reported.get("ok"));
        final String id = (String) reported.get("issueId");
        assertNotNull(id);

        final Map<String, Object> listed = protocol.execute(Json.object("op", "issues")).response();
        assertEquals(1, Json.asArray(listed.get("issues")).size());
        assertEquals(1, Json.integer(listed, "open", -1));
        assertEquals(Boolean.TRUE, listed.get("enabled"));

        final Map<String, Object> resolved = protocol.execute(Json.object(
                "op", "resolve_issue", "id", id, "note", "fixed in a test")).response();
        assertEquals(Boolean.TRUE, resolved.get("ok"));
        final Map<String, Object> issue = Json.asObject(resolved.get("issue"));
        assertEquals("resolved", issue.get("status"));

        assertEquals(0, Json.asArray(protocol.execute(Json.object("op", "issues")).response().get("issues")).size());
        assertEquals(1, Json.asArray(protocol.execute(
                Json.object("op", "issues", "status", "resolved")).response().get("issues")).size());
        assertEquals(1, Json.asArray(protocol.execute(
                Json.object("op", "issues", "status", "all")).response().get("issues")).size());
    }

    @Test
    void issuesArePagedAndValidated() {
        final IssueJournal journal = journal();
        final HarnessProtocol protocol = protocol(journal);
        for (int index = 1; index <= 3; index++) {
            protocol.execute(Json.object("op", "report_issue", "summary", "problem " + index));
        }
        assertEquals(2, Json.asArray(protocol.execute(
                Json.object("op", "issues", "limit", 2)).response().get("issues")).size());
        assertEquals("bad_request", protocol.execute(
                Json.object("op", "issues", "limit", 0)).response().get("errorCode"));
        assertEquals("bad_request", protocol.execute(
                Json.object("op", "issues", "status", "later")).response().get("errorCode"));
    }

    @Test
    void aDisabledJournalRefusesReportsButStillLists() {
        final HarnessProtocol protocol = protocol(IssueJournal.disabled());
        final Map<String, Object> reported = protocol.execute(
                Json.object("op", "report_issue", "summary", "a problem")).response();
        assertEquals("journal_disabled", reported.get("errorCode"));

        final Map<String, Object> listed = protocol.execute(Json.object("op", "issues")).response();
        assertEquals(Boolean.TRUE, listed.get("ok"));
        assertEquals(Boolean.FALSE, listed.get("enabled"));
        assertEquals(0, Json.asArray(listed.get("issues")).size());
    }

    @Test
    void capabilitiesAdvertiseTheJournalAndTheIssueOperations() {
        final IssueJournal journal = journal();
        final HarnessProtocol protocol = protocol(journal);
        protocol.execute(Json.object("op", "report_issue", "summary", "a problem"));
        final Map<String, Object> response = protocol.execute(Json.object("op", "capabilities")).response();
        final Map<String, Object> issues = Json.asObject(response.get("issues"));
        assertEquals(Boolean.TRUE, issues.get("enabled"));
        assertEquals(1, Json.integer(issues, "open", -1));
        assertTrue(Json.asArray(response.get("operations")).contains("report_issue"));
        assertTrue(Json.asArray(response.get("operations")).contains("issues"));
        assertTrue(Json.asArray(response.get("operations")).contains("resolve_issue"));
    }

    @Test
    void unknownTokensAreNotRecordedWhenTheJournalIsDisabled() {
        final IssueJournal journal = IssueJournal.disabled();
        protocol(journal).execute(Json.object("op", "press", "tokens", List.of("bogus")));
        assertTrue(journal.read().issues().isEmpty());
    }
}
