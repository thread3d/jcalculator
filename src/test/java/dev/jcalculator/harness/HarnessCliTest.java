package dev.jcalculator.harness;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HarnessCliTest {

    @Test
    void oneShotPrintsOneJsonResponse() {
        final String output = runWithStdout("2", "+", "3", "=");
        assertTrue(output.startsWith("{"), output);
        assertTrue(output.contains("\"display\":\"5\""), output);
        assertTrue(output.contains("\"ok\":true"), output);
    }

    @Test
    void textModePrintsOnlyTheDisplay() {
        assertEquals("5\n", runWithStdout("--text", "2", "+", "3", "="));
    }

    @Test
    void versionOptionPrintsTheVersion() {
        assertEquals(dev.jcalculator.app.AppInfo.displayVersion() + "\n", runWithStdout("--version"));
    }

    @Test
    void unknownTokenExitsWithTheProtocolCode() {
        final Run run = run("2", "+", "bogus", "=");
        assertEquals(HarnessProtocol.EXIT_PROTOCOL_ERROR, run.exit);
        assertTrue(run.out.contains("\"errorCode\":\"unknown_token\""), run.out);
    }

    @Test
    void calculatorErrorExitsWithTheCalculationCode() {
        final Run run = run("1", "/", "0", "=");
        assertEquals(HarnessProtocol.EXIT_CALCULATION_ERROR, run.exit);
        assertTrue(run.out.contains("\"errorCode\":\"calculator_error\""), run.out);
    }

    @Test
    void noArgumentsIsAUsageError() {
        final Run run = run();
        assertEquals(HarnessProtocol.EXIT_USAGE, run.exit);
        assertTrue(run.err.contains("Usage"), run.err);
    }

    @Test
    void unknownOptionIsAUsageError() {
        final Run run = run("--nope");
        assertEquals(HarnessProtocol.EXIT_USAGE, run.exit);
    }

    @Test
    void batchAnswersEveryLine() {
        final String input = "2 + 3 =\n"
                + "{\"id\":2,\"op\":\"convert\",\"category\":\"length\",\"from\":\"m\",\"to\":\"ft\",\"value\":\"2.5\"}\n";
        final Run run = runWithInput(input, "--batch");
        final String[] lines = run.out.strip().split("\n");
        assertEquals(2, lines.length, run.out);
        assertTrue(lines[0].contains("\"display\":\"5\""), lines[0]);
        assertTrue(lines[1].contains("\"result\":\"8.2021\""), lines[1]);
    }

    @Test
    void batchExitCodeReportsTheWorstResult() {
        final Run run = runWithInput("2 + 3 =\nbogus\n", "--batch");
        assertEquals(HarnessProtocol.EXIT_PROTOCOL_ERROR, run.exit);
    }

    @Test
    void batchDoesNotAcceptCommandLineTokens() {
        final Run run = runWithInput("2 + 3 =\n", "--batch", "7");
        assertEquals(HarnessProtocol.EXIT_USAGE, run.exit);
    }

    @Test
    void processExitCodesAreReal() throws Exception {
        final Path classes = Path.of(System.getProperty("basedir", "."), "target", "classes");
        assertTrue(Files.isDirectory(classes), "target/classes must exist before this test runs");

        assertEquals(0, runProcess(classes, "2", "+", "3", "="));
        assertEquals(1, runProcess(classes, "1", "/", "0", "="));
        assertEquals(2, runProcess(classes, "2", "+", "bogus", "="));
        assertEquals(64, runProcess(classes));
    }

    // ---- Helpers ---------------------------------------------------------------------------------

    private record Run(int exit, String out, String err) {
    }

    private static String runWithStdout(String... args) {
        return run(args).out;
    }

    private static Run runWithInput(String input, String... args) {
        try (StringWriter out = new StringWriter();
             ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
             BufferedReader in = new BufferedReader(new StringReader(input));
             PrintWriter writer = new PrintWriter(out, true);
             PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8)) {
            final int exit = HarnessCli.run(args, in, writer, err);
            return new Run(exit, out.toString(), errBytes.toString(StandardCharsets.UTF_8));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Run run(String... args) {
        return runWithInput("", args);
    }

    private static int runProcess(Path classes, String... args) throws Exception {
        final String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        final String[] command = new String[args.length + 4];
        command[0] = java;
        command[1] = "-cp";
        command[2] = classes.toString();
        command[3] = "dev.jcalculator.harness.HarnessCli";
        System.arraycopy(args, 0, command, 4, args.length);
        final ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().put("JCALC_ISSUES", "off");
        final Process process = builder.start();
        assertTrue(process.waitFor(30, TimeUnit.SECONDS), "the harness process must finish");
        return process.exitValue();
    }
}
