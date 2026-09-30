package ca.pfv.spmf.gui.developerswindow;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/*
 * Copyright (c) 2022 Philippe Fournier-Viger
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

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import ca.pfv.spmf.tools.documentation_downloader.AlgoSPMFDownloadDoc;
import ca.pfv.spmf.tools.documentation_downloader.AlgorithmDownloadResult;
import ca.pfv.spmf.tools.documentation_downloader.DownloadProgressCallback;
import ca.pfv.spmf.tools.documentation_downloader.DownloadStatus;
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
 * Do not remove the copyright and license information
 */
/**
 * An  JFrame to download an offline copy of the SPMF documentation.
 *
 * @author Philippe Fournier-Viger
 */
public class DocumentationDownloaderWindow {

    /** Default output folder name */
    private static final String DEFAULT_FOLDER = "doc";

    /** Button to start the download */
    private JButton btnDownload;

    /** Button to cancel (stop) an ongoing download */
    private JButton btnCancel;

    /** Button to pause or resume an ongoing download */
    private JButton btnPauseResume;

    /** Button to browse for output folder */
    private JButton btnBrowse;

    /** Button to clear the log */
    private JButton btnClearLog;

    /** Button to open the output folder */
    private JButton btnOpenFolder;

    /** Button to re-open the results table after a download has finished */
    private JButton btnShowResults;

    /** Checkbox to skip files that were already downloaded in a previous run */
    private JCheckBox chkSkipExisting;

    /**
     * Checkbox to force re-downloading (overwriting) files that already
     * exist in the destination folder. Mutually exclusive with
     * {@link #chkSkipExisting}: selecting one automatically deselects the
     * other.
     */
    private JCheckBox chkOverwriteExisting;

    /** Progress bar shown during download */
    private JProgressBar progressBar;

    /** Text field showing the selected output folder path */
    private JTextField txtFolderPath;

    /** Text area displaying status/log messages */
    private JTextArea txtLog;

    /** Label showing current status */
    private JLabel lblStatus;

    /** Label showing elapsed time */
    private JLabel lblElapsed;

    /** The downloader thread, kept so we can attempt to cancel/pause it */
    private Thread downloadThread;

    /**
     * The downloader instance used by the currently running (or most
     * recently completed) download, kept so that after the thread finishes
     * we can retrieve {@link AlgoSPMFDownloadDoc#getAlgorithmResults()} and
     * display it in a summary table. Declared {@code volatile} since it is
     * written on the background thread and read on the EDT.
     */
    private volatile AlgoSPMFDownloadDoc downloader;

    /**
     * Flag to signal a hard stop (cancel) to the download thread.
     * Using AtomicBoolean so the download algorithm can read it safely
     * from its own thread without additional synchronisation.
     */
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);

    /**
     * Flag to signal that the download should be paused.
     * The download thread checks this flag after each page and sleeps
     * in a loop until it is cleared.
     */
    private final AtomicBoolean pauseRequested = new AtomicBoolean(false);

    /**
     * Tracks whether the download is currently paused so the
     * Pause/Resume button label can be toggled correctly.
     */
    private volatile boolean isPaused = false;

    /** Timer to update elapsed time */
    private javax.swing.Timer elapsedTimer;

    /** Start time of the download in milliseconds */
    private long startTime;

    /**
     * Creates and displays the Documentation Downloader window.
     */
    public void createAndShowGUI() {
//        // Try to use system look and feel for a more modern appearance
//        try {
//            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
//        } catch (Exception ignored) {
//        }

        JFrame frame = new JFrame("SPMF Documentation Downloader");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(700, 580);
        frame.setMinimumSize(new Dimension(620, 480));
        frame.setLocationRelativeTo(null);

        // ── Main content panel with padding ──────────────────────────────────
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(12, 12, 12, 12));

        // ── Description panel ─────────────────────────────────────────────────
        JPanel descPanel = createDescriptionPanel();
        mainPanel.add(descPanel, BorderLayout.NORTH);

        // ── Centre section: folder selection + log ────────────────────────────
        JPanel centrePanel = new JPanel(new BorderLayout(0, 8));
        centrePanel.add(createFolderSelectionPanel(), BorderLayout.NORTH);
        centrePanel.add(createLogPanel(), BorderLayout.CENTER);
        mainPanel.add(centrePanel, BorderLayout.CENTER);

        // ── Bottom panel: progress + buttons ──────────────────────────────────
        mainPanel.add(createBottomPanel(), BorderLayout.SOUTH);

        frame.setContentPane(mainPanel);
        frame.setVisible(true);

        // Log the default folder choice on startup
        logMessage("INFO", "Output folder set to: "
                + new File(txtFolderPath.getText()).getAbsolutePath());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Panel builders
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds the description panel at the top of the window.
     *
     * @return the constructed panel
     */
    private JPanel createDescriptionPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                new TitledBorder("About"),
                new EmptyBorder(4, 4, 4, 4)));

        JTextArea area = new JTextArea();
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setOpaque(false);
        area.setFont(UIManager.getFont("Label.font"));
        area.setText(
                "This tool downloads an offline copy of the SPMF documentation to a folder "
                + "of your choice. Every HTML page is saved locally so you can browse the "
                + "documentation without an internet connection. "
                + "Select a destination folder below, then click \"Start Download\". "
                + "Use Pause/Resume to temporarily suspend the download, "
                + "or Stop to abort it completely. "
                + "Enable \"Skip files already downloaded\" to resume a previous, "
                + "interrupted download without re-fetching files that are already present. "
                + "Enable \"Overwrite files if they already exist\" to force re-downloading "
                + "every file, even if it is already present locally. These two options are "
                + "mutually exclusive. After the download finishes, a results table will show "
                + "the status (succeeded / failed / skipped) of every algorithm page.");

        panel.add(area, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Builds the folder-selection panel (label + text field + browse button +
     * skip-existing / overwrite-existing checkboxes).
     *
     * @return the constructed panel
     */
    private JPanel createFolderSelectionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                new TitledBorder("Output Folder"),
                new EmptyBorder(4, 4, 4, 4)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Label
        JLabel lblFolder = new JLabel("Destination:");
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        panel.add(lblFolder, gbc);

        // Text field showing current path
        txtFolderPath = new JTextField(
                new File(DEFAULT_FOLDER).getAbsolutePath());
        txtFolderPath.setToolTipText(
                "The folder where documentation pages will be saved.");
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(txtFolderPath, gbc);

        // Browse button
        btnBrowse = new JButton("Browse…");
        btnBrowse.setToolTipText("Choose a destination folder.");
        btnBrowse.addActionListener(e -> browseForFolder());
        gbc.gridx = 2;
        gbc.weightx = 0;
        panel.add(btnBrowse, gbc);

        // ── Skip-existing-files checkbox (spans the full row below) ───────────
        chkSkipExisting = new JCheckBox(
                "Skip files already downloaded (resume a previous download)");
        chkSkipExisting.setToolTipText(
                "If checked, files that already exist in the destination folder "
                + "will not be re-downloaded.");
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        panel.add(chkSkipExisting, gbc);

        // ── Overwrite-existing-files checkbox (spans the full row below) ──────
        chkOverwriteExisting = new JCheckBox(
                "Overwrite files if they already exist");
        chkOverwriteExisting.setToolTipText(
                "If checked, every file will be re-downloaded and overwritten, "
                + "even if it already exists in the destination folder.");
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        panel.add(chkOverwriteExisting, gbc);

        // ── Wire up the mutual-exclusion behaviour between the two checkboxes ──
        // Selecting "Skip existing" clears "Overwrite existing", and vice versa.
        chkSkipExisting.addItemListener(e -> {
            if (chkSkipExisting.isSelected() && chkOverwriteExisting.isSelected()) {
                chkOverwriteExisting.setSelected(false);
            }
        });
        chkOverwriteExisting.addItemListener(e -> {
            if (chkOverwriteExisting.isSelected() && chkSkipExisting.isSelected()) {
                chkSkipExisting.setSelected(false);
            }
        });

        return panel;
    }

    /**
     * Builds the log / status panel.
     *
     * @return the constructed panel
     */
    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBorder(new TitledBorder("Download Log"));

        txtLog = new JTextArea();
        txtLog.setEditable(false);
        txtLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        txtLog.setBackground(new Color(250, 250, 250));

        JScrollPane scrollPane = new JScrollPane(txtLog);
        scrollPane.setPreferredSize(new Dimension(0, 180));
        panel.add(scrollPane, BorderLayout.CENTER);

        // Clear log button aligned to the right
        btnClearLog = new JButton("Clear Log");
        btnClearLog.setToolTipText("Clear all messages from the log.");
        btnClearLog.addActionListener(e -> txtLog.setText(""));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        btnRow.add(btnClearLog);
        panel.add(btnRow, BorderLayout.SOUTH);

        return panel;
    }

    /**
     * Builds the bottom panel containing the progress bar, status label,
     * elapsed time, and action buttons.
     *
     * @return the constructed panel
     */
    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));

        // Progress bar
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("Idle");
        progressBar.setPreferredSize(new Dimension(0, 24));
        panel.add(progressBar, BorderLayout.NORTH);

        // Status + elapsed row
        JPanel statusRow = new JPanel(new BorderLayout());
        lblStatus = new JLabel("Ready.");
        lblStatus.setFont(lblStatus.getFont().deriveFont(Font.ITALIC));
        lblElapsed = new JLabel("");
        lblElapsed.setFont(lblElapsed.getFont().deriveFont(Font.ITALIC));
        statusRow.add(lblStatus, BorderLayout.WEST);
        statusRow.add(lblElapsed, BorderLayout.EAST);
        panel.add(statusRow, BorderLayout.CENTER);

        // Button row
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));

        btnOpenFolder = new JButton("Open Folder");
        btnOpenFolder.setToolTipText(
                "Open the destination folder in the file explorer.");
        btnOpenFolder.setEnabled(false);
        btnOpenFolder.addActionListener(e -> openOutputFolder());

        btnShowResults = new JButton("Show Results Table");
        btnShowResults.setToolTipText(
                "Show the per-algorithm download results table again.");
        btnShowResults.setEnabled(false);
        btnShowResults.addActionListener(e -> showAlgorithmResultsTable());

        // ── Pause / Resume button ─────────────────────────────────────────────
        btnPauseResume = new JButton("Pause");
        btnPauseResume.setToolTipText(
                "Pause the download after the current page finishes.");
        btnPauseResume.setEnabled(false);
        btnPauseResume.addActionListener(e -> togglePauseResume());

        // ── Stop (cancel) button ──────────────────────────────────────────────
        btnCancel = new JButton("Stop");
        btnCancel.setToolTipText("Stop the download immediately.");
        btnCancel.setEnabled(false);
        btnCancel.addActionListener(e -> requestCancel());

        btnDownload = new JButton("Start Download");
        btnDownload.setToolTipText("Begin downloading the SPMF documentation.");
        btnDownload.addActionListener(e -> startDownload());

        buttonRow.add(btnOpenFolder);
        buttonRow.add(btnShowResults);
        buttonRow.add(Box.createHorizontalStrut(20));
        buttonRow.add(btnPauseResume);
        buttonRow.add(btnCancel);
        buttonRow.add(btnDownload);
        panel.add(buttonRow, BorderLayout.SOUTH);

        return panel;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Actions
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Opens a {@link JFileChooser} so the user can pick a destination folder,
     * then updates {@link #txtFolderPath} with the chosen path.
     */
    private void browseForFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Destination Folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);

        // Pre-select the currently entered folder if it exists
        File current = new File(txtFolderPath.getText().trim());
        if (current.exists()) {
            chooser.setCurrentDirectory(current);
        }

        int result = chooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            txtFolderPath.setText(selected.getAbsolutePath());
            logMessage("INFO", "Output folder changed to: "
                    + selected.getAbsolutePath());
        }
    }

    /**
     * Validates the user's input, then starts the download on a background
     * thread so the GUI remains responsive.
     * <p>
     * The background thread calls
     * {@link AlgoSPMFDownloadDoc#runAlgorithm(String, DownloadProgressCallback, boolean)}
     * and passes a progress callback that (a) logs each page URL in full and
     * (b) checks the cancel / pause flags between every page download so that
     * Stop and Pause/Resume work reliably.
     * </p>
     */
    private void startDownload() {
        // ── Validate output folder ────────────────────────────────────────────
        String folderPath = txtFolderPath.getText().trim();
        if (folderPath.isEmpty()) {
            JOptionPane.showMessageDialog(null,
                    "Please specify a destination folder.",
                    "No folder selected",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        File outputDir = new File(folderPath);
        if (!outputDir.exists()) {
            int choice = JOptionPane.showConfirmDialog(null,
                    "The folder \"" + folderPath
                    + "\" does not exist.\nCreate it now?",
                    "Folder not found",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                if (!outputDir.mkdirs()) {
                    JOptionPane.showMessageDialog(null,
                            "Could not create folder: " + folderPath,
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
                logMessage("INFO", "Created folder: " + outputDir.getAbsolutePath());
            } else {
                return;
            }
        }

        if (!outputDir.canWrite()) {
            JOptionPane.showMessageDialog(null,
                    "The selected folder is not writable:\n" + folderPath,
                    "Permission denied",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        // ── Reset control flags ───────────────────────────────────────────────
        cancelRequested.set(false);
        pauseRequested.set(false);
        isPaused = false;

        // ── Read the "skip existing files" / "overwrite existing files" options ─
        // These two checkboxes are mutually exclusive (enforced by the
        // ItemListeners installed in createFolderSelectionPanel()), so at
        // most one of them can be selected at a time.
        final boolean skipExisting = chkSkipExisting.isSelected();
        final boolean overwriteExisting = chkOverwriteExisting.isSelected();

        // ── Update UI for "running" state ─────────────────────────────────────
        setRunningState(true);
        btnShowResults.setEnabled(false);
        logMessage("INFO", "Starting download to: " + outputDir.getAbsolutePath());
        if (skipExisting) {
            logMessage("INFO", "Skip-existing-files option is ENABLED. "
                    + "Files already present in the destination folder will not be re-downloaded.");
        } else if (overwriteExisting) {
            logMessage("INFO", "Overwrite-existing-files option is ENABLED. "
                    + "All files will be re-downloaded, even if already present.");
        }

        // ── Start elapsed-time timer ──────────────────────────────────────────
        startTime = System.currentTimeMillis();
        elapsedTimer = new javax.swing.Timer(1000, e -> updateElapsedLabel());
        elapsedTimer.start();

        // ── Build the progress callback passed to the downloader ──────────────
        //
        // DownloadProgressCallback is a @FunctionalInterface with a single
        // method:
        //
        //   boolean onPageDownloading(String fullUrl, int pageIndex, int totalPages)
        //
        // Returning FALSE from the callback tells the algorithm to stop
        // immediately (acts like a cooperative cancel mechanism).
        //
        // When the "skip existing files" option is active, the algorithm
        // prefixes the URL with AlgoSPMFDownloadDoc.SKIPPED_PREFIX to indicate
        // that the resource was NOT actually downloaded (it already existed
        // locally) so we can log it differently.
        //
        // NOTE: the variable type here MUST be DownloadProgressCallback
        // (the functional interface), NOT AlgoSPMFDownloadDoc. That was the
        // bug causing "target type must be a functional interface" and the
        // subsequent "method not applicable" error.
        //
        DownloadProgressCallback callback = (fullUrl, pageIndex, totalPages) -> {

            // ── Check for cancellation FIRST ──────────────────────────────────
            // If the user pressed Stop, we return false so the algorithm halts.
            if (cancelRequested.get()) {
                return false;   // signal the downloader to abort
            }

            // ── Log the full URL being downloaded (or skipped) ────────────────
            // Skip logging for the empty-string sentinel used internally by
            // AlgoSPMFDownloadDoc.isCancelled() between loop iterations.
            if (fullUrl != null && !fullUrl.isEmpty()) {

                String pageInfo = (totalPages > 0)
                        ? String.format("(%d / %d)", pageIndex, totalPages)
                        : String.format("(resource #%d)", pageIndex);

                if (fullUrl.startsWith(AlgoSPMFDownloadDoc.SKIPPED_PREFIX)) {
                    String actualUrl = fullUrl.substring(
                            AlgoSPMFDownloadDoc.SKIPPED_PREFIX.length());
                    logMessage("INFO", "Skipping " + pageInfo
                            + " (already exists) -> " + actualUrl);
                } else {
                    logMessage("INFO", "Downloading " + pageInfo + " -> " + fullUrl);
                }
            }

            // Update progress bar if we know the total
            if (totalPages > 0) {
                SwingUtilities.invokeLater(() -> {
                    int pct = (int) Math.round((pageIndex * 100.0) / totalPages);
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(pct);
                    progressBar.setString(pct + "% – page " + pageIndex + " of " + totalPages);
                });
            }

            // ── Handle pause: spin-wait until resumed or cancelled ─────────────
            if (pauseRequested.get()) {
                SwingUtilities.invokeLater(() -> {
                    lblStatus.setText("Download paused.");
                    progressBar.setString("Paused");
                });

                // Sleep in short bursts so we react quickly to resume or cancel
                while (pauseRequested.get() && !cancelRequested.get()) {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException ie) {
                        // Restore interrupt flag and bail out
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }

                // Check again after waking up
                if (cancelRequested.get()) {
                    return false;
                }

                SwingUtilities.invokeLater(() -> {
                    lblStatus.setText("Download in progress…");
                    progressBar.setString("Downloading…");
                });
            }

            // Returning true tells the algorithm to continue to the next page
            return true;
        };

        // ── Background download thread ────────────────────────────────────────
        downloadThread = new Thread(() -> {
            boolean success = false;
            try {
                downloader = new AlgoSPMFDownloadDoc();

                // Pass the output folder path, the progress callback, and the
                // skip-existing option so the algorithm can report each URL,
                // honour stop/pause signals, and avoid re-downloading files.
                //
                // Note: when "overwriteExisting" is selected, we simply pass
                // skipExisting = false to the downloader, since the algorithm's
                // default behaviour (when not asked to skip) is to
                // re-download and overwrite any existing file.
                downloader.runAlgorithm(outputDir.getAbsolutePath(), callback, skipExisting);

                // Only mark success if we were NOT cancelled
                success = !cancelRequested.get();

            } catch (Exception ex) {
                final String msg = ex.getMessage() != null
                        ? ex.getMessage() : ex.getClass().getSimpleName();
                SwingUtilities.invokeLater(() ->
                        logMessage("ERROR", "Download failed: " + msg));
            } finally {
                final boolean finalSuccess = success;
                SwingUtilities.invokeLater(() -> onDownloadFinished(finalSuccess));
            }
        }, "DocDownloader-Thread");

        downloadThread.setDaemon(true);
        downloadThread.start();
    }

    /**
     * Signals the download thread to stop by setting {@link #cancelRequested}.
     * <p>
     * The {@link DownloadProgressCallback} checks this flag
     * between pages and returns {@code false} to tell the algorithm to abort.
     * We also interrupt the thread so that any blocking I/O or sleep wakes up
     * immediately.
     * </p>
     */
    private void requestCancel() {
        if (downloadThread != null && downloadThread.isAlive()) {
            cancelRequested.set(true);
            // If the thread is sleeping (paused), wake it up so it can exit
            pauseRequested.set(false);
            downloadThread.interrupt();
            logMessage("WARN", "Stop requested – aborting after current network operation…");
            btnCancel.setEnabled(false);
            btnPauseResume.setEnabled(false);
        }
    }

    /**
     * Toggles between paused and running states.
     * <p>
     * When paused the download callback spins in a wait-loop; when resumed
     * it clears {@link #pauseRequested} and the loop exits naturally.
     * </p>
     */
    private void togglePauseResume() {
        if (!isPaused) {
            // ── Pause ─────────────────────────────────────────────────────────
            pauseRequested.set(true);
            isPaused = true;
            btnPauseResume.setText("Resume");
            btnPauseResume.setToolTipText(
                    "Resume the download from where it was paused.");
            logMessage("INFO", "Pause requested – will pause after current page completes.");
            lblStatus.setText("Pausing…");

        } else {
            // ── Resume ────────────────────────────────────────────────────────
            pauseRequested.set(false);
            isPaused = false;
            btnPauseResume.setText("Pause");
            btnPauseResume.setToolTipText(
                    "Pause the download after the current page finishes.");
            logMessage("INFO", "Resuming download…");
            lblStatus.setText("Download in progress…");
            progressBar.setString("Downloading…");
        }
    }

    /**
     * Opens the output folder in the platform's default file-manager.
     */
    private void openOutputFolder() {
        File dir = new File(txtFolderPath.getText().trim());
        if (!dir.exists()) {
            JOptionPane.showMessageDialog(null,
                    "The folder does not exist: " + dir.getAbsolutePath(),
                    "Folder not found",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            java.awt.Desktop.getDesktop().open(dir);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null,
                    "Could not open folder:\n" + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Results table
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds and displays a modeless {@link JDialog} containing a
     * {@link JTable} that summarises, for every algorithm ("Example") page
     * processed during the last run, whether it succeeded, failed, or was
     * skipped, along with the local file path and remote URL. This makes it
     * easy to spot at a glance which files were not downloaded and why.
     * <p>
     * Does nothing if no downloader has run yet or if it produced no
     * results.
     * </p>
     */
    private void showAlgorithmResultsTable() {
        if (downloader == null) {
            return;
        }

        final List<AlgorithmDownloadResult> results = downloader.getAlgorithmResults();
        if (results == null || results.isEmpty()) {
            JOptionPane.showMessageDialog(null,
                    "No algorithm page results are available.",
                    "Download Results",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String[] columnNames = {"#", "Algorithm / Page", "Status", "Local File", "URL", "Details"};

        Object[][] data = new Object[results.size()][columnNames.length];
        int successCount = 0, failedCount = 0, skippedCount = 0;

        for (int i = 0; i < results.size(); i++) {
            AlgorithmDownloadResult r = results.get(i);
            data[i][0] = i + 1;
            data[i][1] = r.getAlgorithmName();
            data[i][2] = formatStatus(r.getStatus());
            data[i][3] = r.getFilePath();
            data[i][4] = r.getUrl();
            data[i][5] = r.getDetails() == null ? "" : r.getDetails();

            switch (r.getStatus()) {
                case SUCCESS:
                    successCount++;
                    break;
                case FAILED:
                    failedCount++;
                    break;
                default:
                    skippedCount++;
                    break;
            }
        }

        DefaultTableModel model = new DefaultTableModel(data, columnNames) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable table = new JTable(model);
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(22);
        table.setFillsViewportHeight(true);
        if (table.getColumnModel().getColumnCount() >= 6) {
            table.getColumnModel().getColumn(0).setPreferredWidth(30);
            table.getColumnModel().getColumn(1).setPreferredWidth(170);
            table.getColumnModel().getColumn(2).setPreferredWidth(130);
            table.getColumnModel().getColumn(3).setPreferredWidth(230);
            table.getColumnModel().getColumn(4).setPreferredWidth(260);
            table.getColumnModel().getColumn(5).setPreferredWidth(220);
        }

        // Colour-code the "Status" column so problems jump out visually.
        table.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(
                        tbl, value, isSelected, hasFocus, row, column);
                String text = String.valueOf(value);
                if (!isSelected) {
                    if (text.contains("SUCCESS")) {
                        c.setForeground(new Color(0, 130, 0));
                    } else if (text.contains("FAILED")) {
                        c.setForeground(Color.RED.darker());
                    } else if (text.contains("SKIPPED")) {
                        c.setForeground(new Color(180, 120, 0));
                    } else {
                        c.setForeground(Color.BLACK);
                    }
                }
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(950, 420));

        JLabel summaryLabel = new JLabel(String.format(
                "Total: %d   |   Succeeded: %d   |   Failed: %d   |   Skipped: %d",
                results.size(), successCount, failedCount, skippedCount));
        summaryLabel.setBorder(new EmptyBorder(6, 6, 6, 6));
        summaryLabel.setFont(summaryLabel.getFont().deriveFont(Font.BOLD));
        if (failedCount > 0) {
            summaryLabel.setForeground(Color.RED.darker());
        }

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(summaryLabel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        JButton btnClose = new JButton("Close");
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.add(btnClose);
        panel.add(btnPanel, BorderLayout.SOUTH);

        JDialog dialog = new JDialog((JFrame) null,
                "Download Results – Algorithm Pages (" + results.size() + " total)", false);
        dialog.setContentPane(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        btnClose.addActionListener(e -> dialog.dispose());
        dialog.setVisible(true);
    }

    /**
     * Formats a {@link DownloadStatus} value into a short, human-readable
     * (and easily colour-matched) string for display in the results table.
     *
     * @param status the status to format
     * @return a display string such as "SUCCESS" or "FAILED"
     */
    private String formatStatus(DownloadStatus status) {
        switch (status) {
            case SUCCESS:
                return "SUCCESS";
            case FAILED:
                return "FAILED";
            case SKIPPED_EXISTING:
                return "SKIPPED (already exists)";
            case SKIPPED_DUPLICATE:
                return "SKIPPED (duplicate link)";
            default:
                return status.toString();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Appends a timestamped message to the log text area.
     * Safe to call from any thread – the actual append is always done on the
     * Event Dispatch Thread via {@link SwingUtilities#invokeLater}.
     *
     * @param level   severity tag, e.g. {@code "INFO"}, {@code "WARN"}, {@code "ERROR"}
     * @param message the message body
     */
    private void logMessage(String level, String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        String line = String.format("[%s] [%-5s] %s%n", timestamp, level, message);
        SwingUtilities.invokeLater(() -> {
            txtLog.append(line);
            // Auto-scroll to the bottom so the latest entry is always visible
            txtLog.setCaretPosition(txtLog.getDocument().getLength());
        });
    }

    /**
     * Switches the UI between "running" and "idle" states.
     * Must be called on the EDT.
     *
     * @param running {@code true} when a download is in progress
     */
    private void setRunningState(boolean running) {
        btnDownload.setEnabled(!running);
        btnBrowse.setEnabled(!running);
        chkSkipExisting.setEnabled(!running);
        chkOverwriteExisting.setEnabled(!running);

        // Stop button: enabled only while running
        btnCancel.setEnabled(running);

        // Pause/Resume button: enabled only while running;
        // reset label to "Pause" whenever we (re)start
        btnPauseResume.setEnabled(running);
        btnPauseResume.setText("Pause");

        btnOpenFolder.setEnabled(!running);
        txtFolderPath.setEditable(!running);

        if (running) {
            progressBar.setIndeterminate(true);
            progressBar.setValue(0);
        } else {
            progressBar.setIndeterminate(false);
        }
        progressBar.setString(running ? "Downloading…" : "Idle");

        lblStatus.setText(running ? "Download in progress…" : "Ready.");

        if (!running) {
            lblElapsed.setText("");
        }
    }

    /**
     * Updates the elapsed-time label. Called every second by {@link #elapsedTimer}.
     */
    private void updateElapsedLabel() {
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        long minutes = elapsed / 60;
        long seconds = elapsed % 60;
        lblElapsed.setText(String.format("Elapsed: %02d:%02d", minutes, seconds));
    }

    /**
     * Called on the EDT when the download thread finishes (successfully or not).
     * <p>
     * Regardless of the overall outcome (success, error, or user-requested
     * stop), if the downloader produced any per-algorithm results, a
     * summary {@link JTable} dialog is shown so the user can immediately
     * see which pages succeeded, failed, or were skipped.
     * </p>
     *
     * @param success {@code true} if the download completed without errors or cancellation
     */
    private void onDownloadFinished(boolean success) {
        if (elapsedTimer != null) {
            elapsedTimer.stop();
        }

        // Reset pause state so the button starts fresh next time
        isPaused = false;
        pauseRequested.set(false);

        setRunningState(false);

        if (cancelRequested.get()) {
            // User pressed Stop
            lblStatus.setText("Download stopped.");
            progressBar.setString("Stopped");
            progressBar.setValue(0);
            logMessage("WARN", "Download was stopped by the user.");
        } else if (success) {
            // Completed normally
            btnOpenFolder.setEnabled(true);
            lblStatus.setText("Download completed successfully.");
            progressBar.setString("Done");
            progressBar.setValue(100);
            logMessage("INFO", "Documentation downloaded to: "
                    + new File(txtFolderPath.getText().trim()).getAbsolutePath());
            logMessage("INFO",
                    "Open 'documentation.html' in the destination folder to start browsing.");
        } else {
            // Error during download
            lblStatus.setText("Download finished with errors. See log.");
            progressBar.setString("Error");
        }

        // ── Show the per-algorithm results table, if available ────────────────
        if (downloader != null) {
            List<AlgorithmDownloadResult> results = downloader.getAlgorithmResults();
            boolean hasResults = results != null && !results.isEmpty();
            btnShowResults.setEnabled(hasResults);
            if (hasResults) {
                long failedCount = 0;
                for (AlgorithmDownloadResult r : results) {
                    if (r.getStatus() == DownloadStatus.FAILED) {
                        failedCount++;
                    }
                }
                logMessage(failedCount > 0 ? "WARN" : "INFO",
                        "Algorithm pages summary: " + results.size() + " total, "
                        + failedCount + " failed. See the results table for details.");
                showAlgorithmResultsTable();
            }
        }
    }
}