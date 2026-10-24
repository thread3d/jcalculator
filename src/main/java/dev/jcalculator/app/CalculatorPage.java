package dev.jcalculator.app;

import dev.jcalculator.engine.CalculatorMode;
import dev.jcalculator.engine.Command;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The calculator page: the mode selector, the expression line, the display, the memory column, the
 * keypad of the selected page and — on the programmer page — the bit panel.
 *
 * <p>The original splits this across {@code Calculator.xaml} and its operator panels. It renders an
 * {@link AutomationSurface}, so the window and every other front end keep one state machine, and the
 * programmer page exposes the same clickable bits the original's bit panel does.
 */
final class CalculatorPage extends JPanel {

    private final Font displayFont = new Font(Font.SANS_SERIF, Font.BOLD, 42);
    private final Font expressionFont = new Font(Font.SANS_SERIF, Font.PLAIN, 16);

    private final transient AutomationSurface surface;
    private final transient Runnable onHistory;
    private final JLabel expressionLabel = new JLabel(" ", SwingConstants.RIGHT);
    private final JTextField displayField = new JTextField("0");
    private final JComboBox<String> modeSelector =
            new JComboBox<>(new String[]{"Standard", "Scientific", "Programmer"});
    private final JComboBox<String> baseSelector = new JComboBox<>(new String[]{"DEC", "HEX", "OCT", "BIN"});
    private final JComboBox<String> widthSelector = new JComboBox<>(new String[]{"QWORD", "DWORD", "WORD", "BYTE"});
    private final JComboBox<String> angleSelector = new JComboBox<>(new String[]{"DEG", "RAD", "GRAD"});
    private final JPanel keypad = new JPanel(new GridLayout(0, 5, 6, 6));
    private final JPanel sideButtons = new JPanel(new GridLayout(0, 1, 6, 6));
    private final JPanel bitPanel = new JPanel(new GridLayout(2, 32, 1, 1));
    private final List<JButton> bitButtons = new ArrayList<>();

    /**
     * True while {@link #refresh()} is writing the selectors, so their listeners can ignore the
     * echo. {@code JComboBox.setSelectedIndex} fires even when the index does not change, and an
     * echoed RADIX or ANGLE command would otherwise abandon the number being typed.
     */
    private boolean refreshing;

    CalculatorPage(AutomationSurface surface, Runnable onHistory) {
        this.surface = surface;
        this.onHistory = onHistory;
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
        body.add(bitPanel, BorderLayout.NORTH);
        body.add(sideButtons, BorderLayout.WEST);
        body.add(keypad, BorderLayout.CENTER);
        add(body, BorderLayout.SOUTH);

        buildBitPanel();
        installAccessibleNames();
        installSelectors();
        rebuild();
        refresh();
    }

    // ---- Automation accessors ---------------------------------------------------------------------

    String displayText() {
        return displayField.getText();
    }

    String expressionText() {
        return expressionLabel.getText().trim();
    }

    String displayAccessibleName() {
        return displayField.getAccessibleContext().getAccessibleName();
    }

    Optional<JButton> findButton(String label) {
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

    List<String> buttonLabels() {
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

    void clickButton(String label) {
        findButton(label)
                .orElseThrow(() -> new IllegalArgumentException("no button labelled " + label))
                // Zero press time: the action still fires, but a harness does not pay the default
                // 68 ms animation pause for every key.
                .doClick(0);
    }

    void selectMode(CalculatorMode mode) {
        modeSelector.setSelectedIndex(switch (mode) {
            case SCIENTIFIC -> 1;
            case PROGRAMMER -> 2;
            case STANDARD -> 0;
        });
    }

    /** Flips one bit from the bit panel and repaints. */
    void flipBit(int index) {
        surface.toggleBit(index);
        refresh();
    }

    /**
     * The bit button for a bit position.
     *
     * <p>The panel shows the most significant bit first, so the button for bit {@code n} sits at
     * {@code 63 - n} in the list.
     */
    Optional<JButton> findBitButton(int bit) {
        final int position = bitButtons.size() - 1 - bit;
        return bit >= 0 && position < bitButtons.size() ? Optional.of(bitButtons.get(position))
                : Optional.empty();
    }

    /** The bit button at a position, for an automation surface or a test. */
    void clickBit(int bit) {
        findBitButton(bit).orElseThrow(() -> new IllegalArgumentException("no bit " + bit)).doClick(0);
    }

    // ---- Layout ---------------------------------------------------------------------------------

    private void buildBitPanel() {
        bitPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        bitPanel.setVisible(false);
        bitPanel.getAccessibleContext().setAccessibleName("Bits of the word");
        for (int bit = 63; bit >= 0; bit--) {
            final int index = bit;
            final JButton button = new JButton(Integer.toString(bit));
            button.setName("bit" + bit);
            button.getAccessibleContext().setAccessibleName("Bit " + bit);
            button.setFocusable(false);
            button.setFont(button.getFont().deriveFont(9f));
            button.setMargin(new Insets(0, 0, 0, 0));
            button.addActionListener(event -> flipBit(index));
            bitButtons.add(button);
            bitPanel.add(button);
        }
    }

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
        button.addActionListener(event -> onHistory.run());
        panel.add(button);
    }

    // ---- State ----------------------------------------------------------------------------------

    /** Feeds one command through the surface and repaints. */
    void press(Command command) {
        surface.pressCommand(command);
        refresh();
    }

    /** Repaints everything from the surface's state. */
    void refresh() {
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
        final boolean programmer = surface.mode() == CalculatorMode.PROGRAMMER;
        bitPanel.setVisible(programmer);
        if (programmer) {
            refreshBits();
        }
    }

    private void refreshBits() {
        final BigInteger bits = surface.session().manager().engine().currentBits();
        for (int position = 0; position < bitButtons.size(); position++) {
            final JButton button = bitButtons.get(position);
            final boolean set = bits.testBit(63 - position);
            button.setSelected(set);
            button.setBackground(set ? new Color(0x33, 0x66, 0xCC) : null);
            button.setForeground(set ? Color.WHITE : null);
        }
    }
}
