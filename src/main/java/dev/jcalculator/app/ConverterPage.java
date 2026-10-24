package dev.jcalculator.app;

import dev.jcalculator.convert.Unit;
import dev.jcalculator.convert.UnitCategory;
import dev.jcalculator.convert.UnitConverter;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.math.BigDecimal;
import java.util.List;

/**
 * The unit converter page: a category, two units and a value, with the amount expressed in the other
 * units of the category underneath.
 *
 * <p>The original's {@code UnitConverter.xaml} is the same page: the value, the two units and a swap
 * button, plus the supplementary results it shows under the answer. The conversion data and the
 * supplementary rule come from {@link UnitConverter}, which is the same model the JSON protocol uses.
 */
final class ConverterPage extends JPanel {

    private final transient UnitConverter converter = new UnitConverter();
    private final JComboBox<UnitCategory> categorySelector = new JComboBox<>(UnitCategory.values());
    private final JComboBox<Unit> fromSelector = new JComboBox<>();
    private final JComboBox<Unit> toSelector = new JComboBox<>();
    private final JTextField valueField = new JTextField("1", 12);
    private final JLabel resultLabel = new JLabel(" ");
    private final JLabel statusLabel = new JLabel(" ");
    private final DefaultListModel<String> supplementaryModel = new DefaultListModel<>();
    private final JList<String> supplementaryList = new JList<>(supplementaryModel);
    private boolean filling;

    ConverterPage() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        final JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        final JLabel title = new JLabel("Unit converter");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        title.setAlignmentX(LEFT_ALIGNMENT);
        column.add(title);
        column.add(Box.createVerticalStrut(12));

        final JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.add(categorySelector);
        row.add(fromSelector);
        final JButton swap = new JButton("Swap");
        swap.setName("swap-units");
        swap.getAccessibleContext().setAccessibleName("Swap units");
        swap.addActionListener(event -> swapUnits());
        row.add(swap);
        row.add(toSelector);
        row.add(valueField);
        final JButton convert = new JButton("Convert");
        convert.setName("convert");
        convert.getAccessibleContext().setAccessibleName("Convert");
        convert.addActionListener(event -> convertNow());
        row.add(convert);
        column.add(row);

        resultLabel.setFont(resultLabel.getFont().deriveFont(Font.BOLD, 20f));
        resultLabel.setAlignmentX(LEFT_ALIGNMENT);
        column.add(resultLabel);
        statusLabel.setAlignmentX(LEFT_ALIGNMENT);
        column.add(statusLabel);
        column.add(Box.createVerticalStrut(10));

        final JLabel supplementaryTitle = new JLabel("The same amount in other units");
        supplementaryTitle.setAlignmentX(LEFT_ALIGNMENT);
        column.add(supplementaryTitle);
        supplementaryList.setVisibleRowCount(8);
        supplementaryList.getAccessibleContext().setAccessibleName("Other units");
        column.add(new JScrollPane(supplementaryList));

        add(column, BorderLayout.NORTH);

        categorySelector.addActionListener(event -> {
            if (!filling) {
                fillUnits();
                convertNow();
            }
        });
        fromSelector.addActionListener(event -> {
            if (!filling) {
                convertNow();
            }
        });
        toSelector.addActionListener(event -> {
            if (!filling) {
                convertNow();
            }
        });
        valueField.addActionListener(event -> convertNow());

        fillUnits();
        convertNow();
    }

    private void fillUnits() {
        filling = true;
        try {
            final UnitCategory category = (UnitCategory) categorySelector.getSelectedItem();
            final List<Unit> units = converter.units(category == null ? UnitCategory.LENGTH : category);
            fromSelector.removeAllItems();
            toSelector.removeAllItems();
            for (Unit unit : units) {
                fromSelector.addItem(unit);
                toSelector.addItem(unit);
            }
            if (units.size() > 1) {
                toSelector.setSelectedIndex(1);
            }
        } finally {
            filling = false;
        }
    }

    private void swapUnits() {
        final int from = fromSelector.getSelectedIndex();
        fromSelector.setSelectedIndex(toSelector.getSelectedIndex());
        toSelector.setSelectedIndex(from);
        convertNow();
    }

    /** Converts the value and refreshes the supplementary list. */
    void convertNow() {
        statusLabel.setText(" ");
        supplementaryModel.clear();
        final Unit from = (Unit) fromSelector.getSelectedItem();
        final Unit to = (Unit) toSelector.getSelectedItem();
        if (from == null || to == null) {
            return;
        }
        final String text = valueField.getText().trim();
        final BigDecimal value;
        try {
            value = new BigDecimal(text.isEmpty() ? "0" : text);
        } catch (NumberFormatException failure) {
            resultLabel.setText(" ");
            statusLabel.setText("Not a number: " + text);
            return;
        }
        try {
            resultLabel.setText(converter.convertAndFormat(from, to, value, text));
        } catch (RuntimeException failure) {
            resultLabel.setText(" ");
            statusLabel.setText(failure.getMessage() == null ? "Conversion failed" : failure.getMessage());
            return;
        }
        for (UnitConverter.SupplementaryValue supplementary : converter.supplementaryValues(from, to, value)) {
            supplementaryModel.addElement(supplementary.unit().name() + "  " + supplementary.text());
        }
    }

    String resultText() {
        return resultLabel.getText();
    }

    String statusText() {
        return statusLabel.getText().trim();
    }

    int supplementaryCount() {
        return supplementaryModel.getSize();
    }

    String supplementaryAt(int index) {
        return supplementaryModel.getElementAt(index);
    }

    /** Selects a category and two units by name, as the page's menus do. */
    void select(String category, String from, String to) {
        categorySelector.setSelectedItem(UnitCategory.valueOf(category.toUpperCase(java.util.Locale.ROOT)));
        fillUnits();
        fromSelector.setSelectedItem(converter.findUnit((UnitCategory) categorySelector.getSelectedItem(), from));
        toSelector.setSelectedItem(converter.findUnit((UnitCategory) categorySelector.getSelectedItem(), to));
        convertNow();
    }

    void setValue(String text) {
        valueField.setText(text);
        convertNow();
    }
}
