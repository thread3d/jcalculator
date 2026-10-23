package dev.jcalculator.engine;

import dev.jcalculator.core.AngleType;
import dev.jcalculator.core.Rational;
import dev.jcalculator.core.RationalMath;
import dev.jcalculator.format.NumberFormatter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
 */
public final class CalcEngine {

    public static final int MAX_PRECEDENCE_DEPTH = 25;

    public static final String ERROR_OVERFLOW = "Overflow";
    public static final String ERROR_INVALID_INPUT = "Invalid input";
    public static final String ERROR_DIVIDE_BY_ZERO = "Cannot divide by zero";
    public static final String ERROR_UNDEFINED = "Result is undefined";

    /** Display/history sink; the manager and the UI implement it. */
    public interface EngineListener {
        void onDisplay(String primaryDisplay, boolean isError);

        void onExpression(String expression);

        void onHistoryEntry(HistoryEntry entry);

        void onMaxDigitsReached();
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
        this.mode = mode;
        this.precision = mode.precision();
        this.listener = listener;
        this.random = new Random();
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
        return toBits(currentValue, width.bits());
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
            case PI -> constant(RationalMath.pi(precision), inverse ? "2π" : "π");
            case EULER -> constant(RationalMath.e(precision), "e");
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
        if (lastCommand.isBinary()) {
            boolean precedenceInversion = false;
            opCode = command;
            if (mode.hasPrecedence() && prevOpCode != null) {
                final int previous = precedenceOf(prevOpCode);
                final int current = precedenceOf(lastCommand);
                final int incoming = precedenceOf(command);
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
                final int incoming = precedenceOf(command);
                final int pending = opCode == null ? 0 : precedenceOf(opCode);
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

        if (isTrigonometric(command) && isCurrentTooBigForTrig()) {
            currentValue = Rational.zero();
            displayError(ERROR_INVALID_INPUT);
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

        if (inverse && usesInverseFlag(command)) {
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
            storeInteger(Rational.of(toBits(currentValue, width.bits()).negate()));
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
        errorMessage = null;
        checkAndAddLastBinOpToHistory(true);
        displayNum();
    }

    private void constant(BigDecimal value, String text) {
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
                        displayError(rhs.isZero() ? ERROR_UNDEFINED : ERROR_DIVIDE_BY_ZERO);
                        yield rhs;
                    }
                    yield rhs.divide(lhs);
                }
                case MOD -> {
                    if (lhs.isZero()) {
                        displayError(rhs.isZero() ? ERROR_UNDEFINED : ERROR_DIVIDE_BY_ZERO);
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
                    displayError(ERROR_INVALID_INPUT);
                    yield lhs;
                }
            };
        } catch (ArithmeticException exception) {
            displayError(ERROR_INVALID_INPUT);
            return lhs;
        }
    }

    private Rational doIntegerOperation(Command operation, Rational lhsValue, Rational rhsValue) {
        final BigInteger lhsBits = toBits(lhsValue, width.bits());
        final BigInteger rhsBits = toBits(rhsValue, width.bits());
        final BigInteger lhsSigned = toSigned(lhsValue, width.bits());
        final BigInteger rhsSigned = toSigned(rhsValue, width.bits());
        BigInteger result;
        switch (operation) {
            case ADD -> result = lhsSigned.add(rhsSigned);
            case SUBTRACT -> result = rhsSigned.subtract(lhsSigned);
            case MULTIPLY -> result = lhsSigned.multiply(rhsSigned);
            case DIVIDE -> {
                if (lhsSigned.signum() == 0) {
                    displayError(rhsSigned.signum() == 0 ? ERROR_UNDEFINED : ERROR_DIVIDE_BY_ZERO);
                    return rhsValue;
                }
                result = rhsSigned.divide(lhsSigned);
            }
            case MOD -> {
                if (lhsSigned.signum() == 0) {
                    displayError(rhsSigned.signum() == 0 ? ERROR_UNDEFINED : ERROR_DIVIDE_BY_ZERO);
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
                    displayError(ERROR_INVALID_INPUT);
                    return rhsValue;
                }
                result = rhsBits.shiftLeft(lhsSigned.intValue());
            }
            case RIGHT_SHIFT -> {
                if (lhsSigned.signum() < 0 || lhsSigned.compareTo(BigInteger.valueOf(width.bits())) >= 0) {
                    displayError(ERROR_INVALID_INPUT);
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
                    displayError(ERROR_INVALID_INPUT);
                    return rhsValue;
                }
                result = rhsBits.shiftRight(lhsSigned.intValue());
            }
            case POWER -> {
                if (lhsSigned.signum() < 0 || lhsSigned.bitLength() > 20) {
                    displayError(ERROR_INVALID_INPUT);
                    return rhsValue;
                }
                result = rhsSigned.pow(lhsSigned.intValue());
            }
            case ROOT -> {
                if (lhsSigned.signum() <= 0 || lhsSigned.bitLength() > 20) {
                    displayError(ERROR_INVALID_INPUT);
                    return rhsValue;
                }
                result = integerRoot(rhsSigned, lhsSigned.intValue());
            }
            case LOGBASEY -> {
                if (rhsSigned.signum() <= 0 || lhsSigned.signum() <= 0 || lhsSigned.equals(BigInteger.ONE)) {
                    displayError(ERROR_INVALID_INPUT);
                    return rhsValue;
                }
                final double log = Math.log(rhsSigned.doubleValue()) / Math.log(lhsSigned.doubleValue());
                result = BigDecimal.valueOf(log).toBigInteger();
            }
            default -> {
                displayError(ERROR_INVALID_INPUT);
                return rhsValue;
            }
        }
        return Rational.of(result.and(mask()));
    }

    private Rational applyUnary(Command command, Rational value) {
        try {
            return switch (command) {
                case INTEGER_PART -> inverse ? fractionalPart(value) : value.truncate();
                case FRACTIONAL_PART -> fractionalPart(value);
                case COMPLEMENT -> complement(value);
                case SIN -> inverse ? RationalMath.asin(value, angleType, precision) : RationalMath.sin(value, angleType, precision);
                case COS -> inverse ? RationalMath.acos(value, angleType, precision) : RationalMath.cos(value, angleType, precision);
                case TAN -> inverse ? RationalMath.atan(value, angleType, precision) : RationalMath.tan(value, angleType, precision);
                case SINH -> inverse ? RationalMath.asinh(value, precision) : RationalMath.sinh(value, precision);
                case COSH -> inverse ? RationalMath.acosh(value, precision) : RationalMath.cosh(value, precision);
                case TANH -> inverse ? RationalMath.atanh(value, precision) : RationalMath.tanh(value, precision);
                case SEC -> inverse
                        ? RationalMath.acos(invert(value), angleType, precision)
                        : invert(RationalMath.cos(value, angleType, precision));
                case CSC -> inverse
                        ? RationalMath.asin(invert(value), angleType, precision)
                        : invert(RationalMath.sin(value, angleType, precision));
                case COT -> inverse
                        ? RationalMath.atan(invert(value), angleType, precision)
                        : invert(RationalMath.tan(value, angleType, precision));
                case SECH -> inverse
                        ? RationalMath.acosh(invert(value), precision)
                        : invert(RationalMath.cosh(value, precision));
                case CSCH -> inverse
                        ? RationalMath.asinh(invert(value), precision)
                        : invert(RationalMath.sinh(value, precision));
                case COTH -> inverse
                        ? RationalMath.atanh(invert(value), precision)
                        : invert(RationalMath.tanh(value, precision));
                case ASIN -> RationalMath.asin(value, angleType, precision);
                case ACOS -> RationalMath.acos(value, angleType, precision);
                case ATAN -> RationalMath.atan(value, angleType, precision);
                case ASINH -> RationalMath.asinh(value, precision);
                case ACOSH -> RationalMath.acosh(value, precision);
                case ATANH -> RationalMath.atanh(value, precision);
                case ASEC -> RationalMath.acos(invert(value), angleType, precision);
                case ACSC -> RationalMath.asin(invert(value), angleType, precision);
                case ACOT -> RationalMath.atan(invert(value), angleType, precision);
                case ASECH -> RationalMath.acosh(invert(value), precision);
                case ACSCH -> RationalMath.asinh(invert(value), precision);
                case ACOTH -> RationalMath.atanh(invert(value), precision);
                case LN -> inverse ? RationalMath.exp(value, precision) : RationalMath.ln(value, precision);
                case LOG -> RationalMath.log10(value, precision);
                case SQRT -> RationalMath.sqrt(value, precision);
                case SQR -> RationalMath.pow(value, Rational.two(), precision);
                case CUBE -> RationalMath.pow(value, Rational.of(3), precision);
                case CUBEROOT -> RationalMath.root(value, Rational.of(3), precision);
                case FACTORIAL -> RationalMath.factorial(value, precision);
                case RECIPROCAL -> invert(value);
                case DMS -> inverse
                        ? RationalMath.fromDegreesMinutesSeconds(value, precision)
                        : RationalMath.toDegreesMinutesSeconds(value, precision);
                case DEGREES -> RationalMath.fromDegreesMinutesSeconds(value, precision);
                case POW10 -> RationalMath.pow(Rational.ten(), value, precision);
                case POW2 -> RationalMath.pow(Rational.two(), value, precision);
                case POW_E -> RationalMath.exp(value, precision);
                case PERCENT -> percent(value);
                case ABS -> value.abs();
                case CEIL -> value.ceiling();
                case FLOOR -> value.floor();
                case ROTATE_LEFT -> rotateLeft(value);
                case ROTATE_RIGHT -> rotateRight(value);
                case ROTATE_LEFT_CARRY -> rotateLeftCarry(value);
                case ROTATE_RIGHT_CARRY -> rotateRightCarry(value);
                default -> value;
            };
        } catch (ArithmeticException exception) {
            displayError(ERROR_INVALID_INPUT);
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
            return Rational.of(toBits(value, width.bits()).xor(mask()));
        }
        if (radix == 10) {
            return value.truncate().add(Rational.one()).negate();
        }
        return Rational.of(toBits(value, width.bits()).xor(mask()));
    }

    private Rational rotateLeft(Rational value) {
        if (!mode.isIntegerMode()) {
            return value;
        }
        final BigInteger bits = toBits(value, width.bits());
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
        final BigInteger bits = toBits(value, width.bits());
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
        final BigInteger bits = toBits(value, width.bits());
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
        final BigInteger bits = toBits(value, width.bits());
        final boolean lsb = bits.testBit(0);
        BigInteger rotated = bits.shiftRight(1);
        if (carryBit) {
            rotated = rotated.setBit(width.bits() - 1);
        }
        carryBit = lsb;
        return Rational.of(rotated);
    }

    private Rational fractionalPart(Rational value) {
        return value.subtract(value.truncate());
    }

    private Rational invert(Rational value) {
        if (value.isZero()) {
            displayError(ERROR_DIVIDE_BY_ZERO);
            return value;
        }
        return Rational.one().divide(value);
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
            final BigInteger oldValue = toBits(currentValue, oldBits);
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
            numberString = formatValue(currentValue);
            if (numberString.isEmpty()) {
                displayError(ERROR_OVERFLOW);
                return;
            }
        }
        if (listener != null) {
            listener.onDisplay(NumberFormatter.group(numberString, radix), false);
        }
    }

    private void notifyExpression() {
        if (listener != null) {
            listener.onExpression(expression());
        }
    }

    private void notifyHistory(HistoryEntry entry) {
        if (listener != null && entry != null) {
            listener.onHistoryEntry(entry);
        }
    }

    private String formatValue(Rational value) {
        if (mode.isIntegerMode()) {
            final BigInteger bits = toBits(value, width.bits());
            String text;
            if (radix == 10) {
                text = toSigned(value, width.bits()).toString();
            } else {
                text = bits.toString(radix).toUpperCase(Locale.ROOT);
            }
            return text;
        }
        if (isOverflow(value)) {
            displayError(ERROR_OVERFLOW);
            return "";
        }
        return NumberFormatter.format(value, precision, scientificFormat);
    }

    private boolean isOverflow(Rational value) {
        final Rational magnitude = value.abs();
        if (magnitude.isZero()) {
            return false;
        }
        if (magnitude.integerDigitCount() > 10_000) {
            return true;
        }
        if (magnitude.compareTo(Rational.one()) < 0) {
            return magnitude.denominator().toString().length() > 10_000;
        }
        return false;
    }

    private void displayError(String message) {
        error = true;
        errorMessage = message;
        numberString = message;
        expression.clearLine();
        notifyExpression();
        if (listener != null) {
            listener.onDisplay(message, true);
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
            return Rational.of(toBits(parsed, width.bits()));
        }
        return parsed;
    }

    private void storeInteger(Rational value) {
        if (mode.isIntegerMode()) {
            currentValue = Rational.of(toBits(value, width.bits()));
        }
    }

    private Rational truncateForIntegerMath(Rational value) {
        if (!mode.isIntegerMode()) {
            return value;
        }
        return Rational.of(toBits(value, width.bits()));
    }

    private BigInteger toBits(Rational value, int bits) {
        final BigInteger modulusMask = BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE);
        final Rational truncated = value.truncate();
        return truncated.numerator().and(modulusMask);
    }

    private BigInteger toSigned(Rational value, int bits) {
        final BigInteger unsigned = toBits(value, bits);
        if (unsigned.testBit(bits - 1)) {
            return unsigned.subtract(BigInteger.ONE.shiftLeft(bits));
        }
        return unsigned;
    }

    private BigInteger mask() {
        return BigInteger.ONE.shiftLeft(width.bits()).subtract(BigInteger.ONE);
    }

    private BigInteger integerRoot(BigInteger value, int degree) {
        if (degree <= 0) {
            throw new ArithmeticException("bad root degree");
        }
        final boolean negative = value.signum() < 0;
        if (negative && degree % 2 == 0) {
            throw new ArithmeticException("even root of a negative number");
        }
        final BigInteger magnitude = value.abs();
        if (magnitude.equals(BigInteger.ZERO) || magnitude.equals(BigInteger.ONE)) {
            return negative ? magnitude.negate() : magnitude;
        }
        BigInteger guess = BigInteger.ONE.shiftLeft((magnitude.bitLength() + degree - 1) / degree);
        BigInteger previous = null;
        while (!guess.equals(previous)) {
            previous = guess;
            guess = magnitude.divide(guess.pow(degree - 1)).add(guess.multiply(BigInteger.valueOf(degree - 1)))
                    .divide(BigInteger.valueOf(degree));
        }
        while (guess.pow(degree).compareTo(magnitude) > 0) {
            guess = guess.subtract(BigInteger.ONE);
        }
        while (guess.add(BigInteger.ONE).pow(degree).compareTo(magnitude) <= 0) {
            guess = guess.add(BigInteger.ONE);
        }
        return negative ? guess.negate() : guess;
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

    private static int precedenceOf(Command command) {
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

    private static boolean isTrigonometric(Command command) {
        return switch (command) {
            case SIN, COS, TAN, SINH, COSH, TANH, SEC, CSC, COT, SECH, CSCH, COTH,
                    ASIN, ACOS, ATAN, ASINH, ACOSH, ATANH, ASEC, ACSC, ACOT, ASECH, ACSCH, ACOTH -> true;
            default -> false;
        };
    }

    private static boolean usesInverseFlag(Command command) {
        return switch (command) {
            case INTEGER_PART, SIN, COS, TAN, LN, DMS, DEGREES, SINH, COSH, TANH,
                    SEC, CSC, COT, SECH, CSCH, COTH -> true;
            default -> false;
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
