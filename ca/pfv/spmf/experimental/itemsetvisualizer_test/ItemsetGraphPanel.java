package ca.pfv.spmf.experimental.itemsetvisualizer_test;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.List;
import javax.swing.*;

import ca.pfv.spmf.gui.visual_pattern_viewer.itemsets.Itemset;

/**
 * Interactive graph panel for visualizing itemsets as a network with hierarchical layout.
 */
public class ItemsetGraphPanel extends JPanel {
    
    // Configuration constants
    private static final class Config {
        static final int LEVEL_HEIGHT = 120;
        static final int MIN_HORIZONTAL_SPACING = 50;
        static final int NODE_PADDING_X = 15;
        static final int NODE_PADDING_Y = 10;
        static final int MIN_NODE_WIDTH = 100;
        static final int MIN_NODE_HEIGHT = 50;
        static final int MAX_NODE_WIDTH = 350;
        static final Font NODE_FONT = new Font("SansSerif", Font.PLAIN, 13);
        static final Font NODE_FONT_BOLD = new Font("SansSerif", Font.BOLD, 13);
        static final double MIN_ZOOM = 0.3;
        static final double MAX_ZOOM = 3.0;
        static final double ZOOM_FACTOR = 1.1;
        static final double LAYOUT_PADDING = 100.0;
        static final double RESET_VIEW_MAX_ZOOM = 1.2;
    }
    
    // Color scheme
    private static final class ColorScheme {
        static final Color NODE_COLOR = new Color(100, 149, 237);
        static final Color NODE_HOVER_COLOR = new Color(120, 169, 255);
        static final Color SELECTED_COLOR = new Color(255, 69, 0);
        static final Color SUBSET_COLOR = new Color(50, 205, 50);
        static final Color SUPERSET_COLOR = new Color(255, 215, 0);
        static final Color EDGE_COLOR = new Color(150, 150, 150, 80);
        static final Color EDGE_HIGHLIGHT_COLOR = new Color(255, 100, 0, 150);
        static final Color TEXT_COLOR = Color.WHITE;
        static final Color BORDER_COLOR = new Color(50, 50, 50);
        static final Color BACKGROUND_COLOR = new Color(250, 250, 250);
        static final Color LEVEL_SEPARATOR_COLOR = new Color(200, 200, 200);
        static final Color SHADOW_COLOR = new Color(0, 0, 0, 30);
        static final Color BADGE_BACKGROUND = new Color(0, 0, 0, 100);
        static final Color INSTRUCTION_TEXT = new Color(100, 100, 100);
    }
    
    // Node representation
    private static class Node {
        final Itemset itemset;
        double x, y;
        Rectangle2D bounds;
        String displayText;
        final int level;
        
        Node(Itemset itemset, double x, double y, int level) {
            this.itemset = Objects.requireNonNull(itemset, "Itemset cannot be null");
            this.x = x;
            this.y = y;
            this.level = level;
            updateDisplayText();
        }
        
        void updateDisplayText() {
            List<String> items = itemset.getItems();
            displayText = items.isEmpty() ? "∅" : String.join(", ", items);
        }
        
        void updateBounds(int nodeWidth, int nodeHeight) {
            bounds = new Rectangle2D.Double(
                x - nodeWidth / 2.0,
                y - nodeHeight / 2.0,
                nodeWidth,
                nodeHeight
            );
        }
    }
    
    // State
    private List<Itemset> itemsets;
    private Itemset selectedItemset;
    private Itemset hoveredItemset;
    private boolean highlightSubsets;
    private boolean highlightSupersets;
    
    private final List<Node> nodes = new ArrayList<>();
    private final Map<Itemset, Node> itemsetToNode = new HashMap<>();
    
    // View transformation
    private double viewX = 0;
    private double viewY = 0;
    private double zoom = 1.0;
    
    // Mouse interaction
    private Point lastMousePos;
    private boolean isPanning = false;
    private Node draggedNode = null;
    private boolean layoutInitialized = false;
    
    private ItemsetSelectionListener selectionListener;
    
    public ItemsetGraphPanel() {
        setBackground(ColorScheme.BACKGROUND_COLOR);
        setPreferredSize(new Dimension(800, 600));
        setupMouseListeners();
        ToolTipManager.sharedInstance().registerComponent(this);
        
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (!layoutInitialized && !nodes.isEmpty()) {
                    SwingUtilities.invokeLater(() -> resetView());
                }
            }
        });
    }
    
    public void setItemsets(List<Itemset> itemsets, Itemset selected, 
                           boolean highlightSub, boolean highlightSuper) {
        this.itemsets = itemsets;
        this.selectedItemset = selected;
        this.highlightSubsets = highlightSub;
        this.highlightSupersets = highlightSuper;
        
        layoutInitialized = false;
        computeHierarchicalLayout();
        
        SwingUtilities.invokeLater(() -> {
            resetView();
            layoutInitialized = true;
            repaint();
        });
    }
    
    public void setItemsetSelectionListener(ItemsetSelectionListener listener) {
        this.selectionListener = listener;
    }
    
    private void setupMouseListeners() {
        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleMousePressed(e);
            }
            
            @Override
            public void mouseDragged(MouseEvent e) {
                handleMouseDragged(e);
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                handleMouseReleased(e);
            }
            
            @Override
            public void mouseMoved(MouseEvent e) {
                handleMouseMoved(e);
            }
            
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                handleMouseWheel(e);
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                hoveredItemset = null;
                repaint();
            }
        };
        
        addMouseListener(adapter);
        addMouseMotionListener(adapter);
        addMouseWheelListener(adapter);
    }
    
    private void handleMousePressed(MouseEvent e) {
        lastMousePos = e.getPoint();
        isPanning = false;
        draggedNode = null;
        
        Point2D worldPoint = screenToWorld(e.getPoint());
        Node clickedNode = getNodeAtPoint(worldPoint);
        
        if (clickedNode != null) {
            if (SwingUtilities.isLeftMouseButton(e)) {
                if (selectionListener != null) {
                    selectionListener.onItemsetSelected(clickedNode.itemset);
                }
                
                if (e.isShiftDown()) {
                    draggedNode = clickedNode;
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                }
            }
        } else {
            isPanning = true;
            setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        }
    }
    
    private void handleMouseDragged(MouseEvent e) {
        if (lastMousePos == null) return;
        
        int dx = e.getX() - lastMousePos.x;
        int dy = e.getY() - lastMousePos.y;
        
        if (Math.abs(dx) < 2 && Math.abs(dy) < 2) return;
        
        if (draggedNode != null) {
            Point2D worldDelta = screenToWorldDelta(dx, dy);
            draggedNode.x += worldDelta.getX();
            draggedNode.y += worldDelta.getY();
            repaint();
        } else if (isPanning) {
            viewX += dx;
            viewY += dy;
            repaint();
        }
        
        lastMousePos = e.getPoint();
    }
    
    private void handleMouseReleased(MouseEvent e) {
        isPanning = false;
        draggedNode = null;
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        lastMousePos = null;
    }
    
    private void handleMouseMoved(MouseEvent e) {
        Point2D worldPoint = screenToWorld(e.getPoint());
        Node node = getNodeAtPoint(worldPoint);
        
        Itemset newHovered = node != null ? node.itemset : null;
        if (!Objects.equals(newHovered, hoveredItemset)) {
            hoveredItemset = newHovered;
            repaint();
        }
        
        setCursor(Cursor.getPredefinedCursor(
            node != null ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR
        ));
    }
    
    private void handleMouseWheel(MouseWheelEvent e) {
        Point mousePos = e.getPoint();
        Point2D worldBeforeZoom = screenToWorld(mousePos);
        
        double zoomFactor = Math.pow(Config.ZOOM_FACTOR, -e.getPreciseWheelRotation());
        zoom = clamp(zoom * zoomFactor, Config.MIN_ZOOM, Config.MAX_ZOOM);
        
        Point2D worldAfterZoom = screenToWorld(mousePos);
        viewX += (worldAfterZoom.getX() - worldBeforeZoom.getX()) * zoom;
        viewY += (worldAfterZoom.getY() - worldBeforeZoom.getY()) * zoom;
        
        repaint();
    }
    
    private void computeHierarchicalLayout() {
        if (itemsets == null || itemsets.isEmpty()) return;
        
        nodes.clear();
        itemsetToNode.clear();
        
        Map<Integer, List<Itemset>> levelMap = groupByLevel();
        int[] levelRange = getLevelRange(levelMap);
        sortLevelsAlphabetically(levelMap);
        
        double maxLevelWidth = calculateMaxLevelWidth(levelMap);
        positionNodes(levelMap, levelRange[0], levelRange[1], maxLevelWidth);
    }
    
    private Map<Integer, List<Itemset>> groupByLevel() {
        Map<Integer, List<Itemset>> levelMap = new HashMap<>();
        for (Itemset itemset : itemsets) {
            int level = itemset.getItems().size();
            levelMap.computeIfAbsent(level, k -> new ArrayList<>()).add(itemset);
        }
        return levelMap;
    }
    
    private int[] getLevelRange(Map<Integer, List<Itemset>> levelMap) {
        int minLevel = Integer.MAX_VALUE;
        int maxLevel = Integer.MIN_VALUE;
        for (int level : levelMap.keySet()) {
            minLevel = Math.min(minLevel, level);
            maxLevel = Math.max(maxLevel, level);
        }
        return new int[]{minLevel, maxLevel};
    }
    
    private void sortLevelsAlphabetically(Map<Integer, List<Itemset>> levelMap) {
        for (List<Itemset> levelItems : levelMap.values()) {
            levelItems.sort(Comparator.comparing(i -> String.join("", i.getItems())));
        }
    }
    
    private double calculateMaxLevelWidth(Map<Integer, List<Itemset>> levelMap) {
        double maxWidth = 0;
        for (List<Itemset> levelItems : levelMap.values()) {
            if (levelItems != null && !levelItems.isEmpty()) {
                double width = levelItems.size() * (Config.MIN_NODE_WIDTH + Config.MIN_HORIZONTAL_SPACING);
                maxWidth = Math.max(maxWidth, width);
            }
        }
        return maxWidth;
    }
    
    private void positionNodes(Map<Integer, List<Itemset>> levelMap, 
                              int minLevel, int maxLevel, double maxLevelWidth) {
        double startY = Config.LAYOUT_PADDING;
        
        for (int level = maxLevel; level >= minLevel; level--) {
            List<Itemset> levelItems = levelMap.get(level);
            if (levelItems == null || levelItems.isEmpty()) continue;
            
            double y = startY + (maxLevel - level) * Config.LEVEL_HEIGHT;
            double levelWidth = levelItems.size() * (Config.MIN_NODE_WIDTH + Config.MIN_HORIZONTAL_SPACING);
            double startX = (maxLevelWidth - levelWidth) / 2 + Config.LAYOUT_PADDING;
            
            double currentX = startX;
            for (Itemset itemset : levelItems) {
                Node node = new Node(itemset, currentX, y, level);
                nodes.add(node);
                itemsetToNode.put(itemset, node);
                currentX += Config.MIN_NODE_WIDTH + Config.MIN_HORIZONTAL_SPACING;
            }
        }
    }
    
    private Point2D screenToWorld(Point screenPoint) {
        return new Point2D.Double(
            (screenPoint.x - viewX) / zoom,
            (screenPoint.y - viewY) / zoom
        );
    }
    
    private Point2D screenToWorldDelta(double dx, double dy) {
        return new Point2D.Double(dx / zoom, dy / zoom);
    }
    
    private Point worldToScreen(double worldX, double worldY) {
        return new Point(
            (int)(worldX * zoom + viewX),
            (int)(worldY * zoom + viewY)
        );
    }
    
    private void resetView() {
        if (nodes.isEmpty() || getWidth() == 0 || getHeight() == 0) return;
        
        Rectangle2D bounds = calculateContentBounds();
        if (bounds.getWidth() == 0 || bounds.getHeight() == 0) {
            zoom = 1.0;
            viewX = 50;
            viewY = 50;
            return;
        }
        
        // Calculate zoom to fit
        double zoomX = getWidth() / bounds.getWidth();
        double zoomY = getHeight() / bounds.getHeight();
        zoom = clamp(Math.min(zoomX, zoomY), Config.MIN_ZOOM, Config.RESET_VIEW_MAX_ZOOM);
        
        // Center content
        double centerWorldX = bounds.getCenterX();
        double centerWorldY = bounds.getCenterY();
        viewX = getWidth() / 2.0 - centerWorldX * zoom;
        viewY = getHeight() / 2.0 - centerWorldY * zoom;
    }
    
    private Rectangle2D calculateContentBounds() {
        double minX = Double.MAX_VALUE, maxX = Double.MIN_VALUE;
        double minY = Double.MAX_VALUE, maxY = Double.MIN_VALUE;
        
        for (Node node : nodes) {
            minX = Math.min(minX, node.x - Config.MIN_NODE_WIDTH / 2.0);
            maxX = Math.max(maxX, node.x + Config.MIN_NODE_WIDTH / 2.0);
            minY = Math.min(minY, node.y - Config.MIN_NODE_HEIGHT / 2.0);
            maxY = Math.max(maxY, node.y + Config.MIN_NODE_HEIGHT / 2.0);
        }
        
        minX -= Config.LAYOUT_PADDING;
        minY -= Config.LAYOUT_PADDING;
        double width = maxX - minX + Config.LAYOUT_PADDING * 2;
        double height = maxY - minY + Config.LAYOUT_PADDING * 2;
        
        return new Rectangle2D.Double(minX, minY, width, height);
    }
    
    private Node getNodeAtPoint(Point2D worldPoint) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node node = nodes.get(i);
            if (node.bounds != null && node.bounds.contains(worldPoint)) {
                return node;
            }
        }
        return null;
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        if (itemsets == null || itemsets.isEmpty()) {
            drawEmptyState(g);
            return;
        }
        
        Graphics2D g2 = (Graphics2D) g;
        setupRenderingHints(g2);
        
        drawLevelSeparators(g2);
        drawEdges(g2);
        drawNodes(g2);
        drawInstructions(g2);
    }
    
    private void setupRenderingHints(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
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
    
    private void drawLevelSeparators(Graphics2D g2) {
        g2.setColor(ColorScheme.LEVEL_SEPARATOR_COLOR);
        g2.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 
                                     0, new float[]{5, 5}, 0));
        
        Set<Integer> drawnLevels = new HashSet<>();
        for (Node node : nodes) {
            if (drawnLevels.add(node.level)) {
                Point p = worldToScreen(0, node.y);
                g2.drawLine(0, p.y, getWidth(), p.y);
                
                g2.setFont(new Font("SansSerif", Font.BOLD, 11));
                g2.drawString("Size " + node.level, 10, p.y - 5);
            }
        }
    }
    
    private void drawEdges(Graphics2D g2) {
        g2.setStroke(new BasicStroke(2));
        Set<String> drawnEdges = new HashSet<>();
        
        for (Node n1 : nodes) {
            for (Node n2 : nodes) {
                if (n1 != n2 && isDirectSubset(n1.itemset, n2.itemset)) {
                    String edgeKey = getEdgeKey(n1, n2);
                    if (drawnEdges.add(edgeKey)) {
                        drawEdge(g2, n1, n2);
                    }
                }
            }
        }
    }
    
    private void drawEdge(Graphics2D g2, Node n1, Node n2) {
        Point p1 = worldToScreen(n1.x, n1.y);
        Point p2 = worldToScreen(n2.x, n2.y);
        
        boolean highlight = selectedItemset != null && 
                           (n1.itemset.equals(selectedItemset) || n2.itemset.equals(selectedItemset));
        
        g2.setColor(highlight ? ColorScheme.EDGE_HIGHLIGHT_COLOR : ColorScheme.EDGE_COLOR);
        g2.drawLine(p1.x, p1.y, p2.x, p2.y);
    }
    
    private String getEdgeKey(Node n1, Node n2) {
        int id1 = System.identityHashCode(n1);
        int id2 = System.identityHashCode(n2);
        return Math.min(id1, id2) + "-" + Math.max(id1, id2);
    }
    
    private void drawNodes(Graphics2D g2) {
        FontMetrics fm = g2.getFontMetrics(Config.NODE_FONT);
        
        // Draw non-selected nodes first
        for (Node node : nodes) {
            if (!node.itemset.equals(selectedItemset)) {
                drawNode(g2, node, fm);
            }
        }
        
        // Draw selected node on top
        if (selectedItemset != null) {
            Node selectedNode = itemsetToNode.get(selectedItemset);
            if (selectedNode != null) {
                drawNode(g2, selectedNode, fm);
            }
        }
    }
    
    private void drawNode(Graphics2D g2, Node node, FontMetrics fm) {
        Point screenPos = worldToScreen(node.x, node.y);
        
        NodeDimensions dims = calculateNodeDimensions(node, fm);
        node.updateBounds(dims.width, dims.height);
        
        Rectangle nodeRect = calculateNodeScreenRect(screenPos, dims);
        Color nodeColor = determineNodeColor(node);
        
        drawNodeShadow(g2, nodeRect);
        drawNodeBackground(g2, nodeRect, nodeColor);
        drawNodeBorder(g2, nodeRect, node);
        drawNodeText(g2, screenPos, dims.lines, node);
        drawSizeBadge(g2, nodeRect, node);
    }
    
    private static class NodeDimensions {
        int width;
        int height;
        List<String> lines;
        
        NodeDimensions(int width, int height, List<String> lines) {
            this.width = width;
            this.height = height;
            this.lines = lines;
        }
    }
    
    private NodeDimensions calculateNodeDimensions(Node node, FontMetrics fm) {
        int textWidth = fm.stringWidth(node.displayText);
        List<String> lines = new ArrayList<>();
        
        int width = Math.max(Config.MIN_NODE_WIDTH, 
                            Math.min(Config.MAX_NODE_WIDTH, textWidth + Config.NODE_PADDING_X * 2));
        int height = Config.MIN_NODE_HEIGHT;
        
        if (textWidth > Config.MAX_NODE_WIDTH - Config.NODE_PADDING_X * 2) {
            lines = splitIntoLines(node.displayText, fm);
            height = Config.MIN_NODE_HEIGHT + (lines.size() - 1) * (fm.getHeight() + 2);
            width = Config.MAX_NODE_WIDTH;
        } else {
            lines.add(node.displayText);
        }
        
        return new NodeDimensions(width, height, lines);
    }
    
    private List<String> splitIntoLines(String text, FontMetrics fm) {
        List<String> lines = new ArrayList<>();
        String[] items = text.split(", ");
        StringBuilder currentLine = new StringBuilder();
        int maxWidth = Config.MAX_NODE_WIDTH - Config.NODE_PADDING_X * 2;
        
        for (String item : items) {
            String testLine = currentLine.length() == 0 ? item : currentLine + ", " + item;
            if (fm.stringWidth(testLine) > maxWidth) {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder(item);
                } else {
                    lines.add(item);
                }
            } else {
                currentLine = new StringBuilder(testLine);
            }
        }
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        return lines;
    }
    
    private Rectangle calculateNodeScreenRect(Point screenPos, NodeDimensions dims) {
        int scaledWidth = (int)(dims.width * zoom);
        int scaledHeight = (int)(dims.height * zoom);
        return new Rectangle(
            screenPos.x - scaledWidth / 2,
            screenPos.y - scaledHeight / 2,
            scaledWidth,
            scaledHeight
        );
    }
    
    private Color determineNodeColor(Node node) {
        if (node.itemset.equals(selectedItemset)) {
            return ColorScheme.SELECTED_COLOR;
        }
        
        if (selectedItemset != null) {
            if (highlightSubsets && isSubset(node.itemset, selectedItemset)) {
                return ColorScheme.SUBSET_COLOR;
            }
            if (highlightSupersets && isSubset(selectedItemset, node.itemset)) {
                return ColorScheme.SUPERSET_COLOR;
            }
        }
        
        return node.itemset.equals(hoveredItemset) ? 
               ColorScheme.NODE_HOVER_COLOR : ColorScheme.NODE_COLOR;
    }
    
    private void drawNodeShadow(Graphics2D g2, Rectangle rect) {
        g2.setColor(ColorScheme.SHADOW_COLOR);
        g2.fillRoundRect(rect.x + 3, rect.y + 3, rect.width, rect.height, 10, 10);
    }
    
    private void drawNodeBackground(Graphics2D g2, Rectangle rect, Color color) {
        g2.setColor(color);
        g2.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 10, 10);
    }
    
    private void drawNodeBorder(Graphics2D g2, Rectangle rect, Node node) {
        g2.setColor(ColorScheme.BORDER_COLOR);
        g2.setStroke(new BasicStroke(node.itemset.equals(selectedItemset) ? 3 : 2));
        g2.drawRoundRect(rect.x, rect.y, rect.width, rect.height, 10, 10);
    }
    
    private void drawNodeText(Graphics2D g2, Point screenPos, List<String> lines, Node node) {
        g2.setColor(ColorScheme.TEXT_COLOR);
        Font font = node.itemset.equals(selectedItemset) ? Config.NODE_FONT_BOLD : Config.NODE_FONT;
        float fontSize = Math.max(8, (float)(font.getSize() * zoom));
        g2.setFont(font.deriveFont(fontSize));
        FontMetrics fm = g2.getFontMetrics();
        
        int textY = screenPos.y - (lines.size() - 1) * (fm.getHeight() + 2) / 2;
        for (String line : lines) {
            int lineWidth = fm.stringWidth(line);
            g2.drawString(line, screenPos.x - lineWidth / 2, textY + fm.getAscent() / 2);
            textY += fm.getHeight() + 2;
        }
    }
    
    private void drawSizeBadge(Graphics2D g2, Rectangle rect, Node node) {
        String sizeLabel = String.valueOf(node.itemset.getItems().size());
        float badgeFontSize = Math.max(7, (float)(10 * zoom));
        g2.setFont(new Font("SansSerif", Font.BOLD, (int)badgeFontSize));
        FontMetrics fm = g2.getFontMetrics();
        
        int badgeWidth = fm.stringWidth(sizeLabel) + (int)(8 * zoom);
        int badgeHeight = fm.getHeight() + (int)(2 * zoom);
        int badgeX = rect.x + rect.width - badgeWidth - (int)(3 * zoom);
        int badgeY = rect.y + (int)(3 * zoom);
        
        g2.setColor(ColorScheme.BADGE_BACKGROUND);
        g2.fillRoundRect(badgeX, badgeY, badgeWidth, badgeHeight, 5, 5);
        g2.setColor(Color.WHITE);
        g2.drawString(sizeLabel, badgeX + (int)(4 * zoom), badgeY + fm.getAscent() + 1);
    }
    
    private void drawInstructions(Graphics2D g2) {
        g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g2.setColor(ColorScheme.INSTRUCTION_TEXT);
        
        String instructions = "Click: Select | Shift+Drag: Move node | Drag: Pan | Scroll: Zoom";
        g2.drawString(instructions, 10, getHeight() - 10);
        
        String zoomText = String.format("Zoom: %.0f%%", zoom * 100);
        g2.drawString(zoomText, getWidth() - 100, getHeight() - 10);
    }
    
    @Override
    public String getToolTipText(MouseEvent e) {
        Point2D worldPoint = screenToWorld(e.getPoint());
        Node node = getNodeAtPoint(worldPoint);
        
        if (node != null) {
            return buildTooltip(node);
        }
        return null;
    }
    
    private String buildTooltip(Node node) {
        StringBuilder tooltip = new StringBuilder("<html>");
        tooltip.append("<b>Itemset:</b> ").append(String.join(", ", node.itemset.getItems()));
        tooltip.append("<br><b>Size:</b> ").append(node.itemset.getItems().size());
        
        Map<String, String> measures = node.itemset.getMeasures();
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
    
    private boolean isDirectSubset(Itemset smaller, Itemset larger) {
        return isSubset(smaller, larger) && 
               smaller.getItems().size() == larger.getItems().size() - 1;
    }
    
    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
    
    public interface ItemsetSelectionListener {
        void onItemsetSelected(Itemset itemset);
    }
}