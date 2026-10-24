package dev.jcalculator.engine;

import java.util.List;
import java.util.Objects;

/**
 * One token of the expression line, paired with the command that put it there.
 *
 * <p>The original hands the expression to the presentation layer as a
 * {@code vector<pair<wstring,int>>}: the rendered text of each token and the command that produced
 * it, so the view can colour an operator differently from an operand and can attach a tooltip to a
 * function. This record is that pair. A token with a {@code null} command is a plain operand — the
 * digits the user typed — and every other token remembers the button behind it.
 *
 * @param text    the text the expression line shows for this token
 * @param command the command that produced it, or {@code null} for a typed operand
 */
public record ExpressionToken(String text, Command command) {

    public ExpressionToken {
        Objects.requireNonNull(text, "text");
    }

    /** Whether this token is an operand rather than an operator or an opening brace. */
    public boolean isOperand() {
        return command == null || !(command.isBinary() || command == Command.OPEN_PAREN);
    }

    /** Whether this token is a binary operator such as {@code +} or {@code ×}. */
    public boolean isBinaryOperator() {
        return command != null && command.isBinary();
    }

    /**
     * Renders a token stream as the single line the expression area shows.
     *
     * <p>Tokens are separated by one space, except that a closing brace follows its operand, an
     * opening brace is followed by its first token directly, and the equals sign the completed
     * equation appends stays attached to its result — so {@code (1 + 2) × 3} and {@code 2 + 3=} read
     * as the original's expression line does.
     *
     * @param tokens the tokens in the order they were added
     */
    public static String render(List<ExpressionToken> tokens) {
        final StringBuilder builder = new StringBuilder();
        for (int index = 0; index < tokens.size(); index++) {
            final ExpressionToken token = tokens.get(index);
            final boolean tight = index > 0
                    && (token.command() == Command.CLOSE_PAREN
                            || token.command() == Command.EQUALS
                            || tokens.get(index - 1).command() == Command.OPEN_PAREN);
            if (index > 0 && !tight) {
                builder.append(' ');
            }
            builder.append(token.text());
        }
        return builder.toString();
    }
}
