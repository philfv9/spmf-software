package ca.pfv.spmf.gui.guided_interface;

/*
 * Copyright (c) 2008-2022 Philippe Fournier-Viger
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
 * Do not remove copyright and license.
 */


import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import ca.pfv.spmf.algorithmmanager.AlgorithmManager;
import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.gui.parameterselectionpanel.ParameterSelectionPanel;

/**
 * Modal dialog that lets the user pick a {@link AlgorithmType#DATA_PROCESSOR}
 * algorithm (e.g. a filter, converter, or cleaner for a dataset), configure
 * its parameters, choose an output file, and run it on a given input file.
 *
 * <p>
 * This mirrors {@link GenerateDatasetDialog} but operates on an existing
 * input file (supplied by the caller, typically the file currently selected
 * in {@link DataSelectionPanel}) instead of creating one from scratch.
 * Keeping this in its own dialog — rather than embedding it directly in the
 * "Select data" tab — keeps that tab compact.
 * </p>
 *
 * <p>
 * After a successful run, the dialog closes automatically and the caller can
 * retrieve the resulting file path via {@link #getProcessedFilePath()}.
 * </p>
 *
 * @author Philippe Fournier-Viger
 */
@SuppressWarnings("serial")
public class ProcessDatasetDialog extends JDialog {

    private static final Font LABEL_FONT_BOLD = new Font(Font.DIALOG, Font.BOLD, 12);

    private final String inputFilePath;

    private JComboBox<String> comboProcessors;
    private ParameterSelectionPanel paramPanel;
    private JTextField textFieldOutput;
    private JButton buttonChooseOutput;
    private JButton buttonProcess;
    private JButton buttonCancel;

    private final List<DescriptionOfAlgorithm> processorDescriptions = new ArrayList<>();

    /** Set once processing succeeds; null otherwise. */
    private String processedFilePath = null;

    /** True while a processor is running in the background. */
    private boolean processing = false;

    /**
     * Creates the dialog.
     *
     * @param owner            the owning frame
     * @param preselected      the processor algorithm to preselect (may be
     *                         null, in which case the first one found is used)
     * @param inputFilePath    the file to process (shown read-only)
     */
    public ProcessDatasetDialog(Frame owner, DescriptionOfAlgorithm preselected, String inputFilePath) {
        super(owner, "Process dataset", true);
        this.inputFilePath = inputFilePath;

        setLayout(new GridBagLayout());
        buildUI();
        loadProcessors(preselected);

        setMinimumSize(new Dimension(560, 300));
        pack();
        setLocationRelativeTo(owner);
    }

    private void buildUI() {
        GridBagConstraints c;

        // Row 0 — input file (read-only, for reference)
        JLabel lblInput = new JLabel("Input file:");
        lblInput.setFont(LABEL_FONT_BOLD);
        JTextField textFieldInput = new JTextField(inputFilePath == null ? "" : inputFilePath);
        textFieldInput.setEditable(false);

        c = gbc(0, 0);
        add(lblInput, c);
        c = gbc(1, 0); c.weightx = 1; c.gridwidth = 2; c.fill = GridBagConstraints.HORIZONTAL;
        add(textFieldInput, c);

        // Row 1 — processor chooser
        JLabel lblAlgo = new JLabel("Processing tool:");
        lblAlgo.setFont(LABEL_FONT_BOLD);
        comboProcessors = new JComboBox<>();
        comboProcessors.addActionListener(e -> onProcessorSelectionChanged());

        c = gbc(0, 1);
        add(lblAlgo, c);
        c = gbc(1, 1); c.weightx = 1; c.gridwidth = 2; c.fill = GridBagConstraints.HORIZONTAL;
        add(comboProcessors, c);

        // Row 2 — parameters
        JLabel lblParam = new JLabel("Parameters:");
        lblParam.setFont(LABEL_FONT_BOLD);
        paramPanel = new ParameterSelectionPanel(null);

        c = gbc(0, 2); c.anchor = GridBagConstraints.NORTHEAST;
        add(lblParam, c);
        c = gbc(1, 2); c.weightx = 1; c.gridwidth = 2; c.fill = GridBagConstraints.HORIZONTAL;
        add(paramPanel, c);

        // Row 3 — output file
        JLabel lblOut = new JLabel("Output file:");
        lblOut.setFont(LABEL_FONT_BOLD);
        textFieldOutput = new JTextField();
        buttonChooseOutput = new JButton("Select...");
        buttonChooseOutput.addActionListener(e -> chooseOutputFile());

        c = gbc(0, 3);
        add(lblOut, c);
        c = gbc(1, 3); c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        add(textFieldOutput, c);
        c = gbc(2, 3);
        add(buttonChooseOutput, c);

        // Row 4 — action buttons
        buttonProcess = new JButton("\u25B6 Process");
        buttonProcess.addActionListener(e -> onProcessClicked());
        buttonCancel = new JButton("Cancel");
        buttonCancel.addActionListener(e -> dispose());

        JPanel buttons = new JPanel();
        buttons.add(buttonProcess);
        buttons.add(buttonCancel);

        c = gbc(0, 4); c.gridwidth = 3; c.anchor = GridBagConstraints.CENTER;
        c.insets = new Insets(14, 6, 6, 6);
        add(buttons, c);
    }

    private void loadProcessors(DescriptionOfAlgorithm preselected) {
        try {
            AlgorithmManager mgr = AlgorithmManager.getInstance();
            int preselectIndex = -1;
            for (DescriptionOfAlgorithm d : mgr.getAlgorithms()) {
                if (AlgorithmType.DATA_PROCESSOR.equals(d.getAlgorithmType())) {
                    if (preselected != null && d.getName().equals(preselected.getName())) {
                        preselectIndex = processorDescriptions.size();
                    }
                    processorDescriptions.add(d);
                    comboProcessors.addItem(d.getName());
                }
            }
            if (preselectIndex >= 0) {
                comboProcessors.setSelectedIndex(preselectIndex);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        onProcessorSelectionChanged();
    }

    private void onProcessorSelectionChanged() {
        if (paramPanel == null) {
            return; // can be fired while the UI is still being built
        }
        int idx = comboProcessors.getSelectedIndex();
        if (idx >= 0 && idx < processorDescriptions.size()) {
            paramPanel.update(processorDescriptions.get(idx));
        } else {
            paramPanel.update((DescriptionOfAlgorithm) null);
        }
        pack();
    }

    private void chooseOutputFile() {
        JFileChooser fc;
        if (inputFilePath != null && !inputFilePath.trim().isEmpty()) {
            File parent = new File(inputFilePath).getParentFile();
            fc = (parent != null && parent.isDirectory()) ? new JFileChooser(parent) : new JFileChooser();
        } else {
            fc = new JFileChooser();
        }
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            textFieldOutput.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    private void onProcessClicked() {
        if (processing) {
            return;
        }

        if (inputFilePath == null || inputFilePath.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "No input file was provided.",
                    "Cannot process", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int idx = comboProcessors.getSelectedIndex();
        if (idx < 0 || idx >= processorDescriptions.size()) {
            JOptionPane.showMessageDialog(this, "Please select a processing tool.",
                    "No tool selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        final String outputPath = textFieldOutput.getText().trim();
        if (outputPath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please specify an output file.",
                    "No output file", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Warn before silently overwriting the input file.
        if (new File(outputPath).getAbsoluteFile().equals(new File(inputFilePath).getAbsoluteFile())) {
            int answer = JOptionPane.showConfirmDialog(this,
                    "The output file is the same as the input file.\n"
                            + "The input file will be overwritten. Continue?",
                    "Overwrite input file?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
        }

        List<String> missing = paramPanel.getMissingRequiredParameterNames();
        if (missing != null && !missing.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Missing required parameter(s): " + String.join(", ", missing),
                    "Cannot process", JOptionPane.WARNING_MESSAGE);
            return;
        }

        final DescriptionOfAlgorithm processor = processorDescriptions.get(idx);
        final String[] params = paramPanel.getParameterValues();

        setProcessingState(true);

        // Run in the background so the dialog (and the rest of the UI) does
        // not freeze while the processor is working.
        Thread worker = new Thread(() -> {
            Throwable failure = null;
            try {
                processor.runAlgorithm(params, inputFilePath, outputPath);
            } catch (Throwable e) {
                failure = e;
            }
            final Throwable finalFailure = failure;
            SwingUtilities.invokeLater(() -> onProcessingFinished(outputPath, finalFailure));
        }, "SPMF-ProcessDataset");
        worker.setDaemon(true);
        worker.start();
    }

    private void onProcessingFinished(String outputPath, Throwable failure) {
        setProcessingState(false);
        if (failure == null) {
            processedFilePath = outputPath;
            JOptionPane.showMessageDialog(this,
                    "Dataset processed successfully:\n" + outputPath,
                    "Success", JOptionPane.INFORMATION_MESSAGE);
            dispose();
        } else {
            failure.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "An error occurred while processing the dataset:\n" + failure,
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void setProcessingState(boolean running) {
        processing = running;
        buttonProcess.setEnabled(!running);
        buttonCancel.setEnabled(!running);
        buttonChooseOutput.setEnabled(!running);
        comboProcessors.setEnabled(!running);
        textFieldOutput.setEnabled(!running);
        paramPanel.setEnabled(!running);
        setDefaultCloseOperation(running ? DO_NOTHING_ON_CLOSE : DISPOSE_ON_CLOSE);
        setCursor(running ? java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR)
                : java.awt.Cursor.getDefaultCursor());
    }

    /**
     * Returns the path of the processed output file, or {@code null} if the
     * dialog was cancelled or processing failed.
     */
    public String getProcessedFilePath() {
        return processedFilePath;
    }

    private static GridBagConstraints gbc(int x, int y) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;
        return c;
    }
}