package ca.pfv.spmf.experimental.itemsetvisualizer_test;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.*;

import ca.pfv.spmf.experimental.itemsetvisualizer_test.ItemsetGraphPanel.ItemsetSelectionListener;
import ca.pfv.spmf.gui.visual_pattern_viewer.itemsets.Itemset;

/**
 * Table panel for displaying itemsets in a tabular format.
 * Supports sorting, selection, and display of itemset measures.
 */
public class ItemsetTablePanel extends JPanel {
    
    private static final class Config {
        static final Font TABLE_FONT = new Font("SansSerif", Font.PLAIN, 12);
        static final Font HEADER_FONT = new Font("SansSerif", Font.BOLD, 12);
        static final int ROW_HEIGHT = 25;
        static final Color SELECTION_COLOR = new Color(184, 207, 229);
        static final Color ALTERNATE_ROW_COLOR = new Color(245, 245, 245);
        static final Color HOVER_COLOR = new Color(230, 240, 250);
    }
    
    private JTable table;
    private ItemsetTableModel tableModel;
    private List<Itemset> itemsets;
    private Itemset selectedItemset;
    private ItemsetSelectionListener selectionListener;
    
    public ItemsetTablePanel() {
        setLayout(new BorderLayout());
        initializeTable();
    }
    
    private void initializeTable() {
        tableModel = new ItemsetTableModel();
        table = new JTable(tableModel);
        
        // Appearance
        table.setFont(Config.TABLE_FONT);
        table.setRowHeight(Config.ROW_HEIGHT);
        table.setShowGrid(true);
        table.setGridColor(Color.LIGHT_GRAY);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        
        // Header
        JTableHeader header = table.getTableHeader();
        header.setFont(Config.HEADER_FONT);
        header.setReorderingAllowed(false);
        
        // Column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(50);  // #
        table.getColumnModel().getColumn(1).setPreferredWidth(300); // Items
        table.getColumnModel().getColumn(2).setPreferredWidth(80);  // Size
        table.getColumnModel().getColumn(3).setPreferredWidth(200); // Measures
        
        // Alternating row colors
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                
                if (isSelected) {
                    c.setBackground(Config.SELECTION_COLOR);
                    c.setForeground(Color.BLACK);
                } else {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : Config.ALTERNATE_ROW_COLOR);
                    c.setForeground(Color.BLACK);
                }
                
                return c;
            }
        });
        
        // Selection listener
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = table.getSelectedRow();
                if (selectedRow >= 0) {
                    int modelRow = table.convertRowIndexToModel(selectedRow);
                    if (modelRow >= 0 && modelRow < itemsets.size()) {
                        Itemset itemset = itemsets.get(modelRow);
                        if (!Objects.equals(itemset, selectedItemset)) {
                            selectedItemset = itemset;
                            if (selectionListener != null) {
                                selectionListener.onItemsetSelected(itemset);
                            }
                        }
                    }
                }
            }
        });
        
        // Double-click to view details
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = table.getSelectedRow();
                    if (row >= 0) {
                        int modelRow = table.convertRowIndexToModel(row);
                        if (modelRow >= 0 && modelRow < itemsets.size()) {
                            showItemsetDetails(itemsets.get(modelRow));
                        }
                    }
                }
            }
        });
        
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);
        
        // Info panel
        add(createInfoPanel(), BorderLayout.SOUTH);
    }
    
    private JPanel createInfoPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
        
        JLabel infoLabel = new JLabel("Double-click a row to view details");
        infoLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
        infoLabel.setForeground(Color.GRAY);
        panel.add(infoLabel);
        
        return panel;
    }
    
    public void setItemsets(List<Itemset> itemsets, Itemset selected) {
        this.itemsets = itemsets != null ? itemsets : new ArrayList<>();
        this.selectedItemset = selected;
        
        tableModel.setItemsets(this.itemsets);
        
        // Select the specified itemset
        if (selected != null) {
            for (int i = 0; i < this.itemsets.size(); i++) {
                if (this.itemsets.get(i).equals(selected)) {
                    int viewRow = table.convertRowIndexToView(i);
                    table.setRowSelectionInterval(viewRow, viewRow);
                    table.scrollRectToVisible(table.getCellRect(viewRow, 0, true));
                    break;
                }
            }
        }
    }
    
    public void setItemsetSelectionListener(ItemsetSelectionListener listener) {
        this.selectionListener = listener;
    }
    
    private void showItemsetDetails(Itemset itemset) {
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), 
                                     "Itemset Details", true);
        dialog.setLayout(new BorderLayout(10, 10));
        
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        // Items
        JPanel itemsPanel = new JPanel(new BorderLayout(5, 5));
        itemsPanel.setBorder(BorderFactory.createTitledBorder("Items"));
        
        JTextArea itemsArea = new JTextArea(3, 40);
        itemsArea.setText(String.join(", ", itemset.getItems()));
        itemsArea.setWrapStyleWord(true);
        itemsArea.setLineWrap(true);
        itemsArea.setEditable(false);
        itemsArea.setFont(new Font("SansSerif", Font.PLAIN, 12));
        itemsPanel.add(new JScrollPane(itemsArea), BorderLayout.CENTER);
        
        contentPanel.add(itemsPanel);
        contentPanel.add(Box.createVerticalStrut(10));
        
        // Basic info
        JPanel infoPanel = new JPanel(new GridLayout(0, 2, 10, 5));
        infoPanel.setBorder(BorderFactory.createTitledBorder("Information"));
        
        infoPanel.add(new JLabel("Size:"));
        infoPanel.add(new JLabel(String.valueOf(itemset.getItems().size())));
        
        contentPanel.add(infoPanel);
        
        // Measures
        Map<String, String> measures = itemset.getMeasures();
        if (!measures.isEmpty()) {
            contentPanel.add(Box.createVerticalStrut(10));
            
            JPanel measuresPanel = new JPanel(new BorderLayout());
            measuresPanel.setBorder(BorderFactory.createTitledBorder("Measures"));
            
            String[] columnNames = {"Measure", "Value"};
            Object[][] data = new Object[measures.size()][2];
            
            int i = 0;
            for (Map.Entry<String, String> entry : measures.entrySet()) {
                data[i][0] = entry.getKey();
                data[i][1] = entry.getValue();
                i++;
            }
            
            JTable measuresTable = new JTable(data, columnNames);
            measuresTable.setEnabled(false);
            measuresPanel.add(new JScrollPane(measuresTable), BorderLayout.CENTER);
            
            contentPanel.add(measuresPanel);
        }
        
        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dialog.dispose());
        buttonPanel.add(closeButton);
        
        dialog.add(contentPanel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }
    
    /**
     * Custom table model for itemsets.
     */
    private static class ItemsetTableModel extends AbstractTableModel {
        
        private static final String[] COLUMN_NAMES = {"#", "Items", "Size", "Measures"};
        private List<Itemset> itemsets = new ArrayList<>();
        
        public void setItemsets(List<Itemset> itemsets) {
            this.itemsets = itemsets != null ? itemsets : new ArrayList<>();
            fireTableDataChanged();
        }
        
        @Override
        public int getRowCount() {
            return itemsets.size();
        }
        
        @Override
        public int getColumnCount() {
            return COLUMN_NAMES.length;
        }
        
        @Override
        public String getColumnName(int column) {
            return COLUMN_NAMES[column];
        }
        
        @Override
        public Class<?> getColumnClass(int columnIndex) {
            switch (columnIndex) {
                case 0: return Integer.class;  // #
                case 1: return String.class;   // Items
                case 2: return Integer.class;  // Size
                case 3: return String.class;   // Measures
                default: return Object.class;
            }
        }
        
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (rowIndex < 0 || rowIndex >= itemsets.size()) {
                return null;
            }
            
            Itemset itemset = itemsets.get(rowIndex);
            
            switch (columnIndex) {
                case 0: // #
                    return rowIndex + 1;
                    
                case 1: // Items
                    return formatItems(itemset.getItems());
                    
                case 2: // Size
                    return itemset.getItems().size();
                    
                case 3: // Measures
                    return formatMeasures(itemset.getMeasures());
                    
                default:
                    return null;
            }
        }
        
        private String formatItems(List<String> items) {
            if (items.isEmpty()) {
                return "∅";
            }
            
            String joined = String.join(", ", items);
            
            // Truncate if too long
            if (joined.length() > 100) {
                return joined.substring(0, 97) + "...";
            }
            
            return joined;
        }
        
        private String formatMeasures(Map<String, String> measures) {
            if (measures.isEmpty()) {
                return "-";
            }
            
            StringBuilder sb = new StringBuilder();
            int count = 0;
            
            for (Map.Entry<String, String> entry : measures.entrySet()) {
                if (count > 0) {
                    sb.append(", ");
                }
                sb.append(entry.getKey()).append(": ").append(entry.getValue());
                count++;
                
                // Limit display
                if (count >= 3) {
                    if (measures.size() > 3) {
                        sb.append("...");
                    }
                    break;
                }
            }
            
            return sb.toString();
        }
        
        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }
    }
}