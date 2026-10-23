package dev.jcalculator.engine;

import dev.jcalculator.core.AngleType;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the expression line shown above the primary display, and turns a completed computation into
 * a history entry.
 *
 * <p>The original engine delegates this to a history collector with a token stream and expression
 * commands, because the UI renders each token separately. This port only needs the rendered text, so
 * the same rules are expressed more directly:
 *
 * <ul>
 *   <li>a binary operator is appended after its left operand, with spaces;</li>
 *   <li>changing the operator replaces the last operator token instead of appending;</li>
 *   <li>a unary function wraps the operand it applies to;</li>
 *   <li>equals appends {@code =} and produces an immutable entry.</li>
 * </ul>
 */
public final class ExpressionBuilder {

    private final List<String> tokens = new ArrayList<>();
    private final List<HistoryEntry> completed = new ArrayList<>();
    private String lastCompletedExpression;

    /** Whether the most recent token is an operand rather than an operator. */
    public boolean operandAdded() {
        return !tokens.isEmpty() && isOperand(tokens.get(tokens.size() - 1));
    }

    public void addOperand(String text) {
        tokens.add(text);
    }

    public void addOpenBrace() {
        tokens.add("(");
    }

    public void addCloseBrace() {
        tokens.add(")");
    }

    public void addBinaryOperator(Command operator, boolean integerMode) {
        if (tokens.isEmpty()) {
            tokens.add("0");
        }
        if (!tokens.isEmpty() && isBinaryToken(tokens.get(tokens.size() - 1))) {
            tokens.set(tokens.size() - 1, operator.expressionToken(integerMode));
        } else {
            tokens.add(operator.expressionToken(integerMode));
        }
    }

    /** Replaces the trailing operator; used when the user presses a different operator. */
    public void replaceBinaryOperator(Command operator, boolean integerMode, boolean precedenceInversion) {
        if (tokens.isEmpty()) {
            addBinaryOperator(operator, integerMode);
            return;
        }
        final int index = tokens.size() - 1;
        if (isBinaryToken(tokens.get(index))) {
            final String previous = tokens.get(index);
            if (precedenceInversion) {
                // The original encloses the already-computed left operand when the new operator binds
                // more tightly than the replaced one, e.g. "1 * 2 +" changed to "^" becomes "(1 * 2) ^".
                tokens.add(index + 1, ")");
                tokens.add(0, "(");
                tokens.add(operator.expressionToken(integerMode));
            } else {
                tokens.set(index, operator.expressionToken(integerMode));
            }
            return;
        }
        addBinaryOperator(operator, integerMode);
    }

    public void addUnaryOperator(Command operator, boolean inverse, AngleType angleType) {
        if (tokens.isEmpty()) {
            tokens.add("0");
        }
        final int last = tokens.size() - 1;
        final String operand = tokens.get(last);
        if (isOperand(operand)) {
            tokens.set(last, renderUnary(operator, operand, inverse, angleType));
        } else {
            tokens.add(renderUnary(operator, "0", inverse, angleType));
        }
    }

    private static String renderUnary(Command operator, String operand, boolean inverse, AngleType angleType) {
        if (operator == Command.RECIPROCAL) {
            return "1/(" + operand + ")";
        }
        return operator.unaryExpression(operand, inverse, angleType);
    }

    /** Appends an equals sign and records the entry. */
    public HistoryEntry completeEquation(String result) {
        final String expression = currentLine() + "=";
        final HistoryEntry entry = new HistoryEntry(expression, result);
        completed.add(entry);
        lastCompletedExpression = expression;
        return entry;
    }

    public String currentLine() {
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            final String token = tokens.get(i);
            if (i > 0 && !token.equals(")") && !tokens.get(i - 1).equals("(")) {
                builder.append(' ');
            }
            builder.append(token);
        }
        return builder.toString();
    }

    public List<HistoryEntry> completedEntries() {
        return List.copyOf(completed);
    }

    public String lastCompletedExpression() {
        return lastCompletedExpression;
    }

    /** Discards the completed-entry copy kept for the expression line. */
    public void clearCompleted() {
        completed.clear();
    }

    public void clearLine() {
        tokens.clear();
        lastCompletedExpression = null;
    }

    public void removeLastOperand() {
        if (!tokens.isEmpty() && isOperand(tokens.get(tokens.size() - 1))) {
            tokens.remove(tokens.size() - 1);
        }
    }

    private static boolean isOperand(String token) {
        return !isBinaryToken(token) && !token.equals("(");
    }

    private static boolean isBinaryToken(String token) {
        return switch (token) {
            case "+", "-", "×", "÷", "^", "yroot", "log base", "mod", "MOD",
                    "AND", "OR", "XOR", "NAND", "NOR", "Lsh", "Rsh", "Rsh logical",
                    "ROL", "ROR", "ROL carry", "ROR carry" -> true;
            default -> false;
        };
    }
}
