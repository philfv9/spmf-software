package ca.pfv.spmf.gui.visual_pattern_viewer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.List;

import javax.swing.JSlider;

/*
 * Copyright (c) 2008-2025 Philippe Fournier-Viger
 *
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * SPMF is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPMF. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Do not remove copyright and license information
 */
/**
 * A custom {@link JSlider} subclass that paints a distribution histogram as a
 * colored background below the slider track. Each bin is colored from light
 * red (few patterns) to dark red (many patterns), giving immediate visual
 * feedback about where measure values cluster.
 * <p>
 * The histogram is pre-computed in {@link #updateDistribution(List)} and
 * cached; painting itself is just drawing {@code NUM_BINS} rectangles so it
 * is extremely fast.
 *
 * @author Philippe Fournier-Viger 2025
 */
public class MeasureDistributionBar extends JSlider {

    private static final long serialVersionUID = 1L;

    /** Number of histogram bins drawn below the track. */
    private static final int NUM_BINS = 50;

    /** Height reserved for the histogram strip below the slider thumb. */
    private static final int HIST_STRIP_HEIGHT = 5;

    /** Color for bins with zero patterns (empty). */
    private static final Color COLOR_EMPTY = new Color(235, 235, 235);

    /** Color for the least-populated non-empty bin. */
    private static final Color COLOR_LOW = new Color(255, 190, 190);

    /** Color for the most-populated bin. */
    private static final Color COLOR_HIGH = new Color(180, 0, 0);

    /** Thin border drawn around the histogram strip. */
    private static final Color COLOR_BORDER = new Color(180, 180, 180);

    /** Min and max of the measure range (for binning incoming values). */
    private final double measureMin;
    private final double measureMax;

    /** Pre-computed bin counts, length == NUM_BINS. */
    private int[] binCounts = new int[NUM_BINS];

    /** Maximum count across all bins (for color scaling). */
    private int maxBinCount = 0;

    /**
     * Constructs a distribution slider for a single measure.
     *
     * @param measureMin the minimum value of the measure (left edge)
     * @param measureMax the maximum value of the measure (right edge)
     */
    public MeasureDistributionBar(double measureMin, double measureMax) {
        super(0, 1000, 0);
        this.measureMin = measureMin;
        this.measureMax = measureMax;

        setPaintTicks(false);
        setPaintLabels(false);
        setOpaque(false);

        // Make the slider taller to accommodate the histogram strip below
        setPreferredSize(new Dimension(150, 28 + HIST_STRIP_HEIGHT));
        setMinimumSize(new Dimension(80,  28 + HIST_STRIP_HEIGHT));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 28 + HIST_STRIP_HEIGHT));
    }

    // ---------------------------------------------------------------
    // Data update
    // ---------------------------------------------------------------

    /**
     * Recomputes the histogram from the given list of measure values. This is
     * O(n) in the number of values and should be called only when the visible
     * pattern set changes (after filtering / sorting), never from
     * {@link #paintComponent}.
     *
     * @param values measure values for all currently visible patterns;
     *               may be {@code null} or empty
     */
    public void updateDistribution(List<Double> values) {
        // Reset
        for (int i = 0; i < NUM_BINS; i++) binCounts[i] = 0;
        maxBinCount = 0;

        if (values == null || values.isEmpty()) {
            repaint();
            return;
        }

        double range = measureMax - measureMin;

        if (range <= 0) {
            // All values identical — pile everything into the middle bin
            int midBin = NUM_BINS / 2;
            binCounts[midBin] = values.size();
            maxBinCount = values.size();
        } else {
            for (Double v : values) {
                if (v == null) continue;
                // Normalize to [0, 1] then map to bin
                double norm = (v - measureMin) / range;
                norm = Math.max(0.0, Math.min(1.0, norm));
                int bin = (int) (norm * NUM_BINS);
                if (bin >= NUM_BINS) bin = NUM_BINS - 1; // clamp max edge
                binCounts[bin]++;
            }
            for (int c : binCounts) if (c > maxBinCount) maxBinCount = c;
        }

        repaint();
    }

    // ---------------------------------------------------------------
    // Painting
    // ---------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g) {
        // Let the L&F paint the slider track + thumb first
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            paintHistogramStrip(g2);
        } finally {
            g2.dispose();
        }
    }

    /**
     * Paints the histogram strip at the bottom of the component, below the
     * slider track. Computes track bounds by analyzing the slider's geometry.
     *
     * @param g2 the graphics context (will be disposed by the caller)
     */
    private void paintHistogramStrip(Graphics2D g2) {
        // Compute track bounds: horizontal slider track normally sits in the middle
        // vertically, with standard insets on the sides.
        
        Insets ins = getInsets();
        int trackLeft  = ins.left + 8;   // Standard L&F left padding
        int trackRight = getWidth() - ins.right - 8; // Standard L&F right padding
        int trackWidth = trackRight - trackLeft;

        if (trackWidth <= 0) return;

        // The histogram strip sits at the BOTTOM of the component
        int stripY = getHeight() - ins.bottom - HIST_STRIP_HEIGHT;
        int stripH = HIST_STRIP_HEIGHT;

        // Bin pixel width (last bin takes any remainder)
        double binPixelWidth = (double) trackWidth / NUM_BINS;

        // Draw each bin
        for (int i = 0; i < NUM_BINS; i++) {
            int binX = trackLeft + (int) Math.round(i * binPixelWidth);
            int binW = (int) Math.round((i + 1) * binPixelWidth)
                     - (int) Math.round(i * binPixelWidth);
            if (binW < 1) binW = 1;

            Color fill = binColor(binCounts[i]);
            g2.setColor(fill);
            g2.fillRect(binX, stripY, binW, stripH);
        }

        // Border around the whole strip
        g2.setColor(COLOR_BORDER);
        g2.setStroke(new java.awt.BasicStroke(0.8f));
        g2.drawRect(trackLeft, stripY, trackWidth, stripH - 1);

        // Thin dividers between bins (subtle)
        g2.setColor(new Color(160, 160, 160, 60)); // very translucent
        g2.setStroke(new java.awt.BasicStroke(0.5f));
        for (int i = 1; i < NUM_BINS; i++) {
            int divX = trackLeft + (int) Math.round(i * binPixelWidth);
            g2.drawLine(divX, stripY, divX, stripY + stripH - 1);
        }
    }

    /**
     * Returns the fill color for a bin given its count and the global maximum.
     *
     * @param count bin count
     * @return fill color
     */
    private Color binColor(int count) {
        if (count == 0 || maxBinCount == 0) return COLOR_EMPTY;
        // Use a square-root scale so sparse bins are still visible
        double ratio = Math.sqrt((double) count / maxBinCount);
        int r = lerp(COLOR_LOW.getRed(),   COLOR_HIGH.getRed(),   ratio);
        int gr = lerp(COLOR_LOW.getGreen(), COLOR_HIGH.getGreen(), ratio);
        int b = lerp(COLOR_LOW.getBlue(),  COLOR_HIGH.getBlue(),  ratio);
        return new Color(r, gr, b);
    }

    /**
     * Linear interpolation between two integer values.
     *
     * @param a     start value
     * @param b     end value
     * @param ratio blend factor in [0, 1]
     * @return interpolated value clamped to [0, 255]
     */
    private static int lerp(int a, int b, double ratio) {
        return Math.max(0, Math.min(255, (int) Math.round(a + ratio * (b - a))));
    }
}