package dev.jcalculator.app;

import dev.jcalculator.engine.CalculatorMode;

/**
 * One page of the application, as the original's navigation lists them.
 *
 * <p>The three calculator pages switch the session's mode; the date, converter and graphing pages
 * are separate workspaces over the same calculator model.
 */
public enum AppPage {

    STANDARD("Standard", CalculatorMode.STANDARD),
    SCIENTIFIC("Scientific", CalculatorMode.SCIENTIFIC),
    PROGRAMMER("Programmer", CalculatorMode.PROGRAMMER),
    DATE("Date Calculation", null),
    CONVERTER("Converter", null),
    GRAPHING("Graphing", null);

    private final String title;
    private final CalculatorMode mode;

    AppPage(String title, CalculatorMode mode) {
        this.title = title;
        this.mode = mode;
    }

    /** The label the navigation shows. */
    public String title() {
        return title;
    }

    /** The calculator mode this page selects, or {@code null} for a page that is not a keypad. */
    public CalculatorMode mode() {
        return mode;
    }

    /** Whether the page is one of the calculator keypads. */
    public boolean isCalculatorPage() {
        return mode != null;
    }
}
