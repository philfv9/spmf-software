package ca.pfv.spmf.gui.guided_interface;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import ca.pfv.spmf.algorithmmanager.AlgorithmManager;
import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.gui.parameterselectionpanel.ParameterSelectionPanel;
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

/**
 * Modal dialog that lets the user pick a {@link AlgorithmType#DATA_GENERATOR}
 * algorithm, configure its parameters, choose an output file, and generate a
 * dataset.
 *
 * <p>
 * Opening the generator UI in its own dialog (rather than embedding it
 * directly in {@link DataSelectionPanel}) keeps the main "Select data" tab
 * compact, since most users will simply pick an existing file most of the
 * time.
 * </p>
 *
 * <p>
 * After a successful generation, the dialog closes automatically and the
 * caller can retrieve the resulting file path via
 * {@link #getGeneratedFilePath()}, as well as the description of the
 * generator algorithm that was used via
 * {@link #getGeneratorAlgorithmDescription()}. The latter lets the caller
 * know the exact file type that was produced (via the last element of
 * {@link DescriptionOfAlgorithm#getOutputFileType()}), without having to
 * guess it from the generated file's content.
 * </p>
 *
 * @author Philippe Fournier-Viger
 */
@SuppressWarnings("serial")
public class GenerateDatasetDialog extends JDialog {

    private static final Font LABEL_FONT_BOLD = new Font(Font.DIALOG, Font.BOLD, 12);

    private JComboBox<String> comboGenerators;
    private ParameterSelectionPanel paramPanel;
    private javax.swing.JTextField textFieldOutput;
    private JButton buttonChooseOutput;
    private JButton buttonGenerate;
    private JButton buttonCancel;

    private final List<DescriptionOfAlgorithm> generatorDescriptions = new ArrayList<>();

    /** Set once generation succeeds; null otherwise. */
    private String generatedFilePath = null;

    /**
     * Set once generation succeeds, to the description of the generator
     * algorithm that produced {@link #generatedFilePath}; null otherwise.
     * This lets callers know the exact output file type
     * (last element of {@link DescriptionOfAlgorithm#getOutputFileType()})
     * without having to inspect the generated file's content.
     */
    private DescriptionOfAlgorithm generatorAlgorithmDescriptionUsed = null;

    public GenerateDatasetDialog(Frame owner) {
        super(owner, "Generate a new dataset", true);

        setLayout(new GridBagLayout());
        buildUI();
        loadGenerators();

        setMinimumSize(new Dimension(520, 260));
        pack();
        setLocationRelativeTo(owner);
    }

    private void buildUI() {
        GridBagConstraints c;

        // Row 0 — generator chooser
        JLabel lblAlgo = new JLabel("Generator:");
        lblAlgo.setFont(LABEL_FONT_BOLD);
        comboGenerators = new JComboBox<>();
        comboGenerators.addActionListener(e -> onGeneratorSelectionChanged());

        c = gbc(0, 0);
        add(lblAlgo, c);
        c = gbc(1, 0); c.weightx = 1; c.gridwidth = 2; c.fill = GridBagConstraints.HORIZONTAL;
        add(comboGenerators, c);

        // Row 1 — parameters
        JLabel lblParam = new JLabel("Parameters:");
        lblParam.setFont(LABEL_FONT_BOLD);
        paramPanel = new ParameterSelectionPanel(null);

        c = gbc(0, 1); c.anchor = GridBagConstraints.NORTHEAST;
        add(lblParam, c);
        c = gbc(1, 1); c.weightx = 1; c.gridwidth = 2; c.fill = GridBagConstraints.HORIZONTAL;
        add(paramPanel, c);

        // Row 2 — output file
        JLabel lblOut = new JLabel("Output file:");
        lblOut.setFont(LABEL_FONT_BOLD);
        textFieldOutput = new javax.swing.JTextField();
        buttonChooseOutput = new JButton("Select...");
        buttonChooseOutput.addActionListener(e -> chooseOutputFile());

        c = gbc(0, 2);
        add(lblOut, c);
        c = gbc(1, 2); c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        add(textFieldOutput, c);
        c = gbc(2, 2);
        add(buttonChooseOutput, c);

        // Row 3 — action buttons
        buttonGenerate = new JButton("\u25B6 Generate");
        buttonGenerate.addActionListener(e -> onGenerateClicked());
        buttonCancel = new JButton("Cancel");
        buttonCancel.addActionListener(e -> dispose());

        JPanel buttons = new JPanel();
        buttons.add(buttonGenerate);
        buttons.add(buttonCancel);

        c = gbc(0, 3); c.gridwidth = 3; c.anchor = GridBagConstraints.CENTER;
        c.insets = new Insets(14, 6, 6, 6);
        add(buttons, c);
    }

    private void loadGenerators() {
        try {
            AlgorithmManager mgr = AlgorithmManager.getInstance();
            for (DescriptionOfAlgorithm d : mgr.getAlgorithms()) {
                if (mgr.isDataGeneratorAlgorithm(d)) {
                    generatorDescriptions.add(d);
                    comboGenerators.addItem(d.getName());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        onGeneratorSelectionChanged();
    }

    private void onGeneratorSelectionChanged() {
        int idx = comboGenerators.getSelectedIndex();
        if (idx >= 0 && idx < generatorDescriptions.size()) {
            paramPanel.update(generatorDescriptions.get(idx));
        } else {
            paramPanel.update((DescriptionOfAlgorithm) null);
        }
        pack();
    }

    private void chooseOutputFile() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            textFieldOutput.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    private void onGenerateClicked() {
        int idx = comboGenerators.getSelectedIndex();
        if (idx < 0 || idx >= generatorDescriptions.size()) {
            JOptionPane.showMessageDialog(this, "Please select a generator algorithm.",
                    "No generator selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String outputPath = textFieldOutput.getText().trim();
        if (outputPath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please specify an output file.",
                    "No output file", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<String> missing = paramPanel.getMissingRequiredParameterNames();
        if (!missing.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Missing required parameter(s): " + String.join(", ", missing),
                    "Cannot generate", JOptionPane.WARNING_MESSAGE);
            return;
        }

        DescriptionOfAlgorithm generator = generatorDescriptions.get(idx);
        String[] params = paramPanel.getParameterValues();

        buttonGenerate.setEnabled(false);
        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
        try {
            generator.runAlgorithm(params, null, outputPath);
            generatedFilePath = outputPath;
            // Remember which generator produced this file, so that the caller
            // can determine the exact output file type from its description
            // (last element of getOutputFileType()) instead of having to
            // guess it from the file's content.
            generatorAlgorithmDescriptionUsed = generator;
            JOptionPane.showMessageDialog(this,
                    "Dataset generated successfully:\n" + outputPath,
                    "Success", JOptionPane.INFORMATION_MESSAGE);
            dispose();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "An error occurred while generating the dataset:\n" + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        } finally {
            setCursor(java.awt.Cursor.getDefaultCursor());
            buttonGenerate.setEnabled(true);
        }
    }

    /**
     * Returns the path of the generated file, or {@code null} if the dialog
     * was cancelled or generation failed.
     */
    public String getGeneratedFilePath() {
        return generatedFilePath;
    }

    /**
     * Returns the description of the generator algorithm that was used to
     * produce {@link #getGeneratedFilePath()}, or {@code null} if the dialog
     * was cancelled or generation failed. Callers can use
     * {@code getGeneratorAlgorithmDescription().getOutputFileType()} (its
     * last element) to know the exact type of the generated file, without
     * having to guess it from the file's content.
     */
    public DescriptionOfAlgorithm getGeneratorAlgorithmDescription() {
        return generatorAlgorithmDescriptionUsed;
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