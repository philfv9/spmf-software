package ca.pfv.spmf.gui.developerswindow;

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
 *
 * Do not remove the copyright and license information
 */

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import ca.pfv.spmf.algorithmmanager.AlgorithmManager;
import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;

/**
 * Scans the SPMF documentation HTML files for command-line examples such as
 * {@code java -jar spmf.jar run CM-SPAM contextPrefixSpan.txt output.txt 0.5 2 6 1,3 1 true},
 * extracts the algorithm name, the input/output file names and the actual
 * parameter values used in the example, and runs every algorithm found
 * (via {@link AlgorithmManager}). Console output (System.out / System.err) is
 * redirected into a text pane, and each run's outcome (success / error /
 * timeout / skipped) is reported in a sortable/filterable {@link JTable}.
 * <p>
 * The command line is preferably extracted from
 * {@code <span class="highlight">} blocks in the HTML (falling back to
 * scanning the whole page text), which isolates it from surrounding prose.
 * <p>
 * Parameter values are extracted from the documented command line itself
 * (not from the generic "example" hints in {@link DescriptionOfParameter}),
 * using structural information from the algorithm's
 * {@link DescriptionOfAlgorithm} descriptor (see {@link #parseCommandMatch}):
 * <ul>
 *   <li><b>Mandatory zone</b> — the first
 *       {@code getNumberOfMandatoryParameters()} tokens after the file-name
 *       slots are always accepted by position, without content validation:
 *       a runnable documented example must supply a value for every
 *       mandatory parameter, so whatever token sits there is that value.</li>
 *   <li><b>Optional zone</b> — further tokens, up to the total declared
 *       parameter count, are only accepted if quoted or matching an
 *       unambiguous parameter shape ({@link #PARAM_TOKEN_PATTERN}), since
 *       there is no guarantee an optional parameter is present at all.</li>
 * </ul>
 * If no parameter tokens can be extracted and the algorithm requires at
 * least one mandatory parameter, the declared "example" values are used as a
 * fallback, flagged in the "Param Source" column.
 * <p>
 * The special placeholders {@code NOINPUT} and {@code no_output_file} are
 * always consumed as the input/output slot respectively, regardless of what
 * the algorithm descriptor reports.
 * <p>
 * The results table also shows the documentation URL for each algorithm;
 * double-clicking a row opens a details dialog with an "Open in Browser"
 * button.
 * <p>
 * {@link AlgorithmType#DATA_VIEWER} algorithms are skipped by default since
 * they only open a viewer on already-produced output. GUI tools and
 * experiment tools can optionally be skipped as well.
 *
 * @author Philippe Fournier-Viger
 */
public class AlgorithmExampleAutoRunnerWindow {

    // ─────────────────────────────────────────────────────────────────────────
    // Constants
    // ─────────────────────────────────────────────────────────────────────────

    /** Sentinel used in the docs for algorithms with no conventional input file. */
    private static final String NOINPUT_TOKEN = "NOINPUT";

    /** Sentinel used in the docs for algorithms with no conventional output file. */
    private static final String NOOUTPUT_TOKEN = "no_output_file";

    private static final String DEFAULT_DOC_FOLDER     = "D:\\workspace\\spmf\\doc";
    private static final String DEFAULT_TEST_FILES_FOLDER =
            "C:\\Users\\Phil\\Desktop\\SPMF2026\\test_files\\";
    private static final String DEFAULT_OUTPUT_FOLDER  =
            System.getProperty("java.io.tmpdir") + File.separator + "spmf_example_runner_output";

    private static final int MAX_CONSOLE_CHARS = 2_000_000;

    /**
     * Maximum number of whitespace-separated tokens captured after the
     * algorithm name (bounds the regex "tail" group).
     */
    private static final int MAX_TAIL_TOKENS = 40;

    /**
     * Matches {@code spmf.jar run <algo> <token> [...up to MAX_TAIL_TOKENS more tokens]}.
     * Group 1 = algorithm name.
     * Group 2 = first token after the algorithm name (typically the input
     *           file name, or the {@link #NOINPUT_TOKEN} placeholder).
     * Group 3 = remaining tokens on the line (bounded), parsed further in
     *           {@link #parseCommandMatch}.
     */
    private static final Pattern COMMAND_PATTERN = Pattern.compile(
            "spmf\\.jar\\s+run\\s+(\\S+)\\s+(\\S+)((?:\\s+\\S+){0," + MAX_TAIL_TOKENS + "})",
            Pattern.CASE_INSENSITIVE);

    /**
     * Matches a {@code <span class="highlight">...</span>} block from the HTML
     * documentation, used to isolate command-line examples from prose.
     */
    private static final Pattern HIGHLIGHT_SPAN = Pattern.compile(
            "<span\\b[^>]*\\bclass\\s*=\\s*[\"'][^\"']*\\bhighlight\\b[^\"']*[\"'][^>]*>(.*?)</span>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /**
     * Matches a token that unambiguously "looks like" a parameter value:
     * a boolean/null literal, a (possibly negative/decimal) number optionally
     * followed by a percent sign, a comma-separated list of such numbers, a
     * {@code name=value} pair, a single punctuation separator character, or a
     * quoted string. Used to validate tokens beyond the mandatory-parameter
     * count (see {@link #parseCommandMatch}); bare unquoted words are
     * intentionally not matched here.
     */
    private static final Pattern PARAM_TOKEN_PATTERN = Pattern.compile(
            "^(?:true|false|null"
            + "|-?[0-9]+(?:\\.[0-9]+)?%?"
            + "|-?[0-9]+(?:\\.[0-9]+)?(?:,-?[0-9]+(?:\\.[0-9]+)?)+"
            + "|[A-Za-z_][A-Za-z0-9_]*=[A-Za-z0-9._%\\-]+"
            + "|\"[^\"]*\""    // quoted string, e.g. " " or "euclidian"
            + "|'[^']*'"       // single-quoted string
            + "|[,;|:])$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern UNICODE_DASHES = Pattern.compile(
            "[\\u2010\\u2011\\u2012\\u2013\\u2014\\u2015\\u2212]");
    private static final Pattern NUMERIC_ENTITY  = Pattern.compile("&#(x?)([0-9a-fA-F]+);");

    // ─────────────────────────────────────────────────────────────────────────
    // Column indices for the results table
    // (kept as constants so the renderer / dialogs / CSV export all agree)
    // ─────────────────────────────────────────────────────────────────────────

    private static final int COL_INDEX        = 0;
    private static final int COL_ALGORITHM    = 1;
    private static final int COL_CATEGORY     = 2;
    private static final int COL_TYPE         = 3;
    private static final int COL_INPUT_FILE   = 4;
    private static final int COL_OUTPUT_FILE  = 5;
    private static final int COL_PARAMETERS   = 6;
    private static final int COL_PARAM_SOURCE = 7;
    private static final int COL_STATUS       = 8;
    private static final int COL_TIME_MS      = 9;
    private static final int COL_DOC_URL      = 10;
    private static final int COL_MESSAGE      = 11;

    // ─────────────────────────────────────────────────────────────────────────
    // GUI fields
    // ─────────────────────────────────────────────────────────────────────────

    private JFrame frame;

    private JTextField txtDocFolder;
    private JTextField txtTestFilesFolder;
    private JTextField txtOutputFolder;

    private JButton btnBrowseDoc;
    private JButton btnBrowseTestFiles;
    private JButton btnBrowseOutput;

    private JButton btnScan;
    private JButton btnRunAll;
    private JButton btnStop;
    private JButton btnClear;
    private JButton btnExportCsv;

    private JCheckBox chkSkipViewers;
    private JCheckBox chkSkipGuiTools;
    private JCheckBox chkSkipExperiments;

    private JSpinner spinnerTimeoutSeconds;

    private JTextArea txtConsole;
    private JButton   btnCopyConsole;
    private JButton   btnSaveConsole;
    private JButton   btnClearConsole;

    private JTable                         tableResults;
    private DefaultTableModel              modelResults;
    private TableRowSorter<DefaultTableModel> sorterResults;
    private JTextField                     txtFilterResults;

    private JLabel lblStatus;
    private JProgressBar progressBar;

    private JLabel lblTotal;
    private JLabel lblSuccess;
    private JLabel lblError;
    private JLabel lblTimeout;
    private JLabel lblSkipped;

    // ─────────────────────────────────────────────────────────────────────────
    // State
    // ─────────────────────────────────────────────────────────────────────────

    private AlgorithmManager    algorithmManager;
    private List<ExampleCase>   scannedCases;
    private final List<RunResult> runResultsList = new ArrayList<>();

    private volatile boolean stopRequested = false;
    private RunWorker currentWorker;

    /** Buffer that the redirected System.out / System.err writes into for the currently running algorithm. */
    private volatile StringBuilder currentCaptureBuffer;

    private PrintStream originalOut;
    private PrintStream originalErr;

    // ─────────────────────────────────────────────────────────────────────────
    // GUI construction
    // ─────────────────────────────────────────────────────────────────────────

    public void createAndShowGUI() {
        frame = new JFrame("SPMF Documentation - Automatic Example Runner");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(1300, 840);
        frame.setMinimumSize(new Dimension(1050, 640));
        frame.setLocationRelativeTo(null);

        JPanel main = new JPanel(new BorderLayout(8, 8));
        main.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        main.add(createTopPanel(),    BorderLayout.NORTH);
        main.add(createCenterPanel(), BorderLayout.CENTER);
        main.add(createStatusPanel(), BorderLayout.SOUTH);

        frame.setContentPane(main);
        frame.setVisible(true);
    }

    /**
     * Builds the configuration panel: three folder rows plus a controls
     * row, stacked vertically so each row gets exactly its preferred height.
     */
    private JPanel createTopPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JPanel docRow  = createFolderRow(
                "Documentation Folder", DEFAULT_DOC_FOLDER,
                f -> txtDocFolder = f,
                e -> browseFolder(txtDocFolder, "Select the SPMF documentation folder"),
                b -> btnBrowseDoc = b);
        JPanel testRow = createFolderRow(
                "Test Files Folder (physical .txt input files)", DEFAULT_TEST_FILES_FOLDER,
                f -> txtTestFilesFolder = f,
                e -> browseFolder(txtTestFilesFolder, "Select the test files folder"),
                b -> btnBrowseTestFiles = b);
        JPanel outRow  = createFolderRow(
                "Output Folder (results of algorithms will be written here)", DEFAULT_OUTPUT_FOLDER,
                f -> txtOutputFolder = f,
                e -> browseFolder(txtOutputFolder, "Select the output folder"),
                b -> btnBrowseOutput = b);
        JPanel controlsRow = createControlsRow();

        for (JComponent row : new JComponent[] { docRow, testRow, outRow, controlsRow }) {
            row.setAlignmentX(JComponent.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        }

        panel.add(docRow);
        panel.add(Box.createVerticalStrut(4));
        panel.add(testRow);
        panel.add(Box.createVerticalStrut(4));
        panel.add(outRow);
        panel.add(Box.createVerticalStrut(4));
        panel.add(controlsRow);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(BorderFactory.createTitledBorder("Configuration"));
        wrapper.add(panel, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel createFolderRow(String title, String defaultValue,
            java.util.function.Consumer<JTextField> fieldSetter,
            java.awt.event.ActionListener browseAction,
            java.util.function.Consumer<JButton> buttonSetter) {

        JPanel row = new JPanel(new BorderLayout(6, 6));
        row.setBorder(BorderFactory.createTitledBorder(title));

        JTextField field = new JTextField(defaultValue);
        fieldSetter.accept(field);

        JButton browse = new JButton("Browse…");
        browse.addActionListener(browseAction);
        buttonSetter.accept(browse);

        row.add(field,  BorderLayout.CENTER);
        row.add(browse, BorderLayout.EAST);
        return row;
    }

    private JPanel createControlsRow() {
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));

        JPanel checkboxes = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        chkSkipViewers    = new JCheckBox("Skip DATA_VIEWER algorithms", true);
        chkSkipGuiTools   = new JCheckBox("Skip GUI tools (OTHER_GUI_TOOL)", true);
        chkSkipExperiments = new JCheckBox("Skip experiment tools", true);
        checkboxes.add(chkSkipViewers);
        checkboxes.add(chkSkipGuiTools);
        checkboxes.add(chkSkipExperiments);
        checkboxes.add(new JLabel("   Timeout (sec/algorithm):"));
        spinnerTimeoutSeconds = new JSpinner(new SpinnerNumberModel(30, 1, 3600, 5));
        checkboxes.add(spinnerTimeoutSeconds);
        checkboxes.setAlignmentX(JComponent.LEFT_ALIGNMENT);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        btnScan = new JButton("1) Scan Documentation");
        btnScan.addActionListener(e -> startScan());

        btnRunAll = new JButton("2) Run All Examples");
        btnRunAll.setEnabled(false);
        btnRunAll.addActionListener(e -> startRunAll());

        btnStop = new JButton("Stop");
        btnStop.setEnabled(false);
        btnStop.addActionListener(e -> stopRun());

        btnExportCsv = new JButton("Export Results to CSV…");
        btnExportCsv.setEnabled(false);
        btnExportCsv.addActionListener(e -> exportResultsToCsv());

        btnClear = new JButton("Clear Results");
        btnClear.addActionListener(e -> clearResults());

        buttons.add(btnScan);
        buttons.add(btnRunAll);
        buttons.add(btnStop);
        buttons.add(btnExportCsv);
        buttons.add(btnClear);
        buttons.setAlignmentX(JComponent.LEFT_ALIGNMENT);

        container.add(checkboxes);
        container.add(buttons);
        return container;
    }

    private JComponent createCenterPanel() {
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                createResultsTablePanel(), createConsolePanel());
        split.setResizeWeight(0.55);
        split.setOneTouchExpandable(true);
        split.setContinuousLayout(true);
        return split;
    }

    private JPanel createResultsTablePanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder(
                "Results  —  double-click a row for full details, console output, "
                + "stack trace and documentation page"));

        modelResults = new DefaultTableModel(
                new Object[] {
                        "#", "Algorithm", "Category", "Type",
                        "Input File", "Output File", "Parameters Used",
                        "Param Source", "Status", "Time (ms)",
                        "Doc URL",
                        "Message" }, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
            @Override public Class<?> getColumnClass(int col) {
                if (col == COL_INDEX || col == COL_TIME_MS) return Integer.class;
                return String.class;
            }
        };
        tableResults  = new JTable(modelResults);
        sorterResults = new TableRowSorter<>(modelResults);
        tableResults.setRowSorter(sorterResults);
        tableResults.setRowHeight(22);

        tableResults.getColumnModel().getColumn(COL_PARAMETERS).setPreferredWidth(160);
        tableResults.getColumnModel().getColumn(COL_DOC_URL).setPreferredWidth(240);
        tableResults.getColumnModel().getColumn(COL_MESSAGE).setPreferredWidth(220);

        tableResults.setDefaultRenderer(Object.class,  new StatusRowRenderer());
        tableResults.setDefaultRenderer(Integer.class, new StatusRowRenderer());

        tableResults.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) showDetailsForSelectedRow();
            }
        });

        JPanel filterPanel = new JPanel(new BorderLayout(4, 4));
        filterPanel.add(new JLabel("Filter:"), BorderLayout.WEST);
        txtFilterResults = new JTextField();
        txtFilterResults.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate (javax.swing.event.DocumentEvent e) { applyFilter(); }
            @Override public void removeUpdate (javax.swing.event.DocumentEvent e) { applyFilter(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            private void applyFilter() {
                String text = txtFilterResults.getText().trim();
                if (text.isEmpty()) {
                    sorterResults.setRowFilter(null);
                } else {
                    try {
                        sorterResults.setRowFilter(
                                RowFilter.regexFilter("(?i)" + Pattern.quote(text)));
                    } catch (Exception ex) {
                        sorterResults.setRowFilter(null);
                    }
                }
            }
        });
        filterPanel.add(txtFilterResults, BorderLayout.CENTER);

        JPanel summaryPanel = new JPanel(new GridLayout(1, 5, 8, 0));
        lblTotal   = createSummaryLabel("Total: 0",     Color.BLACK);
        lblSuccess = createSummaryLabel("Success: 0",   new Color(30, 120, 30));
        lblError   = createSummaryLabel("Errors: 0",    new Color(160, 40, 40));
        lblTimeout = createSummaryLabel("Timeouts: 0",  new Color(160, 90, 10));
        lblSkipped = createSummaryLabel("Skipped: 0",   Color.GRAY);
        summaryPanel.add(lblTotal);
        summaryPanel.add(lblSuccess);
        summaryPanel.add(lblError);
        summaryPanel.add(lblTimeout);
        summaryPanel.add(lblSkipped);

        JPanel north = new JPanel(new BorderLayout());
        north.add(filterPanel,  BorderLayout.NORTH);
        north.add(summaryPanel, BorderLayout.SOUTH);

        panel.add(north,                       BorderLayout.NORTH);
        panel.add(new JScrollPane(tableResults), BorderLayout.CENTER);
        return panel;
    }

    private JLabel createSummaryLabel(String text, Color color) {
        JLabel l = new JLabel(text, JLabel.CENTER);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        l.setForeground(color);
        return l;
    }

    private JPanel createConsolePanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder(
                "Console Output (System.out / System.err)"));

        txtConsole = new JTextArea();
        txtConsole.setEditable(false);
        txtConsole.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        txtConsole.setLineWrap(false);
        panel.add(new JScrollPane(txtConsole), BorderLayout.CENTER);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btnCopyConsole = new JButton("Copy Console");
        btnCopyConsole.addActionListener(e -> copyToClipboard(txtConsole.getText()));
        btnSaveConsole = new JButton("Save Console…");
        btnSaveConsole.addActionListener(e -> saveTextToFile(txtConsole.getText(), "console_log.txt"));
        btnClearConsole = new JButton("Clear Console");
        btnClearConsole.addActionListener(e -> txtConsole.setText(""));
        btnRow.add(btnCopyConsole);
        btnRow.add(btnSaveConsole);
        btnRow.add(btnClearConsole);
        panel.add(btnRow, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createStatusPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        lblStatus = new JLabel(
                "Ready. Step 1: Scan the documentation folder. Step 2: Run all examples.");
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        panel.add(lblStatus,   BorderLayout.WEST);
        panel.add(progressBar, BorderLayout.EAST);
        return panel;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Row colour renderer
    // ─────────────────────────────────────────────────────────────────────────

    private class StatusRowRenderer extends DefaultTableCellRenderer {

        private final Map<java.awt.font.TextAttribute, Object> underlineAttributes;

        StatusRowRenderer() {
            underlineAttributes = new HashMap<>();
            underlineAttributes.put(
                    java.awt.font.TextAttribute.UNDERLINE,
                    java.awt.font.TextAttribute.UNDERLINE_ON);
        }

        @Override
        public java.awt.Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {

            super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);

            setFont(table.getFont());

            if (!isSelected) {
                int modelRow = table.convertRowIndexToModel(row);
                Object status = table.getModel().getValueAt(modelRow, COL_STATUS);
                Color color;
                switch (String.valueOf(status)) {
                    case "SUCCESS": color = new Color(225, 255, 225); break;
                    case "ERROR":   color = new Color(255, 220, 220); break;
                    case "TIMEOUT": color = new Color(255, 240, 200); break;
                    case "SKIPPED": color = new Color(235, 235, 235); break;
                    default:        color = Color.WHITE;
                }
                setBackground(color);
            }

            int modelCol = table.convertColumnIndexToModel(column);
            if (modelCol == COL_DOC_URL && value != null && !value.toString().isEmpty()) {
                setForeground(isSelected ? Color.WHITE : new Color(0, 70, 180));
                setFont(table.getFont().deriveFont(underlineAttributes));
                setText("<html><a href=''>" + value + "</a></html>");
            } else if (!isSelected) {
                setForeground(Color.BLACK);
            }

            return this;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Browsing helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void browseFolder(JTextField target, String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        File current = new File(target.getText().trim());
        if (current.exists()) chooser.setCurrentDirectory(current);
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            target.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 1: Scan documentation
    // ─────────────────────────────────────────────────────────────────────────

    private void startScan() {
        String dirPath = txtDocFolder.getText().trim();
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            JOptionPane.showMessageDialog(frame,
                    "The specified documentation folder is not valid:\n" + dirPath,
                    "Invalid folder", JOptionPane.ERROR_MESSAGE);
            return;
        }

        btnScan.setEnabled(false);
        btnRunAll.setEnabled(false);
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        progressBar.setString("Scanning documentation…");
        lblStatus.setText("Scanning documentation folder…");

        new SwingWorker<ScanData, Void>() {
            @Override protected ScanData doInBackground() throws Exception {
                return scanDocumentationFolder(dir.toPath());
            }
            @Override protected void done() {
                progressBar.setIndeterminate(false);
                progressBar.setVisible(false);
                btnScan.setEnabled(true);
                try {
                    ScanData data = get();
                    scannedCases = new ArrayList<>(data.casesByKey.values());
                    Collections.sort(scannedCases);
                    long distinctAlgos = scannedCases.stream()
                            .map(c -> c.algorithm.toLowerCase()).distinct().count();
                    long withDocParams = scannedCases.stream()
                            .filter(c -> !c.parameters.isEmpty()).count();
                    lblStatus.setText(String.format(
                            "Scan complete: %d HTML file(s) scanned, %d example case(s) found "
                            + "(%d distinct algorithm names, %d with parameter values extracted "
                            + "from the documentation). Click \"Run All Examples\" to proceed.",
                            data.htmlFileCount, scannedCases.size(),
                            distinctAlgos, withDocParams));
                    btnRunAll.setEnabled(!scannedCases.isEmpty());
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(frame,
                            "Error while scanning documentation:\n" + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                    lblStatus.setText("Scan failed.");
                }
            }
        }.execute();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 2: Run all examples
    // ─────────────────────────────────────────────────────────────────────────

    private void startRunAll() {
        if (scannedCases == null || scannedCases.isEmpty()) {
            JOptionPane.showMessageDialog(frame,
                    "Please scan the documentation folder first.",
                    "No data", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String testFolderPath = txtTestFilesFolder.getText().trim();
        File testFolder = new File(testFolderPath);
        if (!testFolder.exists() || !testFolder.isDirectory()) {
            JOptionPane.showMessageDialog(frame,
                    "The specified test files folder is not valid:\n" + testFolderPath,
                    "Invalid folder", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String outputFolderPath = txtOutputFolder.getText().trim();
        File outputFolder = new File(outputFolderPath);
        if (!outputFolder.exists() && !outputFolder.mkdirs()) {
            JOptionPane.showMessageDialog(frame,
                    "Could not create the output folder:\n" + outputFolderPath,
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        AlgorithmManager manager = getAlgorithmManagerInstance();
        if (manager == null) {
            JOptionPane.showMessageDialog(frame,
                    "Could not load AlgorithmManager (list of SPMF algorithms).",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        clearResults();

        lblStatus.setText("Indexing test files…");
        Map<String, String> testFileIndex;
        try {
            testFileIndex = buildTestFileIndex(testFolder.toPath());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame,
                    "Error while indexing the test files folder:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int timeoutSeconds = (Integer) spinnerTimeoutSeconds.getValue();

        stopRequested = false;
        btnScan.setEnabled(false);
        btnRunAll.setEnabled(false);
        btnStop.setEnabled(true);
        btnExportCsv.setEnabled(false);
        progressBar.setVisible(true);
        progressBar.setIndeterminate(false);
        progressBar.setMaximum(scannedCases.size());
        progressBar.setValue(0);
        progressBar.setString("0 / " + scannedCases.size());
        lblStatus.setText("Running examples…");

        redirectConsoleOutput();

        currentWorker = new RunWorker(
                new ArrayList<>(scannedCases), testFileIndex, outputFolder, timeoutSeconds);
        currentWorker.execute();
    }

    private void stopRun() {
        stopRequested = true;
        if (currentWorker != null) currentWorker.cancel(true);
        lblStatus.setText("Stop requested… finishing current algorithm.");
        btnStop.setEnabled(false);
    }

    private void clearResults() {
        runResultsList.clear();
        modelResults.setRowCount(0);
        updateSummary();
        txtConsole.setText("");
        btnExportCsv.setEnabled(false);
        lblStatus.setText("Results cleared.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RunWorker
    // ─────────────────────────────────────────────────────────────────────────

    private class RunWorker extends SwingWorker<Void, RunResult> {

        private final List<ExampleCase>   cases;
        private final Map<String, String> testFileIndex;
        private final File                outputFolder;
        private final int                 timeoutSeconds;
        private int counter = 0;

        RunWorker(List<ExampleCase> cases, Map<String, String> testFileIndex,
                File outputFolder, int timeoutSeconds) {
            this.cases          = cases;
            this.testFileIndex  = testFileIndex;
            this.outputFolder   = outputFolder;
            this.timeoutSeconds = timeoutSeconds;
        }

        @Override
        protected Void doInBackground() {
            AlgorithmManager manager = getAlgorithmManagerInstance();
            for (ExampleCase ec : cases) {
                if (isCancelled() || stopRequested) break;
                counter++;
                RunResult result;
                try {
                    result = runSingleCase(
                            manager, ec, testFileIndex, outputFolder, timeoutSeconds, counter);
                } catch (Exception unexpected) {
                    // Defensive: never let one bad case abort the whole batch run.
                    result = new RunResult();
                    result.index         = counter;
                    result.algorithm     = ec.algorithm;
                    result.inputFileName = ec.inputFileName == null ? NOINPUT_TOKEN : ec.inputFileName;
                    result.status        = "ERROR";
                    result.errorMessage  = "Unexpected error: " + unexpected;
                    result.stackTrace    = stackTraceToString(unexpected);
                }
                publish(result);
            }
            return null;
        }

        @Override
        protected void process(List<RunResult> chunks) {
            for (RunResult r : chunks) addResultRow(r);
            progressBar.setValue(counter);
            progressBar.setString(counter + " / " + cases.size());
        }

        @Override
        protected void done() {
            restoreConsoleOutput();
            btnScan.setEnabled(true);
            btnRunAll.setEnabled(true);
            btnStop.setEnabled(false);
            btnExportCsv.setEnabled(!runResultsList.isEmpty());
            progressBar.setVisible(false);
            lblStatus.setText(stopRequested
                    ? "Run stopped by user. " + summaryString()
                    : "Run complete. "        + summaryString());
        }
    }

    private String summaryString() {
        long success = runResultsList.stream().filter(r -> "SUCCESS".equals(r.status)).count();
        long error   = runResultsList.stream().filter(r -> "ERROR".equals(r.status)).count();
        long timeout = runResultsList.stream().filter(r -> "TIMEOUT".equals(r.status)).count();
        long skipped = runResultsList.stream().filter(r -> "SKIPPED".equals(r.status)).count();
        return String.format("%d total, %d success, %d error(s), %d timeout(s), %d skipped.",
                runResultsList.size(), success, error, timeout, skipped);
    }

    private void addResultRow(RunResult r) {
        runResultsList.add(r);
        modelResults.addRow(new Object[] {
                r.index, r.algorithm, r.category, r.algorithmType,
                r.inputFileName, r.outputFilePath,
                r.parametersUsed, r.parameterSource,
                r.status, r.timeMs,
                r.docUrl,
                r.errorMessage
        });
        updateSummary();
    }

    private void updateSummary() {
        long success = runResultsList.stream().filter(r -> "SUCCESS".equals(r.status)).count();
        long error   = runResultsList.stream().filter(r -> "ERROR".equals(r.status)).count();
        long timeout = runResultsList.stream().filter(r -> "TIMEOUT".equals(r.status)).count();
        long skipped = runResultsList.stream().filter(r -> "SKIPPED".equals(r.status)).count();
        lblTotal.setText("Total: "    + runResultsList.size());
        lblSuccess.setText("Success: " + success);
        lblError.setText("Errors: "    + error);
        lblTimeout.setText("Timeouts: " + timeout);
        lblSkipped.setText("Skipped: "  + skipped);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Running a single algorithm example
    // ─────────────────────────────────────────────────────────────────────────

    private RunResult runSingleCase(AlgorithmManager manager, ExampleCase ec,
            Map<String, String> testFileIndex, File outputFolder,
            int timeoutSeconds, int index) {

        RunResult r = new RunResult();
        r.index         = index;
        r.algorithm     = ec.algorithm;
        r.inputFileName = (ec.inputFileName == null) ? NOINPUT_TOKEN : ec.inputFileName;

        appendConsoleText(
                "\n===================================================================\n"
                + "[" + index + "] Running: " + ec.algorithm
                + "   (input=" + r.inputFileName
                + ", params=[" + String.join(" ", ec.parameters) + "])\n"
                + "===================================================================\n");

        if (manager == null) {
            r.status       = "ERROR";
            r.errorMessage = "AlgorithmManager not available";
            return r;
        }

        DescriptionOfAlgorithm desc = manager.getDescriptionOfAlgorithm(ec.algorithm);
        if (desc == null) {
            r.status       = "SKIPPED";
            r.errorMessage = "Algorithm name not found in AlgorithmManager "
                    + "(may be a tool name or a typo in the documentation)";
            appendConsoleText(r.errorMessage + "\n");
            return r;
        }

        try {
            String url = desc.getURLOfDocumentation();
            r.docUrl = (url == null || url.trim().isEmpty()) ? "" : url.trim();
        } catch (Exception ex) {
            r.docUrl = "";
        }

        r.category      = desc.getAlgorithmCategory();
        AlgorithmType type = desc.getAlgorithmType();
        r.algorithmType = String.valueOf(type);

        if (type == AlgorithmType.DATA_VIEWER && chkSkipViewers.isSelected()) {
            r.status       = "SKIPPED";
            r.errorMessage = "Viewer algorithm (skipped by filter)";
            return r;
        }
        if (type == AlgorithmType.OTHER_GUI_TOOL && chkSkipGuiTools.isSelected()) {
            r.status       = "SKIPPED";
            r.errorMessage = "GUI tool (skipped by filter)";
            return r;
        }
        if (type == AlgorithmType.EXPERIMENT_TOOL && chkSkipExperiments.isSelected()) {
            r.status       = "SKIPPED";
            r.errorMessage = "Experiment tool (skipped by filter)";
            return r;
        }

        String inputPath = null;
        if (ec.inputFileName != null) {
            inputPath = testFileIndex.get(ec.inputFileName.toLowerCase());
            if (inputPath == null) {
                r.status       = "ERROR";
                r.errorMessage = "Input file not found in test files folder: " + ec.inputFileName;
                appendConsoleText(r.errorMessage + "\n");
                return r;
            }
        }
        r.inputFilePath = inputPath;

        String safeAlgo   = ec.algorithm.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        String safeInput  = (ec.inputFileName == null) ? "noinput"
                : ec.inputFileName.replaceAll("[^a-zA-Z0-9_\\-.]", "_");
        String safeParams = ec.parameters.isEmpty() ? ""
                : "__" + String.join("_", ec.parameters)
                         .replaceAll("[^a-zA-Z0-9_\\-.]", "_");
        File outFile = new File(outputFolder,
                safeAlgo + "__" + safeInput + safeParams + "__output.txt");
        r.outputFilePath = outFile.getAbsolutePath();

        String[] parameters;
        if (!ec.parameters.isEmpty()) {
            parameters        = ec.parameters.toArray(new String[0]);
            r.parameterSource = "DOC";
        } else if (desc.getNumberOfMandatoryParameters() > 0) {
            parameters        = buildFallbackParametersFromDescription(desc);
            r.parameterSource = "DEFAULT (doc had none)";
        } else {
            parameters        = new String[0];
            r.parameterSource = "NONE";
        }
        r.parametersUsed = String.join(" ", parameters);

        currentCaptureBuffer = new StringBuilder();
        long start = System.currentTimeMillis();

        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread t = new Thread(runnable, "SPMF-Example-Runner-" + ec.algorithm);
            t.setDaemon(true);
            return t;
        });

        final String   fInputPath  = inputPath;
        final String   fOutputPath = r.outputFilePath;
        final String[] fParameters = parameters;

        Callable<Throwable> task = () -> {
            try {
                desc.runAlgorithm(fParameters, fInputPath, fOutputPath);
                return null;
            } catch (Throwable t) {
                return t;
            }
        };

        Future<Throwable> future = executor.submit(task);
        try {
            Throwable error = future.get(timeoutSeconds, TimeUnit.SECONDS);
            r.timeMs = System.currentTimeMillis() - start;
            if (error == null) {
                r.status = "SUCCESS";
            } else {
                r.status       = "ERROR";
                r.errorMessage = error.toString();
                r.stackTrace   = stackTraceToString(error);
                appendConsoleText("ERROR: " + error + "\n");
            }
        } catch (TimeoutException te) {
            future.cancel(true);
            r.timeMs       = System.currentTimeMillis() - start;
            r.status       = "TIMEOUT";
            r.errorMessage = "Execution exceeded timeout of " + timeoutSeconds + " second(s)";
            appendConsoleText(r.errorMessage + "\n");
        } catch (Exception ex) {
            r.timeMs       = System.currentTimeMillis() - start;
            r.status       = "ERROR";
            r.errorMessage = ex.toString();
            r.stackTrace   = stackTraceToString(ex);
            appendConsoleText("ERROR: " + ex + "\n");
        } finally {
            executor.shutdownNow();
            r.consoleOutput      = (currentCaptureBuffer == null) ? "" : currentCaptureBuffer.toString();
            currentCaptureBuffer = null;
        }

        r.docOutputFileName = ec.outputFileNameInDoc;
        r.sourceDocFiles    = new ArrayList<>(ec.sourceDocFiles);

        return r;
    }

    private String[] buildFallbackParametersFromDescription(DescriptionOfAlgorithm desc) {
        DescriptionOfParameter[] params = desc.getParametersDescription();
        if (params == null || params.length == 0) return new String[0];

        List<String> values = new ArrayList<>();
        for (DescriptionOfParameter p : params) {
            String example = (p == null) ? null : p.getExample();
            values.add(example == null ? "" : example);
        }

        while (!values.isEmpty()) {
            int                    lastIndex = values.size() - 1;
            DescriptionOfParameter lastParam = params[lastIndex];
            String                 lastValue = values.get(lastIndex);
            boolean optional = (lastParam != null && lastParam.isOptional());
            if (optional && (lastValue == null || lastValue.trim().isEmpty())) {
                values.remove(lastIndex);
            } else {
                break;
            }
        }

        return values.toArray(new String[0]);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Details dialog
    // ─────────────────────────────────────────────────────────────────────────

    private void showDetailsForSelectedRow() {
        int viewRow = tableResults.getSelectedRow();
        if (viewRow < 0) return;
        int modelRow = tableResults.convertRowIndexToModel(viewRow);
        if (modelRow < 0 || modelRow >= runResultsList.size()) return;
        RunResult r = runResultsList.get(modelRow);

        JDialog dialog = new JDialog(frame, "Details: " + r.algorithm, true);
        dialog.setSize(960, 680);
        dialog.setMinimumSize(new Dimension(640, 440));
        dialog.setLocationRelativeTo(frame);
        dialog.setLayout(new BorderLayout(8, 8));

        StringBuilder info = new StringBuilder();
        info.append("Algorithm            : ").append(r.algorithm)         .append(System.lineSeparator());
        info.append("Category             : ").append(r.category)          .append(System.lineSeparator());
        info.append("Type                 : ").append(r.algorithmType)     .append(System.lineSeparator());
        info.append("Documentation URL    : ").append(r.docUrl)            .append(System.lineSeparator());
        info.append("Input file name      : ").append(r.inputFileName)     .append(System.lineSeparator());
        info.append("Input file path      : ").append(r.inputFilePath)     .append(System.lineSeparator());
        info.append("Output file path     : ").append(r.outputFilePath)    .append(System.lineSeparator());
        info.append("Output name (in doc) : ").append(r.docOutputFileName) .append(System.lineSeparator());
        info.append("Parameters used      : ").append(r.parametersUsed)    .append(System.lineSeparator());
        info.append("Parameter source     : ").append(r.parameterSource)   .append(System.lineSeparator());
        info.append("Status               : ").append(r.status)            .append(System.lineSeparator());
        info.append("Time (ms)            : ").append(r.timeMs)            .append(System.lineSeparator());
        info.append("Message              : ").append(r.errorMessage)      .append(System.lineSeparator());
        info.append("Found in doc page(s) : ")
            .append((r.sourceDocFiles == null || r.sourceDocFiles.isEmpty())
                    ? "(unknown)" : String.join(", ", r.sourceDocFiles))
            .append(System.lineSeparator());

        JTextArea txtInfo = new JTextArea(info.toString());
        txtInfo.setEditable(false);
        txtInfo.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Summary", new JScrollPane(txtInfo));

        JTextArea txtConsoleForRow = new JTextArea(
                (r.consoleOutput == null || r.consoleOutput.isEmpty())
                        ? "(no console output captured)" : r.consoleOutput);
        txtConsoleForRow.setEditable(false);
        txtConsoleForRow.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        tabs.addTab("Console Output", new JScrollPane(txtConsoleForRow));

        if (r.stackTrace != null) {
            JTextArea txtStack = new JTextArea(r.stackTrace);
            txtStack.setEditable(false);
            txtStack.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
            tabs.addTab("Stack Trace", new JScrollPane(txtStack));
        }

        dialog.add(tabs, BorderLayout.CENTER);

        final String fullText =
                info.toString() + System.lineSeparator()
                + "--- Console output ---" + System.lineSeparator()
                + (r.consoleOutput == null ? "" : r.consoleOutput)
                + System.lineSeparator()
                + (r.stackTrace == null ? ""
                        : "--- Stack trace ---" + System.lineSeparator() + r.stackTrace);

        JButton btnCopy = new JButton("Copy All to Clipboard");
        btnCopy.addActionListener(e -> copyToClipboard(fullText));

        JButton btnSave = new JButton("Save to File…");
        btnSave.addActionListener(e -> saveTextToFile(fullText, r.algorithm + "_details.txt"));

        JButton btnOpenUrl = new JButton("Open Documentation in Browser");
        boolean hasUrl = r.docUrl != null && !r.docUrl.isEmpty();
        btnOpenUrl.setEnabled(hasUrl);
        btnOpenUrl.setToolTipText(hasUrl ? r.docUrl : "No documentation URL available");
        btnOpenUrl.addActionListener(e -> openUrlInBrowser(r.docUrl, dialog));

        JButton btnClose = new JButton("Close");
        btnClose.addActionListener(e -> dialog.dispose());

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        btnRow.add(btnOpenUrl);
        btnRow.add(btnCopy);
        btnRow.add(btnSave);
        btnRow.add(btnClose);
        dialog.add(btnRow, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    private void openUrlInBrowser(String url, java.awt.Component parent) {
        if (url == null || url.isEmpty()) return;
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                JOptionPane.showMessageDialog(parent,
                        "Cannot open a browser on this platform.\nURL: " + url,
                        "Browser not supported", JOptionPane.WARNING_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent,
                    "Could not open URL:\n" + url + "\n\nError: " + ex.getMessage(),
                    "Error opening URL", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Export to CSV
    // ─────────────────────────────────────────────────────────────────────────

    private void exportResultsToCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Results to CSV");
        chooser.setSelectedFile(new File("spmf_example_run_results.csv"));
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;

        StringBuilder sb = new StringBuilder();
        sb.append("Index,Algorithm,Category,Type,InputFile,OutputFile,"
                + "Parameters,ParamSource,Status,TimeMs,DocURL,Message")
          .append(System.lineSeparator());

        for (RunResult r : runResultsList) {
            sb.append(r.index)                      .append(',')
              .append(csvEscape(r.algorithm))        .append(',')
              .append(csvEscape(r.category))         .append(',')
              .append(csvEscape(r.algorithmType))    .append(',')
              .append(csvEscape(r.inputFileName))    .append(',')
              .append(csvEscape(r.outputFilePath))   .append(',')
              .append(csvEscape(r.parametersUsed))   .append(',')
              .append(csvEscape(r.parameterSource))  .append(',')
              .append(csvEscape(r.status))           .append(',')
              .append(r.timeMs)                      .append(',')
              .append(csvEscape(r.docUrl))           .append(',')
              .append(csvEscape(r.errorMessage))
              .append(System.lineSeparator());
        }

        try {
            Files.write(chooser.getSelectedFile().toPath(),
                    sb.toString().getBytes(StandardCharsets.UTF_8));
            lblStatus.setText("Results exported to: "
                    + chooser.getSelectedFile().getAbsolutePath());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame,
                    "Could not save CSV file:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String csvEscape(String s) {
        if (s == null) return "";
        String escaped = s.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\n") || escaped.contains("\"")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Console redirection
    // ─────────────────────────────────────────────────────────────────────────

    private void redirectConsoleOutput() {
        originalOut = System.out;
        originalErr = System.err;
        PrintStream ps = new PrintStream(
                new ConsoleRedirectStream(), true, StandardCharsets.UTF_8);
        System.setOut(ps);
        System.setErr(ps);
    }

    private void restoreConsoleOutput() {
        if (originalOut != null) System.setOut(originalOut);
        if (originalErr != null) System.setErr(originalErr);
    }

    private class ConsoleRedirectStream extends OutputStream {
        private final StringBuilder lineBuffer = new StringBuilder();

        @Override
        public synchronized void write(int b) {
            char c = (char) b;
            lineBuffer.append(c);
            if (c == '\n') flushLine();
        }

        private void flushLine() {
            String text = lineBuffer.toString();
            lineBuffer.setLength(0);
            appendConsoleText(text);
            StringBuilder buf = currentCaptureBuffer;
            if (buf != null) buf.append(text);
        }

        @Override
        public synchronized void flush() {
            if (lineBuffer.length() > 0) flushLine();
        }
    }

    private void appendConsoleText(String text) {
        if (text == null) return;
        SwingUtilities.invokeLater(() -> {
            txtConsole.append(text);
            if (txtConsole.getDocument().getLength() > MAX_CONSOLE_CHARS) {
                try {
                    txtConsole.getDocument().remove(0,
                            txtConsole.getDocument().getLength() - MAX_CONSOLE_CHARS);
                } catch (Exception ignored) {
                    // Best-effort trimming; never let this crash the console.
                }
            }
            txtConsole.setCaretPosition(txtConsole.getDocument().getLength());
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Documentation scanning
    // ─────────────────────────────────────────────────────────────────────────

    private static class ExampleCase implements Comparable<ExampleCase> {
        final String       algorithm;
        final String       inputFileName;
        final String       outputFileNameInDoc;
        final List<String> parameters;
        final TreeSet<String> sourceDocFiles = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        ExampleCase(String algorithm, String inputFileName,
                String outputFileNameInDoc, List<String> parameters) {
            this.algorithm           = algorithm;
            this.inputFileName       = inputFileName;
            this.outputFileNameInDoc = outputFileNameInDoc;
            this.parameters          = (parameters == null) ? new ArrayList<>() : parameters;
        }

        String dedupKey() {
            return algorithm.toLowerCase() + "|"
                    + (inputFileName == null ? "" : inputFileName.toLowerCase()) + "|"
                    + String.join(",", parameters).toLowerCase();
        }

        @Override
        public int compareTo(ExampleCase o) {
            int c = algorithm.compareToIgnoreCase(o.algorithm);
            if (c != 0) return c;
            String a = (inputFileName   == null) ? "" : inputFileName;
            String b = (o.inputFileName == null) ? "" : o.inputFileName;
            c = a.compareToIgnoreCase(b);
            if (c != 0) return c;
            return String.join(",", parameters)
                    .compareToIgnoreCase(String.join(",", o.parameters));
        }
    }

    private static class RunResult {
        int    index;
        String algorithm;
        String category;
        String algorithmType;
        String inputFileName;
        String inputFilePath;
        String outputFilePath;
        String docOutputFileName;
        String parametersUsed;
        String parameterSource;
        String status;
        String errorMessage;
        String stackTrace;
        String consoleOutput;
        String docUrl;
        List<String> sourceDocFiles;
        long   timeMs;
    }

    private static class ScanData {
        final LinkedHashMap<String, ExampleCase> casesByKey = new LinkedHashMap<>();
        int htmlFileCount;
    }

    private ScanData scanDocumentationFolder(Path dir) throws IOException {
        ScanData result  = new ScanData();
        AlgorithmManager manager = getAlgorithmManagerInstance();

        List<Path> htmlFiles;
        try (Stream<Path> stream = Files.walk(dir)) {
            htmlFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.endsWith(".html") || n.endsWith(".htm");
                    })
                    .collect(Collectors.toList());
        }
        result.htmlFileCount = htmlFiles.size();

        for (Path htmlFile : htmlFiles) {
            String content      = readFileSafely(htmlFile);
            if (content == null || content.isEmpty()) continue;
            String relativeName = dir.relativize(htmlFile).toString();

            boolean foundInSpan = false;

            // Preferred: extract the command line from <span class="highlight"> blocks,
            // which isolates it from surrounding prose.
            Matcher spanMatcher = HIGHLIGHT_SPAN.matcher(content);
            while (spanMatcher.find()) {
                String spanContent = spanMatcher.group(1);
                String textOnly = stripHtml(spanContent);

                Matcher m = COMMAND_PATTERN.matcher(textOnly);
                if (m.find()) {
                    ExampleCase ec = parseCommandMatch(m, manager);
                    if (ec != null) {
                        foundInSpan = true;
                        registerExampleCase(result, ec, relativeName);
                    }
                }
            }

            // Fallback: no highlight span found, scan the whole page text instead.
            if (!foundInSpan) {
                String textOnly = stripHtml(content);
                Matcher m = COMMAND_PATTERN.matcher(textOnly);
                while (m.find()) {
                    ExampleCase ec = parseCommandMatch(m, manager);
                    if (ec == null) continue;
                    registerExampleCase(result, ec, relativeName);
                }
            }
        }

        return result;
    }

    private void registerExampleCase(ScanData result, ExampleCase ec, String relativeName) {
        String key = ec.dedupKey();
        ExampleCase existing = result.casesByKey.get(key);
        if (existing == null) {
            ec.sourceDocFiles.add(relativeName);
            result.casesByKey.put(key, ec);
        } else {
            existing.sourceDocFiles.add(relativeName);
        }
    }

    /**
     * Splits a string into tokens on whitespace, but preserves quoted
     * substrings (single or double quotes) as single tokens, e.g.
     * {@code 0.5 " " true} yields {@code ["0.5", "\" \"", "true"]}. Quotes
     * are kept on the token so downstream code can detect and strip them.
     */
    private List<String> tokenizeRespectingQuotes(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isEmpty()) return tokens;

        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        char quoteChar = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (inQuote) {
                current.append(c);
                if (c == quoteChar) {
                    inQuote = false;
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else if (c == '"' || c == '\'') {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                inQuote = true;
                quoteChar = c;
                current.append(c);
            } else if (Character.isWhitespace(c)) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            tokens.add(current.toString());
        }

        return tokens;
    }

    /**
     * Parses a single {@link #COMMAND_PATTERN} match into an
     * {@link ExampleCase}. Uses the algorithm's declared input/output file
     * requirements (via {@link AlgorithmManager}) to determine how many
     * leading tokens are file-name slots, and the {@link #NOINPUT_TOKEN} /
     * {@link #NOOUTPUT_TOKEN} placeholders for algorithms documented with no
     * input or no output file.
     * <p>
     * Parameters are then extracted using the mandatory/optional split
     * described in the class-level Javadoc. If the descriptor is unknown
     * ({@code desc == null}) no parameters are extracted, since there is no
     * reliable bound to extract them safely.
     *
     * @return the parsed example, or {@code null} if the match should be
     *         ignored (e.g. algorithm name is empty or literally "jar", a
     *         false positive of the regex).
     */
    private ExampleCase parseCommandMatch(Matcher m, AlgorithmManager manager) {
        String algo = cleanToken(m.group(1));
        if (algo.isEmpty() || algo.equalsIgnoreCase("jar")) return null;

        String secondToken = m.group(2) == null ? "" : m.group(2);
        String tailRaw     = m.group(3) == null ? "" : m.group(3).trim();

        List<String> tokens = new ArrayList<>();
        tokens.add(secondToken);
        if (!tailRaw.isEmpty()) {
            for (String t : tokenizeRespectingQuotes(tailRaw)) {
                if (!t.isEmpty()) tokens.add(t);
            }
        }

        DescriptionOfAlgorithm desc = (manager == null)
                ? null : manager.getDescriptionOfAlgorithm(algo);
        boolean needsInput  = (desc == null)
                || (desc.getInputFileTypes()  != null && desc.getInputFileTypes().length  > 0);
        boolean needsOutput = (desc == null)
                || (desc.getOutputFileTypes() != null && desc.getOutputFileTypes().length > 0);

        int    idx           = 0;
        String inputFileName = null;

        // NOINPUT always occupies the input slot, regardless of the descriptor,
        // since it is purely a documentation convention.
        if (idx < tokens.size()
                && tokens.get(idx).trim().equalsIgnoreCase(NOINPUT_TOKEN)) {
            idx++;
        } else if (needsInput && idx < tokens.size()) {
            String raw = cleanToken(tokens.get(idx));
            if (!raw.isEmpty()) inputFileName = extractBaseName(raw);
            idx++;
        }

        String outputFileNameInDoc = null;
        // no_output_file always occupies the output slot, regardless of the
        // descriptor, since it is purely a documentation convention.
        if (idx < tokens.size()
                && tokens.get(idx).trim().equalsIgnoreCase(NOOUTPUT_TOKEN)) {
            outputFileNameInDoc = NOOUTPUT_TOKEN;
            idx++;
        } else if (needsOutput && idx < tokens.size()) {
            String raw = cleanToken(tokens.get(idx));
            if (!raw.isEmpty()) outputFileNameInDoc = extractBaseName(raw);
            idx++;
        }

        // Guard: if we couldn't even identify file-name slots, bail out.
        if (inputFileName == null && outputFileNameInDoc == null && idx == 0) return null;

        // A real file name should look like one, not a stray parameter value;
        // guards against mis-detection when the descriptor is unknown.
        if (inputFileName != null && !looksLikeFileName(inputFileName)) return null;

        List<String> paramCandidates = (idx < tokens.size())
                ? tokens.subList(idx, tokens.size())
                : Collections.emptyList();

        List<String> extractedParams = new ArrayList<>();

        // Without a known descriptor there is no reliable structural bound
        // (no mandatory count, no total count), so no parameters are extracted.
        if (desc != null) {
            int mandatoryParams = desc.getNumberOfMandatoryParameters();
            DescriptionOfParameter[] paramsDescArray = desc.getParametersDescription();
            int totalParams = (paramsDescArray != null) ? paramsDescArray.length : 0;

            for (String raw : paramCandidates) {
                // Never extract more parameter tokens than the algorithm declares in total.
                if (extractedParams.size() >= totalParams) break;

                String cleaned = cleanParamToken(raw);
                if (cleaned.isEmpty()) break;

                boolean inMandatoryZone = extractedParams.size() < mandatoryParams;
                if (inMandatoryZone) {
                    // A runnable documented example must supply a value for every
                    // mandatory parameter, so the token at this position IS the
                    // value, trusted purely by position (no content validation).
                    extractedParams.add(cleaned);
                    continue;
                }

                // Optional zone: no guarantee this token is actually a parameter
                // (the example may simply omit optional parameters and continue
                // with prose), so only accept an unambiguous parameter shape.
                String rawTrimmed = (raw == null) ? "" : raw.trim();
                boolean quoted = isQuoteWrapped(rawTrimmed);
                boolean matchesStandardToken = PARAM_TOKEN_PATTERN.matcher(cleaned).matches();

                if (!quoted && !matchesStandardToken) break;

                extractedParams.add(cleaned);
            }
        }

        return new ExampleCase(algo, inputFileName, outputFileNameInDoc, extractedParams);
    }

    /**
     * @return {@code true} if the raw (trimmed) token is wrapped in a
     *         matching pair of single or double quotes — treated as an
     *         unambiguous signal that the token is a literal string
     *         parameter value.
     */
    private boolean isQuoteWrapped(String rawTrimmed) {
        if (rawTrimmed == null || rawTrimmed.length() < 2) return false;
        char first = rawTrimmed.charAt(0);
        char last  = rawTrimmed.charAt(rawTrimmed.length() - 1);
        return (first == '"' && last == '"') || (first == '\'' && last == '\'');
    }

    /**
     * Loose heuristic: a plausible file-name token contains a letter, digit,
     * dot, underscore, or hyphen (rules out pure punctuation left over from
     * HTML cleanup).
     */
    private boolean looksLikeFileName(String token) {
        if (token == null) return false;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-') return true;
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Token cleaning
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Cleans a token being considered as a <b>parameter value</b>.
     * <p>
     * A token wrapped in matching quotes has its quotes stripped but its
     * inner content returned as-is (even if it is just a space), to support
     * parameters like {@code " "}. Unquoted single-character tokens are
     * returned unchanged (preserves punctuation separators like {@code ,}).
     * Longer unquoted tokens go through {@link #cleanToken}.
     */
    private String cleanParamToken(String token) {
        if (token == null) return "";
        String t = token.trim();
        if (t.isEmpty()) return "";

        if (t.length() >= 2) {
            char first = t.charAt(0);
            char last  = t.charAt(t.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return t.substring(1, t.length() - 1);
            }
        }

        if (t.length() <= 1) return t;
        return cleanToken(t);
    }

    /**
     * Strips characters that are clearly not part of the actual token but
     * were picked up because it sits at the end of a sentence or is wrapped
     * in quotes/brackets in the surrounding text (e.g. a trailing "." after
     * "output.txt." in a sentence, or a wrapping quote/paren). Used for the
     * algorithm name and file names; for parameter values use
     * {@link #cleanParamToken(String)} instead.
     */
    private String cleanToken(String token) {
        if (token == null) return "";
        String t = token.trim();

        // Strip a single layer of wrapping quotes.
        if (t.length() >= 2) {
            char first = t.charAt(0);
            char last  = t.charAt(t.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                t = t.substring(1, t.length() - 1);
            }
        }

        // Strip trailing sentence punctuation (cannot be part of a file name).
        while (!t.isEmpty() && ",.;:!?".indexOf(t.charAt(t.length() - 1)) >= 0) {
            t = t.substring(0, t.length() - 1);
        }

        // Strip an unmatched trailing closing bracket / paren.
        if (!t.isEmpty()) {
            char last = t.charAt(t.length() - 1);
            if ((last == ')' && t.indexOf('(') < 0)
                    || (last == ']' && t.indexOf('[') < 0)
                    || (last == '}' && t.indexOf('{') < 0)) {
                t = t.substring(0, t.length() - 1);
            }
        }

        return t.trim();
    }

    private String extractBaseName(String token) {
        if (token == null) return "";
        String n   = token.replace('\\', '/');
        int    idx = n.lastIndexOf('/');
        return (idx >= 0) ? n.substring(idx + 1) : n;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HTML stripping
    // ─────────────────────────────────────────────────────────────────────────

    private String readFileSafely(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            try {
                return new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
            } catch (IOException ex) {
                return "";
            }
        }
    }

    private String stripHtml(String html) {
        if (html == null || html.isEmpty()) return "";
        String text = html;
        text = text.replaceAll("(?s)<!--.*?-->", " ");
        text = text.replaceAll("(?is)<br\\s*/?>", " ");
        text = text.replaceAll(
                "(?is)</?(p|div|li|ul|ol|table|tr|td|th|h[1-6]|blockquote|"
                + "section|article|header|footer|hr|pre)\\b[^>]*>", " ");
        text = text.replaceAll("(?s)<[^>]+>", "");
        text = decodeHtmlEntities(text);
        text = UNICODE_DASHES.matcher(text).replaceAll("-");
        text = text.replaceAll("[\\s\\u00A0]+", " ").trim();
        return text;
    }

    private String decodeHtmlEntities(String text) {
        String decoded = text
                .replace("&nbsp;",  " ").replace("&#160;",  " ")
                .replace("&amp;",   "&").replace("&lt;",    "<")
                .replace("&gt;",    ">").replace("&quot;",  "\"")
                .replace("&#39;",   "'").replace("&apos;",  "'")
                .replace("&rsquo;", "'").replace("&lsquo;", "'")
                .replace("&rdquo;","\"").replace("&ldquo;","\"")
                .replace("&#8217;", "'").replace("&#8220;","\"")
                .replace("&#8221;","\"");
        return decodeNumericEntities(decoded);
    }

    private String decodeNumericEntities(String text) {
        Matcher m  = NUMERIC_ENTITY.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            try {
                boolean isHex     = "x".equalsIgnoreCase(m.group(1));
                int     codePoint = Integer.parseInt(m.group(2), isHex ? 16 : 10);
                m.appendReplacement(sb,
                        Matcher.quoteReplacement(new String(Character.toChars(codePoint))));
            } catch (Exception ex) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group()));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test files index
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Walks the test-files folder once and builds a case-insensitive map
     * from lower-cased file name to absolute path (first match wins).
     */
    private Map<String, String> buildTestFileIndex(Path testFolder) throws IOException {
        Map<String, String> index = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        try (Stream<Path> stream = Files.walk(testFolder)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .collect(Collectors.toList());
            for (Path p : files) {
                String name = p.getFileName().toString().toLowerCase();
                index.putIfAbsent(name, p.toAbsolutePath().toString());
            }
        }
        return index;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AlgorithmManager helper
    // ─────────────────────────────────────────────────────────────────────────

    private AlgorithmManager getAlgorithmManagerInstance() {
        if (algorithmManager == null) {
            try {
                algorithmManager = AlgorithmManager.getInstance();
            } catch (Exception e) {
                System.err.println("Could not initialise AlgorithmManager: " + e.getMessage());
            }
        }
        return algorithmManager;
    }

    private String stackTraceToString(Throwable t) {
        if (t == null) return "";
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // General utilities
    // ─────────────────────────────────────────────────────────────────────────

    private void copyToClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard()
               .setContents(new StringSelection(text == null ? "" : text), null);
        lblStatus.setText("Copied to clipboard.");
    }

    private void saveTextToFile(String text, String defaultFileName) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save File");
        chooser.setSelectedFile(new File(defaultFileName));
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        File target = chooser.getSelectedFile();
        try {
            Files.write(target.toPath(), (text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            lblStatus.setText("Saved to: " + target.getAbsolutePath());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame,
                    "Could not save file:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Standalone launcher
    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        SwingUtilities.invokeLater(
                () -> new AlgorithmExampleAutoRunnerWindow().createAndShowGUI());
    }
}