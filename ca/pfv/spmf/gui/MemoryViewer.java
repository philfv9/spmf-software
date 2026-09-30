package ca.pfv.spmf.gui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.KeyboardFocusManager;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Window;
import java.awt.event.HierarchyEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

/*
 * Copyright (c) 2008-2026 Philippe Fournier-Viger
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
 * 
 * Do not remove copyright and license
 */
/**
 * A compact, real-time viewer of JVM heap usage.
 * <p>
 * The panel shows a set of statistic tiles (used / committed / peak / average),
 * a heap-usage bar, a time-based chart with hover inspection, and a small
 * toolbar (refresh rate, grid/fill toggles, clear, GC, pause).
 * <p>
 * Sampling runs only while the component is showing on screen and is
 * automatically stopped/restarted when the window is closed or re-opened.
 *
 * @author Philippe Fournier-Viger, 2023
 */
public class MemoryViewer extends JPanel {

    private static final long serialVersionUID = 278261238139167055L;

    /** The single viewer window (created lazily, nulled when disposed). */
    private static JFrame frame;

    // -----------------------------------------------------------------------
    // Configuration
    // -----------------------------------------------------------------------

    /** Number of samples kept in the ring buffer. */
    private static final int CAPACITY = 120;

    /** Bytes per megabyte. */
    private static final double MB = 1024.0 * 1024.0;

    /** Selectable refresh intervals (milliseconds) and their labels. */
    private static final int[]    REFRESH_MS     = {250, 500, 1000, 2000, 5000};
    private static final String[] REFRESH_LABELS = {"250 ms", "500 ms", "1 s", "2 s", "5 s"};
    private static final int      DEFAULT_REFRESH_INDEX = 2;

    // -----------------------------------------------------------------------
    // Palette
    // -----------------------------------------------------------------------

    private static final Color BG             = new Color(246, 247, 249);
    private static final Color CARD_BG        = Color.WHITE;
    private static final Color CARD_BORDER    = new Color(225, 228, 232);
    private static final Color TEXT_PRIMARY   = new Color(33, 37, 41);
    private static final Color TEXT_SECONDARY = new Color(118, 124, 132);
    private static final Color GRID           = new Color(236, 238, 241);
    private static final Color AXIS           = new Color(205, 209, 214);
    private static final Color LINE_COLOR     = new Color(37, 117, 214);
    private static final Color FILL_TOP       = new Color(37, 117, 214, 85);
    private static final Color FILL_BOTTOM    = new Color(37, 117, 214, 0);
    private static final Color COMMITTED      = new Color(236, 146, 44);
    private static final Color GC_MARK        = new Color(128, 96, 210);
    private static final Color BAR_TRACK      = new Color(228, 231, 235);
    private static final Color BAR_OK         = LINE_COLOR;
    private static final Color BAR_WARN       = COMMITTED;
    private static final Color BAR_HIGH       = new Color(222, 72, 72);
    private static final Color BADGE_BG       = new Color(255, 243, 224);
    private static final Color BADGE_FG       = new Color(196, 108, 18);

    private static final BasicStroke LINE_STROKE =
            new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final BasicStroke THIN_STROKE = new BasicStroke(1f);
    private static final BasicStroke DASHED_STROKE = new BasicStroke(
            1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {4f, 4f}, 0f);

    // -----------------------------------------------------------------------
    // Data (ring buffer)
    // -----------------------------------------------------------------------

    private final long[]    usedSamples = new long[CAPACITY];
    private final long[]    timeSamples = new long[CAPACITY];
    private final boolean[] gcSamples   = new boolean[CAPACITY];

    /** Index of the next slot to write. */
    private int head = 0;
    /** Number of valid samples (0..CAPACITY). */
    private int count = 0;

    private long   lastUsed;
    private long   lastCommitted;
    private long   maxHeap = -1;
    private long   peakUsed;
    private double avgUsed;

    // -----------------------------------------------------------------------
    // UI
    // -----------------------------------------------------------------------

    private final Font captionFont;
    private final Font smallFont;
    private final Font valueFont;

    private final StatTile usedTile;
    private final StatTile committedTile;
    private final StatTile peakTile;
    private final StatTile avgTile;
    private final UsageBar usageBar;
    private final JLabel   usageLabel;
    private final ChartPanel chart;

    private final JComboBox<String> refreshCombo;
    private final JCheckBox gridCheck;
    private final JCheckBox fillCheck;
    private final JButton   clearButton;
    private final JButton   gcButton;
    private final JButton   pauseButton;

    private final Timer timer;
    private boolean paused;

    private final SimpleDateFormat clockFormat = new SimpleDateFormat("HH:mm:ss");

    // -----------------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------------

    /** Creates the viewer. Sampling starts automatically once the panel is showing. */
    public MemoryViewer() {
        Font base = UIManager.getFont("Label.font");
        if (base == null) {
            base = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        }
        float sz = base.getSize2D();
        captionFont = base.deriveFont(Font.PLAIN, Math.max(9f, sz - 2f));
        smallFont   = base.deriveFont(Font.PLAIN, Math.max(9f, sz - 1f));
        valueFont   = base.deriveFont(Font.BOLD, sz + 3f);

        setLayout(new BorderLayout(0, 10));
        setBackground(BG);
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // --- Header: stat tiles + usage bar --------------------------------
        usedTile      = new StatTile("Used",      LINE_COLOR, captionFont, valueFont);
        committedTile = new StatTile("Committed", COMMITTED,  captionFont, valueFont);
        peakTile      = new StatTile("Peak",      null,       captionFont, valueFont);
        avgTile       = new StatTile("Average",   null,       captionFont, valueFont);

        JPanel tiles = new JPanel(new GridLayout(1, 4, 8, 0));
        tiles.setOpaque(false);
        tiles.add(usedTile);
        tiles.add(committedTile);
        tiles.add(peakTile);
        tiles.add(avgTile);

        usageBar   = new UsageBar();
        usageLabel = new JLabel(" ");
        usageLabel.setFont(captionFont);
        usageLabel.setForeground(TEXT_SECONDARY);

        JPanel usageRow = new JPanel(new BorderLayout(10, 0));
        usageRow.setOpaque(false);
        usageRow.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        usageRow.add(usageBar, BorderLayout.CENTER);
        usageRow.add(usageLabel, BorderLayout.EAST);

        JPanel header = new JPanel(new BorderLayout(0, 8));
        header.setOpaque(false);
        header.add(tiles, BorderLayout.CENTER);
        header.add(usageRow, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        // --- Controls (created before the chart, which reads the toggles) ---
        refreshCombo = new JComboBox<>(REFRESH_LABELS);
        refreshCombo.setSelectedIndex(DEFAULT_REFRESH_INDEX);
        refreshCombo.setFont(smallFont);
        refreshCombo.setToolTipText("Sampling interval");

        gridCheck = new JCheckBox("Grid", true);
        fillCheck = new JCheckBox("Fill", true);
        for (JCheckBox cb : new JCheckBox[] {gridCheck, fillCheck}) {
            cb.setOpaque(false);
            cb.setFont(smallFont);
            cb.setFocusPainted(false);
        }

        clearButton = new JButton("Clear");
        clearButton.setToolTipText("Discard collected samples");
        gcButton = new JButton("Run GC");
        gcButton.setToolTipText("Request a garbage collection");
        pauseButton = new JButton("Pause");
        pauseButton.setToolTipText("Pause / resume sampling");
        for (JButton b : new JButton[] {clearButton, gcButton, pauseButton}) {
            b.setFont(smallFont);
            b.setFocusPainted(false);
        }

        // --- Chart ---------------------------------------------------------
        chart = new ChartPanel();
        add(chart, BorderLayout.CENTER);

        add(buildToolbar(), BorderLayout.SOUTH);

        // --- Timer & wiring -------------------------------------------------
        timer = new Timer(REFRESH_MS[DEFAULT_REFRESH_INDEX], e -> {
            takeSample(false);
            chart.repaint();
        });
        timer.setCoalesce(true);

        wireActions();
        takeSample(false);

        // Run only while actually visible on screen.
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (isShowing()) {
                    if (!paused) {
                        timer.start();
                    }
                } else {
                    timer.stop();
                }
            }
        });
    }

    private JComponent buildToolbar() {
        JLabel refreshLabel = new JLabel("Refresh");
        refreshLabel.setFont(smallFont);
        refreshLabel.setForeground(TEXT_SECONDARY);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(refreshLabel);
        left.add(refreshCombo);
        left.add(gridCheck);
        left.add(fillCheck);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);
        right.add(clearButton);
        right.add(gcButton);
        right.add(pauseButton);

        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private void wireActions() {
        gridCheck.addActionListener(e -> chart.repaint());
        fillCheck.addActionListener(e -> chart.repaint());

        refreshCombo.addActionListener(e -> {
            int ms = REFRESH_MS[refreshCombo.getSelectedIndex()];
            timer.setDelay(ms);
            timer.setInitialDelay(ms);
            if (timer.isRunning()) {
                timer.restart();
            }
            chart.repaint(); // time axis estimate depends on the delay
        });

        pauseButton.addActionListener(e -> setPaused(!paused));

        clearButton.addActionListener(e -> {
            head = 0;
            count = 0;
            peakUsed = 0;
            avgUsed = 0;
            takeSample(false);
            chart.repaint();
        });

        gcButton.addActionListener(e -> {
            gcButton.setEnabled(false);
            Thread t = new Thread(() -> {
                System.gc();
                SwingUtilities.invokeLater(() -> {
                    takeSample(true);
                    chart.repaint();
                    gcButton.setEnabled(true);
                });
            }, "MemoryViewer-GC");
            t.setDaemon(true);
            t.start();
        });
    }

    private void setPaused(boolean pause) {
        paused = pause;
        pauseButton.setText(paused ? "Resume" : "Pause");
        if (paused) {
            timer.stop();
        } else if (isShowing()) {
            timer.start();
        }
        chart.repaint();
    }

    /** Stops sampling. Called when the owning window is disposed. */
    public void stop() {
        timer.stop();
    }

    // -----------------------------------------------------------------------
    // Sampling
    // -----------------------------------------------------------------------

    /** Ring-buffer index of the i-th valid sample (0 = oldest). */
    private int sampleIndex(int i) {
        return (head - count + i + CAPACITY) % CAPACITY;
    }

    private void takeSample(boolean afterGc) {
        Runtime rt = Runtime.getRuntime();
        long committed = rt.totalMemory();
        long used = committed - rt.freeMemory();
        long max = rt.maxMemory();

        usedSamples[head] = used;
        timeSamples[head] = System.currentTimeMillis();
        gcSamples[head]   = afterGc;
        head = (head + 1) % CAPACITY;
        if (count < CAPACITY) {
            count++;
        }

        lastUsed = used;
        lastCommitted = committed;
        maxHeap = (max == Long.MAX_VALUE) ? -1 : max;

        long peak = 0;
        double sum = 0;
        for (int i = 0; i < count; i++) {
            long v = usedSamples[sampleIndex(i)];
            if (v > peak) {
                peak = v;
            }
            sum += v;
        }
        peakUsed = peak;
        avgUsed = count > 0 ? sum / count : 0;

        updateHeader();
    }

    private void updateHeader() {
        usedTile.setValue(formatBytes(lastUsed));
        committedTile.setValue(formatBytes(lastCommitted));
        peakTile.setValue(formatBytes(peakUsed));
        avgTile.setValue(formatBytes((long) avgUsed));

        long denominator = maxHeap > 0 ? maxHeap : lastCommitted;
        double fraction = denominator > 0 ? (double) lastUsed / denominator : 0;
        usageBar.setFraction(fraction);
        int pct = (int) Math.round(fraction * 100);
        usageLabel.setText(maxHeap > 0
                ? pct + "% of " + formatBytes(maxHeap) + " max heap"
                : pct + "% of committed heap");
    }

    // -----------------------------------------------------------------------
    // Formatting helpers
    // -----------------------------------------------------------------------

    private static String trimNumber(double v, int decimals) {
        String s = String.format(Locale.ROOT, "%." + decimals + "f", v);
        if (s.indexOf('.') >= 0) {
            int end = s.length();
            while (end > 0 && s.charAt(end - 1) == '0') {
                end--;
            }
            if (end > 0 && s.charAt(end - 1) == '.') {
                end--;
            }
            s = s.substring(0, end);
        }
        return s;
    }

    static String formatBytes(long bytes) {
        if (bytes < 0) {
            return "\u2014";
        }
        double mb = bytes / MB;
        if (mb >= 1024) {
            double gb = mb / 1024;
            return trimNumber(gb, gb >= 10 ? 1 : 2) + " GB";
        }
        if (mb >= 100) {
            return trimNumber(mb, 0) + " MB";
        }
        return trimNumber(mb, 1) + " MB";
    }

    static String formatAgo(long ms) {
        long sec = Math.round(ms / 1000.0);
        if (sec <= 0) {
            return "now";
        }
        if (sec < 60) {
            return "-" + sec + " s";
        }
        long m = sec / 60;
        long s = sec % 60;
        return s == 0 ? "-" + m + " min" : "-" + m + "m " + s + "s";
    }

    /** Rounds a raw tick step up to a "nice" value (1, 2, 2.5, 5 × 10^k). */
    private static double niceStep(double raw) {
        double mag = Math.pow(10, Math.floor(Math.log10(raw)));
        double norm = raw / mag;
        double nice;
        if (norm <= 1) {
            nice = 1;
        } else if (norm <= 2) {
            nice = 2;
        } else if (norm <= 2.5) {
            nice = 2.5;
        } else if (norm <= 5) {
            nice = 5;
        } else {
            nice = 10;
        }
        return nice * mag;
    }

    // -----------------------------------------------------------------------
    // Chart
    // -----------------------------------------------------------------------

    private final class ChartPanel extends JPanel {

        private static final long serialVersionUID = 1L;

        // plot rectangle from the last paint (used for hit testing)
        private double plotX, plotY, plotW, plotH;

        /** Index (0..count-1) of the hovered sample, or -1. */
        private int hoverSample = -1;

        private final Path2D.Double linePath = new Path2D.Double();
        private final Path2D.Double areaPath = new Path2D.Double();

        // y scale
        private double   yMaxBytes = 1;
        private double   tickStep;
        private int      tickCount = 1;
        private boolean  unitGB;
        private final String[] tickLabels = new String[8];

        ChartPanel() {
            setOpaque(false);
            setPreferredSize(new Dimension(520, 230));

            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    updateHover(e.getX(), e.getY());
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (hoverSample != -1) {
                        hoverSample = -1;
                        repaint();
                    }
                }
            };
            addMouseMotionListener(mouse);
            addMouseListener(mouse);
        }

        private void updateHover(int mx, int my) {
            int newHover = -1;
            if (plotW > 0 && mx >= plotX - 3 && mx <= plotX + plotW + 3
                    && my >= plotY && my <= plotY + plotH) {
                int slot = (int) Math.round((mx - plotX) / plotW * (CAPACITY - 1));
                int i = slot - (CAPACITY - count);
                if (i >= 0 && i < count) {
                    newHover = i;
                }
            }
            if (newHover != hoverSample) {
                hoverSample = newHover;
                repaint();
            }
        }

        private void computeScale() {
            long ref = Math.max(peakUsed, lastCommitted);
            double refMB = Math.max(ref / MB, 1.0) * 1.05; // small headroom
            unitGB = refMB >= 1024;
            double unit = unitGB ? 1024.0 : 1.0;
            double refUnits = refMB / unit;
            tickStep = niceStep(refUnits / 5.0);
            tickCount = (int) Math.ceil(refUnits / tickStep - 1e-9);
            if (tickCount < 1) {
                tickCount = 1;
            }
            if (tickCount >= tickLabels.length) {
                tickCount = tickLabels.length - 1;
            }
            yMaxBytes = tickCount * tickStep * unit * MB;
            String suffix = unitGB ? " GB" : " MB";
            for (int i = 0; i <= tickCount; i++) {
                tickLabels[i] = trimNumber(i * tickStep, 2) + suffix;
            }
        }

        private double xForSlot(int slot) {
            return plotX + plotW * slot / (CAPACITY - 1);
        }

        private double yForBytes(long bytes) {
            double f = Math.min(1.0, bytes / yMaxBytes);
            return plotY + plotH - plotH * f;
        }

        private long agoForSlot(int slot, long newestTime) {
            int delay = timer.getDelay();
            int i = slot - (CAPACITY - count);
            if (count > 0 && i >= 0 && i < count) {
                return newestTime - timeSamples[sampleIndex(i)];
            }
            if (count > 0) {
                long oldestAgo = newestTime - timeSamples[sampleIndex(0)];
                return oldestAgo + (long) (CAPACITY - count - slot) * delay;
            }
            return (long) (CAPACITY - 1 - slot) * delay;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                        RenderingHints.VALUE_STROKE_PURE);

                int w = getWidth();
                int h = getHeight();

                // card background
                Shape card = new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 12, 12);
                g2.setColor(CARD_BG);
                g2.fill(card);
                g2.setColor(CARD_BORDER);
                g2.draw(card);

                g2.setFont(smallFont);
                FontMetrics fm = g2.getFontMetrics();

                computeScale();
                int labelW = 0;
                for (int i = 0; i <= tickCount; i++) {
                    labelW = Math.max(labelW, fm.stringWidth(tickLabels[i]));
                }

                plotX = 14 + labelW + 8;
                plotY = 18;
                plotW = w - plotX - 18;
                plotH = h - plotY - fm.getHeight() - 14;
                if (plotW < 30 || plotH < 30) {
                    return;
                }
                double bottom = plotY + plotH;
                boolean grid = gridCheck.isSelected();

                // --- horizontal grid + Y labels ---------------------------
                for (int i = 0; i <= tickCount; i++) {
                    double y = bottom - plotH * i / tickCount;
                    if (grid && i > 0) {
                        g2.setColor(GRID);
                        g2.draw(new Line2D.Double(plotX, y, plotX + plotW, y));
                    }
                    String s = tickLabels[i];
                    g2.setColor(TEXT_SECONDARY);
                    g2.drawString(s, (float) (plotX - 8 - fm.stringWidth(s)),
                            (float) (y + fm.getAscent() / 2.0 - 1));
                }

                // --- vertical grid + time labels --------------------------
                long newestTime = count > 0
                        ? timeSamples[sampleIndex(count - 1)] : System.currentTimeMillis();
                int divisions = Math.max(2, (int) (plotW / 90));
                for (int d = 0; d <= divisions; d++) {
                    double frac = (double) d / divisions;
                    double x = plotX + plotW * frac;
                    if (grid && d > 0 && d < divisions) {
                        g2.setColor(GRID);
                        g2.draw(new Line2D.Double(x, plotY, x, bottom));
                    }
                    int slot = (int) Math.round(frac * (CAPACITY - 1));
                    String s = formatAgo(agoForSlot(slot, newestTime));
                    float tx;
                    if (d == 0) {
                        tx = (float) x;
                    } else if (d == divisions) {
                        tx = (float) (x - fm.stringWidth(s));
                    } else {
                        tx = (float) (x - fm.stringWidth(s) / 2.0);
                    }
                    g2.setColor(TEXT_SECONDARY);
                    g2.drawString(s, tx, (float) (bottom + 6 + fm.getAscent()));
                }

                // --- axes ---------------------------------------------------
                g2.setStroke(THIN_STROKE);
                g2.setColor(AXIS);
                g2.draw(new Line2D.Double(plotX, plotY, plotX, bottom));
                g2.draw(new Line2D.Double(plotX, bottom, plotX + plotW, bottom));

                // --- committed heap reference line ---------------------------
                if (lastCommitted > 0) {
                    double yc = yForBytes(lastCommitted);
                    if (yc >= plotY) {
                        g2.setColor(COMMITTED);
                        g2.setStroke(DASHED_STROKE);
                        g2.draw(new Line2D.Double(plotX, yc, plotX + plotW, yc));
                        g2.setFont(captionFont);
                        g2.drawString("Committed", (float) (plotX + 6), (float) (yc - 4));
                        g2.setFont(smallFont);
                    }
                }

                // --- data -----------------------------------------------------
                if (count == 0) {
                    String s = "Collecting samples\u2026";
                    g2.setColor(TEXT_SECONDARY);
                    g2.drawString(s, (float) (plotX + (plotW - fm.stringWidth(s)) / 2),
                            (float) (plotY + plotH / 2));
                } else {
                    paintSeries(g2, bottom, newestTime);
                }

                // --- paused badge -------------------------------------------
                if (paused) {
                    paintBadge(g2, "Paused", plotX + plotW - 6, plotY + 6);
                }
            } finally {
                g2.dispose();
            }
        }

        private void paintSeries(Graphics2D g2, double bottom, long newestTime) {
            Shape oldClip = g2.getClip();
            g2.clip(new Rectangle2D.Double(plotX, plotY - 1, plotW + 1, plotH + 2));

            linePath.reset();
            areaPath.reset();
            double lastX = 0, lastY = 0;
            for (int i = 0; i < count; i++) {
                int idx = sampleIndex(i);
                double x = xForSlot(CAPACITY - count + i);
                double y = yForBytes(usedSamples[idx]);
                if (i == 0) {
                    linePath.moveTo(x, y);
                    areaPath.moveTo(x, bottom);
                    areaPath.lineTo(x, y);
                } else {
                    linePath.lineTo(x, y);
                    areaPath.lineTo(x, y);
                }
                lastX = x;
                lastY = y;
            }
            areaPath.lineTo(lastX, bottom);
            areaPath.closePath();

            if (fillCheck.isSelected()) {
                g2.setPaint(new GradientPaint(0f, (float) plotY, FILL_TOP,
                        0f, (float) bottom, FILL_BOTTOM));
                g2.fill(areaPath);
            }
            g2.setColor(LINE_COLOR);
            g2.setStroke(LINE_STROKE);
            g2.draw(linePath);

            // GC markers (small triangles at the top of the plot)
            g2.setColor(GC_MARK);
            for (int i = 0; i < count; i++) {
                int idx = sampleIndex(i);
                if (gcSamples[idx]) {
                    double x = xForSlot(CAPACITY - count + i);
                    Path2D.Double tri = new Path2D.Double();
                    tri.moveTo(x - 4, plotY);
                    tri.lineTo(x + 4, plotY);
                    tri.lineTo(x, plotY + 6);
                    tri.closePath();
                    g2.fill(tri);
                }
            }
            g2.setClip(oldClip);

            // newest point marker
            paintDot(g2, lastX, lastY);

            // hover crosshair + info box
            if (hoverSample >= 0 && hoverSample < count) {
                int idx = sampleIndex(hoverSample);
                double x = xForSlot(CAPACITY - count + hoverSample);
                double y = yForBytes(usedSamples[idx]);

                g2.setColor(AXIS);
                g2.setStroke(DASHED_STROKE);
                g2.draw(new Line2D.Double(x, plotY, x, bottom));
                paintDot(g2, x, y);

                String l1 = formatBytes(usedSamples[idx]) + (gcSamples[idx] ? "  (after GC)" : "");
                String l2 = formatAgo(newestTime - timeSamples[idx]) + "  \u00B7  "
                        + clockFormat.format(new Date(timeSamples[idx]));

                Font boldSmall = smallFont.deriveFont(Font.BOLD);
                FontMetrics fm1 = g2.getFontMetrics(boldSmall);
                FontMetrics fm2 = g2.getFontMetrics(captionFont);
                int bw = Math.max(fm1.stringWidth(l1), fm2.stringWidth(l2)) + 18;
                int bh = fm1.getHeight() + fm2.getHeight() + 10;
                double bx = x + 12;
                if (bx + bw > plotX + plotW) {
                    bx = x - 12 - bw;
                }
                double by = plotY + 6;

                Shape box = new RoundRectangle2D.Double(bx, by, bw, bh, 8, 8);
                g2.setColor(new Color(255, 255, 255, 238));
                g2.fill(box);
                g2.setColor(CARD_BORDER);
                g2.setStroke(THIN_STROKE);
                g2.draw(box);

                g2.setFont(boldSmall);
                g2.setColor(TEXT_PRIMARY);
                g2.drawString(l1, (float) (bx + 9), (float) (by + 5 + fm1.getAscent()));
                g2.setFont(captionFont);
                g2.setColor(TEXT_SECONDARY);
                g2.drawString(l2, (float) (bx + 9),
                        (float) (by + 5 + fm1.getHeight() + fm2.getAscent()));
                g2.setFont(smallFont);
            }
        }

        private void paintDot(Graphics2D g2, double x, double y) {
            g2.setColor(Color.WHITE);
            g2.fill(new Ellipse2D.Double(x - 5, y - 5, 10, 10));
            g2.setColor(LINE_COLOR);
            g2.fill(new Ellipse2D.Double(x - 3.5, y - 3.5, 7, 7));
        }

        /** Paints a small pill badge whose top-right corner is at (right, top). */
        private void paintBadge(Graphics2D g2, String text, double right, double top) {
            Font f = captionFont.deriveFont(Font.BOLD);
            FontMetrics fm = g2.getFontMetrics(f);
            int bw = fm.stringWidth(text) + 16;
            int bh = fm.getHeight() + 6;
            Shape pill = new RoundRectangle2D.Double(right - bw, top, bw, bh, bh, bh);
            g2.setColor(BADGE_BG);
            g2.fill(pill);
            g2.setColor(BADGE_FG);
            g2.setFont(f);
            g2.drawString(text, (float) (right - bw + 8), (float) (top + 3 + fm.getAscent()));
            g2.setFont(smallFont);
        }
    }

    // -----------------------------------------------------------------------
    // Small reusable components
    // -----------------------------------------------------------------------

    /** A rounded card showing a caption and a value. */
    private static final class StatTile extends JPanel {

        private static final long serialVersionUID = 1L;
        private final JLabel value;

        StatTile(String caption, Color accent, Font captionFont, Font valueFont) {
            setOpaque(false);
            setLayout(new BorderLayout(0, 2));
            setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

            JLabel cap = new JLabel(caption.toUpperCase(Locale.ROOT));
            cap.setFont(captionFont);
            cap.setForeground(TEXT_SECONDARY);
            if (accent != null) {
                cap.setIcon(new DotIcon(accent));
                cap.setIconTextGap(6);
            }

            value = new JLabel("\u2014");
            value.setFont(valueFont);
            value.setForeground(TEXT_PRIMARY);

            add(cap, BorderLayout.NORTH);
            add(value, BorderLayout.CENTER);
        }

        void setValue(String text) {
            value.setText(text);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                Shape r = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.setColor(CARD_BG);
                g2.fill(r);
                g2.setColor(CARD_BORDER);
                g2.draw(r);
            } finally {
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }

    /** A thin rounded progress bar. */
    private static final class UsageBar extends JComponent {

        private static final long serialVersionUID = 1L;
        private double fraction;

        UsageBar() {
            setPreferredSize(new Dimension(80, 8));
            setMinimumSize(new Dimension(20, 8));
        }

        void setFraction(double f) {
            double clamped = Math.max(0, Math.min(1, f));
            if (clamped != fraction) {
                fraction = clamped;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                g2.setColor(BAR_TRACK);
                g2.fill(new RoundRectangle2D.Double(0, 0, w, h, h, h));
                double fw = w * fraction;
                if (fw > 0) {
                    g2.setColor(fraction > 0.9 ? BAR_HIGH : fraction > 0.75 ? BAR_WARN : BAR_OK);
                    g2.fill(new RoundRectangle2D.Double(0, 0, Math.max(fw, h), h, h, h));
                }
            } finally {
                g2.dispose();
            }
        }
    }

    /** A small filled circle used as a legend marker. */
    private static final class DotIcon implements Icon {

        private final Color color;

        DotIcon(Color color) {
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.fill(new Ellipse2D.Double(x, y, 8, 8));
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return 8;
        }

        @Override
        public int getIconHeight() {
            return 8;
        }
    }

    // -----------------------------------------------------------------------
    // Static entry points
    // -----------------------------------------------------------------------

    /**
     * Shows the Memory Viewer window, creating it if necessary.
     * Safe to call from any thread.
     */
    public static void displayMemoryChart() {
        SwingUtilities.invokeLater(() -> {
            if (frame != null) {
                int state = frame.getExtendedState();
                if ((state & Frame.ICONIFIED) != 0) {
                    frame.setExtendedState(state & ~Frame.ICONIFIED);
                }
                frame.setVisible(true);
                frame.toFront();
                frame.requestFocus();
                return;
            }

            final MemoryViewer viewer = new MemoryViewer();
            final JFrame f = new JFrame("SPMF - Memory Viewer");
            f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            f.setContentPane(viewer);
            f.pack();
            f.setMinimumSize(new Dimension(480, 340));

            // Position next to whatever SPMF window is active (or centre of screen)
            Window owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
            f.setLocationRelativeTo(owner);

            // Escape closes the window
            f.getRootPane().registerKeyboardAction(e -> f.dispose(),
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                    JComponent.WHEN_IN_FOCUSED_WINDOW);

            f.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    viewer.stop();
                    if (frame == f) {
                        frame = null;
                    }
                }
            });

            frame = f;
            f.setVisible(true);
        });
    }

    /**
     * Standalone entry point for testing the viewer outside of SPMF.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Not fatal — fall back to the default look and feel
        }
        displayMemoryChart();
    }
}