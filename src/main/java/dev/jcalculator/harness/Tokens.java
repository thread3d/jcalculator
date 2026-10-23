package dev.jcalculator.harness;

import dev.jcalculator.engine.CalculatorMode;
import dev.jcalculator.engine.Command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * The calculator's token language: one token per button, shared by every front end.
 *
 * <p>{@link dev.jcalculator.app.CalculatorCli}, the harness protocol and the MCP tools must accept
 * exactly the same vocabulary, so the mapping lives here once. A token is matched
 * case-insensitively. Single characters {@code 0}-{@code 9} are digits and {@code a}-{@code f} are
 * hexadecimal digits; everything else is a named key.
 *
 * <p>The table is an enum rather than a static map so that it is genuinely immutable: there is no
 * global state a caller could change, and two runs cannot observe different vocabularies.
 */
public final class Tokens {

    private enum Token {
        DECIMAL_POINT(".", Command.DECIMAL_POINT),
        EXPONENT("exp", Command.EXPONENT),
        NEGATE("+/-", Command.NEGATE),
        NEGATE_2("negate", Command.NEGATE),
        NEGATE_3("sign", Command.NEGATE),
        BACKSPACE("back", Command.BACKSPACE),
        BACKSPACE_2("backspace", Command.BACKSPACE),
        CLEAR("clear", Command.CLEAR),
        CLEAR_ENTRY("ce", Command.CLEAR_ENTRY),
        ADD("+", Command.ADD),
        ADD_2("add", Command.ADD),
        SUBTRACT("-", Command.SUBTRACT),
        SUBTRACT_2("sub", Command.SUBTRACT),
        SUBTRACT_3("subtract", Command.SUBTRACT),
        MULTIPLY("*", Command.MULTIPLY),
        MULTIPLY_2("x", Command.MULTIPLY),
        MULTIPLY_3("×", Command.MULTIPLY),
        MULTIPLY_4("mul", Command.MULTIPLY),
        MULTIPLY_5("multiply", Command.MULTIPLY),
        DIVIDE("/", Command.DIVIDE),
        DIVIDE_2("÷", Command.DIVIDE),
        DIVIDE_3("div", Command.DIVIDE),
        DIVIDE_4("divide", Command.DIVIDE),
        POWER("^", Command.POWER),
        POWER_2("pow", Command.POWER),
        POWER_3("power", Command.POWER),
        ROOT("yroot", Command.ROOT),
        ROOT_2("root", Command.ROOT),
        MOD("mod", Command.MOD),
        AND("and", Command.AND),
        OR("or", Command.OR),
        XOR("xor", Command.XOR),
        NAND("nand", Command.NAND),
        NOR("nor", Command.NOR),
        LEFT_SHIFT("lsh", Command.LEFT_SHIFT),
        LEFT_SHIFT_2("<<", Command.LEFT_SHIFT),
        RIGHT_SHIFT("rsh", Command.RIGHT_SHIFT),
        RIGHT_SHIFT_2(">>", Command.RIGHT_SHIFT),
        EQUALS("=", Command.EQUALS),
        EQUALS_2("equals", Command.EQUALS),
        EQUALS_3("equ", Command.EQUALS),
        OPEN_PAREN("(", Command.OPEN_PAREN),
        CLOSE_PAREN(")", Command.CLOSE_PAREN),
        SIN("sin", Command.SIN),
        COS("cos", Command.COS),
        TAN("tan", Command.TAN),
        ASIN("asin", Command.ASIN),
        ACOS("acos", Command.ACOS),
        ATAN("atan", Command.ATAN),
        SINH("sinh", Command.SINH),
        COSH("cosh", Command.COSH),
        TANH("tanh", Command.TANH),
        SEC("sec", Command.SEC),
        CSC("csc", Command.CSC),
        COT("cot", Command.COT),
        SECH("sech", Command.SECH),
        CSCH("csch", Command.CSCH),
        COTH("coth", Command.COTH),
        ASINH("asinh", Command.ASINH),
        ACOSH("acosh", Command.ACOSH),
        ATANH("atanh", Command.ATANH),
        LN("ln", Command.LN),
        LOG("log", Command.LOG),
        SQRT("sqrt", Command.SQRT),
        SQRT_2("√", Command.SQRT),
        SQR("sqr", Command.SQR),
        CUBE("cube", Command.CUBE),
        CUBEROOT("cbrt", Command.CUBEROOT),
        CUBEROOT_2("cuberoot", Command.CUBEROOT),
        FACTORIAL("fact", Command.FACTORIAL),
        FACTORIAL_2("!", Command.FACTORIAL),
        RECIPROCAL("inv", Command.RECIPROCAL),
        RECIPROCAL_2("1/x", Command.RECIPROCAL),
        PERCENT("percent", Command.PERCENT),
        PERCENT_2("%", Command.PERCENT),
        INTEGER_PART("int", Command.INTEGER_PART),
        INTEGER_PART_2("integer", Command.INTEGER_PART),
        FRACTIONAL_PART("frac", Command.FRACTIONAL_PART),
        ABS("abs", Command.ABS),
        CEIL("ceil", Command.CEIL),
        FLOOR("floor", Command.FLOOR),
        DMS("dms", Command.DMS),
        DEGREES("degrees", Command.DEGREES),
        POW10("10^", Command.POW10),
        POW2("2^", Command.POW2),
        PI("pi", Command.PI),
        PI_2("π", Command.PI),
        RANDOM("rand", Command.RANDOM),
        INVERSE("inv-toggle", Command.INVERSE),
        ROTATE_LEFT("rol", Command.ROTATE_LEFT),
        ROTATE_RIGHT("ror", Command.ROTATE_RIGHT),
        COMPLEMENT("not", Command.COMPLEMENT),
        COMPLEMENT_2("com", Command.COMPLEMENT),
        MEMORY_STORE("ms", Command.MEMORY_STORE),
        MEMORY_RECALL("mr", Command.MEMORY_RECALL),
        MEMORY_ADD("m+", Command.MEMORY_ADD),
        MEMORY_SUBTRACT("m-", Command.MEMORY_SUBTRACT),
        MEMORY_CLEAR("mc", Command.MEMORY_CLEAR),
        RADIX_16("hex", Command.RADIX_16),
        RADIX_10("dec", Command.RADIX_10),
        RADIX_8("oct", Command.RADIX_8),
        RADIX_2("bin", Command.RADIX_2),
        WIDTH_QWORD("qword", Command.WIDTH_QWORD),
        WIDTH_DWORD("dword", Command.WIDTH_DWORD),
        WIDTH_WORD("word", Command.WIDTH_WORD),
        WIDTH_BYTE("byte", Command.WIDTH_BYTE),
        ANGLE_DEGREES("deg", Command.ANGLE_DEGREES),
        ANGLE_RADIANS("rad", Command.ANGLE_RADIANS),
        ANGLE_GRADIANS("grad", Command.ANGLE_GRADIANS),
        MODE_STANDARD("standard", Command.MODE_STANDARD),
        MODE_STANDARD_2("basic", Command.MODE_STANDARD),
        MODE_SCIENTIFIC("scientific", Command.MODE_SCIENTIFIC),
        MODE_PROGRAMMER("programmer", Command.MODE_PROGRAMMER);

        private final String label;
        private final Command command;

        Token(String label, Command command) {
            this.label = label;
            this.command = command;
        }

        boolean matches(String text) {
            return label.equals(text);
        }
    }

    private Tokens() {
    }

    /**
     * Resolves a token to a command without knowing the calculator page.
     *
     * <p>This is the right call for a token that has one meaning everywhere. Use
     * {@link #commandFor(String, CalculatorMode)} when the page disambiguates, which it does for
     * {@code "c"}.
     *
     * @param token a button name such as {@code "7"}, {@code "+"} or {@code "sqrt"}
     * @return the command, or {@code null} when the token is not a button
     */
    public static Command commandFor(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        final String normalized = token.toLowerCase(Locale.ROOT);
        if (normalized.length() == 1) {
            final char c = normalized.charAt(0);
            if (c >= '0' && c <= '9') {
                return Command.values()[Command.ZERO.ordinal() + (c - '0')];
            }
            if (c >= 'a' && c <= 'f') {
                return Command.values()[Command.A.ordinal() + (c - 'a')];
            }
        }
        for (Token entry : Token.values()) {
            if (entry.matches(normalized)) {
                return entry.command;
            }
        }
        return null;
    }

    /**
     * Resolves a token in the context of a calculator page.
     *
     * <p>{@code "c"} is the one token whose meaning depends on the page, because the keys differ:
     * the standard and scientific pages label their clear key {@code C}, while the programmer page
     * uses {@code C} for the hexadecimal digit and offers a separate {@code CLR}. Resolving it
     * against the page keeps every front end agreeing with the keypad in front of the user.
     *
     * @param token a button name
     * @param mode  the active calculator page
     * @return the command, or {@code null} when the token is not a button
     */
    public static Command commandFor(String token, CalculatorMode mode) {
        if (token != null && "c".equalsIgnoreCase(token)) {
            return mode == CalculatorMode.PROGRAMMER ? Command.C : Command.CLEAR;
        }
        return commandFor(token);
    }

    /**
     * Resolves a token in the context of a page, refusing an unknown one.
     *
     * @throws UnknownTokenException when the token is not a button
     */
    public static Command require(String token, CalculatorMode mode) {
        final Command command = commandFor(token, mode);
        if (command == null) {
            throw new UnknownTokenException(token);
        }
        return command;
    }

    /**
     * Resolves a token, refusing an unknown one.
     *
     * @throws UnknownTokenException when the token is not a button
     */
    public static Command require(String token) {
        final Command command = commandFor(token);
        if (command == null) {
            throw new UnknownTokenException(token);
        }
        return command;
    }

    /** Resolves a mode name such as {@code "scientific"}. */
    public static CalculatorMode modeFor(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "standard", "basic" -> CalculatorMode.STANDARD;
            case "scientific" -> CalculatorMode.SCIENTIFIC;
            case "programmer" -> CalculatorMode.PROGRAMMER;
            default -> throw new IllegalArgumentException("unknown mode: " + name);
        };
    }

    /** Every token the protocol accepts, sorted, for capability discovery. */
    public static List<String> supportedTokens() {
        final TreeSet<String> tokens = new TreeSet<>();
        for (Token entry : Token.values()) {
            tokens.add(entry.label);
        }
        for (char digit = '0'; digit <= '9'; digit++) {
            tokens.add(String.valueOf(digit));
        }
        for (char digit = 'a'; digit <= 'f'; digit++) {
            tokens.add(String.valueOf(digit));
        }
        return List.copyOf(tokens);
    }

    /** The declared token names in table order, for documentation and tests. */
    public static List<String> declaredTokens() {
        final List<String> tokens = new ArrayList<>();
        for (Token entry : Token.values()) {
            tokens.add(entry.label);
        }
        return List.copyOf(tokens);
    }
}
