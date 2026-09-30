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

import java.io.File;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import ca.pfv.spmf.algorithmmanager.AlgorithmManager;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithmmanager.descriptions.DescriptionAlgoClusterViewer;
import ca.pfv.spmf.algorithmmanager.descriptions.DescriptionAlgoGraphViewerOpenFile;
import ca.pfv.spmf.algorithmmanager.descriptions.DescriptionAlgoSystemTextEditorOpenFile;
import ca.pfv.spmf.algorithms.timeseries.TimeSeries;
import ca.pfv.spmf.algorithms.timeseries.reader_writer.AlgoTimeSeriesReader;
import ca.pfv.spmf.gui.AlgorithmRunner;
import ca.pfv.spmf.gui.AlgorithmRunnerController;
import ca.pfv.spmf.gui.AlgorithmSelectorDialog;
import ca.pfv.spmf.gui.DialogSelectAlgorithmParameter;
import ca.pfv.spmf.gui.RunHistoryEntry;
import ca.pfv.spmf.gui.patternvizualizer.PatternVizualizer;
import ca.pfv.spmf.gui.preferences.PreferencesManager;
import ca.pfv.spmf.gui.texteditor.SPMFTextEditor;
import ca.pfv.spmf.gui.viewers.arffviewer.ARFFDatabaseViewer;
import ca.pfv.spmf.gui.viewers.csvviewer.CSVDatabaseViewer;
import ca.pfv.spmf.gui.viewers.fastaviewer.FastaViewer;
import ca.pfv.spmf.gui.viewers.timeseriesviewer.TimeSeriesViewer;

/**
 * Controller for the {@link GuidedInterface} three-tab interface. Wires
 * {@link DataSelectionPanel}, {@link AlgorithmSelectionPanel}, and
 * {@link ResultsPanel} together and implements the run/stop logic.
 *
 * <p>
 * Unlike {@link AlgorithmRunnerController}, this controller manages its own
 * lightweight {@link Thread} to run algorithms in-process (via
 * {@link DescriptionOfAlgorithm#runAlgorithm(String[], String, String)})
 * instead of delegating to {@link AlgorithmRunner}, since that class's
 * contract is tied specifically to {@link AlgorithmRunnerController}. As a
 * result, "Run in a separated process" is currently only a saved preference
 * in this prototype and does not yet spawn an external JVM.
 * </p>
 *
 * <p>
 * The controller also keeps the input file <b>type</b> — as determined or
 * chosen by the user in {@link DataSelectionPanel} (tab 1) — in sync with
 * {@link AlgorithmSelectionPanel} (tab 2), via {@link #syncInputFileType()}.
 * That type is not displayed again in tab 2 (it is already shown in tab 1);
 * it is used only to compute a "suggested algorithms" set (algorithms whose
 * declared input file type matches), which is passed to
 * {@link AlgorithmSelectorDialog} so that compatible algorithms are
 * highlighted in black and incompatible ones in light gray (without hiding
 * any of them), and to filter tab 2's "Recent" algorithms popup menu.
 * </p>
 *
 * <p>
 * The controller enforces the guided, step-by-step nature of the interface
 * in two complementary ways:
 * </p>
 * <ul>
 *   <li>it enables/disables tabs 2 and 3 (see
 *       {@link #updateAlgorithmTabAvailability()} and
 *       {@link #updateResultsTabAvailability(boolean)}) depending on whether
 *       their prerequisites (an input file; a successful run producing
 *       output) are currently satisfied;</li>
 *   <li>it wires the "Next" button exposed by {@link DataSelectionPanel}
 *       to move to tab 2, and keeps its enabled state in sync with tab 2's
 *       availability. On tab 2 itself there is no separate "Next" button:
 *       the "Run algorithm" button both executes the algorithm and, once a
 *       result file has been produced, automatically navigates to tab 3.</li>
 * </ul>
 *
 * @author Philippe Fournier-Viger
 */
public class GuidedInterfaceController {

    private final DataSelectionPanel dataPanel;
    private final AlgorithmSelectionPanel algorithmPanel;
    private final ResultsPanel resultsPanel;
    private final JTabbedPane tabbedPane;
    private final JFrame parentFrame;

    private String inputFile = null;
    private String outputFile = null;

    /**
     * The type of the current input file, as determined/chosen in
     * {@link DataSelectionPanel} (tab 1), kept in sync via
     * {@link #syncInputFileType()}. May be {@code null} if unknown.
     */
    private String currentInputFileType = null;

    /**
     * True while the output file text field is being updated
     * programmatically by {@link #maybeSuggestOutputFileName(DescriptionOfAlgorithm)},
     * so that {@link #syncOutputFileFromTextField()} can tell such updates
     * apart from the user actually typing/choosing their own output path.
     */
    private boolean updatingOutputFieldProgrammatically = false;

    /**
     * True while the current content of the output file field is a value
     * that was suggested automatically (see
     * {@link #maybeSuggestOutputFileName(DescriptionOfAlgorithm)}) rather
     * than chosen/typed by the user. Used to decide whether selecting a new
     * algorithm is allowed to update the suggested output file name, or
     * whether it must leave the user's own value untouched.
     */
    private boolean outputFileIsAutoSuggested = false;

    private volatile Thread currentThread;
    private volatile Thread killerThread;
    private volatile boolean wasStoppedByUser = false;

    private long runStartTimeMillis = 0L;
    private String[] lastParameters = new String[0];
    private String lastAlgorithmName = "";
    private String lastInputFile = "";
    private String lastOutputFile = "";

    public GuidedInterfaceController(DataSelectionPanel dataPanel, AlgorithmSelectionPanel algorithmPanel,
            ResultsPanel resultsPanel, JTabbedPane tabbedPane, JFrame parentFrame) {
        this.dataPanel = dataPanel;
        this.algorithmPanel = algorithmPanel;
        this.resultsPanel = resultsPanel;
        this.tabbedPane = tabbedPane;
        this.parentFrame = parentFrame;
    }

    // =========================================================================
    // Listener wiring
    // =========================================================================

    public void initializeUIEventHandling() {

        // ---- Data tab -------------------------------------------------------
        dataPanel.addSelectButtonListener(e -> askUserToChooseFile(false));
        dataPanel.addRecentButtonListener(e -> showRecentFilesMenu(false));
//      dataPanel.addViewButtonListener(e -> openInputFileWithViewer());
        dataPanel.addInputFileTextListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { syncInputFileFromTextField(); }
            @Override
            public void removeUpdate(DocumentEvent e) { syncInputFileFromTextField(); }
            @Override
            public void changedUpdate(DocumentEvent e) { syncInputFileFromTextField(); }
        });
        // Keep tab 2's mirrored file type in sync whenever it changes in tab 1
        // (whether auto-detected after loading/generating/processing a file,
        // or changed manually by the user via the file type combo box).
        dataPanel.addFileTypeChangeListener(e -> syncInputFileType());
        // Lets the user move on to tab 2 without having to click the tab
        // header directly. Only enabled once an input file has been chosen
        // (see updateAlgorithmTabAvailability()).
        dataPanel.addNextButtonListener(e -> tabbedPane.setSelectedIndex(GuidedInterface.TAB_ALGORITHM));

        // ---- Algorithm tab --------------------------------------------------
        algorithmPanel.addSelectAlgorithmButtonListener(e -> openAlgorithmSelectorDialog());
        algorithmPanel.addGuideButtonListener(e -> {
            try {
                openHelpWebPageForAlgorithm(algorithmPanel.getSelectedAlgorithmName());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        algorithmPanel.addOutputButtonListener(e -> askUserToChooseFile(true));
        algorithmPanel.addRecentOutputButtonListener(e -> showRecentFilesMenu(true));
        algorithmPanel.addOutputFileTextListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { syncOutputFileFromTextField(); }
            @Override
            public void removeUpdate(DocumentEvent e) { syncOutputFileFromTextField(); }
            @Override
            public void changedUpdate(DocumentEvent e) { syncOutputFileFromTextField(); }
        });
        // Whenever the algorithm shown in tab 2 changes — however that
        // happens (the algorithm selector dialog, the "Recent" popup menu,
        // or restoring a past run from the history tab) — opportunistically
        // suggest an output file name so the user is not forced to type one
        // on every single run.
        algorithmPanel.addAlgorithmChangeListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { onAlgorithmNameChanged(); }
            @Override
            public void removeUpdate(DocumentEvent e) { onAlgorithmNameChanged(); }
            @Override
            public void changedUpdate(DocumentEvent e) { onAlgorithmNameChanged(); }
        });
        algorithmPanel.addRunAsExternalListener(
                e -> PreferencesManager.getInstance().setRunAsExternalProgram(algorithmPanel.isRunAsExternal()));
        algorithmPanel.addMaxSecondsCheckboxListener(
                e -> algorithmPanel.setMaxSecondsFieldEnabled(algorithmPanel.isTimeLimitEnabled()));
        // The Run button is the single forward-navigation action on tab 2:
        // it executes the algorithm and, once a result file is produced,
        // the controller automatically moves on to tab 3 (see
        // notifyOfThreadComplete). No separate "Next" button is needed.
        algorithmPanel.addRunButtonListener(e -> processRunCommand());
        algorithmPanel.addRerunHistoryListener(e -> rerunHistoryEntry(algorithmPanel.getSelectedHistoryEntry()));
        algorithmPanel.addClearHistoryListener(e -> algorithmPanel.clearHistory());

        // ---- Results tab ----------------------------------------------------
        resultsPanel.addOpenButtonListener(e -> {
            String viewerMethod = resultsPanel.getSelectedViewerMethod();
            openSelectedViewer(viewerMethod, lastOutputFile);
        });

        // ---- Keep tab 2's mirrored input-file type synced with tab 1 --------
        // (used only internally by tab 2 to filter its "Recent" algorithms
        // menu; the file itself is not displayed again in tab 2).
        tabbedPane.addChangeListener(e -> {
            if (tabbedPane.getSelectedIndex() == GuidedInterface.TAB_ALGORITHM) {
                algorithmPanel.setInputFileType(currentInputFileType);
            }
        });
    }

    // =========================================================================
    // Tab availability (guided workflow gating)
    // =========================================================================

    /**
     * Enables/disables tab 2 ("Select & run algorithm") and its "Next"
     * button in tab 1, depending on whether an input file has been chosen —
     * running an algorithm without an input file rarely makes sense, so
     * this tab (and the shortcut to reach it) is kept unavailable until
     * that prerequisite is satisfied.
     */
    private void updateAlgorithmTabAvailability() {
        boolean hasInput = inputFile != null && !inputFile.trim().isEmpty();
        tabbedPane.setEnabledAt(GuidedInterface.TAB_ALGORITHM, hasInput);
        dataPanel.setNextButtonEnabled(hasInput);
    }

    /**
     * Enables/disables tab 3 ("Visualize results"). This tab only becomes
     * meaningful once an algorithm has been successfully run and has produced
     * an output file (see {@link #notifyOfThreadComplete(boolean)}).
     * Navigation to tab 3 is performed automatically by the controller once
     * that condition is met — the user does not need to click a separate
     * "Next" button on tab 2.
     *
     * @param enable whether tab 3 should become reachable
     */
    private void updateResultsTabAvailability(boolean enable) {
        tabbedPane.setEnabledAt(GuidedInterface.TAB_RESULTS, enable);
    }

    // =========================================================================
    // Output file name suggestion
    // =========================================================================

    /**
     * Called whenever the algorithm name shown in tab 2 changes (see
     * {@link AlgorithmSelectionPanel#addAlgorithmChangeListener(DocumentListener)}),
     * regardless of how it changed (algorithm selector dialog, "Recent"
     * popup menu, or a history "Rerun"/restore). Used to opportunistically
     * suggest an output file name via
     * {@link #maybeSuggestOutputFileName(DescriptionOfAlgorithm)}.
     */
    private void onAlgorithmNameChanged() {
        String algorithmName = algorithmPanel.getSelectedAlgorithmName();
        if (algorithmName == null || algorithmName.trim().isEmpty()) {
            return;
        }
        try {
            DescriptionOfAlgorithm algorithm =
                    AlgorithmManager.getInstance().getDescriptionOfAlgorithm(algorithmName);
            maybeSuggestOutputFileName(algorithm);
        } catch (Exception ex) {
            // Cannot resolve the algorithm's description — skip the
            // suggestion; the user can still type/choose an output path manually.
        }
    }

    /**
     * Pre-fills tab 2's output file field with a sensible suggested path,
     * computed from the current input file and the newly selected
     * algorithm's name (see {@link #buildSuggestedOutputFileName(String, String)}),
     * whenever the field does not already contain a value the user
     * explicitly chose/typed themselves (tracked via
     * {@link #outputFileIsAutoSuggested}). This avoids forcing the user to
     * manually type an output path on every run.
     *
     * @param algorithm the newly selected algorithm, or null
     */
    private void maybeSuggestOutputFileName(DescriptionOfAlgorithm algorithm) {
        if (algorithm == null || algorithm.getOutputFileTypes() == null) {
            return; // this algorithm does not produce an output file
        }

        String currentOutput = algorithmPanel.getOutputFileText();
        boolean fieldIsEmpty = currentOutput == null || currentOutput.trim().isEmpty();
        if (!fieldIsEmpty && !outputFileIsAutoSuggested) {
            // The field already holds a value the user explicitly chose or
            // typed themselves — never silently override it.
            return;
        }

        if (inputFile == null || inputFile.trim().isEmpty()) {
            return; // nothing to base a suggestion on yet
        }

        String suggested = buildSuggestedOutputFileName(inputFile, algorithm.getName());
        if (suggested == null || suggested.trim().isEmpty()) {
            return;
        }

        updatingOutputFieldProgrammatically = true;
        try {
            algorithmPanel.setOutputFileName(suggested);
        } finally {
            updatingOutputFieldProgrammatically = false;
        }
        outputFile = suggested;
        outputFileIsAutoSuggested = true;
    }

    /**
     * Builds a suggested output file path from the given input file path and
     * algorithm name: same directory as the input file, with the
     * algorithm's name and an "_output" suffix inserted before the input
     * file's extension (or ".txt" appended if the input file has none).
     *
     * @param inputFilePath the current input file path
     * @param algorithmName the name of the algorithm that will produce the output
     * @return the suggested output file path, or null if it could not be built
     */
    private String buildSuggestedOutputFileName(String inputFilePath, String algorithmName) {
        if (inputFilePath == null || inputFilePath.trim().isEmpty()) {
            return null;
        }
        File inputFileObj = new File(inputFilePath);
        String name = inputFileObj.getName();

        String baseName;
        String extension;
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = name.substring(0, dotIndex);
            extension = name.substring(dotIndex); // includes the leading dot
        } else {
            baseName = name;
            extension = ".txt";
        }

        String safeAlgorithmName = (algorithmName == null || algorithmName.trim().isEmpty())
                ? "output" : algorithmName.trim().replaceAll("[^a-zA-Z0-9_\\-]", "_");

        String suggestedName = baseName + "_" + safeAlgorithmName + "_output" + extension;

        String parent = inputFileObj.getParent();
        return (parent != null) ? new File(parent, suggestedName).getPath() : suggestedName;
    }

    // =========================================================================
    // File selection
    // =========================================================================

    private void askUserToChooseFile(boolean isOutput) {
        try {
            String previousPath = isOutput ? PreferencesManager.getInstance().getOutputFilePath()
                    : PreferencesManager.getInstance().getInputFilePath();

            JFileChooser fc = (previousPath != null) ? new JFileChooser(previousPath) : new JFileChooser();
            if (isOutput) {
                fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
            }

            int returnVal = isOutput ? fc.showSaveDialog(parentFrame) : fc.showOpenDialog(parentFrame);
            if (returnVal != JFileChooser.APPROVE_OPTION) {
                return;
            }

            File file = fc.getSelectedFile();
            if (isOutput) {
                algorithmPanel.setOutputFileName(file.getPath());
                outputFile = file.getPath();
                PreferencesManager.getInstance().setOutputFilePath(file.getParent());
                PreferencesManager.getInstance().addRecentOutputFile(file.getPath());
            } else {
                applyInputFile(file.getPath());
                PreferencesManager.getInstance().setInputFilePath(file.getParent());
                PreferencesManager.getInstance().addRecentInputFile(file.getPath());
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(parentFrame,
                    "An error occurred while opening the file dialog.\nERROR MESSAGE = " + e, "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Sets the given path as the current input file, updating tab 1 (text
     * field, View button, preview, file type) and tab 2's availability.
     *
     * @param path the input file path
     */
    private void applyInputFile(String path) {
        inputFile = path;
        // Updating the text field fires the document listener, which
        // re-syncs everything (preview, file type, tab availability).
        dataPanel.setInputFilePath(path);
        // Make sure the state is consistent even if the text field did not
        // fire (e.g. because the same path was already displayed).
        syncInputFileFromTextField();
    }

    private void showRecentFilesMenu(boolean isOutput) {
        List<String> recentFiles = isOutput ? PreferencesManager.getInstance().getRecentOutputFiles()
                : PreferencesManager.getInstance().getRecentInputFiles();

        JPopupMenu menu = new JPopupMenu();
        if (recentFiles == null || recentFiles.isEmpty()) {
            JMenuItem empty = new JMenuItem("(no recent files)");
            empty.setEnabled(false);
            menu.add(empty);
        } else {
            for (String filePath : recentFiles) {
                JMenuItem item = new JMenuItem(new File(filePath).getName());
                item.setToolTipText(filePath);
                item.addActionListener(e -> {
                    if (isOutput) {
                        algorithmPanel.setOutputFileName(filePath);
                        outputFile = filePath;
                    } else {
                        applyInputFile(filePath);
                    }
                });
                menu.add(item);
            }
        }

        JButton anchor = isOutput ? algorithmPanel.getRecentOutputButton() : dataPanel.getRecentButton();
        menu.show(anchor, 0, anchor.getHeight());
    }

    private void showRecentAlgorithmsMenu() {
        List<String> recentAlgorithms = PreferencesManager.getInstance().getRecentAlgorithms();
        JPopupMenu menu = new JPopupMenu();

        if (recentAlgorithms == null || recentAlgorithms.isEmpty()) {
            JMenuItem empty = new JMenuItem("(no recent algorithms)");
            empty.setEnabled(false);
            menu.add(empty);
        } else {
            for (String algorithmName : recentAlgorithms) {
                JMenuItem item = new JMenuItem(algorithmName);
                item.addActionListener(e -> {
                    try {
                        selectAlgorithm(algorithmName);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });
                menu.add(item);
            }
        }

        JButton anchor = algorithmPanel.getRecentAlgorithmButton();
        menu.show(anchor, 0, anchor.getHeight());
    }

    // =========================================================================
    // Algorithm selection
    // =========================================================================

    private void openAlgorithmSelectorDialog() {
        try {
            // Use the file type known from tab 1 (auto-detected or chosen by
            // the user) to compute which algorithms are "suggested" (i.e.
            // declare a matching input file type). Passing this set to the
            // dialog causes it to display compatible algorithms in black and
            // incompatible ones in light gray, without hiding any of them —
            // this is a soft suggestion rather than a hard filter, since the
            // detected/chosen type might occasionally be imprecise.
            Set<String> suggested = computeSuggestedAlgorithms(currentInputFileType);

            // NOTE: this prototype does not yet expose category filters like
            // MainWindow does, so all categories are shown.
            AlgorithmSelectorDialog dialog = new AlgorithmSelectorDialog(parentFrame, false, false, false, true, false,
                    suggested);
            dialog.setVisible(true);

            String chosen = dialog.getSelectedAlgorithm();
            if (chosen == null || chosen.trim().isEmpty()) {
                return;
            }
            selectAlgorithm(chosen);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parentFrame,
                    "Could not open the algorithm selector.\nERROR: " + ex.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    /**
     * Computes the set of algorithm names that are compatible with the given
     * input file type, i.e. whose declared input file type (the last element
     * of {@link DescriptionOfAlgorithm#getInputFileTypes()}) matches
     * {@code fileType} (case-insensitive). Algorithms that do not declare any
     * input file type are considered compatible with every type.
     *
     * @param fileType the current input file type, or {@code null}/empty if
     *                 unknown, in which case {@code null} is returned so
     *                 that the dialog does not highlight/filter anything.
     * @return the set of suggested algorithm names, or {@code null} if the
     *         file type is unknown
     */
    private Set<String> computeSuggestedAlgorithms(String fileType) {
        if (fileType == null || fileType.trim().isEmpty()) {
            return null;
        }
        Set<String> suggested = new HashSet<>();
        try {
            AlgorithmManager mgr = AlgorithmManager.getInstance();
            for (DescriptionOfAlgorithm d : mgr.getAlgorithms()) {
                String[] inputTypes = d.getInputFileTypes();
                if (inputTypes == null || inputTypes.length == 0) {
                    suggested.add(d.getName());
                    continue;
                }
                String last = inputTypes[inputTypes.length - 1];
                if (last != null && last.trim().equalsIgnoreCase(fileType.trim())) {
                    suggested.add(d.getName());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return suggested;
    }

    private void selectAlgorithm(String name) throws Exception {
        algorithmPanel.setSelectedAlgorithm(name);
        DescriptionOfAlgorithm algorithm = AlgorithmManager.getInstance().getDescriptionOfAlgorithm(name);
        algorithmPanel.updateUserInterfaceForAlgorithm(algorithm);
    }

    private void openHelpWebPageForAlgorithm(String algorithmName) throws Exception {
        if (algorithmName == null || algorithmName.trim().isEmpty()) {
            JOptionPane.showMessageDialog(parentFrame,
                    "Please first select an algorithm.", "No algorithm selected",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        DescriptionOfAlgorithm algorithm = AlgorithmManager.getInstance().getDescriptionOfAlgorithm(algorithmName);
        if (algorithm != null && algorithm.getURLOfDocumentation() != null) {
            try {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(algorithm.getURLOfDocumentation()));
            } catch (Exception e) {
                JOptionPane.showMessageDialog(parentFrame,
                        "Could not open the documentation page:\n" + algorithm.getURLOfDocumentation()
                                + "\n\nERROR MESSAGE = " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // =========================================================================
    // Input-file viewer (tab 1's "View" button)
    // =========================================================================

    private void openInputFileWithViewer() {
        try {
            if (inputFile == null || inputFile.isEmpty()) {
                JOptionPane.showMessageDialog(parentFrame,
                        "Please first select an input file using the Select button.", "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (inputFile.toLowerCase().endsWith(".arff")) {
                new ARFFDatabaseViewer(true, inputFile);
                return;
            }
            if (inputFile.toLowerCase().endsWith(".csv")) {
                new CSVDatabaseViewer(true, inputFile);
                return;
            }
            if (isFastaFile(inputFile)) {
                FastaViewer viewer = new FastaViewer(false);
                viewer.setVisible(true);
                viewer.load(inputFile);
                return;
            }

            // If an algorithm has already been chosen in tab 2, try to use the
            // viewer registered for its input type.
            String algorithmName = algorithmPanel.getSelectedAlgorithmName();
            if (algorithmName != null && !algorithmName.trim().isEmpty()) {
                DescriptionOfAlgorithm algorithm =
                        AlgorithmManager.getInstance().getDescriptionOfAlgorithm(algorithmName);
                if (algorithm != null && algorithm.getInputFileTypes() != null) {
                    DescriptionOfAlgorithm viewer = AlgorithmManager.getInstance()
                            .getViewerFor(algorithm.getInputFileTypes());
                    if (viewer != null) {
                        String[] params;
                        DescriptionOfParameter[] descriptions = viewer.getParametersDescription();
                        if (descriptions != null && descriptions.length > 0) {
                            params = new String[descriptions.length];
                            for (int i = 0; i < params.length; i++) {
                                String value = askUserValueForParameter(descriptions[i]);
                                if (value == null) {
                                    return; // user cancelled
                                }
                                params[i] = value;
                            }
                        } else {
                            params = new String[] {};
                        }
                        viewer.runAlgorithm(params, inputFile, null);
                        return;
                    }
                }
            }

            // Fallback: system text editor
            new DescriptionAlgoSystemTextEditorOpenFile().runAlgorithm(new String[] {}, inputFile, null);

        } catch (Exception e) {
            JOptionPane.showMessageDialog(parentFrame, "An error occurred. ERROR MESSAGE = " + e, "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private boolean isFastaFile(String filePath) {
        String lower = filePath.toLowerCase();
        return lower.endsWith(".fasta") || lower.endsWith(".fa")
                || lower.endsWith(".fna") || lower.endsWith(".ffn");
    }

    private String askUserValueForParameter(DescriptionOfParameter paramDescription) {
        DialogSelectAlgorithmParameter dialog =
                new DialogSelectAlgorithmParameter(paramDescription, parentFrame);
        return dialog.getUserInput();
    }

    // =========================================================================
    // Output viewer dispatch (tab 3's "Open using:" button)
    // =========================================================================

    private void openSelectedViewer(String viewerType, String filePath) {
        if (viewerType == null || viewerType.equals("Don't open")) {
            return;
        }
        if (filePath == null || filePath.trim().isEmpty()) {
            JOptionPane.showMessageDialog(parentFrame,
                    "There is no output file to open yet. Please run an algorithm first.",
                    "No output file", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            switch (viewerType) {
                case "System text editor":
                    new DescriptionAlgoSystemTextEditorOpenFile().runAlgorithm(new String[] {}, filePath, null);
                    break;
                case "SPMF text editor":
                    new SPMFTextEditor(false).openAFile(new File(filePath));
                    break;
                case "Pattern viewer":
                    new PatternVizualizer(filePath);
                    break;
                case "Time series viewer":
                    openTimeSeriesViewer(filePath);
                    break;
                case "Cluster viewer":
                    new DescriptionAlgoClusterViewer().runAlgorithm(new String[] {}, filePath, null);
                    break;
                case "Graph viewer":
                    new DescriptionAlgoGraphViewerOpenFile().runAlgorithm(new String[] {}, filePath, null);
                    break;
                default:
                    DescriptionOfAlgorithm viewer;
                    try {
                        viewer = AlgorithmManager.getInstance().getDescriptionOfAlgorithm(viewerType);
                    } catch (Exception e) {
                        viewer = null;
                    }
                    if (viewer == null) {
                        throw new UnsupportedOperationException("Viewer type not supported: " + viewerType);
                    }
                    viewer.runAlgorithm(new String[] {}, filePath, null);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(parentFrame,
                    "An error occurred while trying to open the output file with "
                            + viewerType + ". ERROR MESSAGE = " + e,
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Opens the given file in the time series viewer. The separator used to
     * read the file is taken from the "separator" parameter of the algorithm
     * that produced it (if it has such a parameter), otherwise "," is used.
     */
    private void openTimeSeriesViewer(String filePath) throws java.io.IOException {
        String separator = ",";
        try {
            DescriptionOfAlgorithm algorithm =
                    AlgorithmManager.getInstance().getDescriptionOfAlgorithm(lastAlgorithmName);
            if (algorithm != null && algorithm.getParametersDescription() != null) {
                DescriptionOfParameter[] descriptions = algorithm.getParametersDescription();
                for (int i = 0; i < descriptions.length && i < lastParameters.length; i++) {
                    DescriptionOfParameter description = descriptions[i];
                    if (description != null && description.name != null
                            && description.name.toLowerCase().contains("separator")) {
                        String value = lastParameters[i];
                        if (value != null && !value.isEmpty()) {
                            separator = value;
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            // Keep the default separator.
        }
        AlgoTimeSeriesReader reader = new AlgoTimeSeriesReader();
        List<TimeSeries> timeSeries = reader.runAlgorithm(filePath, separator);
        TimeSeriesViewer viewer = new TimeSeriesViewer(timeSeries);
        viewer.setVisible(true);
    }

    // =========================================================================
    // Validation
    // =========================================================================

    private boolean validateBeforeRun(String algorithmName) {
        List<String> problems = new ArrayList<>();

        if (algorithmName == null || algorithmName.trim().isEmpty()) {
            problems.add("No algorithm has been selected (see tab 2).");
            return reportProblemsIfAny(problems);
        }

        List<String> missingParams = algorithmPanel.getMissingRequiredParameterNames();
        if (missingParams != null && !missingParams.isEmpty()) {
            problems.add("Missing required parameter(s): " + String.join(", ", missingParams));
        }

        try {
            DescriptionOfAlgorithm algorithm =
                    AlgorithmManager.getInstance().getDescriptionOfAlgorithm(algorithmName);
            if (algorithm != null) {
                boolean needsInput  = algorithm.getInputFileTypes()  != null;
                boolean needsOutput = algorithm.getOutputFileTypes() != null;

                if (needsInput && (inputFile == null || inputFile.trim().isEmpty())) {
                    problems.add("No input file has been selected (see tab 1).");
                } else if (needsInput && !new File(inputFile).isFile()) {
                    problems.add("The input file does not exist: " + inputFile);
                }
                if (needsOutput && (outputFile == null || outputFile.trim().isEmpty())) {
                    problems.add("No output file has been selected.");
                }
            }
        } catch (Exception ex) {
            // Cannot verify — skip these checks rather than block a legitimate run.
        }

        return reportProblemsIfAny(problems);
    }

    private boolean reportProblemsIfAny(List<String> problems) {
        if (problems.isEmpty()) {
            return true;
        }
        StringBuilder msg = new StringBuilder(
                "The algorithm cannot be run for the following reason(s):\n\n");
        for (String p : problems) {
            msg.append("\u2022 ").append(p).append('\n');
        }
        msg.append("\nPlease fix the above and try again.");
        JOptionPane.showMessageDialog(parentFrame, msg.toString(),
                "Cannot run algorithm", JOptionPane.WARNING_MESSAGE);
        return false;
    }

    private boolean algorithmHasOutputFile(String algorithmName) {
        if (algorithmName == null || algorithmName.trim().isEmpty()) {
            return false;
        }
        try {
            DescriptionOfAlgorithm algorithm =
                    AlgorithmManager.getInstance().getDescriptionOfAlgorithm(algorithmName);
            return algorithm != null && algorithm.getOutputFileTypes() != null;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean isAlgorithmRunning() {
        Thread t = currentThread;
        return t != null && t.isAlive();
    }

    // =========================================================================
    // Run / Stop
    // =========================================================================

    private void processRunCommand() {
        // If already running, this click means Stop.
        if (isAlgorithmRunning()) {
            wasStoppedByUser = true;
            stopThread(currentThread);
            return;
        }

        final String algorithmName = algorithmPanel.getSelectedAlgorithmName();
        if (!validateBeforeRun(algorithmName)) {
            return;
        }

        final DescriptionOfAlgorithm algorithm;
        try {
            algorithm = AlgorithmManager.getInstance().getDescriptionOfAlgorithm(algorithmName);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(parentFrame,
                    "Could not find the algorithm: " + algorithmName, "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (algorithm == null) {
            JOptionPane.showMessageDialog(parentFrame,
                    "Could not find the algorithm: " + algorithmName, "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        SimpleDateFormat fmt = new SimpleDateFormat("hh:mm:ss aa");
        algorithmPanel.postStatusMessage("Algorithm is running... (" + fmt.format(new Date()) + ") \n");
        algorithmPanel.setUIToRunningState();
        algorithmPanel.startElapsedTimer();

        boolean hasOutput = algorithm.getOutputFileTypes() != null;
        if (hasOutput) {
            // Previous results (if any) are no longer valid for this new run.
            resultsPanel.clearPreview();
            updateResultsTabAvailability(false);
        }

        final String[] parameters = algorithmPanel.getParameterValues();
        wasStoppedByUser = false;
        runStartTimeMillis = System.currentTimeMillis();
        lastAlgorithmName = algorithmName;
        lastParameters = parameters != null
                ? Arrays.copyOf(parameters, parameters.length) : new String[0];
        lastInputFile = inputFile != null ? inputFile : "";
        lastOutputFile = outputFile != null ? outputFile : "";

        if (!algorithmName.trim().isEmpty()) {
            PreferencesManager.getInstance().addRecentAlgorithm(algorithmName);
        }
        if (inputFile != null && !inputFile.isEmpty()) {
            PreferencesManager.getInstance().addRecentInputFile(inputFile);
        }
        if (outputFile != null && !outputFile.isEmpty()) {
            PreferencesManager.getInstance().addRecentOutputFile(outputFile);
        }

        final String finalInput  = inputFile;
        final String finalOutput = outputFile;

        Thread t = new Thread(() -> {
            boolean success = true;
            Throwable failure = null;
            try {
                algorithm.runAlgorithm(parameters, finalInput, finalOutput);
            } catch (Throwable e) {
                success = false;
                // Interruption (Stop button / time limit) is not an error.
                if (!(e instanceof InterruptedException) && !Thread.currentThread().isInterrupted()) {
                    failure = e;
                }
            }
            final boolean finalSuccess = success;
            final Throwable finalFailure = failure;
            SwingUtilities.invokeLater(() -> {
                if (finalFailure != null && !(finalFailure instanceof ThreadDeath)) {
                    reportRunException(finalFailure);
                }
                notifyOfThreadComplete(finalSuccess);
            });
        }, "SPMF-Prototype-AlgorithmThread");

        currentThread = t;
        t.start();

        if (algorithmPanel.isTimeLimitEnabled()) {
            int maxTime = -1;
            try {
                maxTime = Integer.parseInt(algorithmPanel.getMaxSecondsText().trim());
            } catch (NumberFormatException ignored) {
            }
            startKillerThreadIfNeeded(maxTime, t);
        }
    }

    private void stopThread(Thread t) {
        if (t != null) {
            t.interrupt();
        }
    }

    private void startKillerThreadIfNeeded(int maxSeconds, Thread targetThread) {
        if (maxSeconds <= 0) {
            return;
        }
        Thread killer = new Thread(() -> {
            try {
                Thread.sleep(maxSeconds * 1000L);
            } catch (InterruptedException ignored) {
                return; // the run finished before the time limit
            }
            if (targetThread.isAlive()) {
                wasStoppedByUser = true;
                stopThread(targetThread);
            }
        }, "SPMF-Prototype-KillerThread");
        killer.setDaemon(true);
        killerThread = killer;
        killer.start();
    }

    private void reportRunException(Throwable e) {
        if (e instanceof NumberFormatException) {
            JOptionPane.showMessageDialog(parentFrame,
                    "Error. Please check the parameters of the algorithm. "
                            + "The format for numbers is incorrect.\n\n ERROR MESSAGE = " + e,
                    "Error", JOptionPane.ERROR_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(parentFrame,
                    "An error occurred while trying to run the algorithm.\nERROR MESSAGE = " + e,
                    "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
        algorithmPanel.clearConsole();
    }

    /**
     * Called on the EDT once the algorithm thread finishes (successfully,
     * on error, or stopped by the user). If the run succeeded and produced
     * an output file, tab 3 is enabled and the interface automatically
     * navigates there — the user does not need to click a separate "Next"
     * button. This is the single forward-navigation action that replaces
     * the old "Next" button on tab 2.
     */
    private void notifyOfThreadComplete(boolean success) {
        long durationMillis = System.currentTimeMillis() - runStartTimeMillis;
        currentThread = null;

        // The time limit watchdog (if any) is no longer needed.
        Thread killer = killerThread;
        killerThread = null;
        if (killer != null && killer.isAlive()) {
            killer.interrupt();
        }

        LocalDateTime completionTime = LocalDateTime.now();

        if (success || wasStoppedByUser) {
            String status = success ? RunHistoryEntry.STATUS_OK : RunHistoryEntry.STATUS_STOPPED;
            RunHistoryEntry entry = new RunHistoryEntry(
                    lastAlgorithmName,
                    lastInputFile,
                    lastOutputFile,
                    lastParameters, status, completionTime, durationMillis);
            algorithmPanel.addHistoryEntry(entry);
        }

        boolean hasOutput = algorithmHasOutputFile(lastAlgorithmName);

        if (success && hasOutput) {
            resultsPanel.updatePreview(lastOutputFile, lastAlgorithmName,
                    lastInputFile, completionTime, durationMillis);
            if (lastOutputFile != null && !lastOutputFile.isEmpty()) {
                // Enable tab 3 and navigate there automatically — the Run
                // button on tab 2 serves as the combined "run + next" action.
                updateResultsTabAvailability(true);
                tabbedPane.setSelectedIndex(GuidedInterface.TAB_RESULTS);
            }
        }

        if (hasOutput) {
            resultsPanel.flash();
            if (algorithmPanel.isBeepOnCompletionSelected()) {
                java.awt.Toolkit.getDefaultToolkit().beep();
            }
        }

        algorithmPanel.resetUIAfterThreadCompletion();
    }

    // =========================================================================
    // History re-run
    // =========================================================================

    private void rerunHistoryEntry(RunHistoryEntry entry) {
        if (entry == null) {
            return;
        }

        // processRunCommand() acts as "Stop" while a run is in progress; do
        // not silently abort the current run because of a history re-run.
        if (isAlgorithmRunning()) {
            JOptionPane.showMessageDialog(parentFrame,
                    "An algorithm is currently running. Please wait for it to finish\n"
                            + "or stop it before re-running a previous run.",
                    "Algorithm running", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            selectAlgorithm(entry.getAlgorithmName());
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            String entryInput  = entry.getInputFile();
            String entryOutput = entry.getOutputFile();

            inputFile  = (entryInput  == null || entryInput.isEmpty())  ? null : entryInput;
            outputFile = (entryOutput == null || entryOutput.isEmpty()) ? null : entryOutput;

            dataPanel.setInputFilePath(inputFile != null ? inputFile : "");
            syncInputFileFromTextField();
            algorithmPanel.setOutputFileName(outputFile != null ? outputFile : "");

            String[] storedParams = entry.getParameters();
            if (storedParams == null) {
                storedParams = new String[0];
            }
            int panelCount  = algorithmPanel.getParameterCount();
            int restoreCount = Math.min(storedParams.length, panelCount);
            if (restoreCount > 0) {
                String[] paramsToRestore = Arrays.copyOf(storedParams, panelCount);
                for (int i = restoreCount; i < panelCount; i++) {
                    paramsToRestore[i] = "";
                }
                algorithmPanel.setParameterValues(paramsToRestore);
            }

            processRunCommand();
        });
    }

    // =========================================================================
    // Text-field sync helpers
    // =========================================================================

    private void syncInputFileFromTextField() {
        String text = dataPanel.getInputFilePath();
        text = (text == null) ? "" : text.trim();
        if (text.isEmpty()) {
            inputFile = null;
            dataPanel.setViewButtonEnabled(false);
            dataPanel.clearPreview();
        } else {
            inputFile = text;
            // Only try to preview files that actually exist (the user may be
            // in the middle of typing a path).
            if (new File(text).isFile()) {
                dataPanel.setViewButtonEnabled(true);
                dataPanel.showPreviewOfFile(text);
            } else {
                dataPanel.setViewButtonEnabled(false);
                dataPanel.clearPreview();
            }
        }
        syncInputFileType();
        updateAlgorithmTabAvailability();
    }

    private void syncOutputFileFromTextField() {
        String text = algorithmPanel.getOutputFileText();
        text = (text == null) ? "" : text.trim();
        outputFile = text.isEmpty() ? null : text;
        if (!updatingOutputFieldProgrammatically) {
            // The user edited (or cleared) the output field themselves: it
            // no longer holds an automatically-suggested value, so future
            // algorithm changes must not silently overwrite it anymore.
            outputFileIsAutoSuggested = false;
        }
    }

    /**
     * Reads the file type currently known by {@link DataSelectionPanel} (tab
     * 1) — auto-detected from the file's {@code @FILETYPE} header, known from
     * the generator/processor algorithm that produced it, or chosen manually
     * by the user there — and mirrors it into {@link AlgorithmSelectionPanel}
     * (tab 2). It is not displayed again in tab 2; it is used only to compute
     * the "suggested algorithms" set when opening the algorithm selector
     * dialog, and to filter tab 2's "Recent" algorithms popup menu.
     */
    private void syncInputFileType() {
        currentInputFileType = dataPanel.getSelectedFileType();
        algorithmPanel.setInputFileType(currentInputFileType);
    }
}