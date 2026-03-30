package ca.pfv.spmf.experimental.itemsetvisualizer_test;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import javax.swing.*;

import ca.pfv.spmf.experimental.itemsetvisualizer_test.ItemsetGraphPanel.ItemsetSelectionListener;
import ca.pfv.spmf.gui.visual_pattern_viewer.itemsets.Itemset;

/**
 * Matrix/Grid visualization for itemsets.
 * Rows = Itemsets, Columns = Items, Cells show presence/absence.
 */
public class ItemsetTreePanel extends JPanel {
    
    private static final class Config {
        static final int CELL_SIZE = 30;
        static final int MIN_CELL_SIZE = 20;
        static final int MAX_CELL_SIZE = 50;
        static final int HEADER_HEIGHT = 100;
        static final int ROW_LABEL_WIDTH = 200;
        static final int SCROLL_SPEED = 16;
        static final Font HEADER_FONT = new Font("SansSerif", Font.BOLD, 11);
        static final Font ROW_FONT = new Font("SansSerif", Font.PLAIN, 11);
        static final Font CELL_FONT = new Font("SansSerif", Font.BOLD, 10);
        static final Font STATS_FONT = new Font("SansSerif", Font.PLAIN, 10);
    }
    
    private static final class ColorScheme {
        static final Color CELL_PRESENT = new Color(46, 134, 193);
        static final Color CELL_ABSENT = new Color(240, 240, 240);
        static final Color CELL_SELECTED = new Color(231, 76, 60);
        static final Color CELL_SUBSET = new Color(46, 204, 113);
        static final Color CELL_SUPERSET = new Color(241, 196, 15);
        static final Color CELL_HOVER = new Color(52, 152, 219);
        static final Color GRID_LINE = new Color(200, 200, 200);
        static final Color HEADER_BG = new Color(52, 73, 94);
        static final Color ROW_LABEL_BG = new Color(236, 240, 241);
        static final Color ROW_SELECTED_BG = new Color(255, 235, 230);
        static final Color ROW_HOVER_BG = new Color(245, 245, 245);
        static final Color TEXT_DARK = new Color(44, 62, 80);
        static final Color TEXT_LIGHT = Color.WHITE;
        static final Color BACKGROUND = Color.WHITE;
    }
    
    private List<Itemset> itemsets;
    private List<String> allItems;
    private Itemset selectedItemset;
    private Itemset hoveredItemset;
    private boolean highlightSubsets;
    private boolean highlightSupersets;
    
    private final Map<Itemset, Integer> itemsetToRow = new HashMap<>();
    
    private int cellSize = Config.CELL_SIZE;
    private int offsetX = 0;
    private int offsetY = 0;
    private Point lastMousePos;
    private boolean isDragging = false;
    
    private ItemsetSelectionListener selectionListener;
    
    public ItemsetTreePanel() {
        setBackground(ColorScheme.BACKGROUND);
        setPreferredSize(new Dimension(800, 600));
        
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                lastMousePos = e.getPoint();
                
                // Don't drag if clicking on headers
                if (e.getX() < Config.ROW_LABEL_WIDTH || e.getY() < Config.HEADER_HEIGHT) {
                    int row = getRowAtPoint(e.getPoint());
                    if (row >= 0 && row < itemsets.size()) {
                        Itemset clicked = itemsets.get(row);
                        if (selectionListener != null) {
                            selectionListener.onItemsetSelected(clicked);
                        }
                    }
                    return;
                }
                
                isDragging = true;
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                isDragging = false;
                setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            }
            
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = getRowAtPoint(e.getPoint());
                if (row >= 0 && row < itemsets.size()) {
                    Itemset clicked = itemsets.get(row);
                    if (selectionListener != null) {
                        selectionListener.onItemsetSelected(clicked);
                    }
                }
            }
        });
        
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int row = getRowAtPoint(e.getPoint());
                Itemset newHovered = (row >= 0 && row < itemsets.size()) ? itemsets.get(row) : null;
                
                if (!Objects.equals(newHovered, hoveredItemset)) {
                    hoveredItemset = newHovered;
                    repaint();
                }
                
                boolean overGrid = e.getX() >= Config.ROW_LABEL_WIDTH && e.getY() >= Config.HEADER_HEIGHT;
                setCursor(Cursor.getPredefinedCursor(
                    newHovered != null && !overGrid ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR
                ));
            }
            
            @Override
            public void mouseDragged(MouseEvent e) {
                if (isDragging && lastMousePos != null) {
                    int dx = e.getX() - lastMousePos.x;
                    int dy = e.getY() - lastMousePos.y;
                    
                    offsetX += dx;
                    offsetY += dy;
                    
                    // Clamp scrolling
                    clampOffsets();
                    
                    lastMousePos = e.getPoint();
                    repaint();
                }
            }
        });
        
        addMouseWheelListener(e -> {
            if (e.isControlDown()) {
                // Zoom
                int delta = -e.getWheelRotation();
                int oldCellSize = cellSize;
                cellSize = Math.max(Config.MIN_CELL_SIZE, 
                           Math.min(Config.MAX_CELL_SIZE, cellSize + delta * 2));
                
                if (oldCellSize != cellSize) {
                    // Adjust offsets to zoom towards mouse
                    double scale = (double) cellSize / oldCellSize;
                    int mouseX = e.getX() - Config.ROW_LABEL_WIDTH;
                    int mouseY = e.getY() - Config.HEADER_HEIGHT;
                    
                    offsetX = (int)((offsetX - mouseX) * scale + mouseX);
                    offsetY = (int)((offsetY - mouseY) * scale + mouseY);
                    
                    clampOffsets();
                    repaint();
                }
            } else if (e.isShiftDown()) {
                // Horizontal scroll
                offsetX -= e.getWheelRotation() * Config.SCROLL_SPEED;
                clampOffsets();
                repaint();
            } else {
                // Vertical scroll
                offsetY -= e.getWheelRotation() * Config.SCROLL_SPEED;
                clampOffsets();
                repaint();
            }
        });
        
        ToolTipManager.sharedInstance().registerComponent(this);
    }
    
    private void clampOffsets() {
        if (itemsets == null || allItems == null) return;
        
        int maxWidth = allItems.size() * cellSize;
        int maxHeight = itemsets.size() * cellSize;
        
        int viewWidth = getWidth() - Config.ROW_LABEL_WIDTH;
        int viewHeight = getHeight() - Config.HEADER_HEIGHT;
        
        // Allow scrolling but keep at least part of grid visible
        offsetX = Math.max(-(maxWidth - 50), Math.min(viewWidth - 50, offsetX));
        offsetY = Math.max(-(maxHeight - 50), Math.min(viewHeight - 50, offsetY));
    }
    
    public void setItemsets(List<Itemset> itemsets, Itemset selected,
                           boolean highlightSub, boolean highlightSuper) {
        this.itemsets = itemsets;
        this.selectedItemset = selected;
        this.highlightSubsets = highlightSub;
        this.highlightSupersets = highlightSuper;
        
        extractAllItems();
        buildRowMapping();
        
        // Reset view
        offsetX = 0;
        offsetY = 0;
        
        // Scroll to selected item
        if (selected != null) {
            scrollToItemset(selected);
        }
        
        repaint();
    }
    
    public void setItemsetSelectionListener(ItemsetSelectionListener listener) {
        this.selectionListener = listener;
    }
    
    private void extractAllItems() {
        if (itemsets == null || itemsets.isEmpty()) {
            allItems = new ArrayList<>();
            return;
        }
        
        Set<String> itemSet = new TreeSet<>();
        for (Itemset itemset : itemsets) {
            itemSet.addAll(itemset.getItems());
        }
        allItems = new ArrayList<>(itemSet);
    }
    
    private void buildRowMapping() {
        itemsetToRow.clear();
        
        if (itemsets == null || itemsets.isEmpty()) return;
        
        // Sort itemsets by size and then alphabetically
        List<Itemset> sortedItemsets = new ArrayList<>(itemsets);
        sortedItemsets.sort((a, b) -> {
            int sizeCompare = Integer.compare(a.getItems().size(), b.getItems().size());
            if (sizeCompare != 0) return sizeCompare;
            return String.join(",", a.getItems()).compareTo(String.join(",", b.getItems()));
        });
        this.itemsets = sortedItemsets;
        
        for (int i = 0; i < itemsets.size(); i++) {
            itemsetToRow.put(itemsets.get(i), i);
        }
    }
    
    private void scrollToItemset(Itemset itemset) {
        Integer row = itemsetToRow.get(itemset);
        if (row != null) {
            int targetY = -row * cellSize + (getHeight() - Config.HEADER_HEIGHT) / 2;
            offsetY = targetY;
            clampOffsets();
        }
    }
    
    private int getRowAtPoint(Point p) {
        if (p.x > Config.ROW_LABEL_WIDTH) return -1;
        
        int y = p.y - Config.HEADER_HEIGHT - offsetY;
        if (y < 0) return -1;
        
        int row = y / cellSize;
        return (row >= 0 && row < itemsets.size()) ? row : -1;
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        if (itemsets == null || itemsets.isEmpty()) {
            drawEmptyState(g);
            return;
        }
        
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        
        // Clip the grid area
        Shape oldClip = g2.getClip();
        g2.setClip(Config.ROW_LABEL_WIDTH, Config.HEADER_HEIGHT, 
                   getWidth() - Config.ROW_LABEL_WIDTH, 
                   getHeight() - Config.HEADER_HEIGHT);
        
        drawGrid(g2);
        drawCells(g2);
        drawGridLines(g2);
        
        g2.setClip(oldClip);
        
        // Draw fixed headers/labels on top
        drawHeaders(g2);
        drawRowLabels(g2);
        drawStats(g2);
        drawInstructions(g2);
    }
    
    private void drawEmptyState(Graphics g) {
        g.setColor(Color.GRAY);
        g.setFont(new Font("SansSerif", Font.PLAIN, 16));
        String msg = "No itemsets to display";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(msg, 
            (getWidth() - fm.stringWidth(msg)) / 2, 
            getHeight() / 2);
    }
    
    private void drawGrid(Graphics2D g2) {
        g2.setColor(Color.WHITE);
        g2.fillRect(Config.ROW_LABEL_WIDTH, Config.HEADER_HEIGHT, 
                   getWidth() - Config.ROW_LABEL_WIDTH, 
                   getHeight() - Config.HEADER_HEIGHT);
    }
    
    private void drawCells(Graphics2D g2) {
        int startCol = Math.max(0, -offsetX / cellSize - 1);
        int endCol = Math.min(allItems.size(), (-offsetX + getWidth()) / cellSize + 1);
        int startRow = Math.max(0, -offsetY / cellSize - 1);
        int endRow = Math.min(itemsets.size(), (-offsetY + getHeight()) / cellSize + 1);
        
        for (int row = startRow; row < endRow; row++) {
            Itemset itemset = itemsets.get(row);
            Set<String> items = new HashSet<>(itemset.getItems());
            
            for (int col = startCol; col < endCol; col++) {
                String item = allItems.get(col);
                boolean present = items.contains(item);
                
                int x = Config.ROW_LABEL_WIDTH + col * cellSize + offsetX;
                int y = Config.HEADER_HEIGHT + row * cellSize + offsetY;
                
                Color cellColor = determineCellColor(present, itemset);
                
                g2.setColor(cellColor);
                g2.fillRect(x, y, cellSize, cellSize);
                
                if (present) {
                    g2.setColor(ColorScheme.TEXT_LIGHT);
                    g2.setFont(Config.CELL_FONT);
                    String mark = "✓";
                    FontMetrics fm = g2.getFontMetrics();
                    int textX = x + (cellSize - fm.stringWidth(mark)) / 2;
                    int textY = y + (cellSize + fm.getAscent()) / 2 - 2;
                    g2.drawString(mark, textX, textY);
                }
            }
        }
    }
    
    private Color determineCellColor(boolean present, Itemset itemset) {
        if (!present) {
            return ColorScheme.CELL_ABSENT;
        }
        
        if (itemset.equals(selectedItemset)) {
            return ColorScheme.CELL_SELECTED;
        }
        
        if (itemset.equals(hoveredItemset)) {
            return ColorScheme.CELL_HOVER;
        }
        
        if (selectedItemset != null) {
            if (highlightSubsets && isSubset(itemset, selectedItemset)) {
                return ColorScheme.CELL_SUBSET;
            }
            if (highlightSupersets && isSubset(selectedItemset, itemset)) {
                return ColorScheme.CELL_SUPERSET;
            }
        }
        
        return ColorScheme.CELL_PRESENT;
    }
    
    private void drawGridLines(Graphics2D g2) {
        g2.setColor(ColorScheme.GRID_LINE);
        g2.setStroke(new BasicStroke(1));
        
        int startCol = Math.max(0, -offsetX / cellSize);
        int endCol = Math.min(allItems.size(), (-offsetX + getWidth()) / cellSize + 2);
        int startRow = Math.max(0, -offsetY / cellSize);
        int endRow = Math.min(itemsets.size(), (-offsetY + getHeight()) / cellSize + 2);
        
        // Vertical lines
        for (int col = startCol; col <= endCol; col++) {
            int x = Config.ROW_LABEL_WIDTH + col * cellSize + offsetX;
            g2.drawLine(x, Config.HEADER_HEIGHT, x, getHeight());
        }
        
        // Horizontal lines
        for (int row = startRow; row <= endRow; row++) {
            int y = Config.HEADER_HEIGHT + row * cellSize + offsetY;
            g2.drawLine(Config.ROW_LABEL_WIDTH, y, getWidth(), y);
        }
    }
    
    private void drawHeaders(Graphics2D g2) {
        // Header background
        g2.setColor(ColorScheme.HEADER_BG);
        g2.fillRect(0, 0, getWidth(), Config.HEADER_HEIGHT);
        
        // Column headers
        g2.setFont(Config.HEADER_FONT);
        
        int startCol = Math.max(0, -offsetX / cellSize);
        int endCol = Math.min(allItems.size(), (-offsetX + getWidth()) / cellSize + 1);
        
        for (int col = startCol; col < endCol; col++) {
            int x = Config.ROW_LABEL_WIDTH + col * cellSize + offsetX;
            
            if (x + cellSize < Config.ROW_LABEL_WIDTH || x > getWidth()) continue;
            
            String item = allItems.get(col);
            
            // Draw rotated text
            Graphics2D g2Rot = (Graphics2D) g2.create();
            g2Rot.setColor(ColorScheme.TEXT_LIGHT);
            g2Rot.translate(x + cellSize / 2, Config.HEADER_HEIGHT - 10);
            g2Rot.rotate(-Math.PI / 4);
            g2Rot.drawString(item, 0, 0);
            g2Rot.dispose();
        }
        
        // Separator line
        g2.setColor(ColorScheme.GRID_LINE);
        g2.setStroke(new BasicStroke(2));
        g2.drawLine(0, Config.HEADER_HEIGHT, getWidth(), Config.HEADER_HEIGHT);
    }
    
    private void drawRowLabels(Graphics2D g2) {
        // Background
        g2.setColor(ColorScheme.ROW_LABEL_BG);
        g2.fillRect(0, 0, Config.ROW_LABEL_WIDTH, getHeight());
        
        g2.setFont(Config.ROW_FONT);
        FontMetrics fm = g2.getFontMetrics();
        
        int startRow = Math.max(0, -offsetY / cellSize);
        int endRow = Math.min(itemsets.size(), (-offsetY + getHeight()) / cellSize + 1);
        
        for (int row = startRow; row < endRow; row++) {
            int y = Config.HEADER_HEIGHT + row * cellSize + offsetY;
            
            if (y + cellSize < Config.HEADER_HEIGHT || y > getHeight()) continue;
            
            Itemset itemset = itemsets.get(row);
            
            // Row background
            if (itemset.equals(selectedItemset)) {
                g2.setColor(ColorScheme.ROW_SELECTED_BG);
                g2.fillRect(0, y, Config.ROW_LABEL_WIDTH, cellSize);
            } else if (itemset.equals(hoveredItemset)) {
                g2.setColor(ColorScheme.ROW_HOVER_BG);
                g2.fillRect(0, y, Config.ROW_LABEL_WIDTH, cellSize);
            }
            
            // Label
            String label = createRowLabel(itemset);
            g2.setColor(ColorScheme.TEXT_DARK);
            int textY = y + (cellSize + fm.getAscent()) / 2 - 2;
            g2.drawString(label, 5, textY);
            
            // Size badge
            String sizeText = "[" + itemset.getItems().size() + "]";
            g2.setFont(Config.STATS_FONT);
            g2.setColor(new Color(100, 100, 100));
            g2.drawString(sizeText, Config.ROW_LABEL_WIDTH - 35, textY);
            g2.setFont(Config.ROW_FONT);
        }
        
        // Separator
        g2.setColor(ColorScheme.GRID_LINE);
        g2.setStroke(new BasicStroke(2));
        g2.drawLine(Config.ROW_LABEL_WIDTH, 0, Config.ROW_LABEL_WIDTH, getHeight());
    }
    
    private String createRowLabel(Itemset itemset) {
        String label = String.join(", ", itemset.getItems());
        int maxLen = 22;
        if (label.length() > maxLen) {
            return label.substring(0, maxLen - 3) + "...";
        }
        return label;
    }
    
    private void drawStats(Graphics2D g2) {
        g2.setFont(Config.STATS_FONT);
        g2.setColor(ColorScheme.TEXT_LIGHT);
        
        String stats = String.format("Items: %d | Itemsets: %d", allItems.size(), itemsets.size());
        g2.drawString(stats, 5, 15);
    }
    
    private void drawInstructions(Graphics2D g2) {
        g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g2.setColor(ColorScheme.TEXT_DARK);
        
        String instructions = "Click row to select | Drag grid to pan | Ctrl+Scroll to zoom | Scroll to navigate";
        g2.drawString(instructions, 5, getHeight() - 5);
    }
    
    @Override
    public String getToolTipText(MouseEvent e) {
        int row = getRowAtPoint(e.getPoint());
        
        if (row >= 0 && row < itemsets.size()) {
            Itemset itemset = itemsets.get(row);
            return buildTooltip(itemset);
        }
        
        return null;
    }
    
    private String buildTooltip(Itemset itemset) {
        StringBuilder tooltip = new StringBuilder("<html>");
        tooltip.append("<b>Itemset:</b> {").append(String.join(", ", itemset.getItems())).append("}");
        tooltip.append("<br><b>Size:</b> ").append(itemset.getItems().size());
        
        Map<String, String> measures = itemset.getMeasures();
        if (!measures.isEmpty()) {
            tooltip.append("<br><b>Measures:</b><br>");
            measures.forEach((key, value) -> 
                tooltip.append("&nbsp;&nbsp;").append(key)
                       .append(": ").append(value).append("<br>")
            );
        }
        tooltip.append("</html>");
        return tooltip.toString();
    }
    
    private boolean isSubset(Itemset smaller, Itemset larger) {
        if (smaller.getItems().size() >= larger.getItems().size()) {
            return false;
        }
        Set<String> largerSet = new HashSet<>(larger.getItems());
        return smaller.getItems().stream().allMatch(largerSet::contains);
    }
}