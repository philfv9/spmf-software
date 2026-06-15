package ca.pfv.spmf.gui.workflow_editor;

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
 * Do not remove copyright and license information.
 */

import java.awt.BasicStroke;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.Scrollable;
import javax.swing.ToolTipManager;

import ca.pfv.spmf.algorithmmanager.AlgorithmManager;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.gui.viewers.graphviewer.graphmodel.GEdge;
import ca.pfv.spmf.gui.viewers.graphviewer.graphmodel.GNode;

/**
 * Swing panel that renders the branching workflow tree and handles mouse interaction for WorkflowEditorWindow.
 *
 * @author Philippe Fournier-Viger
 */
class DrawPanel extends JPanel implements Scrollable {

    private static final long serialVersionUID = 1L;

    /** Vertical gap between nodes within the same group. */
    private static final int NODE_Y_GAP = 40;

    /** Vertical gap between a parent group and its children. */
    private static final int LEVEL_Y_GAP = 60;

    /** Horizontal padding added on each side of the widest label in a column. */
    private static final int NODE_H_PADDING = 30;

    /** Minimum column width in pixels. */
    private static final int MIN_COL_WIDTH = 120;

    /** Horizontal gap between sibling subtrees. */
    private static final int SIBLING_GAP = 20;

    /** Top margin before the first row of nodes. */
    private static final int TOP_MARGIN = 20;

    /** Left margin before the first column. */
    private static final int LEFT_MARGIN = 20;

    /** Right margin after the last column. */
    private static final int RIGHT_MARGIN = 20;

    /** Bottom margin below the last row of nodes. */
    private static final int BOTTOM_MARGIN = 40;

    /** Scroll unit increment in pixels. */
    private static final int SCROLL_UNIT = 20;

    /** Scroll block increment in pixels. */
    private static final int SCROLL_BLOCK = 100;

    /** Half-size of each arrowhead in pixels. */
    private static final int ARROWHEAD_SIZE = 5;

    /** Half-height of each node hit box in pixels. */
    private static final int HIT_HALF_H = 12;

    /** Font used for off-screen text measurement during layout. */
    private static final Font MEASURE_FONT = new Font("Dialog", Font.PLAIN, 12);

    /** Tooltip dismiss delay in milliseconds, kept long so the popup stays readable. */
    private static final int TOOLTIP_DISMISS_DELAY_MS = 8000;

    /** Tooltip initial delay in milliseconds before it first appears on hover. */
    private static final int TOOLTIP_INITIAL_DELAY_MS = 400;

    /** Off-screen canvas used solely to obtain a FontMetrics for layout measurement. */
    private final Canvas measureCanvas = new Canvas();

    /** The list of root branch nodes of the workflow tree. */
    List<BranchNode> roots = new ArrayList<>();

    /** The currently selected visual node. */
    Node selected;

    /** The branch node that owns the currently selected visual node. */
    BranchNode selectedBranchNode;

    /** Map from each visual node to its mouse-click hit box. */
    private final Map<Node, Rectangle> hitBoxes = new HashMap<>();

    /** Registered listeners that are notified of draw-panel events. */
    private final List<DrawPanelListener> listeners = new ArrayList<>();

    /**
     * Creates a new draw panel with a white background and default preferred size.
     * Enables Swing tooltip support so that mouseMoved can update the tooltip text dynamically.
     */
    DrawPanel() {
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(400, 300));

        // Enable Swing tooltips on this component and use fast dismiss/appearance
        ToolTipManager.sharedInstance().registerComponent(this);
        ToolTipManager.sharedInstance().setDismissDelay(TOOLTIP_DISMISS_DELAY_MS);
        ToolTipManager.sharedInstance().setInitialDelay(TOOLTIP_INITIAL_DELAY_MS);

        registerMouseEvents();
    }

    /**
     * Returns the preferred size of the viewport for this scrollable panel.
     *
     * @return the preferred size.
     */
    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    /**
     * Returns the unit scroll increment for the given scroll direction.
     *
     * @param r the visible rectangle.
     * @param o the scroll orientation.
     * @param d the scroll direction.
     * @return the unit increment in pixels.
     */
    @Override
    public int getScrollableUnitIncrement(Rectangle r, int o, int d) {
        return SCROLL_UNIT;
    }

    /**
     * Returns the block scroll increment for the given scroll direction.
     *
     * @param r the visible rectangle.
     * @param o the scroll orientation.
     * @param d the scroll direction.
     * @return the block increment in pixels.
     */
    @Override
    public int getScrollableBlockIncrement(Rectangle r, int o, int d) {
        return SCROLL_BLOCK;
    }

    /**
     * Returns false so that the panel is never forced to match the viewport width.
     *
     * @return false.
     */
    @Override
    public boolean getScrollableTracksViewportWidth() {
        return false;
    }

    /**
     * Returns false so that the panel is never forced to match the viewport height.
     *
     * @return false.
     */
    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }

    /**
     * Returns the tooltip text for the node currently under the mouse pointer, or null if none.
     * Called automatically by Swing's ToolTipManager when the mouse hovers over this component.
     *
     * @param event the mouse event carrying the current cursor position.
     * @return an HTML tooltip string, or null if the cursor is not over any node.
     */
    @Override
    public String getToolTipText(MouseEvent event) {
        Object[] hit = findHit(event.getX(), event.getY());
        Node hitNode = (Node) hit[0];
        BranchNode hitBranch = (BranchNode) hit[1];

        if (hitNode == null || hitBranch == null) {
            return null;
        }

        if (hitNode instanceof NodeAlgorithm) {
            return buildAlgorithmTooltip(hitBranch);
        }

        if (hitNode instanceof NodeFileInput) {
            NodeFileInput n = (NodeFileInput) hitNode;
            String path = (n.inputFile != null && !n.inputFile.isEmpty())
                    ? n.inputFile : "(not set)";
            return "<html><b>Input file</b><br>" + escapeHtml(path) + "</html>";
        }

        if (hitNode instanceof NodeFileOutput) {
            NodeFileOutput n = (NodeFileOutput) hitNode;
            String path = (n.outputFile != null && !n.outputFile.isEmpty())
                    ? n.outputFile : "(not set)";
            return "<html><b>Output file</b><br>" + escapeHtml(path) + "</html>";
        }

        return null;
    }

    /**
     * Builds an HTML tooltip string for the given algorithm branch node, showing
     * the algorithm name, input/output types, and parameter summary.
     *
     * @param bn the branch node whose algorithm tooltip should be built.
     * @return an HTML string suitable for use as a Swing tooltip.
     */
    private String buildAlgorithmTooltip(BranchNode bn) {
        String algName = bn.group.nodeAlgorithm.name;
        if (algName == null || algName.isEmpty() || algName.equals("Algorithm")) {
            return "<html><b>Algorithm</b><br>(not yet selected)</html>";
        }

        DescriptionOfAlgorithm desc = null;
        try {
            desc = AlgorithmManager.getInstance().getDescriptionOfAlgorithm(algName);
        } catch (Exception ignored) {
            // If we cannot load the description, show basic info only
        }

        StringBuilder sb = new StringBuilder("<html>");
        sb.append("<b>").append(escapeHtml(algName)).append("</b><br>");

        if (desc != null) {
            // Input types - show only the last one if there are multiples
            String[] inputTypes = desc.getInputFileTypes();
            if (inputTypes != null && inputTypes.length > 0) {
                String inputToShow = inputTypes[inputTypes.length - 1];
                sb.append("<i>Input type:</i> ").append(escapeHtml(inputToShow))
                  .append("<br>");
            }

            // Output types - show only the last one if there are multiples
            String[] outputTypes = desc.getOutputFileTypes();
            if (outputTypes != null && outputTypes.length > 0) {
                String outputToShow = outputTypes[outputTypes.length - 1];
                sb.append("<i>Output type:</i> ").append(escapeHtml(outputToShow))
                  .append("<br>");
            }

            // Parameters summary
            DescriptionOfParameter[] params = desc.getParametersDescription();
            if (params != null && params.length > 0) {
                int mandatory = 0;
                int optional  = 0;
                for (DescriptionOfParameter p : params) {
                    if (p.isOptional()) optional++; else mandatory++;
                }
                sb.append("<i>Parameters:</i> ")
                  .append(params.length).append(" total (")
                  .append(mandatory).append(" required, ")
                  .append(optional).append(" optional)<br>");

                // List each parameter with its name and current value
                String[] currentValues = bn.group.nodeAlgorithm.parameters;
                sb.append("<table cellpadding=\"1\">");
                for (int i = 0; i < params.length; i++) {
                    DescriptionOfParameter pd = params[i];
                    String val = (currentValues != null && i < currentValues.length
                            && currentValues[i] != null && !currentValues[i].isEmpty())
                            ? currentValues[i] : "<i>(not set)</i>";
                    sb.append("<tr><td>&nbsp;&bull;&nbsp;")
                      .append(escapeHtml(pd.getName()))
                      .append(":</td><td><b>").append(val).append("</b></td></tr>");
                }
                sb.append("</table>");
            }
        }

        sb.append("</html>");
        return sb.toString();
    }

    /**
     * Escapes HTML special characters in the given string for safe use inside an HTML tooltip.
     *
     * @param text the raw string to escape, may be null.
     * @return the escaped string, or an empty string if the input is null.
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    /**
     * Registers a listener to receive draw-panel event notifications.
     *
     * @param l the listener to add; ignored if null.
     */
    void addDrawPanelListener(DrawPanelListener l) {
        if (l != null)
            listeners.add(l);
    }

    /**
     * Removes a previously registered draw-panel listener.
     *
     * @param l the listener to remove.
     */
    void removeDrawPanelListener(DrawPanelListener l) {
        listeners.remove(l);
    }

    /**
     * Returns true if the selected algorithm node is the topmost node of its branch and has no output node shown.
     * This identifies a root algorithm-only node from which a sibling branch can be created.
     *
     * @return true if a new sibling root branch may be added from the selected algorithm node.
     */
    private boolean isSelectedRootAlgorithmWithNoOutput() {
        if (!(selected instanceof NodeAlgorithm) || selectedBranchNode == null) {
            return false;
        }
        // Must be a root node with no visible output, meaning it is the topmost node of its branch
        return selectedBranchNode.isRoot() && !selectedBranchNode.group.showOutput;
    }

    /**
     * Adds a new algorithm node to the workflow, either as a root or as a child of the selected node.
     */
    void addAlgorithmNode() {
        if (roots.isEmpty()) {
            BranchNode n = createNewBranchNode(true);
            roots.add(n);
            selected = n.group.nodeAlgorithm;
            selectedBranchNode = n;
            relayoutAndRepaint();
            fireAll();
            return;
        }

        if (selected instanceof NodeFileOutput && selectedBranchNode != null) {
            BranchNode n = createNewBranchNode(false);
            selectedBranchNode.addChild(n);
            selected = n.group.nodeAlgorithm;
            selectedBranchNode = n;
            relayoutAndRepaint();
            fireAll();
            return;
        }

        if (selected instanceof NodeFileInput && selectedBranchNode != null) {
            BranchNode existing = selectedBranchNode;
            BranchNode n = createNewBranchNode(true);
            if (existing.group.nodeInput != null && existing.group.nodeInput.inputFile != null) {
                n.group.nodeInput.inputFile = existing.group.nodeInput.inputFile;
                n.group.nodeInput.name = existing.group.nodeInput.name;
            }
            roots.add(n);
            selected = n.group.nodeAlgorithm;
            selectedBranchNode = n;
            relayoutAndRepaint();
            fireAll();
            return;
        }

        // Handle the case where a root algorithm node with no output is selected:
        // add a new sibling root branch so the user can build a parallel workflow branch.
        if (isSelectedRootAlgorithmWithNoOutput()) {
            BranchNode n = createNewBranchNode(true);
            roots.add(n);
            selected = n.group.nodeAlgorithm;
            selectedBranchNode = n;
            relayoutAndRepaint();
            fireAll();
        }
    }

    /**
     * Removes the currently selected branch node from the tree if it is a leaf.
     */
    void removeSelectedNode() {
        if (selectedBranchNode == null || !selectedBranchNode.isLeaf()) {
            return;
        }
        removeLeafNode(selectedBranchNode);
    }

    /**
     * Removes a leaf branch node from the tree, updating selection and notifying listeners.
     *
     * @param bn the leaf branch node to remove; must be a leaf.
     */
    private void removeLeafNode(BranchNode bn) {
        if (bn.isRoot()) {
            roots.remove(bn);
        } else {
            bn.parent.removeChild(bn);
        }
        selected = null;
        selectedBranchNode = null;
        relayoutAndRepaint();
        fireAll();
    }

    /**
     * Duplicates the given branch node as a sibling with no children.
     *
     * @param bn the branch node to duplicate.
     */
    private void duplicateNode(BranchNode bn) {
        NodeFileInput inCopy = null;
        if (bn.group.showInput && bn.group.nodeInput != null) {
            inCopy = new NodeFileInput(bn.group.nodeInput.name, 0, 0);
            inCopy.inputFile = bn.group.nodeInput.inputFile;
        }

        NodeAlgorithm algCopy = new NodeAlgorithm(bn.group.nodeAlgorithm.name, 0, 0);
        if (bn.group.nodeAlgorithm.parameters != null) {
            algCopy.parameters = bn.group.nodeAlgorithm.parameters.clone();
        } else {
            algCopy.parameters = new String[0];
        }

        int idx = countAllBranchNodes() + 1;
        NodeFileOutput outCopy = new NodeFileOutput("Output" + idx + ".txt", 0, 0);
        outCopy.outputFile = "Output" + idx + ".txt";

        GroupOfNodes groupCopy = new GroupOfNodes(inCopy, algCopy, outCopy);
        groupCopy.showInput  = bn.group.showInput;
        groupCopy.showOutput = bn.group.showOutput;

        BranchNode copy = new BranchNode(groupCopy, null);

        if (bn.isRoot()) {
            roots.add(copy);
        } else {
            bn.parent.addChild(copy);
        }

        selected = copy.group.nodeAlgorithm;
        selectedBranchNode = copy;
        relayoutAndRepaint();
        fireAll();
    }

    /**
     * Duplicates the given branch node and its entire subtree as a sibling with fresh output names.
     *
     * @param bn the branch node whose subtree should be duplicated; must have at least one child.
     */
    private void duplicateSubtree(BranchNode bn) {
        BranchNode copy = bn.deepCopy();

        List<BranchNode> allCopied = new ArrayList<>();
        copy.collectAllNodes(allCopied);
        for (BranchNode node : allCopied) {
            int idx = countAllBranchNodes() + allCopied.indexOf(node) + 1;
            node.group.nodeOutput.name       = "Output" + idx + ".txt";
            node.group.nodeOutput.outputFile = "Output" + idx + ".txt";
            node.group.nodeOutput.rectangle  = null;
        }

        if (bn.isRoot()) {
            roots.add(copy);
        } else {
            bn.parent.addChild(copy);
        }

        selected = copy.group.nodeAlgorithm;
        selectedBranchNode = copy;
        relayoutAndRepaint();
        fireAll();
    }

    /**
     * Deletes the given branch node and its entire subtree from the workflow.
     *
     * @param bn the branch node whose subtree should be deleted; must have at least one child.
     */
    private void deleteSubtree(BranchNode bn) {
        if (bn.isRoot()) {
            roots.remove(bn);
        } else {
            bn.parent.removeChild(bn);
        }
        selected = null;
        selectedBranchNode = null;
        relayoutAndRepaint();
        fireAll();
    }

    /**
     * Shows a right-click context popup menu for the given algorithm branch node.
     *
     * @param bn the branch node that was right-clicked.
     * @param x  the X coordinate of the click within this panel.
     * @param y  the Y coordinate of the click within this panel.
     */
    private void showContextMenu(BranchNode bn, int x, int y) {
        JPopupMenu popup = new JPopupMenu();

        JMenuItem itemRemove = new JMenuItem("Remove algorithm");
        itemRemove.setEnabled(bn.isLeaf());
        itemRemove.addActionListener(e -> removeLeafNode(bn));
        popup.add(itemRemove);

        popup.addSeparator();

        JMenuItem itemDuplicateNode = new JMenuItem("Duplicate algorithm node");
        itemDuplicateNode.addActionListener(e -> duplicateNode(bn));
        popup.add(itemDuplicateNode);

        JMenuItem itemDuplicateSubtree = new JMenuItem("Duplicate sub-tree");
        itemDuplicateSubtree.setEnabled(!bn.isLeaf());
        itemDuplicateSubtree.addActionListener(e -> duplicateSubtree(bn));
        popup.add(itemDuplicateSubtree);

        popup.addSeparator();

        JMenuItem itemDeleteSubtree = new JMenuItem("Delete sub-tree");
        itemDeleteSubtree.setEnabled(!bn.isLeaf());
        itemDeleteSubtree.addActionListener(e -> deleteSubtree(bn));
        popup.add(itemDeleteSubtree);

        popup.show(this, x, y);
    }

    /**
     * Validates every subtree in the workflow and returns an error message if something is wrong.
     *
     * @return an error message string, or null if the workflow is valid.
     */
    String validateTheWorkflow() {
        for (BranchNode root : roots) {
            String e = validateSubtree(root, true);
            if (e != null)
                return e;
        }
        return null;
    }

    /**
     * Recomputes the layout of all nodes and repaints the panel.
     */
    void relayoutAndRepaint() {
        if (roots.isEmpty()) {
            hitBoxes.clear();
            setPreferredSize(new Dimension(400, 300));
            revalidate();
            repaint();
            return;
        }

        FontMetrics fm = measureCanvas.getFontMetrics(MEASURE_FONT);
        Map<BranchNode, Integer> widths = new HashMap<>();
        for (BranchNode r : roots)
            computeSubtreeWidth(r, widths, fm);

        int cursorX = LEFT_MARGIN;
        for (BranchNode r : roots) {
            int w = widths.get(r);
            assignCoordinates(r, cursorX + w / 2, TOP_MARGIN, widths);
            cursorX += w + SIBLING_GAP;
        }

        rebuildHitBoxes(widths, fm);
        updatePreferredSizeFromLayout(widths, fm);
        revalidate();
        repaint();
    }

    /**
     * Returns the minimum column width required to display all labels in the given branch node's group.
     *
     * @param bn the branch node to measure.
     * @param fm the FontMetrics used for text measurement.
     * @return the computed column width in pixels.
     */
    private int columnWidthForNode(BranchNode bn, FontMetrics fm) {
        GroupOfNodes g = bn.group;
        int max = 0;
        if (g.showInput && g.nodeInput != null && g.nodeInput.name != null)
            max = Math.max(max, fm.stringWidth(g.nodeInput.name));
        if (g.nodeAlgorithm.name != null)
            max = Math.max(max, fm.stringWidth(g.nodeAlgorithm.name));
        if (g.showOutput && g.nodeOutput != null && g.nodeOutput.name != null)
            max = Math.max(max, fm.stringWidth(g.nodeOutput.name));
        return Math.max(MIN_COL_WIDTH, max + 2 * NODE_H_PADDING);
    }

    /**
     * Recursively computes the total pixel width of the subtree rooted at the given node.
     *
     * @param node   the subtree root.
     * @param widths map to store computed widths keyed by branch node.
     * @param fm     the FontMetrics used for text measurement.
     * @return the computed subtree width in pixels.
     */
    private int computeSubtreeWidth(BranchNode node, Map<BranchNode, Integer> widths,
                                     FontMetrics fm) {
        int own = columnWidthForNode(node, fm);
        if (node.isLeaf()) {
            widths.put(node, own);
            return own;
        }

        int childTotal = 0;
        for (int i = 0; i < node.children.size(); i++) {
            childTotal += computeSubtreeWidth(node.children.get(i), widths, fm);
            if (i < node.children.size() - 1)
                childTotal += SIBLING_GAP;
        }
        int total = Math.max(own, childTotal);
        widths.put(node, total);
        return total;
    }

    /**
     * Recursively assigns pixel coordinates to every node in the subtree rooted at the given branch node.
     *
     * @param bn      the subtree root.
     * @param centerX the horizontal centre of this node's column.
     * @param topY    the Y coordinate of the top of this node's group.
     * @param widths  precomputed subtree widths used to distribute children.
     */
    private void assignCoordinates(BranchNode bn, int centerX, int topY,
                                    Map<BranchNode, Integer> widths) {
        bn.layoutX = centerX;
        bn.layoutY = topY;
        int y = topY;

        if (bn.group.showInput && bn.group.nodeInput != null) {
            bn.group.nodeInput.updatePosition(centerX, y);
            y += NODE_Y_GAP;
        }
        bn.group.nodeAlgorithm.updatePosition(centerX, y);
        y += NODE_Y_GAP;

        if (bn.group.showOutput && bn.group.nodeOutput != null) {
            bn.group.nodeOutput.updatePosition(centerX, y);
            y += NODE_Y_GAP;
        }

        if (!bn.isLeaf()) {
            int childTopY = y + LEVEL_Y_GAP;
            int totalCW = 0;
            for (int i = 0; i < bn.children.size(); i++) {
                totalCW += widths.get(bn.children.get(i));
                if (i < bn.children.size() - 1)
                    totalCW += SIBLING_GAP;
            }
            int startX = centerX - totalCW / 2;
            for (BranchNode child : bn.children) {
                int cw = widths.get(child);
                assignCoordinates(child, startX + cw / 2, childTopY, widths);
                startX += cw + SIBLING_GAP;
            }
        }
    }

    /**
     * Clears and rebuilds the hit-box map for every visible node in the tree.
     *
     * @param widths precomputed subtree widths used to size hit boxes.
     * @param fm     the FontMetrics used for column width calculation.
     */
    private void rebuildHitBoxes(Map<BranchNode, Integer> widths, FontMetrics fm) {
        hitBoxes.clear();
        List<BranchNode> all = new ArrayList<>();
        for (BranchNode r : roots)
            r.collectAllNodes(all);

        for (BranchNode bn : all) {
            int halfW = columnWidthForNode(bn, fm) / 2;
            GroupOfNodes g = bn.group;

            if (g.showInput && g.nodeInput != null) {
                hitBoxes.put(g.nodeInput,
                        makeHitBox(g.nodeInput.getX(), g.nodeInput.getY(), halfW));
            }
            hitBoxes.put(g.nodeAlgorithm,
                    makeHitBox(g.nodeAlgorithm.getX(), g.nodeAlgorithm.getY(), halfW));
            if (g.showOutput && g.nodeOutput != null) {
                hitBoxes.put(g.nodeOutput,
                        makeHitBox(g.nodeOutput.getX(), g.nodeOutput.getY(), halfW));
            }
        }
    }

    /**
     * Creates a rectangular hit box centred on the given coordinates.
     *
     * @param cx    the centre X coordinate.
     * @param cy    the centre Y coordinate.
     * @param halfW the half-width of the hit box.
     * @return the constructed Rectangle.
     */
    private static Rectangle makeHitBox(int cx, int cy, int halfW) {
        return new Rectangle(cx - halfW, cy - HIT_HALF_H, halfW * 2, HIT_HALF_H * 2);
    }

    /**
     * Returns the visual node and its owning branch node located at the given mouse coordinates.
     *
     * @param mx the mouse X coordinate.
     * @param my the mouse Y coordinate.
     * @return a two-element array containing the hit Node (or null) and the hit BranchNode (or null).
     */
    private Object[] findHit(int mx, int my) {
        List<BranchNode> all = new ArrayList<>();
        for (BranchNode r : roots)
            r.collectAllNodes(all);

        for (BranchNode bn : all) {
            GroupOfNodes g = bn.group;

            if (g.showInput && g.nodeInput != null) {
                Rectangle hb = hitBoxes.get(g.nodeInput);
                if (hb != null && hb.contains(mx, my)) {
                    return new Object[]{ g.nodeInput, bn };
                }
            }
            {
                Rectangle hb = hitBoxes.get(g.nodeAlgorithm);
                if (hb != null && hb.contains(mx, my)) {
                    return new Object[]{ g.nodeAlgorithm, bn };
                }
            }
            if (g.showOutput && g.nodeOutput != null) {
                Rectangle hb = hitBoxes.get(g.nodeOutput);
                if (hb != null && hb.contains(mx, my)) {
                    return new Object[]{ g.nodeOutput, bn };
                }
            }
        }
        return new Object[]{ null, null };
    }

    /**
     * Updates the panel's preferred size based on the current layout extents.
     *
     * @param widths precomputed subtree widths used to calculate total width.
     * @param fm     the FontMetrics used for column width calculation.
     */
    private void updatePreferredSizeFromLayout(Map<BranchNode, Integer> widths, FontMetrics fm) {
        int totalWidth = LEFT_MARGIN + RIGHT_MARGIN;
        for (int i = 0; i < roots.size(); i++) {
            totalWidth += widths.get(roots.get(i));
            if (i < roots.size() - 1)
                totalWidth += SIBLING_GAP;
        }
        int maxY = TOP_MARGIN;
        for (BranchNode r : roots)
            maxY = Math.max(maxY, deepestY(r));
        int totalHeight = maxY + BOTTOM_MARGIN;

        Dimension d = new Dimension(totalWidth, totalHeight);
        if (!d.equals(getPreferredSize()))
            setPreferredSize(d);
    }

    /**
     * Returns the largest Y coordinate found among all nodes in the given subtree.
     *
     * @param bn the subtree root to search.
     * @return the maximum Y coordinate value found.
     */
    private int deepestY(BranchNode bn) {
        int max = 0;
        for (Node n : bn.group.getNodes())
            max = Math.max(max, n.getY());
        for (BranchNode child : bn.children)
            max = Math.max(max, deepestY(child));
        return max;
    }

    /**
     * Paints all edges and nodes of the workflow tree onto the panel.
     *
     * @param g the Graphics context provided by Swing.
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (!roots.isEmpty()) {
            FontMetrics fm = g.getFontMetrics();
            Map<BranchNode, Integer> widths = new HashMap<>();
            for (BranchNode r : roots)
                computeSubtreeWidth(r, widths, fm);

            int cursorX = LEFT_MARGIN;
            for (BranchNode r : roots) {
                int w = widths.get(r);
                assignCoordinates(r, cursorX + w / 2, TOP_MARGIN, widths);
                cursorX += w + SIBLING_GAP;
            }

            rebuildHitBoxes(widths, fm);
            updatePreferredSizeFromLayout(widths, fm);
        }

        for (BranchNode r : roots)
            paintEdgesRecursive(g, r);
        for (BranchNode r : roots)
            paintNodesRecursive(g, r);

        notifyRootsChanged();
        notifyHasOutput();
    }

    /**
     * Recursively paints all edges in the subtree rooted at the given branch node.
     *
     * @param g  the Graphics context.
     * @param bn the subtree root.
     */
    private void paintEdgesRecursive(Graphics g, BranchNode bn) {
        GroupOfNodes group = bn.group;
        if (group.showInput && group.nodeInput != null)
            drawArrow((Graphics2D) g, group.nodeInput.getX(), group.nodeInput.getY(),
                    group.nodeAlgorithm.getX(), group.nodeAlgorithm.getY());
        if (group.showOutput && group.nodeOutput != null)
            drawArrow((Graphics2D) g, group.nodeAlgorithm.getX(), group.nodeAlgorithm.getY(),
                    group.nodeOutput.getX(), group.nodeOutput.getY());

        Node last = group.showOutput ? group.nodeOutput : group.nodeAlgorithm;
        for (BranchNode child : bn.children) {
            Node first = (child.group.showInput && child.group.nodeInput != null)
                    ? child.group.nodeInput : child.group.nodeAlgorithm;
            drawArrow((Graphics2D) g, last.getX(), last.getY(), first.getX(), first.getY());
        }
        for (BranchNode child : bn.children)
            paintEdgesRecursive(g, child);
    }

    /**
     * Recursively paints all nodes in the subtree rooted at the given branch node.
     *
     * @param g  the Graphics context.
     * @param bn the subtree root.
     */
    private void paintNodesRecursive(Graphics g, BranchNode bn) {
        GroupOfNodes group = bn.group;
        if (group.showInput && group.nodeInput != null)
            group.nodeInput.paintNode(g, group.nodeInput == selected);
        group.nodeAlgorithm.paintNode(g, group.nodeAlgorithm == selected);
        if (group.showOutput && group.nodeOutput != null)
            group.nodeOutput.paintNode(g, group.nodeOutput == selected);
        for (BranchNode child : bn.children)
            paintNodesRecursive(g, child);
    }

    /**
     * Draws an arrow from one node centre to another, stopping at the target node's radius.
     *
     * @param g  the Graphics2D context.
     * @param x1 the source X coordinate.
     * @param y1 the source Y coordinate.
     * @param x2 the target X coordinate.
     * @param y2 the target Y coordinate.
     */
    private void drawArrow(Graphics2D g, int x1, int y1, int x2, int y2) {
        AffineTransform saved = g.getTransform();
        double dx = x2 - x1, dy = y2 - y1;
        AffineTransform t = AffineTransform.getTranslateInstance(x1, y1);
        t.concatenate(AffineTransform.getRotateInstance(Math.atan2(dy, dx)));
        g.transform(t);
        g.setColor(Color.BLACK);
        int len = (int) Math.sqrt(dx * dx + dy * dy) - GNode.getRadius();
        g.setStroke(new BasicStroke(GEdge.getEdgeThickness()));
        g.drawLine(GNode.getRadius(), 0, len, 0);
        g.setStroke(new BasicStroke(1));
        g.fillPolygon(
                new int[]{ len, len - ARROWHEAD_SIZE, len - ARROWHEAD_SIZE, len },
                new int[]{ 0, -ARROWHEAD_SIZE, ARROWHEAD_SIZE, 0 }, 4);
        g.setTransform(saved);
    }

    /**
     * Registers mouse listeners for left-click selection, right-click context menu,
     * and mouse motion for tooltip updates.
     */
    private void registerMouseEvents() {
        MouseAdapter adapter = new MouseAdapter() {

            /**
             * Handles mouse release for left-click selection and right-click context menu.
             *
             * @param e the mouse event.
             */
            @Override
            public void mouseReleased(MouseEvent e) {
                Object[] hit = findHit(e.getX(), e.getY());
                Node hitNode     = (Node) hit[0];
                BranchNode hitBranch = (BranchNode) hit[1];

                if (e.isPopupTrigger() || e.getButton() == MouseEvent.BUTTON3) {
                    if (hitNode instanceof NodeAlgorithm && hitBranch != null) {
                        selected = hitNode;
                        selectedBranchNode = hitBranch;
                        notifyNodeSelected();
                        notifyHasOutput();
                        notifyCanRemove();
                        repaint();
                        showContextMenu(hitBranch, e.getX(), e.getY());
                    }
                } else {
                    selected = hitNode;
                    selectedBranchNode = hitBranch;
                    notifyNodeSelected();
                    notifyHasOutput();
                    notifyCanRemove();
                    repaint();
                }
            }

            /**
             * Handles mouse press to catch popup trigger on platforms that fire it on press.
             *
             * @param e the mouse event.
             */
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    Object[] hit = findHit(e.getX(), e.getY());
                    Node hitNode     = (Node) hit[0];
                    BranchNode hitBranch = (BranchNode) hit[1];
                    if (hitNode instanceof NodeAlgorithm && hitBranch != null) {
                        selected = hitNode;
                        selectedBranchNode = hitBranch;
                        notifyNodeSelected();
                        notifyHasOutput();
                        notifyCanRemove();
                        repaint();
                        showContextMenu(hitBranch, e.getX(), e.getY());
                    }
                }
            }

            /**
             * Updates the tooltip text dynamically as the mouse moves over different nodes.
             *
             * @param e the mouse event.
             */
            @Override
            public void mouseMoved(MouseEvent e) {
                // Trigger Swing's ToolTipManager to re-query getToolTipText by
                // posting a synthetic mouseMoved event; this is the standard Swing idiom.
                ToolTipManager.sharedInstance().mouseMoved(e);
            }
        };

        addMouseListener(adapter);
        addMouseMotionListener(adapter);
    }

    /**
     * Creates a new branch node with an algorithm node and an output node, optionally including an input node.
     *
     * @param withInput true if the new node should include an input file node.
     * @return the newly created BranchNode.
     */
    private BranchNode createNewBranchNode(boolean withInput) {
        NodeFileInput in = withInput ? new NodeFileInput("", 0, 0) : null;
        NodeAlgorithm alg = new NodeAlgorithm("Algorithm", 0, 0);
        int idx = countAllBranchNodes() + 1;
        NodeFileOutput out = new NodeFileOutput("Output" + idx + ".txt", 0, 0);
        GroupOfNodes g = new GroupOfNodes(in, alg, out);
        g.showInput  = withInput;
        g.showOutput = true;
        return new BranchNode(g, null);
    }

    /**
     * Returns the total number of branch nodes currently in the workflow tree.
     *
     * @return the node count.
     */
    private int countAllBranchNodes() {
        List<BranchNode> all = new ArrayList<>();
        for (BranchNode r : roots)
            r.collectAllNodes(all);
        return all.size();
    }

    /**
     * Validates the subtree rooted at the given branch node and returns an error message if invalid.
     *
     * @param bn     the subtree root to validate.
     * @param isRoot true if this node is a root of the workflow.
     * @return an error message string, or null if the subtree is valid.
     */
    private String validateSubtree(BranchNode bn, boolean isRoot) {
        GroupOfNodes g = bn.group;
        String algName = g.nodeAlgorithm.name;

        if (algName == null || algName.equals("Algorithm") || algName.startsWith(" --")) {
            return "An algorithm has not been selected. Please select an algorithm.";
        }

        if (isRoot && g.showInput) {
            if (g.nodeInput == null || g.nodeInput.name == null || g.nodeInput.name.isEmpty()) {
                return "The input file is not set for algorithm '" + algName
                        + "'. Please select an input file.";
            }
        }

        if (g.showOutput) {
            if (g.nodeOutput == null || g.nodeOutput.outputFile == null
                    || g.nodeOutput.outputFile.isEmpty()) {
                return "The output file is not set for algorithm '" + algName
                        + "'. Please select an output file.";
            }
        }

        String paramError = validateParameters(algName, g.nodeAlgorithm.parameters);
        if (paramError != null) {
            return "Algorithm '" + algName + "': " + paramError;
        }

        for (BranchNode child : bn.children) {
            String err = validateSubtree(child, false);
            if (err != null)
                return err;
        }
        return null;
    }

    /**
     * Validates the parameter values against the algorithm's parameter descriptions.
     *
     * @param algName    the name of the algorithm whose parameters to validate.
     * @param parameters the current parameter values array, may be null or empty.
     * @return an error message string, or null if all parameters are valid.
     */
    private String validateParameters(String algName, String[] parameters) {
        DescriptionOfAlgorithm description;
        try {
            description = AlgorithmManager.getInstance().getDescriptionOfAlgorithm(algName);
        } catch (Exception e) {
            return "Could not retrieve algorithm description: " + e.getMessage();
        }

        if (description == null) {
            return "Algorithm description not found. Please re-select the algorithm.";
        }

        DescriptionOfParameter[] paramDescs = description.getParametersDescription();
        if (paramDescs == null || paramDescs.length == 0) {
            return null;
        }

        for (int i = 0; i < paramDescs.length; i++) {
            DescriptionOfParameter pd = paramDescs[i];
            String value = null;
            if (parameters != null && i < parameters.length) {
                value = parameters[i];
            }

            boolean isBlank = (value == null || value.trim().isEmpty());

            if (isBlank) {
                if (!pd.isOptional()) {
                    return "The mandatory parameter '" + pd.getName()
                            + "' has not been provided. Please fill in all required parameters.";
                }
                continue;
            }

            String typeError = checkParameterType(pd, value.trim());
            if (typeError != null) {
                return typeError;
            }
        }
        return null;
    }

    /**
     * Checks that the given value string is compatible with the type declared in the parameter description.
     *
     * @param pd    the parameter description declaring the expected type.
     * @param value the trimmed non-empty value string to check.
     * @return an error message string, or null if the value is type-compatible.
     */
    private String checkParameterType(DescriptionOfParameter pd, String value) {
        Class<?> type = pd.getParameterType();
        if (type == null) return null;

        if (type == Integer.class || type == int.class) {
            try { Integer.parseInt(value); }
            catch (NumberFormatException e) {
                return "Parameter '" + pd.getName() + "' must be an integer but '"
                        + value + "' was provided.";
            }
            return null;
        }
        if (type == Long.class || type == long.class) {
            try { Long.parseLong(value); }
            catch (NumberFormatException e) {
                return "Parameter '" + pd.getName() + "' must be a long integer but '"
                        + value + "' was provided.";
            }
            return null;
        }
        if (type == Double.class || type == double.class) {
            try { Double.parseDouble(value); }
            catch (NumberFormatException e) {
                return "Parameter '" + pd.getName() + "' must be a decimal number but '"
                        + value + "' was provided.";
            }
            return null;
        }
        if (type == Float.class || type == float.class) {
            try { Float.parseFloat(value); }
            catch (NumberFormatException e) {
                return "Parameter '" + pd.getName() + "' must be a decimal number but '"
                        + value + "' was provided.";
            }
            return null;
        }
        if (type == Boolean.class || type == boolean.class) {
            if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                return "Parameter '" + pd.getName()
                        + "' must be 'true' or 'false' but '"
                        + value + "' was provided.";
            }
            return null;
        }
        return null;
    }

    /**
     * Notifies all listeners that the selected node has changed.
     */
    private void notifyNodeSelected() {
        for (DrawPanelListener l : listeners)
            l.notifyNodeSelected(selected);
    }

    /**
     * Notifies all listeners of the current list of root nodes.
     */
    private void notifyRootsChanged() {
        for (DrawPanelListener l : listeners)
            l.notifyOfListOfRootNodes(new ArrayList<>(roots));
    }

    /**
     * Notifies all listeners whether adding a new algorithm node is currently permitted.
     * Adding is allowed when: the workflow is empty, a NodeFileOutput is selected, a NodeFileInput
     * is selected, or a root algorithm node with no output is selected.
     */
    private void notifyHasOutput() {
        boolean can = roots.isEmpty()
                || (selected instanceof NodeFileOutput && selectedBranchNode != null
                        && selectedBranchNode.group.showOutput)
                || (selected instanceof NodeFileInput && selectedBranchNode != null)
                || isSelectedRootAlgorithmWithNoOutput();
        for (DrawPanelListener l : listeners)
            l.notifyHasOutputNode(can);
    }

    /**
     * Notifies all listeners whether the currently selected node may be removed.
     */
    private void notifyCanRemove() {
        boolean canRemove = (selectedBranchNode != null) && (selectedBranchNode.isLeaf())
                && (selected instanceof NodeAlgorithm);
        for (DrawPanelListener l : listeners)
            l.notifyCanRemoveSelectedNode(canRemove);
    }

    /**
     * Fires all listener notifications after a structural change to the workflow tree.
     */
    private void fireAll() {
        notifyNodeSelected();
        notifyRootsChanged();
        notifyHasOutput();
        notifyCanRemove();
    }
}