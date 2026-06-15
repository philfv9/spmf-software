package ca.pfv.spmf.gui.viewers.fastaviewer;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;

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
 * Dialog for displaying detailed information about a single FASTA sequence entry,
 * including its header, full sequence, length, and GC content.
 *
 * @author Philippe Fournier-Viger
 */
public class SequenceDetailsDialog extends JDialog {

    /** Serial version UID for serialization */
    private static final long serialVersionUID = 1L;

    /** The sequence entry being displayed */
    private final FastaSequenceEntry entry;

    /**
     * Constructs a new sequence details dialog.
     *
     * @param parent the parent frame
     * @param entry  the sequence entry to display
     */
    public SequenceDetailsDialog(JFrame parent, FastaSequenceEntry entry) {
        super(parent, "Sequence Details", true);
        this.entry = entry;
        initComponents();
    }

    /**
     * Initializes and lays out all dialog components.
     */
    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Details", createDetailsPanel());
        tabbedPane.addTab("Sequence", createSequencePanel());
        tabbedPane.addTab("Statistics", createStatsPanel());

        add(tabbedPane, BorderLayout.CENTER);
        add(createButtonPanel(), BorderLayout.SOUTH);

        setSize(600, 450);
        setLocationRelativeTo(getParent());
    }

    /**
     * Creates the details panel showing header and basic metadata.
     *
     * @return the configured details panel
     */
    private JPanel createDetailsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.anchor = GridBagConstraints.WEST;

        addRow(panel, gbc, 0, "Header:", entry.getHeader());
        addRow(panel, gbc, 1, "Length:", String.format("%,d bp", entry.getLength()));
        addRow(panel, gbc, 2, "GC Content:", String.format("%.2f%%", calculateGC()));
        addRow(panel, gbc, 3, "AT Content:", String.format("%.2f%%", 100.0 - calculateGC()));

        return panel;
    }

    /**
     * Creates the panel displaying the full sequence in a scrollable text area.
     *
     * @return the configured sequence panel
     */
    private JPanel createSequencePanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        JTextArea seqArea = new JTextArea(entry.getSequence());
        seqArea.setEditable(false);
        seqArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        seqArea.setLineWrap(true);
        seqArea.setWrapStyleWord(false);

        JScrollPane scrollPane = new JScrollPane(seqArea);
        panel.add(scrollPane, BorderLayout.CENTER);

        JButton copyBtn = new JButton("Copy Sequence");
        copyBtn.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(entry.getSequence()), null);
        });
        JPanel btnRow = new JPanel();
        btnRow.add(copyBtn);
        panel.add(btnRow, BorderLayout.SOUTH);

        return panel;
    }

    /**
     * Creates the statistics panel with nucleotide counts and composition.
     *
     * @return the configured statistics panel
     */
    private JPanel createStatsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.anchor = GridBagConstraints.WEST;

        String seq = entry.getSequence().toUpperCase();
        int total = seq.length();

        int aCount = countChar(seq, 'A');
        int tCount = countChar(seq, 'T');
        int gCount = countChar(seq, 'G');
        int cCount = countChar(seq, 'C');
        int nCount = total - aCount - tCount - gCount - cCount;

        int row = 0;
        addRow(panel, gbc, row++, "A count:", formatCount(aCount, total));
        addRow(panel, gbc, row++, "T count:", formatCount(tCount, total));
        addRow(panel, gbc, row++, "G count:", formatCount(gCount, total));
        addRow(panel, gbc, row++, "C count:", formatCount(cCount, total));
        addRow(panel, gbc, row++, "Other/N count:", formatCount(nCount, total));
        addRow(panel, gbc, row++, "GC Content:", String.format("%.2f%%", calculateGC()));

        return panel;
    }

    /**
     * Creates the button panel at the bottom of the dialog.
     *
     * @return the configured button panel
     */
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel();

        JButton copyFastaBtn = new JButton("Copy as FASTA");
        copyFastaBtn.addActionListener(e -> {
            String fasta = ">" + entry.getHeader() + "\n" + entry.getSequence();
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(fasta), null);
        });

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());

        panel.add(copyFastaBtn);
        panel.add(closeBtn);
        return panel;
    }

    /**
     * Adds a label/value row to a GridBagLayout panel.
     *
     * @param panel the panel to add the row to
     * @param gbc   the grid bag constraints
     * @param row   the row index
     * @param label the label text
     * @param value the value text
     */
    private void addRow(JPanel panel, GridBagConstraints gbc,
                        int row, String label, String value) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        JLabel lbl = new JLabel(label);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
        panel.add(lbl, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(new JLabel(value), gbc);
    }

    /**
     * Calculates the GC content percentage of the sequence.
     *
     * @return GC content as a percentage
     */
    private double calculateGC() {
        String seq = entry.getSequence().toUpperCase();
        int gc = 0;
        for (char c : seq.toCharArray()) {
            if (c == 'G' || c == 'C') gc++;
        }
        return seq.length() > 0 ? (double) gc / seq.length() * 100.0 : 0.0;
    }

    /**
     * Counts occurrences of a character in a string.
     *
     * @param seq the string to search
     * @param ch  the character to count
     * @return the number of occurrences
     */
    private int countChar(String seq, char ch) {
        int count = 0;
        for (char c : seq.toCharArray()) {
            if (c == ch) count++;
        }
        return count;
    }

    /**
     * Formats a count with its percentage for display.
     *
     * @param count the raw count
     * @param total the total length
     * @return formatted string with count and percentage
     */
    private String formatCount(int count, int total) {
        double pct = total > 0 ? (double) count / total * 100.0 : 0.0;
        return String.format("%,d (%.2f%%)", count, pct);
    }
}