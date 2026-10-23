package dev.jcalculator.app;

import dev.jcalculator.convert.UnitCategory;
import dev.jcalculator.convert.UnitConverter;
import dev.jcalculator.core.AngleType;
import dev.jcalculator.core.Rational;
import dev.jcalculator.date.DateCalculationEngine;
import dev.jcalculator.date.DateDifference;
import dev.jcalculator.date.DateUnit;
import dev.jcalculator.engine.CalculatorMode;
import dev.jcalculator.engine.Command;
import dev.jcalculator.engine.HistoryEntry;
import dev.jcalculator.engine.NumberWidth;
import dev.jcalculator.manager.CalculatorManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    private final CalculatorManager manager;
    private boolean running = true;

    public CalculatorCli(CalculatorMode mode) {
        manager = new CalculatorManager(mode);
    }

    public static void main(String[] args) {
        final CalculatorCli cli = new CalculatorCli(CalculatorMode.STANDARD);
        final List<String> tokens = new ArrayList<>(List.of(args));

        if (!tokens.isEmpty() && ("--version".equals(tokens.get(0)) || "-V".equals(tokens.get(0)))) {
            System.out.println(AppInfo.displayVersion());
            return;
        }

        if (tokens.isEmpty()) {
            cli.interactive();
            return;
        }

        boolean calculatorCommandsUsed = false;
        int index = 0;
        while (index < tokens.size()) {
            final String token = tokens.get(index);
            switch (token) {
                case "--mode" -> {
                    if (index + 1 >= tokens.size()) {
                        fail("--mode needs standard, scientific or programmer");
                    }
                    cli.setMode(tokens.get(index + 1));
                    index += 2;
                }
                case "--convert" -> {
                    if (index + 4 >= tokens.size()) {
                        fail("--convert needs <category> <from> <to> <value>");
                    }
                    System.out.println(cli.convert(tokens.get(index + 1), tokens.get(index + 2),
                            tokens.get(index + 3), tokens.get(index + 4)));
                    index += 5;
                }
                case "--date-diff" -> {
                    if (index + 2 >= tokens.size()) {
                        fail("--date-diff needs <from-date> <to-date>");
                    }
                    System.out.println(cli.dateDifference(tokens.get(index + 1), tokens.get(index + 2)));
                    index += 3;
                }
                case "--date-add" -> {
                    if (index + 4 >= tokens.size()) {
                        fail("--date-add needs <date> <years> <months> <days>");
                    }
                    System.out.println(cli.dateAdd(tokens.get(index + 1), tokens.get(index + 2),
                            tokens.get(index + 3), tokens.get(index + 4), true));
                    index += 5;
                }
                case "--date-subtract" -> {
                    if (index + 4 >= tokens.size()) {
                        fail("--date-subtract needs <date> <years> <months> <days>");
                    }
                    System.out.println(cli.dateAdd(tokens.get(index + 1), tokens.get(index + 2),
                            tokens.get(index + 3), tokens.get(index + 4), false));
                    index += 5;
                }
                default -> {
                    cli.execute(token);
                    calculatorCommandsUsed = true;
                    index++;
                }
            }
        }
        if (calculatorCommandsUsed) {
            System.out.println(cli.manager.primaryDisplay());
        }
    }

    private static void fail(String message) {
        System.err.println("jcalculator: " + message);
        System.exit(64);
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

        final Command command = commandFor(normalized);
        if (command == null) {
            System.err.println("unknown token: " + token);
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

    private static Command commandFor(String token) {
        if (token.length() == 1) {
            final char c = token.charAt(0);
            if (c >= '0' && c <= '9') {
                return Command.valueOf(
                        switch (c) {
                            case '0' -> "ZERO";
                            case '1' -> "ONE";
                            case '2' -> "TWO";
                            case '3' -> "THREE";
                            case '4' -> "FOUR";
                            case '5' -> "FIVE";
                            case '6' -> "SIX";
                            case '7' -> "SEVEN";
                            case '8' -> "EIGHT";
                            default -> "NINE";
                        });
            }
            if (c >= 'a' && c <= 'f') {
                return Command.valueOf(String.valueOf(Character.toUpperCase(c)));
            }
        }
        return switch (token) {
            case "+", "add" -> Command.ADD;
            case "-", "sub", "subtract" -> Command.SUBTRACT;
            case "*", "x", "×", "mul", "multiply" -> Command.MULTIPLY;
            case "/", "÷", "div", "divide" -> Command.DIVIDE;
            case "^", "pow", "power" -> Command.POWER;
            case "yroot", "root" -> Command.ROOT;
            case "mod" -> Command.MOD;
            case "and" -> Command.AND;
            case "or" -> Command.OR;
            case "xor" -> Command.XOR;
            case "nand" -> Command.NAND;
            case "nor" -> Command.NOR;
            case "lsh", "<<" -> Command.LEFT_SHIFT;
            case "rsh", ">>" -> Command.RIGHT_SHIFT;
            case "=" , "equals", "equ" -> Command.EQUALS;
            case "." -> Command.DECIMAL_POINT;
            case "exp" -> Command.EXPONENT;
            case "+/-", "negate", "sign" -> Command.NEGATE;
            case "back", "backspace" -> Command.BACKSPACE;
            case "clear", "c" -> Command.CLEAR;
            case "ce" -> Command.CLEAR_ENTRY;
            case "(" -> Command.OPEN_PAREN;
            case ")" -> Command.CLOSE_PAREN;
            case "sin" -> Command.SIN;
            case "cos" -> Command.COS;
            case "tan" -> Command.TAN;
            case "asin" -> Command.ASIN;
            case "acos" -> Command.ACOS;
            case "atan" -> Command.ATAN;
            case "sinh" -> Command.SINH;
            case "cosh" -> Command.COSH;
            case "tanh" -> Command.TANH;
            case "sec" -> Command.SEC;
            case "csc" -> Command.CSC;
            case "cot" -> Command.COT;
            case "sech" -> Command.SECH;
            case "csch" -> Command.CSCH;
            case "coth" -> Command.COTH;
            case "asinh" -> Command.ASINH;
            case "acosh" -> Command.ACOSH;
            case "atanh" -> Command.ATANH;
            case "ln" -> Command.LN;
            case "log" -> Command.LOG;
            case "sqrt", "√" -> Command.SQRT;
            case "sqr" -> Command.SQR;
            case "cube" -> Command.CUBE;
            case "cbrt", "cuberoot" -> Command.CUBEROOT;
            case "fact", "!" -> Command.FACTORIAL;
            case "inv", "1/x" -> Command.RECIPROCAL;
            case "percent", "%" -> Command.PERCENT;
            case "int", "integer" -> Command.INTEGER_PART;
            case "frac" -> Command.FRACTIONAL_PART;
            case "abs" -> Command.ABS;
            case "ceil" -> Command.CEIL;
            case "floor" -> Command.FLOOR;
            case "dms" -> Command.DMS;
            case "degrees" -> Command.DEGREES;
            case "10^" -> Command.POW10;
            case "2^" -> Command.POW2;
            case "pi", "π" -> Command.PI;
            case "e" -> Command.EULER;
            case "rand" -> Command.RANDOM;
            case "ms" -> Command.MEMORY_STORE;
            case "mr" -> Command.MEMORY_RECALL;
            case "m+" -> Command.MEMORY_ADD;
            case "m-" -> Command.MEMORY_SUBTRACT;
            case "mc" -> Command.MEMORY_CLEAR;
            case "inv-toggle" -> Command.INVERSE;
            case "rol" -> Command.ROTATE_LEFT;
            case "ror" -> Command.ROTATE_RIGHT;
            case "not", "com" -> Command.COMPLEMENT;
            default -> null;
        };
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
                """);
    }

    // ---- Accessors used by the GUI and by the executable specifications ------------------------

    public CalculatorManager manager() {
        return manager;
    }
}
