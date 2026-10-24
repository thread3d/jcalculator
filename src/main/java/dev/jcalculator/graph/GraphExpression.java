package dev.jcalculator.graph;

import dev.jcalculator.engine.CalculatorMode;
import dev.jcalculator.engine.Command;
import dev.jcalculator.harness.Tokens;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Reads a function of {@code x} as the button sequence that evaluates it.
 *
 * <p>The graphing page writes a function the way the original's equation box does — {@code x^2},
 * {@code sin(x)}, {@code 1/x} — while the calculator model only understands button order, where a
 * function is pressed after its operand. This class bridges the two: it tokenises the expression
 * with the calculator's own vocabulary and rewrites a prefix function call into the operand followed
 * by the function, which is the order the keypad presses.
 */
public final class GraphExpression {

    /** The variable the plot is sampled over. */
    public static final String VARIABLE = "x";

    private GraphExpression() {
    }

    private static List<String> vocabulary() {
        final List<String> names = new ArrayList<>(Tokens.supportedTokens());
        names.add(VARIABLE);
        names.sort(Comparator.comparingInt(String::length).reversed().thenComparing(name -> name));
        return List.copyOf(names);
    }

    /**
     * Splits an expression into calculator tokens.
     *
     * @param expression the function text
     * @throws IllegalArgumentException when a part of the text is not a button name
     */
    public static List<String> tokenize(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("the expression is empty");
        }
        final String text = expression.toLowerCase(Locale.ROOT).trim();
        final List<String> vocabulary = vocabulary();
        final List<String> tokens = new ArrayList<>();
        int index = 0;
        while (index < text.length()) {
            final char c = text.charAt(index);
            if (Character.isWhitespace(c)) {
                index++;
                continue;
            }
            if (Character.isDigit(c) || " .()+-*/^%".indexOf(c) >= 0) {
                tokens.add(String.valueOf(c));
                index++;
                continue;
            }
            final String match = longestMatch(text, index, vocabulary);
            if (match == null) {
                throw new IllegalArgumentException("not part of a function: " + text.substring(index));
            }
            tokens.add(match);
            index += match.length();
        }
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("the expression has no tokens");
        }
        return List.copyOf(tokens);
    }

    /**
     * Rewrites an expression for one value of {@code x}.
     *
     * <p>The result is in button order: the x value, then the expression, with parentheses kept where
     * they group and removed where they only wrapped a function's argument.
     *
     * @param tokens the expression tokens from {@link #tokenize(String)}
     * @param x      the value to substitute
     * @throws IllegalArgumentException when the parentheses do not balance or a function has no
     *                                  argument
     */
    public static List<String> buttonsFor(List<String> tokens, double x) {
        final List<String> out = new ArrayList<>();
        final int[] position = {0};
        rewrite(tokens, position, out, x);
        if (position[0] != tokens.size()) {
            throw new IllegalArgumentException("unbalanced parentheses in the expression");
        }
        return List.copyOf(out);
    }

    private static void rewrite(List<String> tokens, int[] position, List<String> out, double x) {
        while (position[0] < tokens.size()) {
            final String token = tokens.get(position[0]);
            if (token.equals(")")) {
                return;
            }
            if (isFunction(token)) {
                position[0] += 1;
                final List<String> argument = new ArrayList<>();
                rewriteParenthesised(tokens, position, argument, x);
                out.addAll(argument);
                out.add(token);
                continue;
            }
            if (token.equals("(")) {
                position[0] += 1;
                out.add("(");
                rewrite(tokens, position, out, x);
                require(tokens, position, ")");
                out.add(")");
                continue;
            }
            if (token.equals(VARIABLE)) {
                out.addAll(operandFor(x));
                position[0] += 1;
                continue;
            }
            out.add(token);
            position[0] += 1;
        }
    }

    private static void rewriteParenthesised(List<String> tokens, int[] position, List<String> out, double x) {
        require(tokens, position, "(");
        final int before = out.size();
        rewrite(tokens, position, out, x);
        require(tokens, position, ")");
        if (out.size() == before) {
            throw new IllegalArgumentException("a function needs an argument");
        }
    }

    private static void require(List<String> tokens, int[] position, String expected) {
        if (position[0] >= tokens.size() || !tokens.get(position[0]).equals(expected)) {
            throw new IllegalArgumentException("expected " + expected + " in the expression");
        }
        position[0] += 1;
    }

    private static List<String> operandFor(double x) {
        if (!Double.isFinite(x)) {
            throw new IllegalArgumentException("the sample point is not finite");
        }
        final BigDecimal value = BigDecimal.valueOf(x).stripTrailingZeros();
        final String text = value.abs().toPlainString();
        final List<String> tokens = new ArrayList<>();
        for (int index = 0; index < text.length(); index++) {
            tokens.add(String.valueOf(text.charAt(index)));
        }
        if (value.signum() < 0) {
            tokens.add("+/-");
        }
        return tokens;
    }

    private static String longestMatch(String text, int index, List<String> vocabulary) {
        for (String name : vocabulary) {
            if (text.startsWith(name, index)) {
                return name;
            }
        }
        return null;
    }

    private static boolean isFunction(String token) {
        final Command command = Tokens.commandFor(token, CalculatorMode.SCIENTIFIC);
        return command != null && command.isUnary() && command != Command.NEGATE;
    }
}
