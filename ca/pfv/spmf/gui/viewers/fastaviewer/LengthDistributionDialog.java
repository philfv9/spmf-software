package ca.pfv.spmf.gui.viewers.fastaviewer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.table.DefaultTableModel;

/* Copyright (c) 2008-2024 Philippe Fournier-Viger
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
 * Dialog for displaying the sequence length distribution of a FASTA dataset,
 * with a binned table view and a histogram chart.
 *
 * @author Philippe Fournier-Viger
 */
public class LengthDistributionDialog extends JDialog {

    /** Serial version UID for serialization */
    private static final long serialVersionUID = 1L;

    /** The dataset to analyze */
    private final FastaDataset dataset;

    /** Number of bins to use for the histogram */
    private static final int NUM_BINS = 10;

    /**
     * Constructs a length distribution dialog.
     *
     * @param parent  the parent frame
     * @param dataset the FASTA dataset to analyze
     */
    public LengthDistributionDialog(Frame parent, FastaDataset dataset) {
        super(parent, "Length Distribution", true);
        this.dataset = dataset;
        initComponents();
    }

    /**
     * Initializes and lays out all dialog components.
     */
    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        List<FastaSequenceEntry> sequences = dataset.getSequenceEntries();

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Distribution Table", createTablePanel(sequences));
        tabs.addTab("Histogram", createHistogramPanel(sequences));

        add(tabs, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel();
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        btnPanel.add(closeBtn);
        add(btnPanel, BorderLayout.SOUTH);

        setSize(700, 520);
        setLocationRelativeTo(getParent());
    }

    /**
     * Creates a table panel showing length ranges and their sequence counts.
     *
     * @param sequences the list of sequence entries
     * @return the configured table panel
     */
    private JPanel createTablePanel(List<FastaSequenceEntry> sequences) {
        JPanel panel = new JPanel(new BorderLayout());

        Map<Integer, Integer> distribution = buildRangeDistribution(sequences, 100);
        int total = sequences.size();

        String[] cols = {"Length Range", "Count", "Percentage"};
        DefaultTableModel model = new DefaultTableModel(cols, 0);

        new TreeMap<>(distribution).forEach((rangeStart, count) -> {
            double pct = total > 0 ? (double) count / total * 100.0 : 0.0;
            model.addRow(new Object[]{
                rangeStart + " - " + (rangeStart + 99),
                count,
                String.format("%.2f%%", pct)
            });
        });

        JTable table = new JTable(model);
        table.setEnabled(false);
        table.setAutoCreateRowSorter(true);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    /**
     * Creates a histogram panel for the length distribution.
     *
     * @param sequences the list of sequence entries
     * @return the configured histogram panel
     */
    private JPanel createHistogramPanel(List<FastaSequenceEntry> sequences) {
        // Determine min and max lengths to build NUM_BINS equal-width bins
        int minLen = Integer.MAX_VALUE;
        int maxLen = 0;
        for (FastaSequenceEntry e : sequences) {
            minLen = Math.min(minLen, e.getLength());
            maxLen = Math.max(maxLen, e.getLength());
        }
        if (minLen == Integer.MAX_VALUE) minLen = 0;

        int range = Math.max(1, maxLen - minLen);
        int binSize = (int) Math.ceil((double) range / NUM_BINS);

        int[] bins = new int[NUM_BINS];
        int[] binStarts = new int[NUM_BINS];
        for (int i = 0; i < NUM_BINS; i++) {
            binStarts[i] = minLen + i * binSize;
        }
        for (FastaSequenceEntry e : sequences) {
            int idx = Math.min((e.getLength() - minLen) / binSize, NUM_BINS - 1);
            bins[idx]++;
        }

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(new LengthHistogramPanel(bins, binStarts, binSize), BorderLayout.CENTER);
        return wrapper;
    }

    /**
     * Builds a grouped distribution map with a fixed range bucket size.
     *
     * @param sequences  the list of sequence entries
     * @param bucketSize the size of each bucket
     * @return a map from bucket start to count
     */
    private Map<Integer, Integer> buildRangeDistribution(
            List<FastaSequenceEntry> sequences, int bucketSize) {
        Map<Integer, Integer> dist = new TreeMap<>();
        for (FastaSequenceEntry e : sequences) {
            int bucket = (e.getLength() / bucketSize) * bucketSize;
            dist.merge(bucket, 1, Integer::sum);
        }
        return dist;
    }

    // -----------------------------------------------------------------------
    // Inner class: LengthHistogramPanel
    // -----------------------------------------------------------------------

    /**
     * A panel that renders a histogram for sequence length distribution.
     */
    private static class LengthHistogramPanel extends JPanel {

        /** Serial version UID for serialization */
        private static final long serialVersionUID = 1L;

        /** Bin counts */
        private final int[] bins;

        /** Starting length value for each bin */
        private final int[] binStarts;

        /** Width of each bin in bases */
        private final int binSize;

        /** Padding around the chart area */
        private static final int PADDING = 60;

        /** Bar color */
        private static final Color BAR_COLOR = new Color(60, 179, 113);

        /**
         * Constructs a length histogram panel.
         *
         * @param bins      the bin counts
         * @param binStarts the starting length of each bin
         * @param binSize   the width of each bin in base pairs
         */
        LengthHistogramPanel(int[] bins, int[] binStarts, int binSize) {
            this.bins = bins;
            this.binStarts = binStarts;
            this.binSize = binSize;
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(620, 400));
        }

        /**
         * Paints the histogram chart.
         *
         * @param g the graphics context
         */
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth() - 2 * PADDING;
            int height = getHeight() - 2 * PADDING;

            int maxCount = 1;
            for (int b : bins) maxCount = Math.max(maxCount, b);

            // Axes
            g2.setColor(Color.BLACK);
            g2.drawLine(PADDING, PADDING, PADDING, PADDING + height);
            g2.drawLine(PADDING, PADDING + height, PADDING + width, PADDING + height);

            int n = bins.length;
            int barWidth = (n > 0) ? width / n - 6 : width - 6;
            FontMetrics fm = g2.getFontMetrics();

            for (int i = 0; i < n; i++) {
                int barH = maxCount > 0 ? (int) ((double) bins[i] / maxCount * height) : 0;
                int x = PADDING + i * (barWidth + 6) + 3;
                int y = PADDING + height - barH;

                g2.setColor(BAR_COLOR);
                g2.fillRect(x, y, barWidth, barH);
                g2.setColor(Color.DARK_GRAY);
                g2.drawRect(x, y, barWidth, barH);

                // X-axis label
                String label = String.valueOf(binStarts[i]);
                g2.setColor(Color.BLACK);
                g2.setFont(g2.getFont().deriveFont(9f));
                fm = g2.getFontMetrics();
                g2.drawString(label, x + barWidth / 2 - fm.stringWidth(label) / 2,
                        PADDING + height + 14);

                // Count above bar
                if (bins[i] > 0) {
                    String cnt = String.valueOf(bins[i]);
                    g2.setFont(g2.getFont().deriveFont(10f));
                    fm = g2.getFontMetrics();
                    g2.drawString(cnt, x + barWidth / 2 - fm.stringWidth(cnt) / 2, y - 3);
                }
            }

            // Y-axis ticks
            g2.setFont(g2.getFont().deriveFont(10f));
            fm = g2.getFontMetrics();
            int tickStep = Math.max(1, maxCount / 5);
            for (int tick = 0; tick <= maxCount; tick += tickStep) {
                int ty = PADDING + height - (int) ((double) tick / maxCount * height);
                g2.setColor(Color.LIGHT_GRAY);
                g2.drawLine(PADDING, ty, PADDING + width, ty);
                g2.setColor(Color.BLACK);
                g2.drawLine(PADDING - 4, ty, PADDING, ty);
                String lbl = String.valueOf(tick);
                g2.drawString(lbl, PADDING - fm.stringWidth(lbl) - 6, ty + 4);
            }

            // Title
            g2.setColor(Color.BLACK);
            g2.setFont(g2.getFont().deriveFont(Font.BOLD, 13f));
            String title = "Sequence Length Distribution";
            g2.drawString(title,
                    PADDING + width / 2 - g2.getFontMetrics().stringWidth(title) / 2,
                    PADDING - 12);

            // X-axis title
            g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 11f));
            String xTitle = "Length (bp)";
            g2.drawString(xTitle,
                    PADDING + width / 2 - g2.getFontMetrics().stringWidth(xTitle) / 2,
                    PADDING + height + 30);
        }
    }
}