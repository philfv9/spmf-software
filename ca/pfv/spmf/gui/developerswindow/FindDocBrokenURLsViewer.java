package ca.pfv.spmf.gui.developerswindow;

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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import ca.pfv.spmf.algorithmmanager.AlgorithmManager;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;

/**
 * A tool for SPMF developers that scans the documentation links of all
 * algorithms and reports which ones are broken.
 *
 * Features:
 * <ul>
 *   <li>Start / Pause-Resume / Stop controls</li>
 *   <li>Recheck button: enabled after the first check when errors are present;
 *       only rechecks the URLs that were reported as broken instead of all of them.
 *       Rows that pass the recheck (transient errors) are removed from the table.</li>
 *   <li>Live status label and progress bar</li>
 *   <li>Results table updated in real time</li>
 *   <li>Per-request network timeouts</li>
 * </ul>
 *
 * @author Philippe Fournier-Viger, 2024 (revised)
 */
public class FindDocBrokenURLsViewer {

    /** Max time (ms) to wait for a TCP connection to be established. */
    private static final int CONNECT_TIMEOUT_MS = 5000;

    /** Max time (ms) to wait for the server response once connected. */
    private static final int READ_TIMEOUT_MS = 5000;

    // ── GUI components (instance fields, not static) ──────────────────────────

    private JFrame frame;
    private JButton btnStart;
    private JButton btnPauseResume;
    private JButton btnStop;

    /**
     * Recheck button: only enabled when the first (or any subsequent) check has
     * finished and the results table contains at least one broken-URL row.
     */
    private JButton btnRecheck;

    private JProgressBar progressBar;
    private JLabel lblStatus;
    private JLabel lblSummary;
    private JTable resultsTable;
    private DefaultTableModel tableModel;

    /** The worker currently running the check (null when idle). */
    private CheckWorker currentWorker;

    /**
     * Tracks whether we are in "recheck mode".
     * When true the worker only processes algorithms whose names are already in
     * the results table, and finished rows that are now OK are removed from it.
     */
    private boolean isRecheckMode = false;

    // ── Constructor / GUI bootstrap ───────────────────────────────────────────

    public FindDocBrokenURLsViewer() {
        createAndShowGUI();
    }

    private void createAndShowGUI() {
        frame = new JFrame("SPMF Tool to Find Broken Documentation URLs (for SPMF developers)");
        frame.setSize(1000, 640);
        frame.setLayout(new BorderLayout());
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        // ── Top control panel ─────────────────────────────────────────────────
        JPanel topPanel = new JPanel(new BorderLayout());

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        btnStart       = new JButton("Start Full Check");
        btnPauseResume = new JButton("Pause");
        btnStop        = new JButton("Stop");
        btnRecheck     = new JButton("Recheck Errors");

        // Initial button states
        btnPauseResume.setEnabled(false);
        btnStop.setEnabled(false);
        btnRecheck.setEnabled(false);   // enabled only after a check finds errors

        btnStart.setToolTipText("Check every algorithm's documentation URL.");
        btnRecheck.setToolTipText(
                "Re-check only the URLs currently shown as broken. "
                + "Rows that pass this time (transient errors) will be removed.");

        btnStart.addActionListener(e -> startFullCheck());
        btnPauseResume.addActionListener(e -> togglePauseResume());
        btnStop.addActionListener(e -> stopCheck());
        btnRecheck.addActionListener(e -> startRecheck());

        buttonsPanel.add(btnStart);
        buttonsPanel.add(btnPauseResume);
        buttonsPanel.add(btnStop);
        buttonsPanel.add(btnRecheck);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(980, 25));

        lblStatus = new JLabel("Ready.");
        lblStatus.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        topPanel.add(buttonsPanel, BorderLayout.NORTH);
        topPanel.add(progressBar,  BorderLayout.CENTER);
        topPanel.add(lblStatus,    BorderLayout.SOUTH);

        // ── Results table (filled live) ───────────────────────────────────────
        tableModel = new DefaultTableModel(
                new Object[]{"Algorithm Name", "Documentation URL", "Problem"}, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        resultsTable = new JTable(tableModel);
        resultsTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        DefaultTableCellRenderer redRenderer = new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);
                c.setForeground(Color.RED);
                return c;
            }
        };
        resultsTable.getColumnModel().getColumn(0).setCellRenderer(redRenderer);
        resultsTable.getColumnModel().getColumn(1).setCellRenderer(redRenderer);
        resultsTable.getColumnModel().getColumn(2).setCellRenderer(redRenderer);

        JScrollPane scrollPane = new JScrollPane(resultsTable);

        // ── Bottom summary ────────────────────────────────────────────────────
        lblSummary = new JLabel("No check has been run yet.");
        lblSummary.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        frame.add(topPanel,    BorderLayout.NORTH);
        frame.add(scrollPane,  BorderLayout.CENTER);
        frame.add(lblSummary,  BorderLayout.SOUTH);

        // ── Window-close guard ────────────────────────────────────────────────
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (currentWorker != null && !currentWorker.isDone()) {
                    int choice = JOptionPane.showConfirmDialog(frame,
                            "A check is still running. Stop it and close the window?",
                            "Confirm Close", JOptionPane.YES_NO_OPTION);
                    if (choice != JOptionPane.YES_OPTION) {
                        return;
                    }
                    currentWorker.cancel(true);
                }
                frame.dispose();
            }
        });

        frame.setVisible(true);
    }

    // ── Button actions ────────────────────────────────────────────────────────

    /**
     * Starts a brand-new full check.
     * The results table is cleared and every algorithm is examined.
     */
    private void startFullCheck() {
        isRecheckMode = false;
        tableModel.setRowCount(0);          // wipe previous results
        progressBar.setValue(0);
        lblStatus.setText("Fetching algorithm list…");
        lblSummary.setText("");

        setControlsRunning();

        currentWorker = new CheckWorker(null); // null → full check
        currentWorker.execute();
    }

    /**
     * Starts a recheck of only the algorithms whose names are currently in the
     * results table (i.e. those that were broken in the previous run).
     * Rows that pass this time are removed so the table always reflects the
     * current known-broken set.
     */
    private void startRecheck() {
        // Snapshot the algorithm names that are currently shown as broken.
        List<String> namesToRecheck = new ArrayList<>();
        for (int row = 0; row < tableModel.getRowCount(); row++) {
            namesToRecheck.add((String) tableModel.getValueAt(row, 0));
        }

        if (namesToRecheck.isEmpty()) {
            JOptionPane.showMessageDialog(frame,
                    "There are no broken URLs to recheck.",
                    "Nothing to recheck", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        isRecheckMode = true;
        progressBar.setValue(0);
        lblStatus.setText("Rechecking " + namesToRecheck.size() + " broken URL(s)…");
        lblSummary.setText("");

        setControlsRunning();

        currentWorker = new CheckWorker(namesToRecheck);
        currentWorker.execute();
    }

    private void togglePauseResume() {
        if (currentWorker == null) return;
        if (currentWorker.isPaused()) {
            currentWorker.resumeChecking();
            btnPauseResume.setText("Pause");
            lblStatus.setText("Resumed.");
        } else {
            currentWorker.pauseChecking();
            btnPauseResume.setText("Resume");
            lblStatus.setText("Paused — click Resume to continue.");
        }
    }

    private void stopCheck() {
        if (currentWorker != null) {
            lblStatus.setText("Stopping (finishing current request)…");
            currentWorker.cancel(true);
            btnStop.setEnabled(false);
            btnPauseResume.setEnabled(false);
        }
    }

    // ── Worker lifecycle helpers ──────────────────────────────────────────────

    /** Put the UI into "a check is running" state. */
    private void setControlsRunning() {
        btnStart.setEnabled(false);
        btnRecheck.setEnabled(false);
        btnPauseResume.setEnabled(true);
        btnPauseResume.setText("Pause");
        btnStop.setEnabled(true);
    }

    /**
     * Called on the EDT when the worker finishes (normally, cancelled, or error).
     * Decides whether to enable the Recheck button based on how many rows remain
     * in the table after the run.
     */
    private void onWorkerFinished(CheckWorker worker) {

        // Re-enable idle controls
        btnStart.setEnabled(true);
        btnPauseResume.setEnabled(false);
        btnPauseResume.setText("Pause");
        btnStop.setEnabled(false);

        // Enable Recheck only when there are broken rows to work on
        boolean hasErrors = tableModel.getRowCount() > 0;
        btnRecheck.setEnabled(hasErrors);

        try {
            List<DescriptionOfAlgorithm> brokenURLs = worker.get(); // safe: already done
            int checked = worker.getCheckedCount();
            int total   = worker.getTotalCount();

            String mode    = isRecheckMode ? "Recheck" : "Full check";
            String stopped = worker.isCancelled() ? " (stopped by user)" : "";

            lblSummary.setText(String.format(
                    "%s: examined %d of %d link(s). Broken: %d.%s",
                    mode, checked, total, tableModel.getRowCount(), stopped));
            lblStatus.setText(worker.isCancelled() ? "Stopped." : "Done.");

        } catch (CancellationException ce) {
            lblSummary.setText(String.format(
                    "Stopped after %d of %d link(s). Broken so far: %d.",
                    worker.getCheckedCount(), worker.getTotalCount(),
                    tableModel.getRowCount()));
            lblStatus.setText("Stopped.");

        } catch (InterruptedException | ExecutionException ex) {
            Thread.currentThread().interrupt();
            lblStatus.setText("Error: " + ex.getMessage());
            JOptionPane.showMessageDialog(frame,
                    "An error occurred:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void main(String[] args) {
        SwingUtilities.invokeLater(FindDocBrokenURLsViewer::new);
    }

    // =========================================================================
    //  CheckWorker – cancellable, pausable SwingWorker
    // =========================================================================

    /**
     * Background worker that checks a list of documentation URLs.
     *
     * <p>When {@code algorithmNamesToCheck} is {@code null} the worker fetches
     * the full algorithm list from {@link AlgorithmManager} (full-check mode).
     * When it is non-null only those named algorithms are checked (recheck mode).
     *
     * <p>In recheck mode the worker additionally publishes {@link RecheckedOkEntry}
     * events so the EDT can remove rows that are no longer broken.
     */
    private class CheckWorker extends SwingWorker<List<DescriptionOfAlgorithm>, Object> {

        /** Names to check; null means "all algorithms". */
        private final List<String> algorithmNamesToCheck;

        private final Object  pauseLock    = new Object();
        private volatile boolean paused    = false;
        private volatile int checkedCount  = 0;
        private volatile int totalCount    = 0;

        CheckWorker(List<String> algorithmNamesToCheck) {
            this.algorithmNamesToCheck = algorithmNamesToCheck;
        }

        boolean isPaused()      { return paused; }
        int     getCheckedCount() { return checkedCount; }
        int     getTotalCount()   { return totalCount; }

        void pauseChecking() {
            paused = true;
        }

        void resumeChecking() {
            synchronized (pauseLock) {
                paused = false;
                pauseLock.notifyAll();
            }
        }

        // ── Background work ───────────────────────────────────────────────────

        @Override
        protected List<DescriptionOfAlgorithm> doInBackground() throws Exception {

            List<DescriptionOfAlgorithm> brokenURLs = new ArrayList<>();

            // Determine which algorithm names we will check
            final List<String> names;
            if (algorithmNamesToCheck == null) {
                // Full check: ask AlgorithmManager for the complete list
                names = AlgorithmManager.getInstance()
                        .getListOfAlgorithmsAsString(true, true, true, true, true);
            } else {
                // Recheck: only the names passed in
                names = algorithmNamesToCheck;
            }

            totalCount = names.size();

            for (String algorithmName : names) {

                // ── Cancellation check ────────────────────────────────────────
                if (isCancelled()) break;

                // ── Pause: block here until resumed or cancelled ───────────────
                synchronized (pauseLock) {
                    while (paused && !isCancelled()) {
                        pauseLock.wait(200);
                    }
                }
                if (isCancelled()) break;

                checkedCount++;

                // Skip category separators (e.g. "---Frequent Itemset Mining---")
                if (algorithmName.contains("---")) {
                    publish(new StatusUpdate(algorithmName, checkedCount, totalCount, "skipped"));
                    continue;
                }

                DescriptionOfAlgorithm algorithm = AlgorithmManager.getInstance()
                        .getDescriptionOfAlgorithm(algorithmName);

                if (algorithm == null) {
                    // Defensive: shouldn't happen, but guard anyway
                    publish(new StatusUpdate(algorithmName, checkedCount, totalCount,
                            "not found in AlgorithmManager – skipped"));
                    continue;
                }

                String url = algorithm.getURLOfDocumentation();
                publish(new StatusUpdate(algorithmName, checkedCount, totalCount,
                        "checking " + url));

                String problem = checkUrl(url);

                if (problem != null) {
                    // Still broken
                    brokenURLs.add(algorithm);
                    publish(new BrokenEntry(algorithm, problem));
                } else {
                    // URL is OK now
                    if (isRecheckMode) {
                        // Signal the EDT to remove the row that was previously broken
                        publish(new RecheckedOkEntry(algorithmName));
                    }
                }

                setProgress((int) (100.0 * checkedCount / Math.max(1, totalCount)));
            }

            return brokenURLs;
        }

        // ── EDT update processing ─────────────────────────────────────────────

        @Override
        protected void process(List<Object> chunks) {
            for (Object chunk : chunks) {

                if (chunk instanceof StatusUpdate su) {
                    lblStatus.setText(String.format("[%d / %d]  %s  —  %s",
                            su.index, su.total, su.name, su.detail));
                    progressBar.setValue(
                            (int) (100.0 * su.index / Math.max(1, su.total)));

                } else if (chunk instanceof BrokenEntry be) {
                    // In full-check mode just append.
                    // In recheck mode the row already exists, so update it in place
                    // (the problem description may differ on the second check).
                    if (isRecheckMode) {
                        int existingRow = findRowByName(be.algorithm.getName());
                        if (existingRow >= 0) {
                            tableModel.setValueAt(be.problem, existingRow, 2);
                        } else {
                            // Shouldn't happen in recheck mode, but be safe
                            tableModel.addRow(new Object[]{
                                    be.algorithm.getName(),
                                    be.algorithm.getURLOfDocumentation(),
                                    be.problem});
                        }
                    } else {
                        tableModel.addRow(new Object[]{
                                be.algorithm.getName(),
                                be.algorithm.getURLOfDocumentation(),
                                be.problem});
                    }

                } else if (chunk instanceof RecheckedOkEntry ok) {
                    // Remove the row: this URL is no longer broken
                    int row = findRowByName(ok.algorithmName);
                    if (row >= 0) {
                        tableModel.removeRow(row);
                    }
                }
            }
        }

        @Override
        protected void done() {
            onWorkerFinished(this);
        }

        // ── Network check helpers ─────────────────────────────────────────────

        /**
         * Returns {@code null} if the URL is reachable with HTTP 200, or a short
         * human-readable problem description otherwise.
         */
        private String checkUrl(String url) {
            if (url == null || url.isBlank()) {
                return "empty URL";
            }
            try {
                return attempt(url, "HEAD", 0);
            } catch (Exception e) {
                return "exception: " + e.getMessage();
            }
        }

        /**
         * Tries to reach {@code url} using {@code method}.
         * <ul>
         *   <li>Retries with GET if the server returns 405 / 501 for HEAD.</li>
         *   <li>Follows cross-protocol redirects manually (up to 3 hops).</li>
         * </ul>
         */
        private String attempt(String url, String method, int redirectDepth) throws Exception {
            if (redirectDepth > 3) return "too many redirects";

            HttpURLConnection connection = null;
            try {
                URL urlObject = new URI(url).toURL();
                connection = (HttpURLConnection) urlObject.openConnection();
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (SPMF-LinkChecker)");
                connection.setRequestMethod(method);

                int responseCode = connection.getResponseCode();

                // Some servers refuse HEAD; retry with GET once
                if ((responseCode == 405 || responseCode == 501) && "HEAD".equals(method)) {
                    connection.disconnect();
                    return attempt(url, "GET", redirectDepth);
                }

                // Manual redirect (needed for http → https across different hosts)
                if (responseCode >= 300 && responseCode < 400) {
                    String location = connection.getHeaderField("Location");
                    connection.disconnect();
                    if (location != null) return attempt(location, method, redirectDepth + 1);
                    return "redirect without Location header (HTTP " + responseCode + ")";
                }

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    return "HTTP " + responseCode;
                }
                return null; // OK

            } finally {
                if (connection != null) connection.disconnect();
            }
        }

        // ── Table utility ─────────────────────────────────────────────────────

        /**
         * Finds the first row in the results table whose "Algorithm Name" column
         * equals {@code name}. Returns -1 if not found.
         */
        private int findRowByName(String name) {
            for (int row = 0; row < tableModel.getRowCount(); row++) {
                if (name.equals(tableModel.getValueAt(row, 0))) {
                    return row;
                }
            }
            return -1;
        }
    }

    // =========================================================================
    //  Immutable message types published from background thread → EDT
    // =========================================================================

    /** Carries a progress status message to the status label and progress bar. */
    private static final class StatusUpdate {
        final String name;
        final int    index;
        final int    total;
        final String detail;

        StatusUpdate(String name, int index, int total, String detail) {
            this.name   = name;
            this.index  = index;
            this.total  = total;
            this.detail = detail;
        }
    }

    /** Signals that a broken URL was found (or is still broken on a recheck). */
    private static final class BrokenEntry {
        final DescriptionOfAlgorithm algorithm;
        final String                  problem;

        BrokenEntry(DescriptionOfAlgorithm algorithm, String problem) {
            this.algorithm = algorithm;
            this.problem   = problem;
        }
    }

    /**
     * Signals (recheck mode only) that a previously broken URL is now reachable.
     * The EDT will remove its row from the table.
     */
    private static final class RecheckedOkEntry {
        final String algorithmName;

        RecheckedOkEntry(String algorithmName) {
            this.algorithmName = algorithmName;
        }
    }
}