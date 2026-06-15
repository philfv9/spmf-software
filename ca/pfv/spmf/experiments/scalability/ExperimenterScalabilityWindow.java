package ca.pfv.spmf.experiments.scalability;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;

/*
 * This file is copyright (c) 2021 Philippe Fournier-Viger
 *
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */

/**
 * GUI window for configuring and launching scalability experiments.
 *
 * <p>The user provides:
 * <ul>
 *   <li>One or more algorithm names</li>
 *   <li>Fixed algorithm parameters (no "##")</li>
 *   <li>A single input dataset file</li>
 *   <li>A list of integer percentages (e.g. 20, 40, 60, 80, 100)</li>
 *   <li>Output directory, timeout, and option flags</li>
 * </ul>
 *
 * <p>The experiment engine ({@link ExperimenterForScalability}) then
 * creates subset files (first N% of data lines) and runs each algorithm
 * on each subset.
 *
 * @author Philippe Fournier-Viger
 */
public class ExperimenterScalabilityWindow extends JFrame {

    private static final long serialVersionUID = 1L;

    // ── Algorithm list ───────────────────────────────────────────────
    private final DefaultListModel<String> algorithmModel =
            new DefaultListModel<>();
    private final JList<String> algorithmList =
            new JList<>(algorithmModel);
    private final JTextField tfNewAlgorithm = new JTextField(24);

    // ── Fixed parameters ─────────────────────────────────────────────
    private final JTextField tfParams = new JTextField(30);

    // ── Single input file ────────────────────────────────────────────
    private final JTextField tfInputFile = new JTextField(34);

    // ── Percentages list ─────────────────────────────────────────────
    private final DefaultListModel<String> pctModel =
            new DefaultListModel<>();
    private final JList<String> pctList = new JList<>(pctModel);
    private final JTextField tfNewPct = new JTextField(6);

    // ── SPMF jar ─────────────────────────────────────────────────────
    private final JTextField tfJar = new JTextField(30);

    // ── Output directory ─────────────────────────────────────────────
    private final JTextField tfOutputDir =
            new JTextField("SCALABILITY_EXPERIMENTS", 26);

    // ── Timeout (seconds) ────────────────────────────────────────────
    private final JSpinner spinTimeout =
            new JSpinner(new SpinnerNumberModel(
                    120, 1, Integer.MAX_VALUE, 10));

    // ── Options ──────────────────────────────────────────────────────
    private final JCheckBox chkOutputSize =
            new JCheckBox("Compare output sizes", true);
    private final JCheckBox chkShowCmd =
            new JCheckBox("Show command", false);
    private final JCheckBox chkLatex =
            new JCheckBox("Generate LaTeX figures", true);

    // ── Controls ─────────────────────────────────────────────────────
    private final JButton btnRun    = new JButton("Run Experiment");
    private final JLabel  lblStatus = new JLabel(" ");

    // ────────────────────────────────────────────────────────────────
    // Constructor
    // ────────────────────────────────────────────────────────────────

    /** Builds and displays the window. */
    public ExperimenterScalabilityWindow() {
        super("Scalability Experiment");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(6, 6));
        add(buildMainPanel(), BorderLayout.CENTER);
        add(buildStatusBar(),  BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(680, 640));
    }

    // ────────────────────────────────────────────────────────────────
    // Panel builders
    // ────────────────────────────────────────────────────────────────

    private JPanel buildMainPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        GridBagConstraints g = new GridBagConstraints();
        g.insets  = new Insets(4, 4, 4, 4);
        g.gridx   = 0;
        g.weightx = 1.0;
        g.fill    = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // SPMF jar
        g.gridy   = row++;
        g.fill    = GridBagConstraints.HORIZONTAL;
        g.weighty = 0;
        p.add(buildJarPanel(), g);

        // Algorithm list (takes vertical space)
        g.gridy   = row++;
        g.fill    = GridBagConstraints.BOTH;
        g.weighty = 0.20;
        p.add(buildAlgorithmPanel(), g);

        // Fixed parameters
        g.gridy   = row++;
        g.fill    = GridBagConstraints.HORIZONTAL;
        g.weighty = 0;
        p.add(buildParamsPanel(), g);

        // Single input file
        g.gridy = row++;
        p.add(buildInputFilePanel(), g);

        // Percentages list (takes vertical space)
        g.gridy   = row++;
        g.fill    = GridBagConstraints.BOTH;
        g.weighty = 0.20;
        p.add(buildPctPanel(), g);

        // Output
        g.gridy   = row++;
        g.fill    = GridBagConstraints.HORIZONTAL;
        g.weighty = 0;
        p.add(buildOutputPanel(), g);

        // Options
        g.gridy = row++;
        p.add(buildOptionsPanel(), g);

        // Run button
        g.gridy = row++;
        JPanel bp = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnRun.setPreferredSize(new Dimension(200, 36));
        btnRun.addActionListener(e -> onRun());
        bp.add(btnRun);
        p.add(bp, g);

        return p;
    }

    private JPanel buildJarPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        p.setBorder(titled("SPMF Jar File"));
        p.add(new JLabel("Path to spmf.jar:"));
        p.add(tfJar);
        JButton b = new JButton("Browse…");
        b.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                tfJar.setText(
                    fc.getSelectedFile().getAbsolutePath());
            }
        });
        p.add(b);
        return p;
    }

    private JPanel buildAlgorithmPanel() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setBorder(titled("Algorithms to Compare"));
        algorithmList.setVisibleRowCount(3);
        p.add(new JScrollPane(algorithmList), BorderLayout.CENTER);

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row.add(new JLabel("Name:"));
        row.add(tfNewAlgorithm);

        JButton addBtn = new JButton("Add");
        addBtn.addActionListener(e -> {
            String s = tfNewAlgorithm.getText().trim();
            if (!s.isEmpty()) {
                algorithmModel.addElement(s);
                tfNewAlgorithm.setText("");
            }
        });

        JButton remBtn = new JButton("Remove");
        remBtn.addActionListener(e -> {
            int i = algorithmList.getSelectedIndex();
            if (i >= 0) algorithmModel.remove(i);
        });

        row.add(addBtn);
        row.add(remBtn);
        p.add(row, BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildParamsPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        p.setBorder(titled(
            "Fixed Algorithm Parameters (space-separated, no \"##\")"));
        p.add(new JLabel("Parameters:"));
        p.add(tfParams);
        p.add(new JLabel("e.g.:  0.4  0.6"));
        return p;
    }

    private JPanel buildInputFilePanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        p.setBorder(titled("Input Dataset File (single file)"));
        p.add(new JLabel("File:"));
        p.add(tfInputFile);
        JButton b = new JButton("Browse…");
        b.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                tfInputFile.setText(
                    fc.getSelectedFile().getAbsolutePath());
            }
        });
        p.add(b);
        return p;
    }

    private JPanel buildPctPanel() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setBorder(titled(
            "Dataset Subset Sizes (integer percentages, e.g. 20 40 60 80 100)"));
        pctList.setVisibleRowCount(4);
        p.add(new JScrollPane(pctList), BorderLayout.CENTER);

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row.add(new JLabel("Percentage (1-100):"));
        row.add(tfNewPct);

        JButton addBtn = new JButton("Add");
        addBtn.addActionListener(e -> {
            String s = tfNewPct.getText().trim();
            if (s.isEmpty()) return;
            try {
                int v = Integer.parseInt(s);
                if (v < 1 || v > 100) {
                    warn("Percentage must be between 1 and 100.");
                    return;
                }
                pctModel.addElement(s);
                tfNewPct.setText("");
            } catch (NumberFormatException ex) {
                warn("\"" + s + "\" is not a valid integer.");
            }
        });

        JButton remBtn = new JButton("Remove");
        remBtn.addActionListener(e -> {
            int i = pctList.getSelectedIndex();
            if (i >= 0) pctModel.remove(i);
        });

        row.add(addBtn);
        row.add(remBtn);
        p.add(row, BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildOutputPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        p.setBorder(titled("Output"));
        p.add(new JLabel("Output directory:"));
        p.add(tfOutputDir);

        JButton b = new JButton("Browse…");
        b.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                tfOutputDir.setText(
                    fc.getSelectedFile().getAbsolutePath());
            }
        });
        p.add(b);

        p.add(new JLabel("  Timeout (s):"));
        spinTimeout.setPreferredSize(new Dimension(80, 26));
        p.add(spinTimeout);
        return p;
    }

    private JPanel buildOptionsPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        p.setBorder(titled("Options"));
        p.add(chkOutputSize);
        p.add(chkShowCmd);
        p.add(chkLatex);
        return p;
    }

    private JPanel buildStatusBar() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        p.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        lblStatus.setForeground(Color.DARK_GRAY);
        p.add(lblStatus);
        return p;
    }

    private TitledBorder titled(String title) {
        return BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), title);
    }

    // ────────────────────────────────────────────────────────────────
    // Run handler
    // ────────────────────────────────────────────────────────────────

    /**
     * Validates inputs then launches
     * {@link ExperimenterForScalability#runScalabilityExperiment}
     * in a background thread so the EDT stays responsive.
     */
    private void onRun() {

        // ── Collect algorithm names ──────────────────────────────────
        if (algorithmModel.isEmpty()) {
            warn("Please add at least one algorithm name.");
            return;
        }
        String[] algs = new String[algorithmModel.size()];
        for (int i = 0; i < algorithmModel.size(); i++) {
            algs[i] = algorithmModel.get(i);
        }

        // ── Collect fixed parameters ─────────────────────────────────
        String rawParams = tfParams.getText().trim();
        String[] fixedParams;
        if (rawParams.isEmpty()) {
            fixedParams = new String[0];
        } else {
            fixedParams = rawParams.split("\\s+");
        }
        for (String fp : fixedParams) {
            if ("##".equals(fp)) {
                warn("Do not use '##' in a scalability experiment.\n"
                   + "Parameters are fixed across all runs.\n"
                   + "Remove '##' from the parameters field.");
                return;
            }
        }

        // ── Collect input file ───────────────────────────────────────
        String inputFile = tfInputFile.getText().trim();
        if (inputFile.isEmpty()) {
            warn("Please select an input dataset file.");
            return;
        }

        // ── Collect percentages ──────────────────────────────────────
        if (pctModel.isEmpty()) {
            warn("Please add at least one percentage value.");
            return;
        }
        String[] percentages = new String[pctModel.size()];
        for (int i = 0; i < pctModel.size(); i++) {
            percentages[i] = pctModel.get(i);
        }

        // ── Collect remaining settings ───────────────────────────────
        String  jar     = tfJar.getText().trim();
        String  outDir  = tfOutputDir.getText().trim();
        int     toutMs  = (Integer) spinTimeout.getValue() * 1000;
        boolean cmpOut  = chkOutputSize.isSelected();
        boolean showCmd = chkShowCmd.isSelected();
        boolean latex   = chkLatex.isSelected();

        if (jar.isEmpty()) {
            warn("Please specify the path to spmf.jar.");
            return;
        }
        if (outDir.isEmpty()) {
            warn("Please specify an output directory.");
            return;
        }

        // ── Disable run button while running ─────────────────────────
        btnRun.setEnabled(false);
        lblStatus.setText("Running… please wait.");

        // Capture finals for the background thread
        final String[] fAlgs    = algs;
        final String[] fParams  = fixedParams;
        final String   fInput   = inputFile;
        final String[] fPct     = percentages;
        final String   fJar     = jar;
        final String   fOutDir  = outDir;
        final int      fTout    = toutMs;
        final boolean  fCmpOut  = cmpOut;
        final boolean  fShowCmd = showCmd;
        final boolean  fLatex   = latex;

        new Thread(() -> {
            try {
                ExperimenterForScalability exp =
                        new ExperimenterForScalability();
                exp.setSPMFJarFilePath(fJar);

                exp.runScalabilityExperiment(
                        fAlgs,
                        fParams,
                        fInput,
                        fPct,
                        fOutDir,
                        fTout,
                        fCmpOut,
                        fShowCmd,
                        fLatex);

                SwingUtilities.invokeLater(() -> {
                    lblStatus.setText(
                        "Done. Results saved to: " + fOutDir);
                    btnRun.setEnabled(true);
                    JOptionPane.showMessageDialog(
                        ExperimenterScalabilityWindow.this,
                        "Experiments finished!\nResults in: "
                            + fOutDir,
                        "Done",
                        JOptionPane.INFORMATION_MESSAGE);
                });

            } catch (Exception ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    lblStatus.setText("ERROR: " + ex.getMessage());
                    btnRun.setEnabled(true);
                    JOptionPane.showMessageDialog(
                        ExperimenterScalabilityWindow.this,
                        "Error:\n" + ex.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                });
            }
        }, "ScalabilityThread").start();
    }

    // ────────────────────────────────────────────────────────────────
    // Dialog helpers
    // ────────────────────────────────────────────────────────────────

    private void warn(String msg) {
        JOptionPane.showMessageDialog(
                this, msg, "Warning",
                JOptionPane.WARNING_MESSAGE);
    }
}