package ca.pfv.spmf.gui.viewers.fastaviewer;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * Dialog for translating DNA sequences in a FASTA dataset into protein sequences
 * using the standard genetic code, with options to copy or export the results.
 *
 * @author Philippe Fournier-Viger
 */
public class TranslationDialog extends JDialog {

    /** Serial version UID for serialization */
    private static final long serialVersionUID = 1L;

    /** The dataset whose sequences will be translated */
    private final FastaDataset dataset;

    /** Text area used to display translation results */
    private JTextArea resultArea;

    /** The translated protein sequence entries */
    private List<FastaSequenceEntry> proteinEntries;

    /** Standard genetic code codon table (DNA codons) */
    private static final Map<String, String> CODON_TABLE = new HashMap<>();

    static {
        // Standard genetic code
        CODON_TABLE.put("TTT", "F"); CODON_TABLE.put("TTC", "F");
        CODON_TABLE.put("TTA", "L"); CODON_TABLE.put("TTG", "L");
        CODON_TABLE.put("CTT", "L"); CODON_TABLE.put("CTC", "L");
        CODON_TABLE.put("CTA", "L"); CODON_TABLE.put("CTG", "L");
        CODON_TABLE.put("ATT", "I"); CODON_TABLE.put("ATC", "I");
        CODON_TABLE.put("ATA", "I"); CODON_TABLE.put("ATG", "M");
        CODON_TABLE.put("GTT", "V"); CODON_TABLE.put("GTC", "V");
        CODON_TABLE.put("GTA", "V"); CODON_TABLE.put("GTG", "V");
        CODON_TABLE.put("TCT", "S"); CODON_TABLE.put("TCC", "S");
        CODON_TABLE.put("TCA", "S"); CODON_TABLE.put("TCG", "S");
        CODON_TABLE.put("CCT", "P"); CODON_TABLE.put("CCC", "P");
        CODON_TABLE.put("CCA", "P"); CODON_TABLE.put("CCG", "P");
        CODON_TABLE.put("ACT", "T"); CODON_TABLE.put("ACC", "T");
        CODON_TABLE.put("ACA", "T"); CODON_TABLE.put("ACG", "T");
        CODON_TABLE.put("GCT", "A"); CODON_TABLE.put("GCC", "A");
        CODON_TABLE.put("GCA", "A"); CODON_TABLE.put("GCG", "A");
        CODON_TABLE.put("TAT", "Y"); CODON_TABLE.put("TAC", "Y");
        CODON_TABLE.put("TAA", "*"); CODON_TABLE.put("TAG", "*");
        CODON_TABLE.put("CAT", "H"); CODON_TABLE.put("CAC", "H");
        CODON_TABLE.put("CAA", "Q"); CODON_TABLE.put("CAG", "Q");
        CODON_TABLE.put("AAT", "N"); CODON_TABLE.put("AAC", "N");
        CODON_TABLE.put("AAA", "K"); CODON_TABLE.put("AAG", "K");
        CODON_TABLE.put("GAT", "D"); CODON_TABLE.put("GAC", "D");
        CODON_TABLE.put("GAA", "E"); CODON_TABLE.put("GAG", "E");
        CODON_TABLE.put("TGT", "C"); CODON_TABLE.put("TGC", "C");
        CODON_TABLE.put("TGA", "*"); CODON_TABLE.put("TGG", "W");
        CODON_TABLE.put("CGT", "R"); CODON_TABLE.put("CGC", "R");
        CODON_TABLE.put("CGA", "R"); CODON_TABLE.put("CGG", "R");
        CODON_TABLE.put("AGT", "S"); CODON_TABLE.put("AGC", "S");
        CODON_TABLE.put("AGA", "R"); CODON_TABLE.put("AGG", "R");
        CODON_TABLE.put("GGT", "G"); CODON_TABLE.put("GGC", "G");
        CODON_TABLE.put("GGA", "G"); CODON_TABLE.put("GGG", "G");
    }

    /**
     * Constructs a translation dialog.
     *
     * @param parent  the parent frame
     * @param dataset the FASTA dataset to translate
     */
    public TranslationDialog(Frame parent, FastaDataset dataset) {
        super(parent, "Translate to Protein", true);
        this.dataset = dataset;
        initComponents();
        translate();
    }

    /**
     * Initializes and lays out all dialog components.
     */
    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        add(new JLabel("Translated protein sequences (standard genetic code, frame +1):"),
                BorderLayout.NORTH);

        resultArea = new JTextArea();
        resultArea.setEditable(false);
        resultArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        add(new JScrollPane(resultArea), BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new GridLayout(1, 3, 8, 0));

        JButton copyBtn = new JButton("Copy All");
        copyBtn.addActionListener(e ->
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(resultArea.getText()), null));

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
     * Translates all sequences using frame +1 and populates the result area.
     */
    private void translate() {
        proteinEntries = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        for (FastaSequenceEntry entry : dataset.getSequenceEntries()) {
            String protein = translateSequence(entry.getSequence());
            FastaSequenceEntry pEntry = new FastaSequenceEntry(
                    entry.getHeader() + "_protein", protein);
            proteinEntries.add(pEntry);
            sb.append(">").append(pEntry.getHeader()).append("\n");
            sb.append(protein).append("\n\n");
        }

        resultArea.setText(sb.toString());
        resultArea.setCaretPosition(0);
    }

    /**
     * Translates a single DNA sequence into a protein sequence using the standard code.
     *
     * @param dna the DNA sequence to translate
     * @return the resulting amino acid sequence
     */
    private String translateSequence(String dna) {
        String upper = dna.toUpperCase().replaceAll("[^ATGC]", "N");
        StringBuilder protein = new StringBuilder();
        for (int i = 0; i + 2 < upper.length(); i += 3) {
            String codon = upper.substring(i, i + 3);
            String aa = CODON_TABLE.getOrDefault(codon, "X");
            if ("*".equals(aa)) break;
            protein.append(aa);
        }
        return protein.toString();
    }

    /**
     * Handles the export of translated sequences to a user-chosen file.
     */
    private void handleExport() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Export Protein Sequences");
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                dataset.exportToFile(fc.getSelectedFile().getAbsolutePath(), proteinEntries);
                JOptionPane.showMessageDialog(this,
                        "Exported " + proteinEntries.size() + " protein sequences.",
                        "Export", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                        "Error exporting: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}