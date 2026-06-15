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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
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
 * Dialog for displaying nucleotide composition analysis of a FASTA dataset,
 * including a table of nucleotide frequencies and a bar chart visualization.
 *
 * @author Philippe Fournier-Viger
 */
public class NucleotideCompositionDialog extends JDialog {

    /** Serial version UID for serialization */
    private static final long serialVersionUID = 1L;

    /** The dataset to analyze */
    private final FastaDataset dataset;

    /** Colors used for the bar chart bars */
    private static final Color[] BAR_COLORS = {
        new Color(70, 130, 180),   // steel blue  – A
        new Color(60, 179, 113),   // medium sea green – T/U
        new Color(210, 105, 30),   // chocolate – G
        new Color(220, 20, 60),    // crimson – C
        new Color(128, 128, 128),  // grey – others
    };

    /**
     * Constructs a nucleotide composition dialog.
     *
     * @param parent  the parent frame
     * @param dataset the FASTA dataset to analyze
     */
    public NucleotideCompositionDialog(Frame parent, FastaDataset dataset) {
        super(parent, "Nucleotide Composition", true);
        this.dataset = dataset;
        initComponents();
    }

    /**
     * Initializes and lays out all dialog components.
     */
    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        Map<Character, Integer> composition = dataset.getNucleotideComposition();
        long total = composition.values().stream().mapToLong(Integer::longValue).sum();

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Table", createTablePanel(composition, total));
        tabs.addTab("Bar Chart", createChartPanel(composition, total));

        add(tabs, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel();
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        btnPanel.add(closeBtn);
        add(btnPanel, BorderLayout.SOUTH);

        setSize(600, 500);
        setLocationRelativeTo(getParent());
    }

    /**
     * Creates the table panel showing nucleotide counts and percentages.
     *
     * @param composition the nucleotide composition map
     * @param total       the total number of bases
     * @return the configured table panel
     */
    private JPanel createTablePanel(Map<Character, Integer> composition, long total) {
        JPanel panel = new JPanel(new BorderLayout());

        String[] cols = {"Nucleotide", "Count", "Percentage"};
        DefaultTableModel model = new DefaultTableModel(cols, 0);

        new TreeMap<>(composition).forEach((ch, count) -> {
            double pct = total > 0 ? (double) count / total * 100.0 : 0.0;
            model.addRow(new Object[]{
                String.valueOf(ch),
                String.format("%,d", count),
                String.format("%.2f%%", pct)
            });
        });

        JTable table = new JTable(model);
        table.setEnabled(false);
        table.setFont(new Font("Monospaced", Font.PLAIN, 12));
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    /**
     * Creates a panel containing a bar chart of nucleotide composition.
     *
     * @param composition the nucleotide composition map
     * @param total       the total number of bases
     * @return the configured chart panel
     */
    private JPanel createChartPanel(Map<Character, Integer> composition, long total) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(new BarChartPanel(composition, total), BorderLayout.CENTER);
        return wrapper;
    }

    // -----------------------------------------------------------------------
    // Inner class: BarChartPanel
    // -----------------------------------------------------------------------

    /**
     * A simple bar chart panel that renders nucleotide frequency bars.
     */
    private static class BarChartPanel extends JPanel {

        /** Serial version UID for serialization */
        private static final long serialVersionUID = 1L;

        /** Sorted nucleotide labels */
        private final List<Character> keys;

        /** Percentage value for each nucleotide */
        private final List<Double> percentages;

        /** Padding around the chart area */
        private static final int PADDING = 50;

        /**
         * Constructs a bar chart panel from composition data.
         *
         * @param composition the nucleotide composition map
         * @param total       the total base count
         */
        BarChartPanel(Map<Character, Integer> composition, long total) {
            keys = new ArrayList<>(new TreeMap<>(composition).keySet());
            percentages = new ArrayList<>();
            for (Character ch : keys) {
                double pct = total > 0 ? (double) composition.get(ch) / total * 100.0 : 0.0;
                percentages.add(pct);
            }
            setPreferredSize(new Dimension(500, 350));
            setBackground(Color.WHITE);
        }

        /**
         * Paints the bar chart.
         *
         * @param g the graphics context
         */
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (keys.isEmpty()) return;

            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth() - 2 * PADDING;
            int height = getHeight() - 2 * PADDING;
            int n = keys.size();
            int barWidth = width / n - 10;

            g2.setColor(Color.BLACK);
            // Y axis
            g2.drawLine(PADDING, PADDING, PADDING, PADDING + height);
            // X axis
            g2.drawLine(PADDING, PADDING + height, PADDING + width, PADDING + height);

            FontMetrics fm = g2.getFontMetrics();

            for (int i = 0; i < n; i++) {
                double pct = percentages.get(i);
                int barHeight = (int) (pct / 100.0 * height);
                int x = PADDING + i * (barWidth + 10) + 5;
                int y = PADDING + height - barHeight;

                Color color = BAR_COLORS[i % BAR_COLORS.length];
                g2.setColor(color);
                g2.fillRect(x, y, barWidth, barHeight);
                g2.setColor(Color.DARK_GRAY);
                g2.drawRect(x, y, barWidth, barHeight);

                // Label below axis
                String label = String.valueOf(keys.get(i));
                int labelX = x + barWidth / 2 - fm.stringWidth(label) / 2;
                g2.drawString(label, labelX, PADDING + height + 15);

                // Percentage above bar
                String pctStr = String.format("%.1f%%", pct);
                int pctX = x + barWidth / 2 - fm.stringWidth(pctStr) / 2;
                g2.drawString(pctStr, pctX, y - 3);
            }

            // Y-axis labels
            g2.setColor(Color.BLACK);
            for (int tick = 0; tick <= 100; tick += 20) {
                int ty = PADDING + height - (int) (tick / 100.0 * height);
                g2.drawLine(PADDING - 4, ty, PADDING, ty);
                String label = tick + "%";
                g2.drawString(label, PADDING - fm.stringWidth(label) - 6, ty + 4);
            }

            // Title
            g2.setFont(g2.getFont().deriveFont(Font.BOLD, 14f));
            String title = "Nucleotide Composition";
            g2.drawString(title, PADDING + width / 2 - fm.stringWidth(title) / 2, PADDING - 10);
        }
    }
}