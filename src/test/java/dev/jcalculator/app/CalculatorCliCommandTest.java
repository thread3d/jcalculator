package dev.jcalculator.app;

import dev.jcalculator.core.AngleType;
import dev.jcalculator.diagnostics.Issue;
import dev.jcalculator.diagnostics.IssueDisposition;
import dev.jcalculator.diagnostics.IssueDraft;
import dev.jcalculator.diagnostics.IssueJournal;
import dev.jcalculator.diagnostics.IssueKind;
import dev.jcalculator.diagnostics.IssueSeverity;
import dev.jcalculator.diagnostics.IssueStatus;
import dev.jcalculator.diagnostics.IssueSurface;
import dev.jcalculator.engine.CalculatorMode;
import dev.jcalculator.engine.NumberWidth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The text interface's command line, driven in this process.
 *
 * <p>{@link CalculatorCli#run} answers an exit code instead of exiting, and the interface writes to
 * the streams it was constructed with, so every flag and every refusal can be exercised here rather
 * than in a subprocess. The exit codes asserted below are the ones the executable answers:
 * {@code 0} success, {@code 64} a usage error, {@code 70} an internal failure, and {@code 1} for a
 * journal command that cannot do its work.
 */
class CalculatorCliCommandTest {

    @TempDir
    Path directory;

    private ByteArrayOutputStream outBytes;
    private ByteArrayOutputStream errBytes;
    private IssueJournal journal;

    @BeforeEach
    void setUp() {
        outBytes = new ByteArrayOutputStream();
        errBytes = new ByteArrayOutputStream();
        journal = new IssueJournal(directory.resolve("issues.jsonl"), Clock.systemUTC());
    }

    // ---- Helpers ---------------------------------------------------------------------------------

    private CalculatorCli cli() {
        return cli(journal);
    }

    private CalculatorCli cli(IssueJournal withJournal) {
        return new CalculatorCli(CalculatorMode.STANDARD, withJournal,
                new PrintStream(outBytes, true, StandardCharsets.UTF_8),
                new PrintStream(errBytes, true, StandardCharsets.UTF_8));
    }

    private String out() {
        return outBytes.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return errBytes.toString(StandardCharsets.UTF_8);
    }

    private int run(String... args) {
        return cli().run(List.of(args));
    }

    private String record(String summary) {
        return journal.record(new IssueDraft(IssueKind.USER_REPORT, IssueSeverity.LOW, IssueSurface.CLI,
                summary, "detail of " + summary, Map.of()));
    }

    // ---- Identity ---------------------------------------------------------------------------------

    @Test
    void versionIsPrintedAndTheExitCodeIsZero() {
        assertEquals(0, run("--version"));
        assertEquals(AppInfo.displayVersion(), out().trim());
    }

    @Test
    void versionHasAShortAlias() {
        assertEquals(0, run("-V"));
        assertEquals(AppInfo.displayVersion(), out().trim());
    }

    @Test
    void helpListsTheCommandLanguage() {
        assertEquals(0, run("--help"));
        assertTrue(out().contains("Tokens are calculator buttons"), out());
        assertTrue(out().contains("--issues"), out());
    }

    @Test
    void helpHasAShortAlias() {
        assertEquals(0, run("-h"));
        assertTrue(out().contains("Tokens are calculator buttons"), out());
    }

    // ---- One-shot evaluation ----------------------------------------------------------------------

    @Test
    void aTokenSequencePrintsTheDisplay() {
        assertEquals(0, run("2", "+", "3", "="));
        assertEquals("5", out().trim());
    }

    @Test
    void theModeFlagSwitchesThePageBeforeTheTokens() {
        assertEquals(0, run("--mode", "programmer", "hex", "f", "f", "="));
        assertEquals("FF", out().trim());
    }

    @Test
    void anErrorResultIsPrintedAsItHappens() {
        assertEquals(0, run("1", "/", "0", "="));
        assertTrue(out().contains("Cannot divide by zero"), out());
    }

    @Test
    void anUnknownTokenIsRefusedOnStandardError() {
        assertEquals(0, run("bogus"));
        assertTrue(err().contains("unknown token: bogus"), err());
    }

    @Test
    void anUnknownTokenDoesNotStopTheRestOfTheLine() {
        assertEquals(0, run("2", "bogus", "+", "3", "="));
        assertTrue(err().contains("bogus"), err());
        assertEquals("5", out().trim());
    }

    // ---- Converters and dates ---------------------------------------------------------------------

    @Test
    void convertFlagsPrintTheConvertedValue() {
        assertEquals(0, run("--convert", "length", "m", "ft", "2.5"));
        assertEquals("8.2021", out().trim());
    }

    @Test
    void dateDiffPrintsTheCalendarDifference() {
        assertEquals(0, run("--date-diff", "2024-01-01", "2024-12-31"));
        assertEquals("11 months 4 weeks 2 days", out().trim());
    }

    @Test
    void dateAddAndSubtractPrintTheResultingDate() {
        assertEquals(0, run("--date-add", "2024-01-01", "1", "1", "0"));
        assertEquals("2025-02-01", out().trim());
    }

    @Test
    void dateSubtractMovesTheDateBackward() {
        assertEquals(0, run("--date-subtract", "2024-01-01", "1", "1", "0"));
        assertEquals("2022-12-01", out().trim());
    }

    @Test
    void anUnparsableDateInDateAddIsReportedNotFatal() {
        assertEquals(0, run("--date-add", "2024-13-01", "0", "0", "0"));
        assertTrue(out().contains("Invalid date: 2024-13-01"), out());
    }

    // ---- Usage errors -----------------------------------------------------------------------------

    @Test
    void aFlagWithoutItsArgumentsIsAUsageError() {
        assertEquals(64, run("--mode"));
        assertTrue(err().contains("--mode needs standard, scientific or programmer"), err());

        assertEquals(64, run("--convert", "length", "m"));
        assertTrue(err().contains("--convert needs <category> <from> <to> <value>"), err());
    }

    @Test
    void dateFlagsWithoutTheirArgumentsAreUsageErrors() {
        assertEquals(64, run("--date-diff", "2024-01-01"));
        assertTrue(err().contains("--date-diff needs <from-date> <to-date>"), err());

        assertEquals(64, run("--date-add", "2024-01-01", "1", "0"));
        assertTrue(err().contains("--date-add needs <date> <years> <months> <days>"), err());

        assertEquals(64, run("--date-subtract", "2024-01-01"));
        assertTrue(err().contains("--date-subtract needs"), err());
    }

    @Test
    void anUnknownModeIsAUsageError() {
        assertEquals(64, run("--mode", "bogus"));
        assertTrue(err().contains("unknown mode: bogus"), err());
    }

    @Test
    void anUnknownConversionCategoryIsAUsageError() {
        assertEquals(64, run("--convert", "nonsense", "m", "ft", "1"));
        assertFalse(err().isBlank());
    }

    @Test
    void aMalformedDateOnTheCommandLineIsAUsageError() {
        assertEquals(64, run("--date-diff", "not-a-date", "2024-01-01"));
        assertFalse(err().isBlank());
    }

    @Test
    void aNonNumericDurationIsAUsageError() {
        assertEquals(64, run("--date-add", "2024-01-01", "x", "0", "0"));
        assertFalse(err().isBlank());
    }

    // ---- The issue journal ------------------------------------------------------------------------

    @Test
    void reportRecordsAnIssueAndPrintsItsId() {
        assertEquals(0, run("--report", "the window flickers", "--detail", "on resize",
                "--severity", "high"));
        assertTrue(out().contains("Recorded JC-"), out());

        final List<Issue> issues = journal.read().issues();
        assertEquals(1, issues.size());
        final Issue issue = issues.get(0);
        assertEquals(IssueKind.USER_REPORT, issue.kind());
        assertEquals(IssueSeverity.HIGH, issue.severity());
        assertEquals("the window flickers", issue.summary());
        assertEquals("on resize", issue.detail());
        assertEquals(List.of("cli"), issue.surfaces());
    }

    @Test
    void reportWithoutASummaryIsAUsageError() {
        assertEquals(64, run("--report"));
        assertTrue(err().contains("--report needs a summary"), err());
    }

    @Test
    void reportWithTwoSummariesIsAUsageError() {
        assertEquals(64, run("--report", "one", "two"));
        assertTrue(err().contains("--report takes one summary"), err());
    }

    @Test
    void reportWithoutADetailTextIsAUsageError() {
        assertEquals(64, run("--report", "a problem", "--detail"));
        assertTrue(err().contains("--detail needs text"), err());
    }

    @Test
    void reportWithADisabledJournalAnswersOne() {
        assertEquals(1, cli(IssueJournal.disabled()).run(List.of("--report", "a problem")));
        assertTrue(err().contains("disabled"), err());
    }

    @Test
    void issuesListsOpenEntriesAndTheJournalSummary() {
        final String first = record("first problem");
        final String second = record("second problem");
        journal.resolve(second, IssueDisposition.FIXED, "fixed it");

        assertEquals(0, run("--issues"));
        assertTrue(out().contains(first), out());
        assertFalse(out().contains(second), out());
        assertTrue(out().contains("detail of first problem"), out());
        assertTrue(out().contains("Journal: "), out());
        assertTrue(out().contains("1 open, 1 resolved"), out());
    }

    @Test
    void issuesAllIncludesResolvedEntriesAndShowsTheirResolution() {
        final String first = record("first problem");
        final String second = record("second problem");
        journal.resolve(second, IssueDisposition.WONTFIX, "by design");

        assertEquals(0, run("--issues", "--all"));
        assertTrue(out().contains(first), out());
        assertTrue(out().contains(second), out());
        assertTrue(out().contains("wontfix: by design"), out());
    }

    @Test
    void issuesLimitCapsTheListing() {
        record("first problem");
        record("second problem");

        assertEquals(0, run("--issues", "--limit", "1"));
        assertEquals(1, out().lines().filter(line -> line.startsWith("JC-")).count(), out());
    }

    @Test
    void issuesSaysWhenNothingIsRecorded() {
        assertEquals(0, run("--issues"));
        assertTrue(out().contains("No open issues."), out());

        outBytes.reset();
        assertEquals(0, run("--issues", "--all"));
        assertTrue(out().contains("No recorded issues."), out());
    }

    @Test
    void issuesWithADisabledJournalSaysSo() {
        assertEquals(0, cli(IssueJournal.disabled()).run(List.of("--issues")));
        assertTrue(out().contains("The issue journal is disabled."), out());
    }

    @Test
    void issuesRejectsAnUnknownOption() {
        assertEquals(64, run("--issues", "--bogus"));
        assertTrue(err().contains("unknown option for --issues: --bogus"), err());
    }

    @Test
    void issuesLimitMustBeANumberAndAtLeastOne() {
        assertEquals(64, run("--issues", "--limit"));
        assertTrue(err().contains("--limit needs a number"), err());

        assertEquals(64, run("--issues", "--limit", "0"));
        assertTrue(err().contains("--limit must be at least 1"), err());

        assertEquals(64, run("--issues", "--limit", "many"));
        assertTrue(err().contains("invalid --limit: many"), err());
    }

    @Test
    void resolveMarksAnIssueFixed() {
        final String id = record("a problem");

        assertEquals(0, run("--resolve", id));
        assertTrue(out().contains("Resolved " + id), out());
        final Issue resolved = journal.find(id).orElseThrow();
        assertEquals(IssueStatus.RESOLVED, resolved.status());
        assertEquals(IssueDisposition.FIXED, resolved.resolution().disposition());
    }

    @Test
    void resolveCanWontfixWithANote() {
        final String id = record("a problem");

        assertEquals(0, run("--resolve", id, "--wontfix", "--note", "by design"));
        final Issue resolved = journal.find(id).orElseThrow();
        assertEquals(IssueDisposition.WONTFIX, resolved.resolution().disposition());
        assertEquals("by design", resolved.resolution().note());
    }

    @Test
    void resolvingAnUnknownIssueAnswersOne() {
        assertEquals(1, run("--resolve", "JC-00000000"));
        assertTrue(err().contains("no issue with id JC-00000000"), err());
    }

    @Test
    void resolveWithoutAnIdIsAUsageError() {
        assertEquals(64, run("--resolve"));
        assertTrue(err().contains("--resolve needs an issue id"), err());
    }

    @Test
    void resolveWithTwoIdsIsAUsageError() {
        assertEquals(64, run("--resolve", "JC-11111111", "JC-22222222"));
        assertTrue(err().contains("--resolve takes one issue id"), err());
    }

    @Test
    void resolveWithoutANoteTextIsAUsageError() {
        final String id = record("a problem");
        assertEquals(64, run("--resolve", id, "--note"));
        assertTrue(err().contains("--note needs text"), err());
    }

    // ---- Interactive commands, one token at a time -------------------------------------------------

    @Test
    void theHelpTokenPrintsTheHelp() {
        assertTrue(cli().execute("help"));
        assertTrue(out().contains("Tokens are calculator buttons"), out());
    }

    @Test
    void theVersionTokenPrintsTheVersion() {
        assertTrue(cli().execute("version"));
        assertEquals(AppInfo.displayVersion(), out().trim());
    }

    @Test
    void theModeTokenPrintsTheActivePage() {
        assertTrue(cli().execute("mode"));
        assertEquals("STANDARD", out().trim());
    }

    @Test
    void theHistoryTokenPrintsCompletedCalculations() {
        final CalculatorCli cli = cli();
        cli.execute("2");
        cli.execute("+");
        cli.execute("3");
        cli.execute("=");

        assertTrue(cli.execute("history"));
        assertTrue(out().contains("2 + 3= 5"), out());
    }

    @Test
    void theMemoryTokenPrintsMemorizedValues() {
        final CalculatorCli cli = cli();
        cli.execute("5");
        cli.execute("ms");

        assertTrue(cli.execute("memory"));
        assertTrue(out().contains("5"), out());
    }

    @Test
    void theBaseTokensSwitchTheProgrammerBase() {
        final CalculatorCli cli = cli();
        assertTrue(cli.execute("hex"));
        assertEquals(16, cli.manager().engine().radix());
        assertTrue(cli.execute("oct"));
        assertEquals(8, cli.manager().engine().radix());
        assertTrue(cli.execute("bin"));
        assertEquals(2, cli.manager().engine().radix());
        assertTrue(cli.execute("dec"));
        assertEquals(10, cli.manager().engine().radix());
    }

    @Test
    void theWidthTokensSwitchTheWordWidth() {
        final CalculatorCli cli = cli();
        // The word width only exists on the programmer page, as the original's does.
        assertTrue(cli.execute("programmer"));
        assertTrue(cli.execute("dword"));
        assertEquals(NumberWidth.DWORD, cli.manager().engine().width());
        assertTrue(cli.execute("word"));
        assertEquals(NumberWidth.WORD, cli.manager().engine().width());
        assertTrue(cli.execute("byte"));
        assertEquals(NumberWidth.BYTE, cli.manager().engine().width());
        assertTrue(cli.execute("qword"));
        assertEquals(NumberWidth.QWORD, cli.manager().engine().width());
    }

    @Test
    void theAngleTokensSwitchTheAngleUnit() {
        final CalculatorCli cli = cli();
        assertTrue(cli.execute("rad"));
        assertEquals(AngleType.RADIANS, cli.manager().engine().angleType());
        assertTrue(cli.execute("grad"));
        assertEquals(AngleType.GRADIANS, cli.manager().engine().angleType());
        assertTrue(cli.execute("deg"));
        assertEquals(AngleType.DEGREES, cli.manager().engine().angleType());
    }

    @Test
    void theQuitTokenIsAccepted() {
        assertTrue(cli().execute("quit"));
        assertTrue(cli().execute("exit"));
    }

    @Test
    void aPageTokenSwitchesTheCalculator() {
        final CalculatorCli cli = cli();
        assertTrue(cli.execute("programmer"));
        assertEquals(CalculatorMode.PROGRAMMER, cli.manager().mode());
        assertTrue(cli.execute("scientific"));
        assertEquals(CalculatorMode.SCIENTIFIC, cli.manager().mode());
        assertTrue(cli.execute("standard"));
        assertEquals(CalculatorMode.STANDARD, cli.manager().mode());
    }

    @Test
    void setModeAcceptsTheAliasBasic() {
        final CalculatorCli cli = cli();
        cli.setMode("basic");
        assertEquals(CalculatorMode.STANDARD, cli.manager().mode());
    }

    @Test
    void theJournalIsExposedForOtherFrontEnds() {
        final CalculatorCli cli = cli();
        assertEquals(journal, cli.journal());
        assertEquals(CalculatorMode.STANDARD, cli.manager().mode());
    }

    // ---- Interactive mode -------------------------------------------------------------------------

    private int runInteractive(String script) {
        try (InputStream input = new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8))) {
            return new CalculatorCli(CalculatorMode.STANDARD, journal,
                    new PrintStream(outBytes, true, StandardCharsets.UTF_8),
                    new PrintStream(errBytes, true, StandardCharsets.UTF_8),
                    input).run(List.of());
        } catch (java.io.IOException exception) {
            throw new AssertionError("the scripted input could not be closed", exception);
        }
    }

    @Test
    void theInteractiveLoopEvaluatesATypedLine() {
        assertEquals(0, runInteractive("2 + 3 =\nquit\n"));
        assertTrue(out().contains("a Java calculator"), out());
        assertTrue(out().contains("> "), out());
        assertTrue(out().contains("5"), out());
    }

    @Test
    void theInteractiveLoopShowsAnErrorInsteadOfTheDisplay() {
        assertEquals(0, runInteractive("1 / 0 =\nquit\n"));
        assertTrue(out().contains("Cannot divide by zero"), out());
    }

    @Test
    void theInteractiveLoopStopsQuietlyAtEndOfInput() {
        assertEquals(0, runInteractive(""));
        assertTrue(out().contains("a Java calculator"), out());
        assertTrue(err().isBlank(), err());
    }
}
