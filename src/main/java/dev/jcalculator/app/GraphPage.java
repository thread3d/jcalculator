package dev.jcalculator.app;

import dev.jcalculator.graph.FunctionPlotter;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * The graphing page: a function of x, a range, and the curve it draws.
 *
 * <p>The original's graphing calculator is a renderer over an equation model. This page takes the
 * port's own route — {@link FunctionPlotter} samples the function through the calculator engine — and
 * draws the result, with a trace readout under the canvas and zoom buttons that narrow or widen the
 * range around its centre.
 */
final class GraphPage extends JPanel {

    private static final int SAMPLES = 401;

    private final JTextField expressionField = new JTextField("x^2", 14);
    private final JTextField minXField = new JTextField("-10", 6);
    private final JTextField maxXField = new JTextField("10", 6);
    private final JLabel traceLabel = new JLabel(" ");
    private final JLabel statusLabel = new JLabel(" ");
    private final CurveCanvas canvas = new CurveCanvas();

    GraphPage() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        final JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        final JLabel title = new JLabel("Graphing");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        title.setAlignmentX(LEFT_ALIGNMENT);
        column.add(title);
        column.add(Box.createVerticalStrut(10));

        final JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.add(new JLabel("y ="));
        expressionField.setName("graph-expression");
        expressionField.getAccessibleContext().setAccessibleName("Function of x");
        row.add(expressionField);
        row.add(new JLabel("from x"));
        row.add(minXField);
        row.add(new JLabel("to"));
        row.add(maxXField);

        final JButton plot = new JButton("Plot");
        plot.setName("graph-plot");
        plot.getAccessibleContext().setAccessibleName("Plot");
        plot.addActionListener(event -> plot());
        row.add(plot);

        final JButton zoomIn = new JButton("Zoom in");
        zoomIn.setName("graph-zoom-in");
        zoomIn.addActionListener(event -> zoom(0.5));
        row.add(zoomIn);

        final JButton zoomOut = new JButton("Zoom out");
        zoomOut.setName("graph-zoom-out");
        zoomOut.addActionListener(event -> zoom(2.0));
        row.add(zoomOut);
        column.add(row);

        statusLabel.setAlignmentX(LEFT_ALIGNMENT);
        column.add(statusLabel);
        column.add(Box.createVerticalStrut(8));

        add(column, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);

        traceLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));
        add(traceLabel, BorderLayout.SOUTH);

        expressionField.addActionListener(event -> plot());
        minXField.addActionListener(event -> plot());
        maxXField.addActionListener(event -> plot());
        canvas.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                trace(event.getX());
            }
        });
        plot();
    }

    /** Samples the expression over the given range and draws it. */
    void plot() {
        statusLabel.setText(" ");
        try {
            final double minX = parse(minXField.getText(), -10);
            final double maxX = parse(maxXField.getText(), 10);
            final FunctionPlotter.Plot plot = FunctionPlotter.plot(expressionField.getText(), minX, maxX, SAMPLES);
            canvas.show(plot);
            if (plot.samples().isEmpty()) {
                statusLabel.setText("Nothing to draw in this range");
            }
        } catch (IllegalArgumentException failure) {
            canvas.show(null);
            statusLabel.setText(failure.getMessage());
        }
    }

    private static double parse(String text, double fallback) {
        final String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return fallback;
        }
        try {
            final double value = Double.parseDouble(trimmed);
            if (!Double.isFinite(value)) {
                throw new NumberFormatException("not finite");
            }
            return value;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("the x range must be two numbers", failure);
        }
    }

    /** Narrows or widens the range around its centre. */
    void zoom(double factor) {
        try {
            final double minX = parse(minXField.getText(), -10);
            final double maxX = parse(maxXField.getText(), 10);
            final double centre = (minX + maxX) / 2;
            final double half = (maxX - minX) / 2 * factor;
            minXField.setText(format(centre - half));
            maxXField.setText(format(centre + half));
            plot();
        } catch (IllegalArgumentException failure) {
            statusLabel.setText(failure.getMessage());
        }
    }

    private static String format(double value) {
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    /** Reads the value nearest the pointer, as the trace readout does. */
    void trace(int pixelX) {
        final FunctionPlotter.Plot plot = canvas.plot();
        if (plot == null || plot.samples().isEmpty()) {
            traceLabel.setText(" ");
            return;
        }
        final double x = canvas.xAt(pixelX);
        FunctionPlotter.Sample nearest = plot.samples().get(0);
        for (FunctionPlotter.Sample sample : plot.samples()) {
            if (Math.abs(sample.x() - x) < Math.abs(nearest.x() - x)) {
                nearest = sample;
            }
        }
        traceLabel.setText("x = " + format(nearest.x()) + "   y = " + format(nearest.y()));
    }

    String traceText() {
        return traceLabel.getText().trim();
    }

    String statusText() {
        return statusLabel.getText().trim();
    }

    void setExpression(String expression) {
        expressionField.setText(expression);
        plot();
    }

    void setRange(String minX, String maxX) {
        minXField.setText(minX);
        maxXField.setText(maxX);
        plot();
    }

    FunctionPlotter.Plot plotResult() {
        return canvas.plot();
    }

    // ---- Drawing ---------------------------------------------------------------------------------

    /** Draws the sampled curve with its axes. */
    private static final class CurveCanvas extends JPanel {

        private static final long serialVersionUID = 1L;
        private static final int PADDING = 32;

        /** A rounded pixel coordinate, clamped so a wild value cannot wrap the drawing. */
        private static int pixel(double value) {
            final long clamped = Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, Math.round(value)));
            return Math.toIntExact(clamped);
        }

        private transient FunctionPlotter.Plot plot;

        CurveCanvas() {
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createLineBorder(new Color(0xCC, 0xCC, 0xCC)));
            getAccessibleContext().setAccessibleName("Graph");
        }

        void show(FunctionPlotter.Plot newPlot) {
            this.plot = newPlot;
            repaint();
        }

        FunctionPlotter.Plot plot() {
            return plot;
        }

        /** The x value under a horizontal pixel. */
        double xAt(int pixelX) {
            if (plot == null) {
                return 0;
            }
            final double width = Math.max(1, getWidth() - 2.0 * PADDING);
            return plot.minX() + (pixelX - PADDING) / width * (plot.maxX() - plot.minX());
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (plot == null || plot.samples().isEmpty()) {
                return;
            }
            final Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                final int width = getWidth() - 2 * PADDING;
                final int height = getHeight() - 2 * PADDING;
                if (width <= 0 || height <= 0) {
                    return;
                }
                final double minY = plot.minY();
                final double spanY = plot.height();
                g.setColor(new Color(0xDD, 0xDD, 0xDD));
                g.drawRect(PADDING, PADDING, width, height);
                final int zeroY = pixel(PADDING + height - (0 - minY) / spanY * height);
                if (zeroY >= PADDING && zeroY <= PADDING + height) {
                    g.drawLine(PADDING, zeroY, PADDING + width, zeroY);
                }
                g.setColor(new Color(0x22, 0x44, 0xAA));
                g.setStroke(new BasicStroke(2f));
                int previousX = -1;
                int previousY = -1;
                for (FunctionPlotter.Sample sample : plot.samples()) {
                    final int px = PADDING
                            + pixel((sample.x() - plot.minX()) / (plot.maxX() - plot.minX()) * width);
                    final int py = PADDING + pixel(height - (sample.y() - minY) / spanY * height);
                    if (previousX >= 0) {
                        g.drawLine(previousX, previousY, px, py);
                    }
                    previousX = px;
                    previousY = py;
                }
            } finally {
                g.dispose();
            }
        }

        private List<FunctionPlotter.Sample> samples() {
            return plot == null ? List.of() : plot.samples();
        }
    }
}
