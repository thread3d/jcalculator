package dev.jcalculator.engine;

import dev.jcalculator.core.AngleType;
import dev.jcalculator.core.Rational;
import dev.jcalculator.core.RationalMath;
import dev.jcalculator.format.NumberFormatter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * The calculator engine: a command-driven state machine that keeps one value on display and applies
 * the operations the user asks for.
 *
 * <p>It is the Java counterpart of the original {@code CCalcEngine}. The parts that matter to a user
 * are preserved:
 *
 * <ul>
 *   <li>standard mode evaluates immediately ({@code 2 + 3 × 4 =} is 20);</li>
 *   <li>scientific mode honours precedence ({@code 2 + 3 × 4 =} is 14) and parentheses;</li>
 *   <li>programmer mode works on two's-complement integers of a selectable width and base;</li>
 *   <li>typing replaces the displayed result, while pressing an operator first commits it;</li>
 *   <li>repeated equals repeats the pending operation;</li>
 *   <li>errors freeze the display until clear or clear-entry.</li>
 * </ul>
 *
 * <p>Arithmetic is exact ({@link Rational}); transcendental functions are computed to the mode's
 * precision. The engine never touches the UI directly: it reports through {@link EngineListener}.
 *
 * <p>The class is the state machine, and only that. It owns the original's
 * {@code calc.cpp}/{@code scicomm.cpp} role — the pending operation, the precedence stacks, the
 * parenthesis stack and the mode settings — and delegates the rest along the seams the original uses:
 * {@link OperatorPrecedence} for binding power and the operator-replacement rule,
 * {@link UnaryFunctions} for the functions that need no pending state, {@link DisplayFormatter} for
 * turning a value into the displayed string, {@link BitMath} for the two's-complement word
 * arithmetic, and {@link ExpressionBuilder} for the expression line and its history entries.
 */
public final class CalcEngine {

    public static final int MAX_PRECEDENCE_DEPTH = 25;

    /** The overflow text; kept as an alias of {@link EngineError#OVERFLOW}. */
    public static final String ERROR_OVERFLOW = EngineError.OVERFLOW.message();
    /** The out-of-domain text; kept as an alias of {@link EngineError#DOMAIN}. */
    public static final String ERROR_INVALID_INPUT = EngineError.DOMAIN.message();
    /** The divide-by-zero text; kept as an alias of {@link EngineError#DIVIDE_BY_ZERO}. */
    public static final String ERROR_DIVIDE_BY_ZERO = EngineError.DIVIDE_BY_ZERO.message();
    /** The indeterminate-result text; kept as an alias of {@link EngineError#UNDEFINED}. */
    public static final String ERROR_UNDEFINED = EngineError.UNDEFINED.message();

    /**
     * The sink the engine reports state through: the counterpart of the original's
     * {@code ICalcDisplay}.
     *
     * <p>The engine never touches a view. Each method mirrors one callback the original raises from
     * {@code scicomm.cpp}: the primary display, the expression tokens, the parenthesis depth, and the
     * three signals a presentation layer may act on — a right parenthesis with nothing to close, a
     * binary operator arriving, and the digit limit being reached.
     *
     * <p>The original also reports memorized-number changes through this interface. Here that list
     * belongs to {@link dev.jcalculator.manager.CalculatorManager}, which owns it and reports it as
     * part of its own state, so the engine has nothing to add.
     */
    public interface EngineListener {

        /** The primary display changed; {@code isError} marks the error text. */
        void onDisplay(String primaryDisplay, boolean isError);

        /** The expression line changed; the tokens keep the command behind each part. */
        void onExpression(List<ExpressionToken> tokens);

        /** The number of parentheses still open changed. */
        void onParenthesisCount(int count);

        /** A completed calculation was added. */
        void onHistoryEntry(HistoryEntry entry);

        /** A digit was refused because the display is full; not an error. */
        void onMaxDigitsReached();

        /** A right parenthesis was pressed with nothing to close; not an error. */
        void onNoRightParen();

        /** A binary operator was received, which a presentation layer may echo. */
        void onBinaryOperatorReceived();
    }

    private final EngineListener listener;
    private final ExpressionBuilder expression = new ExpressionBuilder();
    private final CalcInput input = new CalcInput();
    private final Random random;

    private CalculatorMode mode;
    private int precision;
    private int radix = 10;
    private NumberWidth width = NumberWidth.QWORD;
    private AngleType angleType = AngleType.DEGREES;
    private boolean scientificFormat;

    private boolean inverse;
    private boolean record;
    private boolean error;
    private boolean changeOp;
    private boolean noPrevEqu = true;
    private boolean setCalcState;
    private boolean carryBit;

    private Command opCode;
    private Command prevOpCode;
    private Command lastCommand = Command.NONE;
    private Command tempCommand = Command.NONE;

    private Rational currentValue = Rational.zero();
    private Rational lastValue = Rational.zero();
    private Rational holdValue = Rational.zero();
    private Rational memoryValue = Rational.zero();
    private String numberString = "0";
    private EngineError engineError;
    private String errorMessage;

    private int openParenCount;
    private final Rational[] parenValues = new Rational[MAX_PRECEDENCE_DEPTH];
    private final Command[] parenOps = new Command[MAX_PRECEDENCE_DEPTH];
    private int precedenceOpCount;
    private final Rational[] precedenceValues = new Rational[MAX_PRECEDENCE_DEPTH];
    private final Command[] precedenceOps = new Command[MAX_PRECEDENCE_DEPTH];

    public CalcEngine(CalculatorMode mode) {
        this(mode, null);
    }

    public CalcEngine(CalculatorMode mode, EngineListener listener) {
        this(mode, listener, new Random());
    }

    /**
     * Creates an engine that draws {@code RANDOM} values from a caller-supplied source.
     *
     * <p>A harness that passes a seeded {@link Random} gets the same sequence of values on every run,
     * which is what makes the random key reproducible.
     *
     * @param mode     the calculator page
     * @param listener the state observer, or {@code null}
     * @param random   the source used by the random key
     */
    public CalcEngine(CalculatorMode mode, EngineListener listener, Random random) {
        this.mode = mode;
        this.precision = mode.precision();
        this.listener = listener;
        this.random = Objects.requireNonNull(random, "random");
        for (int i = 0; i < MAX_PRECEDENCE_DEPTH; i++) {
            parenValues[i] = Rational.zero();
            precedenceValues[i] = Rational.zero();
        }
        displayNum();
    }

    // ---- Public state ---------------------------------------------------------------------------

    public CalculatorMode mode() {
        return mode;
    }

    public int precision() {
        return precision;
    }

    public int radix() {
        return radix;
    }

    public NumberWidth width() {
        return width;
    }

    public AngleType angleType() {
        return angleType;
    }

    public boolean isError() {
        return error;
    }

    public boolean isRecording() {
        return record;
    }

    public String errorMessage() {
        return errorMessage;
    }

    /** The failure kind behind {@link #errorMessage()}, or {@code null} outside the error state. */
    public EngineError error() {
        return engineError;
    }

    /** The stable machine-readable code of {@link #error()}, or {@code null} outside that state. */
    public String errorCode() {
        return engineError == null ? null : engineError.code();
    }

    /** The value shown on the primary display, already grouped. */
    public String display() {
        return NumberFormatter.group(numberString, radix);
    }

    /** The value shown on the primary display, without digit grouping. */
    public String rawDisplay() {
        return numberString;
    }

    /** The expression line above the display. After equals it shows the completed equation. */
    public String expression() {
        if (!noPrevEqu) {
            final String completed = expression.lastCompletedExpression();
            if (completed != null) {
                return completed;
            }
        }
        return expression.currentLine();
    }

    public List<HistoryEntry> history() {
        return expression.completedEntries();
    }

    /** The expression line as tokens, each paired with the command behind it. */
    public List<ExpressionToken> expressionTokens() {
        return expression.tokens();
    }

    /** The number of parentheses still open. */
    public int parenthesisCount() {
        return openParenCount;
    }

    public Rational currentValue() {
        return currentValue;
    }

    public Rational memoryValue() {
        return memoryValue;
    }

    /** Sets the immediate memory value; used when a memorized slot is recalled. */
    public void setMemoryValue(Rational value) {
        memoryValue = truncateForIntegerMath(value == null ? Rational.zero() : value);
    }

    public boolean hasMemory() {
        return !memoryValue.isZero();
    }

    /** The current value as an integer in two's-complement form, for the programmer modes. */
    public BigInteger currentBits() {
        return BitMath.toBits(currentValue, width.bits());
    }

    /**
     * Flips one bit of the programmer word, as clicking a bit in the original's bit panel does.
     *
     * <p>Outside programmer mode there is no word to flip, so the call is ignored rather than being an
     * error: the panel that sends it is only shown on that page.
     *
     * @param index the bit to flip, counting from the least significant
     * @throws IllegalArgumentException when the index is outside the current word width
     */
    public void toggleBit(int index) {
        if (!mode.isIntegerMode()) {
            return;
        }
        if (index < 0 || index >= width.bits()) {
            throw new IllegalArgumentException(
                    "no bit " + index + " in a " + width.bits() + "-bit word");
        }
        record = false;
        input.clear();
        currentValue = Rational.of(BitMath.toBits(currentValue, width.bits()).flipBit(index));
        displayNum();
    }

    // ---- Public commands ------------------------------------------------------------------------

    /** Switches mode and resets the engine, as opening another calculator page does. */
    public void setMode(CalculatorMode newMode) {
        this.mode = newMode;
        this.precision = newMode.precision();
        this.radix = 10;
        this.width = NumberWidth.QWORD;
        this.angleType = AngleType.DEGREES;
        reset();
    }

    /** Clears all state, including memory and history. */
    public void reset() {
        inverse = false;
        record = false;
        error = false;
        changeOp = false;
        noPrevEqu = true;
        setCalcState = false;
        carryBit = false;
        opCode = null;
        prevOpCode = null;
        lastCommand = Command.NONE;
        tempCommand = Command.NONE;
        currentValue = Rational.zero();
        lastValue = Rational.zero();
        holdValue = Rational.zero();
        memoryValue = Rational.zero();
        numberString = "0";
        engineError = null;
        errorMessage = null;
        openParenCount = 0;
        precedenceOpCount = 0;
        input.clear();
        for (int i = 0; i < MAX_PRECEDENCE_DEPTH; i++) {
            parenOps[i] = null;
            precedenceOps[i] = null;
        }
        expression.clearLine();
        expression.clearCompleted();
        notifyExpression();
        notifyParenthesisCount();
        displayNum();
    }

    /** Feeds one button press into the state machine. */
    public void sendCommand(Command command) {
        if (command == Command.NONE) {
            return;
        }
        processCommandWorker(command);
    }

    // ---- State machine --------------------------------------------------------------------------

    private void processCommandWorker(Command command) {
        if (!command.isGuiSetting()) {
            lastCommand = tempCommand;
            tempCommand = command;
        }

        if (!noPrevEqu) {
            expression.clearLine();
            notifyExpression();
        }

        if (error) {
            if (command == Command.CLEAR) {
                // handled normally below
            } else if (command == Command.CLEAR_ENTRY) {
                command = Command.CLEAR;
            } else {
                if (!command.isGuiSetting()) {
                    tempCommand = lastCommand;
                }
                return;
            }
        }

        // Switching from typing to evaluation commits the typed number.
        if (record) {
            if (command.isBinary() || command.isUnary() || command.isMemoryCommand() || command.isParenthesis()
                    || command == Command.EQUALS || command == Command.PI
                    || isRadixCommand(command) || isWidthCommand(command) || isAngleCommand(command)
                    || command == Command.INVERSE || (command == Command.NEGATE && radix != 10)
                    || command == Command.RANDOM || command == Command.EULER) {
                record = false;
                currentValue = parseInput();
                storeInteger(currentValue);
                displayNum();
            }
        } else if (command.isDigit() || command == Command.DECIMAL_POINT) {
            record = true;
            input.clear();
            if (lastCommand != Command.CLOSE_PAREN) {
                checkAndAddLastBinOpToHistory(true);
            }
        }

        if (command.isDigit()) {
            final int digit = command.digitValue();
            if (digit >= radix) {
                handleErrorCommand(command);
                return;
            }
            if (!input.tryAddDigit(digit, radix, mode.isIntegerMode(), width.maxDecimalValue(),
                    width.bits(), maxInputDigits())) {
                handleErrorCommand(command);
                if (listener != null) {
                    listener.onMaxDigitsReached();
                }
                return;
            }
            if (lastCommand == Command.CLOSE_PAREN) {
                beginImplicitMultiplication();
            }
            displayNum();
            return;
        }

        if (command.isBinary()) {
            processBinary(command);
            return;
        }

        if (command.isUnary()) {
            processUnary(command);
            return;
        }

        switch (command) {
            case CLEAR -> clearAll();
            case CLEAR_ENTRY -> clearEntry();
            case BACKSPACE -> {
                if (record) {
                    input.backspace();
                    displayNum();
                } else {
                    handleErrorCommand(command);
                }
            }
            case EQUALS -> evaluate();
            case OPEN_PAREN, CLOSE_PAREN -> processParenthesis(command);
            case RADIX_10, RADIX_16, RADIX_8, RADIX_2 -> setRadix(radixOf(command), null);
            case WIDTH_QWORD, WIDTH_DWORD, WIDTH_WORD, WIDTH_BYTE -> {
                if (record) {
                    currentValue = parseInput();
                    record = false;
                }
                setRadix(radix, widthOf(command));
            }
            case ANGLE_DEGREES -> angleType = AngleType.DEGREES;
            case ANGLE_RADIANS -> angleType = AngleType.RADIANS;
            case ANGLE_GRADIANS -> angleType = AngleType.GRADIANS;
            case NEGATE -> negate();
            case MEMORY_RECALL -> recallMemory();
            case MEMORY_ADD -> memoryValue = truncateForIntegerMath(memoryValue.add(currentValue));
            case MEMORY_SUBTRACT -> memoryValue = truncateForIntegerMath(memoryValue.subtract(currentValue));
            case MEMORY_STORE -> memoryValue = truncateForIntegerMath(currentValue);
            case MEMORY_CLEAR -> memoryValue = Rational.zero();
            case PI -> constant(RationalMath.pi(precision));
            case EULER -> constant(RationalMath.e(precision));
            case RANDOM -> randomValue();
            case FLOAT_FORMAT -> {
                scientificFormat = !scientificFormat;
                displayNum();
            }
            case EXPONENT -> {
                if (record && !mode.isIntegerMode() && input.tryBeginExponent()) {
                    displayNum();
                } else {
                    handleErrorCommand(command);
                }
            }
            case DECIMAL_POINT -> decimalPoint();
            case INVERSE -> inverse = !inverse;
            case MODE_STANDARD -> setMode(CalculatorMode.STANDARD);
            case MODE_SCIENTIFIC -> setMode(CalculatorMode.SCIENTIFIC);
            case MODE_PROGRAMMER -> setMode(CalculatorMode.PROGRAMMER);
            default -> {
                // A command with no state-machine meaning is ignored.
            }
        }
    }

    private void processBinary(Command command) {
        notifyBinaryOperatorReceived();
        if (lastCommand.isBinary()) {
            boolean precedenceInversion = false;
            opCode = command;
            if (mode.hasPrecedence() && prevOpCode != null) {
                final int previous = OperatorPrecedence.of(prevOpCode);
                final int current = OperatorPrecedence.of(lastCommand);
                final int incoming = OperatorPrecedence.of(command);
                if (current <= previous && incoming > previous) {
                    precedenceInversion = true;
                    prevOpCode = null;
                }
            }
            expression.replaceBinaryOperator(command, mode.isIntegerMode(), precedenceInversion);
            notifyExpression();
            return;
        }

        if (!expression.operandAdded()) {
            expression.addOperand(numberString);
            notifyExpression();
        }

        if (changeOp) {
            boolean again = true;
            while (again) {
                again = false;
                final int incoming = OperatorPrecedence.of(command);
                final int pending = opCode == null ? 0 : OperatorPrecedence.of(opCode);
                if (incoming > pending && mode.hasPrecedence()) {
                    if (precedenceOpCount < MAX_PRECEDENCE_DEPTH) {
                        precedenceValues[precedenceOpCount] = lastValue;
                        precedenceOps[precedenceOpCount] = opCode;
                    } else {
                        handleErrorCommand(command);
                    }
                    precedenceOpCount++;
                } else {
                    currentValue = doOperation(opCode, currentValue, lastValue);
                    prevOpCode = opCode;
                    if (!error) {
                        displayNum();
                        if (!mode.hasPrecedence()) {
                            final HistoryEntry entry = expression.completeEquation(display());
                            notifyHistory(entry);
                            expression.clearLine();
                            expression.addOperand(numberString);
                            notifyExpression();
                        }
                    }
                    if (precedenceOpCount != 0 && precedenceOps[precedenceOpCount - 1] != null) {
                        precedenceOpCount--;
                        opCode = precedenceOps[precedenceOpCount];
                        lastValue = precedenceValues[precedenceOpCount];
                        again = true;
                    }
                }
            }
        }

        lastValue = currentValue;
        opCode = command;
        expression.addBinaryOperator(command, mode.isIntegerMode());
        notifyExpression();
        noPrevEqu = true;
        changeOp = true;
    }

    private void processUnary(Command command) {
        if (lastCommand.isBinary()) {
            currentValue = lastValue;
        }
        if (command != Command.PERCENT) {
            if (!expression.operandAdded()) {
                expression.addOperand(numberString);
            }
            expression.addUnaryOperator(command, inverse, angleType);
        }

        if (UnaryFunctions.isTrigonometric(command) && isCurrentTooBigForTrig()) {
            currentValue = Rational.zero();
            displayError(EngineError.DOMAIN);
            return;
        }

        currentValue = applyUnary(command, currentValue);
        storeInteger(currentValue);
        if (error) {
            return;
        }
        displayNum();

        if (command == Command.PERCENT) {
            checkAndAddLastBinOpToHistory(true);
            expression.removeLastOperand();
            expression.addOperand(numberString);
        }
        notifyExpression();

        if (inverse && UnaryFunctions.usesInverseFlag(command)) {
            inverse = false;
        }
    }

    private void evaluate() {
        while (openParenCount > 0) {
            if (error) {
                break;
            }
            tempCommand = lastCommand;
            processCommandWorker(Command.CLOSE_PAREN);
            lastCommand = tempCommand;
            tempCommand = Command.EQUALS;
        }

        if (!noPrevEqu) {
            lastValue = currentValue;
        }
        if (lastCommand.isBinary()) {
            currentValue = lastValue;
        }
        if (!expression.operandAdded()) {
            expression.addOperand(numberString);
        }

        resolveHighestPrecedenceOperation();
        while (mode.hasPrecedence() && precedenceOpCount > 0) {
            precedenceOpCount--;
            opCode = precedenceOps[precedenceOpCount];
            lastValue = precedenceValues[precedenceOpCount];
            noPrevEqu = true;
            resolveHighestPrecedenceOperation();
        }

        if (!error) {
            final HistoryEntry entry = expression.completeEquation(display());
            notifyHistory(entry);
            notifyExpression();
            lastValue = currentValue;
            prevOpCode = null;
            precedenceOpCount = 0;
        }
        changeOp = false;
    }

    private void resolveHighestPrecedenceOperation() {
        if (opCode != null) {
            if (noPrevEqu) {
                holdValue = currentValue;
            } else {
                currentValue = holdValue;
                displayNum();
                if (!expression.operandAdded()) {
                    expression.addOperand(numberString);
                }
            }
            currentValue = doOperation(opCode, currentValue, lastValue);
            storeInteger(currentValue);
            prevOpCode = opCode;
            lastValue = currentValue;
            if (!error) {
                displayNum();
            }
            noPrevEqu = false;
        } else if (!error) {
            displayNum();
        }
    }

    private void processParenthesis(Command command) {
        final boolean opening = command == Command.OPEN_PAREN;
        if ((opening && openParenCount >= MAX_PRECEDENCE_DEPTH)
                || (!opening && openParenCount == 0)
                || (precedenceOpCount >= MAX_PRECEDENCE_DEPTH && precedenceOps[precedenceOpCount - 1] != null)) {
            if (!opening && openParenCount == 0) {
                notifyNoRightParen();
            }
            handleErrorCommand(command);
            return;
        }

        if (opening) {
            if (lastCommand.isDigit() || lastCommand.isUnary() || lastCommand == Command.DECIMAL_POINT
                    || lastCommand == Command.CLOSE_PAREN) {
                processCommandWorker(Command.MULTIPLY);
            }
            checkAndAddLastBinOpToHistory(true);
            expression.addOpenBrace();
            parenValues[openParenCount] = lastValue;
            parenOps[openParenCount] = changeOp ? opCode : null;
            openParenCount++;
            if (precedenceOpCount < MAX_PRECEDENCE_DEPTH) {
                precedenceOps[precedenceOpCount++] = null;
            }
            lastValue = Rational.zero();
            if (lastCommand.isBinary()) {
                currentValue = Rational.zero();
            }
            tempCommand = Command.NONE;
            opCode = null;
            changeOp = false;
        } else {
            if (lastCommand.isBinary()) {
                currentValue = lastValue;
            }
            if (!expression.operandAdded()) {
                expression.addOperand(numberString);
            }
            currentValue = doOperation(opCode, currentValue, lastValue);
            storeInteger(currentValue);
            prevOpCode = opCode;

            while (precedenceOpCount > 0) {
                final Command nested = precedenceOps[--precedenceOpCount];
                if (nested == null) {
                    break;
                }
                opCode = nested;
                lastValue = precedenceValues[precedenceOpCount];
                currentValue = doOperation(opCode, currentValue, lastValue);
                storeInteger(currentValue);
                prevOpCode = opCode;
            }
            expression.addCloseBrace();
            openParenCount--;
            lastValue = parenValues[openParenCount];
            opCode = parenOps[openParenCount];
            changeOp = opCode != null;
        }

        notifyExpression();
        notifyParenthesisCount();
        if (!error) {
            displayNum();
        }
    }

    private void beginImplicitMultiplication() {
        opCode = Command.MULTIPLY;
        lastValue = currentValue;
        holdValue = Rational.zero();
        noPrevEqu = true;
        if (!expression.operandAdded()) {
            expression.addOpenBrace();
            expression.addOperand(numberString);
            expression.addCloseBrace();
        }
        expression.addBinaryOperator(Command.MULTIPLY, mode.isIntegerMode());
        changeOp = true;
        prevOpCode = null;
        precedenceOpCount = 0;
        notifyExpression();
    }

    private void decimalPoint() {
        if (lastCommand == Command.CLOSE_PAREN) {
            beginImplicitMultiplication();
        }
        if (record && !mode.isIntegerMode() && input.tryAddDecimalPoint()) {
            displayNum();
        } else {
            handleErrorCommand(Command.DECIMAL_POINT);
        }
    }

    private void negate() {
        if (record) {
            if (input.tryToggleSign(mode.isIntegerMode(), width.maxDecimalValue())) {
                displayNum();
            } else {
                handleErrorCommand(Command.NEGATE);
            }
            return;
        }
        if (lastCommand.isBinary()) {
            currentValue = lastValue;
        }
        if (!expression.operandAdded()) {
            expression.addOperand(numberString);
        }
        if (mode.isIntegerMode()) {
            storeInteger(Rational.of(BitMath.toBits(currentValue, width.bits()).negate()));
        } else {
            currentValue = currentValue.negate();
        }
        displayNum();
        expression.addUnaryOperator(Command.NEGATE, inverse, angleType);
        notifyExpression();
    }

    private void clearAll() {
        if (!changeOp) {
            checkAndAddLastBinOpToHistory(false);
        }
        lastValue = Rational.zero();
        changeOp = false;
        openParenCount = 0;
        precedenceOpCount = 0;
        tempCommand = Command.NONE;
        lastCommand = Command.NONE;
        opCode = null;
        prevOpCode = null;
        noPrevEqu = true;
        carryBit = false;
        expression.clearLine();
        notifyExpression();
        notifyParenthesisCount();
        clearTemporaryValues();
    }

    private void clearEntry() {
        clearTemporaryValues();
    }

    private void clearTemporaryValues() {
        inverse = false;
        input.clear();
        record = true;
        error = false;
        engineError = null;
        errorMessage = null;
        checkAndAddLastBinOpToHistory(true);
        displayNum();
    }

    private void constant(BigDecimal value) {
        if (mode.isIntegerMode()) {
            handleErrorCommand(Command.NONE);
            return;
        }
        checkAndAddLastBinOpToHistory(true);
        currentValue = Rational.of(value);
        displayNum();
        inverse = false;
    }

    private void randomValue() {
        if (mode.isIntegerMode()) {
            handleErrorCommand(Command.NONE);
            return;
        }
        checkAndAddLastBinOpToHistory(true);
        currentValue = Rational.of(new BigDecimal(random.nextDouble()).round(RationalMath.context(precision)));
        displayNum();
        inverse = false;
    }

    private void recallMemory() {
        currentValue = truncateForIntegerMath(memoryValue);
        if (setCalcState) {
            setCalcState = false;
        }
        checkAndAddLastBinOpToHistory(true);
        displayNum();
    }

    // ---- Calculation ----------------------------------------------------------------------------

    /**
     * Applies a binary operator using the original's operand convention: {@code lhs} is the value on
     * display and {@code rhs} is the value saved when the operator was pressed. Subtraction, division
     * and root are therefore {@code rhs op lhs}.
     */
    private Rational doOperation(Command operation, Rational lhs, Rational rhs) {
        if (operation == null) {
            return lhs;
        }
        try {
            if (mode.isIntegerMode()) {
                return doIntegerOperation(operation, lhs, rhs);
            }
            return switch (operation) {
                case ADD -> lhs.add(rhs);
                case SUBTRACT -> rhs.subtract(lhs);
                case MULTIPLY -> lhs.multiply(rhs);
                case DIVIDE -> {
                    if (lhs.isZero()) {
                        displayError(rhs.isZero() ? EngineError.UNDEFINED : EngineError.DIVIDE_BY_ZERO);
                        yield rhs;
                    }
                    yield rhs.divide(lhs);
                }
                case MOD -> {
                    if (lhs.isZero()) {
                        displayError(rhs.isZero() ? EngineError.UNDEFINED : EngineError.DIVIDE_BY_ZERO);
                        yield rhs;
                    }
                    yield rhs.modulo(lhs);
                }
                case POWER -> RationalMath.pow(rhs, lhs, precision);
                case ROOT -> RationalMath.root(rhs, lhs, precision);
                case LOGBASEY -> {
                    if (rhs.signum() <= 0 || lhs.signum() <= 0) {
                        throw new ArithmeticException("logarithm domain");
                    }
                    yield RationalMath.log10(rhs, precision)
                            .divide(RationalMath.log10(lhs, precision));
                }
                default -> {
                    // Bitwise operators are programmer-mode only.
                    displayError(EngineError.DOMAIN);
                    yield lhs;
                }
            };
        } catch (CalculationException failure) {
            displayError(failure.error());
            return lhs;
        } catch (ArithmeticException exception) {
            displayError(EngineError.DOMAIN);
            return lhs;
        }
    }

    private Rational doIntegerOperation(Command operation, Rational lhsValue, Rational rhsValue) {
        final BigInteger lhsBits = BitMath.toBits(lhsValue, width.bits());
        final BigInteger rhsBits = BitMath.toBits(rhsValue, width.bits());
        final BigInteger lhsSigned = BitMath.toSigned(lhsValue, width.bits());
        final BigInteger rhsSigned = BitMath.toSigned(rhsValue, width.bits());
        BigInteger result;
        switch (operation) {
            case ADD -> result = lhsSigned.add(rhsSigned);
            case SUBTRACT -> result = rhsSigned.subtract(lhsSigned);
            case MULTIPLY -> result = lhsSigned.multiply(rhsSigned);
            case DIVIDE -> {
                if (lhsSigned.signum() == 0) {
                    displayError(rhsSigned.signum() == 0 ? EngineError.UNDEFINED : EngineError.DIVIDE_BY_ZERO);
                    return rhsValue;
                }
                result = rhsSigned.divide(lhsSigned);
            }
            case MOD -> {
                if (lhsSigned.signum() == 0) {
                    displayError(rhsSigned.signum() == 0 ? EngineError.UNDEFINED : EngineError.DIVIDE_BY_ZERO);
                    return rhsValue;
                }
                result = rhsSigned.remainder(lhsSigned);
            }
            case AND -> result = lhsBits.and(rhsBits);
            case OR -> result = lhsBits.or(rhsBits);
            case XOR -> result = lhsBits.xor(rhsBits);
            case NAND -> result = lhsBits.and(rhsBits).xor(mask());
            case NOR -> result = lhsBits.or(rhsBits).xor(mask());
            case LEFT_SHIFT -> {
                if (lhsSigned.signum() < 0 || lhsSigned.compareTo(BigInteger.valueOf(width.bits())) >= 0) {
                    displayError(EngineError.INVALID_RANGE);
                    return rhsValue;
                }
                result = rhsBits.shiftLeft(lhsSigned.intValue());
            }
            case RIGHT_SHIFT -> {
                if (lhsSigned.signum() < 0 || lhsSigned.compareTo(BigInteger.valueOf(width.bits())) >= 0) {
                    displayError(EngineError.INVALID_RANGE);
                    return rhsValue;
                }
                result = rhsBits.shiftRight(lhsSigned.intValue());
                if (rhsBits.testBit(width.bits() - 1)) {
                    final BigInteger fill = mask().shiftLeft(width.bits() - lhsSigned.intValue());
                    result = result.or(fill.and(mask()));
                }
            }
            case RIGHT_SHIFT_LOGICAL -> {
                if (lhsSigned.signum() < 0 || lhsSigned.compareTo(BigInteger.valueOf(width.bits())) >= 0) {
                    displayError(EngineError.INVALID_RANGE);
                    return rhsValue;
                }
                result = rhsBits.shiftRight(lhsSigned.intValue());
            }
            case POWER -> {
                if (lhsSigned.signum() < 0 || lhsSigned.bitLength() > 20) {
                    displayError(EngineError.INVALID_RANGE);
                    return rhsValue;
                }
                result = rhsSigned.pow(lhsSigned.intValue());
            }
            case ROOT -> {
                if (lhsSigned.signum() <= 0 || lhsSigned.bitLength() > 20) {
                    displayError(EngineError.INVALID_RANGE);
                    return rhsValue;
                }
                result = BitMath.integerRoot(rhsSigned, lhsSigned.intValue());
            }
            case LOGBASEY -> {
                if (rhsSigned.signum() <= 0 || lhsSigned.signum() <= 0 || lhsSigned.equals(BigInteger.ONE)) {
                    displayError(EngineError.DOMAIN);
                    return rhsValue;
                }
                final double log = Math.log(rhsSigned.doubleValue()) / Math.log(lhsSigned.doubleValue());
                result = BigDecimal.valueOf(log).toBigInteger();
            }
            default -> {
                displayError(EngineError.DOMAIN);
                return rhsValue;
            }
        }
        return Rational.of(result.and(mask()));
    }

    private Rational applyUnary(Command command, Rational value) {
        try {
            return switch (command) {
                case PERCENT -> percent(value);
                case COMPLEMENT -> complement(value);
                case ROTATE_LEFT -> rotateLeft(value);
                case ROTATE_RIGHT -> rotateRight(value);
                case ROTATE_LEFT_CARRY -> rotateLeftCarry(value);
                case ROTATE_RIGHT_CARRY -> rotateRightCarry(value);
                default -> UnaryFunctions.apply(command, value, inverse, angleType, precision);
            };
        } catch (CalculationException failure) {
            displayError(failure.error());
            return value;
        }
    }

    private Rational percent(Rational value) {
        if (opCode == Command.MULTIPLY || opCode == Command.DIVIDE) {
            return value.divide(Rational.hundred());
        }
        return value.multiply(lastValue.divide(Rational.hundred()));
    }

    private Rational complement(Rational value) {
        if (mode.isIntegerMode()) {
            return Rational.of(BitMath.toBits(value, width.bits()).xor(mask()));
        }
        if (radix == 10) {
            return value.truncate().add(Rational.one()).negate();
        }
        return Rational.of(BitMath.toBits(value, width.bits()).xor(mask()));
    }

    private Rational rotateLeft(Rational value) {
        if (!mode.isIntegerMode()) {
            return value;
        }
        final BigInteger bits = BitMath.toBits(value, width.bits());
        final int msb = bits.testBit(width.bits() - 1) ? 1 : 0;
        BigInteger rotated = bits.shiftLeft(1).and(mask());
        if (msb == 1) {
            rotated = rotated.setBit(0);
        }
        return Rational.of(rotated);
    }

    private Rational rotateRight(Rational value) {
        if (!mode.isIntegerMode()) {
            return value;
        }
        final BigInteger bits = BitMath.toBits(value, width.bits());
        final boolean lsb = bits.testBit(0);
        BigInteger rotated = bits.shiftRight(1);
        if (lsb) {
            rotated = rotated.setBit(width.bits() - 1);
        }
        return Rational.of(rotated);
    }

    private Rational rotateLeftCarry(Rational value) {
        if (!mode.isIntegerMode()) {
            return value;
        }
        final BigInteger bits = BitMath.toBits(value, width.bits());
        final boolean msb = bits.testBit(width.bits() - 1);
        BigInteger rotated = bits.shiftLeft(1).and(mask());
        if (carryBit) {
            rotated = rotated.setBit(0);
        }
        carryBit = msb;
        return Rational.of(rotated);
    }

    private Rational rotateRightCarry(Rational value) {
        if (!mode.isIntegerMode()) {
            return value;
        }
        final BigInteger bits = BitMath.toBits(value, width.bits());
        final boolean lsb = bits.testBit(0);
        BigInteger rotated = bits.shiftRight(1);
        if (carryBit) {
            rotated = rotated.setBit(width.bits() - 1);
        }
        carryBit = lsb;
        return Rational.of(rotated);
    }

    // ---- Settings -------------------------------------------------------------------------------

    /**
     * Changes the base and/or width. A width change masks the current value; narrowing therefore
     * discards high bits, and widening sign-extends a negative value, which is what switching
     * QWORD/DWORD/WORD/BYTE in the original does.
     */
    public void setRadix(int newRadix, NumberWidth newWidth) {
        if (newRadix != 10 && newRadix != 16 && newRadix != 8 && newRadix != 2) {
            throw new IllegalArgumentException("unsupported radix: " + newRadix);
        }
        if (mode.isIntegerMode() && newWidth != null && newWidth != width) {
            final int oldBits = width.bits();
            final BigInteger oldValue = BitMath.toBits(currentValue, oldBits);
            final boolean negative = oldValue.testBit(oldBits - 1);
            BigInteger widened = oldValue;
            if (negative && newWidth.bits() > oldBits) {
                final BigInteger oldMask = BigInteger.ONE.shiftLeft(oldBits).subtract(BigInteger.ONE);
                final BigInteger newMask = BigInteger.ONE.shiftLeft(newWidth.bits()).subtract(BigInteger.ONE);
                widened = oldValue.or(newMask.andNot(oldMask));
            }
            width = newWidth;
            currentValue = Rational.of(widened.and(mask()));
        }
        radix = newRadix;
        displayNum();
    }

    public void setPrecision(int newPrecision) {
        if (newPrecision < 1) {
            throw new IllegalArgumentException("precision must be positive");
        }
        precision = newPrecision;
        displayNum();
    }

    public void setAngleType(AngleType newAngleType) {
        angleType = newAngleType;
    }

    public void setScientificFormat(boolean enabled) {
        scientificFormat = enabled;
        displayNum();
    }

    // ---- Display --------------------------------------------------------------------------------

    private void displayNum() {
        if (record) {
            numberString = input.toDisplayString(radix);
        } else {
            if (mode.isIntegerMode()) {
                currentValue = truncateForIntegerMath(currentValue);
            }
            try {
                numberString = DisplayFormatter.format(currentValue, mode, radix, width, precision,
                        scientificFormat);
            } catch (CalculationException failure) {
                displayError(failure.error());
                return;
            }
        }
        if (listener != null) {
            listener.onDisplay(NumberFormatter.group(numberString, radix), false);
        }
    }

    private void notifyExpression() {
        if (listener != null) {
            listener.onExpression(expression.tokens());
        }
    }

    private void notifyParenthesisCount() {
        if (listener != null) {
            listener.onParenthesisCount(openParenCount);
        }
    }

    private void notifyNoRightParen() {
        if (listener != null) {
            listener.onNoRightParen();
        }
    }

    private void notifyBinaryOperatorReceived() {
        if (listener != null) {
            listener.onBinaryOperatorReceived();
        }
    }

    private void notifyHistory(HistoryEntry entry) {
        if (listener != null && entry != null) {
            listener.onHistoryEntry(entry);
        }
    }

    private void displayError(EngineError failure) {
        error = true;
        engineError = failure;
        errorMessage = failure.message();
        numberString = failure.message();
        expression.clearLine();
        notifyExpression();
        if (listener != null) {
            listener.onDisplay(failure.message(), true);
        }
    }

    private void handleErrorCommand(Command command) {
        if (!command.isGuiSetting()) {
            tempCommand = lastCommand;
        }
    }

    // ---- Helpers --------------------------------------------------------------------------------

    private Rational parseInput() {
        final CalcInput.RationalValue value = input.toRationalValue(radix);
        final Rational parsed = Rational.of(value.numerator(), value.denominator());
        if (mode.isIntegerMode()) {
            return Rational.of(BitMath.toBits(parsed, width.bits()));
        }
        return parsed;
    }

    private void storeInteger(Rational value) {
        if (mode.isIntegerMode()) {
            currentValue = Rational.of(BitMath.toBits(value, width.bits()));
        }
    }

    private Rational truncateForIntegerMath(Rational value) {
        if (!mode.isIntegerMode()) {
            return value;
        }
        return Rational.of(BitMath.toBits(value, width.bits()));
    }

    private BigInteger mask() {
        return BitMath.mask(width.bits());
    }

    private int maxInputDigits() {
        if (radix == 10) {
            return mode.isIntegerMode() ? width.maxDecimalValue().length() - 1 : precision;
        }
        final int bitsPerDigit = switch (radix) {
            case 16 -> 4;
            case 8 -> 3;
            case 2 -> 1;
            default -> 1;
        };
        return width.bits() / bitsPerDigit;
    }

    private boolean isCurrentTooBigForTrig() {
        return currentValue.abs().integerDigitCount() > 100;
    }

    private static boolean isRadixCommand(Command command) {
        return command == Command.RADIX_10 || command == Command.RADIX_16
                || command == Command.RADIX_8 || command == Command.RADIX_2;
    }

    private static boolean isWidthCommand(Command command) {
        return command == Command.WIDTH_QWORD || command == Command.WIDTH_DWORD
                || command == Command.WIDTH_WORD || command == Command.WIDTH_BYTE;
    }

    private static boolean isAngleCommand(Command command) {
        return command == Command.ANGLE_DEGREES || command == Command.ANGLE_RADIANS
                || command == Command.ANGLE_GRADIANS;
    }

    private static int radixOf(Command command) {
        return switch (command) {
            case RADIX_16 -> 16;
            case RADIX_8 -> 8;
            case RADIX_2 -> 2;
            default -> 10;
        };
    }

    private static NumberWidth widthOf(Command command) {
        return switch (command) {
            case WIDTH_DWORD -> NumberWidth.DWORD;
            case WIDTH_WORD -> NumberWidth.WORD;
            case WIDTH_BYTE -> NumberWidth.BYTE;
            default -> NumberWidth.QWORD;
        };
    }

    private void checkAndAddLastBinOpToHistory(boolean addToHistory) {
        if (changeOp) {
            if (expression.operandAdded()) {
                expression.removeLastOperand();
            }
        } else if (expression.operandAdded() && !error) {
            if ((lastCommand.isUnary() || lastCommand == Command.NEGATE || lastCommand == Command.CLOSE_PAREN)
                    && openParenCount == 0) {
                if (addToHistory) {
                    final HistoryEntry entry = expression.completeEquation(display());
                    notifyHistory(entry);
                    expression.clearLine();
                }
            } else {
                expression.removeLastOperand();
            }
        }
        notifyExpression();
    }

    /** Exposed for tests: the list of history entries, oldest first. */
    public List<HistoryEntry> historySnapshot() {
        return new ArrayList<>(expression.completedEntries());
    }

    /** Discards the engine's copy of completed entries; the manager keeps the visible history. */
    public void clearHistory() {
        expression.clearCompleted();
    }
}
