package dev.jcalculator.app;

import dev.jcalculator.engine.CalculatorMode;
import dev.jcalculator.engine.Command;
import dev.jcalculator.harness.Tokens;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Drives the real desktop window deterministically.
 *
 * <p>Every action runs on the Swing event thread and returns only after the window has repainted its
 * state, so a harness can read the display immediately afterwards without sleeping or polling. Keys
 * are clicked through the actual buttons, which means the same listeners a user triggers are the
 * ones under test.
 *
 * <p>The window needs a display. On a headless machine use {@link AutomationSurface} instead: it is
 * the same controller the window renders, and it needs no screen. {@link #isSupported()} reports
 * whether a window can be opened at all.
 */
public final class UiDriver implements AutoCloseable {

    /** What the window is showing, read from the widgets themselves. */
    public record Snapshot(
            String display,
            String expression,
            boolean error,
            CalculatorMode mode,
            int radix,
            String width,
            String angle) {
    }

    private final CalculatorWindow window;

    private UiDriver(CalculatorWindow window) {
        this.window = window;
    }

    /** Whether a window can be opened in this environment. */
    public static boolean isSupported() {
        return !GraphicsEnvironment.isHeadless();
    }

    /**
     * Opens the window and returns a driver for it.
     *
     * @throws IllegalStateException when there is no display available
     */
    public static UiDriver launch() {
        if (!isSupported()) {
            throw new IllegalStateException(
                    "jCalculator's window needs a display; run in a desktop session, or use "
                            + "AutomationSurface for headless automation");
        }
        return new UiDriver(onEdt(() -> {
            final CalculatorWindow created = new CalculatorWindow();
            created.setVisible(true);
            return created;
        }));
    }

    /** The window being driven. */
    public CalculatorWindow window() {
        return window;
    }

    // ---- Reading ---------------------------------------------------------------------------------

    /** The display text as rendered. */
    public String display() {
        return onEdt(window::displayText);
    }

    /** The expression line as rendered. */
    public String expression() {
        return onEdt(window::expressionText);
    }

    /** Whether the window is showing an error. */
    public boolean isError() {
        return onEdt(() -> window.surface().isError());
    }

    /** The labels currently on screen. */
    public List<String> buttons() {
        return onEdt(window::buttonLabels);
    }

    /** A snapshot read from the widgets. */
    public Snapshot snapshot() {
        return onEdt(() -> {
            final AutomationSurface.Snapshot state = window.surface().snapshot();
            return new Snapshot(
                    window.displayText(),
                    window.expressionText(),
                    state.error(),
                    state.mode(),
                    state.radix(),
                    state.width().name(),
                    state.angleType().name());
        });
    }

    // ---- Driving ---------------------------------------------------------------------------------

    /** Clicks the button with this label. */
    public void click(String label) {
        onEdt(() -> {
            window.clickButton(label);
            return null;
        });
    }

    /** Presses a token, as a keyboard shortcut does; the token need not be on screen. */
    public void press(String token) {
        final Command command = Tokens.require(token, window.surface().mode());
        onEdt(() -> {
            window.pressCommand(command);
            return null;
        });
    }

    /** Types digits, hexadecimal digits and decimal points one key at a time. */
    public void type(String text) {
        onEdt(() -> {
            final CalculatorMode mode = window.surface().mode();
            for (int index = 0; index < text.length(); index++) {
                window.pressCommand(Tokens.require(String.valueOf(text.charAt(index)), mode));
            }
            return null;
        });
    }

    /** Selects a page through the mode selector. */
    public void selectMode(String name) {
        final CalculatorMode mode = Tokens.modeFor(name);
        onEdt(() -> {
            window.selectMode(mode);
            return null;
        });
    }

    // ---- Lifecycle -------------------------------------------------------------------------------

    /** Hides and disposes the window. */
    @Override
    public void close() {
        onEdt(() -> {
            window.setVisible(false);
            window.dispose();
            return null;
        });
    }

    private static <T> T onEdt(Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        if (SwingUtilities.isEventDispatchThread()) {
            return action.get();
        }
        final AtomicReference<T> result = new AtomicReference<>();
        final AtomicReference<RuntimeException> failure = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    result.set(action.get());
                } catch (RuntimeException exception) {
                    failure.set(exception);
                }
            });
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while driving the window", exception);
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException("window action failed", exception.getCause());
        }
        if (failure.get() != null) {
            throw failure.get();
        }
        return result.get();
    }
}
