package ca.pfv.spmf.gui.viewers.fastaviewer;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
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
 * Dialog for computing and displaying the reverse complement of all sequences
 * in a FASTA dataset, with options to copy or export the results.
 *
 * @author Philippe Fournier-Viger
 */
public class ReverseComplementDialog extends JDialog {

    /** Serial version UID for serialization */
    private static final long serialVersionUID = 1L;

    /** The dataset whose sequences will be reverse-complemented */
    private final FastaDataset dataset;

    /** Text area used to display reverse complement results */
    private JTextArea resultArea;

    /** The computed reverse-complement entries */
    private List<FastaSequenceEntry> rcEntries;

    /**
     * Constructs a reverse complement dialog.
     *
     * @param parent  the parent frame
     * @param dataset the FASTA dataset to process
     */
    public ReverseComplementDialog(Frame parent, FastaDataset dataset) {
        super(parent, "Reverse Complement", true);
        this.dataset = dataset;
        initComponents();
        compute();
    }

    /**
     * Initializes and lays out all dialog components.
     */
    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        add(new JLabel("Reverse complement of all sequences:"), BorderLayout.NORTH);

        resultArea = new JTextArea();
        resultArea.setEditable(false);
        resultArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        add(new JScrollPane(resultArea), BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new GridLayout(1, 3, 8, 0));

        JButton copyBtn = new JButton("Copy All");
        copyBtn.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(resultArea.getText()), null);
        });

        JButton exportBtn = new JButton("Export...");
        exportBtn.addActionListener(e -> handleExport());

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());

        btnPanel.add(copyBtn);
        btnPanel.add(exportBtn);
        btnPanel.add(closeBtn);

        add(btnPanel, BorderLayout.SOUTH);

        setSize(650, 500);
        setLocationRelativeTo(getParent());
    }

    /**
     * Computes the reverse complement for every sequence and populates the result area.
     */
    private void compute() {
        rcEntries = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        for (FastaSequenceEntry entry : dataset.getSequenceEntries()) {
            String rc = reverseComplement(entry.getSequence());
            FastaSequenceEntry rcEntry = new FastaSequenceEntry(
                    entry.getHeader() + "_RC", rc);
            rcEntries.add(rcEntry);
            sb.append(">").append(rcEntry.getHeader()).append("\n");
            sb.append(rc).append("\n\n");
        }

        resultArea.setText(sb.toString());
        resultArea.setCaretPosition(0);
    }

    /**
     * Handles the export action, writing reverse-complement sequences to a file.
     */
    private void handleExport() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Export Reverse Complement Sequences");
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                dataset.exportToFile(fc.getSelectedFile().getAbsolutePath(), rcEntries);
                JOptionPane.showMessageDialog(this,
                        "Exported " + rcEntries.size() + " sequences.",
                        "Export", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                        "Error exporting: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Computes the reverse complement of a DNA sequence.
     *
     * @param sequence the input DNA sequence
     * @return the reverse complement sequence
     */
    private String reverseComplement(String sequence) {
        StringBuilder sb = new StringBuilder(sequence.length());
        for (int i = sequence.length() - 1; i >= 0; i--) {
            sb.append(complement(sequence.charAt(i)));
        }
        return sb.toString();
    }

    /**
     * Returns the complement of a single nucleotide character.
     *
     * @param base the nucleotide character (A, T, G, C or ambiguous)
     * @return the complementary nucleotide character
     */
    private char complement(char base) {
        switch (Character.toUpperCase(base)) {
            case 'A': return Character.isUpperCase(base) ? 'T' : 't';
            case 'T': return Character.isUpperCase(base) ? 'A' : 'a';
            case 'U': return Character.isUpperCase(base) ? 'A' : 'a';
            case 'G': return Character.isUpperCase(base) ? 'C' : 'c';
            case 'C': return Character.isUpperCase(base) ? 'G' : 'g';
            case 'N': return base;
            case 'R': return Character.isUpperCase(base) ? 'Y' : 'y';
            case 'Y': return Character.isUpperCase(base) ? 'R' : 'r';
            case 'S': return base;
            case 'W': return base;
            case 'K': return Character.isUpperCase(base) ? 'M' : 'm';
            case 'M': return Character.isUpperCase(base) ? 'K' : 'k';
            case 'B': return Character.isUpperCase(base) ? 'V' : 'v';
            case 'D': return Character.isUpperCase(base) ? 'H' : 'h';
            case 'H': return Character.isUpperCase(base) ? 'D' : 'd';
            case 'V': return Character.isUpperCase(base) ? 'B' : 'b';
            default:  return base;
        }
    }
}