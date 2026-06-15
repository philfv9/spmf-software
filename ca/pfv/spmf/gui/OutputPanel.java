package ca.pfv.spmf.gui;
/*
 * Copyright (c) 2008-2015 Philippe Fournier-Viger
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
 * Do not remove copyright, authorship and license information.
 */
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

import ca.pfv.spmf.gui.pattern_diff_analyzer.PatternDiffAnalyzer;

/**
 * A reusable panel that shows a preview of the most recent output file
 * (first {@value #MAX_PREVIEW_LINES} lines) and provides buttons:
 *
 * <ul>
 *   <li><b>"View output with ▼"</b> — clicking it pops up a menu listing
 *       every available viewer method so the user can choose how to open
 *       the file.  This replaces the old combo-box + separate button
 *       design.  The available items are supplied at runtime via
 *       {@link #setViewerOptions(String[])}.</li>
 *   <li><b>"Open folder"</b> — opens the folder that contains the output
 *       file in the platform's default file manager.</li>
 *   <li><b>"Compare with..."</b> — opens the Pattern Diff Analyzer with
 *       the current output file pre-loaded as File A, allowing the user
 *       to select a second file and compare patterns.</li>
 * </ul>
 *
 * <p>The panel owner (typically {@link AlgorithmRunnerController}) registers
 * a single {@link ActionListener} via {@link #addOpenButtonListener}.  When
 * the user picks a viewer from the popup menu the listener's
 * {@link ActionListener#actionPerformed} is called; the controller then
 * calls {@link #getSelectedViewerMethod()} to find out which item was
 * chosen.</p>
 *
 * <p>The "Open folder" button is handled entirely inside this panel and
 * does not require any controller involvement.</p>
 *
 * @author Philippe Fournier-Viger
 */
@SuppressWarnings("serial")
public class OutputPanel extends JPanel {

    // =====================================================================
    // Constants
    // =====================================================================

    /** Number of lines shown in the preview area. */
    private static final int MAX_PREVIEW_LINES = 20;

    /** Font used for the preview text area. */
    private static final Font PREVIEW_FONT =
            new Font(Font.MONOSPACED, Font.PLAIN, 12);

    /** Foreground colour for the "no output file" placeholder message. */
    private static final Color COLOR_PLACEHOLDER = new Color(120, 120, 120);

    // =====================================================================
    // Widgets
    // =====================================================================

    /** Scrollable text area that shows the first N lines of the output file. */
    private final JTextArea previewArea;

    /** Label above the preview area that shows the output file path. */
    private final JLabel    lblFilePath;

    /**
     * "View output with ▼" button.  Clicking it shows {@link #viewerPopup}.
     */
    private final JButton   btnOpen;

    /**
     * "Open folder" button.  Clicking it opens the parent directory of the
     * current output file in the platform's default file manager.
     */
    private final JButton   btnOpenFolder;

    /**
     * "Compare with..." button.  Clicking it opens the Pattern Diff Analyzer
     * with the current output file pre-loaded as File A (File 1).  The user
     * can then select a second file inside that tool and run the comparison.
     */
    private final JButton   btnContrastWith;

    /**
     * Popup menu shown when the user clicks {@link #btnOpen}.
     * Items are rebuilt every time {@link #setViewerOptions(String[])} is
     * called.
     */
    private final JPopupMenu viewerPopup;

    // =====================================================================
    // State
    // =====================================================================

    /**
     * The viewer method that was most recently selected from the popup menu.
     * Initialised to an empty string; never {@code null}.
     */
    private String selectedViewerMethod = "";

    /**
     * The full path of the output file currently being previewed, or an
     * empty string if no file has been set yet.
     */
    private String currentOutputFilePath = "";

    /**
     * The list of viewer-method names currently shown in {@link #viewerPopup}.
     * Kept so that {@link #rebuildPopup()} can reconstruct the menu whenever
     * options change.
     */
    private String[] currentViewerOptions = new String[0];

    /**
     * Every {@link ActionListener} registered via
     * {@link #addOpenButtonListener}.  All listeners are notified together
     * when the user selects a viewer from the popup menu.
     */
    private final List<ActionListener> openListeners = new ArrayList<>();

    // =====================================================================
    // Construction
    // =====================================================================

    /**
     * Constructs an empty {@code OutputPanel}.  Call
     * {@link #updatePreview(String)} to load a file and
     * {@link #setViewerOptions(String[])} to populate the viewer popup.
     */
    public OutputPanel() {
        setLayout(new BorderLayout(4, 4));
        setBorder(new EmptyBorder(4, 4, 4, 4));

        // -----------------------------------------------------------------
        // Top bar: file-path label + action buttons
        // -----------------------------------------------------------------
        JPanel topBar = new JPanel(new BorderLayout(8, 0));
        topBar.setOpaque(false);

        lblFilePath = new JLabel(" ");
        lblFilePath.setFont(new Font(Font.DIALOG, Font.PLAIN, 11));
        lblFilePath.setForeground(new Color(80, 80, 80));
        topBar.add(lblFilePath, BorderLayout.CENTER);

        // Button panel — FlowLayout so buttons sit side-by-side at their
        // natural size and never stretch.
        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        buttonBar.setOpaque(false);

        // "View output with ▼" — shows the viewer-selection popup
        btnOpen = new JButton("View output with \u25BC");
        btnOpen.setToolTipText(
                "Choose how to open the output file");
        btnOpen.setEnabled(false);
        btnOpen.addActionListener(e -> showViewerPopup());
        buttonBar.add(btnOpen);

        // "Open folder" — opens the parent directory in the file manager
        btnOpenFolder = new JButton("Open folder");
        btnOpenFolder.setToolTipText(
                "Open the folder containing the output file");
        btnOpenFolder.setEnabled(false);
//        btnOpenFolder.setIcon(new ImageIcon(
//                MainWindow.class.getResource("/ca/pfv/spmf/gui/icons/Open24.gif")));
        btnOpenFolder.addActionListener(e -> openContainingFolder());
        buttonBar.add(btnOpenFolder);

        // "Compare with..." — opens Pattern Diff Analyzer with current file
        // pre-loaded as File A so the user only needs to pick File B.
        btnContrastWith = new JButton("Compare with...");
        btnContrastWith.setToolTipText(
                "Open the Pattern Diff Analyzer with this file pre-loaded as File A");
        btnContrastWith.setEnabled(false);
        btnContrastWith.addActionListener(e -> openPatternDiffAnalyzer());
        buttonBar.add(btnContrastWith);

        topBar.add(buttonBar, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        // -----------------------------------------------------------------
        // Preview text area
        // -----------------------------------------------------------------
        previewArea = new JTextArea();
        previewArea.setEditable(false);
        previewArea.setFont(PREVIEW_FONT);
        previewArea.setLineWrap(false);
        previewArea.setWrapStyleWord(false);
        previewArea.setForeground(COLOR_PLACEHOLDER);
        previewArea.setText("No output file to preview.");

        JScrollPane scroll = new JScrollPane(previewArea);
        scroll.setBorder(BorderFactory.createLineBorder(
                new Color(200, 200, 200)));
        scroll.setPreferredSize(new Dimension(600, 140));
        add(scroll, BorderLayout.CENTER);

        // -----------------------------------------------------------------
        // Popup menu (initially empty — populated via setViewerOptions)
        // -----------------------------------------------------------------
        viewerPopup = new JPopupMenu();
    }

    // =====================================================================
    // Public API used by the panel / controller
    // =====================================================================

    /**
     * Replaces the viewer options shown in the popup menu.  The popup is
     * rebuilt immediately so the new items take effect before the user
     * clicks the button.
     *
     * <p>Passing an empty array or {@code null} clears the popup.</p>
     *
     * @param options array of viewer-method names; may be null or empty
     */
    public void setViewerOptions(String[] options) {
        currentViewerOptions = (options != null) ? options : new String[0];
        rebuildPopup();
    }

    /**
     * Returns the viewer-method name that was most recently chosen by the
     * user from the popup menu, or an empty string if the user has not yet
     * made a selection.
     *
     * @return the selected viewer method name, never {@code null}
     */
    public String getSelectedViewerMethod() {
        return selectedViewerMethod;
    }

    /**
     * Registers an {@link ActionListener} that is called whenever the user
     * selects a viewer from the popup menu.  The controller should call
     * {@link #getSelectedViewerMethod()} inside the listener to find out
     * which viewer was chosen.
     *
     * @param listener the listener to add
     */
    public void addOpenButtonListener(ActionListener listener) {
        openListeners.add(listener);
    }

    /**
     * Loads and displays a preview of the given output file.  The first
     * {@value #MAX_PREVIEW_LINES} lines are shown in the text area and the
     * file path is displayed in the label above the area.  Passing
     * {@code null} or an empty string clears the preview and disables the
     * action buttons.
     *
     * <p>This method is safe to call from any thread; UI updates are
     * dispatched to the EDT via {@link SwingUtilities#invokeLater}.</p>
     *
     * @param outputFilePath full path of the file to preview, or {@code null}
     *                       / empty to clear the preview
     */
    public void updatePreview(String outputFilePath) {
        SwingUtilities.invokeLater(() -> {

            if (outputFilePath == null || outputFilePath.trim().isEmpty()) {
                currentOutputFilePath = "";
                lblFilePath.setText(" ");
                previewArea.setForeground(COLOR_PLACEHOLDER);
                previewArea.setText("No output file to preview.");
                btnOpen.setEnabled(false);
                btnOpenFolder.setEnabled(false);
                // Disable the contrast button when there is no file
                btnContrastWith.setEnabled(false);
                return;
            }

            currentOutputFilePath = outputFilePath;
            File file = new File(outputFilePath);

            lblFilePath.setText(outputFilePath);
            lblFilePath.setToolTipText(outputFilePath);

            if (!file.exists() || !file.isFile()) {
                previewArea.setForeground(COLOR_PLACEHOLDER);
                previewArea.setText(
                        "Output file not found:\n" + outputFilePath);
                btnOpen.setEnabled(false);
                btnOpenFolder.setEnabled(false);
                // File does not exist so contrast is not possible either
                btnContrastWith.setEnabled(false);
                return;
            }

            // Read the first MAX_PREVIEW_LINES lines
            StringBuilder sb     = new StringBuilder();
            int           count  = 0;
            try (BufferedReader br =
                         new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null
                        && count < MAX_PREVIEW_LINES) {
                    sb.append(line).append('\n');
                    count++;
                }
                if (count == MAX_PREVIEW_LINES) {
                    sb.append("… (preview limited to ")
                      .append(MAX_PREVIEW_LINES)
                      .append(" lines)");
                } else if (count == 0) {
                    sb.append("(empty file)");
                }
            } catch (IOException ex) {
                sb.setLength(0);
                sb.append("Could not read file:\n").append(ex.getMessage());
            }

            previewArea.setForeground(
                    UIManager.getColor("TextArea.foreground") != null
                    ? UIManager.getColor("TextArea.foreground")
                    : Color.BLACK);
            previewArea.setText(sb.toString());
            previewArea.setCaretPosition(0);   // scroll back to top

            btnOpen.setEnabled(!(currentViewerOptions.length == 0));
            btnOpenFolder.setEnabled(file.getParentFile() != null);
            // Enable "Compare with..." whenever a valid file is loaded
            btnContrastWith.setEnabled(true);
        });
    }

    // =====================================================================
    // Private helpers
    // =====================================================================

    /**
     * Rebuilds {@link #viewerPopup} from {@link #currentViewerOptions}.
     * Each item, when clicked, stores its name in
     * {@link #selectedViewerMethod} and fires all registered
     * {@link #openListeners}.
     *
     * <p>If there are no options a single disabled placeholder item is shown
     * so the popup is never visually empty.</p>
     */
    private void rebuildPopup() {
        viewerPopup.removeAll();

        if (currentViewerOptions.length == 0) {
            JMenuItem placeholder = new JMenuItem("(no viewers available)");
            placeholder.setEnabled(false);
            viewerPopup.add(placeholder);
            btnOpen.setEnabled(false);
            return;
        }

        for (String option : currentViewerOptions) {
            JMenuItem item = new JMenuItem(option);
            item.addActionListener(e -> {
                selectedViewerMethod = option;
                // Notify all registered listeners (typically the controller)
                java.awt.event.ActionEvent evt =
                        new java.awt.event.ActionEvent(
                                this,
                                java.awt.event.ActionEvent.ACTION_PERFORMED,
                                option);
                for (ActionListener l : openListeners) {
                    l.actionPerformed(evt);
                }
            });
            viewerPopup.add(item);
        }

        // Enable the button only when there is also a file to open
        btnOpen.setEnabled(!currentOutputFilePath.isEmpty());
    }

    /**
     * Shows {@link #viewerPopup} anchored directly below {@link #btnOpen}.
     */
    private void showViewerPopup() {
        viewerPopup.show(btnOpen, 0, btnOpen.getHeight());
    }

    /**
     * Opens the Pattern Diff Analyzer with {@link #currentOutputFilePath}
     * pre-loaded as File A (file 1).  The analyzer window is shown as a
     * non-modal frame; the user can then browse for File B inside it and
     * run the comparison without leaving the main SPMF window.
     */
    private void openPatternDiffAnalyzer() {
        if (currentOutputFilePath == null || currentOutputFilePath.isEmpty()) {
            return;
        }

        // Run on the EDT — the constructor already calls setVisible(true)
        // internally via finalizeWindow(), so we just create the instance.
        // We pass false so closing the analyzer does not exit the JVM.
        SwingUtilities.invokeLater(() -> {
            PatternDiffAnalyzer analyzer =
                    new PatternDiffAnalyzer(false, currentOutputFilePath);
            analyzer.setVisible(true);
        });
    }

    /**
     * Opens the folder that contains {@link #currentOutputFilePath} in the
     * platform's default file manager using {@link Desktop#open(File)}.
     * Shows a tool-tip-style label update on failure rather than a modal
     * dialog so the interaction feels lightweight.
     */
    private void openContainingFolder() {
        if (currentOutputFilePath == null
                || currentOutputFilePath.isEmpty()) {
            return;
        }

        File file = new File(currentOutputFilePath);

        if (!file.exists()) {
            lblFilePath.setText(
                    "File does not exist: " + currentOutputFilePath);
            return;
        }

        File folder = file.getParentFile();

        if (folder == null || !folder.exists()) {
            lblFilePath.setText(
                    "Cannot open folder: " + currentOutputFilePath);
            return;
        }

        try {
            String os = System.getProperty("os.name").toLowerCase();

            if (os.contains("win")) {
                new ProcessBuilder(
                        "explorer.exe",
                        "/select," + file.getAbsolutePath())
                        .start();
                return;
            } else if (os.contains("mac")) {
                new ProcessBuilder(
                        "open",
                        "-R",
                        file.getAbsolutePath())
                        .start();
                return;
            }
        } catch (Exception e) {
            // Fall back to opening the folder
        }

        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(folder);
            } else {
                lblFilePath.setText(
                        "Opening folders is not supported on this platform.");
            }
        } catch (IOException ex) {
            lblFilePath.setText(
                    "Could not open folder: " + ex.getMessage());
        }
    }
}