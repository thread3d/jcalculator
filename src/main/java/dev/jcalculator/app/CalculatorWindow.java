package dev.jcalculator.app;

import dev.jcalculator.core.Rational;
import dev.jcalculator.diagnostics.Faults;
import dev.jcalculator.diagnostics.IssueJournal;
import dev.jcalculator.diagnostics.IssueSurface;
import dev.jcalculator.engine.CalculatorMode;
import dev.jcalculator.engine.Command;
import dev.jcalculator.engine.HistoryEntry;
import dev.jcalculator.format.NumberFormatter;

import javax.swing.AbstractAction;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntConsumer;

/**
 * Swing front end for jCalculator: the application shell around the calculator pages.
 *
 * <p>The original is a UWP application with a navigation menu, a settings page and a title-bar switch
 * for always-on-top. This window is the same shell: a menu that moves between the three keypad pages,
 * the date page, the converter page and the graphing page; a view menu for the theme, always-on-top,
 * the history and the memory list; an edit menu for copy and paste; and a help menu with the about
 * box. It renders an {@link AutomationSurface}, so the window and every other front end share one
 * state machine.
 *
 * <p>Every control carries its label as both its component name and its accessible name, which is
 * what lets an automation layer find a control by name instead of by screen position.
 */
public final class CalculatorWindow extends JFrame {

    private static final String CALCULATOR_CARD = "calculator";
    private static final String DATE_CARD = "date";
    private static final String CONVERTER_CARD = "converter";
    private static final String GRAPH_CARD = "graphing";

    private final transient AutomationSurface surface = new AutomationSurface();
    private final transient SettingsStore settingsStore;
    private transient Settings settings;
    private final transient CalculatorPage calculatorPage;
    private final transient DatePage datePage;
    private final transient ConverterPage converterPage;
    private final transient GraphPage graphPage;
    private final transient JPanel cards = new JPanel(new CardLayout());
    private final transient JCheckBoxMenuItem alwaysOnTopItem = new JCheckBoxMenuItem("Always on top");
    private transient AppPage currentPage = AppPage.STANDARD;

    public CalculatorWindow() {
        this(SettingsStore.shared());
    }

    /**
     * Creates the window over an explicit settings store.
     *
     * @param store where the preferences are read and written
     */
    public CalculatorWindow(SettingsStore store) {
        super(AppInfo.displayVersion());
        this.settingsStore = store;
        this.settings = store.load();
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(560, 660));
        setLayout(new BorderLayout());

        calculatorPage = new CalculatorPage(surface, this::showHistory);
        datePage = new DatePage(surface);
        converterPage = new ConverterPage();
        graphPage = new GraphPage();
        cards.add(calculatorPage, CALCULATOR_CARD);
        cards.add(datePage, DATE_CARD);
        cards.add(converterPage, CONVERTER_CARD);
        cards.add(graphPage, GRAPH_CARD);
        add(cards, BorderLayout.CENTER);

        setJMenuBar(buildMenuBar());
        setAlwaysOnTop(settings.alwaysOnTop());
        alwaysOnTopItem.setSelected(settings.alwaysOnTop());
        installKeyboardShortcuts();
        selectPage(AppPage.STANDARD);
        applyTheme(settings.theme());
        pack();
        setLocationRelativeTo(null);
    }

    public static void main(String[] args) {
        installErrorReporting();
        SwingUtilities.invokeLater(() -> new CalculatorWindow().setVisible(true));
    }

    /** Records an unexpected failure on any thread, then lets the stack trace be printed. */
    private static void installErrorReporting() {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                IssueJournal.shared().record(Faults.internalError(IssueSurface.SWING, error,
                        Map.of("thread", thread.getName(), "surface", IssueSurface.SWING.wireName())));
            } catch (RuntimeException ignored) {
                // Best effort: the stack trace below is still printed.
            }
            error.printStackTrace();
        });
    }

    // ---- Automation accessors ---------------------------------------------------------------------

    /** The controller the window renders. */
    public AutomationSurface surface() {
        return surface;
    }

    /** The text currently rendered in the display field. */
    public String displayText() {
        return calculatorPage.displayText();
    }

    /** The text currently rendered in the expression line. */
    public String expressionText() {
        return calculatorPage.expressionText();
    }

    /** The accessible name of the display control, which automation tools key off. */
    public String displayAccessibleName() {
        return calculatorPage.displayAccessibleName();
    }

    /** Finds a visible button by the label it shows. */
    public Optional<JButton> findButton(String label) {
        return calculatorPage.findButton(label);
    }

    /** Every label on the calculator page, keypad first. */
    public List<String> buttonLabels() {
        return calculatorPage.buttonLabels();
    }

    /** Clicks the button with this label, exactly as a user would. */
    public void clickButton(String label) {
        calculatorPage.clickButton(label);
    }

    /** Clicks the bit button for a position on the programmer page's bit panel. */
    public void clickBit(int bit) {
        calculatorPage.findBitButton(bit)
                .orElseThrow(() -> new IllegalArgumentException("no bit " + bit))
                .doClick(0);
    }

    /** Presses an engine command, as a keyboard shortcut does. */
    public void pressCommand(Command command) {
        calculatorPage.press(command);
    }

    /** Selects a calculator page through the mode selector. */
    public void selectMode(CalculatorMode mode) {
        selectPage(switch (mode) {
            case SCIENTIFIC -> AppPage.SCIENTIFIC;
            case PROGRAMMER -> AppPage.PROGRAMMER;
            case STANDARD -> AppPage.STANDARD;
        });
    }

    // ---- Pages ------------------------------------------------------------------------------------

    /** Moves to a page, selecting the calculator mode when the page is a keypad. */
    public void selectPage(AppPage page) {
        currentPage = page;
        if (page.mode() != null) {
            calculatorPage.selectMode(page.mode());
        }
        ((CardLayout) cards.getLayout()).show(cards,
                switch (page) {
                    case DATE -> DATE_CARD;
                    case CONVERTER -> CONVERTER_CARD;
                    case GRAPHING -> GRAPH_CARD;
                    default -> CALCULATOR_CARD;
                });
        calculatorPage.refresh();
        refreshPageSelection();
    }

    /** The page currently shown. */
    public AppPage page() {
        return currentPage;
    }

    /** The converter page, for the automation surface and the tests. */
    ConverterPage converterPage() {
        return converterPage;
    }

    /** The date page, for the automation surface and the tests. */
    DatePage datePage() {
        return datePage;
    }

    /** The graphing page, for the automation surface and the tests. */
    GraphPage graphPage() {
        return graphPage;
    }

    /** The current preferences, for an automation surface or a test. */
    public Settings settings() {
        return settings;
    }

    // ---- Menu -------------------------------------------------------------------------------------

    private JMenuBar buildMenuBar() {
        final JMenuBar bar = new JMenuBar();
        bar.add(fileMenu());
        bar.add(pagesMenu());
        bar.add(viewMenu());
        bar.add(helpMenu());
        return bar;
    }

    private JMenu fileMenu() {
        final JMenu menu = new JMenu("File");
        addItem(menu, "Copy", this::copyDisplay);
        addItem(menu, "Paste", this::paste);
        menu.addSeparator();
        addItem(menu, "Clear", () -> pressCommand(Command.CLEAR));
        menu.addSeparator();
        addItem(menu, "Exit", this::dispose);
        return menu;
    }

    private JMenu pagesMenu() {
        final JMenu menu = new JMenu("Pages");
        AppPage previous = null;
        for (AppPage page : AppPage.values()) {
            if (previous != null && previous.isCalculatorPage() && !page.isCalculatorPage()) {
                menu.addSeparator();
            }
            final JRadioButtonMenuItem item =
                    new JRadioButtonMenuItem(page.title(), page == AppPage.STANDARD);
            item.setName("page:" + page.name());
            item.addActionListener(event -> selectPage(page));
            menu.add(item);
            previous = page;
        }
        return menu;
    }

    private JMenu viewMenu() {
        final JMenu menu = new JMenu("View");
        final JMenu theme = new JMenu("Theme");
        for (Settings.Theme choice : Settings.Theme.values()) {
            final JRadioButtonMenuItem item =
                    new JRadioButtonMenuItem(choice.title(), choice == settings.theme());
            item.setName("theme:" + choice.name());
            item.addActionListener(event -> changeTheme(choice));
            theme.add(item);
        }
        menu.add(theme);

        alwaysOnTopItem.setName("always-on-top");
        alwaysOnTopItem.addActionListener(event -> {
            settings = settings.withAlwaysOnTop(alwaysOnTopItem.isSelected());
            setAlwaysOnTop(settings.alwaysOnTop());
            settingsStore.save(settings);
        });
        menu.add(alwaysOnTopItem);
        menu.addSeparator();
        addItem(menu, "History", this::showHistory);
        addItem(menu, "Memory", this::showMemory);
        addItem(menu, "Clear history", () -> {
            surface.session().manager().clearHistory();
            calculatorPage.refresh();
        });
        return menu;
    }

    private JMenu helpMenu() {
        final JMenu menu = new JMenu("Help");
        addItem(menu, "About", this::showAbout);
        addItem(menu, "Keyboard shortcuts", this::showShortcuts);
        return menu;
    }

    private void addItem(JMenu menu, String label, Runnable action) {
        final JMenuItem item = new JMenuItem(label);
        item.setName(label);
        item.getAccessibleContext().setAccessibleName(label);
        item.addActionListener(event -> action.run());
        menu.add(item);
    }

    private void refreshPageSelection() {
        final JMenuBar bar = getJMenuBar();
        if (bar == null) {
            return;
        }
        for (int menuIndex = 0; menuIndex < bar.getMenuCount(); menuIndex++) {
            final JMenu menu = bar.getMenu(menuIndex);
            for (int itemIndex = 0; itemIndex < menu.getItemCount(); itemIndex++) {
                final JMenuItem item = menu.getItem(itemIndex);
                if (item instanceof JRadioButtonMenuItem radio && item.getName() != null
                        && item.getName().startsWith("page:")) {
                    radio.setSelected(item.getName().equals("page:" + currentPage.name()));
                }
            }
        }
    }

    // ---- Appearance -------------------------------------------------------------------------------

    private void changeTheme(Settings.Theme choice) {
        settings = settings.withTheme(choice);
        applyTheme(choice);
        settingsStore.save(settings);
    }

    /** Applies a colour scheme; a look and feel that cannot be installed leaves the current one. */
    private void applyTheme(Settings.Theme theme) {
        try {
            if (theme == Settings.Theme.SYSTEM) {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } else {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
                paintPalette(theme == Settings.Theme.DARK);
            }
            SwingUtilities.updateComponentTreeUI(this);
        } catch (Exception ignored) {
            // An unavailable look and feel is not a reason to fail to start.
        }
    }

    private static void paintPalette(boolean dark) {
        final Color background = dark ? new Color(0x22, 0x22, 0x22) : new Color(0xF4, 0xF4, 0xF4);
        final Color foreground = dark ? new Color(0xEE, 0xEE, 0xEE) : new Color(0x11, 0x11, 0x11);
        UIManager.put("Panel.background", background);
        UIManager.put("OptionPane.background", background);
        UIManager.put("Label.foreground", foreground);
        UIManager.put("Button.background", dark ? new Color(0x33, 0x33, 0x33) : new Color(0xE8, 0xE8, 0xE8));
        UIManager.put("Button.foreground", foreground);
        UIManager.put("MenuBar.background", background);
        UIManager.put("Menu.foreground", foreground);
        UIManager.put("MenuBar.foreground", foreground);
        UIManager.put("TextField.background", dark ? new Color(0x2E, 0x2E, 0x2E) : Color.WHITE);
        UIManager.put("TextField.foreground", foreground);
    }

    // ---- Clipboard --------------------------------------------------------------------------------

    /** Copies the display to the system clipboard. */
    public void copyDisplay() {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(surface.session().manager().rawDisplay()), null);
        } catch (RuntimeException ignored) {
            // A clipboard the platform refuses is not an error in the calculator.
        }
    }

    /** Pastes the system clipboard through {@link #pasteText(String)}. */
    public boolean paste() {
        try {
            final Object data = Toolkit.getDefaultToolkit().getSystemClipboard()
                    .getData(DataFlavor.stringFlavor);
            return data instanceof String text && pasteText(text);
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Pastes a text when the active page accepts it.
     *
     * @return whether the text was entered
     */
    public boolean pasteText(String text) {
        final CalculatorMode mode = surface.mode();
        final int radix = surface.radix();
        if (!ClipboardText.accepts(text, mode, radix)) {
            return false;
        }
        final List<String> tokens = ClipboardText.tokens(text, mode, radix);
        if (!ClipboardText.everyTokenIsAButton(tokens, mode)) {
            return false;
        }
        selectPage(switch (mode) {
            case SCIENTIFIC -> AppPage.SCIENTIFIC;
            case PROGRAMMER -> AppPage.PROGRAMMER;
            case STANDARD -> AppPage.STANDARD;
        });
        for (String token : tokens) {
            surface.session().press(token);
        }
        calculatorPage.refresh();
        return true;
    }

    // ---- Dialogs ----------------------------------------------------------------------------------

    /** Shows the history list: copy an entry's expression, or clear the list. */
    private void showHistory() {
        final List<HistoryEntry> entries = surface.history();
        final DefaultListModel<String> model = new DefaultListModel<>();
        for (HistoryEntry entry : entries) {
            model.addElement(entry.expression() + "  " + entry.result());
        }
        final JList<String> list = new JList<>(model);
        list.getAccessibleContext().setAccessibleName("History entries");
        final JFrame frame = new JFrame("History");
        frame.setLayout(new BorderLayout());
        frame.add(new JScrollPane(list), BorderLayout.CENTER);

        final JPanel buttons = new JPanel();
        final JButton copy = new JButton("Copy");
        copy.addActionListener(event -> {
            final int index = list.getSelectedIndex();
            if (index >= 0 && index < entries.size()) {
                try {
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                            new StringSelection(entries.get(index).expression()), null);
                } catch (RuntimeException ignored) {
                    // Best effort.
                }
            }
        });
        final JButton load = new JButton("Load");
        load.setName("history-load");
        load.getAccessibleContext().setAccessibleName("Re-run the selected calculation");
        load.addActionListener(event -> {
            final int index = list.getSelectedIndex();
            if (index >= 0 && index < entries.size()) {
                surface.session().replay(entries.get(index));
                calculatorPage.refresh();
            }
        });
        final JButton clear = new JButton("Clear all");
        clear.addActionListener(event -> {
            surface.session().manager().clearHistory();
            model.clear();
            calculatorPage.refresh();
        });
        final JButton close = new JButton("Close");
        close.addActionListener(event -> frame.dispose());
        buttons.add(load);
        buttons.add(copy);
        buttons.add(clear);
        buttons.add(close);
        frame.add(buttons, BorderLayout.SOUTH);
        frame.setSize(420, 320);
        frame.setLocationRelativeTo(this);
        frame.setAlwaysOnTop(settings.alwaysOnTop());
        frame.setVisible(true);
    }

    /** Shows the memorized values with the per-slot keys the original's memory panel has. */
    private void showMemory() {
        final DefaultListModel<String> model = new DefaultListModel<>();
        final JList<String> list = new JList<>(model);
        list.getAccessibleContext().setAccessibleName("Memorized values");
        final JFrame frame = new JFrame("Memory");
        frame.setLayout(new BorderLayout());
        frame.add(new JScrollPane(list), BorderLayout.CENTER);
        final Runnable reload = () -> {
            model.clear();
            for (Rational value : surface.session().manager().memorizedNumbers()) {
                model.addElement(NumberFormatter.format(value, surface.mode().precision(), false));
            }
        };
        reload.run();

        final JPanel buttons = new JPanel();
        buttons.add(slotButton("M+", list, surface.session().manager()::memorizedNumberAdd, reload));
        buttons.add(slotButton("M-", list, surface.session().manager()::memorizedNumberSubtract, reload));
        buttons.add(slotButton("MC", list, surface.session().manager()::memorizedNumberClear, reload));
        final JButton clearAll = new JButton("Clear all");
        clearAll.addActionListener(event -> {
            surface.session().manager().memorizedNumberClearAll();
            reload.run();
        });
        final JButton close = new JButton("Close");
        close.addActionListener(event -> frame.dispose());
        buttons.add(clearAll);
        buttons.add(close);
        frame.add(buttons, BorderLayout.SOUTH);
        frame.setSize(320, 260);
        frame.setLocationRelativeTo(this);
        frame.setAlwaysOnTop(settings.alwaysOnTop());
        frame.setVisible(true);
    }

    private JButton slotButton(String label, JList<String> list, IntConsumer action, Runnable reload) {
        final JButton button = new JButton(label);
        button.setName(label);
        button.getAccessibleContext().setAccessibleName(label);
        button.addActionListener(event -> {
            final int index = list.getSelectedIndex();
            if (index < 0) {
                return;
            }
            action.accept(index);
            reload.run();
            calculatorPage.refresh();
        });
        return button;
    }

    /** The about box: version, licence and where the issue journal lives. */
    private void showAbout() {
        JOptionPane.showMessageDialog(this,
                AppInfo.NAME + " " + AppInfo.VERSION + "\n"
                        + "A Java implementation of the Windows Calculator.\n"
                        + "MIT licence; derived from Microsoft's MIT-licensed calculator.\n"
                        + "Issues: " + IssueJournal.shared().location(),
                "About " + AppInfo.NAME, JOptionPane.INFORMATION_MESSAGE);
    }

    private void showShortcuts() {
        JOptionPane.showMessageDialog(this,
                "0-9 . + - * /    enter a number or an operator\n"
                        + "Enter            equals\n"
                        + "Escape           clear\n"
                        + "Backspace        delete the last digit\n"
                        + "Ctrl+C / Ctrl+V  copy the display / paste a number\n"
                        + "Ctrl+1 … Ctrl+6  calculator, date, converter and graphing pages\n"
                        + "Ctrl+H / Ctrl+M  history / memory",
                "Keyboard shortcuts", JOptionPane.INFORMATION_MESSAGE);
    }

    // ---- Keyboard ---------------------------------------------------------------------------------

    private void installKeyboardShortcuts() {
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "equals", () -> pressCommand(Command.EQUALS));
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clear", () -> pressCommand(Command.CLEAR));
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "backspace",
                () -> pressCommand(Command.BACKSPACE));
        bind(KeyStroke.getKeyStroke('.'), "decimal", () -> pressCommand(Command.DECIMAL_POINT));
        for (int digit = 0; digit < 10; digit++) {
            final Command command = Command.values()[Command.ZERO.ordinal() + digit];
            bind(KeyStroke.getKeyStroke(KeyEvent.VK_0 + digit, 0), "digit" + digit,
                    () -> pressCommand(command));
        }
        final String[] operators = {"+", "-", "*", "/"};
        final Command[] commands = {Command.ADD, Command.SUBTRACT, Command.MULTIPLY, Command.DIVIDE};
        for (int index = 0; index < operators.length; index++) {
            final Command command = commands[index];
            bind(KeyStroke.getKeyStroke(operators[index].charAt(0)), "operator" + operators[index],
                    () -> pressCommand(command));
        }
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), "copy", this::copyDisplay);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), "paste", this::paste);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK), "history", this::showHistory);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_M, InputEvent.CTRL_DOWN_MASK), "memory", this::showMemory);
        final AppPage[] pages = AppPage.values();
        for (int index = 0; index < pages.length; index++) {
            final AppPage page = pages[index];
            bind(KeyStroke.getKeyStroke(KeyEvent.VK_1 + index, InputEvent.CTRL_DOWN_MASK),
                    "page" + index, () -> selectPage(page));
        }
    }

    private void bind(KeyStroke stroke, String name, Runnable action) {
        getRootPane().getInputMap(JPanel.WHEN_IN_FOCUSED_WINDOW).put(stroke, name);
        getRootPane().getActionMap().put(name, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                action.run();
            }
        });
    }
}
