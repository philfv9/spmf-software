package ca.pfv.spmf.gui.viewers.fastaviewer;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.BufferedWriter;
import java.io.FileWriter;
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
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
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
 * Dialog for finding Open Reading Frames (ORFs) within the sequences of a FASTA
 * dataset, supporting all six reading frames and a configurable minimum ORF length.
 *
 * @author Philippe Fournier-Viger
 */
public class ORFFinderDialog extends JDialog {

    /** Serial version UID for serialization */
    private static final long serialVersionUID = 1L;

    /** The dataset to search for ORFs */
    private final FastaDataset dataset;

    /** Table model used to display found ORFs */
    private DefaultTableModel tableModel;

    /** Spinner controlling the minimum ORF length in codons */
    private JSpinner minLengthSpinner;

    /** The list of ORF result records found during the last search */
    private final List<ORFRecord> foundORFs = new ArrayList<>();

    /**
     * Constructs an ORF finder dialog.
     *
     * @param parent  the parent frame
     * @param dataset the FASTA dataset to search
     */
    public ORFFinderDialog(Frame parent, FastaDataset dataset) {
        super(parent, "Find Open Reading Frames (ORFs)", true);
        this.dataset = dataset;
        initComponents();
    }

    /**
     * Initializes and lays out all dialog components.
     */
    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Parameter panel
        JPanel paramPanel = new JPanel();
        paramPanel.add(new JLabel("Minimum ORF length (codons):"));
        minLengthSpinner = new JSpinner(new SpinnerNumberModel(30, 1, 10000, 1));
        paramPanel.add(minLengthSpinner);

        JButton findBtn = new JButton("Find ORFs");
        findBtn.addActionListener(e -> findORFs());
        paramPanel.add(findBtn);
        add(paramPanel, BorderLayout.NORTH);

        // Results table
        String[] cols = {"Sequence", "Frame", "Start", "End", "Length (aa)", "ORF Sequence"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(tableModel);
        table.setAutoCreateRowSorter(true);
        table.setFont(new Font("Monospaced", Font.PLAIN, 11));
        add(new JScrollPane(table), BorderLayout.CENTER);

        // Buttons
        JPanel btnPanel = new JPanel(new GridLayout(1, 3, 8, 0));

        JButton copyBtn = new JButton("Copy Table");
        copyBtn.addActionListener(e -> copyTable());

        JButton exportBtn = new JButton("Export...");
        exportBtn.addActionListener(e -> exportTable());

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());

        btnPanel.add(copyBtn);
        btnPanel.add(exportBtn);
        btnPanel.add(closeBtn);
        add(btnPanel, BorderLayout.SOUTH);

        setSize(900, 550);
        setLocationRelativeTo(getParent());
    }

    /**
     * Searches all sequences for ORFs in all six reading frames and populates the results table.
     */
    private void findORFs() {
        tableModel.setRowCount(0);
        foundORFs.clear();

        int minLength = (Integer) minLengthSpinner.getValue();

        for (FastaSequenceEntry entry : dataset.getSequenceEntries()) {
            String seq = entry.getSequence().toUpperCase();
            String seqRC = reverseComplement(seq);

            // Forward frames (+1, +2, +3)
            for (int frame = 0; frame < 3; frame++) {
                findORFsInFrame(entry.getHeader(), seq, frame, true, minLength);
            }
            // Reverse frames (-1, -2, -3)
            for (int frame = 0; frame < 3; frame++) {
                findORFsInFrame(entry.getHeader(), seqRC, frame, false, minLength);
            }
        }

        JOptionPane.showMessageDialog(this,
                "Found " + foundORFs.size() + " ORF(s).",
                "ORF Search Complete", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Searches a single strand for ORFs starting from a given frame offset.
     *
     * @param seqHeader the header of the parent sequence
     * @param seq       the nucleotide sequence to search (must be uppercase)
     * @param frame     the reading frame offset (0, 1, or 2)
     * @param forward   true if this is a forward strand, false for reverse complement
     * @param minLength the minimum number of codons for a valid ORF
     */
    private void findORFsInFrame(String seqHeader, String seq,
                                  int frame, boolean forward, int minLength) {
        int i = frame;
        while (i + 2 < seq.length()) {
            String codon = seq.substring(i, i + 3);
            if ("ATG".equals(codon)) {
                int start = i;
                int end = start;
                StringBuilder orfSeq = new StringBuilder("ATG");
                int j = i + 3;
                boolean stopFound = false;
                while (j + 2 < seq.length()) {
                    String nextCodon = seq.substring(j, j + 3);
                    if ("TAA".equals(nextCodon) || "TAG".equals(nextCodon) || "TGA".equals(nextCodon)) {
                        end = j + 3;
                        stopFound = true;
                        break;
                    }
                    orfSeq.append(nextCodon);
                    j += 3;
                }
                if (!stopFound) end = j;

                int orfLenCodons = orfSeq.length() / 3;
                if (orfLenCodons >= minLength) {
                    String frameLabel = (forward ? "+" : "-") + (frame + 1);
                    ORFRecord rec = new ORFRecord(seqHeader, frameLabel, start + 1, end,
                            orfLenCodons, orfSeq.toString());
                    foundORFs.add(rec);
                    tableModel.addRow(new Object[]{
                        seqHeader, frameLabel,
                        start + 1, end, orfLenCodons,
                        orfSeq.length() > 40 ? orfSeq.substring(0, 40) + "..." : orfSeq.toString()
                    });
                }
                i = i + 3;
            } else {
                i += 3;
            }
        }
    }

    /**
     * Copies the ORF results table to the system clipboard as tab-separated text.
     */
    private void copyTable() {
        StringBuilder sb = new StringBuilder();
        sb.append("Sequence\tFrame\tStart\tEnd\tLength(aa)\tSequence\n");
        for (ORFRecord r : foundORFs) {
            sb.append(r.sequenceHeader).append("\t")
              .append(r.frame).append("\t")
              .append(r.start).append("\t")
              .append(r.end).append("\t")
              .append(r.lengthCodons).append("\t")
              .append(r.orfSequence).append("\n");
        }
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(sb.toString()), null);
    }

    /**
     * Exports the ORF results to a user-chosen tab-separated text file.
     */
    private void exportTable() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Export ORF Results");
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (BufferedWriter bw = new BufferedWriter(
                    new FileWriter(fc.getSelectedFile()))) {
                bw.write("Sequence\tFrame\tStart\tEnd\tLength(aa)\tSequence");
                bw.newLine();
                for (ORFRecord r : foundORFs) {
                    bw.write(r.sequenceHeader + "\t" + r.frame + "\t" +
                             r.start + "\t" + r.end + "\t" +
                             r.lengthCodons + "\t" + r.orfSequence);
                    bw.newLine();
                }
                JOptionPane.showMessageDialog(this,
                        "Exported " + foundORFs.size() + " ORF(s).",
                        "Export", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                        "Error: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Computes the reverse complement of a DNA sequence.
     *
     * @param seq the input uppercase DNA sequence
     * @return the reverse complement sequence
     */
    private String reverseComplement(String seq) {
        StringBuilder sb = new StringBuilder(seq.length());
        for (int i = seq.length() - 1; i >= 0; i--) {
            char c = seq.charAt(i);
            switch (c) {
                case 'A': sb.append('T'); break;
                case 'T': sb.append('A'); break;
                case 'G': sb.append('C'); break;
                case 'C': sb.append('G'); break;
                default:  sb.append('N'); break;
            }
        }
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // Inner class: ORFRecord
    // -----------------------------------------------------------------------

    /**
     * Immutable data record holding information about a single found ORF.
     */
    private static class ORFRecord {

        /** Header of the parent sequence */
        final String sequenceHeader;

        /** Reading frame label (e.g. "+1", "-2") */
        final String frame;

        /** Start position (1-based) of the ORF */
        final int start;

        /** End position (1-based) of the ORF */
        final int end;

        /** Length of the ORF in codons (amino acids) */
        final int lengthCodons;

        /** The nucleotide sequence of the ORF */
        final String orfSequence;

        /**
         * Constructs a new ORF record.
         *
         * @param sequenceHeader the parent sequence header
         * @param frame          the reading frame label
         * @param start          the start position (1-based)
         * @param end            the end position (1-based)
         * @param lengthCodons   the ORF length in codons
         * @param orfSequence    the ORF nucleotide sequence
         */
        ORFRecord(String sequenceHeader, String frame, int start, int end,
                  int lengthCodons, String orfSequence) {
            this.sequenceHeader = sequenceHeader;
            this.frame = frame;
            this.start = start;
            this.end = end;
            this.lengthCodons = lengthCodons;
            this.orfSequence = orfSequence;
        }
    }
}