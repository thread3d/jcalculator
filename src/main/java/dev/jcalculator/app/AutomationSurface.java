package dev.jcalculator.app;

import dev.jcalculator.core.AngleType;
import dev.jcalculator.engine.CalculatorMode;
import dev.jcalculator.engine.Command;
import dev.jcalculator.engine.HistoryEntry;
import dev.jcalculator.engine.NumberWidth;
import dev.jcalculator.harness.CalculatorSession;
import dev.jcalculator.harness.Tokens;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The window's scriptable controller.
 *
 * <p>{@link CalculatorWindow} renders this surface and nothing else: every button is a {@link Key}
 * from one of its layouts, and every displayed value comes from its snapshot. That separation is
 * what makes the desktop window drivable without a robot: a harness can press keys by label, switch
 * modes, and read the display without a screen, because the surface is the same state machine the
 * window wires up.
 *
 * <p>Clicking by label resolves the label in the layout of the active mode, so {@code "sin"} only
 * exists on the scientific page and {@code "AND"} only on the programmer page.
 */
public final class AutomationSurface {

    /** One window key. A {@code null} command marks an application action such as HISTORY. */
    public record Key(String label, Command command) {

        public Key {
            Objects.requireNonNull(label, "label");
        }

        /** Whether this key feeds the calculator engine. */
        public boolean isCommand() {
            return command != null;
        }
    }

    /** The state the window renders, as plain values. */
    public record Snapshot(
            String display,
            String expression,
            boolean error,
            CalculatorMode mode,
            int radix,
            NumberWidth width,
            AngleType angleType,
            int parenthesisCount,
            String signal) {
    }

    private final CalculatorSession session;

    /** Creates a standard-mode surface with seed {@code 0}. */
    public AutomationSurface() {
        this(CalculatorMode.STANDARD, 0L);
    }

    /** Creates a surface in the given mode with seed {@code 0}. */
    public AutomationSurface(CalculatorMode mode) {
        this(mode, 0L);
    }

    /** Creates a surface with a seeded random key. */
    public AutomationSurface(CalculatorMode mode, long randomSeed) {
        this.session = new CalculatorSession(mode, randomSeed);
    }

    /** The session behind the surface. */
    public CalculatorSession session() {
        return session;
    }

    // ---- Layouts ---------------------------------------------------------------------------------

    /** The memory keys, which every page shows beside the keypad. */
    public static List<Key> sideKeys() {
        return List.of(
                new Key("MC", Command.MEMORY_CLEAR),
                new Key("MR", Command.MEMORY_RECALL),
                new Key("M+", Command.MEMORY_ADD),
                new Key("M-", Command.MEMORY_SUBTRACT),
                new Key("MS", Command.MEMORY_STORE));
    }

    /** The keypad of the active page, in reading order. */
    public List<Key> keypadKeys() {
        return switch (mode()) {
            case PROGRAMMER -> programmerKeys();
            case SCIENTIFIC -> scientificKeys();
            case STANDARD -> standardKeys();
        };
    }

    /** Every key on the active page: the memory column then the keypad. */
    public List<Key> allKeys() {
        final List<Key> keys = new ArrayList<>(sideKeys());
        keys.addAll(keypadKeys());
        return List.copyOf(keys);
    }

    private static List<Key> standardKeys() {
        return List.of(
                new Key("%", Command.PERCENT),
                new Key("√", Command.SQRT),
                new Key("x²", Command.SQR),
                new Key("1/x", Command.RECIPROCAL),
                new Key("CE", Command.CLEAR_ENTRY),
                new Key("7", Command.SEVEN),
                new Key("8", Command.EIGHT),
                new Key("9", Command.NINE),
                new Key("÷", Command.DIVIDE),
                new Key("C", Command.CLEAR),
                new Key("4", Command.FOUR),
                new Key("5", Command.FIVE),
                new Key("6", Command.SIX),
                new Key("×", Command.MULTIPLY),
                new Key("⌫", Command.BACKSPACE),
                new Key("1", Command.ONE),
                new Key("2", Command.TWO),
                new Key("3", Command.THREE),
                new Key("-", Command.SUBTRACT),
                new Key("±", Command.NEGATE),
                new Key("0", Command.ZERO),
                new Key(".", Command.DECIMAL_POINT),
                new Key("=", Command.EQUALS),
                new Key("+", Command.ADD),
                new Key("HIST", null));
    }

    private static List<Key> scientificKeys() {
        return List.of(
                new Key("sin", Command.SIN),
                new Key("cos", Command.COS),
                new Key("tan", Command.TAN),
                new Key("ln", Command.LN),
                new Key("log", Command.LOG),
                new Key("x²", Command.SQR),
                new Key("√", Command.SQRT),
                new Key("xʸ", Command.POWER),
                new Key("π", Command.PI),
                new Key("e", Command.EULER),
                new Key("n!", Command.FACTORIAL),
                new Key("1/x", Command.RECIPROCAL),
                new Key("(", Command.OPEN_PAREN),
                new Key(")", Command.CLOSE_PAREN),
                new Key("%", Command.PERCENT),
                new Key("7", Command.SEVEN),
                new Key("8", Command.EIGHT),
                new Key("9", Command.NINE),
                new Key("÷", Command.DIVIDE),
                new Key("C", Command.CLEAR),
                new Key("4", Command.FOUR),
                new Key("5", Command.FIVE),
                new Key("6", Command.SIX),
                new Key("×", Command.MULTIPLY),
                new Key("CE", Command.CLEAR_ENTRY),
                new Key("1", Command.ONE),
                new Key("2", Command.TWO),
                new Key("3", Command.THREE),
                new Key("-", Command.SUBTRACT),
                new Key("⌫", Command.BACKSPACE),
                new Key("0", Command.ZERO),
                new Key("±", Command.NEGATE),
                new Key(".", Command.DECIMAL_POINT),
                new Key("+", Command.ADD),
                new Key("=", Command.EQUALS));
    }

    private static List<Key> programmerKeys() {
        return List.of(
                new Key("A", Command.A),
                new Key("B", Command.B),
                new Key("C", Command.C),
                new Key("D", Command.D),
                new Key("E", Command.E),
                new Key("F", Command.F),
                new Key("7", Command.SEVEN),
                new Key("8", Command.EIGHT),
                new Key("9", Command.NINE),
                new Key("÷", Command.DIVIDE),
                new Key("AND", Command.AND),
                new Key("4", Command.FOUR),
                new Key("5", Command.FIVE),
                new Key("6", Command.SIX),
                new Key("×", Command.MULTIPLY),
                new Key("OR", Command.OR),
                new Key("1", Command.ONE),
                new Key("2", Command.TWO),
                new Key("3", Command.THREE),
                new Key("-", Command.SUBTRACT),
                new Key("XOR", Command.XOR),
                new Key("0", Command.ZERO),
                new Key("±", Command.NEGATE),
                new Key("=", Command.EQUALS),
                new Key("+", Command.ADD),
                new Key("NOT", Command.COMPLEMENT),
                new Key("Lsh", Command.LEFT_SHIFT),
                new Key("Rsh", Command.RIGHT_SHIFT),
                new Key("ROL", Command.ROTATE_LEFT),
                new Key("ROR", Command.ROTATE_RIGHT),
                new Key("C", Command.CLEAR_ENTRY),
                new Key("⌫", Command.BACKSPACE),
                new Key("(", Command.OPEN_PAREN),
                new Key(")", Command.CLOSE_PAREN),
                new Key("MOD", Command.MOD),
                new Key("HIST", null),
                new Key("CLR", Command.CLEAR));
    }

    // ---- Driving ---------------------------------------------------------------------------------

    /** Presses a button by the label shown in the active layout. */
    public void click(String label) {
        final Key key = keyFor(label);
        if (!key.isCommand()) {
            throw new IllegalArgumentException(label + " is an application action, not a calculator key");
        }
        session.press(key.command());
    }

    /** Presses a button by token name, which need not be on the active page. */
    public void pressToken(String token) {
        session.press(token);
    }

    /**
     * Flips one bit of the programmer word, as clicking a bit in the bit panel does.
     *
     * @param index the bit to flip, counting from the least significant
     */
    public void toggleBit(int index) {
        session.manager().engine().toggleBit(index);
    }

    /** Presses an engine command directly, as the keyboard shortcuts do. */
    public void pressCommand(Command command) {
        session.press(Objects.requireNonNull(command, "command"));
    }

    /** Types digits, hexadecimal digits and decimal points as number entry. */
    public void type(String text) {
        for (int index = 0; index < text.length(); index++) {
            final char c = text.charAt(index);
            if (c == '.' || (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) {
                session.press(Tokens.require(String.valueOf(c), mode()));
            } else {
                throw new IllegalArgumentException("cannot type: " + c);
            }
        }
    }

    /** Switches page. */
    public void setMode(CalculatorMode mode) {
        session.setMode(Objects.requireNonNull(mode, "mode"));
    }

    // ---- Reading ---------------------------------------------------------------------------------

    public CalculatorMode mode() {
        return session.manager().mode();
    }

    public String display() {
        return session.manager().primaryDisplay();
    }

    public String expression() {
        return session.manager().expression();
    }

    public boolean isError() {
        return session.manager().isError();
    }

    public int radix() {
        return session.manager().engine().radix();
    }

    public NumberWidth width() {
        return session.manager().engine().width();
    }

    public AngleType angleType() {
        return session.manager().engine().angleType();
    }

    public List<HistoryEntry> history() {
        return session.manager().history();
    }

    public Snapshot snapshot() {
        return new Snapshot(display(), expression(), isError(), mode(), radix(), width(), angleType(),
                session.manager().parenthesisCount(), session.manager().lastSignal());
    }

    // ---- Helpers ---------------------------------------------------------------------------------

    private Key keyFor(String label) {
        for (Key key : allKeys()) {
            if (key.label().equals(label)) {
                return key;
            }
        }
        throw new IllegalArgumentException("no key labelled " + label + " on the " + mode() + " page");
    }
}
