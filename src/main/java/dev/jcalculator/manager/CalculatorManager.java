package dev.jcalculator.manager;

import dev.jcalculator.core.Rational;
import dev.jcalculator.engine.CalcEngine;
import dev.jcalculator.engine.CalculatorMode;
import dev.jcalculator.engine.Command;
import dev.jcalculator.engine.ExpressionToken;
import dev.jcalculator.engine.HistoryEntry;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Application-level calculator state: the current mode, the history list of each mode and the
 * memorized numbers.
 *
 * <p>This is the Java counterpart of {@code CalculatorManager}. It owns one engine and switches it
 * when the user moves between the standard, scientific and programmer pages; each page keeps its own
 * history, as the original's history lists do. Memory is a list of up to 100 values plus the
 * engine's immediate memory.
 */
public final class CalculatorManager implements CalcEngine.EngineListener {

    public static final int MAX_MEMORY_SIZE = 100;
    public static final int MAX_HISTORY_SIZE = 20;

    /** A right parenthesis was pressed with nothing open to close. */
    public static final String SIGNAL_NO_RIGHT_PAREN = "no_right_paren";
    /** A binary operator arrived; a presentation layer may echo it. */
    public static final String SIGNAL_BINARY_OPERATOR = "binary_operator";
    /** A digit was refused because the display is full; not an error. */
    public static final String SIGNAL_MAX_DIGITS = "max_digits";
    /** A memorized number was recalled, so the input changed without a keystroke. */
    public static final String SIGNAL_INPUT_CHANGED = "input_changed";
    /** A memorized slot was stored, adjusted or removed. */
    public static final String SIGNAL_MEMORY_CHANGED = "memory_changed";

    private final CalcEngine engine;
    private final Map<CalculatorMode, List<HistoryEntry>> histories = new EnumMap<>(CalculatorMode.class);
    private final List<Rational> memorizedNumbers = new ArrayList<>();

    private CalculatorMode mode;
    private String primaryDisplay = "0";
    private String expression = "";
    private boolean error;
    private String errorMessage;
    private int parenthesisCount;
    private String lastSignal;

    public CalculatorManager() {
        this(CalculatorMode.STANDARD);
    }

    public CalculatorManager(CalculatorMode mode) {
        this(mode, new Random());
    }

    /**
     * Creates a manager whose random key draws from a seeded source, so a harness can reproduce a
     * sequence of {@code RANDOM} values across runs.
     *
     * @param mode       the calculator page to start on
     * @param randomSeed the seed for the random key
     */
    public CalculatorManager(CalculatorMode mode, long randomSeed) {
        this(mode, new Random(randomSeed));
    }

    public CalculatorManager(CalculatorMode mode, Random random) {
        this.mode = mode;
        this.engine = new CalcEngine(mode, this, Objects.requireNonNull(random, "random"));
        for (CalculatorMode value : CalculatorMode.values()) {
            histories.put(value, new ArrayList<>());
        }
        primaryDisplay = engine.display();
    }

    // ---- Commands -------------------------------------------------------------------------------

    /** Feeds a command to the calculator, handling mode and memory at this level. */
    public void sendCommand(Command command) {
        switch (command) {
            case MODE_STANDARD -> setMode(CalculatorMode.STANDARD);
            case MODE_SCIENTIFIC -> setMode(CalculatorMode.SCIENTIFIC);
            case MODE_PROGRAMMER -> setMode(CalculatorMode.PROGRAMMER);
            case MEMORY_STORE -> memorizeNumber();
            case MEMORY_ADD -> memorizedNumberAdd(0);
            case MEMORY_SUBTRACT -> memorizedNumberSubtract(0);
            case MEMORY_CLEAR -> memorizedNumberClearAll();
            default -> engine.sendCommand(command);
        }
    }

    /** Types a sequence of commands, for scripted use. */
    public void sendCommands(Command... commands) {
        for (Command command : commands) {
            sendCommand(command);
        }
    }

    /** Switches calculator pages; each page starts from a cleared display. */
    public void setMode(CalculatorMode newMode) {
        if (mode == newMode) {
            engine.sendCommand(Command.CLEAR);
            return;
        }
        mode = newMode;
        engine.setMode(newMode);
        primaryDisplay = engine.display();
        expression = "";
        error = engine.isError();
        errorMessage = engine.errorMessage();
        parenthesisCount = 0;
        lastSignal = null;
    }

    public void reset() {
        engine.reset();
        memorizedNumbers.clear();
        histories.values().forEach(List::clear);
        primaryDisplay = engine.display();
        expression = "";
        error = false;
        errorMessage = null;
        parenthesisCount = 0;
        lastSignal = null;
    }

    // ---- Memory ---------------------------------------------------------------------------------
    //
    // The original keeps a vector of memorized values and performs every slot operation by selecting
    // the slot into the engine's immediate memory, running the engine command there, and copying the
    // result back. That indirection is preserved here: the engine owns the arithmetic, this class
    // owns the list.

    /** Stores the displayed value as a new slot at the front, as {@code MS} does. */
    public void memorizeNumber() {
        if (error) {
            return;
        }
        engine.sendCommand(Command.MEMORY_STORE);
        memorizedNumbers.add(0, engine.memoryValue());
        while (memorizedNumbers.size() > MAX_MEMORY_SIZE) {
            memorizedNumbers.remove(memorizedNumbers.size() - 1);
        }
        lastSignal = SIGNAL_MEMORY_CHANGED;
    }

    /** Recalls a memorized slot into the display. */
    public void memorizedNumberLoad(int index) {
        if (error) {
            return;
        }
        selectMemory(index);
        engine.sendCommand(Command.MEMORY_RECALL);
        lastSignal = SIGNAL_INPUT_CHANGED;
    }

    /** Adds the displayed value to one slot, as {@code M+} on that slot does. */
    public void memorizedNumberAdd(int index) {
        if (error) {
            return;
        }
        if (memorizedNumbers.isEmpty()) {
            // The original stores the displayed value when there is nothing to add to.
            memorizeNumber();
            return;
        }
        final int slot = checkedIndex(index);
        selectMemory(slot);
        engine.sendCommand(Command.MEMORY_ADD);
        copyMemoryBack(slot);
        lastSignal = SIGNAL_MEMORY_CHANGED;
    }

    /**
     * Subtracts the displayed value from one slot, as {@code M-} on that slot does.
     *
     * <p>The original's empty-list case stores the value and then subtracts it twice, because
     * {@code -x = x - 2x} in a model where the engine's memory starts out holding {@code x}.
     */
    public void memorizedNumberSubtract(int index) {
        if (error) {
            return;
        }
        if (memorizedNumbers.isEmpty()) {
            memorizeNumber();
            if (memorizedNumbers.isEmpty()) {
                // Nothing could be stored, so there is no slot to subtract twice from;
                // returning rather than recurring keeps a failed store from recursing forever.
                return;
            }
            memorizedNumberSubtract(0);
            memorizedNumberSubtract(0);
            return;
        }
        final int slot = checkedIndex(index);
        selectMemory(slot);
        engine.sendCommand(Command.MEMORY_SUBTRACT);
        copyMemoryBack(slot);
        lastSignal = SIGNAL_MEMORY_CHANGED;
    }

    /** Removes one memorized slot; a slot that does not exist is left alone. */
    public void memorizedNumberClear(int index) {
        if (index >= 0 && index < memorizedNumbers.size()) {
            memorizedNumbers.remove(index);
            lastSignal = SIGNAL_MEMORY_CHANGED;
        }
    }

    /** Clears every memorized slot, as {@code MC} does. */
    public void memorizedNumberClearAll() {
        memorizedNumbers.clear();
        engine.sendCommand(Command.MEMORY_CLEAR);
        lastSignal = SIGNAL_MEMORY_CHANGED;
    }

    /** Recalls a memorized slot into the display; an alias of {@link #memorizedNumberLoad(int)}. */
    public void recallMemory(int index) {
        memorizedNumberLoad(index);
    }

    public List<Rational> memorizedNumbers() {
        return List.copyOf(memorizedNumbers);
    }

    public void clearMemory() {
        memorizedNumberClearAll();
    }

    private void selectMemory(int index) {
        engine.setMemoryValue(memorizedNumbers.get(checkedIndex(index)));
    }

    private void copyMemoryBack(int index) {
        memorizedNumbers.set(index, engine.memoryValue());
    }

    private int checkedIndex(int index) {
        if (index < 0 || index >= memorizedNumbers.size()) {
            throw new IndexOutOfBoundsException("no memory at index " + index);
        }
        return index;
    }

    // ---- State ----------------------------------------------------------------------------------

    public CalculatorMode mode() {
        return mode;
    }

    public CalcEngine engine() {
        return engine;
    }

    public String primaryDisplay() {
        return primaryDisplay;
    }

    /** The primary display without digit grouping. */
    public String rawDisplay() {
        return engine.rawDisplay();
    }

    public String expression() {
        return expression;
    }

    /** How many parentheses the expression still has open. */
    public int parenthesisCount() {
        return parenthesisCount;
    }

    /**
     * The last signal the engine raised that is not an error, or {@code null}.
     *
     * <p>One of {@link #SIGNAL_NO_RIGHT_PAREN}, {@link #SIGNAL_BINARY_OPERATOR},
     * {@link #SIGNAL_MAX_DIGITS}, {@link #SIGNAL_INPUT_CHANGED} or {@link #SIGNAL_MEMORY_CHANGED}. A
     * presentation layer can react to
     * it — the original uses these to beep — and an automation surface can assert that the
     * calculator complained without treating the complaint as a failure.
     */
    public String lastSignal() {
        return lastSignal;
    }

    public boolean isError() {
        return error;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public List<HistoryEntry> history() {
        return List.copyOf(histories.get(mode));
    }

    public List<HistoryEntry> history(CalculatorMode forMode) {
        return List.copyOf(histories.get(forMode));
    }

    public void clearHistory() {
        histories.get(mode).clear();
        engine.clearHistory();
    }

    // ---- Engine listener ------------------------------------------------------------------------

    @Override
    public void onDisplay(String display, boolean isError) {
        primaryDisplay = display;
        error = isError;
        errorMessage = isError ? display : null;
    }

    @Override
    public void onExpression(List<ExpressionToken> tokens) {
        expression = ExpressionToken.render(tokens);
    }

    @Override
    public void onParenthesisCount(int count) {
        parenthesisCount = count;
    }

    @Override
    public void onHistoryEntry(HistoryEntry entry) {
        final List<HistoryEntry> list = histories.get(mode);
        list.add(0, entry);
        while (list.size() > MAX_HISTORY_SIZE) {
            list.remove(list.size() - 1);
        }
    }

    @Override
    public void onMaxDigitsReached() {
        // Not an error: the engine refused the digit and the display is unchanged.
        lastSignal = SIGNAL_MAX_DIGITS;
    }

    @Override
    public void onNoRightParen() {
        // Not an error: nothing was added to the expression.
        lastSignal = SIGNAL_NO_RIGHT_PAREN;
    }

    @Override
    public void onBinaryOperatorReceived() {
        lastSignal = SIGNAL_BINARY_OPERATOR;
    }
}
