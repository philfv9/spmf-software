package ca.pfv.spmf.gui.guided_interface;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import ca.pfv.spmf.algorithmmanager.AlgorithmManager;
import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithmmanager.descriptions.DescriptionAlgoClusterViewer;
import ca.pfv.spmf.algorithmmanager.descriptions.DescriptionAlgoGraphViewerOpenFile;
import ca.pfv.spmf.algorithmmanager.descriptions.DescriptionAlgoSystemTextEditorOpenFile;
import ca.pfv.spmf.algorithms.timeseries.TimeSeries;
import ca.pfv.spmf.algorithms.timeseries.reader_writer.AlgoTimeSeriesReader;
import ca.pfv.spmf.gui.AlgorithmRunnerPanel;
import ca.pfv.spmf.gui.AlgorithmViewerOptions;
import ca.pfv.spmf.gui.DialogSelectAlgorithmParameter;
import ca.pfv.spmf.gui.patternvizualizer.PatternVizualizer;
import ca.pfv.spmf.gui.texteditor.SPMFTextEditor;
import ca.pfv.spmf.gui.viewers.timeseriesviewer.TimeSeriesViewer;
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
 * This is the data selection panel (tab 1) of the guided interface of SPMF. 
 * 
 * @author Philippe Fournier-Viger
 */
@SuppressWarnings("serial")
public class DataSelectionPanel extends JPanel {

    private static final int MAX_PREVIEW_LINES = 200;
    private static final Font LABEL_FONT_BOLD    = new Font(Font.DIALOG, Font.BOLD, 12);
    private static final Font SECTION_TITLE_FONT = new Font(Font.DIALOG, Font.BOLD, 13);
    private static final Font HINT_FONT          = new Font(Font.DIALOG, Font.ITALIC, 11);
    private static final Color HINT_COLOR        = new Color(100, 100, 100);

    /** Base title of the preview section's border, before the line-count suffix is appended. */
    private static final String PREVIEW_TITLE_BASE = "Step 3 \u2014 Preview";

    /**
     * Text shown in the read-only file type label when no type could be
     * detected for the current file (or no file is loaded). The file type
     * is purely informational and used internally to tailor the tool/
     * algorithm suggestions — the user is never asked to choose it manually.
     */
    private static final String UNKNOWN_FILE_TYPE_TEXT = "Unknown";

    /**
     * Placeholder item shown (and disabled) in the "Process file with" and
     * "Calculate statistics" combo boxes when no registered algorithm of
     * that kind is compatible with the current file type, instead of
     * leaving the combo box empty (which looks like the list failed to
     * load).
     */
    private static final String NO_COMPATIBLE_ITEMS_PLACEHOLDER = "(none available for this file type)";

    /**
     * When the "View" popup is dismissed by a mouse press on the "View"
     * button itself, Swing's {@code MenuSelectionManager} closes it
     * <i>before</i> the button's own {@code mousePressed} handler runs. To
     * avoid instantly re-opening it in that case (which would make it
     * impossible to close the popup by clicking the button again), presses
     * arriving within this many milliseconds after the popup closed are
     * ignored.
     */
    private static final long VIEW_POPUP_REOPEN_GUARD_MS = 250L;

    // -----------------------------------------------------------------------
    // Section 1: file selection
    // -----------------------------------------------------------------------
    private JTextField textFieldInput;
    private JButton     buttonSelect;
    private JButton     buttonRecent;
    /** Opens {@link GenerateDatasetDialog} to create a new dataset from scratch. */
    private JButton     buttonGenerate;
    /**
     * Single "View" button offering, through a popup menu, every viewer
     * option applicable to the current file (text editors, and any
     * dedicated/advanced viewer suited to the file's type). This replaces
     * what used to be two separate controls: a "View with text editor"
     * button and a "View data with:" combo box + button.
     * <p>
     * It is deliberately placed on its own row, below the "Select...",
     * "Recent", and "Generate..." buttons: those three are all ways of
     * <i>acquiring</i> a file (choosing an existing one, picking a recently
     * used one, or creating a new one), whereas "View" acts on the file
     * that is already the current selection — a conceptually different
     * action that does not belong in the same button group.
     * <p>
     * <b>Important:</b> for mouse clicks, the popup menu is shown from a
     * {@link java.awt.event.MouseListener}'s {@code mousePressed} callback
     * (see {@link #installViewButtonPopupTrigger()}), <i>not</i> from an
     * {@link ActionListener} (which only fires on mouse <i>release</i>).
     * Showing the popup that late causes Swing's {@code MenuSelectionManager}
     * to treat the tail end of that same click gesture as a "click outside
     * the popup", closing it again immediately (a one-frame flash).
     * Triggering on press, like real drop-down/combo components do, avoids
     * this entirely. An {@link ActionListener} is nevertheless attached so
     * that the button also works from the keyboard (Space/Enter) — it only
     * opens the popup if it is not already visible, so for a mouse click it
     * is a no-op (the popup was already opened on press). For the same
     * reason, this button deliberately does <b>not</b> expose a generic
     * {@code addActionListener}-style hook to outside code.
     */
    private JButton     buttonView;
    /** The currently shown "View" popup menu, or {@code null}. */
    private JPopupMenu  viewPopupMenu;
    /** Time (ms) at which {@link #viewPopupMenu} was last closed; see {@link #VIEW_POPUP_REOPEN_GUARD_MS}. */
    private long        viewPopupClosedAt = 0L;

    /**
     * "Next" button letting the user move on to tab 2 without having to
     * click the tab header directly. Only enabled once an input file has
     * been chosen (see {@link #setNextButtonEnabled(boolean)}), mirroring
     * tab 2's own enabled state as managed by {@code GuidedInterfaceController}.
     */
    private JButton buttonNext;

    // -----------------------------------------------------------------------
    // Section 1b: file type — read-only, detected automatically. The user
    // is never asked to choose it manually: it is purely informational and
    // used internally to tailor the "Process file with" / "Calculate
    // statistics" / "View" suggestions to the current file.
    // -----------------------------------------------------------------------
    /** Shows the detected file type (e.g. "Transaction database"), or {@link #UNKNOWN_FILE_TYPE_TEXT}. */
    private JLabel labelFileTypeValue;
    /**
     * The file type currently detected for {@link #currentFilePath}, or
     * {@code null} if unknown. This is the value returned by
     * {@link #getSelectedFileType()} and used to filter the algorithm
     * lists — there is no user-editable combo box anymore, so this field is
     * the single source of truth for the current file's type.
     */
    private String currentDetectedFileType = null;
    /**
     * Listeners notified whenever {@link #currentDetectedFileType} changes
     * (i.e. whenever a new/different file type is detected, or detection
     * fails and the type becomes unknown again). Used by
     * {@code GuidedInterfaceController} to keep tab 2 informed, exactly as
     * it previously listened to the (now removed) file type combo box's
     * {@code ActionListener}.
     */
    private final List<ActionListener> fileTypeChangeListeners = new ArrayList<>();

    // -----------------------------------------------------------------------
    // Section 2a: process data (DATA_PROCESSOR), result is a new/updated file
    // -----------------------------------------------------------------------
    private JComboBox<String> comboProcessors;
    private JButton            buttonRunProcessor;
    private List<DescriptionOfAlgorithm> processorDescriptions = new ArrayList<>();
    /** Subset of {@link #processorDescriptions} matching the current file type; indices used by {@link #comboProcessors}. */
    private List<DescriptionOfAlgorithm> filteredProcessorDescriptions = new ArrayList<>();

    // -----------------------------------------------------------------------
    // Section 2b (merged into the "View" button of Section 1): view data
    // (DATA_VIEWER + generic viewer options). Rather than a combo box, the
    // available options are offered through a popup menu shown when the
    // "View" button (see {@link #buttonView}) is pressed.
    // -----------------------------------------------------------------------
    /**
     * All algorithms explicitly declared as {@link AlgorithmType#DATA_VIEWER}.
     * Used as one of the sources feeding {@link #filteredViewerLabels}, filtered
     * by the algorithm's declared input file type.
     */
    private List<DescriptionOfAlgorithm> viewerDescriptions = new ArrayList<>();
    /**
     * The labels currently offered in the "View" button's popup menu. Each
     * label is either:
     * <ul>
     *   <li>the name of a {@link DescriptionOfAlgorithm} (either one from
     *       {@link #viewerDescriptions}, or one of the specialized viewer
     *       algorithm names returned by
     *       {@link AlgorithmRunnerPanel#buildViewerOptionsForAlgorithm(DescriptionOfAlgorithm)}
     *       such as {@code Visualize_With_ItemItemset_Matrix}), or</li>
     *   <li>one of a handful of hard-coded generic labels ("System text
     *       editor", "SPMF text editor", "Pattern viewer", "Time series
     *       viewer", "Cluster viewer", "Graph viewer") that are handled
     *       specially in {@link #openViewerByLabel(String, String)}.</li>
     * </ul>
     */
    private List<String> filteredViewerLabels = new ArrayList<>();

    // -----------------------------------------------------------------------
    // Section 2c: statistics (result shown in a popup, not embedded)
    // -----------------------------------------------------------------------
    private JComboBox<String> comboStats;
    private JButton           buttonRunStats;
    private List<DescriptionOfAlgorithm> statsDescriptions = new ArrayList<>();
    /** Subset of {@link #statsDescriptions} matching the current file type; indices used by {@link #comboStats}. */
    private List<DescriptionOfAlgorithm> filteredStatsDescriptions = new ArrayList<>();

    // -----------------------------------------------------------------------
    // Section 3: preview area (gets almost all the vertical space)
    // -----------------------------------------------------------------------
    private JTextArea previewArea;
    private JScrollPane previewScrollPane;
    /**
     * Border of the preview section, whose title is updated dynamically to
     * reflect how many lines are actually shown (see
     * {@link #updatePreviewBorderTitle(int, boolean)}), e.g. "Preview (7
     * lines)" for a short file, or "Preview (first 200 lines)" only when the
     * file is actually longer than {@link #MAX_PREVIEW_LINES}.
     */
    private TitledBorder previewBorder;

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------
    /** The file path that will be forwarded to tab 2 as the input file. */
    private String currentFilePath = null;

    // -----------------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------------

    public DataSelectionPanel() {
        setLayout(new BorderLayout(8, 8));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(buildFileSection());
        top.add(Box.createVerticalStrut(8));
        top.add(buildToolsSection());

        add(top,                BorderLayout.NORTH);
        add(buildPreviewArea(), BorderLayout.CENTER);
        add(buildFooter(),      BorderLayout.SOUTH);

        loadStatsCalculators();
        loadProcessors();
        loadViewers();

        // No file type known yet: show every algorithm in each list.
        filterAlgorithmLists(null);
    }

    // -----------------------------------------------------------------------
    // Section 1: file selection
    // -----------------------------------------------------------------------

    private JPanel buildFileSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(titledSectionBorder("Step 1 \u2014 Input file"));

        GridBagConstraints c;

        // Row 0 — file path, plus the three ways of acquiring a file:
        // choosing an existing one ("Select..."), picking a previously used
        // one ("Recent"), or creating a brand new one ("Generate...").
        JLabel lblFile = new JLabel("File:");
        lblFile.setFont(LABEL_FONT_BOLD);

        textFieldInput = new JTextField();
        textFieldInput.setToolTipText("Type or paste the input file path, or use Select");

        buttonSelect = new JButton("Select...");

        buttonRecent = new JButton("Recent \u25BC");
        // The trailing "\u25BC" (small down-pointing triangle) marks this
        // as a button that opens a drop-down/popup menu.

        buttonGenerate = new JButton("Generate...");
        buttonGenerate.setToolTipText(
                "Opens a window to generate a dataset using a data-generator algorithm");
        buttonGenerate.addActionListener(e -> openGenerateDialog());

        c = gbc(0, 0);
        panel.add(lblFile, c);

        c = gbc(1, 0); c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(textFieldInput, c);

        c = gbc(2, 0); panel.add(buttonSelect, c);
        c = gbc(3, 0); panel.add(buttonRecent, c);
        c = gbc(4, 0); panel.add(buttonGenerate, c);

        // Row 1 — file type: read-only, detected automatically (header,
        // then extension, then the generator/processor that produced the
        // file). The user is never asked to pick it manually — it is shown
        // purely for information, and used internally to tailor the
        // "Process file with" / "Calculate statistics" / "View" suggestions.
        JLabel lblFileType = new JLabel("File type:");
        lblFileType.setFont(LABEL_FONT_BOLD);

        labelFileTypeValue = new JLabel(UNKNOWN_FILE_TYPE_TEXT);
        labelFileTypeValue.setForeground(HINT_COLOR);
        labelFileTypeValue.setToolTipText(
                "The type of the input file, detected automatically from its \"@FILETYPE\" header, "
                        + "its extension (.fasta/.fa/.fna/.ffn, .arff, .csv), or from the generator/processor "
                        + "algorithm used to produce it. It is used to suggest compatible tools and algorithms "
                        + "for this file \u2014 there is nothing to configure here.");

        c = gbc(0, 1);
        panel.add(lblFileType, c);
        c = gbc(1, 1); c.gridwidth = 4; c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(labelFileTypeValue, c);

        // Row 2 — "View" button, on its own row (see the field's javadoc
        // for why it is kept apart from the three buttons above).
        buttonView = new JButton("\uD83D\uDD0D View \u25BC");
        buttonView.setEnabled(false);
        buttonView.setToolTipText(
                "Open the current file with a viewer (a simple text editor, or a dedicated/"
                        + "advanced viewer suited to the file's type, if available) \u2014 click to choose from the menu");
        installViewButtonPopupTrigger();

        JPanel viewRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        viewRow.add(buttonView);

        c = gbc(1, 2); c.gridwidth = 4; c.anchor = GridBagConstraints.EAST; c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(viewRow, c);

        return panel;
    }

    /**
     * Builds the footer shown at the bottom of the whole tab, containing the
     * "Next" button that lets the user move on to tab 2 ("Select & run
     * algorithm") without having to click the tab header directly. The
     * button starts disabled and is only enabled once an input file has
     * been chosen (see {@link #setNextButtonEnabled(boolean)}), mirroring
     * tab 2's own gating.
     */
    private JPanel buildFooter() {
        buttonNext = new JButton("Next: Select & run algorithm \u2192");
        buttonNext.setEnabled(false);
        buttonNext.setToolTipText(
                "Go to the \"Select & run algorithm\" tab \u2014 enabled once an input file has been chosen");

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        footer.add(buttonNext);
        return footer;
    }

    /**
     * Wires the "View" button so that clicking it opens the viewer popup
     * menu. For the mouse this is done through a
     * {@link java.awt.event.MouseListener#mousePressed} callback (showing
     * the popup immediately when the mouse goes down), exactly like a combo
     * box's drop-down — <b>not</b> through a regular {@link ActionListener},
     * which only fires on mouse <i>release</i> and is what previously caused
     * the popup to flash open and immediately close again.
     * <p>
     * An {@link ActionListener} is still attached for <b>keyboard</b>
     * activation (Space/Enter), which never goes through {@code mousePressed}.
     * It opens the popup only if it is not already showing, so for a mouse
     * click (popup already opened on press) it does nothing.
     */
    private void installViewButtonPopupTrigger() {
        buttonView.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!buttonView.isEnabled() || !SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                // If this very press just closed the popup (MenuSelectionManager
                // runs before us), do not immediately re-open it.
                if (System.currentTimeMillis() - viewPopupClosedAt < VIEW_POPUP_REOPEN_GUARD_MS) {
                    return;
                }
                showViewPopupMenu();
            }
        });
        buttonView.addActionListener(e -> {
            if (viewPopupMenu == null || !viewPopupMenu.isVisible()) {
                showViewPopupMenu();
            }
        });
    }

    // -----------------------------------------------------------------------
    // Section 2: optional data tools (process / statistics)
    // -----------------------------------------------------------------------

    private JPanel buildToolsSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(titledSectionBorder("Step 2 \u2014 Data tools (optional)"));

        GridBagConstraints c;
        int row = 0;

        // Process row
        JLabel lblProcess = new JLabel("Process file with:");
        lblProcess.setFont(LABEL_FONT_BOLD);
        comboProcessors = new JComboBox<>();
        buttonRunProcessor = new JButton("\u2699 Process...");
        buttonRunProcessor.setToolTipText(
                "Transform the current file (e.g. filter/convert) and optionally use the result as the new input file");
        buttonRunProcessor.addActionListener(e -> openProcessDialog());

        c = gbc(0, row);
        panel.add(lblProcess, c);
        c = gbc(1, row); c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(comboProcessors, c);
        c = gbc(2, row);
        panel.add(buttonRunProcessor, c);
        row++;

        JLabel lblProcessHint = new JLabel(
                "Applies a transformation algorithm (filter, convert, clean, ...) to the current file.");
        lblProcessHint.setFont(HINT_FONT);
        lblProcessHint.setForeground(HINT_COLOR);
        c = gbc(1, row); c.gridwidth = 2; c.insets = new Insets(0, 6, 8, 6);
        panel.add(lblProcessHint, c);
        row++;

        // Statistics row (result shown in a popup)
        JLabel lblStats = new JLabel("Calculate statistics:");
        lblStats.setFont(LABEL_FONT_BOLD);
        comboStats     = new JComboBox<>();
        buttonRunStats = new JButton("\u25B6 Calculate");
        buttonRunStats.setToolTipText("Run the selected statistics calculator on the current file "
                + "and show the result in a popup");
        buttonRunStats.addActionListener(e -> runStatsCalculatorAndShowPopup());

        c = gbc(0, row);
        panel.add(lblStats, c);
        c = gbc(1, row); c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(comboStats, c);
        c = gbc(2, row);
        panel.add(buttonRunStats, c);
        row++;

        JLabel lblStatsHint = new JLabel(
                "Computes statistics about the current file and displays them in a popup window.");
        lblStatsHint.setFont(HINT_FONT);
        lblStatsHint.setForeground(HINT_COLOR);
        c = gbc(1, row); c.gridwidth = 2; c.insets = new Insets(0, 4, 4, 6);
        panel.add(lblStatsHint, c);

        return panel;
    }

    private JScrollPane buildPreviewArea() {
        previewArea = new JTextArea();
        previewArea.setEditable(false);
        previewArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        previewScrollPane = new JScrollPane(previewArea);
        previewBorder = titledSectionBorder(PREVIEW_TITLE_BASE);
        previewScrollPane.setBorder(previewBorder);
        previewScrollPane.setPreferredSize(new Dimension(700, 380));
        return previewScrollPane;
    }

    private static TitledBorder titledSectionBorder(String title) {
        TitledBorder border = BorderFactory.createTitledBorder(title);
        border.setTitleFont(SECTION_TITLE_FONT);
        return border;
    }

    /**
     * Updates the title of the Step 3 preview section's border to reflect
     * how many lines are actually shown in the preview:
     * <ul>
     *   <li>if the file is not truncated (it has {@link #MAX_PREVIEW_LINES}
     *       lines or fewer), the title shows the exact number of lines,
     *       e.g. "Preview (7 lines)" or "Preview (1 line)";</li>
     *   <li>if the file is longer than {@link #MAX_PREVIEW_LINES} and was
     *       therefore truncated, the title shows
     *       "Preview (first 200 lines)" instead, since only a prefix of the
     *       file is actually displayed;</li>
     *   <li>if no file is currently loaded (or it could not be read), the
     *       title is shown without any line count.</li>
     * </ul>
     *
     * @param lineCount the number of lines actually shown in the preview
     * @param truncated {@code true} if the file has more lines than what is shown
     */
    private void updatePreviewBorderTitle(int lineCount, boolean truncated) {
        String title;
        if (truncated) {
            title = PREVIEW_TITLE_BASE + " (first " + MAX_PREVIEW_LINES + " lines)";
        } else if (lineCount <= 0) {
            title = PREVIEW_TITLE_BASE;
        } else if (lineCount == 1) {
            title = PREVIEW_TITLE_BASE + " (1 line)";
        } else {
            title = PREVIEW_TITLE_BASE + " (" + lineCount + " lines)";
        }

        if (previewBorder != null) {
            previewBorder.setTitle(title);
        }
        if (previewScrollPane != null) {
            previewScrollPane.repaint();
        }
    }

    // -----------------------------------------------------------------------
    // File type detection & algorithm-list filtering
    // -----------------------------------------------------------------------

    /**
     * Try to determine the file type of the given file,
     * using the following strategy:
     *
     * <ol>
     *   <li><b>Header line</b>: if the first line of the file is of the form
     *       {@code @FILETYPE="Transaction database"}, the quoted value is
     *       returned directly.</li>
     *   <li><b>File extension</b>: if the filename ends with a recognized
     *       extension the corresponding canonical SPMF type name is returned:
     *       <ul>
     *         <li>{@code .fasta}, {@code .fa}, {@code .fna}, {@code .ffn}
     *             → {@code "FASTA file"}</li>
     *         <li>{@code .arff} → {@code "ARFF file"}</li>
     *         <li>{@code .csv}  → {@code "CSV file"}</li>
     *       </ul>
     *   </li>
     * </ol>
     *
     * Returns {@code null} when neither strategy succeeds, in which case the
     * file type is simply shown as "Unknown" — there is no manual fallback
     * anymore, since the user is never asked to pick the type themselves.
     *
     * @param filePath the path of the file to inspect
     * @return the detected file type string, or {@code null} if unknown
     */
    private String detectFileTypeAutomatically(String filePath) {
        // --- Step 1: check the @FILETYPE header (within the first 10 lines) ---
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String marker = "@FILETYPE=";
            String line;
            int lineCount = 0;
            while (lineCount < 10 && (line = reader.readLine()) != null) {
                lineCount++;
                String trimmed = line.trim();
                if (trimmed.startsWith(marker)) {
                    String value = trimmed.substring(marker.length()).trim();
                    // Strip double-quotes if there is
                    if (value.length() >= 2
                            && value.startsWith("\"")
                            && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    value = value.trim();
                    if (!value.isEmpty()) {
                        return value;
                    }
                }
            }
        } catch (IOException e) {
            // Cannot read the file — do extension check.
        }

        // --- Step 2: infer type from the file extension ------------------
        String lower = filePath.toLowerCase();
        if (lower.endsWith(".fasta")
                || lower.endsWith(".fa")
                || lower.endsWith(".fna")
                || lower.endsWith(".ffn")) {
            return "FASTA file";
        }
        if (lower.endsWith(".arff")) {
            return "ARFF file";
        }
        if (lower.endsWith(".csv")) {
            return "CSV file";
        }

        // Unknown.
        return null;
    }

    /**
     * Determines the type of the given file and updates the read-only file
     * type label and the filtered algorithm lists accordingly. Since there
     * is no manual override anymore, the outcome is always one of:
     * <ul>
     *   <li>the known/detected type (shown as-is), or</li>
     *   <li>{@link #UNKNOWN_FILE_TYPE_TEXT}, if detection failed — in which
     *       case every algorithm is shown, unfiltered, in the Process/
     *       Statistics/View lists.</li>
     * </ul>
     *
     * @param filePath      the file whose type must be determined
     * @param knownFileType the file type if it is already known (e.g.
     *                      because the file was produced by a generator or
     *                      processor algorithm whose output type is known),
     *                      or {@code null}/empty if it must be detected
     *                      automatically (header then extension)
     */
    private void determineAndApplyFileType(String filePath, String knownFileType) {
        String fileType = (knownFileType != null && !knownFileType.trim().isEmpty())
                ? knownFileType.trim() : null;

        if (fileType == null) {
            // Try header first, then extension.
            fileType = detectFileTypeAutomatically(filePath);
        }

        currentDetectedFileType = fileType;

        if (fileType != null) {
            labelFileTypeValue.setText(fileType);
            labelFileTypeValue.setForeground(null); // default (normal) text color
            filterAlgorithmLists(fileType);
        } else {
            labelFileTypeValue.setText(UNKNOWN_FILE_TYPE_TEXT);
            labelFileTypeValue.setForeground(HINT_COLOR);
            filterAlgorithmLists(null); // show every algorithm since the type is unknown
        }

        notifyFileTypeChangeListeners();
    }

    /**
     * Refreshes the Process/Statistics combo boxes, and the list of viewer
     * options offered by the "View" button's popup menu, so that they only
     * show algorithms/options compatible with {@code fileType}. If
     * {@code fileType} is {@code null} or empty, no filtering is applied
     * (every algorithm is shown).
     */
    private void filterAlgorithmLists(String fileType) {
        applyFilter(comboProcessors, buttonRunProcessor, processorDescriptions, filteredProcessorDescriptions, fileType);
        refreshViewerLabels(fileType);
        applyFilter(comboStats, buttonRunStats, statsDescriptions, filteredStatsDescriptions, fileType);
    }

    /**
     * Rebuilds {@code combo} and {@code filteredListOut} from
     * {@code masterList}, keeping only the algorithms compatible with
     * {@code fileType} (or all of them if {@code fileType} is unknown).
     * <p>
     * If, after filtering, no algorithm is compatible with {@code fileType},
     * an explicit, disabled placeholder item
     * ({@link #NO_COMPATIBLE_ITEMS_PLACEHOLDER}) is shown instead of leaving
     * {@code combo} empty — an empty combo box looks like the list simply
     * failed to load, which is confusing. {@code combo} itself, and
     * {@code associatedButton} (if any), are disabled in that case so the
     * user cannot try to run a nonexistent tool.
     *
     * @param combo            the combo box to rebuild
     * @param associatedButton the button that triggers the action associated
     *                         with {@code combo} (e.g. "Process..." or
     *                         "Calculate"), or {@code null} if none;
     *                         disabled together with {@code combo} when no
     *                         compatible algorithm is found
     * @param masterList       the full list of candidate algorithms
     * @param filteredListOut  receives the filtered subset (cleared first)
     * @param fileType         the current input file type, or {@code null}/
     *                         empty if unknown
     */
    private void applyFilter(JComboBox<String> combo, JButton associatedButton, List<DescriptionOfAlgorithm> masterList,
            List<DescriptionOfAlgorithm> filteredListOut, String fileType) {

        String previousSelection = (String) combo.getSelectedItem();

        combo.removeAllItems();
        filteredListOut.clear();

        boolean noFilter = (fileType == null) || fileType.trim().isEmpty();

        for (DescriptionOfAlgorithm d : masterList) {
            if (noFilter || matchesFileType(d, fileType)) {
                filteredListOut.add(d);
                combo.addItem(d.getName());
            }
        }

        if (filteredListOut.isEmpty()) {
            // Nothing matches the current file type: make this explicit
            // instead of leaving the combo box empty and looking broken.
            combo.addItem(NO_COMPATIBLE_ITEMS_PLACEHOLDER);
            combo.setEnabled(false);
            if (associatedButton != null) {
                associatedButton.setEnabled(false);
            }
            return;
        }

        combo.setEnabled(true);
        if (associatedButton != null) {
            associatedButton.setEnabled(true);
        }
        if (previousSelection != null && !NO_COMPATIBLE_ITEMS_PLACEHOLDER.equals(previousSelection)) {
            combo.setSelectedItem(previousSelection); // no-op if no longer present
        }
        if (combo.getSelectedIndex() < 0 && combo.getItemCount() > 0) {
            combo.setSelectedIndex(0);
        }
    }

    /**
     * Rebuilds {@link #filteredViewerLabels} for the given file type,
     * combining two sources of viewer options:
     * <ol>
     *   <li>Algorithms explicitly declared as {@link AlgorithmType#DATA_VIEWER}
     *       (from {@link #viewerDescriptions}) whose declared input file type
     *       matches {@code fileType} (or all of them, if no specific type is
     *       known).</li>
     *   <li>A set of generic, general-purpose viewer options tailored to
     *       {@code fileType} — such as "System text editor", "SPMF text
     *       editor", "Pattern viewer", "Time series viewer", "Cluster
     *       viewer", "Graph viewer", and specialized visualizers — computed
     *       via {@link AlgorithmViewerOptions#buildViewerOptionsForAlgorithm(DescriptionOfAlgorithm)},
     *       the same logic used to populate the viewer choices in the
     *       "Visualize results" tab. Since that method needs a
     *       {@link DescriptionOfAlgorithm} to read the output file type(s)
     *       from, a representative algorithm that <i>produces</i> files of
     *       {@code fileType} is looked up via
     *       {@link #findAlgorithmProducingFileType(String)}.</li>
     * </ol>
     * Duplicate labels (e.g. an algorithm name appearing in both sources)
     * are only added once. Once the list is built, the most specific
     * {@link AlgorithmType#DATA_VIEWER} for {@code fileType} — as reported by
     * {@link AlgorithmManager#getViewerFor(String[])} — is moved to the front
     * of the list (if present), so that it appears first/most prominently in
     * the "View" button's popup menu.
     *
     * @param fileType the current input file type, or {@code null}/empty if unknown
     */
    private void refreshViewerLabels(String fileType) {
        filteredViewerLabels.clear();

        boolean noFilter = (fileType == null) || fileType.trim().isEmpty();

        // ---- Source 1: algorithms explicitly registered as DATA_VIEWER ----
        for (DescriptionOfAlgorithm d : viewerDescriptions) {
            if (noFilter || matchesFileType(d, fileType)) {
                addViewerOption(d.getName());
            }
        }

        // ---- Source 2: generic viewer options tailored to the file type ---
        if (!noFilter) {
            DescriptionOfAlgorithm representative = findAlgorithmProducingFileType(fileType);
            if (representative != null) {
                try {
                    String[] genericOptions =
                    		AlgorithmViewerOptions.buildViewerOptionsForAlgorithm(representative);
                    if (genericOptions != null) {
                        for (String option : genericOptions) {
                            addViewerOption(option);
                        }
                    }
                } catch (Exception e) {
                    // Ignore — fall back to whatever specific viewers were
                    // already added from viewerDescriptions above.
                }
            }
            // Always make sure the universal text editors are offered, even
            // if no representative algorithm produces this file type.
            addViewerOption("System text editor");
            addViewerOption("SPMF text editor");
        } else {
            // No specific file type known yet: still offer the universal
            // fallback viewers so the user is never left with an empty list.
            addViewerOption("System text editor");
            addViewerOption("SPMF text editor");
        }

        // Move the most specific viewer (if any) to the front of the list,
        // so it stands out as the recommended choice in the popup menu.
        if (!noFilter) {
            try {
                DescriptionOfAlgorithm mostSpecificViewer =
                        AlgorithmManager.getInstance().getViewerFor(new String[] { fileType });
                if (mostSpecificViewer != null) {
                    String label = mostSpecificViewer.getName();
                    if (filteredViewerLabels.remove(label)) {
                        filteredViewerLabels.add(0, label);
                    }
                }
            } catch (Exception e) {
                // Ignore — keep the list in its natural order.
            }
        }
    }

    /**
     * Adds {@code label} to {@link #filteredViewerLabels} if it is
     * non-empty and not already present.
     *
     * @param label the viewer option label (an algorithm name, or a generic
     *              hard-coded label such as "System text editor")
     */
    private void addViewerOption(String label) {
        if (label == null || label.trim().isEmpty()) {
            return;
        }
        if (!filteredViewerLabels.contains(label)) {
            filteredViewerLabels.add(label);
        }
    }

    /**
     * Searches every known algorithm for one that <b>produces</b> (as
     * output) files of the given type, i.e. whose declared output file type
     * (last element of {@link DescriptionOfAlgorithm#getOutputFileTypes()})
     * matches {@code fileType} (case-insensitive). The first match found is
     * returned as a representative algorithm, used solely to feed
     * {@link AlgorithmViewerOptions#buildViewerOptionsForAlgorithm(DescriptionOfAlgorithm)}
     * — any algorithm sharing that output type would yield the same viewer
     * option list.
     *
     * @param fileType the file type to search for
     * @return a representative algorithm producing that file type, or
     *         {@code null} if none was found
     */
    private DescriptionOfAlgorithm findAlgorithmProducingFileType(String fileType) {
        try {
            AlgorithmManager mgr = AlgorithmManager.getInstance();
            for (DescriptionOfAlgorithm d : mgr.getAlgorithms()) {
                String[] outputTypes = d.getOutputFileTypes();
                if (outputTypes != null && outputTypes.length > 0) {
                    String last = outputTypes[outputTypes.length - 1];
                    if (last != null && last.trim().equalsIgnoreCase(fileType.trim())) {
                        return d;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Returns {@code true} if the given algorithm accepts {@code fileType}
     * as input, i.e. the last element of its
     * {@link DescriptionOfAlgorithm#getInputFileTypes()} array matches
     * {@code fileType} (case-insensitive). Algorithms that do not declare
     * any input file type are considered compatible with every type, so
     * that they are not hidden by mistake.
     */
    private boolean matchesFileType(DescriptionOfAlgorithm d, String fileType) {
        String[] inputTypes = d.getInputFileTypes();
        if (inputTypes == null || inputTypes.length == 0) {
            return true;
        }
        String last = inputTypes[inputTypes.length - 1];
        return last != null && last.trim().equalsIgnoreCase(fileType.trim());
    }

    /**
     * Returns the file type currently detected for the input file, or
     * {@code null} if it is unknown. There is no manual override anymore —
     * this always reflects automatic detection (header, extension, or the
     * generator/processor algorithm that produced the file).
     */
    public String getSelectedFileType() {
        return currentDetectedFileType;
    }

    /**
     * Registers a listener that is notified whenever the detected file
     * type changes — i.e. whenever a file is loaded/generated/processed
     * and a (possibly different, possibly unknown) type is detected for
     * it. This allows other panels (such as the algorithm selection panel)
     * to stay in sync with the current file's type and tailor/highlight
     * their own algorithm lists accordingly.
     */
    public void addFileTypeChangeListener(ActionListener l) {
        fileTypeChangeListeners.add(l);
    }

    /** Notifies every listener registered via {@link #addFileTypeChangeListener(ActionListener)}. */
    private void notifyFileTypeChangeListeners() {
        ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "fileTypeChanged");
        for (ActionListener l : new ArrayList<>(fileTypeChangeListeners)) {
            l.actionPerformed(event);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Returns the owning {@link Frame}, or {@code null} if there is none (or it is not a Frame). */
    private Frame getOwnerFrame() {
        Window ancestor = SwingUtilities.getWindowAncestor(this);
        return (ancestor instanceof Frame) ? (Frame) ancestor : null;
    }

    /**
     * Checks that a current file is set <b>and</b> exists on disk, showing
     * a warning dialog otherwise.
     *
     * @return {@code true} if an action can be performed on the current file
     */
    private boolean checkCurrentFileUsable() {
        if (currentFilePath == null || currentFilePath.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please select or generate a dataset first.",
                    "No file selected", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        if (!new File(currentFilePath).isFile()) {
            JOptionPane.showMessageDialog(this,
                    "The selected file does not exist:\n" + currentFilePath,
                    "File not found", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
    }

    // -----------------------------------------------------------------------
    // Generate-dataset dialog
    // -----------------------------------------------------------------------

    /**
     * Opens the {@link GenerateDatasetDialog}. If the user successfully
     * generates a dataset, the resulting file becomes the current file for
     * this tab and its preview is shown immediately.
     */
    private void openGenerateDialog() {
        GenerateDatasetDialog dialog = new GenerateDatasetDialog(getOwnerFrame());
        dialog.setVisible(true); // blocks until closed

        String generatedFile = dialog.getGeneratedFilePath();
        if (generatedFile != null && !generatedFile.trim().isEmpty()) {
            setInputFilePath(generatedFile);

            // If the dialog can tell us which generator algorithm was used,
            // its description tells us the exact type of file produced (the
            // last element of getOutputFileTypes()), so there is no need to
            // guess it from the file's header/extension.
            String knownFileType = null;
            try {
                DescriptionOfAlgorithm generatorDescription = dialog.getGeneratorAlgorithmDescription();
                if (generatorDescription != null) {
                    String[] outputTypes = generatorDescription.getOutputFileTypes();
                    if (outputTypes != null && outputTypes.length > 0) {
                        knownFileType = outputTypes[outputTypes.length - 1];
                    }
                }
            } catch (Exception e) {
                // Fall back to auto-detection from header/extension.
            }

            showPreviewOfFile(generatedFile, knownFileType);
            setViewButtonEnabled(true);
        }
    }

    // -----------------------------------------------------------------------
    // Data processing (DATA_PROCESSOR algorithms)
    // -----------------------------------------------------------------------

    private void loadProcessors() {
        try {
            AlgorithmManager mgr = AlgorithmManager.getInstance();
            for (DescriptionOfAlgorithm d : mgr.getAlgorithms()) {
                if (AlgorithmType.DATA_PROCESSOR.equals(d.getAlgorithmType())) {
                    processorDescriptions.add(d);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Opens the {@link ProcessDatasetDialog} for the currently selected
     * processor algorithm, running it on the current file. If processing
     * succeeds, the user is asked whether the produced file should become
     * the new current file (and its preview shown).
     */
    private void openProcessDialog() {
        if (!checkCurrentFileUsable()) {
            return;
        }

        int idx = comboProcessors.getSelectedIndex();
        if (idx < 0 || idx >= filteredProcessorDescriptions.size()) {
            JOptionPane.showMessageDialog(this,
                    "Please select a data processing tool.",
                    "No tool selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        DescriptionOfAlgorithm processor = filteredProcessorDescriptions.get(idx);

        ProcessDatasetDialog dialog = new ProcessDatasetDialog(getOwnerFrame(), processor, currentFilePath);
        dialog.setVisible(true); // blocks until closed

        String processedFile = dialog.getProcessedFilePath();
        if (processedFile != null && !processedFile.trim().isEmpty()) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "The dataset was processed successfully:\n" + processedFile
                            + "\n\nUse this file as the current input file?",
                    "Processing complete", JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                setInputFilePath(processedFile);

                // The processor's own description tells us the type of file
                // it produces, so there is no need to inspect the file
                // header/extension.
                String knownFileType = null;
                String[] outputTypes = processor.getOutputFileTypes();
                if (outputTypes != null && outputTypes.length > 0) {
                    knownFileType = outputTypes[outputTypes.length - 1];
                }

                showPreviewOfFile(processedFile, knownFileType);
                setViewButtonEnabled(true);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Data viewing (DATA_VIEWER algorithms + generic viewer options),
    // triggered from the "View" button's popup menu
    // -----------------------------------------------------------------------

    /**
     * Collects every algorithm explicitly declared as
     * {@link AlgorithmType#DATA_VIEWER} into {@link #viewerDescriptions}.
     * This is only <b>one</b> of the sources used to populate
     * {@link #filteredViewerLabels} — see {@link #refreshViewerLabels(String)}
     * for how it is combined with the generic, type-driven viewer options
     * built via
     * {@link AlgorithmViewerOptions#buildViewerOptionsForAlgorithm(DescriptionOfAlgorithm)}.
     */
    private void loadViewers() {
        try {
            AlgorithmManager mgr = AlgorithmManager.getInstance();
            for (DescriptionOfAlgorithm d : mgr.getAlgorithms()) {
                if (AlgorithmType.DATA_VIEWER.equals(d.getAlgorithmType())) {
                    viewerDescriptions.add(d);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Shows, below the "View" button, a popup menu listing every viewer
     * option applicable to the current file (refreshed just before display,
     * based on the currently detected file type). Selecting an item opens
     * the current file with that viewer, via {@link #openSelectedViewer(String)}.
     * <p>
     * For mouse clicks this method is called synchronously from
     * {@link #installViewButtonPopupTrigger() the button's mousePressed
     * handler} — i.e. it is shown <i>while the mouse button is still down</i>,
     * exactly like a combo box's drop-down. This is what actually prevents
     * the "flash open then instantly close" bug: Swing's
     * {@code MenuSelectionManager} dismisses popups upon seeing a
     * <i>mouse-press</i> event outside of them, so showing the popup any
     * later (e.g. from an {@code ActionListener}, which only runs on mouse
     * <i>release</i>) means the popup is born right before/at the tail of a
     * click gesture that {@code MenuSelectionManager} can still interpret as
     * "outside", closing it again in the same instant.
     */
    private void showViewPopupMenu() {
        if (!checkCurrentFileUsable()) {
            return;
        }

        // Make sure the list of viewer options reflects the current file type.
        refreshViewerLabels(getSelectedFileType());

        if (filteredViewerLabels.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No viewer is available for this file.",
                    "No viewer available", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JPopupMenu popup = new JPopupMenu();
        for (String label : filteredViewerLabels) {
            JMenuItem item = new JMenuItem(label);
            item.addActionListener(e -> openSelectedViewer(label));
            popup.add(item);
        }
        popup.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) { }
            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                viewPopupClosedAt = System.currentTimeMillis();
            }
            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
                viewPopupClosedAt = System.currentTimeMillis();
            }
        });
        viewPopupMenu = popup;
        popup.show(buttonView, 0, buttonView.getHeight());
    }

    /**
     * Opens the current file with the viewer identified by
     * {@code viewerLabel} — dispatching to
     * {@link #openViewerByLabel(String, String)}. Viewers typically open
     * their own dedicated window (e.g. a graphical/table/pattern/time-series/
     * cluster/graph viewer, or a text editor), so no popup is shown on
     * success; the opened window is itself the feedback. Any exception is
     * reported via a dialog since this is a user-initiated action from this
     * tab.
     *
     * @param viewerLabel the label of the viewer option that was chosen
     *                    from the "View" button's popup menu
     */
    private void openSelectedViewer(String viewerLabel) {
        if (!checkCurrentFileUsable()) {
            return;
        }

        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
        try {
            openViewerByLabel(viewerLabel, currentFilePath);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "An error occurred while opening the viewer:\n" + e,
                    "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        } finally {
            setCursor(java.awt.Cursor.getDefaultCursor());
        }
    }

    /**
     * Dispatches the given viewer label to the appropriate viewer
     * implementation, mirroring the logic used by the "Visualize results"
     * tab (see {@code GuidedInterfaceController.openSelectedViewer}), but
     * applied here to the current <b>input</b> file rather than an output
     * file.
     *
     * @param viewerLabel one of the hard-coded generic labels, or the name
     *                    of a registered {@link DescriptionOfAlgorithm}
     * @param filePath    the file to open
     * @throws Exception if the viewer could not be opened
     */
    private void openViewerByLabel(String viewerLabel, String filePath) throws Exception {
        switch (viewerLabel) {
            case "System text editor":
                new DescriptionAlgoSystemTextEditorOpenFile().runAlgorithm(new String[] {}, filePath, null);
                return;
            case "SPMF text editor":
                new SPMFTextEditor(false).openAFile(new File(filePath));
                return;
            case "Pattern viewer":
                new PatternVizualizer(filePath);
                return;
            case "Time series viewer":
                openTimeSeriesViewerForFile(filePath);
                return;
            case "Cluster viewer":
                new DescriptionAlgoClusterViewer().runAlgorithm(new String[] {}, filePath, null);
                return;
            case "Graph viewer":
                new DescriptionAlgoGraphViewerOpenFile().runAlgorithm(new String[] {}, filePath, null);
                return;
            default:
                runNamedAlgorithmAsViewer(viewerLabel, filePath);
        }
    }

    /**
     * Reads the given file as a time series database (using a comma as the
     * default column separator) and opens it in the {@link TimeSeriesViewer}.
     *
     * @param filePath the file to read and display
     * @throws IOException if the file cannot be read
     */
    private void openTimeSeriesViewerForFile(String filePath) throws IOException {
        String separator = ",";
        AlgoTimeSeriesReader reader = new AlgoTimeSeriesReader();
        List<TimeSeries> timeSeries = reader.runAlgorithm(filePath, separator);
        TimeSeriesViewer viewer = new TimeSeriesViewer(timeSeries);
        viewer.setVisible(true);
    }

    /**
     * Looks up the algorithm registered under {@code algorithmName} and runs
     * it directly on {@code filePath}, prompting the user for any declared
     * parameters beforehand. Used both for algorithms explicitly declared as
     * {@link AlgorithmType#DATA_VIEWER} and for the specialized viewer
     * algorithm names returned by
     * {@link AlgorithmViewerOptions#buildViewerOptionsForAlgorithm(DescriptionOfAlgorithm)}
     * (e.g. {@code Visualize_With_ItemItemset_Matrix}).
     *
     * @param algorithmName the registered name of the viewer algorithm
     * @param filePath      the file to open
     * @throws Exception if the algorithm cannot be found or fails to run
     */
    private void runNamedAlgorithmAsViewer(String algorithmName, String filePath) throws Exception {
        DescriptionOfAlgorithm viewer = AlgorithmManager.getInstance().getDescriptionOfAlgorithm(algorithmName);
        if (viewer == null) {
            throw new UnsupportedOperationException("Viewer not found: " + algorithmName);
        }

        // DialogSelectAlgorithmParameter requires a JFrame (not just a Frame)
        // as its owner, so we look up the ancestor window as a JFrame here.
        Window ancestor = SwingUtilities.getWindowAncestor(this);
        JFrame ownerFrame = (ancestor instanceof JFrame) ? (JFrame) ancestor : null;

        // Ask for parameter values, if any.
        DescriptionOfParameter[] paramDescriptions = viewer.getParametersDescription();
        String[] params;
        if (paramDescriptions != null && paramDescriptions.length > 0) {
            params = new String[paramDescriptions.length];
            for (int i = 0; i < paramDescriptions.length; i++) {
                DialogSelectAlgorithmParameter dialog =
                        new DialogSelectAlgorithmParameter(paramDescriptions[i], ownerFrame);
                String value = dialog.getUserInput();
                if (value == null && !paramDescriptions[i].isOptional()) {
                    return; // user cancelled a required parameter
                }
                params[i] = value == null ? "" : value;
            }
        } else {
            params = new String[] {};
        }

        viewer.runAlgorithm(params, filePath, null);
    }

    // -----------------------------------------------------------------------
    // Statistics
    // -----------------------------------------------------------------------

    private void loadStatsCalculators() {
        try {
            AlgorithmManager mgr = AlgorithmManager.getInstance();
            for (DescriptionOfAlgorithm d : mgr.getAlgorithms()) {
                if (AlgorithmType.DATA_STATS_CALCULATOR.equals(d.getAlgorithmType())) {
                    statsDescriptions.add(d);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Runs the selected statistics calculator on the current file and shows
     * its output (captured from {@code System.out}) in a popup dialog,
     * instead of occupying permanent space in the tab.
     */
    private void runStatsCalculatorAndShowPopup() {
        if (!checkCurrentFileUsable()) {
            return;
        }

        int idx = comboStats.getSelectedIndex();
        if (idx < 0 || idx >= filteredStatsDescriptions.size()) {
            JOptionPane.showMessageDialog(this,
                    "Please select a statistics calculator.",
                    "No calculator selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        DescriptionOfAlgorithm stats = filteredStatsDescriptions.get(idx);

        PrintStream originalOut = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String errorMessage = null;
        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
        try {
            System.setOut(new PrintStream(baos, true));
            stats.runAlgorithm(new String[]{}, currentFilePath, null);
        } catch (Throwable e) {
            errorMessage = e.toString();
            e.printStackTrace();
        } finally {
            System.out.flush();
            System.setOut(originalOut);
            setCursor(java.awt.Cursor.getDefaultCursor());
        }

        String output = baos.toString();
        if (errorMessage != null) {
            output += "\n[ERROR] " + errorMessage;
        }
        if (output.trim().isEmpty()) {
            output = "(No output was produced.)";
        }

        JTextArea area = new JTextArea(output);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setCaretPosition(0);

        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(550, 400));

        JOptionPane.showMessageDialog(this, scroll,
                "Statistics: " + stats.getName(), JOptionPane.INFORMATION_MESSAGE);
    }

    // -----------------------------------------------------------------------
    // Preview handling
    // -----------------------------------------------------------------------

    /**
     * Loads and displays a preview of the given file, and (re)determines its
     * file type automatically (header, then extension), if possible; the
     * file type label simply shows "Unknown" otherwise.
     */
    public void showPreviewOfFile(String filePath) {
        showPreviewOfFile(filePath, null);
    }

    /**
     * Loads and displays a preview of the given file. The Step 3 section's
     * border title is updated to reflect the number of lines actually
     * shown (see {@link #updatePreviewBorderTitle(int, boolean)}).
     *
     * @param filePath      the file to preview
     * @param knownFileType the file type if it is already known (e.g. the
     *                      file was produced by a generator/processor whose
     *                      output type is known), or {@code null}/empty to
     *                      have it detected automatically (header then
     *                      extension)
     */
    private void showPreviewOfFile(String filePath, String knownFileType) {
        if (filePath == null || filePath.trim().isEmpty()) {
            previewArea.setText("");
            updatePreviewBorderTitle(0, false);
            return;
        }
        currentFilePath = filePath;

        StringBuilder sb = new StringBuilder();
        int lineCount = 0;
        boolean truncated = false;
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while (lineCount < MAX_PREVIEW_LINES && (line = reader.readLine()) != null) {
                sb.append(line).append('\n');
                lineCount++;
            }
            if (reader.readLine() != null) {
                sb.append("... (truncated) ...\n");
                truncated = true;
            }
            previewArea.setText(sb.toString());
            previewArea.setCaretPosition(0);
            updatePreviewBorderTitle(lineCount, truncated);
        } catch (IOException e) {
            previewArea.setText("Could not read file: " + e.getMessage());
            updatePreviewBorderTitle(0, false);
        }

        // Always (re)determine the file type for the *current* file, even if
        // it could not be read — otherwise the label and the filtered
        // algorithm lists would keep reflecting the previous file's type.
        determineAndApplyFileType(filePath, knownFileType);
    }

    /** Clears both the preview area and its border title, and resets the file type label. */
    public void clearPreview() {
        previewArea.setText("");
        currentFilePath = null;
        updatePreviewBorderTitle(0, false);

        currentDetectedFileType = null;
        if (labelFileTypeValue != null) {
            labelFileTypeValue.setText(UNKNOWN_FILE_TYPE_TEXT);
            labelFileTypeValue.setForeground(HINT_COLOR);
        }
        filterAlgorithmLists(null);
        notifyFileTypeChangeListeners();
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Sets the path shown in the input file text field. Note that
     * {@code setText} synchronously fires the field's document listeners
     * (e.g. the controller's, which loads the preview or clears it if the
     * file does not exist), so {@link #currentFilePath} is assigned
     * <b>before</b> the text is set — that way the listeners' outcome
     * (preview loaded / cleared) is the final state, instead of being
     * silently overwritten afterwards.
     */
    public void setInputFilePath(String path) {
        currentFilePath = (path == null || path.trim().isEmpty()) ? null : path;
        textFieldInput.setText(path == null ? "" : path);
    }

    public String getInputFilePath() {
        return textFieldInput.getText();
    }

    /**
     * Returns the file path that should be forwarded to tab 2 as the
     * algorithm input — either the selected, generated, or processed file.
     */
    public String getCurrentFilePath() {
        return currentFilePath;
    }

    public void setViewButtonEnabled(boolean enabled) {
        buttonView.setEnabled(enabled);
    }

    public JButton getRecentButton() {
        return buttonRecent;
    }

    /**
     * Registers a listener invoked when the "Next" button (which moves the
     * user on to tab 2) is clicked.
     */
    public void addNextButtonListener(ActionListener l) {
        buttonNext.addActionListener(l);
    }

    /**
     * Enables/disables the "Next" button, kept in sync by
     * {@code GuidedInterfaceController} with tab 2's own enabled state
     * (which requires an input file to have been chosen).
     */
    public void setNextButtonEnabled(boolean enabled) {
        buttonNext.setEnabled(enabled);
    }

    // -----------------------------------------------------------------------
    // Listener registration
    // -----------------------------------------------------------------------

    public void addSelectButtonListener(ActionListener l) { buttonSelect.addActionListener(l); }
    public void addRecentButtonListener(ActionListener l) { buttonRecent.addActionListener(l); }

    // NOTE: there is intentionally no addViewButtonListener(...) method.
    // The "View" button is fully self-contained: it always shows the viewer
    // popup menu (see installViewButtonPopupTrigger() and
    // showViewPopupMenu()), and attaching any extra external listener to it
    // (as used to be done for the old, single-purpose "View with text
    // editor" button) would run on mouse *release* — right after the popup
    // has opened on mouse *press* — and could steal focus from the popup,
    // instantly closing it again. If outside code needs to react to a file
    // being viewed, it should be notified from within this class instead
    // (e.g. by adding a dedicated callback around openSelectedViewer(...),
    // if that need ever arises).

    public void addInputFileTextListener(DocumentListener l) {
        textFieldInput.getDocument().addDocumentListener(l);
    }

    // -----------------------------------------------------------------------
    // Layout helper
    // -----------------------------------------------------------------------

    private static GridBagConstraints gbc(int x, int y) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx   = x;
        c.gridy   = y;
        c.insets  = new Insets(4, 6, 4, 6);
        c.anchor  = GridBagConstraints.WEST;
        c.fill    = GridBagConstraints.NONE;
        c.weightx = 0;
        c.weighty = 0;
        return c;
    }
}