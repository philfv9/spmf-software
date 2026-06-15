package ca.pfv.spmf.gui.viewers.fastaviewer;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Map;

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
 * Dialog for displaying detailed statistics about the FASTA dataset.
 * 
 * @author Philippe Fournier-Viger
 */
public class StatisticsDialog extends JDialog {
    
    /** Serial version UID */
    private static final long serialVersionUID = 1L;
    
    /** The dataset to analyze */
    private final FastaDataset dataset;

    /**
     * Constructs a statistics dialog.
     * 
     * @param parent the parent frame
     * @param dataset the dataset to analyze
     */
    public StatisticsDialog(Frame parent, FastaDataset dataset) {
        super(parent, "Detailed Statistics", true);
        this.dataset = dataset;
        initComponents();
    }

    /**
     * Initializes the dialog components.
     */
    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        
        JTabbedPane tabbedPane = new JTabbedPane();
        
        // General statistics tab
        tabbedPane.addTab("General", createGeneralPanel());
        
        // Composition tab
        tabbedPane.addTab("Composition", createCompositionPanel());
        
        // Length statistics tab
        tabbedPane.addTab("Length", createLengthPanel());
        
        add(tabbedPane, BorderLayout.CENTER);
        
        // Button panel
        JPanel buttonPanel = new JPanel();
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        buttonPanel.add(closeButton);
        add(buttonPanel, BorderLayout.SOUTH);
        
        setSize(600, 500);
        setLocationRelativeTo(getParent());
    }

    /**
     * Creates the general statistics panel.
     * 
     * @return the panel
     */
    private JPanel createGeneralPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        double[] stats = dataset.getLengthStatistics();
        
        int row = 0;
        addStatRow(panel, gbc, row++, "Total Sequences:", 
            String.valueOf(dataset.getSequenceCount()));
        addStatRow(panel, gbc, row++, "Total Bases:", 
            String.format("%,d", (long)stats[3]));
        addStatRow(panel, gbc, row++, "Minimum Length:", 
            String.format("%,d", (long)stats[0]));
        addStatRow(panel, gbc, row++, "Maximum Length:", 
            String.format("%,d", (long)stats[1]));
        addStatRow(panel, gbc, row++, "Average Length:", 
            String.format("%.2f", stats[2]));
        
        // Calculate GC content
        Map<Character, Integer> composition = dataset.getNucleotideComposition();
        int gcCount = composition.getOrDefault('G', 0) + composition.getOrDefault('C', 0) +
                     composition.getOrDefault('g', 0) + composition.getOrDefault('c', 0);
        double gcContent = stats[3] > 0 ? (double) gcCount / stats[3] * 100 : 0;
        addStatRow(panel, gbc, row++, "GC Content:", 
            String.format("%.2f%%", gcContent));
        
        return panel;
    }

    /**
     * Creates the composition statistics panel.
     * 
     * @return the panel
     */
    private JPanel createCompositionPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        Map<Character, Integer> composition = dataset.getNucleotideComposition();
        double[] stats = dataset.getLengthStatistics();
        long totalBases = (long) stats[3];
        
        String[] columnNames = {"Nucleotide", "Count", "Percentage"};
        DefaultTableModel model = new DefaultTableModel(columnNames, 0);
        
        composition.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                double percentage = totalBases > 0 ? 
                    (double) entry.getValue() / totalBases * 100 : 0;
                model.addRow(new Object[]{
                    entry.getKey(),
                    String.format("%,d", entry.getValue()),
                    String.format("%.2f%%", percentage)
                });
            });
        
        JTable table = new JTable(model);
        table.setEnabled(false);
        JScrollPane scrollPane = new JScrollPane(table);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }

    /**
     * Creates the length statistics panel.
     * 
     * @return the panel
     */
    private JPanel createLengthPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // Create histogram data
        Map<Integer, Integer> lengthDistribution = calculateLengthDistribution();
        
        String[] columnNames = {"Length Range", "Count", "Percentage"};
        DefaultTableModel model = new DefaultTableModel(columnNames, 0);
        
        int totalSequences = dataset.getSequenceCount();
        
        lengthDistribution.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                int rangeStart = entry.getKey();
                int rangeEnd = rangeStart + 99;
                double percentage = totalSequences > 0 ? 
                    (double) entry.getValue() / totalSequences * 100 : 0;
                model.addRow(new Object[]{
                    rangeStart + "-" + rangeEnd,
                    entry.getValue(),
                    String.format("%.2f%%", percentage)
                });
            });
        
        JTable table = new JTable(model);
        table.setEnabled(false);
        JScrollPane scrollPane = new JScrollPane(table);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }

    /**
     * Calculates the length distribution grouped by ranges.
     * 
     * @return map of range start to count
     */
    private Map<Integer, Integer> calculateLengthDistribution() {
        Map<Integer, Integer> distribution = new java.util.TreeMap<>();
        
        for (FastaSequenceEntry entry : dataset.getSequenceEntries()) {
            int length = entry.getLength();
            int rangeStart = (length / 100) * 100;
            distribution.merge(rangeStart, 1, Integer::sum);
        }
        
        return distribution;
    }

    /**
     * Adds a statistics row to the panel.
     * 
     * @param panel the panel
     * @param gbc the grid bag constraints
     * @param row the row number
     * @param label the label text
     * @param value the value text
     */
    private void addStatRow(JPanel panel, GridBagConstraints gbc, 
                           int row, String label, String value) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        JLabel labelComp = new JLabel(label);
        labelComp.setFont(labelComp.getFont().deriveFont(Font.BOLD));
        panel.add(labelComp, gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(new JLabel(value), gbc);
    }
}