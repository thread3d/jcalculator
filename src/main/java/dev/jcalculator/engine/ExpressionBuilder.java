package dev.jcalculator.engine;

import dev.jcalculator.core.AngleType;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the expression line shown above the primary display, and turns a completed computation into
 * a history entry.
 *
 * <p>The counterpart of the original's history collector. The original works in two layers: the
 * engine keeps an equation as a string buffer with the start index of every operand and operator, and
 * the history collector rewrites that buffer. This class keeps the same information as a list of
 * {@link ExpressionToken}s, which is also what the display layer receives, so the two never disagree
 * about what the line says.
 *
 * <p>The rules the original applies, and this class reproduces:
 *
 * <ul>
 *   <li>a binary operator is appended after its left operand;</li>
 *   <li>pressing a different operator over a pending one replaces it, and when the new operator binds
 *       more tightly than the one before, the already-computed left operand is enclosed;</li>
 *   <li>a unary function wraps the operand it applies to;</li>
 *   <li>equals appends {@code =} and produces an immutable entry.</li>
 * </ul>
 */
public final class ExpressionBuilder {

    private final List<ExpressionToken> tokens = new ArrayList<>();
    private final List<HistoryEntry> completed = new ArrayList<>();
    private String lastCompletedExpression;

    /** Whether the most recent token is an operand rather than an operator. */
    public boolean operandAdded() {
        return !tokens.isEmpty() && tokens.get(tokens.size() - 1).isOperand();
    }

    /** The tokens of the line as the display shows it, including the equals sign once it is complete. */
    public List<ExpressionToken> tokens() {
        if (lastCompletedExpression == null) {
            return List.copyOf(tokens);
        }
        final List<ExpressionToken> shown = new ArrayList<>(tokens);
        shown.add(new ExpressionToken("=", Command.EQUALS));
        return List.copyOf(shown);
    }

    public void addOperand(String text) {
        tokens.add(new ExpressionToken(text, null));
    }

    public void addOpenBrace() {
        tokens.add(new ExpressionToken("(", Command.OPEN_PAREN));
    }

    public void addCloseBrace() {
        tokens.add(new ExpressionToken(")", Command.CLOSE_PAREN));
    }

    public void addBinaryOperator(Command operator, boolean integerMode) {
        if (tokens.isEmpty()) {
            addOperand("0");
        }
        final ExpressionToken token = new ExpressionToken(operator.expressionToken(integerMode), operator);
        if (tokens.get(tokens.size() - 1).isBinaryOperator()) {
            tokens.set(tokens.size() - 1, token);
        } else {
            tokens.add(token);
        }
    }

    /**
     * Replaces the trailing operator, as pressing a second operator does.
     *
     * <p>The original's {@code ChangeLastBinOp}: the pending operator is removed and the new one takes
     * its place. When that would invert precedence — {@code 1 × 2 +} becomes {@code ^}, and the
     * partial result {@code 1 × 2} was already computed — the left operand is enclosed so the line
     * still says what was calculated.
     *
     * @param operator           the operator just pressed
     * @param integerMode        whether the programmer page's spellings apply
     * @param precedenceInversion whether the left operand has to be enclosed
     */
    public void replaceBinaryOperator(Command operator, boolean integerMode, boolean precedenceInversion) {
        if (tokens.isEmpty()) {
            addBinaryOperator(operator, integerMode);
            return;
        }
        final int index = tokens.size() - 1;
        if (!tokens.get(index).isBinaryOperator()) {
            addBinaryOperator(operator, integerMode);
            return;
        }
        tokens.remove(index);
        if (precedenceInversion) {
            tokens.add(0, new ExpressionToken("(", Command.OPEN_PAREN));
            tokens.add(new ExpressionToken(")", Command.CLOSE_PAREN));
        }
        tokens.add(new ExpressionToken(operator.expressionToken(integerMode), operator));
    }

    public void addUnaryOperator(Command operator, boolean inverse, AngleType angleType) {
        if (tokens.isEmpty()) {
            addOperand("0");
        }
        final int last = tokens.size() - 1;
        final ExpressionToken operand = tokens.get(last);
        if (operand.isOperand()) {
            tokens.set(last, new ExpressionToken(
                    renderUnary(operator, operand.text(), inverse, angleType), operator));
        } else {
            tokens.add(new ExpressionToken(renderUnary(operator, "0", inverse, angleType), operator));
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
        return completeEquation(result, List.of());
    }

    /**
     * Appends an equals sign and records the entry with the buttons behind it.
     *
     * @param result   the formatted result
     * @param commands the buttons that produced it, which let the entry be re-run
     */
    public HistoryEntry completeEquation(String result, List<Command> commands) {
        final String expression = ExpressionToken.render(tokens) + "=";
        final HistoryEntry entry = new HistoryEntry(expression, result, commands);
        completed.add(entry);
        lastCompletedExpression = expression;
        return entry;
    }

    /** The line as one string, which is what the expression area renders. */
    public String currentLine() {
        return ExpressionToken.render(tokens());
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
        if (!tokens.isEmpty() && tokens.get(tokens.size() - 1).isOperand()) {
            tokens.remove(tokens.size() - 1);
        }
    }
}
