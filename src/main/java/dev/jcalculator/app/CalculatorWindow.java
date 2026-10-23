package dev.jcalculator.app;

import dev.jcalculator.engine.CalculatorMode;
import dev.jcalculator.engine.Command;
import dev.jcalculator.engine.HistoryEntry;
import dev.jcalculator.manager.CalculatorManager;

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
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * Swing front end for jCalculator.
 *
 * <p>The original application is a XAML page; this is the same calculator as a desktop window:
 * a mode selector, the expression line, the display, and the keys of the selected mode. It talks to
 * the same {@link CalculatorManager} the command-line interface and the tests use, so the two front
 * ends cannot drift apart.
 */
public final class CalculatorWindow extends JFrame {

    private final Font displayFont = new Font(Font.SANS_SERIF, Font.BOLD, 42);
    private final Font expressionFont = new Font(Font.SANS_SERIF, Font.PLAIN, 16);

    private final CalculatorManager manager = new CalculatorManager();
    private final JLabel expressionLabel = new JLabel(" ", SwingConstants.RIGHT);
    private final JTextField displayField = new JTextField("0");
    private final JComboBox<String> modeSelector = new JComboBox<>(new String[]{"Standard", "Scientific", "Programmer"});
    private final JComboBox<String> baseSelector = new JComboBox<>(new String[]{"DEC", "HEX", "OCT", "BIN"});
    private final JComboBox<String> widthSelector = new JComboBox<>(new String[]{"QWORD", "DWORD", "WORD", "BYTE"});
    private final JComboBox<String> angleSelector = new JComboBox<>(new String[]{"DEG", "RAD", "GRAD"});
    private final JPanel keypad = new JPanel(new GridLayout(0, 5, 6, 6));
    private final JPanel sideButtons = new JPanel(new GridLayout(0, 1, 6, 6));

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

        modeSelector.addActionListener(event -> {
            manager.setMode(switch (modeSelector.getSelectedIndex()) {
                case 1 -> CalculatorMode.SCIENTIFIC;
                case 2 -> CalculatorMode.PROGRAMMER;
                default -> CalculatorMode.STANDARD;
            });
            rebuild();
            refresh();
        });
        baseSelector.addActionListener(event -> {
            press(switch (baseSelector.getSelectedIndex()) {
                case 1 -> Command.RADIX_16;
                case 2 -> Command.RADIX_8;
                case 3 -> Command.RADIX_2;
                default -> Command.RADIX_10;
            });
        });
        widthSelector.addActionListener(event -> {
            press(switch (widthSelector.getSelectedIndex()) {
                case 1 -> Command.WIDTH_DWORD;
                case 2 -> Command.WIDTH_WORD;
                case 3 -> Command.WIDTH_BYTE;
                default -> Command.WIDTH_QWORD;
            });
        });
        angleSelector.addActionListener(event -> {
            press(switch (angleSelector.getSelectedIndex()) {
                case 1 -> Command.ANGLE_RADIANS;
                case 2 -> Command.ANGLE_GRADIANS;
                default -> Command.ANGLE_DEGREES;
            });
        });

        installKeyboardShortcuts();
        rebuild();
        refresh();
        pack();
        setLocationRelativeTo(null);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // The cross-platform look and feel is the fallback.
            }
            new CalculatorWindow().setVisible(true);
        });
    }

    // ---- Layout ---------------------------------------------------------------------------------

    private void rebuild() {
        keypad.removeAll();
        sideButtons.removeAll();

        addSide("MC", Command.MEMORY_CLEAR);
        addSide("MR", Command.MEMORY_RECALL);
        addSide("M+", Command.MEMORY_ADD);
        addSide("M-", Command.MEMORY_SUBTRACT);
        addSide("MS", Command.MEMORY_STORE);

        if (manager.mode() == CalculatorMode.PROGRAMMER) {
            add("A", Command.A);
            add("B", Command.B);
            add("C", Command.C);
            add("D", Command.D);
            add("E", Command.E);
            add("F", Command.F);
            add("7", Command.SEVEN);
            add("8", Command.EIGHT);
            add("9", Command.NINE);
            add("÷", Command.DIVIDE);
            add("AND", Command.AND);
            add("4", Command.FOUR);
            add("5", Command.FIVE);
            add("6", Command.SIX);
            add("×", Command.MULTIPLY);
            add("OR", Command.OR);
            add("1", Command.ONE);
            add("2", Command.TWO);
            add("3", Command.THREE);
            add("-", Command.SUBTRACT);
            add("XOR", Command.XOR);
            add("0", Command.ZERO);
            add("±", Command.NEGATE);
            add("=", Command.EQUALS);
            add("+", Command.ADD);
            add("NOT", Command.COMPLEMENT);
            add("Lsh", Command.LEFT_SHIFT);
            add("Rsh", Command.RIGHT_SHIFT);
            add("ROL", Command.ROTATE_LEFT);
            add("ROR", Command.ROTATE_RIGHT);
            add("C", Command.CLEAR_ENTRY);
            add("⌫", Command.BACKSPACE);
            add("(", Command.OPEN_PAREN);
            add(")", Command.CLOSE_PAREN);
            add("MOD", Command.MOD);
            addAction("HIST", this::showHistory);
            add("CLR", Command.CLEAR);
        } else if (manager.mode() == CalculatorMode.SCIENTIFIC) {
            add("sin", Command.SIN);
            add("cos", Command.COS);
            add("tan", Command.TAN);
            add("ln", Command.LN);
            add("log", Command.LOG);
            add("x²", Command.SQR);
            add("√", Command.SQRT);
            add("xʸ", Command.POWER);
            add("π", Command.PI);
            add("e", Command.EULER);
            add("n!", Command.FACTORIAL);
            add("1/x", Command.RECIPROCAL);
            add("(", Command.OPEN_PAREN);
            add(")", Command.CLOSE_PAREN);
            add("%", Command.PERCENT);
            add("7", Command.SEVEN);
            add("8", Command.EIGHT);
            add("9", Command.NINE);
            add("÷", Command.DIVIDE);
            add("C", Command.CLEAR);
            add("4", Command.FOUR);
            add("5", Command.FIVE);
            add("6", Command.SIX);
            add("×", Command.MULTIPLY);
            add("CE", Command.CLEAR_ENTRY);
            add("1", Command.ONE);
            add("2", Command.TWO);
            add("3", Command.THREE);
            add("-", Command.SUBTRACT);
            add("⌫", Command.BACKSPACE);
            add("0", Command.ZERO);
            add("±", Command.NEGATE);
            add(".", Command.DECIMAL_POINT);
            add("+", Command.ADD);
            add("=", Command.EQUALS);
        } else {
            add("%", Command.PERCENT);
            add("√", Command.SQRT);
            add("x²", Command.SQR);
            add("1/x", Command.RECIPROCAL);
            add("CE", Command.CLEAR_ENTRY);
            add("7", Command.SEVEN);
            add("8", Command.EIGHT);
            add("9", Command.NINE);
            add("÷", Command.DIVIDE);
            add("C", Command.CLEAR);
            add("4", Command.FOUR);
            add("5", Command.FIVE);
            add("6", Command.SIX);
            add("×", Command.MULTIPLY);
            add("⌫", Command.BACKSPACE);
            add("1", Command.ONE);
            add("2", Command.TWO);
            add("3", Command.THREE);
            add("-", Command.SUBTRACT);
            add("±", Command.NEGATE);
            add("0", Command.ZERO);
            add(".", Command.DECIMAL_POINT);
            add("=", Command.EQUALS);
            add("+", Command.ADD);
            addAction("HIST", this::showHistory);
        }

        keypad.setVisible(true);
        keypad.revalidate();
        keypad.repaint();
        sideButtons.revalidate();
        sideButtons.repaint();
    }

    private void add(String label, Command command) {
        final JButton button = new JButton(label);
        button.setFocusable(false);
        button.addActionListener(event -> press(command));
        keypad.add(button);
    }

    private void addSide(String label, Command command) {
        final JButton button = new JButton(label);
        button.setFocusable(false);
        button.addActionListener(event -> press(command));
        sideButtons.add(button);
    }

    /** Adds a button that runs an application action rather than an engine command. */
    private void addAction(String label, Runnable action) {
        final JButton button = new JButton(label);
        button.setFocusable(false);
        button.addActionListener(event -> action.run());
        keypad.add(button);
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
        manager.sendCommand(command);
        refresh();
    }

    private void refresh() {
        displayField.setText(manager.primaryDisplay());
        displayField.setForeground(manager.isError() ? Color.RED : Color.BLACK);
        expressionLabel.setText(manager.expression().isEmpty() ? " " : manager.expression());
        baseSelector.setSelectedIndex(switch (manager.engine().radix()) {
            case 16 -> 1;
            case 8 -> 2;
            case 2 -> 3;
            default -> 0;
        });
        widthSelector.setSelectedIndex(manager.engine().width().ordinal());
        angleSelector.setSelectedIndex(manager.engine().angleType().ordinal());
    }

    /** Shows the history in a small window; used by the HISTORY key. */
    private void showHistory() {
        final StringBuilder text = new StringBuilder();
        for (HistoryEntry entry : manager.history()) {
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
