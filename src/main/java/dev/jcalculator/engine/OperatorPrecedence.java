package dev.jcalculator.engine;

/**
 * Operator precedence and the left-operand inversion the original applies when an operator is
 * replaced.
 *
 * <p>The counterpart of the original's {@code scioper.cpp}. Standard mode evaluates as the user
 * types and ignores these levels; scientific mode uses them, and programmer mode uses them for
 * arithmetic but not for the bitwise operators, which the original treats as immediate.
 */
public final class OperatorPrecedence {

    private OperatorPrecedence() {
    }

    /**
     * The binding power of one operator, higher binding tighter.
     *
     * <p>The levels, lowest first: bitwise or/xor, bitwise and/nand/nor, addition and subtraction,
     * multiplication and its peers, then power/root/log-base. A command that is not a binary
     * operator has level zero, which is what an empty pending operator compares as.
     */
    public static int of(Command command) {
        if (command == null) {
            return 0;
        }
        return switch (command) {
            case OR, XOR -> 0;
            case AND, NAND, NOR -> 1;
            case ADD, SUBTRACT -> 2;
            case LEFT_SHIFT, RIGHT_SHIFT, RIGHT_SHIFT_LOGICAL, MOD, DIVIDE, MULTIPLY -> 3;
            case POWER, ROOT, LOGBASEY -> 4;
            default -> 0;
        };
    }

    /**
     * Whether replacing one operator with another must enclose the already-computed left operand.
     *
     * <p>The original decides this when the user presses a second operator over the first: if the
     * pending operator binds no more tightly than the one before it, and the new operator binds more
     * tightly than that one, the partial result has to be parenthesised. Typing {@code 1 × 2 +} and
     * then {@code ^} therefore reads {@code (1 × 2) ^}.
     *
     * @param previous the operator before the pending one
     * @param current  the pending operator, the one being replaced
     * @param incoming the operator just pressed
     */
    public static boolean inverts(int previous, int current, int incoming) {
        return current <= previous && incoming > previous;
    }
}
