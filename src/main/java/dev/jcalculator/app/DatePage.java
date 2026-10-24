package dev.jcalculator.app;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * The date calculation page: the difference between two dates, and adding or subtracting a duration.
 *
 * <p>The original's {@code DateCalculator.xaml} offers the same two modes over the same calendar
 * arithmetic; this page is a thin shell over {@link dev.jcalculator.harness.CalculatorSession}, so
 * the results match the JSON protocol's {@code date_diff}, {@code date_add} and
 * {@code date_subtract}.
 */
final class DatePage extends JPanel {

    private final transient AutomationSurface surface;
    private final JTextField fromField = new JTextField("2024-01-01", 12);
    private final JTextField toField = new JTextField("2024-12-31", 12);
    private final JLabel differenceLabel = new JLabel(" ");
    private final JTextField yearsField = new JTextField("0", 4);
    private final JTextField monthsField = new JTextField("0", 4);
    private final JTextField daysField = new JTextField("0", 4);
    private final JCheckBox subtractBox = new JCheckBox("Subtract");
    private final JLabel durationLabel = new JLabel(" ");
    private final JLabel statusLabel = new JLabel(" ");

    DatePage(AutomationSurface surface) {
        this.surface = surface;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        final JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        final JLabel title = new JLabel("Date calculation");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        title.setAlignmentX(LEFT_ALIGNMENT);
        column.add(title);
        column.add(Box.createVerticalStrut(12));

        final JPanel difference = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        difference.setAlignmentX(LEFT_ALIGNMENT);
        difference.add(new JLabel("From"));
        difference.add(fromField);
        difference.add(new JLabel("To"));
        difference.add(toField);
        final JButton diffButton = new JButton("Difference");
        diffButton.setName("date-difference");
        diffButton.getAccessibleContext().setAccessibleName("Difference");
        diffButton.addActionListener(event -> showDifference());
        difference.add(diffButton);
        column.add(difference);

        differenceLabel.setFont(differenceLabel.getFont().deriveFont(Font.BOLD, 16f));
        differenceLabel.setAlignmentX(LEFT_ALIGNMENT);
        column.add(differenceLabel);
        column.add(Box.createVerticalStrut(16));

        final JPanel duration = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        duration.setAlignmentX(LEFT_ALIGNMENT);
        duration.add(new JLabel("Years"));
        duration.add(yearsField);
        duration.add(new JLabel("Months"));
        duration.add(monthsField);
        duration.add(new JLabel("Days"));
        duration.add(daysField);
        duration.add(subtractBox);
        final JButton applyButton = new JButton("Add to date");
        applyButton.setName("date-add");
        applyButton.getAccessibleContext().setAccessibleName("Add to date");
        applyButton.addActionListener(event -> addDuration());
        duration.add(applyButton);
        column.add(duration);

        durationLabel.setFont(durationLabel.getFont().deriveFont(Font.BOLD, 16f));
        durationLabel.setAlignmentX(LEFT_ALIGNMENT);
        column.add(durationLabel);
        column.add(Box.createVerticalStrut(12));

        statusLabel.setAlignmentX(LEFT_ALIGNMENT);
        column.add(statusLabel);
        column.add(Box.createVerticalGlue());

        add(column, BorderLayout.NORTH);
        for (JTextField field : new JTextField[]{fromField, toField, yearsField, monthsField, daysField}) {
            field.addActionListener(event -> showDifference());
        }
    }

    /** Reads the two dates and shows their calendar difference. */
    void showDifference() {
        statusLabel.setText(" ");
        try {
            final LocalDate from = LocalDate.parse(fromField.getText().trim());
            final LocalDate to = LocalDate.parse(toField.getText().trim());
            final String difference = surface.session().dateDifference(from, to);
            differenceLabel.setText(difference == null ? "Calculation failed" : difference);
        } catch (DateTimeParseException failure) {
            differenceLabel.setText(" ");
            statusLabel.setText("Dates are written as YYYY-MM-DD");
        }
    }

    /** Applies the duration to the from date. */
    void addDuration() {
        statusLabel.setText(" ");
        try {
            final LocalDate start = LocalDate.parse(fromField.getText().trim());
            final int years = Integer.parseInt(yearsField.getText().trim());
            final int months = Integer.parseInt(monthsField.getText().trim());
            final int days = Integer.parseInt(daysField.getText().trim());
            final LocalDate result = surface.session().dateAdd(start, years, months, days,
                    subtractBox.isSelected());
            durationLabel.setText(result == null ? "Date out of bounds" : result.toString());
        } catch (DateTimeParseException failure) {
            statusLabel.setText("Dates are written as YYYY-MM-DD");
        } catch (NumberFormatException failure) {
            statusLabel.setText("A duration is a whole number of years, months and days");
        }
    }

    String differenceText() {
        return differenceLabel.getText();
    }

    String durationText() {
        return durationLabel.getText();
    }

    String statusText() {
        return statusLabel.getText().trim();
    }
}
