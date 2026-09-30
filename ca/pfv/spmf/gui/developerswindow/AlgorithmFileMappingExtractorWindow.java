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
 */

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import ca.pfv.spmf.algorithmmanager.AlgorithmManager;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;

/**
 * Single-click tool that scans the SPMF documentation HTML files, extracts every
 * {@code spmf.jar run <algo> <file>} example, cross-references AlgorithmManager
 * for input-file types / DescriptionOfAlgorithm class names, checks coverage
 * against a physical test-files folder, and shows EVERYTHING (including
 * algorithms with no doc example, files with no algorithm, and file-type
 * conflicts) fully merged into two JTables plus one consolidated text report.
 *
 * There is a single action button: "Scan & Analyse".
 *
 * @author Philippe Fournier-Viger
 */
public class AlgorithmFileMappingExtractorWindow {

    // ── frame ──────────────────────────────────────────────────────────────
    private JFrame frame;

    // ── inputs ─────────────────────────────────────────────────────────────
    private JTextField txtDocFolder;
    private JTextField txtTestFolder;

    // ── single action ──────────────────────────────────────────────────────
    private JButton btnScanAndAnalyse;
    private JButton btnClear;

    // ── progress / status ──────────────────────────────────────────────────
    private JProgressBar progressBar;
    private JLabel       lblStatus;

    // ── summary strip ──────────────────────────────────────────────────────
    private JLabel lblHtmlFiles;
    private JLabel lblDistinctAlgos;
    private JLabel lblDistinctFiles;
    private JLabel lblTotalPairs;
    private JLabel lblMissingAlgos;
    private JLabel lblUnusedTestFiles;
    private JLabel lblMissingFromFolder;
    private JLabel lblTypeConflicts;

    // ── Table 1 : Algorithm -> Input File (fully merged) ─────────────────────
    // Columns: Algorithm | Input File | Input File Type | Description Class |
    //          Occurrences | In Test Folder? | Status | Doc Page(s)
    private JTable            tableAlgoToFile;
    private DefaultTableModel modelAlgoToFile;
    private TableRowSorter<DefaultTableModel> sorterAlgoToFile;
    private JTextField        txtFilterAlgo;

    // ── Table 2 : Input File -> Algorithms (fully merged) ─────────────────────
    // Columns: Input File | Algorithms | Description Classes | # Algorithms |
    //          Input File Type(s) | Type Conflict? | In Test Folder? | Doc Page(s) / Notes
    private JTable            tableFileToAlgos;
    private DefaultTableModel modelFileToAlgos;
    private TableRowSorter<DefaultTableModel> sorterFileToAlgos;
    private JTextField        txtFilterFile;

    // ── consolidated plain-text report (built FROM the same rows as the tables) ─
    private JTextArea txtReport;

    // ── cached result (kept for double-click detail popups) ──────────────────
    private ScanResult lastResult;

    // ── caches ─────────────────────────────────────────────────────────────
    private final Map<String, String> inputFileTypeCache = new ConcurrentHashMap<>();
    private final Map<String, String> descClassNameCache = new ConcurrentHashMap<>();
    private AlgorithmManager algorithmManager;

    // ── constants ──────────────────────────────────────────────────────────
    private static final String UNKNOWN       = "N/A";
    private static final String NOINPUT_TOKEN = "NOINPUT";

    private static final String DEFAULT_DOC_FOLDER  = "D:\\workspace\\spmf\\doc";
    private static final String DEFAULT_TEST_FOLDER =
            "C:\\Users\\Phil\\Desktop\\SPMF2026\\test_files\\";

    private static final Pattern COMMAND_PATTERN = Pattern.compile(
            "spmf\\.jar\\s+run\\s+(\\S+)\\s+(\\S+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern UNICODE_DASHES = Pattern.compile(
            "[\\u2010\\u2011\\u2012\\u2013\\u2014\\u2015\\u2212]");

    private static final Pattern NUMERIC_ENTITY =
            Pattern.compile("&#(x?)([0-9a-fA-F]+);");

    // Row-highlight colours (used consistently in both tables).
    private static final Color COLOR_PROBLEM_STRONG = new Color(255, 210, 210); // e.g. missing / unused
    private static final Color COLOR_PROBLEM_MEDIUM = new Color(255, 240, 190); // e.g. type conflict
    private static final Color COLOR_PROBLEM_MILD   = new Color(255, 250, 220); // e.g. not in test folder
    private static final Color COLOR_OK             = Color.WHITE;

    // =========================================================================
    // GUI construction
    // =========================================================================

    public void createAndShowGUI() {
        frame = new JFrame(
                "SPMF - Algorithm / Input-File Extractor & Coverage Analyser");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(1300, 860);
        frame.setMinimumSize(new Dimension(950, 620));
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));

        root.add(buildTopPanel(),    BorderLayout.NORTH);
        root.add(buildCentrePanel(), BorderLayout.CENTER);
        root.add(buildStatusPanel(), BorderLayout.SOUTH);

        frame.setContentPane(root);
        frame.setVisible(true);
    }

    // ── top panel : folders + single button + summary strip ──────────────────

    private JPanel buildTopPanel() {
        JPanel outer = new JPanel(new BorderLayout(6, 6));

        JPanel folders = new JPanel(new GridLayout(2, 1, 4, 4));

        JPanel docRow = new JPanel(new BorderLayout(6, 0));
        docRow.setBorder(BorderFactory.createTitledBorder("Documentation Folder"));
        txtDocFolder = new JTextField(DEFAULT_DOC_FOLDER);
        JButton btnBrowseDoc = new JButton("Browse…");
        btnBrowseDoc.addActionListener(e -> browseFolder(txtDocFolder,
                "Select the SPMF documentation folder"));
        docRow.add(txtDocFolder, BorderLayout.CENTER);
        docRow.add(btnBrowseDoc, BorderLayout.EAST);

        JPanel testRow = new JPanel(new BorderLayout(6, 0));
        testRow.setBorder(BorderFactory.createTitledBorder(
                "Test Files Folder (optional - enables coverage columns)"));
        txtTestFolder = new JTextField(DEFAULT_TEST_FOLDER);
        JButton btnBrowseTest = new JButton("Browse…");
        btnBrowseTest.addActionListener(e -> browseFolder(txtTestFolder,
                "Select the test files folder"));
        testRow.add(txtTestFolder, BorderLayout.CENTER);
        testRow.add(btnBrowseTest, BorderLayout.EAST);

        folders.add(docRow);
        folders.add(testRow);

        btnScanAndAnalyse = new JButton("▶  Scan & Analyse");
        btnScanAndAnalyse.setFont(btnScanAndAnalyse.getFont().deriveFont(Font.BOLD, 13f));
        btnScanAndAnalyse.setToolTipText(
                "Scans the documentation, cross-references AlgorithmManager, checks test-file "
                + "coverage, and merges ALL findings into the two tables below - in one step.");
        btnScanAndAnalyse.addActionListener(e -> startScanAndAnalyse());

        btnClear = new JButton("Clear");
        btnClear.addActionListener(e -> clearAll());

        JPanel btnCol = new JPanel(new GridLayout(2, 1, 4, 6));
        btnCol.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 0));
        btnCol.add(btnScanAndAnalyse);
        btnCol.add(btnClear);

        outer.add(folders, BorderLayout.CENTER);
        outer.add(btnCol,  BorderLayout.EAST);
        outer.add(buildSummaryStrip(), BorderLayout.SOUTH);

        return outer;
    }

    private JPanel buildSummaryStrip() {
        JPanel strip = new JPanel(new GridLayout(2, 4, 8, 2));
        strip.setBorder(BorderFactory.createTitledBorder(
                "Summary  (all details are in the two tables below - no hidden pop-ups)"));

        lblHtmlFiles         = summaryLabel("HTML files scanned: –");
        lblDistinctAlgos     = summaryLabel("Distinct algorithms: –");
        lblDistinctFiles     = summaryLabel("Distinct input files: –");
        lblTotalPairs        = summaryLabel("Algorithm/file pairs: –");
        lblMissingAlgos      = summaryLabel("Algorithms w/o doc example: –");
        lblUnusedTestFiles   = summaryLabel("Test files w/o algorithm: –");
        lblMissingFromFolder = summaryLabel("Doc files missing from folder: –");
        lblTypeConflicts     = summaryLabel("Files with type conflicts: –");

        strip.add(lblHtmlFiles);
        strip.add(lblDistinctAlgos);
        strip.add(lblDistinctFiles);
        strip.add(lblTotalPairs);
        strip.add(lblMissingAlgos);
        strip.add(lblUnusedTestFiles);
        strip.add(lblMissingFromFolder);
        strip.add(lblTypeConflicts);

        return strip;
    }

    private JLabel summaryLabel(String text) {
        JLabel l = new JLabel(text, JLabel.CENTER);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        return l;
    }

    // ── centre panel : both tables ALWAYS visible + report always visible ────
    // (no tabs, no dialogs - everything on one screen)

    private JSplitPane buildCentrePanel() {

        JPanel table1Panel = buildAlgoToFileTablePanel();
        JPanel table2Panel = buildFileToAlgosTablePanel();

        JSplitPane tablesSplit = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, table1Panel, table2Panel);
        tablesSplit.setResizeWeight(0.5);
        tablesSplit.setOneTouchExpandable(true);
        tablesSplit.setContinuousLayout(true);

        JPanel reportPanel = buildReportPanel();

        JSplitPane mainSplit = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT, tablesSplit, reportPanel);
        mainSplit.setResizeWeight(0.72);
        mainSplit.setOneTouchExpandable(true);
        mainSplit.setContinuousLayout(true);

        return mainSplit;
    }

    // Table 1 ─────────────────────────────────────────────────────────────────
    private JPanel buildAlgoToFileTablePanel() {
        modelAlgoToFile = new DefaultTableModel(new Object[]{
                "Algorithm",         // 0
                "Input File",        // 1
                "Input File Type",   // 2
                "Description Class", // 3
                "Occurrences",       // 4
                "In Test Folder?",   // 5
                "Status",            // 6
                "Doc Page(s)"        // 7
        }, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) {
                return c == 4 ? Integer.class : String.class;
            }
        };

        tableAlgoToFile  = new JTable(modelAlgoToFile);
        sorterAlgoToFile = new TableRowSorter<>(modelAlgoToFile);
        tableAlgoToFile.setRowSorter(sorterAlgoToFile);
        tableAlgoToFile.setRowHeight(22);
        tableAlgoToFile.getColumnModel().getColumn(0).setPreferredWidth(190);
        tableAlgoToFile.getColumnModel().getColumn(1).setPreferredWidth(150);
        tableAlgoToFile.getColumnModel().getColumn(2).setPreferredWidth(120);
        tableAlgoToFile.getColumnModel().getColumn(3).setPreferredWidth(190);
        tableAlgoToFile.getColumnModel().getColumn(4).setPreferredWidth(70);
        tableAlgoToFile.getColumnModel().getColumn(5).setPreferredWidth(95);
        tableAlgoToFile.getColumnModel().getColumn(6).setPreferredWidth(140);
        tableAlgoToFile.getColumnModel().getColumn(7).setPreferredWidth(230);

        RowHighlightRenderer renderer1 = new RowHighlightRenderer(this::colourForAlgoRow);
        tableAlgoToFile.setDefaultRenderer(Object.class, renderer1);
        tableAlgoToFile.setDefaultRenderer(Integer.class, renderer1);

        tableAlgoToFile.setToolTipText(
                "Includes algorithms with NO documentation example (Status = MISSING FROM DOCS). "
                + "Double-click a row for the full list of documentation pages.");
        tableAlgoToFile.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) showDocPagesForAlgoRow();
            }
        });

        return wrapTableInPanel(tableAlgoToFile, sorterAlgoToFile,
                f -> txtFilterAlgo = f,
                "Algorithm -> Input File  (every algorithm, including those with NO example - see Status column)");
    }

    // Table 2 ─────────────────────────────────────────────────────────────────
    private JPanel buildFileToAlgosTablePanel() {
        modelFileToAlgos = new DefaultTableModel(new Object[]{
                "Input File",           // 0
                "Algorithms",           // 1
                "Description Classes",  // 2
                "# Algorithms",         // 3
                "Input File Type(s)",   // 4
                "Type Conflict?",       // 5
                "In Test Folder?",      // 6
                "Doc Page(s) / Notes"   // 7
        }, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) {
                return c == 3 ? Integer.class : String.class;
            }
        };

        tableFileToAlgos  = new JTable(modelFileToAlgos);
        sorterFileToAlgos = new TableRowSorter<>(modelFileToAlgos);
        tableFileToAlgos.setRowSorter(sorterFileToAlgos);
        tableFileToAlgos.setRowHeight(22);
        tableFileToAlgos.getColumnModel().getColumn(0).setPreferredWidth(150);
        tableFileToAlgos.getColumnModel().getColumn(1).setPreferredWidth(220);
        tableFileToAlgos.getColumnModel().getColumn(2).setPreferredWidth(220);
        tableFileToAlgos.getColumnModel().getColumn(3).setPreferredWidth(75);
        tableFileToAlgos.getColumnModel().getColumn(4).setPreferredWidth(120);
        tableFileToAlgos.getColumnModel().getColumn(5).setPreferredWidth(90);
        tableFileToAlgos.getColumnModel().getColumn(6).setPreferredWidth(95);
        tableFileToAlgos.getColumnModel().getColumn(7).setPreferredWidth(230);

        RowHighlightRenderer renderer2 = new RowHighlightRenderer(this::colourForFileRow);
        tableFileToAlgos.setDefaultRenderer(Object.class, renderer2);
        tableFileToAlgos.setDefaultRenderer(Integer.class, renderer2);

        tableFileToAlgos.setToolTipText(
                "Includes files present in the test folder but used by NO algorithm "
                + "(# Algorithms = 0). Double-click a row for full details.");
        tableFileToAlgos.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) showDetailsForFileRow();
            }
        });

        return wrapTableInPanel(tableFileToAlgos, sorterFileToAlgos,
                f -> txtFilterFile = f,
                "Input File -> Algorithms  (includes test-folder files used by NO algorithm - see # Algorithms column)");
    }

    // Report panel ────────────────────────────────────────────────────────────
    private JPanel buildReportPanel() {
        txtReport = new JTextArea();
        txtReport.setEditable(false);
        txtReport.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));

        JButton btnCopy = new JButton("Copy Report to Clipboard");
        btnCopy.addActionListener(e -> copyToClipboard(txtReport.getText()));

        JButton btnSave = new JButton("Save Report to File…");
        btnSave.addActionListener(e ->
                saveTextToFile(txtReport.getText(), "spmf_analysis_report.txt"));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        btnRow.add(btnCopy);
        btnRow.add(btnSave);

        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder(
                "Consolidated Plain-text Report (generated from the exact same rows as the two tables above)"));
        panel.add(new JScrollPane(txtReport), BorderLayout.CENTER);
        panel.add(btnRow, BorderLayout.SOUTH);
        return panel;
    }

    // ── status bar ─────────────────────────────────────────────────────────

    private JPanel buildStatusPanel() {
        lblStatus = new JLabel(
                "Ready - fill in the folder path(s) and click \"Scan & Analyse\".");
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);

        JPanel p = new JPanel(new BorderLayout(6, 0));
        p.add(lblStatus,   BorderLayout.CENTER);
        p.add(progressBar, BorderLayout.EAST);
        return p;
    }

    // ── shared table-wrapping helper (filter box + scroll pane) ──────────────

    private JPanel wrapTableInPanel(JTable table,
            TableRowSorter<DefaultTableModel> sorter,
            java.util.function.Consumer<JTextField> fieldSetter,
            String title) {

        JTextField txtFilter = new JTextField();
        fieldSetter.accept(txtFilter);
        txtFilter.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate (javax.swing.event.DocumentEvent e) { filter(); }
            @Override public void removeUpdate (javax.swing.event.DocumentEvent e) { filter(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { filter(); }
            private void filter() {
                String t = txtFilter.getText().trim();
                sorter.setRowFilter(t.isEmpty() ? null : safeRegexFilter(t));
            }
        });

        JPanel filterRow = new JPanel(new BorderLayout(4, 0));
        filterRow.add(new JLabel("Filter: "), BorderLayout.WEST);
        filterRow.add(txtFilter, BorderLayout.CENTER);

        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(filterRow, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private RowFilter<DefaultTableModel, Object> safeRegexFilter(String text) {
        try {
            return RowFilter.regexFilter("(?i)" + Pattern.quote(text));
        } catch (Exception ex) {
            return null;
        }
    }

    // ── row-colouring logic (reads MODEL values, so sorting/filtering is safe) ─

    /** Returns the background colour for a row of Table 1, or null for default. */
    private Color colourForAlgoRow(int modelRow) {
        if (modelRow < 0 || modelRow >= modelAlgoToFile.getRowCount()) return null;
        String status      = (String) modelAlgoToFile.getValueAt(modelRow, 6);
        String inTestFolder = (String) modelAlgoToFile.getValueAt(modelRow, 5);
        if ("MISSING FROM DOCS".equals(status)) return COLOR_PROBLEM_STRONG;
        if ("No".equals(inTestFolder))          return COLOR_PROBLEM_MILD;
        return COLOR_OK;
    }

    /** Returns the background colour for a row of Table 2, or null for default. */
    private Color colourForFileRow(int modelRow) {
        if (modelRow < 0 || modelRow >= modelFileToAlgos.getRowCount()) return null;
        Object countObj = modelFileToAlgos.getValueAt(modelRow, 3);
        int count = (countObj instanceof Integer) ? (Integer) countObj : -1;
        String conflict     = (String) modelFileToAlgos.getValueAt(modelRow, 5);
        String inTestFolder = (String) modelFileToAlgos.getValueAt(modelRow, 6);
        if (count == 0)                return COLOR_PROBLEM_STRONG; // unused test file
        if ("Yes".equals(conflict))    return COLOR_PROBLEM_MEDIUM; // file-type conflict
        if ("No".equals(inTestFolder)) return COLOR_PROBLEM_MILD;   // referenced but absent from folder
        return COLOR_OK;
    }

    /** Generic renderer that delegates colour decisions to a model-row function. */
    private static class RowHighlightRenderer extends DefaultTableCellRenderer {
        private final java.util.function.IntFunction<Color> colourFn;
        RowHighlightRenderer(java.util.function.IntFunction<Color> colourFn) {
            this.colourFn = colourFn;
        }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (!isSelected) {
                int modelRow = table.convertRowIndexToModel(row);
                Color c = colourFn.apply(modelRow);
                setBackground(c != null ? c : Color.WHITE);
            }
            return this;
        }
    }

    // ── double-click detail popups (lightweight, on-demand only) ─────────────

    private void showDocPagesForAlgoRow() {
        int view = tableAlgoToFile.getSelectedRow();
        if (view < 0 || lastResult == null) return;
        int model = tableAlgoToFile.convertRowIndexToModel(view);
        String algo = (String) modelAlgoToFile.getValueAt(model, 0);
        String file = (String) modelAlgoToFile.getValueAt(model, 1);
        String status = (String) modelAlgoToFile.getValueAt(model, 6);

        String msg;
        if ("MISSING FROM DOCS".equals(status)) {
            msg = "This algorithm has no documentation example at all.";
        } else {
            TreeSet<String> pages =
                    lastResult.pairToSourceFiles.get(new AlgoFilePair(algo, file));
            msg = (pages == null || pages.isEmpty())
                    ? "No source information available."
                    : String.join("\n", pages);
        }
        JOptionPane.showMessageDialog(frame, msg,
                "Details for: " + algo + " / " + file, JOptionPane.INFORMATION_MESSAGE);
    }

    private void showDetailsForFileRow() {
        int view = tableFileToAlgos.getSelectedRow();
        if (view < 0) return;
        int model = tableFileToAlgos.convertRowIndexToModel(view);
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < modelFileToAlgos.getColumnCount(); c++) {
            sb.append(modelFileToAlgos.getColumnName(c)).append(": ")
              .append(modelFileToAlgos.getValueAt(model, c)).append(System.lineSeparator());
        }
        JOptionPane.showMessageDialog(frame, sb.toString(),
                "Details for: " + modelFileToAlgos.getValueAt(model, 0),
                JOptionPane.INFORMATION_MESSAGE);
    }

    // ── folder browser ─────────────────────────────────────────────────────

    private void browseFolder(JTextField target, String title) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle(title);
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setAcceptAllFileFilterUsed(false);
        File cur = new File(target.getText().trim());
        if (cur.exists()) fc.setCurrentDirectory(cur);
        if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION)
            target.setText(fc.getSelectedFile().getAbsolutePath());
    }

    // =========================================================================
    // Single-action entry point
    // =========================================================================

    private void startScanAndAnalyse() {
        String docPath  = txtDocFolder.getText().trim();
        String testPath = txtTestFolder.getText().trim();

        File docDir = new File(docPath);
        if (docPath.isEmpty() || !docDir.exists() || !docDir.isDirectory()) {
            JOptionPane.showMessageDialog(frame,
                    "Please specify a valid documentation folder.",
                    "Invalid documentation folder", JOptionPane.WARNING_MESSAGE);
            return;
        }

        File testDir = new File(testPath);
        boolean doTestCoverage = !testPath.isEmpty()
                && testDir.exists() && testDir.isDirectory();

        setUiBusy(true);
        new AnalysisWorker(docDir.toPath(),
                doTestCoverage ? testDir.toPath() : null).execute();
    }

    private void setUiBusy(boolean busy) {
        btnScanAndAnalyse.setEnabled(!busy);
        btnClear.setEnabled(!busy);
        progressBar.setVisible(busy);
        if (busy) {
            progressBar.setIndeterminate(false);
            progressBar.setValue(0);
            progressBar.setString("Starting…");
        }
    }

    // =========================================================================
    // Background worker
    // =========================================================================

    private class AnalysisWorker extends SwingWorker<AnalysisResult, Integer> {

        private final Path docDir;
        private final Path testDir; // may be null
        private volatile int totalHtml = 0;

        AnalysisWorker(Path docDir, Path testDir) {
            this.docDir  = docDir;
            this.testDir = testDir;
        }

        @Override
        protected AnalysisResult doInBackground() throws Exception {
            ScanResult scan = AlgorithmFileMappingExtractorWindow.this.scanDirectory(docDir, this::publish, total -> totalHtml = total);
            List<DescriptionOfAlgorithm> missingAlgos = findMissingAlgorithms(scan);
            TestCoverageResult coverage =
                    (testDir != null) ? buildTestCoverage(scan, testDir) : null;
            return new AnalysisResult(scan, missingAlgos, coverage, testDir != null);
        }

        @Override
        protected void process(List<Integer> chunks) {
            if (totalHtml > 0 && !chunks.isEmpty()) {
                int done = chunks.get(chunks.size() - 1);
                progressBar.setMaximum(totalHtml);
                progressBar.setValue(done);
                progressBar.setString(done + " / " + totalHtml + " HTML files scanned");
            }
        }

        @Override
        protected void done() {
            try {
                AnalysisResult ar = get();
                lastResult = ar.scan;
                displayAll(ar);
                lblStatus.setText("Analysis complete - all findings are merged into the two tables.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame,
                        "Error during analysis:\n" + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                lblStatus.setText("Analysis failed.");
            } finally {
                setUiBusy(false);
            }
        }
    }

    // =========================================================================
    // Data model
    // =========================================================================

    private static class AlgoFilePair implements Comparable<AlgoFilePair> {
        final String algorithm;
        final String file;

        AlgoFilePair(String a, String f) { algorithm = a; file = f; }

        @Override public boolean equals(Object o) {
            if (!(o instanceof AlgoFilePair)) return false;
            AlgoFilePair x = (AlgoFilePair) o;
            return algorithm.equalsIgnoreCase(x.algorithm) && file.equalsIgnoreCase(x.file);
        }
        @Override public int hashCode() {
            return (algorithm.toLowerCase() + "|" + file.toLowerCase()).hashCode();
        }
        @Override public int compareTo(AlgoFilePair o) {
            int c = algorithm.compareToIgnoreCase(o.algorithm);
            return c != 0 ? c : file.compareToIgnoreCase(o.file);
        }
    }

    private static class ScanResult {
        final Set<AlgoFilePair>                  pairs             = new TreeSet<>();
        final Map<AlgoFilePair, Integer>         pairOccurrences   = new TreeMap<>();
        final Map<AlgoFilePair, TreeSet<String>> pairToSourceFiles = new TreeMap<>();
        final Map<String, TreeSet<String>>       fileToAlgos       =
                new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        final Map<String, TreeSet<String>>       fileToSourceFiles =
                new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        final TreeSet<String>                    distinctAlgorithms =
                new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        final Map<String, String>                algorithmToFileType =
                new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        final Map<String, String>                algorithmToDescClass =
                new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        final Map<String, String>                relFileToText = new TreeMap<>();
        int htmlFileCount = 0;
    }

    private static class TestCoverageResult {
        /** name -> whether present in test folder (case-insensitive). */
        final Set<String> presentInFolder = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        /** name -> relative path within the test folder (for files with no algorithm). */
        final Map<String, String> unusedTestFileToRelPath =
                new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        int totalTestFiles = 0;
    }

    private static class AnalysisResult {
        final ScanResult                     scan;
        final List<DescriptionOfAlgorithm>   missingAlgos;
        final TestCoverageResult             coverage;         // may be null
        final boolean                        testFolderProvided;

        AnalysisResult(ScanResult scan, List<DescriptionOfAlgorithm> missingAlgos,
                TestCoverageResult coverage, boolean testFolderProvided) {
            this.scan               = scan;
            this.missingAlgos       = missingAlgos;
            this.coverage           = coverage;
            this.testFolderProvided = testFolderProvided;
        }
    }

    // =========================================================================
    // Core scan
    // =========================================================================
    /**
     * Matches a token that ends with a "real" file extension: a dot followed by
     * 1-10 alphanumeric characters at the very end of the string. Used to reject
     * stray words (e.g. "What", "in", "the") that the loose \S+ file-token group
     * in COMMAND_PATTERN sometimes captures from surrounding sentence text.
     */
    private static final Pattern FILE_EXTENSION_PATTERN =
            Pattern.compile(".+\\.[A-Za-z0-9]{1,10}$");

    /**
     * Returns true if {@code name} looks like a real file name, i.e. it ends
     * with a dot followed by a short alphanumeric extension (.txt, .dat, .csv,
     * .arff, etc.). Names with no extension at all (plain words) are rejected.
     */
    private boolean hasFileExtension(String name) {
        return name != null && FILE_EXTENSION_PATTERN.matcher(name).matches();
    }
    
    private ScanResult scanDirectory(Path dir,
            IntConsumer progress, IntConsumer totalSetter) throws IOException {

        ScanResult result = new ScanResult();

        List<Path> htmlFiles;
        try (Stream<Path> s = Files.walk(dir)) {
            htmlFiles = s.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.endsWith(".html") || n.endsWith(".htm");
                    })
                    .collect(Collectors.toList());
        }

        result.htmlFileCount = htmlFiles.size();
        totalSetter.accept(htmlFiles.size());

        int done = 0;
        for (Path html : htmlFiles) {
            String content = readFileSafely(html);
            String text    = stripHtml(content);
            String relName = dir.relativize(html).toString();
            result.relFileToText.put(relName, text);

            Matcher m = COMMAND_PATTERN.matcher(text);
            while (m.find()) {
                String algo    = cleanToken(m.group(1));
                String rawFile = cleanToken(m.group(2));
                if (algo.isEmpty() || rawFile.isEmpty() || algo.equalsIgnoreCase("jar")) continue;

                boolean isNoInput = rawFile.equalsIgnoreCase(NOINPUT_TOKEN);
                String file = isNoInput ? NOINPUT_TOKEN : extractBaseName(rawFile);

                // Reject tokens that don't look like real file names (no extension),
                // e.g. stray words such as "What" or "in" picked up from surrounding
                // sentence text by the loose \S+ group in COMMAND_PATTERN.
                if (!isNoInput && !hasFileExtension(file)) {
                    continue;
                }

                AlgoFilePair pair = new AlgoFilePair(algo, file);
                result.pairs.add(pair);
                result.distinctAlgorithms.add(algo);
                result.pairOccurrences.merge(pair, 1, Integer::sum);
                result.pairToSourceFiles
                        .computeIfAbsent(pair, k -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER))
                        .add(relName);
                result.fileToAlgos
                        .computeIfAbsent(file, k -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER))
                        .add(algo);
                result.fileToSourceFiles
                        .computeIfAbsent(file, k -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER))
                        .add(relName);

                if (!result.algorithmToFileType.containsKey(algo))
                    result.algorithmToFileType.put(algo, lookupFileType(algo));
                if (!result.algorithmToDescClass.containsKey(algo))
                    result.algorithmToDescClass.put(algo, lookupDescClassName(algo));
            }

            progress.accept(++done);
        }
        return result;
    }

    // =========================================================================
    // Missing-algorithms detection
    // =========================================================================

    private List<DescriptionOfAlgorithm> findMissingAlgorithms(ScanResult scan) {
        AlgorithmManager mgr = getManager();
        if (mgr == null) return Collections.emptyList();

        List<DescriptionOfAlgorithm> missing = new ArrayList<>();
        for (DescriptionOfAlgorithm d : mgr.getAlgorithms()) {
            String[] types = d.getInputFileTypes();
            if (types == null || types.length == 0) continue; // no input needed -> not "missing"
            if (!scan.distinctAlgorithms.contains(d.getName())) missing.add(d);
        }
        missing.sort((a, b) -> {
            int c = a.getAlgorithmCategory().compareToIgnoreCase(b.getAlgorithmCategory());
            return c != 0 ? c : a.getName().compareToIgnoreCase(b.getName());
        });
        return missing;
    }

    // =========================================================================
    // Test-files coverage (raw data only - row building happens later)
    // =========================================================================

    private TestCoverageResult buildTestCoverage(ScanResult scan, Path testDir) throws IOException {
        TestCoverageResult cov = new TestCoverageResult();

        List<Path> testFiles;
        try (Stream<Path> s = Files.walk(testDir)) {
            testFiles = s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".txt"))
                    .collect(Collectors.toList());
        }
        cov.totalTestFiles = testFiles.size();

        TreeMap<String, String> testNameToRel = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Path p : testFiles) {
            String name = p.getFileName().toString();
            testNameToRel.putIfAbsent(name, testDir.relativize(p).toString());
        }
        cov.presentInFolder.addAll(testNameToRel.keySet());

        // Test files that are NOT referenced by any algorithm in the docs.
        for (Map.Entry<String, String> e : testNameToRel.entrySet()) {
            if (!scan.fileToAlgos.containsKey(e.getKey())) {
                cov.unusedTestFileToRelPath.put(e.getKey(), e.getValue());
            }
        }

        return cov;
    }

    /** Searches the raw (HTML-stripped) documentation text for a literal file name. */
    private TreeSet<String> findRawMentions(String fileName, ScanResult scan) {
        TreeSet<String> pages = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        String lower = fileName.toLowerCase();
        for (Map.Entry<String, String> e : scan.relFileToText.entrySet()) {
            String text = e.getValue();
            if (text != null && text.toLowerCase().contains(lower)) pages.add(e.getKey());
        }
        return pages;
    }

    // =========================================================================
    // Row construction - the SAME row lists are used for the tables AND the report
    // =========================================================================

    /** One row for Table 1 (order matches the column order of modelAlgoToFile). */
    private List<Object[]> buildAlgoToFileRows(AnalysisResult ar, Set<String> inTestFolderSet) {
        ScanResult scan = ar.scan;
        List<Object[]> rows = new ArrayList<>();

        for (AlgoFilePair pair : scan.pairs) {
            int    occ       = scan.pairOccurrences.getOrDefault(pair, 1);
            String fileType  = scan.algorithmToFileType.getOrDefault(pair.algorithm, UNKNOWN);
            String descClass = scan.algorithmToDescClass.getOrDefault(pair.algorithm, UNKNOWN);
            TreeSet<String> pages = scan.pairToSourceFiles.get(pair);
            String docPages  = (pages == null || pages.isEmpty()) ? "" : String.join(", ", pages);

            boolean noInput = NOINPUT_TOKEN.equalsIgnoreCase(pair.file);
            String inFolder = !ar.testFolderProvided ? "N/A (no test folder)"
                    : noInput ? "N/A (no input)"
                    : inTestFolderSet.contains(pair.file) ? "Yes" : "No";
            String status = noInput ? "OK (no input needed)" : "OK";

            rows.add(new Object[]{
                    pair.algorithm, pair.file, fileType, descClass,
                    occ, inFolder, status, docPages
            });
        }

        // Algorithms that require an input file but have NO documentation example at all.
        for (DescriptionOfAlgorithm d : ar.missingAlgos) {
            String types = typesToString(d);
            String descClass = simpleClassOf(d);
            rows.add(new Object[]{
                    d.getName(), "(none found)", types, descClass,
                    0, "N/A", "MISSING FROM DOCS", ""
            });
        }

        return rows;
    }

    /** One row for Table 2 (order matches the column order of modelFileToAlgos). */
    private List<Object[]> buildFileToAlgosRows(AnalysisResult ar, Set<String> inTestFolderSet) {
        ScanResult scan = ar.scan;
        List<Object[]> rows = new ArrayList<>();

        for (Map.Entry<String, TreeSet<String>> e : scan.fileToAlgos.entrySet()) {
            String inputFile = e.getKey();
            TreeSet<String> algos = e.getValue();
            boolean noInput = NOINPUT_TOKEN.equalsIgnoreCase(inputFile);

            TreeSet<String> descClasses = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            TreeSet<String> distinctTypes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (String a : algos) {
                descClasses.add(scan.algorithmToDescClass.getOrDefault(a, UNKNOWN));
                String t = scan.algorithmToFileType.getOrDefault(a, UNKNOWN);
                if (!UNKNOWN.equals(t)) distinctTypes.add(t);
            }

            String typesStr = noInput ? "(not applicable)"
                    : distinctTypes.isEmpty() ? UNKNOWN : String.join(", ", distinctTypes);
            String conflict = (!noInput && distinctTypes.size() >= 2) ? "Yes" : "No";

            TreeSet<String> pages = scan.fileToSourceFiles.get(inputFile);
            String docPages = (pages == null || pages.isEmpty()) ? "" : String.join(", ", pages);

            String inFolder = !ar.testFolderProvided ? "N/A (no test folder)"
                    : noInput ? "N/A (no input)"
                    : inTestFolderSet.contains(inputFile) ? "Yes" : "No";

            rows.add(new Object[]{
                    inputFile, String.join(", ", algos), String.join(", ", descClasses),
                    algos.size(), typesStr, conflict, inFolder, docPages
            });
        }

        // Files that physically exist in the test folder but are used by NO algorithm.
        if (ar.coverage != null) {
            for (Map.Entry<String, String> e : ar.coverage.unusedTestFileToRelPath.entrySet()) {
                String name = e.getKey();
                TreeSet<String> mentions = findRawMentions(name, scan);
                String note = mentions.isEmpty()
                        ? "(not found anywhere in documentation text - path: " + e.getValue() + ")"
                        : "Mentioned in raw text (but not as a run example) in: "
                                + String.join(", ", mentions) + "  [path: " + e.getValue() + "]";

                rows.add(new Object[]{
                        name, "(none - not used by any algorithm)", "", 0,
                        "", "No", "Yes", note
                });
            }
        }

        return rows;
    }

    private String typesToString(DescriptionOfAlgorithm d) {
        String[] t = d.getInputFileTypes();
        if (t == null || t.length == 0) return UNKNOWN;
        return String.join(", ", t);
    }

    private String simpleClassOf(DescriptionOfAlgorithm d) {
        try {
            String s = d.getClass().getSimpleName();
            return (s == null || s.isBlank()) ? UNKNOWN : s;
        } catch (Exception e) {
            return UNKNOWN;
        }
    }

    // =========================================================================
    // Display - builds rows once, feeds BOTH tables and the report from them
    // =========================================================================

    private void displayAll(AnalysisResult ar) {
        ScanResult scan = ar.scan;

        Set<String> inTestFolderSet = (ar.coverage != null)
                ? ar.coverage.presentInFolder
                : Collections.emptySet();

        List<Object[]> algoRows = buildAlgoToFileRows(ar, inTestFolderSet);
        List<Object[]> fileRows = buildFileToAlgosRows(ar, inTestFolderSet);

        // ── populate Table 1 ──────────────────────────────────────────────────
        modelAlgoToFile.setRowCount(0);
        for (Object[] row : algoRows) modelAlgoToFile.addRow(row);

        // ── populate Table 2 ──────────────────────────────────────────────────
        modelFileToAlgos.setRowCount(0);
        for (Object[] row : fileRows) modelFileToAlgos.addRow(row);

        // ── derive summary counts directly from the row data (single source of truth) ─
        long distinctFilesNoNoinput = scan.fileToAlgos.keySet().stream()
                .filter(f -> !NOINPUT_TOKEN.equalsIgnoreCase(f)).count();

        long missingCount = algoRows.stream()
                .filter(r -> "MISSING FROM DOCS".equals(r[6])).count();
        long unusedTestFileCount = fileRows.stream()
                .filter(r -> ((Integer) r[3]) == 0).count();
        long missingFromFolderCount = fileRows.stream()
                .filter(r -> "No".equals(r[6])).count();
        long typeConflictCount = fileRows.stream()
                .filter(r -> "Yes".equals(r[5])).count();

        lblHtmlFiles.setText("HTML files scanned: " + scan.htmlFileCount);
        lblDistinctAlgos.setText("Distinct algorithms: " + scan.distinctAlgorithms.size());
        lblDistinctFiles.setText("Distinct input files: " + distinctFilesNoNoinput);
        lblTotalPairs.setText("Algorithm/file pairs: " + scan.pairs.size());
        lblMissingAlgos.setText("Algorithms w/o doc example: " + missingCount);
        lblTypeConflicts.setText("Files with type conflicts: " + typeConflictCount);

        if (ar.testFolderProvided) {
            lblUnusedTestFiles.setText("Test files w/o algorithm: " + unusedTestFileCount
                    + " / " + ar.coverage.totalTestFiles);
            lblMissingFromFolder.setText("Doc files missing from folder: " + missingFromFolderCount);
        } else {
            lblUnusedTestFiles.setText("Test files w/o algorithm: (no test folder given)");
            lblMissingFromFolder.setText("Doc files missing from folder: (no test folder given)");
        }

        Color ok = new Color(30, 120, 30), bad = new Color(160, 40, 40), neutral = Color.DARK_GRAY;
        lblMissingAlgos.setForeground(missingCount == 0 ? ok : bad);
        lblTypeConflicts.setForeground(typeConflictCount == 0 ? ok : bad);
        lblUnusedTestFiles.setForeground(!ar.testFolderProvided ? neutral : unusedTestFileCount == 0 ? ok : bad);
        lblMissingFromFolder.setForeground(!ar.testFolderProvided ? neutral : missingFromFolderCount == 0 ? ok : bad);

        // ── build the plain-text report FROM the same rows (guaranteed consistency) ─
        txtReport.setText(buildReportFromRows(ar, algoRows, fileRows,
                distinctFilesNoNoinput, missingCount, unusedTestFileCount,
                missingFromFolderCount, typeConflictCount));
        txtReport.setCaretPosition(0);
    }

    // =========================================================================
    // Plain-text report - built strictly from the row arrays used by the tables
    // =========================================================================

    private String buildReportFromRows(AnalysisResult ar,
            List<Object[]> algoRows, List<Object[]> fileRows,
            long distinctFiles, long missingCount, long unusedTestFileCount,
            long missingFromFolderCount, long typeConflictCount) {

        String nl = System.lineSeparator();
        StringBuilder sb = new StringBuilder();

        sb.append("=== SPMF Algorithm / Input-File - Consolidated Analysis Report ===").append(nl).append(nl);

        sb.append("-- OVERVIEW --").append(nl);
        sb.append("  HTML files scanned         : ").append(ar.scan.htmlFileCount).append(nl);
        sb.append("  Distinct algorithms         : ").append(ar.scan.distinctAlgorithms.size()).append(nl);
        sb.append("  Distinct input files         : ").append(distinctFiles).append(nl);
        sb.append("  Algorithm/file pairs         : ").append(ar.scan.pairs.size()).append(nl);
        sb.append("  Algorithms w/o doc example   : ").append(missingCount).append(nl);
        sb.append("  Files with type conflicts   : ").append(typeConflictCount).append(nl);
        if (ar.testFolderProvided) {
            sb.append("  Test files w/o algorithm     : ").append(unusedTestFileCount)
              .append(" / ").append(ar.coverage.totalTestFiles).append(nl);
            sb.append("  Doc files missing from folder: ").append(missingFromFolderCount).append(nl);
        } else {
            sb.append("  (no test folder supplied - coverage columns show N/A)").append(nl);
        }
        sb.append(nl);

        sb.append("-- TABLE 1: ALGORITHM -> INPUT FILE  (").append(algoRows.size()).append(" rows) --").append(nl);
        sb.append(String.format("  %-28s %-22s %-14s %-22s %-6s %-16s %-20s %s%n",
                "Algorithm", "Input File", "File Type", "Description Class",
                "Occ.", "In Test Folder", "Status", "Doc Page(s)"));
        for (Object[] r : algoRows) {
            sb.append(String.format("  %-28s %-22s %-14s %-22s %-6s %-16s %-20s %s%n",
                    trimTo(r[0], 28), trimTo(r[1], 22), trimTo(r[2], 14), trimTo(r[3], 22),
                    r[4], trimTo(r[5], 16), trimTo(r[6], 20), r[7]));
        }
        sb.append(nl);

        sb.append("-- TABLE 2: INPUT FILE -> ALGORITHMS  (").append(fileRows.size()).append(" rows) --").append(nl);
        sb.append(String.format("  %-22s %-30s %-6s %-16s %-10s %-16s %s%n",
                "Input File", "Algorithms", "#", "File Type(s)", "Conflict?",
                "In Test Folder", "Doc Page(s) / Notes"));
        for (Object[] r : fileRows) {
            sb.append(String.format("  %-22s %-30s %-6s %-16s %-10s %-16s %s%n",
                    trimTo(r[0], 22), trimTo(r[1], 30), r[3], trimTo(r[4], 16),
                    trimTo(r[5], 10), trimTo(r[6], 16), r[7]));
        }

        return sb.toString();
    }

    private String trimTo(Object value, int maxLen) {
        String s = String.valueOf(value);
        return s.length() > maxLen ? s.substring(0, maxLen - 1) + "…" : s;
    }

    // =========================================================================
    // Clear
    // =========================================================================

    private void clearAll() {
        lastResult = null;
        modelAlgoToFile.setRowCount(0);
        modelFileToAlgos.setRowCount(0);
        txtReport.setText("");
        if (txtFilterAlgo != null) txtFilterAlgo.setText("");
        if (txtFilterFile != null) txtFilterFile.setText("");

        lblHtmlFiles.setText("HTML files scanned: –");
        lblDistinctAlgos.setText("Distinct algorithms: –");
        lblDistinctFiles.setText("Distinct input files: –");
        lblTotalPairs.setText("Algorithm/file pairs: –");
        lblMissingAlgos.setText("Algorithms w/o doc example: –");
        lblUnusedTestFiles.setText("Test files w/o algorithm: –");
        lblMissingFromFolder.setText("Doc files missing from folder: –");
        lblTypeConflicts.setText("Files with type conflicts: –");

        Color neutral = new JLabel().getForeground();
        for (JLabel l : new JLabel[]{lblMissingAlgos, lblUnusedTestFiles,
                lblMissingFromFolder, lblTypeConflicts}) {
            l.setForeground(neutral);
        }

        lblStatus.setText("Cleared.");
    }

    // =========================================================================
    // AlgorithmManager helpers
    // =========================================================================

    private AlgorithmManager getManager() {
        if (algorithmManager == null) {
            try { algorithmManager = AlgorithmManager.getInstance(); }
            catch (Exception e) {
                System.err.println("AlgorithmManager unavailable: " + e.getMessage());
            }
        }
        return algorithmManager;
    }

    private String lookupFileType(String algoName) {
        return inputFileTypeCache.computeIfAbsent(algoName, n -> {
            AlgorithmManager m = getManager();
            if (m == null) return UNKNOWN;
            try {
                DescriptionOfAlgorithm d = m.getDescriptionOfAlgorithm(n);
                if (d == null) return UNKNOWN;
                String[] t = d.getInputFileTypes();
                if (t == null || t.length == 0) return UNKNOWN;
                String last = t[t.length - 1];
                return (last == null || last.isBlank()) ? UNKNOWN : last.trim();
            } catch (Exception e) { return UNKNOWN; }
        });
    }

    private String lookupDescClassName(String algoName) {
        return descClassNameCache.computeIfAbsent(algoName, n -> {
            AlgorithmManager m = getManager();
            if (m == null) return UNKNOWN;
            try {
                DescriptionOfAlgorithm d = m.getDescriptionOfAlgorithm(n);
                if (d == null) return UNKNOWN;
                String s = d.getClass().getSimpleName();
                return (s == null || s.isBlank()) ? UNKNOWN : s.trim();
            } catch (Exception e) { return UNKNOWN; }
        });
    }

    // =========================================================================
    // HTML stripping / cleaning
    // =========================================================================

    private String stripHtml(String html) {
        if (html == null || html.isEmpty()) return "";
        String t = html;
        t = t.replaceAll("(?s)<!--.*?-->", " ");
        t = t.replaceAll("(?is)<br\\s*/?>", " ");
        t = t.replaceAll("(?is)</?(p|div|li|ul|ol|table|tr|td|th|h[1-6]"
                + "|blockquote|section|article|header|footer|hr|pre)\\b[^>]*>", " ");
        t = t.replaceAll("(?s)<[^>]+>", "");
        t = decodeEntities(t);
        t = UNICODE_DASHES.matcher(t).replaceAll("-");
        return t.replaceAll("[\\s\\u00A0]+", " ").trim();
    }

    private String decodeEntities(String text) {
        String s = text
                .replace("&nbsp;",  " ").replace("&#160;",  " ")
                .replace("&amp;",   "&").replace("&lt;",    "<")
                .replace("&gt;",    ">").replace("&quot;",  "\"")
                .replace("&#39;",   "'").replace("&apos;",  "'")
                .replace("&rsquo;", "'").replace("&lsquo;", "'")
                .replace("&rdquo;","\"").replace("&ldquo;","\"")
                .replace("&#8217;","'" ).replace("&#8220;","\"")
                .replace("&#8221;","\"");

        Matcher m = NUMERIC_ENTITY.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            try {
                boolean hex = "x".equalsIgnoreCase(m.group(1));
                int cp = Integer.parseInt(m.group(2), hex ? 16 : 10);
                m.appendReplacement(sb, Matcher.quoteReplacement(new String(Character.toChars(cp))));
            } catch (Exception ex) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group()));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String cleanToken(String token) {
        if (token == null) return "";
        String t = token.trim();
        if (t.length() >= 2) {
            char f = t.charAt(0), l = t.charAt(t.length() - 1);
            if ((f == '"' && l == '"') || (f == '\'' && l == '\''))
                t = t.substring(1, t.length() - 1);
        }
        while (!t.isEmpty() && ",.;:!?".indexOf(t.charAt(t.length() - 1)) >= 0)
            t = t.substring(0, t.length() - 1);
        if (!t.isEmpty()) {
            char l = t.charAt(t.length() - 1);
            if ((l == ')' && t.indexOf('(') < 0)
                    || (l == ']' && t.indexOf('[') < 0)
                    || (l == '}' && t.indexOf('{') < 0))
                t = t.substring(0, t.length() - 1);
        }
        return t.trim();
    }

    // =========================================================================
    // I/O utilities
    // =========================================================================

    private String extractBaseName(String token) {
        String n = token.replace('\\', '/');
        int i = n.lastIndexOf('/');
        return i >= 0 ? n.substring(i + 1) : n;
    }

    private String readFileSafely(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            try {
                return new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
            } catch (IOException ex) { return ""; }
        }
    }

    private void copyToClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text), null);
        lblStatus.setText("Copied to clipboard.");
    }

    private void saveTextToFile(String text, String defaultName) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Save Report");
        fc.setSelectedFile(new File(defaultName));
        if (fc.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        try {
            Files.write(fc.getSelectedFile().toPath(), text.getBytes(StandardCharsets.UTF_8));
            lblStatus.setText("Saved to: " + fc.getSelectedFile().getAbsolutePath());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame,
                    "Could not save file:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // =========================================================================
    // Entry point
    // =========================================================================

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AlgorithmFileMappingExtractorWindow().createAndShowGUI());
    }
}