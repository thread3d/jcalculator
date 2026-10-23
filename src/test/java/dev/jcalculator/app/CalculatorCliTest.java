package dev.jcalculator.app;

import dev.jcalculator.diagnostics.Issue;
import dev.jcalculator.diagnostics.IssueJournal;
import dev.jcalculator.diagnostics.IssueKind;
import dev.jcalculator.engine.CalculatorMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculatorCliTest {

    @TempDir
    Path directory;

    @Test
    void unknownTokenIsRecordedFromTheTextInterface() {
        final IssueJournal journal = new IssueJournal(directory.resolve("issues.jsonl"), Clock.systemUTC());
        final CalculatorCli cli = new CalculatorCli(CalculatorMode.STANDARD, journal);
        assertFalse(cli.execute("bogus"));

        final List<Issue> issues = journal.read().issues();
        assertEquals(1, issues.size());
        assertEquals(IssueKind.UNSUPPORTED_INPUT, issues.get(0).kind());
        assertEquals(List.of("cli"), issues.get(0).surfaces());
    }

    @Test
    void knownTokensAreNotRecorded() {
        final IssueJournal journal = new IssueJournal(directory.resolve("issues.jsonl"), Clock.systemUTC());
        final CalculatorCli cli = new CalculatorCli(CalculatorMode.STANDARD, journal);
        assertTrue(cli.execute("2"));
        assertTrue(cli.execute("+"));
        assertTrue(cli.execute("3"));
        assertTrue(cli.execute("="));
        assertEquals("5", cli.manager().primaryDisplay());
        assertTrue(journal.read().issues().isEmpty());
    }

    @Test
    void eIsEulersConstantInScientificModeRatherThanHexadecimalE() {
        final IssueJournal journal = new IssueJournal(directory.resolve("issues.jsonl"), Clock.systemUTC());
        final CalculatorCli cli = new CalculatorCli(CalculatorMode.SCIENTIFIC, journal);
        assertTrue(cli.execute("e"));
        assertTrue(cli.manager().primaryDisplay().startsWith("2.71828"),
                cli.manager().primaryDisplay());
        assertTrue(journal.read().issues().isEmpty());
    }

    @Test
    void aHexLetterOutsideTheProgrammerPageIsRefusedAndRecorded() {
        final IssueJournal journal = new IssueJournal(directory.resolve("issues.jsonl"), Clock.systemUTC());
        final CalculatorCli cli = new CalculatorCli(CalculatorMode.STANDARD, journal);
        assertFalse(cli.execute("a"));
        assertEquals("0", cli.manager().primaryDisplay());

        final List<Issue> issues = journal.read().issues();
        assertEquals(1, issues.size());
        assertEquals(IssueKind.UNSUPPORTED_INPUT, issues.get(0).kind());
        assertEquals("a", issues.get(0).context().get("token"));
    }

    @Test
    void reportIssuesAndResolveThroughTheCommandLine() throws Exception {
        final Path journal = directory.resolve("issues.jsonl");

        final String reported = runCli(journal, "--report", "2 + 2 displayed 5", "--detail", "Expected 4");
        assertTrue(reported.contains("Recorded JC-"), reported);

        final String listed = runCli(journal, "--issues");
        assertTrue(listed.contains("2 + 2 displayed 5"), listed);
        final String id = firstLine(listed).split("\\s+")[0];
        assertTrue(id.startsWith("JC-"), id);

        final String resolved = runCli(journal, "--resolve", id, "--note", "fixed in a test");
        assertTrue(resolved.contains("Resolved " + id), resolved);

        final String afterResolution = runCli(journal, "--issues");
        assertTrue(afterResolution.contains("No open issues."), afterResolution);
        assertTrue(runCli(journal, "--issues", "--all").contains("resolved"));
    }

    @Test
    void theHelpMentionsTheIssueCommands() throws Exception {
        final String help = runCli(directory.resolve("unused.jsonl"), "--help");
        assertTrue(help.contains("--issues"), help);
        assertTrue(help.contains("--report"), help);
        assertTrue(help.contains("--resolve"), help);
    }

    private static String firstLine(String text) {
        return text.lines().findFirst().orElse("");
    }

    private static String runCli(Path journal, String... args) throws Exception {
        final Path classes = Path.of(System.getProperty("basedir", "."), "target", "classes");
        assertTrue(java.nio.file.Files.isDirectory(classes), "target/classes must exist before this test runs");
        final String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        final String[] command = new String[args.length + 4];
        command[0] = java;
        command[1] = "-cp";
        command[2] = classes.toString();
        command[3] = CalculatorCli.class.getName();
        System.arraycopy(args, 0, command, 4, args.length);
        final ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().put(IssueJournal.PATH_ENVIRONMENT, journal.toAbsolutePath().toString());
        final Process process = builder.start();
        process.getOutputStream().close();
        assertTrue(process.waitFor(30, TimeUnit.SECONDS), "the CLI process must finish");
        final String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);
        return output;
    }
}
