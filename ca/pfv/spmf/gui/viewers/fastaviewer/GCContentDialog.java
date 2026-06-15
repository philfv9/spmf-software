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
 * Dialog for displaying GC content analysis of a FASTA dataset, showing
 * per-sequence GC values and an overall scatter/histogram visualization.
 *
 * @author Philippe Fournier-Viger
 */
public class GCContentDialog extends JDialog {

    /** Serial version UID for serialization */
    private static final long serialVersionUID = 1L;

    /** The dataset to analyze */
    private final FastaDataset dataset;

    /**
     * Constructs a GC content analysis dialog.
     *
     * @param parent  the parent frame
     * @param dataset the FASTA dataset to analyze
     */
    public GCContentDialog(Frame parent, FastaDataset dataset) {
        super(parent, "GC Content Analysis", true);
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
        tabs.addTab("Per-Sequence Table", createTablePanel(sequences));
        tabs.addTab("GC Histogram", createHistogramPanel(sequences));

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
     * Creates a table panel showing per-sequence GC content.
     *
     * @param sequences the list of sequence entries
     * @return the configured table panel
     */
    private JPanel createTablePanel(List<FastaSequenceEntry> sequences) {
        JPanel panel = new JPanel(new BorderLayout());

        String[] cols = {"#", "Header", "Length", "GC Count", "GC %"};
        DefaultTableModel model = new DefaultTableModel(cols, 0);

        for (int i = 0; i < sequences.size(); i++) {
            FastaSequenceEntry entry = sequences.get(i);
            String seq = entry.getSequence().toUpperCase();
            int gcCount = 0;
            for (char c : seq.toCharArray()) {
                if (c == 'G' || c == 'C') gcCount++;
            }
            double gcPct = seq.length() > 0 ? (double) gcCount / seq.length() * 100.0 : 0.0;
            model.addRow(new Object[]{
                i + 1,
                entry.getHeader(),
                String.format("%,d", entry.getLength()),
                String.format("%,d", gcCount),
                String.format("%.2f%%", gcPct)
            });
        }

        JTable table = new JTable(model);
        table.setEnabled(false);
        table.setAutoCreateRowSorter(true);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    /**
     * Creates a histogram panel showing the distribution of GC content values.
     *
     * @param sequences the list of sequence entries
     * @return the configured histogram panel
     */
    private JPanel createHistogramPanel(List<FastaSequenceEntry> sequences) {
        // Build 10 bins: 0-10%, 10-20%, ..., 90-100%
        int[] bins = new int[10];
        for (FastaSequenceEntry entry : sequences) {
            String seq = entry.getSequence().toUpperCase();
            int gc = 0;
            for (char c : seq.toCharArray()) {
                if (c == 'G' || c == 'C') gc++;
            }
            double pct = seq.length() > 0 ? (double) gc / seq.length() * 100.0 : 0.0;
            int bin = Math.min((int) (pct / 10), 9);
            bins[bin]++;
        }

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(new GCHistogramPanel(bins), BorderLayout.CENTER);
        return wrapper;
    }

    // -----------------------------------------------------------------------
    // Inner class: GCHistogramPanel
    // -----------------------------------------------------------------------

    /**
     * A panel that renders a histogram of GC content distribution.
     */
    private static class GCHistogramPanel extends javax.swing.JPanel {

        /** Serial version UID for serialization */
        private static final long serialVersionUID = 1L;

        /** Bin counts for 10 GC percentage ranges */
        private final int[] bins;

        /** Padding around the chart */
        private static final int PADDING = 55;

        /** Bar color for GC histogram */
        private static final Color BAR_COLOR = new Color(70, 130, 180);

        /**
         * Constructs a GC histogram panel.
         *
         * @param bins array of 10 bin counts representing GC% ranges
         */
        GCHistogramPanel(int[] bins) {
            this.bins = bins;
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(600, 380));
        }

        /**
         * Paints the histogram.
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

            int barWidth = width / bins.length - 6;
            FontMetrics fm = g2.getFontMetrics();

            for (int i = 0; i < bins.length; i++) {
                int barH = maxCount > 0 ? (int) ((double) bins[i] / maxCount * height) : 0;
                int x = PADDING + i * (barWidth + 6) + 3;
                int y = PADDING + height - barH;

                g2.setColor(BAR_COLOR);
                g2.fillRect(x, y, barWidth, barH);
                g2.setColor(Color.DARK_GRAY);
                g2.drawRect(x, y, barWidth, barH);

                // X-axis label
                String xLabel = (i * 10) + "-" + ((i + 1) * 10) + "%";
                g2.setColor(Color.BLACK);
                g2.setFont(g2.getFont().deriveFont(9f));
                int lx = x + barWidth / 2 - fm.stringWidth(xLabel) / 2;
                g2.drawString(xLabel, lx, PADDING + height + 14);

                // Count above bar
                if (bins[i] > 0) {
                    String countStr = String.valueOf(bins[i]);
                    g2.setFont(g2.getFont().deriveFont(10f));
                    g2.drawString(countStr, x + barWidth / 2 - fm.stringWidth(countStr) / 2, y - 3);
                }
            }

            // Y-axis ticks
            g2.setFont(g2.getFont().deriveFont(10f));
            fm = g2.getFontMetrics();
            for (int tick = 0; tick <= maxCount; tick += Math.max(1, maxCount / 5)) {
                int ty = PADDING + height - (int) ((double) tick / maxCount * height);
                g2.setColor(Color.LIGHT_GRAY);
                g2.drawLine(PADDING, ty, PADDING + width, ty);
                g2.setColor(Color.BLACK);
                g2.drawLine(PADDING - 4, ty, PADDING, ty);
                String label = String.valueOf(tick);
                g2.drawString(label, PADDING - fm.stringWidth(label) - 6, ty + 4);
            }

            // Title
            g2.setColor(Color.BLACK);
            g2.setFont(g2.getFont().deriveFont(Font.BOLD, 13f));
            String title = "GC Content Distribution";
            g2.drawString(title, PADDING + width / 2 - g2.getFontMetrics().stringWidth(title) / 2,
                    PADDING - 12);
        }
    }
}