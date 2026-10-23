package dev.jcalculator.engine;

import java.util.List;
import java.util.Locale;

/**
 * Every button the calculator can receive, named as the original command table names them.
 *
 * <p>The enum keeps the original's structural distinctions, because the engine's state machine keys
 * off them: digits, binary operators, unary functions, GUI settings and plain commands.
 */
public enum Command {

    // Digits and number entry
    ZERO(0), ONE(1), TWO(2), THREE(3), FOUR(4), FIVE(5), SIX(6), SEVEN(7), EIGHT(8), NINE(9),
    A(10), B(11), C(12), D(13), E(14), F(15),
    DECIMAL_POINT, EXPONENT, NEGATE, BACKSPACE, CLEAR, CLEAR_ENTRY,

    // Binary operators
    AND, OR, XOR, NAND, NOR, LEFT_SHIFT, RIGHT_SHIFT, RIGHT_SHIFT_LOGICAL,
    ROTATE_LEFT, ROTATE_RIGHT, ROTATE_LEFT_CARRY, ROTATE_RIGHT_CARRY,
    DIVIDE, MULTIPLY, ADD, SUBTRACT, MOD, ROOT, POWER, LOGBASEY,

    // Unary functions
    COMPLEMENT, SIN, COS, TAN, SINH, COSH, TANH, SEC, CSC, COT, SECH, CSCH, COTH,
    ASIN, ACOS, ATAN, ASINH, ACOSH, ATANH, ASEC, ACSC, ACOT, ASECH, ACSCH, ACOTH,
    LN, LOG, SQRT, SQR, CUBE, FACTORIAL, RECIPROCAL, DMS, DEGREES, CUBEROOT,
    POW10, POW2, POW_E, PERCENT, INTEGER_PART, FRACTIONAL_PART, ABS, CEIL, FLOOR,

    // Values, constants and evaluation
    PI, EULER, RANDOM, EQUALS,
    OPEN_PAREN, CLOSE_PAREN,

    // Memory
    MEMORY_CLEAR, MEMORY_RECALL, MEMORY_STORE, MEMORY_ADD, MEMORY_SUBTRACT,

    // GUI settings
    INVERSE, FLOAT_FORMAT,
    RADIX_10, RADIX_16, RADIX_8, RADIX_2,
    WIDTH_QWORD, WIDTH_DWORD, WIDTH_WORD, WIDTH_BYTE,
    ANGLE_DEGREES, ANGLE_RADIANS, ANGLE_GRADIANS,
    MODE_STANDARD, MODE_SCIENTIFIC, MODE_PROGRAMMER,

    /** The absence of a command; the state machine compares against it. */
    NONE;

    private final int digit;

    Command() {
        this(-1);
    }

    Command(int digit) {
        this.digit = digit;
    }

    public boolean isDigit() {
        return digit >= 0;
    }

    public int digitValue() {
        if (digit < 0) {
            throw new IllegalStateException(this + " is not a digit");
        }
        return digit;
    }

    public boolean isBinary() {
        return switch (this) {
            case AND, OR, XOR, NAND, NOR, LEFT_SHIFT, RIGHT_SHIFT, RIGHT_SHIFT_LOGICAL,
                    DIVIDE, MULTIPLY, ADD, SUBTRACT, MOD, ROOT, POWER, LOGBASEY -> true;
            default -> false;
        };
    }

    public boolean isUnary() {
        return switch (this) {
            case COMPLEMENT, SIN, COS, TAN, SINH, COSH, TANH, SEC, CSC, COT, SECH, CSCH, COTH,
                    ASIN, ACOS, ATAN, ASINH, ACOSH, ATANH, ASEC, ACSC, ACOT, ASECH, ACSCH, ACOTH,
                    LN, LOG, SQRT, SQR, CUBE, FACTORIAL, RECIPROCAL, DMS, DEGREES, CUBEROOT,
                    POW10, POW2, POW_E, PERCENT, INTEGER_PART, FRACTIONAL_PART, ABS, CEIL, FLOOR,
                    ROTATE_LEFT, ROTATE_RIGHT, ROTATE_LEFT_CARRY, ROTATE_RIGHT_CARRY -> true;
            default -> false;
        };
    }

    public boolean isParenthesis() {
        return this == OPEN_PAREN || this == CLOSE_PAREN;
    }

    public boolean isGuiSetting() {
        return switch (this) {
            case INVERSE, FLOAT_FORMAT, MEMORY_CLEAR, BACKSPACE, EXPONENT, MEMORY_STORE,
                    MEMORY_ADD, MEMORY_SUBTRACT, RADIX_10, RADIX_16, RADIX_8, RADIX_2,
                    WIDTH_QWORD, WIDTH_DWORD, WIDTH_WORD, WIDTH_BYTE,
                    ANGLE_DEGREES, ANGLE_RADIANS, ANGLE_GRADIANS,
                    MODE_STANDARD, MODE_SCIENTIFIC, MODE_PROGRAMMER -> true;
            // MEMORY_RECALL is deliberately absent: recalling a value behaves like entering one.
            default -> false;
        };
    }

    public boolean isMemoryCommand() {
        return switch (this) {
            case MEMORY_CLEAR, MEMORY_RECALL, MEMORY_STORE, MEMORY_ADD, MEMORY_SUBTRACT -> true;
            default -> false;
        };
    }

    /** The token shown in the expression/history line for this operator. */
    public String expressionToken(boolean integerMode) {
        return switch (this) {
            case ADD -> "+";
            case SUBTRACT -> "-";
            case MULTIPLY -> "×";
            case DIVIDE -> "÷";
            case POWER -> "^";
            case ROOT -> "yroot";
            case LOGBASEY -> "log base";
            case MOD -> integerMode ? "MOD" : "mod";
            case AND -> "AND";
            case OR -> "OR";
            case XOR -> "XOR";
            case NAND -> "NAND";
            case NOR -> "NOR";
            case LEFT_SHIFT -> "Lsh";
            case RIGHT_SHIFT -> "Rsh";
            case RIGHT_SHIFT_LOGICAL -> "Rsh logical";
            case ROTATE_LEFT -> "ROL";
            case ROTATE_RIGHT -> "ROR";
            case ROTATE_LEFT_CARRY -> "ROL carry";
            case ROTATE_RIGHT_CARRY -> "ROR carry";
            default -> name().toLowerCase(Locale.ROOT);
        };
    }

    /**
     * Rendering of a unary function in the expression line.
     *
     * @param operand     the operand text
     * @param inverse     whether the inverse flag was active
     * @param angleType   the angle unit, which changes the function's name
     */
    public String unaryExpression(String operand, boolean inverse, dev.jcalculator.core.AngleType angleType) {
        final String name = unaryName(inverse, angleType);
        return name + "(" + operand + ")";
    }

    public String unaryName(boolean inverse, dev.jcalculator.core.AngleType angleType) {
        final String function = switch (this) {
            case SIN -> "sin";
            case COS -> "cos";
            case TAN -> "tan";
            case SINH -> "sinh";
            case COSH -> "cosh";
            case TANH -> "tanh";
            case SEC -> "sec";
            case CSC -> "csc";
            case COT -> "cot";
            case SECH -> "sech";
            case CSCH -> "csch";
            case COTH -> "coth";
            case ASIN -> "sin⁻¹";
            case ACOS -> "cos⁻¹";
            case ATAN -> "tan⁻¹";
            case ASINH -> "sinh⁻¹";
            case ACOSH -> "cosh⁻¹";
            case ATANH -> "tanh⁻¹";
            case ASEC -> "sec⁻¹";
            case ACSC -> "csc⁻¹";
            case ACOT -> "cot⁻¹";
            case ASECH -> "sech⁻¹";
            case ACSCH -> "csch⁻¹";
            case ACOTH -> "coth⁻¹";
            case LN -> inverse ? "e^" : "ln";
            case LOG -> "log";
            case SQRT -> "√";
            case SQR -> "sqr";
            case CUBE -> "cube";
            case CUBEROOT -> "cuberoot";
            case FACTORIAL -> "fact";
            case RECIPROCAL -> "reciproc";
            case DMS -> inverse ? "degrees" : "dms";
            case DEGREES -> "degrees";
            case POW10 -> "10^";
            case POW2 -> "2^";
            case POW_E -> "e^";
            case INTEGER_PART -> inverse ? "frac" : "integer";
            case ABS -> "abs";
            case CEIL -> "ceil";
            case FLOOR -> "floor";
            case NEGATE -> "negate";
            default -> name().toLowerCase(Locale.ROOT);
        };
        if (this == SIN || this == COS || this == TAN || this == SEC || this == CSC || this == COT) {
            final String suffix = switch (angleType) {
                case DEGREES -> "°";
                case RADIANS -> " rad";
                case GRADIANS -> " grad";
            };
            return function + (inverse ? "⁻¹" : "") + suffix;
        }
        return function;
    }

    /** Digits of a number in the order a keypad emits them. */
    public static List<Command> digits() {
        return List.of(ZERO, ONE, TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT, NINE);
    }
}
