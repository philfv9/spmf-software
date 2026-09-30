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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.PlainDocument;

import ca.pfv.spmf.algorithmmanager.AlgorithmManager;
import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.gui.AlgorithmProgressBar;
import ca.pfv.spmf.gui.ConsolePanel;
import ca.pfv.spmf.gui.RunHistoryEntry;
import ca.pfv.spmf.gui.RunHistoryPanel;
import ca.pfv.spmf.gui.parameterselectionpanel.ParameterSelectionPanel;
import ca.pfv.spmf.gui.preferences.PreferencesManager;

/**
 * This is the algorithm selection panel (tab 2) of the guided interface of SPMF.
 *
 * <p>
 * Note: the input file itself (path and type) is chosen and displayed in the
 * "Select data" tab (tab 1). This panel deliberately does not repeat that
 * information — it only keeps a private, non-displayed copy of the current
 * input file's type (see {@link #currentInputFileType}), which is used
 * internally to filter the "Recent" algorithms popup menu.
 * </p>
 *
 * <p>
 * The "Run algorithm" button serves a dual purpose: it runs the selected
 * algorithm and, once a run has produced an output file, navigating to the
 * "Visualize results" tab (tab 3) is handled automatically by the controller.
 * There is no separate "Next" button on this tab — the Run button is the
 * single action that advances the wizard.
 * </p>
 *
 * @author Philippe Fournier-Viger
 */
@SuppressWarnings("serial")
public class AlgorithmSelectionPanel extends JPanel {

    private static final Font LABEL_FONT_BOLD = new Font(Font.DIALOG, Font.BOLD, 12);
    private static final int TAB_CONSOLE = 0;
    private static final int TAB_HISTORY = 1;

    private static final String MAX_SECONDS_TOOLTIP =
            "Maximum number of seconds before the algorithm is automatically stopped";

    // Algorithm row
    private JTextField textFieldAlgorithm;
    private JButton buttonSelectAlgorithm;
    private JButton buttonRecentAlgorithm;
    private JButton buttonGuide;

    /**
     * Current input file type, mirrored from tab 1 (auto-detected or chosen
     * manually by the user there). Not displayed in this panel (that
     * information already appears in the "Select data" tab); used only to
     * filter the "Recent" algorithms popup menu so that only algorithms
     * compatible with this type are proposed.
     */
    private String currentInputFileType = null;

    // Output row
    private JPanel rowOutput;
    private JTextField textFieldOutput;
    private JButton buttonOutput;
    private JButton buttonRecentOutput;

    // Parameters
    private JPanel rowParameters;
    private ParameterSelectionPanel parameterPanel;

    // Options
    private JPanel rowOptions;
    private JCheckBox chckbxRunAsExternal;
    private JCheckBox chckbxBeepOnCompletion;
    private JCheckBox chckbxMaxSeconds;
    private JTextField textMaxSeconds;
    /** The text field's border when its content is valid (or the field is disabled). */
    private Border maxSecondsDefaultBorder;
    /** The text field's border used to flag an invalid (empty/non-positive) time limit. */
    private static final Border MAX_SECONDS_INVALID_BORDER =
            BorderFactory.createLineBorder(new Color(200, 0, 0), 1);

    // Run + progress
    private JButton buttonRun;
    private JPanel rowProgress;
    private AlgorithmProgressBar progressBar;

    // Bottom tabs
    private JTabbedPane bottomTabbedPane;
    private ConsolePanel consolePanel;
    private RunHistoryPanel runHistoryPanel;

    public AlgorithmSelectionPanel() {
        setLayout(new GridBagLayout());

        int y = 0;
        add(buildAlgorithmRow(), gbcRow(y++));

        rowOutput = buildOutputRow();
        add(rowOutput, gbcRow(y++));

        rowParameters = buildParametersRow();
        add(rowParameters, gbcRow(y++));

        rowOptions = buildOptionsRow();
        add(rowOptions, gbcRow(y++));

        add(buildRunRow(), gbcRow(y++));

        rowProgress = buildProgressRow();
        rowProgress.setVisible(false);
        add(rowProgress, gbcRow(y++));

        add(buildBottomTabs(), gbcFill(y++));

        applyInitialVisibility();
    }

    private GridBagConstraints gbcRow(int y) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = y;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.NORTHWEST;
        return c;
    }

    private GridBagConstraints gbcFill(int y) {
        GridBagConstraints c = gbcRow(y);
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        return c;
    }

    // =====================================================================
    // Row builders
    // =====================================================================

    private JPanel buildAlgorithmRow() {
        JPanel row = new JPanel(new GridBagLayout());
        JLabel label = sectionLabel("Algorithm:");

        textFieldAlgorithm = new JTextField();
        textFieldAlgorithm.setEditable(false);
        textFieldAlgorithm.setToolTipText("Currently selected algorithm — click Select to change");

        buttonSelectAlgorithm = new JButton("Select");
        buttonRecentAlgorithm = new JButton("Recent \u25BC");
        buttonRecentAlgorithm.setToolTipText(
                "Shows recently used algorithms compatible with the current input file's type");
        // The "Recent" menu is built and filtered internally by this panel
        // (see showRecentAlgorithmsMenu()), based on the input file type
        // mirrored from tab 1 via setInputFileType(String).
        buttonRecentAlgorithm.addActionListener(e -> showRecentAlgorithmsMenu());

        buttonGuide = new JButton("Guide");
        buttonGuide.setEnabled(false);

        layoutStandardRow(row, label, textFieldAlgorithm, buttonSelectAlgorithm, buttonRecentAlgorithm, buttonGuide);
        return row;
    }

    private JPanel buildOutputRow() {
        JPanel row = new JPanel(new GridBagLayout());
        JLabel label = sectionLabel("Output file:");

        textFieldOutput = new JTextField();
        textFieldOutput.setToolTipText(
                "Type or paste the output file path, or use Select \u2014 a name is suggested "
                        + "automatically based on the input file and the selected algorithm");

        buttonOutput = new JButton("Select");
        buttonRecentOutput = new JButton("Recent \u25BC");
        JPanel placeholder = new JPanel();
        placeholder.setOpaque(false);

        layoutStandardRow(row, label, textFieldOutput, buttonOutput, buttonRecentOutput, placeholder);
        return row;
    }

    private JPanel buildParametersRow() {
        JPanel row = new JPanel(new GridBagLayout());
        JLabel label = sectionLabel("Parameters:");

        parameterPanel = new ParameterSelectionPanel(null);
        parameterPanel.update((DescriptionOfAlgorithm) null);

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.gridy = 0;
        c.gridx = 0;
        c.anchor = GridBagConstraints.NORTHEAST;
        row.add(label, c);

        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        row.add(parameterPanel, c);

        return row;
    }

    private JPanel buildOptionsRow() {
        JPanel row = new JPanel(new GridBagLayout());
        JLabel label = sectionLabel("Options:");

        chckbxRunAsExternal = new JCheckBox("Run in a separated process");
        chckbxRunAsExternal.setToolTipText(
                "Preference only in this prototype \u2014 execution still happens in the same process");
        chckbxRunAsExternal.setSelected(PreferencesManager.getInstance().getRunAsExternalProgram());

        chckbxBeepOnCompletion = new JCheckBox("Beep on completion");
        chckbxBeepOnCompletion.setSelected(PreferencesManager.getInstance().getBeepOnCompletion());
        chckbxBeepOnCompletion.addActionListener(
                e -> PreferencesManager.getInstance().setBeepOnCompletion(chckbxBeepOnCompletion.isSelected()));

        JPanel checkboxes = new JPanel();
        checkboxes.setLayout(new BoxLayout(checkboxes, BoxLayout.Y_AXIS));
        checkboxes.add(chckbxRunAsExternal);
        checkboxes.add(chckbxBeepOnCompletion);

        chckbxMaxSeconds = new JCheckBox("Time limit (s):");
        textMaxSeconds = new JTextField(6);
        textMaxSeconds.setEnabled(false);
        textMaxSeconds.setEditable(false);
        textMaxSeconds.setToolTipText(MAX_SECONDS_TOOLTIP);
        maxSecondsDefaultBorder = textMaxSeconds.getBorder();
        installDigitsOnlyFilter(textMaxSeconds);
        textMaxSeconds.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { validateMaxSecondsField(); }
            @Override
            public void removeUpdate(DocumentEvent e) { validateMaxSecondsField(); }
            @Override
            public void changedUpdate(DocumentEvent e) { validateMaxSecondsField(); }
        });
        // Re-validate (and reset the border) whenever the checkbox itself is
        // toggled, since enabling/disabling the field changes whether an
        // empty value is considered a problem.
        chckbxMaxSeconds.addActionListener(e -> validateMaxSecondsField());

        JPanel timeLimit = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        timeLimit.add(chckbxMaxSeconds);
        timeLimit.add(textMaxSeconds);

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.gridy = 0;
        c.gridx = 0;
        c.anchor = GridBagConstraints.NORTHEAST;
        row.add(label, c);

        c.gridx = 1;
        row.add(checkboxes, c);

        c.gridx = 2;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        row.add(timeLimit, c);

        return row;
    }

    /**
     * Installs a {@link DocumentFilter} on {@code field} that silently
     * rejects any inserted text containing non-digit characters, so the
     * time-limit field can never contain letters, symbols, or decimal
     * points — only whole numbers (or be empty).
     */
    private void installDigitsOnlyFilter(JTextField field) {
        PlainDocument doc = new PlainDocument();
        doc.setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
                    throws BadLocationException {
                if (string != null && string.chars().allMatch(Character::isDigit)) {
                    super.insertString(fb, offset, string, attr);
                }
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                    throws BadLocationException {
                if (text == null || text.isEmpty() || text.chars().allMatch(Character::isDigit)) {
                    super.replace(fb, offset, length, text == null ? "" : text, attrs);
                }
            }
        });
        field.setDocument(doc);
    }

    /**
     * Validates the current content of {@link #textMaxSeconds} and gives
     * immediate visual feedback: the field's border turns red and its
     * tooltip explains the problem whenever the time limit is enabled but
     * the field is empty or not a positive whole number (e.g. "0"); the
     * border is restored to normal otherwise (including when the checkbox
     * itself is unchecked, since the field's content is irrelevant then).
     * Non-digit characters can never actually be typed in the first place
     * (see {@link #installDigitsOnlyFilter(JTextField)}), but this also
     * catches the empty-field and "0" cases.
     */
    private void validateMaxSecondsField() {
        if (textMaxSeconds == null || chckbxMaxSeconds == null) {
            return;
        }
        if (!chckbxMaxSeconds.isSelected() || isTimeLimitValueValid()) {
            textMaxSeconds.setBorder(maxSecondsDefaultBorder);
            textMaxSeconds.setToolTipText(MAX_SECONDS_TOOLTIP);
        } else {
            textMaxSeconds.setBorder(MAX_SECONDS_INVALID_BORDER);
            textMaxSeconds.setToolTipText("Please enter a positive whole number of seconds");
        }
    }

    /**
     * Builds the run row. The single "Run algorithm" button both executes the
     * selected algorithm and — once a successful run has produced an output
     * file — triggers navigation to the "Visualize results" tab (tab 3) via
     * the controller. There is no separate "Next" button on this tab.
     */
    private JPanel buildRunRow() {
        buttonRun = new JButton("\u25B6 Run algorithm");
        buttonRun.setEnabled(false);
        buttonRun.setToolTipText(
                "Run the selected algorithm \u2014 if a result file is produced, "
                        + "the interface will automatically move on to the \"Visualize results\" tab");

        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER));
        row.add(buttonRun);
        return row;
    }

    private JPanel buildProgressRow() {
        progressBar = new AlgorithmProgressBar();
        JPanel row = new JPanel(new BorderLayout());
        row.setBorder(new EmptyBorder(4, 4, 4, 4));
        row.add(progressBar, BorderLayout.CENTER);
        return row;
    }

    private JTabbedPane buildBottomTabs() {
        consolePanel = new ConsolePanel(false);
        consolePanel.setPreferredSize(new Dimension(600, 200));

        runHistoryPanel = new RunHistoryPanel();
        runHistoryPanel.setPreferredSize(new Dimension(600, 200));

        bottomTabbedPane = new JTabbedPane();
        bottomTabbedPane.addTab("Console", consolePanel);
        bottomTabbedPane.addTab("Run history", runHistoryPanel);
        return bottomTabbedPane;
    }

    private void layoutStandardRow(JPanel row, JLabel label, JTextField field, Component b2, Component b3,
            Component b4) {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.gridy = 0;

        c.gridx = 0;
        c.anchor = GridBagConstraints.WEST;
        row.add(label, c);

        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        row.add(field, c);

        c.gridx = 2;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        row.add(b2, c);

        c.gridx = 3;
        row.add(b3, c);

        c.gridx = 4;
        row.add(b4, c);
    }

    private static JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(LABEL_FONT_BOLD);
        return label;
    }

    // =====================================================================
    // Compound updates
    // =====================================================================

    public void applyInitialVisibility() {
        try {
            updateUserInterfaceForAlgorithm(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Updates every section of the panel to reflect the newly selected
     * algorithm.
     *
     * @param algorithm the selected algorithm, or null
     */
    public void updateUserInterfaceForAlgorithm(DescriptionOfAlgorithm algorithm) throws Exception {
        buttonRun.setEnabled(algorithm != null);
        buttonGuide.setEnabled(algorithm != null);

        parameterPanel.update(algorithm);
        DescriptionOfParameter[] params = (algorithm == null) ? null : algorithm.getParametersDescription();
        boolean hasParams = params != null && params.length > 0;
        rowParameters.setVisible(hasParams);

        boolean hasOutput = algorithm != null && algorithm.getOutputFileTypes() != null;
        rowOutput.setVisible(hasOutput);

        boolean isDataMining = algorithm != null && AlgorithmType.DATA_MINING.equals(algorithm.getAlgorithmType());
        rowOptions.setVisible(isDataMining);

        revalidate();
        repaint();
    }

    // =====================================================================
    // Recent algorithms menu (filtered by the current input file type)
    // =====================================================================

    /**
     * Builds and shows the "Recent" algorithms popup menu, anchored below
     * {@link #buttonRecentAlgorithm}. The list of recent algorithms (from
     * {@link PreferencesManager#getRecentAlgorithms()}) is filtered so that
     * only algorithms compatible with {@link #currentInputFileType} are
     * shown (see {@link #isCompatibleWithCurrentInputFileType(String)}).
     * Selecting an entry updates the algorithm field and refreshes the rest
     * of the panel (parameters, output row visibility, etc.) directly.
     */
    private void showRecentAlgorithmsMenu() {
        List<String> recentAlgorithms = PreferencesManager.getInstance().getRecentAlgorithms();
        if (recentAlgorithms == null) {
            recentAlgorithms = new ArrayList<>();
        }

        List<String> compatibleAlgorithms = new ArrayList<>();
        for (String algorithmName : recentAlgorithms) {
            if (isCompatibleWithCurrentInputFileType(algorithmName)) {
                compatibleAlgorithms.add(algorithmName);
            }
        }

        JPopupMenu menu = new JPopupMenu();
        if (compatibleAlgorithms.isEmpty()) {
            String message;
            if (recentAlgorithms.isEmpty()) {
                message = "(no recent algorithms)";
            } else if (currentInputFileType != null && !currentInputFileType.trim().isEmpty()) {
                message = "(no recent algorithms compatible with: " + currentInputFileType + ")";
            } else {
                message = "(no recent algorithms)";
            }
            JMenuItem empty = new JMenuItem(message);
            empty.setEnabled(false);
            menu.add(empty);
        } else {
            for (String algorithmName : compatibleAlgorithms) {
                JMenuItem item = new JMenuItem(algorithmName);
                item.addActionListener(e -> selectRecentAlgorithm(algorithmName));
                menu.add(item);
            }
        }

        menu.show(buttonRecentAlgorithm, 0, buttonRecentAlgorithm.getHeight());
    }

    /**
     * Returns {@code true} if the algorithm with the given name is
     * compatible with {@link #currentInputFileType}.
     *
     * <p>The compatibility rules are:</p>
     * <ul>
     *   <li>If {@link #currentInputFileType} is unknown ({@code null} or
     *       empty), no filtering is applied and every algorithm is
     *       considered compatible — we cannot exclude anything without
     *       knowing the type.</li>
     *   <li>If {@link #currentInputFileType} is known:
     *     <ul>
     *       <li>Algorithms that declare <b>no</b> input file type
     *           ({@link DescriptionOfAlgorithm#getInputFileTypes()} returns
     *           {@code null} or an empty array) take <b>no</b> input file
     *           at all (e.g. data generators, standalone tools). They are
     *           <b>not</b> compatible with a file — they are excluded.</li>
     *       <li>Algorithms that declare an input file type are compatible
     *           only if the last element of that array matches
     *           {@link #currentInputFileType} (case-insensitive).</li>
     *     </ul>
     *   </li>
     *   <li>If the algorithm's description cannot be resolved, it is
     *       included rather than silently dropped.</li>
     * </ul>
     */
    private boolean isCompatibleWithCurrentInputFileType(String algorithmName) {
        // No file type known yet: cannot filter, show everything.
        if (currentInputFileType == null || currentInputFileType.trim().isEmpty()) {
            return true;
        }
        try {
            DescriptionOfAlgorithm d =
                    AlgorithmManager.getInstance().getDescriptionOfAlgorithm(algorithmName);
            if (d == null) {
                // Cannot resolve description: include rather than drop silently.
                return true;
            }
            String[] inputTypes = d.getInputFileTypes();
            // Null or empty means the algorithm takes NO input file at all
            // (e.g. a generator or a standalone tool). When we have a known
            // input file type, such algorithms are not applicable — exclude them.
            if (inputTypes == null || inputTypes.length == 0) {
                return false;
            }
            // The last element is the actual file type (earlier elements are
            // used for human-readable labels / sub-type hierarchies).
            String last = inputTypes[inputTypes.length - 1];
            return last != null && last.trim().equalsIgnoreCase(currentInputFileType.trim());
        } catch (Exception e) {
            // Cannot verify — include rather than drop silently.
            return true;
        }
    }

    /** Applies the given algorithm, chosen from the "Recent" popup menu. */
    private void selectRecentAlgorithm(String algorithmName) {
        try {
            setSelectedAlgorithm(algorithmName);
            DescriptionOfAlgorithm algorithm = AlgorithmManager.getInstance().getDescriptionOfAlgorithm(algorithmName);
            updateUserInterfaceForAlgorithm(algorithm);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // =====================================================================
    // Listener registration
    // =====================================================================

    public void addRunButtonListener(ActionListener l) {
        buttonRun.addActionListener(l);
    }

    public void addSelectAlgorithmButtonListener(ActionListener l) {
        buttonSelectAlgorithm.addActionListener(l);
    }

    /**
     * @deprecated the "Recent" algorithms menu is now built and filtered
     *             internally by this panel (see {@link #showRecentAlgorithmsMenu()}).
     *             This method is kept only so that additional listeners can
     *             still be attached to the button if needed; it is no
     *             longer required for the default "Recent" menu to work.
     */
    @Deprecated
    public void addRecentAlgorithmButtonListener(ActionListener l) {
        buttonRecentAlgorithm.addActionListener(l);
    }

    public void addGuideButtonListener(ActionListener l) {
        buttonGuide.addActionListener(l);
    }

    public void addOutputButtonListener(ActionListener l) {
        buttonOutput.addActionListener(l);
    }

    public void addRecentOutputButtonListener(ActionListener l) {
        buttonRecentOutput.addActionListener(l);
    }

    public void addOutputFileTextListener(DocumentListener l) {
        textFieldOutput.getDocument().addDocumentListener(l);
    }

    /**
     * Registers a listener that is notified whenever the currently selected
     * algorithm's name changes, however that happens: the algorithm
     * selector dialog, the "Recent" popup menu, or restoring a past run
     * from the history tab. {@code GuidedInterfaceController} uses this to
     * opportunistically suggest an output file name so the user is not
     * forced to type one on every single run.
     */
    public void addAlgorithmChangeListener(DocumentListener l) {
        textFieldAlgorithm.getDocument().addDocumentListener(l);
    }

    public void addRunAsExternalListener(ActionListener l) {
        chckbxRunAsExternal.addActionListener(l);
    }

    public void addMaxSecondsCheckboxListener(ActionListener l) {
        chckbxMaxSeconds.addActionListener(l);
    }

    public void addRerunHistoryListener(ActionListener l) {
        runHistoryPanel.addRerunListener(l);
    }

    public void addClearHistoryListener(ActionListener l) {
        runHistoryPanel.addClearHistoryListener(l);
    }

    // =====================================================================
    // State readers / writers
    // =====================================================================

    public String getSelectedAlgorithmName() {
        return textFieldAlgorithm.getText();
    }

    public void setSelectedAlgorithm(String name) {
        textFieldAlgorithm.setText(name == null ? "" : name);
    }

    public String[] getParameterValues() {
        return parameterPanel.getParameterValues();
    }

    public void setParameterValues(String[] values) {
        parameterPanel.setParameterValues(values);
    }

    public int getParameterCount() {
        return parameterPanel.getParameterCount();
    }

    public List<String> getMissingRequiredParameterNames() {
        return parameterPanel.getMissingRequiredParameterNames();
    }

    public String getOutputFileText() {
        return textFieldOutput.getText();
    }

    public void setOutputFileName(String name) {
        textFieldOutput.setText(name == null ? "" : name);
    }

    public boolean isOutputFileSectionVisible() {
        return rowOutput.isVisible();
    }

    /**
     * Updates the input file type mirrored from tab 1 (auto-detected from the
     * file's header, known from the generator/processor that produced it, or
     * chosen manually by the user there). This value is <b>not</b> displayed
     * in this panel — the input file's path and type are already shown in
     * the "Select data" tab, so repeating them here would be redundant. It
     * is kept only to filter the "Recent" algorithms popup menu (see
     * {@link #showRecentAlgorithmsMenu()}) so that only algorithms
     * compatible with this type are proposed.
     *
     * @param fileType the current input file type, or {@code null}/empty if unknown
     */
    public void setInputFileType(String fileType) {
        currentInputFileType = fileType;
    }

    /**
     * Returns the input file type currently known for the input file chosen
     * in tab 1 (see {@link #setInputFileType(String)}), or {@code null} if
     * unknown.
     */
    public String getInputFileType() {
        return currentInputFileType;
    }

    public boolean isRunAsExternal() {
        return chckbxRunAsExternal.isSelected();
    }

    public void setRunAsExternalSelected(boolean s) {
        chckbxRunAsExternal.setSelected(s);
    }

    public boolean isBeepOnCompletionSelected() {
        return chckbxBeepOnCompletion.isSelected();
    }

    public boolean isTimeLimitEnabled() {
        return chckbxMaxSeconds.isSelected();
    }

    public String getMaxSecondsText() {
        return textMaxSeconds.getText();
    }

    /**
     * Returns {@code true} if the content of the time-limit field is a
     * positive whole number of seconds. The checkbox state is <b>not</b>
     * taken into account: callers should combine this with
     * {@link #isTimeLimitEnabled()} (e.g. to refuse starting a run when the
     * time limit is enabled but its value is empty or "0").
     */
    public boolean isTimeLimitValueValid() {
        String text = textMaxSeconds.getText();
        if (text == null) {
            return false;
        }
        text = text.trim();
        if (text.isEmpty() || !text.matches("[0-9]+")) {
            return false;
        }
        try {
            return Integer.parseInt(text) > 0;
        } catch (NumberFormatException e) {
            return false; // too large for an int
        }
    }

    public void setMaxSecondsFieldEnabled(boolean enabled) {
        textMaxSeconds.setEnabled(enabled);
        textMaxSeconds.setEditable(enabled);
        validateMaxSecondsField();
    }

    public JButton getRecentAlgorithmButton() {
        return buttonRecentAlgorithm;
    }

    public JButton getRecentOutputButton() {
        return buttonRecentOutput;
    }

    public RunHistoryEntry getSelectedHistoryEntry() {
        return runHistoryPanel.getSelectedEntry();
    }

    public void addHistoryEntry(RunHistoryEntry entry) {
        runHistoryPanel.addEntry(entry);
    }

    public void clearHistory() {
        runHistoryPanel.clearHistory();
    }

    public void startElapsedTimer() {
        progressBar.startAlgorithm();
    }

    // =====================================================================
    // Run-state transitions
    // =====================================================================

    /**
     * Switches the panel to its "running" state: shows the progress bar,
     * turns the Run button into a Stop button, and locks every control
     * whose modification during a run would be confusing (algorithm
     * selection, output file, options) — the values captured at the start
     * of the run are the ones used, so editing them mid-run has no effect.
     */
    public void setUIToRunningState() {
        rowProgress.setVisible(true);
        progressBar.setIndeterminate(true);
        progressBar.setString("Running...");
        buttonRun.setText("\u25A0 Stop algorithm");
        buttonRun.setEnabled(true);

        buttonSelectAlgorithm.setEnabled(false);
        buttonRecentAlgorithm.setEnabled(false);

        textFieldOutput.setEditable(false);
        buttonOutput.setEnabled(false);
        buttonRecentOutput.setEnabled(false);

        chckbxMaxSeconds.setEnabled(false);
        textMaxSeconds.setEnabled(false);
        textMaxSeconds.setEditable(false);
        chckbxRunAsExternal.setEnabled(false);

        bottomTabbedPane.setSelectedIndex(TAB_CONSOLE);
        revalidate();
        repaint();
    }

    /** Restores the panel to its idle state after a run has finished or been stopped. */
    public void resetUIAfterThreadCompletion() {
        progressBar.endAlgorithm();
        buttonRun.setText("\u25B6 Run algorithm");
        buttonRun.setEnabled(!getSelectedAlgorithmName().trim().isEmpty());
        rowProgress.setVisible(false);

        buttonSelectAlgorithm.setEnabled(true);
        buttonRecentAlgorithm.setEnabled(true);

        textFieldOutput.setEditable(true);
        buttonOutput.setEnabled(true);
        buttonRecentOutput.setEnabled(true);

        chckbxMaxSeconds.setEnabled(true);
        setMaxSecondsFieldEnabled(chckbxMaxSeconds.isSelected());
        chckbxRunAsExternal.setEnabled(true);

        revalidate();
        repaint();
    }

    public void postStatusMessage(String msg) {
        consolePanel.postStatusMessage(msg);
    }

    public void appendConsoleLine(String line) {
        consolePanel.appendLine(line);
    }

    public void clearConsole() {
        consolePanel.clearConsole();
    }

    public void redirectOutputStream() {
        consolePanel.redirectOutputStream();
    }

    public void showConsoleTab() {
        bottomTabbedPane.setSelectedIndex(TAB_CONSOLE);
    }

    public void showHistoryTab() {
        bottomTabbedPane.setSelectedIndex(TAB_HISTORY);
    }
}