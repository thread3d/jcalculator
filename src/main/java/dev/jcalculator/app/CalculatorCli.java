package dev.jcalculator.app;

import dev.jcalculator.convert.UnitCategory;
import dev.jcalculator.convert.UnitConverter;
import dev.jcalculator.date.DateCalculationEngine;
import dev.jcalculator.date.DateDifference;
import dev.jcalculator.date.DateUnit;
import dev.jcalculator.diagnostics.Faults;
import dev.jcalculator.diagnostics.Issue;
import dev.jcalculator.diagnostics.IssueDisposition;
import dev.jcalculator.diagnostics.IssueDraft;
import dev.jcalculator.diagnostics.IssueJournal;
import dev.jcalculator.diagnostics.IssueKind;
import dev.jcalculator.diagnostics.IssueSeverity;
import dev.jcalculator.diagnostics.IssueSurface;
import dev.jcalculator.engine.CalculatorMode;
import dev.jcalculator.engine.Command;
import dev.jcalculator.engine.HistoryEntry;
import dev.jcalculator.harness.Tokens;
import dev.jcalculator.manager.CalculatorManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Text front end for jCalculator.
 *
 * <p>Run without arguments for an interactive session, or pass tokens to evaluate one sequence:
 *
 * <pre>
 *   java -cp target/classes dev.jcalculator.app.CalculatorCli 2 + 3 * 4 =
 *   java -cp target/classes dev.jcalculator.app.CalculatorCli --mode programmer FF and 0F =
 *   java -cp target/classes dev.jcalculator.app.CalculatorCli --convert length m ft 2.5
 *   java -cp target/classes dev.jcalculator.app.CalculatorCli --date-diff 2024-01-01 2024-12-31
 * </pre>
 *
 * The command language mirrors the keypad: every token is a button press, so the same sequences the
 * application's engine tests use can be replayed here.
 */
public final class CalculatorCli {

    private static final int DEFAULT_ISSUE_LIMIT = 20;

    private final CalculatorManager manager;
    private final IssueJournal journal;
    private final PrintStream out;
    private final PrintStream err;
    private final InputStream in;
    private boolean running = true;

    public CalculatorCli(CalculatorMode mode) {
        this(mode, IssueJournal.shared());
    }

    /**
     * Creates the text interface.
     *
     * @param mode    the calculator page to start on
     * @param journal where problems met in the interface are recorded
     */
    public CalculatorCli(CalculatorMode mode, IssueJournal journal) {
        this(mode, journal, System.out, System.err);
    }

    /**
     * Creates the text interface writing to caller-supplied streams.
     *
     * <p>Tests use this to read what the interface printed without capturing the process's own
     * standard output, exactly as {@code HarnessCli} takes its streams.
     *
     * @param mode    the calculator page to start on
     * @param journal where problems met in the interface are recorded
     * @param out     where ordinary output goes
     * @param err     where refusals and errors go
     */
    public CalculatorCli(CalculatorMode mode, IssueJournal journal, PrintStream out, PrintStream err) {
        this(mode, journal, out, err, System.in);
    }

    /**
     * Creates the text interface over caller-supplied streams, for tests that script the input.
     *
     * @param mode    the calculator page to start on
     * @param journal where problems met in the interface are recorded
     * @param out     where ordinary output goes
     * @param err     where refusals and errors go
     * @param in      the interactive session's input
     */
    public CalculatorCli(CalculatorMode mode, IssueJournal journal, PrintStream out, PrintStream err,
            InputStream in) {
        manager = new CalculatorManager(mode);
        this.journal = Objects.requireNonNull(journal, "journal");
        this.out = Objects.requireNonNull(out, "out");
        this.err = Objects.requireNonNull(err, "err");
        this.in = Objects.requireNonNull(in, "in");
    }

    public static void main(String[] args) {
        final int code = new CalculatorCli(CalculatorMode.STANDARD).run(List.of(args));
        if (code != 0) {
            System.exit(code);
        }
    }

    /**
     * Runs one command line and returns the process exit code instead of exiting.
     *
     * <p>The exit codes are the ones the executable documents: {@code 0} success, {@code 64} a usage
     * error and {@code 70} an unexpected internal failure. The journal commands answer {@code 1} when
     * the journal is disabled, cannot be written, or does not hold the issue being resolved. Keeping
     * the decision here is what lets the tests exercise every path in this process.
     *
     * @param args the command line, without the program name
     */
    public int run(List<String> args) {
        try {
            if (args.isEmpty()) {
                interactive();
                return 0;
            }
            switch (args.get(0)) {
                case "--version", "-V" -> {
                    out.println(AppInfo.displayVersion());
                    return 0;
                }
                case "--help", "-h" -> {
                    printHelp();
                    return 0;
                }
                case "--issues" -> {
                    printIssues(args.subList(1, args.size()));
                    return 0;
                }
                case "--report" -> {
                    return printReport(args.subList(1, args.size()));
                }
                case "--resolve" -> {
                    return printResolve(args.subList(1, args.size()));
                }
                default -> {
                    runTokens(args);
                    return 0;
                }
            }
        } catch (UsageException exception) {
            err.println("jcalculator: " + exception.getMessage());
            return 64;
        } catch (IllegalArgumentException exception) {
            err.println("jcalculator: " + exception.getMessage());
            return 64;
        } catch (DateTimeException exception) {
            // A date the caller mistyped is a usage error, not a fault in the calculator, so it must
            // not be recorded in the issue journal as an unexpected failure.
            err.println("jcalculator: " + exception.getMessage());
            return 64;
        } catch (RuntimeException exception) {
            recordInternalFailure(exception);
            err.println("jcalculator: internal error: " + exception.getMessage());
            return 70;
        }
    }

    /** Runs a sequence of tokens, as a one-shot command line or an interactive line. */
    private void runTokens(List<String> tokens) {
        boolean calculatorCommandsUsed = false;
        int index = 0;
        while (index < tokens.size()) {
            final String token = tokens.get(index);
            switch (token) {
                case "--mode" -> {
                    if (index + 1 >= tokens.size()) {
                        fail("--mode needs standard, scientific or programmer");
                    }
                    setMode(tokens.get(index + 1));
                    index += 2;
                }
                case "--convert" -> {
                    if (index + 4 >= tokens.size()) {
                        fail("--convert needs <category> <from> <to> <value>");
                    }
                    out.println(convert(tokens.get(index + 1), tokens.get(index + 2),
                            tokens.get(index + 3), tokens.get(index + 4)));
                    index += 5;
                }
                case "--date-diff" -> {
                    if (index + 2 >= tokens.size()) {
                        fail("--date-diff needs <from-date> <to-date>");
                    }
                    out.println(dateDifference(tokens.get(index + 1), tokens.get(index + 2)));
                    index += 3;
                }
                case "--date-add" -> {
                    if (index + 4 >= tokens.size()) {
                        fail("--date-add needs <date> <years> <months> <days>");
                    }
                    out.println(dateAdd(tokens.get(index + 1), tokens.get(index + 2),
                            tokens.get(index + 3), tokens.get(index + 4), true));
                    index += 5;
                }
                case "--date-subtract" -> {
                    if (index + 4 >= tokens.size()) {
                        fail("--date-subtract needs <date> <years> <months> <days>");
                    }
                    out.println(dateAdd(tokens.get(index + 1), tokens.get(index + 2),
                            tokens.get(index + 3), tokens.get(index + 4), false));
                    index += 5;
                }
                default -> {
                    execute(token);
                    calculatorCommandsUsed = true;
                    index++;
                }
            }
        }
        if (calculatorCommandsUsed) {
            out.println(manager.primaryDisplay());
        }
    }

    /** Signals a usage error; {@link #run} turns it into exit code 64. */
    private static final class UsageException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        UsageException(String message) {
            super(message);
        }
    }

    private static void fail(String message) {
        throw new UsageException(message);
    }

    // ---- Issue journal commands ---------------------------------------------------------------------

    private void printIssues(List<String> options) {
        boolean all = false;
        int limit = DEFAULT_ISSUE_LIMIT;
        int index = 0;
        while (index < options.size()) {
            switch (options.get(index)) {
                case "--all", "--resolved" -> {
                    all = true;
                    index++;
                }
                case "--limit" -> {
                    if (index + 1 >= options.size()) {
                        fail("--limit needs a number");
                    }
                    limit = parseLimit(options.get(index + 1));
                    index += 2;
                }
                default -> fail("unknown option for --issues: " + options.get(index));
            }
        }
        if (!journal.isEnabled()) {
            out.println("The issue journal is disabled.");
            return;
        }
        final IssueJournal.Snapshot snapshot = journal.read();
        final List<Issue> selected = new ArrayList<>();
        for (Issue issue : snapshot.issues()) {
            if (all || issue.isOpen()) {
                selected.add(issue);
            }
            if (selected.size() >= limit) {
                break;
            }
        }
        if (selected.isEmpty()) {
            out.println(all ? "No recorded issues." : "No open issues.");
        }
        for (Issue issue : selected) {
            out.println(issue.id() + "  " + issue.status().wireName() + "  "
                    + issue.severity().wireName() + "  [" + String.join(",", issue.surfaces()) + "]  "
                    + issue.occurrences() + "x  " + issue.summary());
            final String detail = firstLine(issue.detail());
            if (!detail.isBlank()) {
                out.println("    " + detail);
            }
            if (issue.resolution() != null) {
                final Issue.Resolution resolution = issue.resolution();
                out.println("    " + resolution.disposition().wireName()
                        + (resolution.note().isBlank() ? "" : ": " + resolution.note())
                        + " (" + resolution.resolvedAt() + ")");
            }
        }
        out.println("Journal: " + journal.location() + " — " + snapshot.openCount()
                + " open, " + snapshot.resolvedCount() + " resolved");
    }

    private int printReport(List<String> options) {
        String summary = null;
        String detail = "";
        IssueSeverity severity = IssueSeverity.MEDIUM;
        int index = 0;
        while (index < options.size()) {
            switch (options.get(index)) {
                case "--detail" -> {
                    if (index + 1 >= options.size()) {
                        fail("--detail needs text");
                    }
                    detail = options.get(index + 1);
                    index += 2;
                }
                case "--severity" -> {
                    if (index + 1 >= options.size()) {
                        fail("--severity needs low, medium, high or critical");
                    }
                    severity = IssueSeverity.fromWire(options.get(index + 1));
                    index += 2;
                }
                default -> {
                    if (summary != null) {
                        fail("--report takes one summary; quote it as a single argument");
                    }
                    summary = options.get(index);
                    index++;
                }
            }
        }
        if (summary == null) {
            fail("--report needs a summary");
        }
        if (!journal.isEnabled()) {
            err.println("jcalculator: the issue journal is disabled; set "
                    + IssueJournal.PATH_PROPERTY + " or " + IssueJournal.PATH_ENVIRONMENT);
            return 1;
        }
        final String issueId = journal.record(new IssueDraft(
                IssueKind.USER_REPORT, severity, IssueSurface.CLI, summary, detail, Map.of()));
        if (journal.find(issueId).isEmpty()) {
            err.println("jcalculator: the issue journal could not be written: " + journal.location());
            return 1;
        }
        final IssueJournal.Snapshot snapshot = journal.read();
        out.println("Recorded " + issueId + " — " + snapshot.openCount() + " open issue(s) in "
                + journal.location());
        return 0;
    }

    private int printResolve(List<String> options) {
        String id = null;
        String note = "";
        IssueDisposition disposition = IssueDisposition.FIXED;
        int index = 0;
        while (index < options.size()) {
            switch (options.get(index)) {
                case "--note" -> {
                    if (index + 1 >= options.size()) {
                        fail("--note needs text");
                    }
                    note = options.get(index + 1);
                    index += 2;
                }
                case "--wontfix" -> {
                    disposition = IssueDisposition.WONTFIX;
                    index++;
                }
                default -> {
                    if (id != null) {
                        fail("--resolve takes one issue id");
                    }
                    id = options.get(index);
                    index++;
                }
            }
        }
        if (id == null) {
            fail("--resolve needs an issue id");
        }
        if (!journal.resolve(id, disposition, note)) {
            err.println("jcalculator: no issue with id " + id);
            return 1;
        }
        final IssueDisposition resolvedAs = disposition;
        final String issueId = id;
        journal.find(issueId).ifPresent(issue -> out.println("Resolved " + issue.id() + " as "
                + resolvedAs.wireName() + ": " + issue.summary()));
        return 0;
    }

    private static int parseLimit(String text) {
        try {
            final int limit = Integer.parseInt(text);
            if (limit < 1) {
                fail("--limit must be at least 1");
            }
            return limit;
        } catch (NumberFormatException exception) {
            fail("invalid --limit: " + text);
            return DEFAULT_ISSUE_LIMIT;
        }
    }

    private static String firstLine(String text) {
        return text.lines().findFirst().orElse("");
    }

    private void recordUnknownToken(String token) {
        try {
            journal.record(Faults.unknownToken(IssueSurface.CLI, token, manager.mode()));
        } catch (RuntimeException ignored) {
            // Best effort: an unknown token is still reported on standard error.
        }
    }

    private void recordInternalFailure(RuntimeException exception) {
        try {
            journal.record(Faults.internalError(IssueSurface.CLI, exception,
                    Map.of("surface", IssueSurface.CLI.wireName())));
        } catch (RuntimeException ignored) {
            // Best effort: the failure is still reported on standard error.
        }
    }

    // ---- Interactive ----------------------------------------------------------------------------

    private void interactive() {
        out.println(AppInfo.displayVersion() + " - a Java calculator");
        out.println("Mode: " + manager.mode() + ". Type 'help' for commands, 'quit' to exit.");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            while (running) {
                out.print(prompt());
                final String line = reader.readLine();
                if (line == null) {
                    break;
                }
                for (String token : line.trim().split("\\s+")) {
                    if (token.isEmpty()) {
                        continue;
                    }
                    execute(token);
                    if (!running) {
                        break;
                    }
                }
                if (!manager.isError()) {
                    out.println(manager.primaryDisplay());
                }
            }
        } catch (IOException exception) {
            err.println("input error: " + exception.getMessage());
        }
    }

    private String prompt() {
        final String expression = manager.expression();
        return (expression.isEmpty() ? "" : expression + "  ") + "> ";
    }

    // ---- Token dispatch -------------------------------------------------------------------------

    /** Executes one token; returns false when the token is not a keypad command. */
    public boolean execute(String token) {
        final String normalized = token.toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "help", "?" -> {
                printHelp();
                return true;
            }
            case "quit", "exit" -> {
                running = false;
                return true;
            }
            case "version" -> {
                out.println(AppInfo.displayVersion());
                return true;
            }
            case "history" -> {
                for (HistoryEntry entry : manager.history()) {
                    out.println(entry.expression() + " " + entry.result());
                }
                return true;
            }
            case "memory" -> {
                out.println(manager.memorizedNumbers());
                return true;
            }
            case "mode" -> {
                out.println(manager.mode());
                return true;
            }
            case "standard", "scientific", "programmer" -> {
                setMode(normalized);
                return true;
            }
            case "hex" -> {
                press(Command.RADIX_16);
                return true;
            }
            case "dec" -> {
                press(Command.RADIX_10);
                return true;
            }
            case "oct" -> {
                press(Command.RADIX_8);
                return true;
            }
            case "bin" -> {
                press(Command.RADIX_2);
                return true;
            }
            case "qword" -> {
                press(Command.WIDTH_QWORD);
                return true;
            }
            case "dword" -> {
                press(Command.WIDTH_DWORD);
                return true;
            }
            case "word" -> {
                press(Command.WIDTH_WORD);
                return true;
            }
            case "byte" -> {
                press(Command.WIDTH_BYTE);
                return true;
            }
            case "deg" -> {
                press(Command.ANGLE_DEGREES);
                return true;
            }
            case "rad" -> {
                press(Command.ANGLE_RADIANS);
                return true;
            }
            case "grad" -> {
                press(Command.ANGLE_GRADIANS);
                return true;
            }
            default -> {
                // fall through to keypad handling
            }
        }

        final Command command = Tokens.commandFor(normalized, manager.mode());
        if (command == null) {
            recordUnknownToken(token);
            err.println(Tokens.refusalMessage(token, manager.mode()));
            return false;
        }
        press(command);
        return true;
    }

    private void press(Command command) {
        manager.sendCommand(command);
        if (manager.isError()) {
            out.println(manager.primaryDisplay());
        }
    }

    // ---- Direct services ------------------------------------------------------------------------

    public void setMode(String modeName) {
        final CalculatorMode mode = switch (modeName.toLowerCase(Locale.ROOT)) {
            case "standard", "basic" -> CalculatorMode.STANDARD;
            case "scientific" -> CalculatorMode.SCIENTIFIC;
            case "programmer" -> CalculatorMode.PROGRAMMER;
            default -> throw new IllegalArgumentException("unknown mode: " + modeName);
        };
        manager.setMode(mode);
    }

    public String convert(String categoryName, String from, String to, String value) {
        final UnitConverter converter = new UnitConverter();
        final UnitCategory category = UnitCategory.valueOf(categoryName.toUpperCase(Locale.ROOT));
        return converter.convertAndFormat(category, from, to, value);
    }

    public String dateDifference(String from, String to) {
        final DateCalculationEngine engine = new DateCalculationEngine();
        final DateDifference difference = engine.tryGetDateDifference(LocalDate.parse(from), LocalDate.parse(to), DateUnit.ALL);
        if (difference == null) {
            return "Calculation failed";
        }
        return formatDifference(difference);
    }

    public String dateAdd(String start, String years, String months, String days, boolean add) {
        final DateCalculationEngine engine = new DateCalculationEngine();
        final DateDifference duration = new DateDifference(
                Integer.parseInt(years), Integer.parseInt(months), 0, Integer.parseInt(days));
        try {
            final LocalDate date = LocalDate.parse(start);
            final LocalDate result = add ? engine.addDuration(date, duration) : engine.subtractDuration(date, duration);
            return result == null ? "Date out of bounds" : result.toString();
        } catch (DateTimeParseException exception) {
            return "Invalid date: " + start;
        }
    }

    private static String formatDifference(DateDifference difference) {
        final List<String> parts = new ArrayList<>();
        if (difference.year() != 0) {
            parts.add(difference.year() + (difference.year() == 1 ? " year" : " years"));
        }
        if (difference.month() != 0) {
            parts.add(difference.month() + (difference.month() == 1 ? " month" : " months"));
        }
        if (difference.week() != 0) {
            parts.add(difference.week() + (difference.week() == 1 ? " week" : " weeks"));
        }
        if (difference.day() != 0 || parts.isEmpty()) {
            parts.add(difference.day() + (difference.day() == 1 ? " day" : " days"));
        }
        return String.join(" ", parts);
    }

    private void printHelp() {
        out.println("""
                Tokens are calculator buttons, entered in order:
                  digits 0-9 and A-F      operators + - * / ^ mod and or xor nand nor lsh rsh
                  =  (  )  .  exp  +/-    clear  ce  back
                  sqrt sqr cube cbrt ln log sin cos tan asin acos atan sinh cosh tanh
                  sec csc cot sech csch coth asinh acosh atanh
                  fact 1/x % int frac abs ceil floor dms pi e rand
                  ms mr m+ m- mc
                Settings:
                  standard scientific programmer | hex dec oct bin | qword dword word byte | deg rad grad
                Other:
                  history  memory  help  quit
                Problems met in use (recorded in the project's issue journal):
                  --issues [--all] [--limit N]          list recorded problems
                  --report <summary> [--detail T] [--severity S]   record a problem
                  --resolve <id> [--note T] [--wontfix]            close a problem
                """);
    }

    // ---- Accessors used by the GUI and by the executable specifications ------------------------

    public CalculatorManager manager() {
        return manager;
    }

    /** The journal this interface records problems in. */
    public IssueJournal journal() {
        return journal;
    }
}
