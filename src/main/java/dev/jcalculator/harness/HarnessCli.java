package dev.jcalculator.harness;

import dev.jcalculator.app.AppInfo;
import dev.jcalculator.engine.CalculatorMode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Headless command-line entry point for automation.
 *
 * <p>This is the machine-facing companion of {@link dev.jcalculator.app.CalculatorCli}: it never
 * prompts, never writes a banner, and reports every result as one JSON document on one line, so a
 * harness can pipe it and parse it without scraping human text.
 *
 * <pre>
 *   java -cp target/classes dev.jcalculator.harness.HarnessCli 2 + 3 '*' 4 =
 *   java -cp target/classes dev.jcalculator.harness.HarnessCli --seed 7 rand
 *   printf '2 + 3 =\n{"op":"convert","category":"length","from":"m","to":"ft","value":"2.5"}\n' \
 *     | java -cp target/classes dev.jcalculator.harness.HarnessCli --batch
 * </pre>
 *
 * <p>Exit codes: {@code 0} success, {@code 1} calculation error, {@code 2} malformed request,
 * {@code 64} bad command line, {@code 70} unexpected internal failure.
 */
public final class HarnessCli {

    private static final String HELP = """
            Usage: HarnessCli [options] [tokens...]

              --seed N            seed the random key (default 0)
              --mode MODE         standard, scientific or programmer (default standard)
              --batch, --stdin    read requests from standard input, one per line
              --text              print the display instead of JSON
              --json              print one JSON response per request (default)
              --version, -V       print the version
              --help, -h          print this help

            Without --batch, the tokens on the command line are pressed once and one
            response is printed. With --batch, every input line is either a bare token
            sequence or a JSON request, and every line produces one JSON response.

            Exit codes: 0 success, 1 calculation error, 2 malformed request, 64 usage.
            """;

    private HarnessCli() {
    }

    public static void main(String[] args) {
        final BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        final PrintWriter out = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), false);
        final int exit = run(args, in, out, System.err);
        out.flush();
        if (exit != HarnessProtocol.EXIT_OK) {
            System.exit(exit);
        }
    }

    /**
     * Runs the harness command line.
     *
     * <p>Exposed separately from {@link #main(String[])} so tests can drive it without forking a JVM
     * and without {@code System.exit}.
     *
     * @return the process exit code
     */
    public static int run(String[] args, BufferedReader in, PrintWriter out, PrintStream err) {
        long seed = 0L;
        CalculatorMode mode = CalculatorMode.STANDARD;
        boolean batch = false;
        boolean text = false;
        final List<String> tokens = new ArrayList<>();

        int index = 0;
        while (index < args.length) {
            final String arg = args[index];
            switch (arg) {
                case "--version", "-V" -> {
                    out.println(AppInfo.displayVersion());
                    out.flush();
                    return HarnessProtocol.EXIT_OK;
                }
                case "--help", "-h" -> {
                    out.print(HELP);
                    out.flush();
                    return HarnessProtocol.EXIT_OK;
                }
                case "--seed" -> {
                    if (index + 1 >= args.length) {
                        return usage(err, "--seed needs a number");
                    }
                    try {
                        seed = Long.parseLong(args[index + 1]);
                    } catch (NumberFormatException exception) {
                        return usage(err, "invalid seed: " + args[index + 1]);
                    }
                    index += 2;
                }
                case "--mode" -> {
                    if (index + 1 >= args.length) {
                        return usage(err, "--mode needs standard, scientific or programmer");
                    }
                    try {
                        mode = Tokens.modeFor(args[index + 1]);
                    } catch (IllegalArgumentException exception) {
                        return usage(err, exception.getMessage());
                    }
                    index += 2;
                }
                case "--batch", "--stdin" -> {
                    batch = true;
                    index++;
                }
                case "--text" -> {
                    text = true;
                    index++;
                }
                case "--json" -> {
                    text = false;
                    index++;
                }
                case "--" -> {
                    for (int rest = index + 1; rest < args.length; rest++) {
                        tokens.add(args[rest]);
                    }
                    index = args.length;
                }
                default -> {
                    if (arg.startsWith("--") || isShortOption(arg)) {
                        return usage(err, "unknown option: " + arg);
                    }
                    tokens.add(arg);
                    index++;
                }
            }
        }

        final HarnessProtocol protocol = new HarnessProtocol(new CalculatorSession(mode, seed));
        if (batch) {
            if (!tokens.isEmpty()) {
                return usage(err, "--batch does not take tokens on the command line");
            }
            return batch(protocol, in, out, err);
        }
        if (tokens.isEmpty()) {
            err.print(HELP);
            return HarnessProtocol.EXIT_USAGE;
        }
        return oneShot(protocol, tokens, text, out, err);
    }

    private static int batch(HarnessProtocol protocol, BufferedReader in, PrintWriter out, PrintStream err) {
        int worst = HarnessProtocol.EXIT_OK;
        try {
            String line;
            while ((line = in.readLine()) != null) {
                final HarnessProtocol.Outcome outcome = protocol.handleLine(line);
                if (outcome == null) {
                    continue;
                }
                out.println(Json.write(outcome.response()));
                out.flush();
                worst = worse(worst, outcome.exitCode());
            }
        } catch (IOException exception) {
            err.println("jcalculator: input error: " + exception.getMessage());
            return HarnessProtocol.EXIT_INTERNAL_ERROR;
        }
        return worst;
    }

    private static int oneShot(HarnessProtocol protocol, List<String> tokens, boolean text,
                               PrintWriter out, PrintStream err) {
        final HarnessProtocol.Outcome outcome = protocol.execute(
                Json.object("op", "press", "tokens", List.copyOf(tokens)));
        if (text) {
            out.println(outcome.response().get("display"));
        } else {
            out.println(Json.write(outcome.response()));
        }
        out.flush();
        if (outcome.exitCode() != HarnessProtocol.EXIT_OK) {
            final Object message = outcome.response().get("message") != null
                    ? outcome.response().get("message")
                    : outcome.response().get("errorMessage");
            err.println("jcalculator: " + message);
        }
        return outcome.exitCode();
    }

    private static boolean isShortOption(String arg) {
        return arg.length() == 2 && arg.charAt(0) == '-' && arg.charAt(1) != '-';
    }

    private static int usage(PrintStream err, String message) {
        err.println("jcalculator: " + message);
        err.print(HELP);
        return HarnessProtocol.EXIT_USAGE;
    }

    private static int worse(int current, int candidate) {
        return rank(candidate) > rank(current) ? candidate : current;
    }

    private static int rank(int exitCode) {
        return switch (exitCode) {
            case HarnessProtocol.EXIT_OK -> 0;
            case HarnessProtocol.EXIT_CALCULATION_ERROR -> 1;
            case HarnessProtocol.EXIT_PROTOCOL_ERROR -> 2;
            default -> 3;
        };
    }
}
