package dev.jcalculator.app;

import dev.jcalculator.diagnostics.Faults;
import dev.jcalculator.diagnostics.IssueJournal;
import dev.jcalculator.diagnostics.IssueSurface;
import dev.jcalculator.engine.CalculatorMode;
import dev.jcalculator.engine.Command;
import dev.jcalculator.engine.HistoryEntry;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Swing front end for jCalculator.
 *
 * <p>The original application is a XAML page; this is the same calculator as a desktop window:
 * a mode selector, the expression line, the display, and the keys of the selected mode. It renders
 * an {@link AutomationSurface}, which is also the surface a harness drives, so the window and every
 * other front end share one state machine.
 *
 * <p>Every button carries its label as both its component name and its accessible name, and the
 * display, expression line and selectors do too. That is what lets an automation layer — or an
 * accessibility tool — find a control by name instead of by screen position.
 */
public final class CalculatorWindow extends JFrame {

    private final Font displayFont = new Font(Font.SANS_SERIF, Font.BOLD, 42);
    private final Font expressionFont = new Font(Font.SANS_SERIF, Font.PLAIN, 16);

    private final AutomationSurface surface = new AutomationSurface();
    private final JLabel expressionLabel = new JLabel(" ", SwingConstants.RIGHT);
    private final JTextField displayField = new JTextField("0");
    private final JComboBox<String> modeSelector = new JComboBox<>(new String[]{"Standard", "Scientific", "Programmer"});
    private final JComboBox<String> baseSelector = new JComboBox<>(new String[]{"DEC", "HEX", "OCT", "BIN"});
    private final JComboBox<String> widthSelector = new JComboBox<>(new String[]{"QWORD", "DWORD", "WORD", "BYTE"});
    private final JComboBox<String> angleSelector = new JComboBox<>(new String[]{"DEG", "RAD", "GRAD"});
    private final JPanel keypad = new JPanel(new GridLayout(0, 5, 6, 6));
    private final JPanel sideButtons = new JPanel(new GridLayout(0, 1, 6, 6));

    /**
     * True while {@link #refresh()} is writing the selectors, so their listeners can ignore the
     * echo. {@code JComboBox.setSelectedIndex} fires even when the index does not change, and an
     * echoed RADIX or ANGLE command would otherwise abandon the number being typed.
     */
    private boolean refreshing;

    public CalculatorWindow() {
        super(AppInfo.displayVersion());
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(520, 620));
        setLayout(new BorderLayout(10, 10));

        final JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.X_AXIS));
        top.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        top.add(modeSelector);
        top.add(Box.createHorizontalStrut(8));
        top.add(baseSelector);
        top.add(Box.createHorizontalStrut(8));
        top.add(widthSelector);
        top.add(Box.createHorizontalStrut(8));
        top.add(angleSelector);
        top.add(Box.createHorizontalGlue());
        add(top, BorderLayout.NORTH);

        final JPanel displayPanel = new JPanel();
        displayPanel.setLayout(new BoxLayout(displayPanel, BoxLayout.Y_AXIS));
        displayPanel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        expressionLabel.setFont(expressionFont);
        expressionLabel.setForeground(new Color(0x55, 0x55, 0x55));
        expressionLabel.setAlignmentX(RIGHT_ALIGNMENT);
        displayField.setFont(displayFont);
        displayField.setHorizontalAlignment(SwingConstants.RIGHT);
        displayField.setEditable(false);
        displayField.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        displayField.setBackground(Color.WHITE);
        displayPanel.add(expressionLabel);
        displayPanel.add(displayField);
        add(displayPanel, BorderLayout.CENTER);

        final JPanel body = new JPanel(new BorderLayout(8, 8));
        body.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        sideButtons.setPreferredSize(new Dimension(96, 0));
        body.add(sideButtons, BorderLayout.WEST);
        body.add(keypad, BorderLayout.CENTER);
        add(body, BorderLayout.SOUTH);

        installAccessibleNames();
        installSelectors();
        installKeyboardShortcuts();
        rebuild();
        refresh();
        pack();
        setLocationRelativeTo(null);
    }

    public static void main(String[] args) {
        installErrorReporting();
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // The cross-platform look and feel is the fallback.
            }
            new CalculatorWindow().setVisible(true);
        });
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
        return displayField.getText();
    }

    /** The text currently rendered in the expression line. */
    public String expressionText() {
        return expressionLabel.getText().trim();
    }

    /** The accessible name of the display control, which automation tools key off. */
    public String displayAccessibleName() {
        return displayField.getAccessibleContext().getAccessibleName();
    }

    /** Finds a visible button by the label it shows. */
    public Optional<JButton> findButton(String label) {
        for (Component component : keypad.getComponents()) {
            if (component instanceof JButton button && label.equals(button.getText())) {
                return Optional.of(button);
            }
        }
        for (Component component : sideButtons.getComponents()) {
            if (component instanceof JButton button && label.equals(button.getText())) {
                return Optional.of(button);
            }
        }
        return Optional.empty();
    }

    /** Every label currently on screen, keypad first. */
    public List<String> buttonLabels() {
        final List<String> labels = new ArrayList<>();
        for (Component component : sideButtons.getComponents()) {
            if (component instanceof JButton button) {
                labels.add(button.getText());
            }
        }
        for (Component component : keypad.getComponents()) {
            if (component instanceof JButton button) {
                labels.add(button.getText());
            }
        }
        return List.copyOf(labels);
    }

    /** Clicks the button with this label, exactly as a user would. */
    public void clickButton(String label) {
        findButton(label)
                .orElseThrow(() -> new IllegalArgumentException("no button labelled " + label))
                // Zero press time: the action still fires, but a harness does not pay the default
                // 68 ms animation pause for every key.
                .doClick(0);
    }

    /** Presses an engine command, as a keyboard shortcut does. */
    public void pressCommand(Command command) {
        press(command);
    }

    /** Selects a page through the mode selector. */
    public void selectMode(CalculatorMode mode) {
        modeSelector.setSelectedIndex(switch (mode) {
            case SCIENTIFIC -> 1;
            case PROGRAMMER -> 2;
            case STANDARD -> 0;
        });
    }

    // ---- Layout ---------------------------------------------------------------------------------

    private void installAccessibleNames() {
        displayField.setName("display");
        displayField.getAccessibleContext().setAccessibleName("Display");
        expressionLabel.setName("expression");
        expressionLabel.getAccessibleContext().setAccessibleName("Expression");
        modeSelector.setName("mode");
        modeSelector.getAccessibleContext().setAccessibleName("Mode");
        baseSelector.setName("base");
        baseSelector.getAccessibleContext().setAccessibleName("Base");
        widthSelector.setName("width");
        widthSelector.getAccessibleContext().setAccessibleName("Width");
        angleSelector.setName("angle");
        angleSelector.getAccessibleContext().setAccessibleName("Angle");
        keypad.getAccessibleContext().setAccessibleName("Keypad");
        sideButtons.getAccessibleContext().setAccessibleName("Memory");
    }

    private void installSelectors() {
        modeSelector.addActionListener(event -> {
            if (refreshing) {
                return;
            }
            surface.setMode(switch (modeSelector.getSelectedIndex()) {
                case 1 -> CalculatorMode.SCIENTIFIC;
                case 2 -> CalculatorMode.PROGRAMMER;
                default -> CalculatorMode.STANDARD;
            });
            rebuild();
            refresh();
        });
        baseSelector.addActionListener(event -> {
            if (refreshing) {
                return;
            }
            press(switch (baseSelector.getSelectedIndex()) {
                case 1 -> Command.RADIX_16;
                case 2 -> Command.RADIX_8;
                case 3 -> Command.RADIX_2;
                default -> Command.RADIX_10;
            });
        });
        widthSelector.addActionListener(event -> {
            if (refreshing) {
                return;
            }
            press(switch (widthSelector.getSelectedIndex()) {
                case 1 -> Command.WIDTH_DWORD;
                case 2 -> Command.WIDTH_WORD;
                case 3 -> Command.WIDTH_BYTE;
                default -> Command.WIDTH_QWORD;
            });
        });
        angleSelector.addActionListener(event -> {
            if (refreshing) {
                return;
            }
            press(switch (angleSelector.getSelectedIndex()) {
                case 1 -> Command.ANGLE_RADIANS;
                case 2 -> Command.ANGLE_GRADIANS;
                default -> Command.ANGLE_DEGREES;
            });
        });
    }

    private void rebuild() {
        keypad.removeAll();
        sideButtons.removeAll();

        for (AutomationSurface.Key key : AutomationSurface.sideKeys()) {
            addButton(sideButtons, key.label(), key.command());
        }
        for (AutomationSurface.Key key : surface.keypadKeys()) {
            if (key.isCommand()) {
                addButton(keypad, key.label(), key.command());
            } else {
                addActionButton(keypad, key.label());
            }
        }

        keypad.setVisible(true);
        keypad.revalidate();
        keypad.repaint();
        sideButtons.revalidate();
        sideButtons.repaint();
    }

    private void addButton(JPanel panel, String label, Command command) {
        final JButton button = new JButton(label);
        button.setFocusable(false);
        button.setName(label);
        button.getAccessibleContext().setAccessibleName(label);
        button.addActionListener(event -> press(command));
        panel.add(button);
    }

    /** Adds a button that runs an application action rather than an engine command. */
    private void addActionButton(JPanel panel, String label) {
        final JButton button = new JButton(label);
        button.setFocusable(false);
        button.setName(label);
        button.getAccessibleContext().setAccessibleName(label);
        button.addActionListener(event -> runAction(label));
        panel.add(button);
    }

    private void runAction(String label) {
        if ("HIST".equals(label)) {
            showHistory();
        }
    }

    private void installKeyboardShortcuts() {
        keypad.setFocusable(true);
        getRootPane().getInputMap(JPanel.WHEN_IN_FOCUSED_WINDOW).put(
                javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "equals");
        getRootPane().getInputMap(JPanel.WHEN_IN_FOCUSED_WINDOW).put(
                javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clear");
        getRootPane().getActionMap().put("equals", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                press(Command.EQUALS);
            }
        });
        getRootPane().getActionMap().put("clear", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                press(Command.CLEAR);
            }
        });
        final String digits = "0123456789";
        for (int i = 0; i < digits.length(); i++) {
            final int digit = i;
            final String key = "digit" + digit;
            getRootPane().getInputMap(JPanel.WHEN_IN_FOCUSED_WINDOW).put(
                    javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_0 + digit, 0), key);
            getRootPane().getActionMap().put(key, new javax.swing.AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    press(Command.values()[Command.ZERO.ordinal() + digit]);
                }
            });
        }
        final String[] operators = {"+", "-", "*", "/"};
        final Command[] commands = {Command.ADD, Command.SUBTRACT, Command.MULTIPLY, Command.DIVIDE};
        for (int i = 0; i < operators.length; i++) {
            final String key = "operator" + operators[i];
            getRootPane().getInputMap(JPanel.WHEN_IN_FOCUSED_WINDOW).put(
                    javax.swing.KeyStroke.getKeyStroke(operators[i].charAt(0)), key);
            final Command command = commands[i];
            getRootPane().getActionMap().put(key, new javax.swing.AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    press(command);
                }
            });
        }
        getRootPane().getInputMap(JPanel.WHEN_IN_FOCUSED_WINDOW).put(
                javax.swing.KeyStroke.getKeyStroke('.'), "decimal");
        getRootPane().getActionMap().put("decimal", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                press(Command.DECIMAL_POINT);
            }
        });
        getRootPane().getInputMap(JPanel.WHEN_IN_FOCUSED_WINDOW).put(
                javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "backspace");
        getRootPane().getActionMap().put("backspace", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                press(Command.BACKSPACE);
            }
        });
    }

    // ---- Actions --------------------------------------------------------------------------------

    private void press(Command command) {
        surface.pressCommand(command);
        refresh();
    }

    private void refresh() {
        refreshing = true;
        try {
            displayField.setText(surface.display());
            displayField.setForeground(surface.isError() ? Color.RED : Color.BLACK);
            expressionLabel.setText(surface.expression().isEmpty() ? " " : surface.expression());
            baseSelector.setSelectedIndex(switch (surface.radix()) {
                case 16 -> 1;
                case 8 -> 2;
                case 2 -> 3;
                default -> 0;
            });
            widthSelector.setSelectedIndex(surface.width().ordinal());
            angleSelector.setSelectedIndex(surface.angleType().ordinal());
        } finally {
            refreshing = false;
        }
    }

    /** Shows the history in a small window; used by the HISTORY key. */
    private void showHistory() {
        final StringBuilder text = new StringBuilder();
        for (HistoryEntry entry : surface.history()) {
            text.append(entry.expression()).append(' ').append(entry.result()).append('\n');
        }
        final JTextArea area = new JTextArea(text.toString(), 12, 32);
        area.setEditable(false);
        final JFrame frame = new JFrame("History");
        frame.add(new JScrollPane(area));
        frame.pack();
        frame.setLocationRelativeTo(this);
        frame.setVisible(true);
    }
}
