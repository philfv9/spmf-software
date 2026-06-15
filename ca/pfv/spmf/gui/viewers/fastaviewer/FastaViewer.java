package ca.pfv.spmf.gui.viewers.fastaviewer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

/* Copyright (c) 2008-2024 Philippe Fournier-Viger
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

/**
 * A comprehensive GUI application for visualizing and analyzing FASTA files.
 * Features include dual-view mode, sequence statistics, export options,
 * filtering, and various bioinformatics analyses.
 * 
 * @author Philippe Fournier-Viger
 */
public class FastaViewer extends JFrame {
    
    /** Serial version UID for serialization */
    private static final long serialVersionUID = -8629592973530410144L;
    
    /** Default window width */
    private static final int DEFAULT_WIDTH = 1200;
    
    /** Default window height */
    private static final int DEFAULT_HEIGHT = 800;
    
    /** Highlight color for search results */
    private static final Color HIGHLIGHT_COLOR = new Color(255, 255, 0, 100);
    
    /** The text area for displaying sequences in text mode */
    private JTextArea textArea;
    
    /** The table for displaying sequences in table mode */
    private JTable sequenceTable;
    
    /** Table model for the sequence table */
    private DefaultTableModel tableModel;
    
    /** Row sorter for the table */
    private TableRowSorter<DefaultTableModel> rowSorter;
    
    /** The current FASTA dataset loaded in memory */
    private FastaDataset dataset;
    
    /** The status bar for displaying application status messages */
    private JLabel statusBar;
    
    /** Statistics panel showing sequence information */
    private JPanel statisticsPanel;
    
    /** Label for sequence count */
    private JLabel sequenceCountLabel;
    
    /** Label for total bases */
    private JLabel totalBasesLabel;
    
    /** Label for average length */
    private JLabel avgLengthLabel;
    
    /** Label for GC content */
    private JLabel gcContentLabel;
    
    /** Search field for filtering sequences */
    private JTextField searchField;
    
    /** Tabbed pane for switching between views */
    private JTabbedPane tabbedPane;
    
    /** Checkbox for case-sensitive search */
    private JCheckBox caseSensitiveCheck;
    
    /** Current file path */
    private String currentFilePath;
    
    /** View mode: text or table */
    private enum ViewMode { TEXT, TABLE }
    
    /** Current view mode */
    private ViewMode currentViewMode = ViewMode.TEXT;

    /**
     * Constructs a new FASTA viewer window.
     * 
     * @param runAsStandaloneApp if true, the application will exit when the window is closed
     */
    public FastaViewer(boolean runAsStandaloneApp) {
        dataset = new FastaDataset();
        initializeComponents();
        configureWindow(runAsStandaloneApp);
    }

    /**
     * Initializes all GUI components.
     */
    private void initializeComponents() {
        setLayout(new BorderLayout(5, 5));
        
        createMenuBar();
        createToolbar();
        createMainPanel();
        createStatisticsPanel();
        createStatusBar();
        
        setupKeyboardShortcuts();
    }

    /**
     * Configures the main window properties.
     * 
     * @param runAsStandaloneApp if true, sets the default close operation to exit
     */
    private void configureWindow(boolean runAsStandaloneApp) {
        setTitle("SPMF FASTA Viewer - Professional Edition");
        setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        if (runAsStandaloneApp) {
            setDefaultCloseOperation(EXIT_ON_CLOSE);
        }
        setLocationRelativeTo(null);
        
        // Set application icon if available
        try {
            // You can add your icon here
            // setIconImage(new ImageIcon("path/to/icon.png").getImage());
        } catch (Exception e) {
            // Icon not critical
        }
    }

    /**
     * Creates and configures the menu bar.
     */
    private void createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(createFileMenu());
        menuBar.add(createEditMenu());
        menuBar.add(createViewMenu());
        menuBar.add(createAnalysisMenu());
        menuBar.add(createToolsMenu());
        menuBar.add(createHelpMenu());
        setJMenuBar(menuBar);
    }

    /**
     * Creates the File menu.
     * 
     * @return the configured File menu
     */
    private JMenu createFileMenu() {
        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);
        
        JMenuItem openItem = new JMenuItem("Open...", KeyEvent.VK_O);
        openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
        openItem.addActionListener(e -> handleOpenFile());
        
        JMenuItem reloadItem = new JMenuItem("Reload", KeyEvent.VK_R);
        reloadItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
        reloadItem.addActionListener(e -> handleReload());
        
        JMenuItem exportItem = new JMenuItem("Export...", KeyEvent.VK_E);
        exportItem.addActionListener(e -> handleExport());
        
        JMenuItem exportSelectedItem = new JMenuItem("Export Selected...");
        exportSelectedItem.addActionListener(e -> handleExportSelected());
        
        JMenuItem propertiesItem = new JMenuItem("File Properties...");
        propertiesItem.addActionListener(e -> showFileProperties());
        
        JMenuItem exitItem = new JMenuItem("Exit", KeyEvent.VK_X);
        exitItem.addActionListener(e -> dispose());
        
        fileMenu.add(openItem);
        fileMenu.add(reloadItem);
        fileMenu.addSeparator();
        fileMenu.add(exportItem);
        fileMenu.add(exportSelectedItem);
        fileMenu.addSeparator();
        fileMenu.add(propertiesItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);
        
        return fileMenu;
    }

    /**
     * Creates the Edit menu.
     * 
     * @return the configured Edit menu
     */
    private JMenu createEditMenu() {
        JMenu editMenu = new JMenu("Edit");
        editMenu.setMnemonic(KeyEvent.VK_E);
        
        JMenuItem findItem = new JMenuItem("Find...", KeyEvent.VK_F);
        findItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK));
        findItem.addActionListener(e -> searchField.requestFocus());
        
        JMenuItem copyItem = new JMenuItem("Copy Selected", KeyEvent.VK_C);
        copyItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK));
        copyItem.addActionListener(e -> handleCopy());
        
        JMenuItem selectAllItem = new JMenuItem("Select All", KeyEvent.VK_A);
        selectAllItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK));
        selectAllItem.addActionListener(e -> handleSelectAll());
        
        editMenu.add(findItem);
        editMenu.addSeparator();
        editMenu.add(copyItem);
        editMenu.add(selectAllItem);
        
        return editMenu;
    }

    /**
     * Creates the View menu.
     * 
     * @return the configured View menu
     */
    private JMenu createViewMenu() {
        JMenu viewMenu = new JMenu("View");
        viewMenu.setMnemonic(KeyEvent.VK_V);
        
        JCheckBoxMenuItem wrapTextItem = new JCheckBoxMenuItem("Wrap Text", false);
        wrapTextItem.addActionListener(e -> {
            textArea.setLineWrap(wrapTextItem.isSelected());
            textArea.setWrapStyleWord(wrapTextItem.isSelected());
        });
        
        JCheckBoxMenuItem showLineNumbersItem = new JCheckBoxMenuItem("Show Statistics Panel", true);
        showLineNumbersItem.addActionListener(e -> 
            statisticsPanel.setVisible(showLineNumbersItem.isSelected())
        );
        
        JMenuItem increaseFontItem = new JMenuItem("Increase Font Size");
        increaseFontItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, InputEvent.CTRL_DOWN_MASK));
        increaseFontItem.addActionListener(e -> changeFontSize(2));
        
        JMenuItem decreaseFontItem = new JMenuItem("Decrease Font Size");
        decreaseFontItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK));
        decreaseFontItem.addActionListener(e -> changeFontSize(-2));
        
        JMenuItem resetFontItem = new JMenuItem("Reset Font Size");
        resetFontItem.addActionListener(e -> resetFontSize());
        
        viewMenu.add(wrapTextItem);
        viewMenu.add(showLineNumbersItem);
        viewMenu.addSeparator();
        viewMenu.add(increaseFontItem);
        viewMenu.add(decreaseFontItem);
        viewMenu.add(resetFontItem);
        
        return viewMenu;
    }

    /**
     * Creates the Analysis menu.
     * 
     * @return the configured Analysis menu
     */
    private JMenu createAnalysisMenu() {
        JMenu analysisMenu = new JMenu("Analysis");
        analysisMenu.setMnemonic(KeyEvent.VK_A);
        
        JMenuItem statisticsItem = new JMenuItem("Show Detailed Statistics");
        statisticsItem.addActionListener(e -> showDetailedStatistics());
        
        JMenuItem compositionItem = new JMenuItem("Nucleotide Composition");
        compositionItem.addActionListener(e -> showNucleotideComposition());
        
        JMenuItem gcContentItem = new JMenuItem("GC Content Analysis");
        gcContentItem.addActionListener(e -> showGCContentAnalysis());
        
        JMenuItem lengthDistItem = new JMenuItem("Length Distribution");
        lengthDistItem.addActionListener(e -> showLengthDistribution());
        
        analysisMenu.add(statisticsItem);
        analysisMenu.add(compositionItem);
        analysisMenu.add(gcContentItem);
        analysisMenu.add(lengthDistItem);
        
        return analysisMenu;
    }

    /**
     * Creates the Tools menu.
     * 
     * @return the configured Tools menu
     */
    private JMenu createToolsMenu() {
        JMenu toolsMenu = new JMenu("Tools");
        toolsMenu.setMnemonic(KeyEvent.VK_T);
        
        JMenuItem countCodonsItem = new JMenuItem("Count Codons");
        countCodonsItem.addActionListener(e -> handleCountCodons());
        
        JMenuItem countKmersItem = new JMenuItem("Count K-mers");
        countKmersItem.addActionListener(e -> handleCountKmers());
        
        JMenuItem countTopKmersItem = new JMenuItem("Count Top-K K-mers");
        countTopKmersItem.addActionListener(e -> handleCountTopKKmers());
        
        JMenuItem reverseComplementItem = new JMenuItem("Reverse Complement");
        reverseComplementItem.addActionListener(e -> handleReverseComplement());
        
        JMenuItem translateItem = new JMenuItem("Translate to Protein");
        translateItem.addActionListener(e -> handleTranslate());
        
        JMenuItem filterByLengthItem = new JMenuItem("Filter by Length...");
        filterByLengthItem.addActionListener(e -> handleFilterByLength());
        
        JMenuItem findORFsItem = new JMenuItem("Find ORFs");
        findORFsItem.addActionListener(e -> handleFindORFs());
        
        toolsMenu.add(countCodonsItem);
        toolsMenu.add(countKmersItem);
        toolsMenu.add(countTopKmersItem);
        toolsMenu.addSeparator();
        toolsMenu.add(reverseComplementItem);
        toolsMenu.add(translateItem);
        toolsMenu.addSeparator();
        toolsMenu.add(filterByLengthItem);
        toolsMenu.add(findORFsItem);
        
        return toolsMenu;
    }

    /**
     * Creates the Help menu.
     * 
     * @return the configured Help menu
     */
    private JMenu createHelpMenu() {
        JMenu helpMenu = new JMenu("Help");
        helpMenu.setMnemonic(KeyEvent.VK_H);
        
        JMenuItem aboutItem = new JMenuItem("About FASTA Viewer");
        aboutItem.addActionListener(e -> showAboutDialog());
        
        JMenuItem userGuideItem = new JMenuItem("User Guide");
        userGuideItem.addActionListener(e -> showUserGuide());
        
        JMenuItem fastaFormatItem = new JMenuItem("FASTA Format Information");
        fastaFormatItem.addActionListener(e -> showFastaFormatInfo());
        
        helpMenu.add(userGuideItem);
        helpMenu.add(fastaFormatItem);
        helpMenu.addSeparator();
        helpMenu.add(aboutItem);
        
        return helpMenu;
    }

    /**
     * Creates the toolbar with commonly used actions.
     */
    private void createToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBorder(new EmptyBorder(5, 5, 5, 5));
        
        // Open button
        JButton openBtn = createToolbarButton("Open", "Open FASTA file", e -> handleOpenFile());
        toolbar.add(openBtn);
        
        // Reload button
        JButton reloadBtn = createToolbarButton("Reload", "Reload current file", e -> handleReload());
        toolbar.add(reloadBtn);
        
        toolbar.addSeparator();
        
        // Export button
        JButton exportBtn = createToolbarButton("Export", "Export sequences", e -> handleExport());
        toolbar.add(exportBtn);
        
        toolbar.addSeparator();
        
        // Statistics button
        JButton statsBtn = createToolbarButton("Stats", "Show detailed statistics", e -> showDetailedStatistics());
        toolbar.add(statsBtn);
        
        toolbar.addSeparator();
        
        // Search components
        toolbar.add(new JLabel(" Search: "));
        searchField = new JTextField(20);
        searchField.setMaximumSize(new Dimension(200, 25));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void changedUpdate(DocumentEvent e) { performSearch(); }
            public void removeUpdate(DocumentEvent e) { performSearch(); }
            public void insertUpdate(DocumentEvent e) { performSearch(); }
        });
        toolbar.add(searchField);
        
        caseSensitiveCheck = new JCheckBox("Case sensitive");
        toolbar.add(caseSensitiveCheck);
        
        add(toolbar, BorderLayout.NORTH);
    }

    /**
     * Creates a toolbar button with specified properties.
     * 
     * @param text the button text
     * @param tooltip the tooltip text
     * @param listener the action listener
     * @return the configured button
     */
    private JButton createToolbarButton(String text, String tooltip, ActionListener listener) {
        JButton button = new JButton(text);
        button.setToolTipText(tooltip);
        button.addActionListener(listener);
        button.setFocusable(false);
        return button;
    }

    /**
     * Creates the main panel with tabbed views.
     */
    private void createMainPanel() {
        tabbedPane = new JTabbedPane();
        
        // Text view
        textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        textArea.setLineWrap(false);
        JScrollPane textScrollPane = new JScrollPane(textArea);
        tabbedPane.addTab("Text View", textScrollPane);
        
        // Table view
        String[] columnNames = {"#", "Header", "Length", "GC%", "Sequence Preview"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        sequenceTable = new JTable(tableModel);
        sequenceTable.setAutoCreateRowSorter(true);
        sequenceTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        sequenceTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        sequenceTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        sequenceTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        sequenceTable.getColumnModel().getColumn(3).setPreferredWidth(80);
        sequenceTable.getColumnModel().getColumn(4).setPreferredWidth(400);
        
        sequenceTable.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    showSequenceDetails();
                }
            }
        });
        
        rowSorter = new TableRowSorter<>(tableModel);
        sequenceTable.setRowSorter(rowSorter);
        
        JScrollPane tableScrollPane = new JScrollPane(sequenceTable);
        tabbedPane.addTab("Table View", tableScrollPane);
        
        add(tabbedPane, BorderLayout.CENTER);
    }

    /**
     * Creates the statistics panel showing dataset information.
     */
    private void createStatisticsPanel() {
        statisticsPanel = new JPanel();
        statisticsPanel.setLayout(new GridLayout(1, 4, 10, 5));
        statisticsPanel.setBorder(BorderFactory.createTitledBorder("Dataset Statistics"));
        
        sequenceCountLabel = new JLabel("Sequences: 0");
        totalBasesLabel = new JLabel("Total Bases: 0");
        avgLengthLabel = new JLabel("Avg Length: 0");
        gcContentLabel = new JLabel("GC Content: 0%");
        
        statisticsPanel.add(sequenceCountLabel);
        statisticsPanel.add(totalBasesLabel);
        statisticsPanel.add(avgLengthLabel);
        statisticsPanel.add(gcContentLabel);
        
        add(statisticsPanel, BorderLayout.SOUTH);
    }

    /**
     * Creates the status bar.
     */
    private void createStatusBar() {
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusBar = new JLabel("Ready");
        statusBar.setBorder(BorderFactory.createEtchedBorder());
        statusPanel.add(statusBar, BorderLayout.CENTER);
        
        statisticsPanel.add(statusPanel);
    }

    /**
     * Sets up keyboard shortcuts.
     */
    private void setupKeyboardShortcuts() {
        // ESC to clear search
        searchField.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clearSearch");
        searchField.getActionMap().put("clearSearch", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                searchField.setText("");
            }
        });
    }

    /**
     * Handles the Open File action.
     */
    private void handleOpenFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Open FASTA File");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".fasta") 
                    || f.getName().toLowerCase().endsWith(".fa") 
                    || f.getName().toLowerCase().endsWith(".fna")
                    || f.getName().toLowerCase().endsWith(".ffn");
            }
            public String getDescription() {
                return "FASTA files (*.fasta, *.fa, *.fna, *.ffn)";
            }
        });
        
        if (currentFilePath != null) {
            fileChooser.setCurrentDirectory(new File(currentFilePath).getParentFile());
        }
        
        int returnValue = fileChooser.showOpenDialog(this);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            load(fileChooser.getSelectedFile().getAbsolutePath());
        }
    }

    /**
     * Handles the Reload action.
     */
    private void handleReload() {
        if (currentFilePath != null) {
            load(currentFilePath);
        } else {
            showInfo("No file to reload.");
        }
    }

    /**
     * Handles the Export action.
     */
    private void handleExport() {
        if (dataset.isEmpty()) {
            showInfo("No sequences to export.");
            return;
        }
        
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export Sequences");
        
        int returnValue = fileChooser.showSaveDialog(this);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            try {
                String outputPath = fileChooser.getSelectedFile().getAbsolutePath();
                dataset.exportToFile(outputPath, dataset.getSequenceEntries());
                updateStatusBar("Exported " + dataset.getSequenceCount() + " sequences to " + outputPath);
                showInfo("Successfully exported sequences.");
            } catch (IOException e) {
                showError("Error exporting sequences: " + e.getMessage());
            }
        }
    }

    /**
     * Handles the Export Selected action.
     */
    private void handleExportSelected() {
        if (sequenceTable.getSelectedRowCount() == 0) {
            showInfo("No sequences selected.");
            return;
        }
        
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export Selected Sequences");
        
        int returnValue = fileChooser.showSaveDialog(this);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            try {
                List<FastaSequenceEntry> selectedSequences = getSelectedSequences();
                String outputPath = fileChooser.getSelectedFile().getAbsolutePath();
                dataset.exportToFile(outputPath, selectedSequences);
                updateStatusBar("Exported " + selectedSequences.size() + " sequences to " + outputPath);
                showInfo("Successfully exported selected sequences.");
            } catch (IOException e) {
                showError("Error exporting sequences: " + e.getMessage());
            }
        }
    }

    /**
     * Gets the currently selected sequences from the table.
     * 
     * @return list of selected sequences
     */
    private List<FastaSequenceEntry> getSelectedSequences() {
        int[] selectedRows = sequenceTable.getSelectedRows();
        List<FastaSequenceEntry> allSequences = dataset.getSequenceEntries();
        List<FastaSequenceEntry> selected = new java.util.ArrayList<>();
        
        for (int row : selectedRows) {
            int modelRow = sequenceTable.convertRowIndexToModel(row);
            selected.add(allSequences.get(modelRow));
        }
        
        return selected;
    }

    /**
     * Handles the Copy action.
     */
    private void handleCopy() {
        if (tabbedPane.getSelectedIndex() == 0) {
            // Text view
            String selectedText = textArea.getSelectedText();
            if (selectedText != null && !selectedText.isEmpty()) {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(selectedText), null);
                updateStatusBar("Copied to clipboard");
            }
        } else {
            // Table view
            int[] selectedRows = sequenceTable.getSelectedRows();
            if (selectedRows.length > 0) {
                StringBuilder sb = new StringBuilder();
                List<FastaSequenceEntry> allSequences = dataset.getSequenceEntries();
                
                for (int row : selectedRows) {
                    int modelRow = sequenceTable.convertRowIndexToModel(row);
                    FastaSequenceEntry entry = allSequences.get(modelRow);
                    sb.append(">").append(entry.getHeader()).append("\n");
                    sb.append(entry.getSequence()).append("\n\n");
                }
                
                Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(sb.toString()), null);
                updateStatusBar("Copied " + selectedRows.length + " sequence(s) to clipboard");
            }
        }
    }

    /**
     * Handles the Select All action.
     */
    private void handleSelectAll() {
        if (tabbedPane.getSelectedIndex() == 0) {
            textArea.selectAll();
        } else {
            sequenceTable.selectAll();
        }
    }

    /**
     * Changes the font size of the text area.
     * 
     * @param delta the amount to change the font size
     */
    private void changeFontSize(int delta) {
        Font currentFont = textArea.getFont();
        int newSize = Math.max(8, Math.min(72, currentFont.getSize() + delta));
        textArea.setFont(new Font(currentFont.getFontName(), currentFont.getStyle(), newSize));
    }

    /**
     * Resets the font size to default.
     */
    private void resetFontSize() {
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
    }

    /**
     * Performs a search based on the current search field text.
     */
    private void performSearch() {
        String searchText = searchField.getText();
        
        if (searchText.trim().isEmpty()) {
            displaySequences();
            if (rowSorter != null) {
                rowSorter.setRowFilter(null);
            }
            return;
        }
        
        if (tabbedPane.getSelectedIndex() == 0) {
            // Text view search
            searchAndDisplayText(searchText);
        } else {
            // Table view filter
            filterTable(searchText);
        }
    }

    /**
     * Searches and displays results in text view.
     * 
     * @param searchTerm the term to search for
     */
    private void searchAndDisplayText(String searchTerm) {
        List<FastaSequenceEntry> sequences = dataset.getSequenceEntries();
        StringBuilder content = new StringBuilder();
        int matchCount = 0;
        
        boolean caseSensitive = caseSensitiveCheck.isSelected();
        String searchLower = caseSensitive ? searchTerm : searchTerm.toLowerCase();
        
        for (FastaSequenceEntry entry : sequences) {
            String header = entry.getHeader();
            String sequence = entry.getSequence();
            
            String headerToSearch = caseSensitive ? header : header.toLowerCase();
            String seqToSearch = caseSensitive ? sequence : sequence.toLowerCase();
            
            if (headerToSearch.contains(searchLower) || seqToSearch.contains(searchLower)) {
                String highlightedHeader = highlightText(header, searchTerm, caseSensitive);
                String highlightedSequence = highlightText(sequence, searchTerm, caseSensitive);
                content.append(">").append(highlightedHeader).append("\n");
                content.append(highlightedSequence).append("\n\n");
                matchCount++;
            }
        }
        
        textArea.setText(content.toString());
        textArea.setCaretPosition(0);
        updateStatusBar("Found " + matchCount + " match(es) for '" + searchTerm + "'");
    }

    /**
     * Filters the table based on search text.
     * 
     * @param searchText the text to filter by
     */
    private void filterTable(String searchText) {
        if (rowSorter != null) {
            RowFilter<DefaultTableModel, Object> filter = RowFilter.regexFilter(
                caseSensitiveCheck.isSelected() ? searchText : "(?i)" + searchText
            );
            rowSorter.setRowFilter(filter);
            updateStatusBar("Filtered to " + sequenceTable.getRowCount() + " sequence(s)");
        }
    }

    /**
     * Highlights occurrences of search term in text.
     * 
     * @param text the text to process
     * @param searchTerm the term to highlight
     * @param caseSensitive whether search is case sensitive
     * @return the text with highlighted search terms
     */
    private String highlightText(String text, String searchTerm, boolean caseSensitive) {
        String flags = caseSensitive ? "" : "(?i)";
        return text.replaceAll(flags + Pattern.quote(searchTerm), "**$0**");
    }

    /**
     * Displays all sequences in both text and table views.
     */
    private void displaySequences() {
        List<FastaSequenceEntry> sequences = dataset.getSequenceEntries();
        
        // Update text view
        StringBuilder content = new StringBuilder();
        for (FastaSequenceEntry entry : sequences) {
            content.append(">").append(entry.getHeader()).append("\n");
            content.append(entry.getSequence()).append("\n\n");
        }
        textArea.setText(content.toString());
        textArea.setCaretPosition(0);
        
        // Update table view
        tableModel.setRowCount(0);
        for (int i = 0; i < sequences.size(); i++) {
            FastaSequenceEntry entry = sequences.get(i);
            String preview = entry.getSequence().substring(
                0, Math.min(50, entry.getSequence().length())
            ) + "...";
            
            double gcContent = calculateGCContent(entry.getSequence());
            
            tableModel.addRow(new Object[]{
                i + 1,
                entry.getHeader(),
                entry.getLength(),
                String.format("%.1f", gcContent),
                preview
            });
        }
        
        updateStatistics();
        updateStatusBar("Displayed " + sequences.size() + " sequence(s)");
    }

    /**
     * Updates the statistics panel with current dataset information.
     */
    private void updateStatistics() {
        if (dataset.isEmpty()) {
            sequenceCountLabel.setText("Sequences: 0");
            totalBasesLabel.setText("Total Bases: 0");
            avgLengthLabel.setText("Avg Length: 0");
            gcContentLabel.setText("GC Content: 0%");
            return;
        }
        
        List<FastaSequenceEntry> sequences = dataset.getSequenceEntries();
        int totalBases = 0;
        int gcCount = 0;
        
        for (FastaSequenceEntry entry : sequences) {
            String seq = entry.getSequence().toUpperCase();
            totalBases += seq.length();
            for (char c : seq.toCharArray()) {
                if (c == 'G' || c == 'C') {
                    gcCount++;
                }
            }
        }
        
        double avgLength = (double) totalBases / sequences.size();
        double gcContent = totalBases > 0 ? (double) gcCount / totalBases * 100 : 0;
        
        sequenceCountLabel.setText("Sequences: " + sequences.size());
        totalBasesLabel.setText("Total Bases: " + String.format("%,d", totalBases));
        avgLengthLabel.setText("Avg Length: " + String.format("%.1f", avgLength));
        gcContentLabel.setText("GC Content: " + String.format("%.1f%%", gcContent));
    }

    /**
     * Calculates GC content for a sequence.
     * 
     * @param sequence the DNA sequence
     * @return GC content as percentage
     */
    private double calculateGCContent(String sequence) {
        String seq = sequence.toUpperCase();
        int gcCount = 0;
        for (char c : seq.toCharArray()) {
            if (c == 'G' || c == 'C') {
                gcCount++;
            }
        }
        return seq.length() > 0 ? (double) gcCount / seq.length() * 100 : 0;
    }

    /**
     * Shows detailed sequence information when double-clicked in table.
     */
    private void showSequenceDetails() {
        int selectedRow = sequenceTable.getSelectedRow();
        if (selectedRow >= 0) {
            int modelRow = sequenceTable.convertRowIndexToModel(selectedRow);
            FastaSequenceEntry entry = dataset.getSequenceEntries().get(modelRow);
            
            SequenceDetailsDialog dialog = new SequenceDetailsDialog(this, entry);
            dialog.setVisible(true);
        }
    }

    /**
     * Loads a FASTA file from the specified path.
     * 
     * @param filePath the path to the FASTA file
     */
    public void load(String filePath) {
        try {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            dataset = new FastaDataset();
            dataset.loadFile(filePath);
            currentFilePath = filePath;
            displaySequences();
            updateStatusBar("Loaded file: " + new File(filePath).getName());
            setTitle("SPMF FASTA Viewer - " + new File(filePath).getName());
        } catch (IOException e) {
            showError("Error reading file: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            showError("Invalid FASTA file format: " + e.getMessage());
        } finally {
            setCursor(Cursor.getDefaultCursor());
        }
    }

    /**
     * Shows detailed statistics dialog.
     */
    private void showDetailedStatistics() {
        if (dataset.isEmpty()) {
            showInfo("No sequences loaded.");
            return;
        }
        
        StatisticsDialog dialog = new StatisticsDialog(this, dataset);
        dialog.setVisible(true);
    }

    /**
     * Shows nucleotide composition analysis.
     */
    private void showNucleotideComposition() {
        if (dataset.isEmpty()) {
            showInfo("No sequences loaded.");
            return;
        }
        
        NucleotideCompositionDialog dialog = new NucleotideCompositionDialog(this, dataset);
        dialog.setVisible(true);
    }

    /**
     * Shows GC content analysis.
     */
    private void showGCContentAnalysis() {
        if (dataset.isEmpty()) {
            showInfo("No sequences loaded.");
            return;
        }
        
        GCContentDialog dialog = new GCContentDialog(this, dataset);
        dialog.setVisible(true);
    }

    /**
     * Shows length distribution analysis.
     */
    private void showLengthDistribution() {
        if (dataset.isEmpty()) {
            showInfo("No sequences loaded.");
            return;
        }
        
        LengthDistributionDialog dialog = new LengthDistributionDialog(this, dataset);
        dialog.setVisible(true);
    }

    /**
     * Shows file properties dialog.
     */
    private void showFileProperties() {
        if (currentFilePath == null) {
            showInfo("No file loaded.");
            return;
        }
        
        File file = new File(currentFilePath);
        StringBuilder props = new StringBuilder();
        props.append("File: ").append(file.getName()).append("\n");
        props.append("Path: ").append(file.getAbsolutePath()).append("\n");
        props.append("Size: ").append(String.format("%,d", file.length())).append(" bytes\n");
        props.append("Last Modified: ").append(new java.util.Date(file.lastModified())).append("\n");
        props.append("\nDataset Information:\n");
        props.append("Sequences: ").append(dataset.getSequenceCount()).append("\n");
        
        JOptionPane.showMessageDialog(this, props.toString(), "File Properties", 
            JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Handles codon counting.
     */
    private void handleCountCodons() {
        if (dataset.isEmpty()) {
            showInfo("No sequences loaded.");
            return;
        }
        
        int response = JOptionPane.showConfirmDialog(this, 
            "Include degeneracy?", "Count Codons", JOptionPane.YES_NO_OPTION);
        boolean includeDegeneracy = (response == JOptionPane.YES_OPTION);
        
        String output = chooseOutputFilePath();
        if (output != null) {
            try {
                AlgoCountCodons algo = new AlgoCountCodons();
                algo.runAlgorithm(dataset, output, includeDegeneracy);
                algo.printStats();
                updateStatusBar("Codons counted. Output: " + output);
                promptToViewOutput(output);
            } catch (Exception e) {
                showError("Error counting codons: " + e.getMessage());
            }
        }
    }

    /**
     * Handles k-mer counting.
     */
    private void handleCountKmers() {
        if (dataset.isEmpty()) {
            showInfo("No sequences loaded.");
            return;
        }
        
        String kValue = JOptionPane.showInputDialog(this, "Enter k value (1-20):", "3");
        if (kValue != null && !kValue.trim().isEmpty()) {
            try {
                int k = Integer.parseInt(kValue.trim());
                if (k <= 0 || k > 20) {
                    showError("K value must be between 1 and 20.");
                    return;
                }
                
                String output = chooseOutputFilePath();
                if (output != null) {
                    AlgoCountKMers algo = new AlgoCountKMers();
                    algo.runAlgorithm(dataset, k, output);
                    algo.printStats();
                    updateStatusBar("K-mers counted. Output: " + output);
                    promptToViewOutput(output);
                }
            } catch (NumberFormatException e) {
                showError("Invalid number format.");
            } catch (Exception e) {
                showError("Error counting k-mers: " + e.getMessage());
            }
        }
    }

    /**
     * Handles top-k k-mer counting.
     */
    private void handleCountTopKKmers() {
        if (dataset.isEmpty()) {
            showInfo("No sequences loaded.");
            return;
        }
        
        JPanel panel = new JPanel(new GridLayout(2, 2, 5, 5));
        JTextField kField = new JTextField("3");
        JTextField topkField = new JTextField("10");
        panel.add(new JLabel("K-mer size:"));
        panel.add(kField);
        panel.add(new JLabel("Top K count:"));
        panel.add(topkField);
        
        int result = JOptionPane.showConfirmDialog(this, panel, 
            "Count Top-K K-mers", JOptionPane.OK_CANCEL_OPTION);
        
        if (result == JOptionPane.OK_OPTION) {
            try {
                int k = Integer.parseInt(kField.getText().trim());
                int topk = Integer.parseInt(topkField.getText().trim());
                
                if (k <= 0 || k > 20 || topk <= 0) {
                    showError("Invalid values. K must be 1-20, Top-K must be positive.");
                    return;
                }
                
                String output = chooseOutputFilePath();
                if (output != null) {
                    AlgoCountTopKMers algo = new AlgoCountTopKMers();
                    algo.runAlgorithm(dataset, k, topk, output);
                    algo.printStats();
                    updateStatusBar("Top-K k-mers counted. Output: " + output);
                    promptToViewOutput(output);
                }
            } catch (NumberFormatException e) {
                showError("Invalid number format.");
            } catch (Exception e) {
                showError("Error counting top-k k-mers: " + e.getMessage());
            }
        }
    }

    /**
     * Handles reverse complement generation.
     */
    private void handleReverseComplement() {
        if (dataset.isEmpty()) {
            showInfo("No sequences loaded.");
            return;
        }
        
        ReverseComplementDialog dialog = new ReverseComplementDialog(this, dataset);
        dialog.setVisible(true);
    }

    /**
     * Handles translation to protein.
     */
    private void handleTranslate() {
        if (dataset.isEmpty()) {
            showInfo("No sequences loaded.");
            return;
        }
        
        TranslationDialog dialog = new TranslationDialog(this, dataset);
        dialog.setVisible(true);
    }

    /**
     * Handles filtering sequences by length.
     */
    private void handleFilterByLength() {
        if (dataset.isEmpty()) {
            showInfo("No sequences loaded.");
            return;
        }
        
        JPanel panel = new JPanel(new GridLayout(2, 2, 5, 5));
        JTextField minField = new JTextField("0");
        JTextField maxField = new JTextField("10000");
        panel.add(new JLabel("Minimum length:"));
        panel.add(minField);
        panel.add(new JLabel("Maximum length:"));
        panel.add(maxField);
        
        int result = JOptionPane.showConfirmDialog(this, panel, 
            "Filter by Length", JOptionPane.OK_CANCEL_OPTION);
        
        if (result == JOptionPane.OK_OPTION) {
            try {
                int min = Integer.parseInt(minField.getText().trim());
                int max = Integer.parseInt(maxField.getText().trim());
                
                List<FastaSequenceEntry> filtered = new java.util.ArrayList<>();
                for (FastaSequenceEntry entry : dataset.getSequenceEntries()) {
                    if (entry.getLength() >= min && entry.getLength() <= max) {
                        filtered.add(entry);
                    }
                }
                
                if (filtered.isEmpty()) {
                    showInfo("No sequences match the length criteria.");
                } else {
                    String output = chooseOutputFilePath();
                    if (output != null) {
                        dataset.exportToFile(output, filtered);
                        updateStatusBar("Filtered " + filtered.size() + " sequences to " + output);
                        showInfo("Filtered " + filtered.size() + " sequences.");
                    }
                }
            } catch (NumberFormatException e) {
                showError("Invalid number format.");
            } catch (IOException e) {
                showError("Error exporting filtered sequences: " + e.getMessage());
            }
        }
    }

    /**
     * Handles finding ORFs (Open Reading Frames).
     */
    private void handleFindORFs() {
        if (dataset.isEmpty()) {
            showInfo("No sequences loaded.");
            return;
        }
        
        ORFFinderDialog dialog = new ORFFinderDialog(this, dataset);
        dialog.setVisible(true);
    }

    /**
     * Chooses an output file path.
     * 
     * @return the selected file path or null
     */
    private String chooseOutputFilePath() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select Output File");
        
        if (currentFilePath != null) {
            fileChooser.setCurrentDirectory(new File(currentFilePath).getParentFile());
        }
        
        int userSelection = fileChooser.showSaveDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            return fileChooser.getSelectedFile().getAbsolutePath();
        }
        return null;
    }

    /**
     * Prompts to view output file.
     * 
     * @param outputFilePath the output file path
     */
    private void promptToViewOutput(String outputFilePath) {
        int response = JOptionPane.showConfirmDialog(this, 
            "Do you want to view the output file?", "View Output", 
            JOptionPane.YES_NO_OPTION);
        
        if (response == JOptionPane.YES_OPTION) {
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(new File(outputFilePath));
                } else {
                    showError("Desktop operations not supported.");
                }
            } catch (IOException e) {
                showError("Error opening file: " + e.getMessage());
            }
        }
    }

    /**
     * Shows the about dialog.
     */
    private void showAboutDialog() {
        String message = "SPMF FASTA Viewer - Professional Edition\n\n" +
                        "Version 1.0\n\n" +
                        "A comprehensive tool for viewing and analyzing FASTA files.\n\n" +
                        "Part of the SPMF Data Mining Software\n" +
                        "Copyright (c) 2008-2024 Philippe Fournier-Viger\n\n" +
                        "Licensed under GPL v3";
        
        JOptionPane.showMessageDialog(this, message, "About FASTA Viewer", 
            JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Shows the user guide.
     */
    private void showUserGuide() {
        String guide = "FASTA Viewer User Guide\n\n" +
                      "Features:\n" +
                      "• Open and view FASTA files\n" +
                      "• Switch between text and table views\n" +
                      "• Search and filter sequences\n" +
                      "• Export sequences (all or selected)\n" +
                      "• Analyze sequences (statistics, composition, GC content)\n" +
                      "• Count codons and k-mers\n" +
                      "• Find ORFs and translate to protein\n\n" +
                      "Keyboard Shortcuts:\n" +
                      "• Ctrl+O: Open file\n" +
                      "• Ctrl+F: Focus search field\n" +
                      "• Ctrl+C: Copy selected\n" +
                      "• Ctrl+A: Select all\n" +
                      "• F5: Reload file\n" +
                      "• ESC: Clear search\n\n" +
                      "Tips:\n" +
                      "• Double-click a sequence in table view for details\n" +
                      "• Use the search field for real-time filtering\n" +
                      "• Right-click sequences for context menu options";
        
        JTextArea textArea = new JTextArea(guide);
        textArea.setEditable(false);
        textArea.setRows(20);
        textArea.setColumns(50);
        JScrollPane scrollPane = new JScrollPane(textArea);
        
        JOptionPane.showMessageDialog(this, scrollPane, "User Guide", 
            JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Shows FASTA format information.
     */
    private void showFastaFormatInfo() {
        String info = "FASTA Format Information\n\n" +
                     "The FASTA format is a text-based format for representing nucleotide\n" +
                     "or peptide sequences.\n\n" +
                     "Structure:\n" +
                     "• Header line starts with '>'\n" +
                     "• Followed by sequence identifier\n" +
                     "• Sequence data on subsequent lines\n" +
                     "• Comments start with ';'\n\n" +
                     "Example:\n" +
                     ">Sequence1 Description\n" +
                     "ATCGATCGATCG\n" +
                     ">Sequence2\n" +
                     "GCTAGCTAGCTA\n\n" +
                     "Supported file extensions:\n" +
                     ".fasta, .fa, .fna, .ffn";
        
        JTextArea textArea = new JTextArea(info);
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(textArea);
        
        JOptionPane.showMessageDialog(this, scrollPane, "FASTA Format", 
            JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Updates the status bar.
     * 
     * @param message the status message
     */
    private void updateStatusBar(String message) {
        statusBar.setText(message);
    }

    /**
     * Shows an error dialog.
     * 
     * @param message the error message
     */
    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", 
            JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Shows an info dialog.
     * 
     * @param message the info message
     */
    private void showInfo(String message) {
        JOptionPane.showMessageDialog(this, message, "Information", 
            JOptionPane.INFORMATION_MESSAGE);
    }
}