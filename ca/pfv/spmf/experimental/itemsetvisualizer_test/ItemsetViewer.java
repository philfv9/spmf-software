package ca.pfv.spmf.experimental.itemsetvisualizer_test;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;

import ca.pfv.spmf.gui.visual_pattern_viewer.itemsets.Itemset;
import ca.pfv.spmf.gui.visual_pattern_viewer.itemsets.ItemsetsReader;

/**
 * Main viewer for itemset visualization with multiple view types.
 */
public class ItemsetViewer extends JFrame {
    
    private static final String WINDOW_TITLE = "SPMF - Itemset Visualizer";
    
    // View types
    private enum ViewType {
        TABLE("Table View", true),
        GRAPH("Graph View", false),
        MATRIX("Matrix View", false);
        
        final String displayName;
        final boolean usesSorting;
        
        ViewType(String displayName, boolean usesSorting) {
            this.displayName = displayName;
            this.usesSorting = usesSorting;
        }
    }
    
    // Data
    private List<Itemset> allItemsets = new ArrayList<>();
    private List<Itemset> filteredItemsets = new ArrayList<>();
    private Itemset selectedItemset;
    
    // UI Components
    private JTextField pathField;
    private JButton loadButton;
    private JLabel statusLabel;
    private JComboBox<ViewType> viewSelector;
    
    // View panels
    private ItemsetTablePanel tablePanel;
    private ItemsetGraphPanel graphPanel;
    private ItemsetTreePanel matrixPanel;
    
    private JPanel viewContainer;
    private CardLayout viewCardLayout;
    
    // Filter/Sort controls
    private JPanel filterPanel;
    private JPanel sortPanel;
    private JTextField filterField;
    private JComboBox<String> sortComboBox;
    private JCheckBox highlightSubsetsCheckbox;
    private JCheckBox highlightSupersetsCheckbox;
    
    public ItemsetViewer() {
        setTitle(WINDOW_TITLE);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);
        
        initializeComponents();
        layoutComponents();
        setupListeners();
        
        updateViewVisibility();
    }
    
    private void initializeComponents() {
        // File selection
        pathField = new JTextField();
        pathField.setEditable(false);
        loadButton = new JButton("Load File");
        
        // Status
        statusLabel = new JLabel("No file loaded");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        
        // View selector
        viewSelector = new JComboBox<>(ViewType.values());
        viewSelector.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, 
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof ViewType) {
                    setText(((ViewType) value).displayName);
                }
                return this;
            }
        });
        
        // View panels
        tablePanel = new ItemsetTablePanel();
        graphPanel = new ItemsetGraphPanel();
        matrixPanel = new ItemsetTreePanel();
        
        viewCardLayout = new CardLayout();
        viewContainer = new JPanel(viewCardLayout);
        
        viewContainer.add(new JScrollPane(tablePanel), ViewType.TABLE.name());
        viewContainer.add(graphPanel, ViewType.GRAPH.name());
        viewContainer.add(matrixPanel, ViewType.MATRIX.name());
        
        // Filter controls
        filterField = new JTextField(20);
        filterField.setToolTipText("Filter itemsets by item name");
        
        highlightSubsetsCheckbox = new JCheckBox("Highlight Subsets", false);
        highlightSupersetsCheckbox = new JCheckBox("Highlight Supersets", false);
        
        // Sort controls
        sortComboBox = new JComboBox<>(new String[]{
            "Size (Ascending)",
            "Size (Descending)",
            "Alphabetical",
            "Support (if available)"
        });
    }
    
    private void layoutComponents() {
        setLayout(new BorderLayout(10, 10));
        
        // Top panel - File selection
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        
        JPanel filePanel = new JPanel(new BorderLayout(10, 0));
        JLabel fileLabel = new JLabel("File:");
        filePanel.add(fileLabel, BorderLayout.WEST);
        filePanel.add(pathField, BorderLayout.CENTER);
        filePanel.add(loadButton, BorderLayout.EAST);
        
        JPanel viewSelectorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        viewSelectorPanel.add(new JLabel("View:"));
        viewSelectorPanel.add(viewSelector);
        
        topPanel.add(filePanel, BorderLayout.CENTER);
        topPanel.add(viewSelectorPanel, BorderLayout.EAST);
        
        // Control panel - Filter and Sort
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
        controlPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 0, 1, Color.LIGHT_GRAY),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        
        // Filter section
        filterPanel = new JPanel();
        filterPanel.setLayout(new BoxLayout(filterPanel, BoxLayout.Y_AXIS));
        filterPanel.setBorder(BorderFactory.createTitledBorder("Filter"));
        
        JPanel filterInputPanel = new JPanel(new BorderLayout(5, 5));
        filterInputPanel.add(new JLabel("Search:"), BorderLayout.WEST);
        filterInputPanel.add(filterField, BorderLayout.CENTER);
        filterInputPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        
        JButton clearFilterButton = new JButton("Clear");
        clearFilterButton.addActionListener(e -> {
            filterField.setText("");
            applyFilter();
        });
        
        JPanel filterButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        filterButtonPanel.add(clearFilterButton);
        
        filterPanel.add(filterInputPanel);
        filterPanel.add(Box.createVerticalStrut(5));
        filterPanel.add(filterButtonPanel);
        filterPanel.add(Box.createVerticalStrut(10));
        filterPanel.add(highlightSubsetsCheckbox);
        filterPanel.add(highlightSupersetsCheckbox);
        
        // Sort section
        sortPanel = new JPanel();
        sortPanel.setLayout(new BoxLayout(sortPanel, BoxLayout.Y_AXIS));
        sortPanel.setBorder(BorderFactory.createTitledBorder("Sort"));
        
        JPanel sortComboPanel = new JPanel(new BorderLayout());
        sortComboPanel.add(new JLabel("Order:"), BorderLayout.WEST);
        sortComboPanel.add(sortComboBox, BorderLayout.CENTER);
        sortComboPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        
        sortPanel.add(sortComboPanel);
        
        // Add sections to control panel
        controlPanel.add(filterPanel);
        controlPanel.add(Box.createVerticalStrut(10));
        controlPanel.add(sortPanel);
        controlPanel.add(Box.createVerticalGlue());
        
        // Info panel
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setBorder(BorderFactory.createTitledBorder("Statistics"));
        
        JLabel infoLabel = new JLabel("No data loaded");
        infoPanel.add(infoLabel);
        
        controlPanel.add(infoPanel);
        
        // Bottom status bar
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
        statusPanel.add(statusLabel, BorderLayout.WEST);
        
        // Add all to frame
        add(topPanel, BorderLayout.NORTH);
        add(controlPanel, BorderLayout.WEST);
        add(viewContainer, BorderLayout.CENTER);
        add(statusPanel, BorderLayout.SOUTH);
    }
    
    private void setupListeners() {
        // Load button
        loadButton.addActionListener(e -> loadFile());
        
        // View selector
        viewSelector.addActionListener(e -> {
            ViewType selected = (ViewType) viewSelector.getSelectedItem();
            if (selected != null) {
                viewCardLayout.show(viewContainer, selected.name());
                updateViewVisibility();
                refreshCurrentView();
            }
        });
        
        // Filter field
        filterField.addActionListener(e -> applyFilter());
        filterField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
        });
        
        // Sort combo
        sortComboBox.addActionListener(e -> applySortAndRefresh());
        
        // Highlight checkboxes
        highlightSubsetsCheckbox.addActionListener(e -> refreshCurrentView());
        highlightSupersetsCheckbox.addActionListener(e -> refreshCurrentView());
        
        // Selection listeners
        tablePanel.setItemsetSelectionListener(this::onItemsetSelected);
        graphPanel.setItemsetSelectionListener(this::onItemsetSelected);
        matrixPanel.setItemsetSelectionListener(this::onItemsetSelected);
    }
    
    private void updateViewVisibility() {
        ViewType currentView = (ViewType) viewSelector.getSelectedItem();
        if (currentView != null) {
            sortPanel.setVisible(currentView.usesSorting);
        }
    }
    
    private void loadFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Itemsets File");
        chooser.setFileFilter(new FileNameExtensionFilter("Text files (*.txt)", "txt"));
        
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            pathField.setText(file.getAbsolutePath());
            
            try {
                loadItemsetsFromFile(file);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                    "Error loading file: " + ex.getMessage(),
                    "Load Error",
                    JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        }
    }
    
    private void loadItemsetsFromFile(File file) throws IOException {
        statusLabel.setText("Loading...");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        
        SwingWorker<List<Itemset>, Void> worker = new SwingWorker<List<Itemset>, Void>() {
            @Override
            protected List<Itemset> doInBackground() throws Exception {
                return new ItemsetsReader(file.getAbsolutePath()).getPatterns();
            }
            
            @Override
            protected void done() {
                try {
                    allItemsets = get();
                    filteredItemsets = new ArrayList<>(allItemsets);
                    selectedItemset = null;
                    
                    applySortAndRefresh();
                    
                    statusLabel.setText(String.format("Loaded %d itemsets from %s", 
                        allItemsets.size(), file.getName()));
                    
                } catch (Exception ex) {
                    statusLabel.setText("Error loading file");
                    JOptionPane.showMessageDialog(ItemsetViewer.this,
                        "Error: " + ex.getMessage(),
                        "Load Error",
                        JOptionPane.ERROR_MESSAGE);
                } finally {
                    setCursor(Cursor.getDefaultCursor());
                }
            }
        };
        
        worker.execute();
    }
    
    private void applyFilter() {
        String filterText = filterField.getText().trim().toLowerCase();
        
        if (filterText.isEmpty()) {
            filteredItemsets = new ArrayList<>(allItemsets);
        } else {
            filteredItemsets = new ArrayList<>();
            for (Itemset itemset : allItemsets) {
                for (String item : itemset.getItems()) {
                    if (item.toLowerCase().contains(filterText)) {
                        filteredItemsets.add(itemset);
                        break;
                    }
                }
            }
        }
        
        applySortAndRefresh();
        
        statusLabel.setText(String.format("Showing %d of %d itemsets", 
            filteredItemsets.size(), allItemsets.size()));
    }
    
    private void applySortAndRefresh() {
        ViewType currentView = (ViewType) viewSelector.getSelectedItem();
        
        // Only sort if current view uses sorting
        if (currentView != null && currentView.usesSorting) {
            applySort();
        }
        
        refreshCurrentView();
    }
    
    private void applySort() {
        String sortOption = (String) sortComboBox.getSelectedItem();
        
        if (sortOption == null) return;
        
        Comparator<Itemset> comparator = null;
        
        switch (sortOption) {
            case "Size (Ascending)":
                comparator = Comparator.comparingInt(i -> i.getItems().size());
                break;
            case "Size (Descending)":
                comparator = Comparator.comparingInt((Itemset i) -> i.getItems().size()).reversed();
                break;
            case "Alphabetical":
                comparator = Comparator.comparing(i -> String.join(",", i.getItems()));
                break;
            case "Support (if available)":
                comparator = (i1, i2) -> {
                    String sup1 = i1.getMeasures().getOrDefault("support", "0");
                    String sup2 = i2.getMeasures().getOrDefault("support", "0");
                    try {
                        return Double.compare(Double.parseDouble(sup2), Double.parseDouble(sup1));
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                };
                break;
        }
        
        if (comparator != null) {
            filteredItemsets.sort(comparator);
        }
    }
    
    private void refreshCurrentView() {
        ViewType currentView = (ViewType) viewSelector.getSelectedItem();
        if (currentView == null) return;
        
        boolean highlightSubs = highlightSubsetsCheckbox.isSelected();
        boolean highlightSuper = highlightSupersetsCheckbox.isSelected();
        
        switch (currentView) {
            case TABLE:
                tablePanel.setItemsets(filteredItemsets, selectedItemset);
                break;
            case GRAPH:
                graphPanel.setItemsets(filteredItemsets, selectedItemset, highlightSubs, highlightSuper);
                break;
            case MATRIX:
                matrixPanel.setItemsets(filteredItemsets, selectedItemset, highlightSubs, highlightSuper);
                break;
        }
    }
    
    private void onItemsetSelected(Itemset itemset) {
        selectedItemset = itemset;
        refreshCurrentView();
        
        if (itemset != null) {
            statusLabel.setText(String.format("Selected: {%s} (size: %d)", 
                String.join(", ", itemset.getItems()), 
                itemset.getItems().size()));
        }
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            ItemsetViewer viewer = new ItemsetViewer();
            viewer.setVisible(true);
        });
    }
}