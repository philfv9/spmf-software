package ca.pfv.spmf.gui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.border.TitledBorder;

/*
 * Copyright (c) 2008-2023 Philippe Fournier-Viger
 *
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 */
/**
 * This class is for monitoring the memory usage using a JFrame window.
 * It displays a real-time chart of JVM memory consumption with configurable
 * refresh rate, grid/fill toggles, pause/resume, and garbage collection trigger.
 *
 * @author Philippe Fournier-Viger, 2023
 */
public class MemoryViewer extends JPanel {

    /** Generated serial UID */
    private static final long serialVersionUID = 278261238139167055L;

    /** The singleton JFrame instance */
    private static JFrame frame = null;

    /** Default refresh interval in milliseconds */
    private static final int DEFAULT_REFRESH_RATE = 1000;

    /** Number of data points retained and displayed */
    private static final int VALUE_COUNT = 100;

    /** Chart margin constants (pixels) */
    private static final int MARGIN_LEFT   = 60;
    private static final int MARGIN_RIGHT  = 20;
    private static final int MARGIN_TOP    = 30;
    private static final int MARGIN_BOTTOM = 30;

    // -----------------------------------------------------------------------
    // Colors — chart uses white background with custom accent colors
    // -----------------------------------------------------------------------

    /** Background color for the chart area */
    private static final Color CHART_BACKGROUND = Color.WHITE;

    /** Accent color for the memory line */
    private static final Color LINE_COLOR = new Color(24, 103, 192);

    /** Semi-transparent fill beneath the memory line */
    private static final Color FILL_COLOR = new Color(24, 103, 192, 45);

    /** Color used for grid lines */
    private static final Color GRID_COLOR = new Color(210, 210, 210);

    /** Reusable stroke for the memory line */
    private static final BasicStroke LINE_STROKE =
            new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    /** Reusable stroke for regular lines */
    private static final BasicStroke NORMAL_STROKE = new BasicStroke(1.0f);

    // -----------------------------------------------------------------------
    // Data
    // -----------------------------------------------------------------------

    /** Circular buffer storing sampled used-memory values (MB) */
    private final int[] memoryValues = new int[VALUE_COUNT];

    /**
     * Write pointer into {@link #memoryValues}.
     * The oldest sample is always at {@code position} (next to be overwritten).
     */
    private int position = 0;

    /**
     * Y-axis ceiling (MB). Updated each sample to be at least the current
     * JVM committed heap so the chart never clips.
     */
    private int maxMemoryYAxis = 1; // avoid division by zero before first sample

    // -----------------------------------------------------------------------
    // Reusable coordinate arrays to avoid allocation on every paint
    // -----------------------------------------------------------------------

    /** Reusable X-coordinate array for polygon (VALUE_COUNT + 2 points) */
    private final int[] xPoints = new int[VALUE_COUNT + 2];

    /** Reusable Y-coordinate array for polygon (VALUE_COUNT + 2 points) */
    private final int[] yPoints = new int[VALUE_COUNT + 2];

    // -----------------------------------------------------------------------
    // Reusable objects to minimize allocations
    // -----------------------------------------------------------------------

    /** Reusable StringBuilder for label formatting */
    private final StringBuilder labelBuilder = new StringBuilder(20);

    // -----------------------------------------------------------------------
    // UI components
    // -----------------------------------------------------------------------

    /** The inner panel that owns the paintComponent override */
    private ChartPanel chartPanel;

    /** Checkbox: show/hide horizontal and vertical grid lines */
    private JCheckBox gridCheckBox;

    /** Checkbox: fill the area under the memory curve */
    private JCheckBox fillCheckBox;

    /** Toggles the sampling timer between running and stopped */
    private JButton pauseButton;

    /** Triggers a JVM garbage-collection pass */
    private JButton gcButton;

    /** Displays the most-recently sampled used memory */
    private JLabel currentMemoryLabel;

    /** Displays the peak used-memory value in the visible window */
    private JLabel peakMemoryLabel;

    /** Displays the rolling average used-memory value */
    private JLabel avgMemoryLabel;

    /** Displays the total committed JVM heap */
    private JLabel totalMemoryLabel;

    /** Sampling timer — fires every {@code delay} milliseconds */
    private Timer timer;

    /** True while sampling is suspended by the user */
    private boolean isPaused = false;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    /**
     * Constructs the MemoryViewer panel and starts the sampling timer.
     * All child components are created here; no lazy initialisation is needed.
     */
    public MemoryViewer() {

        setLayout(new BorderLayout(4, 4));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // --- Statistics bar (top) ------------------------------------------
        currentMemoryLabel = new JLabel("Current: — MB");
        peakMemoryLabel    = new JLabel("Peak: — MB");
        avgMemoryLabel     = new JLabel("Avg: — MB");
        totalMemoryLabel   = new JLabel("Total JVM: — MB");
        add(buildStatsPanel(), BorderLayout.NORTH);

        // --- Chart (centre) ------------------------------------------------
        chartPanel = new ChartPanel();
        add(chartPanel, BorderLayout.CENTER);

        // --- Controls (bottom) ---------------------------------------------
        gridCheckBox = new JCheckBox("Grid lines", true);
        fillCheckBox = new JCheckBox("Fill area",  true);
        pauseButton  = new JButton("Pause");
        gcButton     = new JButton("Run GC");
        gcButton.setToolTipText("Request garbage collection");
        add(buildControlPanel(), BorderLayout.SOUTH);

        // Wire up actions after all fields are assigned
        wireActions();

        // Take the first sample before the timer fires so the chart is not empty
        takeSample();

        // Start the sampling timer
        timer = new Timer(DEFAULT_REFRESH_RATE, e -> {
            takeSample();
            // Only repaint the chart panel, not the entire viewer
            chartPanel.repaint();
            // Stop automatically when the window has been hidden
            if (!isShowing()) {
                timer.stop();
            }
        });
        timer.setCoalesce(true); // merge missed ticks instead of firing bursts
        timer.start();

        ToolTipManager.sharedInstance().setInitialDelay(0);
    }

    // -----------------------------------------------------------------------
    // UI builders
    // -----------------------------------------------------------------------

    /**
     * Builds the top panel that shows live statistics labels.
     *
     * @return a configured {@link JPanel}
     */
    private JPanel buildStatsPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 2));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Statistics",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION));

        panel.add(currentMemoryLabel);
        panel.add(peakMemoryLabel);
        panel.add(avgMemoryLabel);
        panel.add(totalMemoryLabel);
        return panel;
    }

    /**
     * Builds the bottom panel containing display toggles,
     * action buttons, and the refresh-rate slider.
     *
     * @return a configured {@link JPanel}
     */
    private JPanel buildControlPanel() {
        // --- Toggle checkboxes and action buttons --------------------------
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        buttonRow.add(gridCheckBox);
        buttonRow.add(fillCheckBox);
        buttonRow.add(Box.createHorizontalStrut(10));
        buttonRow.add(pauseButton);
        buttonRow.add(gcButton);

        // --- Refresh-rate slider -------------------------------------------
        JSlider refreshSlider = new JSlider(JSlider.HORIZONTAL, 200, 5000, DEFAULT_REFRESH_RATE);
        refreshSlider.setMajorTickSpacing(1000);
        refreshSlider.setMinorTickSpacing(200);
        refreshSlider.setPaintTicks(true);
        refreshSlider.setPaintLabels(true);
        refreshSlider.setSnapToTicks(false);
        refreshSlider.addChangeListener(e -> {
            // Only apply when the user finishes dragging to avoid rapid timer resets
            if (!refreshSlider.getValueIsAdjusting()) {
                timer.setDelay(refreshSlider.getValue());
            }
        });

        JPanel sliderRow = new JPanel(new BorderLayout(4, 0));
        sliderRow.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        sliderRow.add(new JLabel("Refresh (ms): "), BorderLayout.WEST);
        sliderRow.add(refreshSlider, BorderLayout.CENTER);

        // --- Outer wrapper ------------------------------------------------
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Controls",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION));
        panel.add(buttonRow);
        panel.add(sliderRow);
        return panel;
    }

    // -----------------------------------------------------------------------
    // Event wiring
    // -----------------------------------------------------------------------

    /**
     * Attaches action listeners and mouse listeners to interactive
     * components. Kept separate so the constructor remains readable.
     */
    private void wireActions() {

        // Repaint whenever a display toggle changes
        gridCheckBox.addActionListener(e -> chartPanel.repaint());
        fillCheckBox.addActionListener(e -> chartPanel.repaint());

        // Pause / resume the sampling timer
        pauseButton.addActionListener(e -> {
            isPaused = !isPaused;
            if (isPaused) {
                timer.stop();
                pauseButton.setText("Resume");
            } else {
                timer.start();
                pauseButton.setText("Pause");
            }
        });

        // Trigger garbage collection
        gcButton.addActionListener(e -> System.gc());

        // Tooltip on mouse hover over chart
        chartPanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                updateChartTooltip(e.getX(), e.getY());
            }
        });
    }

    // -----------------------------------------------------------------------
    // Sampling
    // -----------------------------------------------------------------------

    /**
     * Reads the current JVM memory figures, stores the used-memory value in
     * the circular buffer, updates the Y-axis ceiling, and refreshes the
     * statistics labels. This method must only be called on the EDT.
     */
    private void takeSample() {
        Runtime rt = Runtime.getRuntime();

        // All values in bytes; convert to MB once
        long totalBytes = rt.totalMemory();
        long freeBytes  = rt.freeMemory();
        int  totalMB    = (int) (totalBytes >> 20); // divide by 1 048 576
        int  usedMB     = (int) ((totalBytes - freeBytes) >> 20);

        // Write into circular buffer
        memoryValues[position] = usedMB;
        position = (position + 1) % VALUE_COUNT;

        // Recompute Y-axis ceiling: at least the committed heap,
        // or the highest sample if the user's workload exceeded it
        int newMax = totalMB;
        for (int v : memoryValues) {
            if (v > newMax) {
                newMax = v;
            }
        }
        maxMemoryYAxis = Math.max(newMax, 1); // guard against zero

        // Update statistics labels
        updateStatsLabels(usedMB, totalMB);
    }

    /**
     * Refreshes the four statistics labels from already-computed values.
     * Uses reusable StringBuilder to avoid string allocation.
     *
     * @param currentMB used memory in the latest sample (MB)
     * @param totalMB   total committed JVM heap (MB)
     */
    private void updateStatsLabels(int currentMB, int totalMB) {
        int peak = 0;
        long sum = 0;
        int count = 0;

        for (int v : memoryValues) {
            if (v > peak)  { peak = v; }
            if (v > 0)     { sum += v; count++; }
        }

        // Reuse StringBuilder to avoid string concatenation allocations
        labelBuilder.setLength(0);
        labelBuilder.append("Current: ").append(currentMB).append(" MB");
        currentMemoryLabel.setText(labelBuilder.toString());

        labelBuilder.setLength(0);
        labelBuilder.append("Total JVM: ").append(totalMB).append(" MB");
        totalMemoryLabel.setText(labelBuilder.toString());

        labelBuilder.setLength(0);
        labelBuilder.append("Peak: ").append(peak).append(" MB");
        peakMemoryLabel.setText(labelBuilder.toString());

        labelBuilder.setLength(0);
        labelBuilder.append("Avg: ").append(count > 0 ? sum / count : 0).append(" MB");
        avgMemoryLabel.setText(labelBuilder.toString());
    }

    // -----------------------------------------------------------------------
    // Tooltip helper
    // -----------------------------------------------------------------------

    /**
     * Computes and sets the chart tooltip when the mouse is over a data point,
     * or clears it when the mouse is outside the chart area.
     *
     * @param mx mouse X in chart-panel coordinates
     * @param my mouse Y in chart-panel coordinates
     */
    private void updateChartTooltip(int mx, int my) {
        int chartW = chartPanel.getWidth()  - MARGIN_LEFT - MARGIN_RIGHT;
        int chartH = chartPanel.getHeight() - MARGIN_TOP  - MARGIN_BOTTOM;

        // Outside chart area → clear tooltip
        if (mx < MARGIN_LEFT || mx > MARGIN_LEFT + chartW
                || my < MARGIN_TOP || my > MARGIN_TOP + chartH) {
            chartPanel.setToolTipText(null);
            return;
        }

        int index = (mx - MARGIN_LEFT) * VALUE_COUNT / chartW;
        if (index < 0 || index >= VALUE_COUNT) {
            chartPanel.setToolTipText(null);
            return;
        }

        int valueMB = memoryValues[(position + index) % VALUE_COUNT];
        int valueY  = MARGIN_TOP + chartH - (int) ((double) valueMB * chartH / maxMemoryYAxis);

        if (Math.abs(my - valueY) < 15) {
            // How many steps ago (in terms of array slots, not real seconds)
            int stepsAgo = VALUE_COUNT - index - 1;
            String timeLabel = (stepsAgo == 0) ? "now" : stepsAgo + " samples ago";
            chartPanel.setToolTipText(
                    "<html><b>" + valueMB + " MB</b><br><small>" + timeLabel + "</small></html>");
        } else {
            chartPanel.setToolTipText(null);
        }
    }

    // -----------------------------------------------------------------------
    // Inner chart panel
    // -----------------------------------------------------------------------

    /**
     * A dedicated panel that owns the chart painting logic.
     * Keeping paint code here (rather than overriding {@code paintComponent}
     * on the outer class) avoids confusion about which component is being
     * painted and simplifies size queries.
     */
    private final class ChartPanel extends JPanel {

        private static final long serialVersionUID = 1L;

        /** Cached foreground color */
        private Color cachedFgColor = null;

        /** Cached font metrics */
        private FontMetrics cachedFontMetrics = null;

        ChartPanel() {
            setPreferredSize(new Dimension(700, 380));
            setBackground(CHART_BACKGROUND);
            setBorder(BorderFactory.createLineBorder(GRID_COLOR, 1));
            // Disable double buffering at component level (Swing already does it at top level)
            setDoubleBuffered(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g); // clears background to white

            // Don't create a new Graphics2D - reuse the one provided
            Graphics2D g2 = (Graphics2D) g;

            // Set rendering hints once
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            // Performance hint
            g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_SPEED);

            paintChart(g2, getWidth(), getHeight());
        }
    }

    // -----------------------------------------------------------------------
    // Chart painting
    // -----------------------------------------------------------------------

    /**
     * Paints the full chart: grid, axes, labels, area fill, and data line.
     * Uses pre-allocated arrays to avoid memory allocation during paint.
     *
     * @param g2     the Graphics2D context (already anti-aliased)
     * @param width  pixel width of the chart panel
     * @param height pixel height of the chart panel
     */
    private void paintChart(Graphics2D g2, int width, int height) {

        int chartW = width  - MARGIN_LEFT - MARGIN_RIGHT;
        int chartH = height - MARGIN_TOP  - MARGIN_BOTTOM;

        // Guard against degenerate sizes during layout
        if (chartW <= 0 || chartH <= 0) {
            return;
        }

        // Use cached foreground color
        Color fgColor = UIManager.getColor("Label.foreground");
        if (fgColor == null) { fgColor = Color.DARK_GRAY; }

        FontMetrics fm = g2.getFontMetrics();

        // --- Grid and Y-axis labels ----------------------------------------
        final int numYDivisions = 5;
        for (int i = 0; i <= numYDivisions; i++) {
            int valueMB = maxMemoryYAxis * i / numYDivisions;
            int y       = MARGIN_TOP + chartH - (i * chartH / numYDivisions);

            // Y-axis label, right-aligned before MARGIN_LEFT
            // Reuse StringBuilder
            labelBuilder.setLength(0);
            labelBuilder.append(valueMB).append(" MB");
            String label = labelBuilder.toString();
            int labelW   = fm.stringWidth(label);
            g2.setColor(fgColor);
            g2.drawString(label, MARGIN_LEFT - labelW - 4, y + (fm.getAscent() >> 1));

            // Horizontal grid line
            if (gridCheckBox.isSelected()) {
                g2.setColor(GRID_COLOR);
                g2.drawLine(MARGIN_LEFT, y, MARGIN_LEFT + chartW, y);
            }
        }

        // --- Vertical grid lines ------------------------------------------
        if (gridCheckBox.isSelected()) {
            final int numXDivisions = 10;
            g2.setColor(GRID_COLOR);
            for (int i = 0; i <= numXDivisions; i++) {
                int x = MARGIN_LEFT + i * chartW / numXDivisions;
                g2.drawLine(x, MARGIN_TOP, x, MARGIN_TOP + chartH);
            }
        }

        // --- Axes ---------------------------------------------------------
        g2.setColor(fgColor);
        g2.setStroke(NORMAL_STROKE);
        // Y-axis
        g2.drawLine(MARGIN_LEFT, MARGIN_TOP, MARGIN_LEFT, MARGIN_TOP + chartH);
        // X-axis
        g2.drawLine(MARGIN_LEFT, MARGIN_TOP + chartH,
                    MARGIN_LEFT + chartW, MARGIN_TOP + chartH);

        // --- Populate reusable coordinate arrays ---------------------------
        // Closing point: bottom-left
        xPoints[0] = MARGIN_LEFT;
        yPoints[0] = MARGIN_TOP + chartH;

        double scale = (double) chartH / maxMemoryYAxis;

        for (int i = 0; i < VALUE_COUNT; i++) {
            int v = memoryValues[(position + i) % VALUE_COUNT];
            xPoints[i + 1] = MARGIN_LEFT + i * chartW / VALUE_COUNT;
            yPoints[i + 1] = MARGIN_TOP + chartH - (int) (v * scale);
        }

        // Closing point: bottom-right
        xPoints[VALUE_COUNT + 1] = MARGIN_LEFT + chartW;
        yPoints[VALUE_COUNT + 1] = MARGIN_TOP + chartH;

        // --- Area fill ---------------------------------------------------
        if (fillCheckBox.isSelected()) {
            g2.setColor(FILL_COLOR);
            g2.fillPolygon(xPoints, yPoints, VALUE_COUNT + 2);
        }

        // --- Data line ---------------------------------------------------
        g2.setColor(LINE_COLOR);
        g2.setStroke(LINE_STROKE);
        // Draw only the data points (indices 1 to VALUE_COUNT)
        g2.drawPolyline(xPoints, yPoints, VALUE_COUNT + 1);

        // --- Chart title --------------------------------------------------
        g2.setColor(fgColor);
        g2.setStroke(NORMAL_STROKE);
        g2.drawString("Memory Usage (MB)", MARGIN_LEFT, MARGIN_TOP - 8);
    }

    // -----------------------------------------------------------------------
    // Static factory / entry points
    // -----------------------------------------------------------------------

    /**
     * Brings the Memory Viewer window to the front, creating it if necessary.
     * Safe to call from any thread — marshals to the EDT internally.
     */
    public static void displayMemoryChart() {
        SwingUtilities.invokeLater(() -> {
            if (frame != null) {
                frame.setVisible(true);
                frame.toFront();
                return;
            }

            frame = new JFrame("SPMF - Memory Viewer (JVM)");
            frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
            frame.setMinimumSize(new Dimension(600, 420));

            MemoryViewer viewer = new MemoryViewer();
            frame.add(viewer);
            frame.pack(); // respect preferred sizes instead of hard-coding 750×600
            frame.setLocationRelativeTo(null);

            // Null the static reference when the window is closed so that
            // the next call to displayMemoryChart() creates a fresh instance.
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    if (viewer.timer != null) {
                        viewer.timer.stop();
                    }
                    frame = null;
                }
            });

            frame.setVisible(true);
        });
    }

    /**
     * Standalone entry point for testing the viewer outside of SPMF.
     *
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) {
        // Apply the system Look & Feel before any Swing component is created
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Fall back to the default cross-platform L&F — not fatal
            e.printStackTrace();
        }
        displayMemoryChart();
    }
}