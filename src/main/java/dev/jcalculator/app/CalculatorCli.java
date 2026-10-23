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
import java.io.InputStreamReader;
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
        manager = new CalculatorManager(mode);
        this.journal = Objects.requireNonNull(journal, "journal");
    }

    public static void main(String[] args) {
        final CalculatorCli cli = new CalculatorCli(CalculatorMode.STANDARD);
        final List<String> tokens = new ArrayList<>(List.of(args));
        try {
            if (tokens.isEmpty()) {
                cli.interactive();
                return;
            }
            switch (tokens.get(0)) {
                case "--version", "-V" -> System.out.println(AppInfo.displayVersion());
                case "--help", "-h" -> printHelp();
                case "--issues" -> cli.printIssues(tokens.subList(1, tokens.size()));
                case "--report" -> cli.printReport(tokens.subList(1, tokens.size()));
                case "--resolve" -> cli.printResolve(tokens.subList(1, tokens.size()));
                default -> cli.runTokens(tokens);
            }
        } catch (IllegalArgumentException exception) {
            fail(exception.getMessage());
        } catch (RuntimeException exception) {
            cli.recordInternalFailure(exception);
            System.err.println("jcalculator: internal error: " + exception.getMessage());
            System.exit(70);
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
                    System.out.println(convert(tokens.get(index + 1), tokens.get(index + 2),
                            tokens.get(index + 3), tokens.get(index + 4)));
                    index += 5;
                }
                case "--date-diff" -> {
                    if (index + 2 >= tokens.size()) {
                        fail("--date-diff needs <from-date> <to-date>");
                    }
                    System.out.println(dateDifference(tokens.get(index + 1), tokens.get(index + 2)));
                    index += 3;
                }
                case "--date-add" -> {
                    if (index + 4 >= tokens.size()) {
                        fail("--date-add needs <date> <years> <months> <days>");
                    }
                    System.out.println(dateAdd(tokens.get(index + 1), tokens.get(index + 2),
                            tokens.get(index + 3), tokens.get(index + 4), true));
                    index += 5;
                }
                case "--date-subtract" -> {
                    if (index + 4 >= tokens.size()) {
                        fail("--date-subtract needs <date> <years> <months> <days>");
                    }
                    System.out.println(dateAdd(tokens.get(index + 1), tokens.get(index + 2),
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
            System.out.println(manager.primaryDisplay());
        }
    }

    private static void fail(String message) {
        System.err.println("jcalculator: " + message);
        System.exit(64);
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
            System.out.println("The issue journal is disabled.");
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
            System.out.println(all ? "No recorded issues." : "No open issues.");
        }
        for (Issue issue : selected) {
            System.out.println(issue.id() + "  " + issue.status().wireName() + "  "
                    + issue.severity().wireName() + "  [" + String.join(",", issue.surfaces()) + "]  "
                    + issue.occurrences() + "x  " + issue.summary());
            final String detail = firstLine(issue.detail());
            if (!detail.isBlank()) {
                System.out.println("    " + detail);
            }
            if (issue.resolution() != null) {
                final Issue.Resolution resolution = issue.resolution();
                System.out.println("    " + resolution.disposition().wireName()
                        + (resolution.note().isBlank() ? "" : ": " + resolution.note())
                        + " (" + resolution.resolvedAt() + ")");
            }
        }
        System.out.println("Journal: " + journal.location() + " — " + snapshot.openCount()
                + " open, " + snapshot.resolvedCount() + " resolved");
    }

    private void printReport(List<String> options) {
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
            System.err.println("jcalculator: the issue journal is disabled; set "
                    + IssueJournal.PATH_PROPERTY + " or " + IssueJournal.PATH_ENVIRONMENT);
            System.exit(1);
        }
        final String issueId = journal.record(new IssueDraft(
                IssueKind.USER_REPORT, severity, IssueSurface.CLI, summary, detail, Map.of()));
        if (journal.find(issueId).isEmpty()) {
            System.err.println("jcalculator: the issue journal could not be written: " + journal.location());
            System.exit(1);
        }
        final IssueJournal.Snapshot snapshot = journal.read();
        System.out.println("Recorded " + issueId + " — " + snapshot.openCount() + " open issue(s) in "
                + journal.location());
    }

    private void printResolve(List<String> options) {
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
            System.err.println("jcalculator: no issue with id " + id);
            System.exit(1);
        }
        final IssueDisposition resolvedAs = disposition;
        final String issueId = id;
        journal.find(issueId).ifPresent(issue -> System.out.println("Resolved " + issue.id() + " as "
                + resolvedAs.wireName() + ": " + issue.summary()));
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
        System.out.println(AppInfo.displayVersion() + " - a Java calculator");
        System.out.println("Mode: " + manager.mode() + ". Type 'help' for commands, 'quit' to exit.");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            while (running) {
                System.out.print(prompt());
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
                    System.out.println(manager.primaryDisplay());
                }
            }
        } catch (IOException exception) {
            System.err.println("input error: " + exception.getMessage());
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
                System.out.println(AppInfo.displayVersion());
                return true;
            }
            case "history" -> {
                for (HistoryEntry entry : manager.history()) {
                    System.out.println(entry.expression() + " " + entry.result());
                }
                return true;
            }
            case "memory" -> {
                System.out.println(manager.memorizedNumbers());
                return true;
            }
            case "mode" -> {
                System.out.println(manager.mode());
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
            System.err.println(Tokens.refusalMessage(token, manager.mode()));
            return false;
        }
        press(command);
        return true;
    }

    private void press(Command command) {
        manager.sendCommand(command);
        if (manager.isError()) {
            System.out.println(manager.primaryDisplay());
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

    private static void printHelp() {
        System.out.println("""
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
