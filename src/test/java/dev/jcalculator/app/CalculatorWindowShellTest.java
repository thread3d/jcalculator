package dev.jcalculator.app;

import dev.jcalculator.engine.CalculatorMode;
import dev.jcalculator.engine.Command;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The application shell: page navigation, the converter and date workspaces, the bit panel, the
 * graphing page, clipboard pasting and the preferences.
 *
 * <p>Everything here needs a window, so each test is skipped on a display-less machine, exactly as
 * the driver tests are.
 */
class CalculatorWindowShellTest {

    @TempDir
    Path directory;

    private static void onEdt(Runnable action) {
        try {
            javax.swing.SwingUtilities.invokeAndWait(action);
        } catch (Exception failure) {
            throw new AssertionError("the window could not be driven", failure);
        }
    }

    private static <T> T read(java.util.function.Supplier<T> supplier) {
        final AtomicReference<T> value = new AtomicReference<>();
        onEdt(() -> value.set(supplier.get()));
        return value.get();
    }

    private CalculatorWindow window() {
        final SettingsStore store = new SettingsStore(directory.resolve("settings.properties"));
        final CalculatorWindow window = new CalculatorWindow(store);
        window.setVisible(true);
        return window;
    }

    @Test
    void theShellMovesBetweenPages() {
        Assumptions.assumeTrue(UiDriver.isSupported(), "no display available");
        final CalculatorWindow window = window();
        try {
            assertEquals(AppPage.STANDARD, read(window::page));

            onEdt(() -> window.selectPage(AppPage.CONVERTER));
            assertEquals(AppPage.CONVERTER, read(window::page));

            onEdt(() -> window.selectPage(AppPage.DATE));
            assertEquals(AppPage.DATE, read(window::page));

            onEdt(() -> window.selectPage(AppPage.GRAPHING));
            assertEquals(AppPage.GRAPHING, read(window::page));

            onEdt(() -> window.selectPage(AppPage.PROGRAMMER));
            assertEquals(AppPage.PROGRAMMER, read(window::page));
            assertEquals(CalculatorMode.PROGRAMMER, read(() -> window.surface().mode()));
        } finally {
            onEdt(window::dispose);
        }
    }

    @Test
    void theConverterPageConvertsAndSuggestsTheOtherUnits() {
        Assumptions.assumeTrue(UiDriver.isSupported(), "no display available");
        final CalculatorWindow window = window();
        try {
            onEdt(() -> {
                window.selectPage(AppPage.CONVERTER);
                window.converterPage().select("length", "Meter", "Foot");
                window.converterPage().setValue("2.5");
            });
            assertEquals("8.2021", read(() -> window.converterPage().resultText()));
            assertTrue(read(() -> window.converterPage().supplementaryCount()) > 5);
        } finally {
            onEdt(window::dispose);
        }
    }

    @Test
    void theDatePageComputesADifferenceAndADuration() {
        Assumptions.assumeTrue(UiDriver.isSupported(), "no display available");
        final CalculatorWindow window = window();
        try {
            onEdt(() -> {
                window.selectPage(AppPage.DATE);
                window.datePage().showDifference();
            });
            assertEquals("11 months 4 weeks 2 days", read(() -> window.datePage().differenceText()));

            onEdt(() -> {
                window.datePage().addDuration();
            });
            assertEquals("2024-01-01", read(() -> window.datePage().durationText()));
        } finally {
            onEdt(window::dispose);
        }
    }

    @Test
    void aPastedNumberIsEnteredAndAnUnacceptableOneIsNot() {
        Assumptions.assumeTrue(UiDriver.isSupported(), "no display available");
        final CalculatorWindow window = window();
        try {
            assertTrue(read(() -> window.pasteText("42")));
            assertEquals("42", read(window::displayText));

            assertFalse(read(() -> window.pasteText("sin(1)")));
            assertEquals("42", read(window::displayText));
        } finally {
            onEdt(window::dispose);
        }
    }

    @Test
    void aPasteOnTheProgrammerPageIsBoundByItsBase() {
        Assumptions.assumeTrue(UiDriver.isSupported(), "no display available");
        final CalculatorWindow window = window();
        try {
            onEdt(() -> window.selectPage(AppPage.PROGRAMMER));
            assertFalse(read(() -> window.pasteText("FF")));

            onEdt(() -> window.pressCommand(Command.RADIX_16));
            assertTrue(read(() -> window.pasteText("FF")));
            assertEquals("FF", read(window::displayText));
        } finally {
            onEdt(window::dispose);
        }
    }

    @Test
    void aBitCanBeFlippedFromTheBitPanel() {
        Assumptions.assumeTrue(UiDriver.isSupported(), "no display available");
        final CalculatorWindow window = window();
        try {
            onEdt(() -> {
                window.selectPage(AppPage.PROGRAMMER);
                window.pressCommand(Command.CLEAR);
                window.clickBit(3);
            });
            assertEquals("8", read(window::displayText));

            onEdt(() -> window.clickBit(3));
            assertEquals("0", read(window::displayText));
        } finally {
            onEdt(window::dispose);
        }
    }

    @Test
    void theGraphPagePlotsAFunction() {
        Assumptions.assumeTrue(UiDriver.isSupported(), "no display available");
        final CalculatorWindow window = window();
        try {
            onEdt(() -> {
                window.selectPage(AppPage.GRAPHING);
                window.graphPage().setExpression("x^2");
                window.graphPage().setRange("-2", "2");
            });
            assertEquals(401, read(() -> window.graphPage().plotResult().samples().size()));
        } finally {
            onEdt(window::dispose);
        }
    }

    @Test
    void anUnplottableExpressionIsExplained() {
        Assumptions.assumeTrue(UiDriver.isSupported(), "no display available");
        final CalculatorWindow window = window();
        try {
            onEdt(() -> window.graphPage().setExpression("z+1"));
            assertFalse(read(() -> window.graphPage().statusText()).isBlank());
        } finally {
            onEdt(window::dispose);
        }
    }

    @Test
    void theStoredPreferencesAreAppliedAtStartup() {
        Assumptions.assumeTrue(UiDriver.isSupported(), "no display available");
        final SettingsStore store = new SettingsStore(directory.resolve("settings.properties"));
        store.save(new Settings(Settings.Theme.DARK, true));

        final CalculatorWindow window = new CalculatorWindow(store);
        try {
            assertEquals(Settings.Theme.DARK, read(() -> window.settings().theme()));
            assertTrue(read(window::isAlwaysOnTop));
        } finally {
            onEdt(window::dispose);
        }
    }
}
