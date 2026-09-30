package ca.pfv.spmf.gui.developerswindow;

/*
 * Copyright (c) 2024 Philippe Fournier-Viger
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
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import ca.pfv.spmf.gui.Main;
import ca.pfv.spmf.gui.SortableJTable;

/**
 * A developer tool that automatically discovers and runs every class whose
 * simple name starts with "MainTest" in the {@code ca.pfv.spmf.test} package,
 * and reports success / failure / timeout for each one in a sortable table.
 *
 * <p>Features:</p>
 * <ul>
 * <li>Discovers MainTest* classes whether SPMF is run from a directory of
 *     .class files (e.g. inside an IDE) or from a packaged JAR file.</li>
 * <li>Runs each class's {@code main(String[])} method on a background thread
 *     with a configurable per-test timeout.</li>
 * <li>Captures all {@code System.out} / {@code System.err} output produced
 *     during the run.</li>
 * <li>Catches any exception thrown by the test and stores its stack trace.</li>
 * <li>Displays results in a {@link SortableJTable} with a live search filter
 *     that matches against any column.</li>
 * <li>Double-click a row to open a details dialog containing the full console
 *     output and, if applicable, the exception stack trace.</li>
 * <li>Live progress bar showing how many tests have been run out of the total
 *     discovered.</li>
 * </ul>
 *
 * <p><b>Known limitations:</b> A test that calls {@code System.exit(...)} will
 * terminate the entire JVM and cannot be recovered. A test that opens a modal
 * GUI dialog will block until dismissed manually.</p>
 *
 * @author Philippe Fournier-Viger
 */
public class MainTestAutoRunnerWindow extends JFrame {

    /** Serial UID */
    private static final long serialVersionUID = 1L;

    /** Per-test timeout, in milliseconds. */
    private static final long PER_TEST_TIMEOUT_MS = 60_000L;

    /** The package to scan for MainTest* classes. */
    private static final String TEST_PACKAGE = "ca.pfv.spmf.test";

    /** Column names for the results table. */
    private static final String[] COLUMN_NAMES = {
            "Class", "Status", "Duration (ms)", "Exception Type"
    };

    /** Status values. */
    private static final String STATUS_SUCCESS     = "SUCCESS";
    private static final String STATUS_ERROR       = "ERROR";
    private static final String STATUS_TIMEOUT     = "TIMEOUT";
    private static final String STATUS_NO_MAIN     = "NO_MAIN";
    private static final String STATUS_NOT_FOUND   = "NOT_FOUND";
    private static final String STATUS_INTERRUPTED = "INTERRUPTED";

    /** The results table model. */
    private DefaultTableModel tableModel;

    /** The sortable table that shows results. */
    private SortableJTable table;

    /** The row sorter, used for the search filter. */
    private TableRowSorter<DefaultTableModel> rowSorter;

    /** The progress bar. */
    private JProgressBar progressBar;

    /** The label showing running status / final summary. */
    private JLabel statusLabel;

    /** The "Run" button. */
    private JButton runButton;

    /** The "Stop" button. */
    private JButton stopButton;

    /** The search text field. */
    private JTextField searchField;

    /** The current background worker, if any. */
    private SwingWorker<Void, TestResult> worker;

    /** All discovered test class names (sorted). */
    private final List<String> testClassNames = new ArrayList<>();

    /** Results in row order (parallel to the table model rows). */
    private final List<TestResult> results = new ArrayList<>();

    /** Total discovered tests (cached for the progress bar). */
    private int totalTests = 0;

    /** Number of tests completed so far. */
    private int completedTests = 0;

    /**
     * Internal holder for the result of one test run.
     */
    private static class TestResult {
        String className;
        String status;
        String output;
        String exceptionType;
        Throwable exception;
        long durationMs;

        String simpleName() {
            int idx = className.lastIndexOf('.');
            return idx >= 0 ? className.substring(idx + 1) : className;
        }
    }

    /**
     * Create and show the window. This is the entry point invoked by the
     * DevelopersToolsWindow.
     */
    public void createAndShowGUI() {
        setTitle("SPMF MainTest Auto-Runner  \u2014  v" + Main.SPMF_VERSION);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        add(buildTopPanel(), BorderLayout.NORTH);
        add(buildCenterPanel(), BorderLayout.CENTER);
        add(buildBottomPanel(), BorderLayout.SOUTH);

        setSize(950, 600);
        setLocationRelativeTo(null);
        setVisible(true);

        // Discover tests up-front so the user sees how many will run.
        discoverTests();
    }

    /**
     * Build the top panel containing the Run / Stop buttons and the
     * progress bar.
     */
    private JPanel buildTopPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 4));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        runButton = new JButton("Run all MainTest* classes");
        runButton.addActionListener(e -> startRunning());
        stopButton = new JButton("Stop");
        stopButton.setEnabled(false);
        stopButton.addActionListener(e -> stopRunning());
        left.add(runButton);
        left.add(stopButton);

        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("No tests discovered yet");

        panel.add(left, BorderLayout.WEST);
        panel.add(progressBar, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Build the center panel containing the search bar and the results table.
     */
    private JPanel buildCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));

        // ── Search bar ──────────────────────────────────────────────────────
        JPanel searchPanel = new JPanel(new BorderLayout(4, 0));
        searchPanel.add(new JLabel("Search:"), BorderLayout.WEST);
        searchField = new JTextField();
        searchField.setToolTipText(
                "Type to filter results by any column (case-insensitive).");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { updateFilter(); }
            public void removeUpdate(DocumentEvent e) { updateFilter(); }
            public void changedUpdate(DocumentEvent e) { updateFilter(); }
        });
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));

        // ── Table ──────────────────────────────────────────────────────────
        tableModel = new DefaultTableModel(COLUMN_NAMES, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 2) {
                    return Integer.class;
                }
                return String.class;
            }
        };

        table = new SortableJTable(tableModel);
        rowSorter = new TableRowSorter<>(tableModel);
        // Allow sorting by duration numerically rather than lexically.
        rowSorter.setComparator(2, java.util.Comparator.comparingInt(
                o -> ((Number) o).intValue()));
        table.setRowSorter(rowSorter);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setPreferredWidth(320);
        table.getColumnModel().getColumn(1).setPreferredWidth(90);
        table.getColumnModel().getColumn(2).setPreferredWidth(90);
        table.getColumnModel().getColumn(3).setPreferredWidth(280);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int viewRow = table.getSelectedRow();
                    if (viewRow >= 0) {
                        int modelRow = table.convertRowIndexToModel(viewRow);
                        showDetails(modelRow);
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new java.awt.Dimension(900, 400));

        panel.add(searchPanel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Build the bottom status panel.
     */
    private JPanel buildBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        statusLabel = new JLabel("Ready.");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        statusLabel.setForeground(Color.DARK_GRAY);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 2, 0, 2));
        panel.add(statusLabel, BorderLayout.WEST);
        return panel;
    }

    /**
     * Update the row filter based on the search field text.
     */
    private void updateFilter() {
        String text = searchField.getText();
        if (text == null || text.trim().isEmpty()) {
            rowSorter.setRowFilter(null);
        } else {
            try {
                rowSorter.setRowFilter(RowFilter.regexFilter(
                        "(?i)" + Pattern.quote(text)));
            } catch (PatternSyntaxException ex) {
                // Ignore malformed patterns.
            }
        }
    }

    /**
     * Discover all MainTest* classes in the test package and update the UI.
     */
    private void discoverTests() {
        testClassNames.clear();
        statusLabel.setText("Discovering tests...");
        try {
            findMainTestClasses(TEST_PACKAGE, testClassNames);
        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Error while discovering tests: " + e.getMessage());
            return;
        }
        Collections.sort(testClassNames);
        totalTests = testClassNames.size();
        progressBar.setMaximum(totalTests);
        progressBar.setValue(0);
        progressBar.setString("0 / " + totalTests + " tests");
        statusLabel.setText("Discovered " + totalTests
                + " MainTest* classes. Click \"Run\" to start.");
    }

    /**
     * Scan a package for classes whose simple name starts with "MainTest".
     * Supports both file-system class folders (e.g. running inside an IDE)
     * and packaged JARs (e.g. running from spmf.jar).
     *
     * @param packageName the package to scan
     * @param out         the list to which discovered fully-qualified class
     *                    names will be appended
     * @throws IOException if the package resources cannot be read
     */
    private static void findMainTestClasses(String packageName, List<String> out)
            throws IOException {
        String packagePath = packageName.replace('.', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = MainTestAutoRunnerWindow.class.getClassLoader();
        }
        Enumeration<URL> resources = classLoader.getResources(packagePath);
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            String protocol = url.getProtocol();
            if ("file".equals(protocol)) {
                File dir = new File(url.getFile());
                scanDirectory(dir, packageName, out);
            } else if ("jar".equals(protocol)) {
                String urlPath = url.getPath();
                int bang = urlPath.indexOf('!');
                if (bang >= 0) {
                    urlPath = urlPath.substring(0, bang);
                }
                if (urlPath.startsWith("file:")) {
                    urlPath = urlPath.substring(5);
                }
                String jarPath = URLDecoder.decode(urlPath, "UTF-8");
                try (JarFile jar = new JarFile(jarPath)) {
                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        String name = entry.getName();
                        if (name.startsWith(packagePath)
                                && name.endsWith(".class")
                                && !name.contains("$")) {
                            String className = name.replace('/', '.')
                                    .substring(0, name.length() - 6);
                            String simpleName = className.substring(
                                    className.lastIndexOf('.') + 1);
                            if (simpleName.startsWith("MainTest")) {
                                out.add(className);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Recursively scan a directory for MainTest*.class files.
     */
    private static void scanDirectory(File dir, String packageName,
                                      List<String> out) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, packageName + "." + file.getName(), out);
            } else if (file.getName().endsWith(".class")
                    && !file.getName().contains("$")) {
                String simpleName = file.getName().substring(
                        0, file.getName().length() - 6);
                if (simpleName.startsWith("MainTest")) {
                    out.add(packageName + "." + simpleName);
                }
            }
        }
    }

    /**
     * Start the background worker that runs every discovered test.
     */
    private void startRunning() {
        if (totalTests == 0) {
            JOptionPane.showMessageDialog(this,
                    "No MainTest* classes were discovered in package "
                            + TEST_PACKAGE + ".",
                    "Nothing to run",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (worker != null && !worker.isDone()) {
            return; // already running
        }

        // Clear previous results.
        results.clear();
        tableModel.setRowCount(0);
        completedTests = 0;
        progressBar.setValue(0);
        progressBar.setString("0 / " + totalTests + " tests");
        runButton.setEnabled(false);
        stopButton.setEnabled(true);
        statusLabel.setText("Running...");

        final List<String> toRun = new ArrayList<>(testClassNames);

        worker = new SwingWorker<Void, TestResult>() {
            @Override
            protected Void doInBackground() throws Exception {
                for (String className : toRun) {
                    if (isCancelled()) {
                        break;
                    }
                    TestResult result = runTestWithTimeout(className);
                    publish(result);
                }
                return null;
            }

            @Override
            protected void process(List<TestResult> chunks) {
                for (TestResult r : chunks) {
                    addResultRow(r);
                }
            }

            @Override
            protected void done() {
                runButton.setEnabled(true);
                stopButton.setEnabled(false);
                String msg;
                if (isCancelled()) {
                    msg = "Stopped after " + completedTests + " / "
                            + totalTests + " tests.";
                } else {
                    msg = "Completed all " + totalTests + " tests.";
                }
                int success = 0, error = 0, timeout = 0, other = 0;
                for (TestResult r : results) {
                    switch (r.status) {
                        case STATUS_SUCCESS: success++; break;
                        case STATUS_ERROR:   error++;   break;
                        case STATUS_TIMEOUT: timeout++; break;
                        default:             other++;   break;
                    }
                }
                statusLabel.setText(msg + "  Success=" + success
                        + "  Error=" + error + "  Timeout=" + timeout
                        + "  Other=" + other);
            }
        };
        worker.execute();
    }

    /**
     * Cancel the running worker, if any.
     */
    private void stopRunning() {
        if (worker != null && !worker.isDone()) {
            worker.cancel(true);
        }
    }

    /**
     * Add a result row to the table and update the progress bar.
     */
    private void addResultRow(TestResult r) {
        results.add(r);
        completedTests = results.size();
        tableModel.addRow(new Object[] {
                r.simpleName(),
                r.status,
                Integer.valueOf((int) r.durationMs),
                r.exceptionType != null ? r.exceptionType : ""
        });
        progressBar.setValue(completedTests);
        progressBar.setString(completedTests + " / " + totalTests + " tests");
    }

    /**
     * Run one test class's main() on a worker thread with a hard timeout.
     * Captures System.out / System.err and any throwable thrown by the test.
     *
     * @param className the fully-qualified name of the class to run
     * @return a populated {@link TestResult}
     */
    private TestResult runTestWithTimeout(String className) {
        final TestResult result = new TestResult();
        result.className = className;

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final PrintStream capture = new PrintStream(baos, true);
        final PrintStream originalOut = System.out;
        final PrintStream originalErr = System.err;

        final Throwable[] holder = new Throwable[1];
        final boolean[] completedFlag = new boolean[1];
        final String[] flaggedStatus = new String[1];

        Thread t = new Thread(() -> {
            System.setOut(capture);
            System.setErr(capture);
            try {
                Class<?> clazz = Class.forName(className);
                try {
                    Method mainMethod = clazz.getMethod("main", String[].class);
                    mainMethod.invoke(null, (Object) new String[0]);
                    completedFlag[0] = true;
                } catch (NoSuchMethodException nsme) {
                    flaggedStatus[0] = STATUS_NO_MAIN;
                    completedFlag[0] = true;
                }
            } catch (ClassNotFoundException cnfe) {
                holder[0] = cnfe;
                flaggedStatus[0] = STATUS_NOT_FOUND;
                completedFlag[0] = true;
            } catch (InvocationTargetException ite) {
                holder[0] = ite.getCause() != null ? ite.getCause() : ite;
                flaggedStatus[0] = STATUS_ERROR;
                completedFlag[0] = true;
            } catch (Throwable th) {
                holder[0] = th;
                flaggedStatus[0] = STATUS_ERROR;
                completedFlag[0] = true;
            } finally {
                System.setOut(originalOut);
                System.setErr(originalErr);
            }
        }, "MainTest-runner-" + className);
        t.setDaemon(true);

        long start = System.currentTimeMillis();
        t.start();
        try {
            t.join(PER_TEST_TIMEOUT_MS);
        } catch (InterruptedException ie) {
            flaggedStatus[0] = STATUS_INTERRUPTED;
        }
        long duration = System.currentTimeMillis() - start;

        if (t.isAlive()) {
            t.interrupt();
            flaggedStatus[0] = STATUS_TIMEOUT;
        }

        result.durationMs = duration;
        result.output = baos.toString();
        if (flaggedStatus[0] != null) {
            result.status = flaggedStatus[0];
        } else if (completedFlag[0] && holder[0] == null) {
            result.status = STATUS_SUCCESS;
        } else if (holder[0] != null) {
            result.status = STATUS_ERROR;
        } else {
            result.status = STATUS_SUCCESS;
        }
        if (holder[0] != null) {
            result.exception = holder[0];
            result.exceptionType = holder[0].getClass().getName();
        }
        return result;
    }

    /**
     * Show a non-modal details dialog for a particular test result.
     *
     * @param modelRow the model-row index into {@link #results}
     */
    private void showDetails(int modelRow) {
        if (modelRow < 0 || modelRow >= results.size()) {
            return;
        }
        TestResult r = results.get(modelRow);

        JDialog dialog = new JDialog(this, "Details: " + r.simpleName(), false);
        dialog.setLayout(new BorderLayout(8, 8));
        ((JPanel) dialog.getContentPane()).setBorder(
                BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // ── Summary header ─────────────────────────────────────────────────
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(new JLabel("Class: " + r.className));
        header.add(new JLabel("Status: " + r.status));
        header.add(new JLabel("Duration: " + r.durationMs + " ms"));
        if (r.exceptionType != null) {
            JLabel l4 = new JLabel("Exception: " + r.exceptionType);
            l4.setForeground(new Color(180, 0, 0));
            header.add(l4);
        }

        // ── Text area with output and stack trace ──────────────────────────
        JTextArea area = new JTextArea(28, 90);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        StringBuilder sb = new StringBuilder();
        sb.append("=== Console Output ===\n");
        sb.append(r.output != null && !r.output.isEmpty()
                ? r.output : "(no output)");
        if (r.exception != null) {
            sb.append("\n\n=== Stack Trace ===\n");
            StringWriter sw = new StringWriter();
            r.exception.printStackTrace(new PrintWriter(sw));
            sb.append(sw.toString());
        }
        area.setText(sb.toString());
        area.setCaretPosition(0);

        JScrollPane scroll = new JScrollPane(area);

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(ev -> dialog.dispose());
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.add(closeBtn);

        dialog.add(header, BorderLayout.NORTH);
        dialog.add(scroll, BorderLayout.CENTER);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    /**
     * Standalone entry point.
     */
    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            MainTestAutoRunnerWindow w = new MainTestAutoRunnerWindow();
            w.createAndShowGUI();
        });
    }
}