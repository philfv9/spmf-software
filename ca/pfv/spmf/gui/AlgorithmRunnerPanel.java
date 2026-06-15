package ca.pfv.spmf.gui;

/*
 * Copyright (c) 2008-2015 Philippe Fournier-Viger
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 * SPMF is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see http://www.gnu.org/licenses/.
 */

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.awt.event.MouseListener;

import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentListener;

import ca.pfv.spmf.algorithmmanager.AlgorithmManager;
import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.gui.parameterselectionpanel.ParameterSelectionPanel;
import ca.pfv.spmf.gui.preferences.PreferencesManager;

/**
 * Reusable JPanel containing the complete algorithm-runner UI.
 *
 * <p><b>Layout strategy:</b> every row — including the algorithm row — is a
 * {@link RowSlot} with an identical 5-column inner {@link GridBagLayout}:
 * <pre>
 *   col 0 (weightx=0) – section label (fixed width, right-aligned)
 *   col 1 (weightx=1) – main text field / checkbox / parameter panel
 *   col 2 (weightx=0) – "Select"   button  — all share {@code selectDim}
 *   col 3 (weightx=0) – "Recent ▼" button  — all share {@code recentDim}
 *   col 4 (weightx=0) – "View" / "Guide" — all share {@code viewDocDim}
 * </pre>
 * Because every button in the same logical column has the same preferred size,
 * the layout manager assigns equal widths to those columns across all rows,
 * producing perfect alignment without a truly shared grid.</p>
 *
 * <p><b>Hide/show without layout shift:</b> each hideable row is a
 * {@link RowSlot} that always reserves its full preferred height via a
 * {@link CardLayout} that swaps between real content and a transparent
 * spacer.</p>
 *
 * @author Philippe Fournier-Viger
 */
@SuppressWarnings("serial")
public class AlgorithmRunnerPanel extends JPanel {

    // --- Layout constants ------------------------------------------------
    private static final int   INSET_V           = 4;
    private static final int   INSET_H           = 4;
    private static final int   INSET_SECTION_TOP = 8;
    private static final Font  LABEL_FONT_BOLD   = new Font(Font.DIALOG, Font.BOLD, 12);
    private static final Color LABEL_COLOR       = new Color(60, 60, 60);

    // --- CardLayout card names -------------------------------------------
    private static final String CARD_VISIBLE = "visible";
    private static final String CARD_HIDDEN  = "hidden";

    // --- Tab indices -----------------------------------------------------
    private static final int TAB_CONSOLE = 0;
    private static final int TAB_HISTORY = 1;
    private static final int TAB_OUTPUT  = 2;
    
    private final boolean showViewAndTransform;
    private final boolean showGenerateData;
    private final boolean showTools;
    private final boolean showAlgorithms;
    private final boolean showExperimentTools;

    // =====================================================================
    // RowSlot — fixed-height CardLayout wrapper for every row
    // =====================================================================

    /**
     * A panel that always occupies the preferred height of its content card,
     * whether that content is shown or not. Uses a {@link CardLayout} to swap
     * between real content and a transparent spacer without shifting other rows.
     */
    private static final class RowSlot extends JPanel {

        private final CardLayout cardLayout   = new CardLayout();
        private final JPanel     contentPanel;
        private       boolean    contentShown = true;

        RowSlot(JPanel content) {
            setLayout(cardLayout);
            setOpaque(false);
            this.contentPanel = content;

            JPanel spacer = new JPanel();
            spacer.setOpaque(false);

            add(content, CARD_VISIBLE);
            add(spacer,  CARD_HIDDEN);
        }

        void setContentVisible(boolean visible) {
            cardLayout.show(this, visible ? CARD_VISIBLE : CARD_HIDDEN);
            contentShown = visible;
        }

        boolean isContentVisible() { return contentShown; }

        /** Always reports the content card's height so the slot never collapses. */
        @Override
        public Dimension getPreferredSize() {
            Dimension d = contentPanel.getPreferredSize();
            return new Dimension(super.getPreferredSize().width, d.height);
        }

        @Override public Dimension getMinimumSize() { return getPreferredSize(); }
    }

    // =====================================================================
    // Widgets
    // =====================================================================

    /** SPMF logo label. */
    private JLabel labelSPMF;

    /** Section labels (col 0 of each row). */
    private JLabel lblChooseAnAlgorithm, lblSetInputFile,
                   lblSetOutputFile, lblParameters, lblOptions;

    /**
     * Read-only field showing the currently selected algorithm name.
     * Selection is done via the Select dialog, not by typing.
     */
    private JTextField textFieldAlgorithm;

    /** Text fields for input / output file paths (col 1). */
    private JTextField textFieldInput, textFieldOutput;

    /** Text field for the time-limit value in seconds. */
    private JTextField textMaxSeconds;

    /** Opens the searchable algorithm-selector dialog. */
    private JButton buttonSelectAlgorithm;

    /** Shows the recent-algorithms popup menu. */
    private JButton buttonRecentAlgorithm;

    /** Opens the documentation web page for the selected algorithm. */
    private JButton buttonExample;

    /** Browse / Recent / View buttons for the input-file row. */
    private JButton buttonInput, buttonRecentInput, buttonViewInput;

    /** Browse / Recent buttons for the output-file row. */
    private JButton buttonOutput, buttonRecentOutput;

    /** Runs or stops the current algorithm. */
    private JButton buttonRun;

    /** Checkbox: run the algorithm in a separate JVM process. */
    private JCheckBox chckbxRunAsExternal;

    /** Checkbox: enable a wall-clock time limit. */
    private JCheckBox chckbxMaxSeconds;

    /** Fixed-size wrapper holding the time-limit checkbox + field. */
    private JPanel optionsTimeLimitWrapper;

    /** Progress bar shown while an algorithm is running. */
    private JProgressBar progressBar;

    /** Parameter selection panel (col 1–4 of the parameters row). */
    private ParameterSelectionPanel parameterPanel;

    /** Console, history, and output-preview tabs. */
    private JTabbedPane     bottomTabbedPane;
    private ConsolePanel    consolePanel;
    private RunHistoryPanel runHistoryPanel;
    private OutputPanel     outputPanel;

    // --- Row slots -------------------------------------------------------
    private RowSlot rowSlotAlgorithm;
    private RowSlot rowSlotInput;
    private RowSlot rowSlotParameters;
    private RowSlot rowSlotOutput;
    private RowSlot rowSlotOptions;
    private RowSlot rowSlotRun;
    private RowSlot rowSlotProgress;

    // =====================================================================
    // Construction
    // =====================================================================

    /**
     * Builds all widgets and lays them out.
     *
     * @param showViewAndTransform include view-and-transform algorithms
     * @param showGenerateData     include data-generation algorithms
     * @param showTools            include tool algorithms
     * @param showAlgorithms       include data-mining algorithms
     * @param showExperimentTools  include experiment-tool algorithms
     * @throws Exception if AlgorithmManager cannot be initialised
     */
    public AlgorithmRunnerPanel(boolean showViewAndTransform,
                                boolean showGenerateData,
                                boolean showTools,
                                boolean showAlgorithms,
                                boolean showExperimentTools) throws Exception {

        setBorder(new EmptyBorder(12, 12, 12, 12));
        setLayout(new GridBagLayout());
        
        this.showViewAndTransform = showViewAndTransform;
        this.showGenerateData = showGenerateData;
        this.showTools = showTools;
        this.showAlgorithms = showAlgorithms;
        this.showExperimentTools = showExperimentTools;

        // -----------------------------------------------------------------
        // Compute per-column button sizes.
        //
        // col 2: "Select"      — compact
        // col 3: "Recent ▼"   — compact
        // col 4: "View" and "Guide" must share one size so col 4
        //        is the same width in every row.  We take the larger of the
        //        two natural widths and add padding.
        // -----------------------------------------------------------------
        JButton protoSelect = new JButton("Select");
        JButton protoRecent = new JButton("Recent \u25BC");
        JButton protoView   = new JButton("View");
        JButton protoDoc    = new JButton("Guide");

        int btnH = Math.max(
                Math.max(protoSelect.getPreferredSize().height,
                         protoRecent.getPreferredSize().height),
                Math.max(protoView.getPreferredSize().height,
                         protoDoc.getPreferredSize().height));

        Dimension selectDim  = new Dimension(
                protoSelect.getPreferredSize().width  + 6,  btnH);
        Dimension recentDim  = new Dimension(
                protoRecent.getPreferredSize().width  + 6,  btnH);
        // col 4: both "View" and "Guide" get the same width
        Dimension viewDocDim = new Dimension(
                Math.max(protoView.getPreferredSize().width,
                         protoDoc.getPreferredSize().width) + 14, btnH);

        // -----------------------------------------------------------------
        // Compute label column (col 0) width.
        //
        // Measure all label texts, find the widest, and apply that width
        // to every label. Each label is right-aligned so the field columns
        // all start at exactly the same horizontal position.
        // -----------------------------------------------------------------
        JLabel protoAlgo   = makeSectionLabel("Algorithm:");
        JLabel protoInput  = makeSectionLabel("Input file:");
        JLabel protoOutput = makeSectionLabel("Output file:");
        JLabel protoParam  = makeSectionLabel("Parameters:");
        JLabel protoOpt    = makeSectionLabel("Options:");

        int labelW = Math.max(
                Math.max(protoAlgo.getPreferredSize().width,
                         protoInput.getPreferredSize().width),
                Math.max(protoOutput.getPreferredSize().width,
                         Math.max(protoParam.getPreferredSize().width,
                                  protoOpt.getPreferredSize().width))) + 8;

        int labelH = Math.max(
                Math.max(protoAlgo.getPreferredSize().height,
                         protoInput.getPreferredSize().height),
                Math.max(protoOutput.getPreferredSize().height,
                         Math.max(protoParam.getPreferredSize().height,
                                  protoOpt.getPreferredSize().height)));

        Dimension labelDim = new Dimension(labelW, labelH);

        // -----------------------------------------------------------------
        // Row 0 — SPMF logo (spans all 5 columns, never hidden)
        // -----------------------------------------------------------------
        labelSPMF = new JLabel(new ImageIcon(
                AlgorithmRunnerPanel.class.getResource("spmf.png")));
        labelSPMF.setToolTipText("Click to see information about SPMF");

        GridBagConstraints gbc = defaultGbc();
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 5;
        gbc.fill   = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(INSET_V, INSET_H, INSET_V, INSET_H);
        add(labelSPMF, gbc);

        // -----------------------------------------------------------------
        // Row 1 — Algorithm selector
        // Every row (including this one) is a RowSlot so all inner
        // GridBagLayouts are structurally identical and button sizes drive
        // consistent column widths across rows.
        // -----------------------------------------------------------------
        lblChooseAnAlgorithm = makeSectionLabel("Algorithm:");
        lblChooseAnAlgorithm.setHorizontalAlignment(SwingConstants.LEFT);
        lblChooseAnAlgorithm.setPreferredSize(labelDim);

        textFieldAlgorithm = new JTextField();
        textFieldAlgorithm.setEditable(false);
        textFieldAlgorithm.setToolTipText(
                "Currently selected algorithm — click Select to change");

        buttonSelectAlgorithm = new JButton("Select");
        buttonSelectAlgorithm.setPreferredSize(selectDim);
        buttonSelectAlgorithm.setToolTipText("Open the searchable algorithm browser");

        buttonRecentAlgorithm = new JButton("Recent \u25BC");
        buttonRecentAlgorithm.setPreferredSize(recentDim);
        buttonRecentAlgorithm.setToolTipText("Show recently used algorithms");

        buttonExample = new JButton("Guide");
        buttonExample.setPreferredSize(viewDocDim);
        buttonExample.setToolTipText("Open the documentation page for this algorithm");
        buttonExample.setEnabled(false);

        rowSlotAlgorithm = new RowSlot(buildStandardRow(
                lblChooseAnAlgorithm, textFieldAlgorithm,
                buttonSelectAlgorithm, buttonRecentAlgorithm, buttonExample,
                labelDim, selectDim, recentDim, viewDocDim, INSET_SECTION_TOP));
        placeRowSlot(rowSlotAlgorithm, 1);

        // -----------------------------------------------------------------
        // Row 2 — Input file (hideable)
        // -----------------------------------------------------------------
        lblSetInputFile = makeSectionLabel("Input file:");
        lblSetInputFile.setHorizontalAlignment(SwingConstants.LEFT);
        lblSetInputFile.setPreferredSize(labelDim);

        textFieldInput = new JTextField();
        textFieldInput.setEditable(true);
        textFieldInput.setToolTipText(
                "Type or paste the input file path, or use Select");

        buttonInput = new JButton("Select");
        buttonInput.setPreferredSize(selectDim);
        buttonInput.setToolTipText("Select an input file");

        buttonRecentInput = new JButton("Recent \u25BC");
        buttonRecentInput.setPreferredSize(recentDim);
        buttonRecentInput.setToolTipText("Show recently used input files");

        buttonViewInput = new JButton("View");
        buttonViewInput.setPreferredSize(viewDocDim);
        buttonViewInput.setToolTipText("Open the input file in a viewer");
        buttonViewInput.setEnabled(false);

        rowSlotInput = new RowSlot(buildStandardRow(
                lblSetInputFile, textFieldInput,
                buttonInput, buttonRecentInput, buttonViewInput,
                labelDim, selectDim, recentDim, viewDocDim, INSET_V));
        placeRowSlot(rowSlotInput, 2);

        // -----------------------------------------------------------------
        // Row 3 — Parameters (hideable)
        // -----------------------------------------------------------------
        lblParameters = makeSectionLabel("Parameters:");
        lblParameters.setHorizontalAlignment(SwingConstants.LEFT);
        lblParameters.setPreferredSize(labelDim);

        parameterPanel = new ParameterSelectionPanel(null);
        parameterPanel.update((DescriptionOfAlgorithm) null);
        int reservedParamH = parameterPanel.getPreferredSize().height;

        // Wrapper locks the parameter panel height so the row never shrinks.
        final int lockedH = reservedParamH;
        JPanel paramWrapper = new JPanel(new BorderLayout()) {
            @Override public Dimension getPreferredSize() {
                return new Dimension(super.getPreferredSize().width, lockedH);
            }
            @Override public Dimension getMinimumSize()  { return getPreferredSize(); }
            @Override public Dimension getMaximumSize()  {
                return new Dimension(Integer.MAX_VALUE, lockedH);
            }
        };
        paramWrapper.setOpaque(false);
        paramWrapper.add(parameterPanel, BorderLayout.NORTH);

        rowSlotParameters = new RowSlot(
                buildParameterRow(lblParameters, paramWrapper, labelDim, INSET_V));
        placeRowSlot(rowSlotParameters, 3);

        // -----------------------------------------------------------------
        // Row 4 — Output file (hideable)
        // -----------------------------------------------------------------
        lblSetOutputFile = makeSectionLabel("Output file:");
        lblSetOutputFile.setHorizontalAlignment(SwingConstants.LEFT);
        lblSetOutputFile.setPreferredSize(labelDim);

        textFieldOutput = new JTextField();
        textFieldOutput.setEditable(true);
        textFieldOutput.setToolTipText(
                "Type or paste the output file path, or use Select");

        buttonOutput = new JButton("Select");
        buttonOutput.setPreferredSize(selectDim);
        buttonOutput.setToolTipText("Select an output file location");

        buttonRecentOutput = new JButton("Recent \u25BC");
        buttonRecentOutput.setPreferredSize(recentDim);
        buttonRecentOutput.setToolTipText("Show recently used output files");

        // Col 4 placeholder: same size as viewDocDim so col 4 width is
        // consistent with the algorithm and input rows.
        JPanel outputPlaceholder = fixedSizePanel(viewDocDim);

        rowSlotOutput = new RowSlot(buildStandardRow(
                lblSetOutputFile, textFieldOutput,
                buttonOutput, buttonRecentOutput, outputPlaceholder,
                labelDim, selectDim, recentDim, viewDocDim, INSET_V));
        placeRowSlot(rowSlotOutput, 4);

        // -----------------------------------------------------------------
        // Row 5 — Options (hideable)
        // -----------------------------------------------------------------
        lblOptions = makeSectionLabel("Options:");
        lblOptions.setHorizontalAlignment(SwingConstants.LEFT);
        lblOptions.setPreferredSize(labelDim);

        chckbxRunAsExternal = new JCheckBox("Run in a separated process");
        chckbxRunAsExternal.setToolTipText(
                "Run the algorithm in a new JVM process (requires spmf.jar)");
        chckbxRunAsExternal.setSelected(
                PreferencesManager.getInstance().getRunAsExternalProgram());

        chckbxMaxSeconds = new JCheckBox("Time limit (s):");
        chckbxMaxSeconds.setToolTipText(
                "Stop the algorithm after the given number of seconds");

        textMaxSeconds = new JTextField(6);
        textMaxSeconds.setToolTipText("Maximum execution time in seconds");
        textMaxSeconds.setEditable(false);
        textMaxSeconds.setEnabled(false);

        optionsTimeLimitWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        optionsTimeLimitWrapper.setOpaque(false);
        optionsTimeLimitWrapper.add(chckbxMaxSeconds);
        optionsTimeLimitWrapper.add(textMaxSeconds);

        // Lock the wrapper to its fully-visible size so hiding the widgets
        // inside it does not change the row height.
        chckbxMaxSeconds.setVisible(true);
        textMaxSeconds.setVisible(true);
        Dimension wrapperPref = optionsTimeLimitWrapper.getPreferredSize();
        chckbxMaxSeconds.setVisible(false);
        textMaxSeconds.setVisible(false);
        optionsTimeLimitWrapper.setPreferredSize(wrapperPref);
        optionsTimeLimitWrapper.setMinimumSize(wrapperPref);
        optionsTimeLimitWrapper.setMaximumSize(wrapperPref);

        rowSlotOptions = new RowSlot(
                buildOptionsRow(lblOptions, chckbxRunAsExternal,
                                optionsTimeLimitWrapper, labelDim, INSET_V));
        placeRowSlot(rowSlotOptions, 5);

        // -----------------------------------------------------------------
        // Row 6 — Run button (hideable)
        // -----------------------------------------------------------------
        buttonRun = new JButton("\u25B6 Run algorithm");
        buttonRun.setEnabled(false);
        buttonRun.setToolTipText(
                "Run the selected algorithm with the given parameters");
        buttonRun.setPreferredSize(new Dimension(
                buttonRun.getPreferredSize().width  + 40,
                buttonRun.getPreferredSize().height + 8));

        JPanel runContent = new JPanel(new FlowLayout(FlowLayout.CENTER, INSET_H, INSET_V));
        runContent.setOpaque(false);
        runContent.add(buttonRun);

        rowSlotRun = new RowSlot(runContent);
        placeRowSlot(rowSlotRun, 6);

        // -----------------------------------------------------------------
        // Row 7 — Progress bar (hideable, starts hidden)
        // -----------------------------------------------------------------
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("");
        progressBar.setToolTipText("Shows progress while an algorithm is running");

        JPanel progressContent = new JPanel(new BorderLayout());
        progressContent.setOpaque(false);
        progressContent.setBorder(new EmptyBorder(INSET_V, INSET_H, INSET_V, INSET_H));
        progressContent.add(progressBar, BorderLayout.CENTER);

        rowSlotProgress = new RowSlot(progressContent);
        rowSlotProgress.setContentVisible(false);
        placeRowSlot(rowSlotProgress, 7);

        // -----------------------------------------------------------------
        // Row 8 — Vertical spacer
        // -----------------------------------------------------------------
        gbc = defaultGbc();
        gbc.gridx = 0; gbc.gridy = 8; gbc.gridwidth = 5;
        gbc.fill    = GridBagConstraints.VERTICAL;
        gbc.weighty = 0.001;
        gbc.insets  = new Insets(0, 0, 0, 0);
        add(Box.createVerticalGlue(), gbc);

        // -----------------------------------------------------------------
        // Row 9 — Bottom tabbed pane
        // -----------------------------------------------------------------
        consolePanel = new ConsolePanel(false);
        consolePanel.setBorder(new EmptyBorder(2, 2, 2, 2));
        consolePanel.setPreferredSize(new Dimension(600, 160));

        runHistoryPanel = new RunHistoryPanel();
        runHistoryPanel.setPreferredSize(new Dimension(600, 160));

        outputPanel = new OutputPanel();
        outputPanel.setPreferredSize(new Dimension(600, 160));

        bottomTabbedPane = new JTabbedPane(JTabbedPane.TOP);
        bottomTabbedPane.addTab("Console",     consolePanel);
        bottomTabbedPane.addTab("Run history", runHistoryPanel);
        bottomTabbedPane.addTab("Output",      outputPanel);
        bottomTabbedPane.setToolTipTextAt(TAB_CONSOLE,
                "Console showing messages produced by algorithms");
        bottomTabbedPane.setToolTipTextAt(TAB_HISTORY,
                "Past algorithm runs — double-click a row to re-run");
        bottomTabbedPane.setToolTipTextAt(TAB_OUTPUT,
                "Preview of the most recent output file");

        gbc = defaultGbc();
        gbc.gridx = 0; gbc.gridy = 9;
        gbc.gridwidth  = 5;
        gbc.gridheight = GridBagConstraints.REMAINDER;
        gbc.fill    = GridBagConstraints.BOTH;
        gbc.weightx = 1; gbc.weighty = 1;
        gbc.insets  = new Insets(INSET_V, INSET_H, INSET_V, INSET_H);
        add(bottomTabbedPane, gbc);
    }

    // =====================================================================
    // Row-content builders
    // =====================================================================

    /**
     * Builds a standard 5-column inner row panel.
     * Each button column receives its own explicit preferred size so that
     * the same size is used in every row, making the columns align visually.
     *
     * @param label      col 0 — fixed width, right-aligned
     * @param field      col 1 (weightx=1, fills horizontally)
     * @param btn2       col 2 — sized to {@code col2Dim}
     * @param btn3       col 3 — sized to {@code col3Dim}
     * @param btn4       col 4 — sized to {@code col4Dim}
     * @param labelDim   preferred size for col-0 label
     * @param col2Dim    preferred size for col-2 component
     * @param col3Dim    preferred size for col-3 component
     * @param col4Dim    preferred size for col-4 component
     * @param topInset   extra top inset for this row
     */
    private JPanel buildStandardRow(JLabel             label,
                                    JTextField          field,
                                    java.awt.Component  btn2,
                                    java.awt.Component  btn3,
                                    java.awt.Component  btn4,
                                    Dimension           labelDim,
                                    Dimension           col2Dim,
                                    Dimension           col3Dim,
                                    Dimension           col4Dim,
                                    int                 topInset) {
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);

        GridBagConstraints c = defaultGbc();
        c.gridy = 0; c.gridheight = 1; c.gridwidth = 1;

        // col 0 — label, fixed width, right-aligned
        c.gridx = 0; c.weightx = 0;
        c.fill = GridBagConstraints.NONE; c.anchor = GridBagConstraints.EAST;
        c.insets = new Insets(topInset, INSET_H, INSET_V, INSET_H);
        label.setPreferredSize(labelDim);
        row.add(label, c);

        // col 1 — text field, takes all remaining space
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(topInset, INSET_H, INSET_V, INSET_H);
        row.add(field, c);

        // col 2 — primary button
        c.gridx = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.CENTER;
        c.insets = new Insets(topInset, INSET_H, INSET_V, INSET_H);
        btn2.setPreferredSize(col2Dim);
        row.add(btn2, c);

        // col 3 — recent button
        c.gridx = 3;
        c.insets = new Insets(topInset, INSET_H, INSET_V, INSET_H);
        btn3.setPreferredSize(col3Dim);
        row.add(btn3, c);

        // col 4 — view / documentation button
        c.gridx = 4;
        c.insets = new Insets(topInset, INSET_H, INSET_V, INSET_H);
        btn4.setPreferredSize(col4Dim);
        row.add(btn4, c);

        return row;
    }

    /**
     * Builds the parameters row: label in col 0 (fixed-width, right-aligned),
     * locked-height parameter wrapper spanning cols 1–4.
     */
    private JPanel buildParameterRow(JLabel    label,
                                     JPanel    paramWrapper,
                                     Dimension labelDim,
                                     int       topInset) {
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);

        GridBagConstraints c = defaultGbc();
        c.gridy = 0; c.gridheight = 1;

        c.gridx = 0; c.gridwidth = 1; c.weightx = 0;
        c.fill = GridBagConstraints.NONE; c.anchor = GridBagConstraints.NORTHEAST;
        c.insets = new Insets(topInset, INSET_H, INSET_V, INSET_H);
        label.setPreferredSize(labelDim);
        row.add(label, c);

        c.gridx = 1; c.gridwidth = 4; c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL; c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(topInset, INSET_H, INSET_V, INSET_H);
        row.add(paramWrapper, c);

        return row;
    }

    /**
     * Builds the options row: label (fixed-width, right-aligned) |
     * run-as-external checkbox | time-limit wrapper (cols 2–4).
     */
    private JPanel buildOptionsRow(JLabel    label,
                                   JCheckBox cbExternal,
                                   JPanel    timeLimitWrapper,
                                   Dimension labelDim,
                                   int       topInset) {
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);

        GridBagConstraints c = defaultGbc();
        c.gridy = 0; c.gridheight = 1;

        c.gridx = 0; c.gridwidth = 1; c.weightx = 0;
        c.fill = GridBagConstraints.NONE; c.anchor = GridBagConstraints.EAST;
        c.insets = new Insets(topInset, INSET_H, INSET_V, INSET_H);
        label.setPreferredSize(labelDim);
        row.add(label, c);

        c.gridx = 1; c.gridwidth = 1; c.weightx = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(topInset, INSET_H, INSET_V, INSET_H);
        row.add(cbExternal, c);

        c.gridx = 2; c.gridwidth = 3; c.weightx = 1;
        c.insets = new Insets(topInset, INSET_H, INSET_V, INSET_H);
        row.add(timeLimitWrapper, c);

        return row;
    }

    // =====================================================================
    // Placement helper for the outer GridBagLayout
    // =====================================================================

    /** Places a {@link RowSlot} spanning all 5 columns in the given grid row. */
    private void placeRowSlot(RowSlot slot, int gridRow) {
        GridBagConstraints c = defaultGbc();
        c.gridx = 0; c.gridy = gridRow; c.gridwidth = 5;
        c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor  = GridBagConstraints.NORTHWEST;
        c.insets  = new Insets(0, 0, 0, 0);
        add(slot, c);
    }

    // =====================================================================
    // Private helpers
    // =====================================================================

    /** Returns a {@link GridBagConstraints} with sensible defaults. */
    private static GridBagConstraints defaultGbc() {
        GridBagConstraints c = new GridBagConstraints();
        c.gridheight = 1;
        c.fill       = GridBagConstraints.HORIZONTAL;
        c.anchor     = GridBagConstraints.WEST;
        c.weightx    = 0;
        c.weighty    = 0;
        c.insets     = new Insets(INSET_V, INSET_H, INSET_V, INSET_H);
        return c;
    }

    /** Returns a transparent panel fixed to the given size. */
    private static JPanel fixedSizePanel(Dimension size) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setPreferredSize(size);
        p.setMinimumSize(size);
        p.setMaximumSize(size);
        return p;
    }

    /** Creates a bold, dark section-header label. */
    private static JLabel makeSectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(LABEL_FONT_BOLD);
        label.setForeground(LABEL_COLOR);
        return label;
    }

    // =====================================================================
    // Listener registration
    // =====================================================================

    /** Registers a listener on the Run/Stop button. */
    public void addRunButtonListener(ActionListener l)             { buttonRun.addActionListener(l); }

    /** Registers a listener on the Documentation button. */
    public void addExampleButtonListener(ActionListener l)         { buttonExample.addActionListener(l); }

    /** Registers a listener on the Recent-algorithm button. */
    public void addRecentAlgorithmButtonListener(ActionListener l) { buttonRecentAlgorithm.addActionListener(l); }

    /** Registers a listener on the Select-algorithm button. */
    public void addSelectAlgorithmButtonListener(ActionListener l) { buttonSelectAlgorithm.addActionListener(l); }

    /** Registers a listener on the input-file Select button. */
    public void addInputButtonListener(ActionListener l)           { buttonInput.addActionListener(l); }

    /** Registers a listener on the output-file Select button. */
    public void addOutputButtonListener(ActionListener l)          { buttonOutput.addActionListener(l); }

    /** Registers a listener on the Recent-input button. */
    public void addRecentInputButtonListener(ActionListener l)     { buttonRecentInput.addActionListener(l); }

    /** Registers a listener on the Recent-output button. */
    public void addRecentOutputButtonListener(ActionListener l)    { buttonRecentOutput.addActionListener(l); }

    /** Registers a listener on the View-input button. */
    public void addViewInputButtonListener(ActionListener l)       { buttonViewInput.addActionListener(l); }

    /** Registers a listener on the Run-as-external checkbox. */
    public void addRunAsExternalListener(ActionListener l)         { chckbxRunAsExternal.addActionListener(l); }

    /** Registers a listener on the time-limit checkbox. */
    public void addMaxSecondsCheckboxListener(ActionListener l)    { chckbxMaxSeconds.addActionListener(l); }

    /** Registers a DocumentListener on the input-file text field. */
    public void addInputFileTextListener(DocumentListener l)  { textFieldInput.getDocument().addDocumentListener(l); }

    /** Registers a DocumentListener on the output-file text field. */
    public void addOutputFileTextListener(DocumentListener l) { textFieldOutput.getDocument().addDocumentListener(l); }

    /** Registers a listener on the SPMF logo. */
    public void addLogoMouseListener(MouseListener l)              { labelSPMF.addMouseListener(l); }

    /** Registers a listener on the history Re-run action. */
    public void addRerunHistoryListener(ActionListener l)          { runHistoryPanel.addRerunListener(l); }

    /** Registers a listener on the history Clear button. */
    public void addClearHistoryListener(ActionListener l)          { runHistoryPanel.addClearHistoryListener(l); }

    /** Registers a listener on the Output-tab Open button. */
    public void addOutputTabViewButtonListener(ActionListener l)   { outputPanel.addOpenButtonListener(l); }

    // =====================================================================
    // State readers
    // =====================================================================

    /** Returns the currently displayed algorithm name (may be empty). */
    public String getSelectedAlgorithmName()         { return textFieldAlgorithm.getText(); }

    /** Returns all current parameter values from the parameter panel. */
    public String[] getParameterValues()             { return parameterPanel.getParameterValues(); }

    /** Returns the number of parameter fields currently shown. */
    public int getParameterCount()                   { return parameterPanel.getParameterCount(); }

    /** Returns the viewer method chosen in the Output tab. */
    public String getOutputTabSelectedViewerMethod() { return outputPanel.getSelectedViewerMethod(); }

    /** Returns whether the Run-as-external checkbox is selected. */
    public boolean isRunAsExternal()                 { return chckbxRunAsExternal.isSelected(); }

    /** Returns whether the time-limit checkbox is selected. */
    public boolean isTimeLimitEnabled()              { return chckbxMaxSeconds.isSelected(); }

    /** Returns the raw text of the max-seconds field. */
    public String getMaxSecondsText()                { return textMaxSeconds.getText(); }

    /** Returns whether the output-file section is currently visible. */
    public boolean isOutputFileSectionVisible()      { return rowSlotOutput.isContentVisible(); }

    /** Returns the current input-file path text. */
    public String getInputFileText()                 { return textFieldInput.getText(); }

    /** Returns the current output-file path text. */
    public String getOutputFileText()                { return textFieldOutput.getText(); }

    /** Returns the history entry currently selected in the history table. */
    public RunHistoryEntry getSelectedHistoryEntry() { return runHistoryPanel.getSelectedEntry(); }

    /** Returns the Recent-algorithm button (used to anchor its popup). */
    public JButton getRecentAlgorithmButton()        { return buttonRecentAlgorithm; }

    /** Returns the Recent-input button (used to anchor its popup). */
    public JButton getRecentInputButton()            { return buttonRecentInput; }

    /** Returns the Recent-output button (used to anchor its popup). */
    public JButton getRecentOutputButton()           { return buttonRecentOutput; }
    
    /**
     * Returns the showViewAndTransform flag passed during construction.
     */
    public boolean getShowViewAndTransform() {
        return showViewAndTransform;
    }

    /**
     * Returns the showGenerateData flag passed during construction.
     */
    public boolean getShowGenerateData() {
        return showGenerateData;
    }

    /**
     * Returns the showTools flag passed during construction.
     */
    public boolean getShowTools() {
        return showTools;
    }

    /**
     * Returns the showAlgorithms flag passed during construction.
     */
    public boolean getShowAlgorithms() {
        return showAlgorithms;
    }

    /**
     * Returns the showExperimentTools flag passed during construction.
     */
    public boolean getShowExperimentTools() {
        return showExperimentTools;
    }

    // =====================================================================
    // State writers
    // =====================================================================

    /** Sets the displayed input file path. */
    public void setInputFileName(String name)        { textFieldInput.setText(name); }

    /** Sets the displayed output file path. */
    public void setOutputFileName(String name)       { textFieldOutput.setText(name); }

    /** Enables or disables the View-input button. */
    public void setViewInputButtonEnabled(boolean e) { buttonViewInput.setEnabled(e); }

    /** Sets the Run-as-external checkbox state. */
    public void setRunAsExternalSelected(boolean s)  { chckbxRunAsExternal.setSelected(s); }

    /** Pushes parameter values into the parameter panel. */
    public void setParameterValues(String[] values)  { parameterPanel.setParameterValues(values); }

    /**
     * Sets the displayed algorithm name in the read-only text field.
     *
     * @param algorithmName the algorithm name to display
     */
    public void setSelectedAlgorithm(String algorithmName) {
        textFieldAlgorithm.setText(algorithmName == null ? "" : algorithmName);
    }

    /**
     * Shows or hides the time-limit widgets. The row height is preserved
     * because the wrapper has a fixed preferred size.
     *
     * @param visible true to show, false to hide
     */
    public void setTimeLimitWidgetsVisible(boolean visible) {
        chckbxMaxSeconds.setVisible(visible);
        textMaxSeconds.setVisible(visible);
        optionsTimeLimitWrapper.revalidate();
        optionsTimeLimitWrapper.repaint();
    }

    /**
     * Enables or disables the max-seconds text field.
     *
     * @param enabled true to enable
     */
    public void setMaxSecondsFieldEnabled(boolean enabled) {
        textMaxSeconds.setEnabled(enabled);
        textMaxSeconds.setEditable(enabled);
    }

    /** Adds an entry to the run-history panel. */
    public void addHistoryEntry(RunHistoryEntry entry) { runHistoryPanel.addEntry(entry); }

    /** Clears all entries from the run-history panel. */
    public void clearHistory()                         { runHistoryPanel.clearHistory(); }

    /** Switches the bottom pane to the Console tab. */
    public void showConsoleTab()  { bottomTabbedPane.setSelectedIndex(TAB_CONSOLE); }

    /** Switches the bottom pane to the History tab. */
    public void showHistoryTab()  { bottomTabbedPane.setSelectedIndex(TAB_HISTORY); }

    /** Switches the bottom pane to the Output tab. */
    public void showOutputTab()   { bottomTabbedPane.setSelectedIndex(TAB_OUTPUT); }

    /**
     * Updates the Output tab with a preview of the given file.
     * Pass null to clear the tab when no output was produced.
     *
     * @param outputFilePath full path of the output file, or null/empty to clear
     */
    public void updateOutputTab(String outputFilePath) {
        outputPanel.updatePreview(outputFilePath);

        if (outputFilePath == null || outputFilePath.trim().isEmpty()) {
            return;
        }

        String algorithmName = getSelectedAlgorithmName();
        try {
            DescriptionOfAlgorithm algorithm =
                    AlgorithmManager.getInstance()
                                    .getDescriptionOfAlgorithm(algorithmName);
            if (algorithm != null && algorithm.getOutputFileTypes() != null) {
                outputPanel.setViewerOptions(
                        buildViewerOptionsForAlgorithm(algorithm));
            }
        } catch (Exception e) {
            outputPanel.setViewerOptions(new String[]{
                    "System text editor", "SPMF text editor", "Pattern viewer"});
        }
        showOutputTab();
    }

    // =====================================================================
    // Compound UI updates driven by algorithm selection
    // =====================================================================

    /**
     * Updates every section of the panel to reflect the newly selected
     * algorithm. Pass null to hide all optional sections.
     *
     * @param algorithm the selected algorithm, or null
     * @throws Exception if AlgorithmManager lookup fails
     */
    public void updateUserInterfaceForAlgorithm(
            DescriptionOfAlgorithm algorithm) throws Exception {
        updateRunButton(algorithm);
        updateExampleButton(algorithm);
        updateParameterPanel(algorithm);
        updateInputFileComponents(algorithm);
        updateOutputFileComponents(algorithm);
        updateButtonViewInput(algorithm);
        updateRunningOptions(algorithm);
        bottomTabbedPane.setVisible(algorithm != null);
    }

    /** Hides all optional components; called once before any algorithm is selected. */
    public void applyInitialVisibility() throws Exception {
        updateUserInterfaceForAlgorithm(null);
    }

    private void updateRunButton(DescriptionOfAlgorithm algorithm) {
        boolean on = algorithm != null;
        buttonRun.setEnabled(on);
        rowSlotRun.setContentVisible(on);
    }

    private void updateExampleButton(DescriptionOfAlgorithm algorithm) {
        buttonExample.setEnabled(algorithm != null);
    }

    /**
     * Rebuilds the parameter panel for the given algorithm. The RowSlot
     * preserves its height so no other rows shift.
     */
    private void updateParameterPanel(DescriptionOfAlgorithm algorithm) {
        if (algorithm == null) {
            rowSlotParameters.setContentVisible(false);
            return;
        }
        boolean hasParams = algorithm.getParametersDescription().length > 0;
        if (hasParams) {
            parameterPanel.update(algorithm);
        }
        rowSlotParameters.setContentVisible(hasParams);
        rowSlotParameters.revalidate();
        rowSlotParameters.repaint();
    }

    private void updateInputFileComponents(DescriptionOfAlgorithm algorithm) {
        boolean on = algorithm != null && algorithm.getInputFileTypes() != null;
        rowSlotInput.setContentVisible(on);
    }

    private void updateOutputFileComponents(DescriptionOfAlgorithm algorithm) {
        boolean on = algorithm != null && algorithm.getOutputFileTypes() != null;
        rowSlotOutput.setContentVisible(on);
    }

    private void updateRunningOptions(DescriptionOfAlgorithm algorithm) {
        boolean on = algorithm != null
                && AlgorithmType.DATA_MINING.equals(algorithm.getAlgorithmType());
        rowSlotOptions.setContentVisible(on);
        if (on) {
            setTimeLimitWidgetsVisible(chckbxRunAsExternal.isSelected());
        }
    }

    private void updateButtonViewInput(DescriptionOfAlgorithm algorithm)
            throws Exception {
        if (algorithm == null || algorithm.getInputFileTypes() == null
                || algorithm.getAlgorithmType() == AlgorithmType.DATA_VIEWER) {
            buttonViewInput.setEnabled(false);
            return;
        }
        DescriptionOfAlgorithm viewer = AlgorithmManager.getInstance()
                .getViewerFor(algorithm.getInputFileTypes());
        buttonViewInput.setEnabled(viewer != null);
    }

    private String[] buildViewerOptionsForAlgorithm(
            DescriptionOfAlgorithm algorithm) {
        String[] types = algorithm.getOutputFileTypes();
        java.util.List<String> opts = new java.util.ArrayList<>();

        AlgorithmType at = algorithm.getAlgorithmType();
        opts.add("System text editor");
        opts.add("SPMF text editor");
        if (!at.equals(AlgorithmType.OTHER_TOOL)
                && !at.equals(AlgorithmType.OTHER_GUI_TOOL)
                && !at.equals(AlgorithmType.DATA_GENERATOR)) {
            opts.add("Pattern viewer");
        }

        if (at != AlgorithmType.DATA_VIEWER) {
            try {
                opts.add(AlgorithmManager.getInstance()
                        .getViewerFor(types).getName());
            } catch (Exception ignored) { }
        }

        for (String t : types) {
            if (t.contains("itemset")) {
                opts.add("Visualize_With_ItemItemset_Matrix"); break;
            }
        }
        for (String t : types) {
            if (t.contains("sequential patterns")) {
                opts.add("Visualize_sequential_patterns_with_transition_graph_viewer");
                break;
            }
        }
        if (types[0].equals("Time series database")) {
            opts.remove("Pattern viewer");
            opts.add("Time series viewer");
        }
        if (types[0].equals("Clusters")) opts.add("Cluster viewer");

        int last = types.length - 1;
        if (types[last].equals("Top-k Frequent subgraphs")
                || types[last].equals("Frequent subgraphs")) {
            opts.remove("Pattern viewer");
            opts.add("Graph viewer");
        }
        return opts.toArray(new String[0]);
    }

    // =====================================================================
    // Run-state transitions
    // =====================================================================

    /** Puts the panel into its running state. */
    public void setUIToRunningState() {
        rowSlotProgress.setContentVisible(true);
        progressBar.setIndeterminate(true);
        progressBar.setString("Running...");
        buttonRun.setText("\u25A0 Stop algorithm");
        buttonSelectAlgorithm.setEnabled(false);
        chckbxMaxSeconds.setEnabled(false);
        chckbxRunAsExternal.setEnabled(false);
        bottomTabbedPane.setSelectedIndex(TAB_CONSOLE);
    }

    /** Resets the panel to its idle state after a run finishes or is stopped. */
    public void resetUIAfterThreadCompletion() {
        buttonRun.setText("\u25B6 Run algorithm");
        progressBar.setIndeterminate(false);
        progressBar.setString("");
        rowSlotProgress.setContentVisible(false);
        buttonSelectAlgorithm.setEnabled(true);
        chckbxMaxSeconds.setEnabled(true);
        chckbxRunAsExternal.setEnabled(true);
    }

    // =====================================================================
    // Console delegation
    // =====================================================================

    /** Posts a status message to the console. */
    public void postStatusMessage(String msg)  { consolePanel.postStatusMessage(msg); }

    /** Appends a line to the console. */
    public void appendConsoleLine(String line) { consolePanel.appendLine(line); }

    /** Clears the console. */
    public void clearConsole()                 { consolePanel.clearConsole(); }

    /** Redirects stdout to the console. */
    public void redirectOutputStream()         { consolePanel.redirectOutputStream(); }
}