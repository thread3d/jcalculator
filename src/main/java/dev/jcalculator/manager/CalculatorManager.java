package dev.jcalculator.manager;

import dev.jcalculator.core.Rational;
import dev.jcalculator.engine.CalcEngine;
import dev.jcalculator.engine.CalculatorMode;
import dev.jcalculator.engine.Command;
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

    private final CalcEngine engine;
    private final Map<CalculatorMode, List<HistoryEntry>> histories = new EnumMap<>(CalculatorMode.class);
    private final List<Rational> memorizedNumbers = new ArrayList<>();

    private CalculatorMode mode;
    private String primaryDisplay = "0";
    private String expression = "";
    private boolean error;
    private String errorMessage;

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
            case MEMORY_STORE -> {
                engine.sendCommand(Command.MEMORY_STORE);
                rememberCurrentValue();
            }
            case MEMORY_ADD -> {
                engine.sendCommand(Command.MEMORY_ADD);
                adjustFirstMemory(engine.currentValue());
            }
            case MEMORY_SUBTRACT -> {
                engine.sendCommand(Command.MEMORY_SUBTRACT);
                adjustFirstMemory(engine.currentValue().negate());
            }
            case MEMORY_CLEAR -> {
                engine.sendCommand(Command.MEMORY_CLEAR);
                memorizedNumbers.clear();
            }
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
    }

    public void reset() {
        engine.reset();
        memorizedNumbers.clear();
        histories.values().forEach(List::clear);
        primaryDisplay = engine.display();
        expression = "";
        error = false;
        errorMessage = null;
    }

    // ---- Memory ---------------------------------------------------------------------------------

    private void rememberCurrentValue() {
        if (error) {
            return;
        }
        memorizedNumbers.add(0, engine.memoryValue());
        while (memorizedNumbers.size() > MAX_MEMORY_SIZE) {
            memorizedNumbers.remove(memorizedNumbers.size() - 1);
        }
    }

    private void adjustFirstMemory(Rational delta) {
        if (error) {
            return;
        }
        if (memorizedNumbers.isEmpty()) {
            memorizedNumbers.add(engine.memoryValue());
        } else {
            memorizedNumbers.set(0, engine.memoryValue());
        }
    }

    /** Recalls a memorized slot into the display. */
    public void recallMemory(int index) {
        if (index < 0 || index >= memorizedNumbers.size()) {
            throw new IndexOutOfBoundsException("no memory at index " + index);
        }
        engine.setMemoryValue(memorizedNumbers.get(index));
        engine.sendCommand(Command.MEMORY_RECALL);
    }

    public List<Rational> memorizedNumbers() {
        return List.copyOf(memorizedNumbers);
    }

    public void clearMemory() {
        engine.sendCommand(Command.MEMORY_CLEAR);
        memorizedNumbers.clear();
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
    public void onExpression(String currentExpression) {
        expression = currentExpression;
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
        // The engine already refused the digit; a UI may beep here.
    }
}
