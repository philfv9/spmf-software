package ca.pfv.spmf.gui.viewers.graph_transition_viewer;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.Scrollable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/*
 * Copyright (c) 2008-2025 Philippe Fournier-Viger
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
 * Interactive JPanel that renders a sequential-pattern transition graph with zoom, pan, selection, and tooltips.
 * <p>
 * Node size is determined by pattern count (number of patterns containing that item).
 * Edge thickness is determined by weight (pattern count by default).
 *
 * @author Philippe Fournier-Viger 2025
 */
public class TransitionGraphPanel extends JPanel implements Scrollable {

	private static final long serialVersionUID = 1L;

	/** The transition graph data model. */
	private TransitionGraph graph;

	/** The list of nodes currently displayed. */
	private List<TransitionGraph.GraphNode> displayNodes;

	/** The list of edges currently displayed. */
	private List<TransitionGraph.GraphEdge> displayEdges;

	/** Set of visible node labels for O(1) lookup. */
	private Set<String> visibleNodeLabels = new HashSet<>();

	/** Set of visible edge keys for O(1) lookup. */
	private Set<String> visibleEdgeKeys = new HashSet<>();

	/** Current zoom level. */
	private double zoom = 1.0;

	/** Current horizontal pan offset in screen pixels. */
	private double panX = 0;

	/** Current vertical pan offset in screen pixels. */
	private double panY = 0;

	/** Last recorded mouse position for drag computation. */
	private Point lastMousePos;

	/** Label of the currently selected node, or {@code null}. */
	private String selectedNode = null;

	/** Key of the currently selected edge, or {@code null}. */
	private String selectedEdgeKey = null;

	/** Label of the currently hovered node, or {@code null}. */
	private String hoveredNode = null;

	/** Key of the currently hovered edge, or {@code null}. */
	private String hoveredEdgeKey = null;

	/** Set of edge keys highlighted from an external pattern selection. */
	private Set<String> highlightedEdgeKeys = new HashSet<>();

	/** Set of node labels highlighted from an external pattern selection. */
	private Set<String> highlightedNodeLabels = new HashSet<>();

	/** The node currently being dragged, or {@code null}. */
	private TransitionGraph.GraphNode draggedNode = null;

	/** Whether a node drag is in progress. */
	private boolean draggingNode = false;

	/** Current tooltip text, or {@code null} if none. */
	private String tooltipText = null;

	/** Current tooltip screen position, or {@code null}. */
	private Point tooltipPos = null;

	/** Listener notified when a node is selected or deselected. */
	private NodeSelectionListener nodeSelectionListener;

	/** Listener notified when an edge is selected or deselected. */
	private EdgeSelectionListener edgeSelectionListener;

	/** Whether to draw pattern-count badges on nodes. */
	private boolean showPatternCountBadge = false;

	/** Whether to draw self-loop edges. */
	private boolean showSelfLoops = true;

	/** Whether to draw sequential edges. */
	private boolean showSequentialEdges = true;

	/** Whether to draw co-occurrence edges. */
	private boolean showCoOccurrenceEdges = true;

	/** Whether to draw weight labels on edges. */
	private boolean showEdgeWeights = false;

	/** Map from node label to pattern count in the filtered pattern set. */
	private Map<String, Integer> patternCountsPerNode = new HashMap<>();

	/** Cached count of parallel edges for each visible edge key. */
	private Map<String, Integer> visibleParallelCount = new HashMap<>();

	/** Cached index within its parallel group for each visible edge key. */
	private Map<String, Integer> visibleParallelIndex = new HashMap<>();

	/** Cached maximum node pattern count for rendering normalization. */
	private int cachedMaxNodePC = 1;

	/** Cached maximum edge weight for rendering normalization. */
	private double cachedMaxWeight = 1.0;

	// Rendering constants
	private static final int MIN_NODE_RADIUS = 12;
	private static final int MAX_NODE_RADIUS = 40;
	private static final float MIN_EDGE_WIDTH = 1.0f;
	private static final float MAX_EDGE_WIDTH = 8.0f;
	private static final double MIN_ARROW_SIZE = 6.0;
	private static final double MAX_ARROW_SIZE = 18.0;
	private static final double PARALLEL_EDGE_OFFSET = 25.0;

	// Color constants
	private static final Color BACKGROUND_COLOR = Color.WHITE;
	private static final Color NODE_COLOR = new Color(70, 130, 180);
	private static final Color NODE_SELECTED_COLOR = new Color(255, 140, 0);
	private static final Color NODE_HOVER_COLOR = new Color(100, 160, 210);
	private static final Color NODE_PATTERN_HIGHLIGHT_COLOR = new Color(180, 50, 180);
	private static final Color EDGE_COLOR = new Color(150, 150, 150);
	private static final Color EDGE_CO_OCCURRENCE_COLOR = new Color(100, 170, 100);
	private static final Color EDGE_HIGHLIGHT_IN = new Color(50, 180, 50);
	private static final Color EDGE_HIGHLIGHT_OUT = new Color(220, 50, 50);
	private static final Color EDGE_PATTERN_HIGHLIGHT = new Color(180, 50, 180);
	private static final Color EDGE_SELECTED_COLOR = new Color(255, 140, 0);
	private static final Color LABEL_COLOR = Color.BLACK;
	private static final Color LABEL_ON_DARK_COLOR = Color.WHITE;
	private static final Color BADGE_COLOR = new Color(220, 50, 50);
	private static final Color BADGE_TEXT_COLOR = Color.WHITE;
	private static final Color EDGE_WEIGHT_LABEL_COLOR = new Color(80, 80, 80);
	private static final Color EDGE_WEIGHT_LABEL_BG = new Color(255, 255, 255, 200);
	private static final Color DIM_COLOR = new Color(190, 190, 190, 90);
	private static final Color DIM_EDGE_COLOR = new Color(200, 200, 200, 60);
	private static final Color HOVERED_EDGE_COLOR = new Color(80, 80, 200);
	
	// Add these instance variables (around the other constants/fields near the top)

	/** Minimum canvas size. */
	private static final int MIN_CANVAS_WIDTH = 1200;
	private static final int MIN_CANVAS_HEIGHT = 900;

	/** Extra margin around graph content when computing dynamic canvas size. */
	private static final int CANVAS_MARGIN = 150;

	/** Calculated canvas size based on graph content. */
	private Dimension calculatedCanvasSize = new Dimension(MIN_CANVAS_WIDTH, MIN_CANVAS_HEIGHT);

	/**
	 * Listener interface for node selection events.
	 */
	public interface NodeSelectionListener {
		void onNodeSelected(String nodeLabel);
		void onNodeDeselected();
	}

	/**
	 * Listener interface for edge selection events.
	 */
	public interface EdgeSelectionListener {
		void onEdgeSelected(String edgeKey);
		void onEdgeDeselected();
	}

	/**
	 * Constructor. Creates an empty graph panel with mouse interaction handlers.
	 */
	public TransitionGraphPanel() {
		setBackground(BACKGROUND_COLOR);
		setPreferredSize(new Dimension(800, 600));

		addMouseWheelListener(e -> {
			double oldZoom = zoom;
			if (e.getWheelRotation() < 0) zoom *= 1.1;
			else zoom /= 1.1;
			zoom = Math.max(0.05, Math.min(zoom, 20.0));
			Point mp = e.getPoint();
			panX = mp.x - (mp.x - panX) * zoom / oldZoom;
			panY = mp.y - (mp.y - panY) * zoom / oldZoom;
			repaint();
		});

		addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				lastMousePos = e.getPoint();
				if (SwingUtilities.isLeftMouseButton(e)) handleLeftClick(e.getPoint());
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				draggingNode = false;
				draggedNode = null;
			}
		});

		addMouseMotionListener(new MouseMotionAdapter() {
			@Override
			public void mouseDragged(MouseEvent e) {
				if (draggingNode && draggedNode != null) {
					draggedNode.setX((e.getX() - panX) / zoom);
					draggedNode.setY((e.getY() - panY) / zoom);
					repaint();
					return;
				}
				if (SwingUtilities.isMiddleMouseButton(e) || SwingUtilities.isRightMouseButton(e)) {
					panX += e.getX() - lastMousePos.x;
					panY += e.getY() - lastMousePos.y;
					lastMousePos = e.getPoint();
					repaint();
				}
			}

			@Override
			public void mouseMoved(MouseEvent e) {
				updateHoverState(e.getPoint());
			}
		});
	}

	private void handleLeftClick(Point point) {
		String clickedNode = findNodeAt(point);
		if (clickedNode != null) {
			if (selectedEdgeKey != null) { selectedEdgeKey = null; fireEdgeDeselected(); }
			if (clickedNode.equals(selectedNode)) { selectedNode = null; fireNodeDeselected(); }
			else { selectedNode = clickedNode; fireNodeSelected(clickedNode); }
			draggedNode = graph.getNodes().get(clickedNode);
			draggingNode = true;
			repaint();
			return;
		}

		String clickedEdge = findEdgeAt(point);
		if (clickedEdge != null) {
			if (selectedNode != null) { selectedNode = null; fireNodeDeselected(); }
			if (clickedEdge.equals(selectedEdgeKey)) { selectedEdgeKey = null; fireEdgeDeselected(); }
			else { selectedEdgeKey = clickedEdge; fireEdgeSelected(clickedEdge); }
			repaint();
			return;
		}

		boolean hadNode = selectedNode != null, hadEdge = selectedEdgeKey != null;
		selectedNode = null;
		selectedEdgeKey = null;
		if (hadNode) fireNodeDeselected();
		if (hadEdge) fireEdgeDeselected();
		repaint();
	}

	private void updateHoverState(Point point) {
		String prevH = hoveredNode, prevE = hoveredEdgeKey;
		hoveredNode = findNodeAt(point);
		hoveredEdgeKey = (hoveredNode == null) ? findEdgeAt(point) : null;

		if (hoveredNode != null) {
			TransitionGraph.GraphNode node = graph != null ? graph.getNodes().get(hoveredNode) : null;
			if (node != null) {
				StringBuilder sb = new StringBuilder();
				sb.append("Item: ").append(node.getItemLabel());
				sb.append("\nAppears in: ").append(node.getPatternCount()).append(" patterns");
				sb.append("\nMax pattern support: ").append(node.getMaxSupport());

				Integer filteredCount = patternCountsPerNode.get(node.getItemLabel());
				if (filteredCount != null && filteredCount != node.getPatternCount()) {
					sb.append("\nVisible patterns: ").append(filteredCount);
				}

				tooltipText = sb.toString();
				tooltipPos = point;
			}
		} else if (hoveredEdgeKey != null) {
			TransitionGraph.GraphEdge edge = graph != null ? graph.getEdges().get(hoveredEdgeKey) : null;
			if (edge != null) {
				StringBuilder sb = new StringBuilder();
				sb.append("Edge: ").append(edge.getDisplayLabel());
				sb.append("\nType: ").append(edge.getEdgeTypeLabel());
				sb.append("\nAppears in: ").append(edge.getPatternCount()).append(" patterns");
				sb.append("\nMax pattern support: ").append(edge.getMaxSupport());
				tooltipText = sb.toString();
				tooltipPos = point;
			}
		} else {
			tooltipText = null;
			tooltipPos = null;
		}

		if (!Objects.equals(prevH, hoveredNode) || !Objects.equals(prevE, hoveredEdgeKey)) repaint();
	}

	private void fireNodeSelected(String label) { if (nodeSelectionListener != null) nodeSelectionListener.onNodeSelected(label); }
	private void fireNodeDeselected() { if (nodeSelectionListener != null) nodeSelectionListener.onNodeDeselected(); }
	private void fireEdgeSelected(String key) { if (edgeSelectionListener != null) edgeSelectionListener.onEdgeSelected(key); }
	private void fireEdgeDeselected() { if (edgeSelectionListener != null) edgeSelectionListener.onEdgeDeselected(); }

	public void setNodeSelectionListener(NodeSelectionListener listener) { this.nodeSelectionListener = listener; }
	public void setEdgeSelectionListener(EdgeSelectionListener listener) { this.edgeSelectionListener = listener; }

	public void setGraph(TransitionGraph graph, List<TransitionGraph.GraphNode> nodes,
			List<TransitionGraph.GraphEdge> edges) {
		this.graph = graph;
		this.displayNodes = nodes;
		this.displayEdges = edges;
		this.hoveredNode = null;
		this.hoveredEdgeKey = null;
		this.highlightedEdgeKeys.clear();
		this.highlightedNodeLabels.clear();
		zoom = 1.0;
		panX = 0;
		panY = 0;
		clearSelectionQuietly();
		rebuildVisibilityIndex();
		recomputeVisibleParallelEdgeInfo();
		updateCachedRenderValues();
		recalculateCanvasSize();
		revalidate();
		repaint();
	}
	
	/**
	 * Recalculate the canvas size based on graph content so that the panel
	 * grows dynamically when there are many nodes spread over a large area.
	 */
	private void recalculateCanvasSize() {
		if (displayNodes == null || displayNodes.isEmpty()) {
			calculatedCanvasSize = new Dimension(MIN_CANVAS_WIDTH, MIN_CANVAS_HEIGHT);
			return;
		}

		double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;

		for (TransitionGraph.GraphNode n : displayNodes) {
			int r = computeNodeRadius(n.getPatternCount(), cachedMaxNodePC);
			double x = n.getX(), y = n.getY();
			if (x - r < minX) minX = x - r;
			if (y - r < minY) minY = y - r;
			if (x + r > maxX) maxX = x + r;
			if (y + r > maxY) maxY = y + r;
		}

		if (minX > maxX) {
			calculatedCanvasSize = new Dimension(MIN_CANVAS_WIDTH, MIN_CANVAS_HEIGHT);
			return;
		}

		int contentW = (int) Math.ceil(maxX - minX) + 2 * CANVAS_MARGIN;
		int contentH = (int) Math.ceil(maxY - minY) + 2 * CANVAS_MARGIN;

		// Scale up for larger graphs: add extra space based on node count
		int nodeCount = displayNodes.size();
		if (nodeCount > 30) {
			double scaleFactor = 1.0 + (nodeCount - 30) * 0.02;
			scaleFactor = Math.min(scaleFactor, 4.0); // cap at 4x
			contentW = (int) (contentW * scaleFactor);
			contentH = (int) (contentH * scaleFactor);
		}

		calculatedCanvasSize = new Dimension(
				Math.max(contentW, MIN_CANVAS_WIDTH),
				Math.max(contentH, MIN_CANVAS_HEIGHT));
	}
	
	@Override
	public Dimension getPreferredSize() {
		return new Dimension(calculatedCanvasSize);
	}

	public void setPatternHighlight(Set<String> edgeKeys, Set<String> nodeLabels) {
		this.highlightedEdgeKeys = edgeKeys != null ? edgeKeys : new HashSet<>();
		this.highlightedNodeLabels = nodeLabels != null ? nodeLabels : new HashSet<>();
		repaint();
	}

	public void clearPatternHighlight() {
		this.highlightedEdgeKeys.clear();
		this.highlightedNodeLabels.clear();
		repaint();
	}

	public void setShowPatternCountBadge(boolean show) { this.showPatternCountBadge = show; repaint(); }

	public void setShowSelfLoops(boolean show) { this.showSelfLoops = show; onDisplayToggleChanged(); }

	public void setShowSequentialEdges(boolean show) { this.showSequentialEdges = show; onDisplayToggleChanged(); }

	public void setShowCoOccurrenceEdges(boolean show) { this.showCoOccurrenceEdges = show; onDisplayToggleChanged(); }

	public void setShowEdgeWeights(boolean show) { this.showEdgeWeights = show; repaint(); }

	public void setPatternCountsPerNode(Map<String, Integer> counts) {
		this.patternCountsPerNode = counts != null ? counts : new HashMap<>();
		repaint();
	}

	private void onDisplayToggleChanged() {
		recomputeVisibleParallelEdgeInfo();
		validateSelection();
		repaint();
	}

	public void setSelectedNode(String nodeLabel) {
		this.selectedNode = nodeLabel;
		if (nodeLabel != null) this.selectedEdgeKey = null;
		repaint();
	}

	public void setSelectedEdge(String edgeKey) {
		this.selectedEdgeKey = edgeKey;
		if (edgeKey != null) this.selectedNode = null;
		repaint();
	}

	public void clearSelection() {
		boolean hadN = selectedNode != null, hadE = selectedEdgeKey != null;
		selectedNode = null;
		selectedEdgeKey = null;
		if (hadN) fireNodeDeselected();
		if (hadE) fireEdgeDeselected();
		repaint();
	}

	private void clearSelectionQuietly() { selectedNode = null; selectedEdgeKey = null; }

	public double getZoom() { return zoom; }
	public String getSelectedNode() { return selectedNode; }
	public String getSelectedEdgeKey() { return selectedEdgeKey; }

	public void resetView() { zoom = 1.0; panX = 0; panY = 0; repaint(); }

	public void centerOnNode(String itemLabel) {
		if (graph == null || displayNodes == null) return;
		TransitionGraph.GraphNode node = graph.getNodes().get(itemLabel);
		if (node == null) return;
		panX = getWidth() / 2.0 - node.getX() * zoom;
		panY = getHeight() / 2.0 - node.getY() * zoom;
		repaint();
	}

	/**
	 * Get the current display nodes list (for legend info extraction).
	 * @return the list of currently displayed nodes, or {@code null}
	 */
	public List<TransitionGraph.GraphNode> getDisplayNodes() {
		return displayNodes;
	}

	/**
	 * Get the cached maximum node pattern count.
	 * @return the max node pattern count
	 */
	public int getCachedMaxNodePC() {
		return cachedMaxNodePC;
	}

	private void rebuildVisibilityIndex() {
		visibleNodeLabels.clear();
		visibleEdgeKeys.clear();
		if (displayNodes != null) {
			for (TransitionGraph.GraphNode n : displayNodes)
				visibleNodeLabels.add(n.getItemLabel());
		}
		if (displayEdges != null) {
			for (TransitionGraph.GraphEdge e : displayEdges)
				if (shouldDrawEdge(e)) visibleEdgeKeys.add(e.getKey());
		}
	}

	private void validateSelection() {
		visibleEdgeKeys.clear();
		if (displayEdges != null) {
			for (TransitionGraph.GraphEdge e : displayEdges)
				if (shouldDrawEdge(e)) visibleEdgeKeys.add(e.getKey());
		}
		if (selectedNode != null && !visibleNodeLabels.contains(selectedNode)) {
			selectedNode = null; fireNodeDeselected();
		}
		if (selectedEdgeKey != null && !visibleEdgeKeys.contains(selectedEdgeKey)) {
			selectedEdgeKey = null; fireEdgeDeselected();
		}
	}

	private void updateCachedRenderValues() {
		cachedMaxNodePC = (graph != null) ? Math.max(graph.getMaxNodePatternCount(), 1) : 1;
		cachedMaxWeight = (graph != null) ? Math.max(graph.getMaxEdgeWeight(), 1.0) : 1.0;
	}

	private void recomputeVisibleParallelEdgeInfo() {
		visibleParallelCount.clear();
		visibleParallelIndex.clear();
		if (displayEdges == null) return;

		Map<String, List<TransitionGraph.GraphEdge>> groups = new HashMap<>();
		for (TransitionGraph.GraphEdge edge : displayEdges) {
			if (shouldDrawEdge(edge)) {
				String pk = makeUndirectedPairKey(edge.getSourceItem(), edge.getTargetItem());
				groups.computeIfAbsent(pk, k -> new ArrayList<>()).add(edge);
			}
		}
		for (List<TransitionGraph.GraphEdge> group : groups.values()) {
			int count = group.size();
			for (int i = 0; i < count; i++) {
				String key = group.get(i).getKey();
				visibleParallelCount.put(key, count);
				visibleParallelIndex.put(key, i);
			}
		}
	}

	private static String makeUndirectedPairKey(String a, String b) {
		if (a.equals(b)) return "SELF:" + a;
		return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
	}

	private String findNodeAt(Point sp) {
		if (displayNodes == null || graph == null) return null;
		for (int i = displayNodes.size() - 1; i >= 0; i--) {
			TransitionGraph.GraphNode n = displayNodes.get(i);
			double sx = n.getX() * zoom + panX, sy = n.getY() * zoom + panY;
			double dx = sp.x - sx, dy = sp.y - sy;
			double maxDist = computeNodeRadius(n.getPatternCount(), cachedMaxNodePC) * zoom;
			if (dx * dx + dy * dy <= maxDist * maxDist) return n.getItemLabel();
		}
		return null;
	}

	private String findEdgeAt(Point sp) {
		if (displayEdges == null || graph == null) return null;
		double spx = sp.getX(), spy = sp.getY();

		for (TransitionGraph.GraphEdge edge : displayEdges) {
			if (!shouldDrawEdge(edge)) continue;
			TransitionGraph.GraphNode src = graph.getNodes().get(edge.getSourceItem());
			TransitionGraph.GraphNode tgt = graph.getNodes().get(edge.getTargetItem());
			if (src == null || tgt == null) continue;
			double x1 = src.getX() * zoom + panX, y1 = src.getY() * zoom + panY;
			double x2 = tgt.getX() * zoom + panX, y2 = tgt.getY() * zoom + panY;
			double tol = computeEdgeWidth(edge.getWeight(), cachedMaxWeight) * zoom + 5;

			if (src.getItemLabel().equals(tgt.getItemLabel())) {
				int sr = computeNodeRadius(src.getPatternCount(), cachedMaxNodePC);
				double ls = (sr + 18) * zoom;
				double cx = x1, cy = y1 - ls / 2.0;
				double dist = Math.sqrt((spx - cx) * (spx - cx) + (spy - cy) * (spy - cy));
				if (Math.abs(dist - ls / 2.0) <= tol) return edge.getKey();
				continue;
			}
			double co = computeCurveOffset(edge) * zoom;
			if (Math.abs(co) < 1.0) {
				if (Line2D.ptSegDist(x1, y1, x2, y2, spx, spy) <= tol) return edge.getKey();
			} else {
				double ang = Math.atan2(y2 - y1, x2 - x1);
				double mx = (x1 + x2) / 2, my = (y1 + y2) / 2;
				double cx = mx + (-Math.sin(ang)) * co, cy = my + Math.cos(ang) * co;
				double d1 = Line2D.ptSegDist(x1, y1, cx, cy, spx, spy);
				double d2 = Line2D.ptSegDist(cx, cy, x2, y2, spx, spy);
				if (Math.min(d1, d2) <= tol) return edge.getKey();
			}
		}
		return null;
	}

	private boolean shouldDrawEdge(TransitionGraph.GraphEdge edge) {
		if (!showSelfLoops && edge.getSourceItem().equals(edge.getTargetItem())) return false;
		if (!showSequentialEdges && edge.getEdgeType() == TransitionGraph.EdgeType.SEQUENTIAL) return false;
		if (!showCoOccurrenceEdges && edge.getEdgeType() == TransitionGraph.EdgeType.CO_OCCURRENCE) return false;
		return true;
	}

	private int computeNodeRadius(int patternCount, int maxPatternCount) {
		if (maxPatternCount <= 0) return MIN_NODE_RADIUS;
		return (int) (MIN_NODE_RADIUS + (double) patternCount / maxPatternCount * (MAX_NODE_RADIUS - MIN_NODE_RADIUS));
	}

	private float computeEdgeWidth(double w, double maxW) {
		if (maxW <= 0) return MIN_EDGE_WIDTH;
		return (float) (MIN_EDGE_WIDTH + w / maxW * (MAX_EDGE_WIDTH - MIN_EDGE_WIDTH));
	}

	private double computeArrowSize(double w, double maxW) {
		if (maxW <= 0) return MIN_ARROW_SIZE;
		return MIN_ARROW_SIZE + w / maxW * (MAX_ARROW_SIZE - MIN_ARROW_SIZE);
	}

	private double computeCurveOffset(TransitionGraph.GraphEdge edge) {
		String k = edge.getKey();
		int cnt = visibleParallelCount.getOrDefault(k, 1);
		if (cnt <= 1) return 0;
		int idx = visibleParallelIndex.getOrDefault(k, 0);
		return (idx - (cnt - 1) / 2.0) * PARALLEL_EDGE_OFFSET;
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		if (graph == null || displayNodes == null || displayEdges == null) {
			g.setColor(Color.GRAY);
			g.setFont(new Font("SansSerif", Font.PLAIN, 14));
			g.drawString("No graph loaded. Open a sequential patterns file to begin.", 20, 30);
			return;
		}
		Graphics2D g2 = (Graphics2D) g.create();
		renderGraph(g2, zoom, panX, panY, getWidth(), getHeight(), true);
		g2.dispose();
	}

	private void renderGraph(Graphics2D g2, double renderZoom, double renderPanX,
			double renderPanY, int canvasW, int canvasH, boolean drawTooltip) {
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

		int maxPC = cachedMaxNodePC;
		double maxWeight = cachedMaxWeight;

		Set<String> selIn = new HashSet<>(), selOut = new HashSet<>(), selRel = new HashSet<>();
		if (selectedNode != null) {
			selRel.add(selectedNode);
			for (TransitionGraph.GraphEdge e : graph.getIncomingEdges(selectedNode)) {
				selIn.add(e.getKey()); selRel.add(e.getSourceItem()); selRel.add(e.getTargetItem());
			}
			for (TransitionGraph.GraphEdge e : graph.getOutgoingEdges(selectedNode)) {
				selOut.add(e.getKey()); selRel.add(e.getSourceItem()); selRel.add(e.getTargetItem());
			}
		}
		Set<String> edgeSelNodes = new HashSet<>();
		if (selectedEdgeKey != null) {
			TransitionGraph.GraphEdge se = graph.getEdges().get(selectedEdgeKey);
			if (se != null) { edgeSelNodes.add(se.getSourceItem()); edgeSelNodes.add(se.getTargetItem()); }
		}

		boolean hasHL = !highlightedEdgeKeys.isEmpty() || !highlightedNodeLabels.isEmpty();
		boolean hasNS = selectedNode != null, hasES = selectedEdgeKey != null;
		boolean hasAny = hasNS || hasES || hasHL;

		// ===== Edges =====
		for (TransitionGraph.GraphEdge edge : displayEdges) {
			if (!shouldDrawEdge(edge)) continue;
			TransitionGraph.GraphNode src = graph.getNodes().get(edge.getSourceItem());
			TransitionGraph.GraphNode tgt = graph.getNodes().get(edge.getTargetItem());
			if (src == null || tgt == null) continue;

			double x1 = src.getX() * renderZoom + renderPanX, y1 = src.getY() * renderZoom + renderPanY;
			double x2 = tgt.getX() * renderZoom + renderPanX, y2 = tgt.getY() * renderZoom + renderPanY;
			float ew = computeEdgeWidth(edge.getWeight(), maxWeight);
			boolean isCo = edge.getEdgeType() == TransitionGraph.EdgeType.CO_OCCURRENCE;

			g2.setColor(resolveEdgeColor(edge, hasAny, selIn, selOut, isCo));
			g2.setStroke(makeEdgeStroke(ew, isCo, renderZoom));

			if (src.getItemLabel().equals(tgt.getItemLabel())) {
				int sr = computeNodeRadius(src.getPatternCount(), maxPC);
				double ls = (sr + 18) * renderZoom;
				g2.draw(new Ellipse2D.Double(x1 - ls / 2, y1 - ls, ls, ls));
				if (showEdgeWeights) drawEdgeWeightLabel(g2, edge, x1, y1 - ls - 4, renderZoom);
				continue;
			}

			double co = computeCurveOffset(edge) * renderZoom;
			int sr = computeNodeRadius(src.getPatternCount(), maxPC);
			int tr = computeNodeRadius(tgt.getPatternCount(), maxPC);
			double ang = Math.atan2(y2 - y1, x2 - x1);

			if (Math.abs(co) < 1.0) {
				drawStraightEdge(g2, x1, y1, x2, y2, sr, tr, ang, edge, isCo, maxWeight, renderZoom);
			} else {
				drawCurvedEdge(g2, x1, y1, x2, y2, sr, tr, ang, co, edge, isCo, maxWeight, renderZoom);
			}
		}

		// ===== Nodes =====
		for (TransitionGraph.GraphNode node : displayNodes) {
			double sx = node.getX() * renderZoom + renderPanX, sy = node.getY() * renderZoom + renderPanY;
			int radius = computeNodeRadius(node.getPatternCount(), maxPC);
			double r = radius * renderZoom;
			Color nc = resolveNodeColor(node, hasNS, hasES, hasHL, selRel, edgeSelNodes);
			drawNode(g2, sx, sy, r, nc, node.getItemLabel(), renderZoom);
			if (showPatternCountBadge) {
				Integer cnt = patternCountsPerNode.get(node.getItemLabel());
				if (cnt != null && cnt > 0) drawBadge(g2, sx, sy, r, cnt, renderZoom);
			}
		}

		if (drawTooltip && tooltipText != null && tooltipPos != null) {
			drawTooltip(g2, tooltipText, tooltipPos, canvasW, canvasH);
		}
	}

	private Color resolveEdgeColor(TransitionGraph.GraphEdge edge, boolean hasAny,
			Set<String> selIn, Set<String> selOut, boolean isCo) {
		String key = edge.getKey();
		if (key.equals(selectedEdgeKey)) return EDGE_SELECTED_COLOR;
		if (highlightedEdgeKeys.contains(key)) return EDGE_PATTERN_HIGHLIGHT;
		if (selOut.contains(key)) return EDGE_HIGHLIGHT_OUT;
		if (selIn.contains(key)) return EDGE_HIGHLIGHT_IN;
		if (key.equals(hoveredEdgeKey)) return HOVERED_EDGE_COLOR;
		if (hasAny) return DIM_EDGE_COLOR;
		return isCo ? EDGE_CO_OCCURRENCE_COLOR : EDGE_COLOR;
	}

	private Color resolveNodeColor(TransitionGraph.GraphNode node, boolean hasNS,
			boolean hasES, boolean hasHL, Set<String> selRel, Set<String> edgeSelN) {
		String l = node.getItemLabel();
		if (l.equals(selectedNode)) return NODE_SELECTED_COLOR;
		if (highlightedNodeLabels.contains(l)) return NODE_PATTERN_HIGHLIGHT_COLOR;
		if (l.equals(hoveredNode)) return NODE_HOVER_COLOR;
		if (hasNS) return selRel.contains(l) ? NODE_COLOR : DIM_COLOR;
		if (hasES) return edgeSelN.contains(l) ? NODE_SELECTED_COLOR : DIM_COLOR;
		if (hasHL) return DIM_COLOR;
		return NODE_COLOR;
	}

	private Stroke makeEdgeStroke(float width, boolean dashed, double z) {
		float w = width * (float) z;
		if (dashed) return new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
				10.0f, new float[]{6.0f * (float) z, 4.0f * (float) z}, 0.0f);
		return new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
	}

	private void drawStraightEdge(Graphics2D g2, double x1, double y1, double x2, double y2,
			int sr, int tr, double ang, TransitionGraph.GraphEdge edge, boolean isCo,
			double maxW, double z) {
		double sx = x1 + sr * z * Math.cos(ang), sy = y1 + sr * z * Math.sin(ang);
		double ex = x2 - tr * z * Math.cos(ang), ey = y2 - tr * z * Math.sin(ang);
		g2.draw(new Line2D.Double(sx, sy, ex, ey));
		if (!isCo) drawArrowHead(g2, ex, ey, ang, computeArrowSize(edge.getWeight(), maxW) * z);
		if (showEdgeWeights) drawEdgeWeightLabel(g2, edge, (sx + ex) / 2, (sy + ey) / 2, z);
	}

	private void drawCurvedEdge(Graphics2D g2, double x1, double y1, double x2, double y2,
			int sr, int tr, double ang, double co, TransitionGraph.GraphEdge edge, boolean isCo,
			double maxW, double z) {
		double mx = (x1 + x2) / 2, my = (y1 + y2) / 2;
		double px = -Math.sin(ang), py = Math.cos(ang);
		double cx = mx + px * co, cy = my + py * co;
		double sa = Math.atan2(cy - y1, cx - x1);
		double sx = x1 + sr * z * Math.cos(sa), sy = y1 + sr * z * Math.sin(sa);
		double ea = Math.atan2(cy - y2, cx - x2);
		double ex = x2 + tr * z * Math.cos(ea), ey = y2 + tr * z * Math.sin(ea);
		g2.draw(new CubicCurve2D.Double(sx, sy, cx, cy, cx, cy, ex, ey));
		if (!isCo) drawArrowHead(g2, ex, ey, Math.atan2(ey - cy, ex - cx), computeArrowSize(edge.getWeight(), maxW) * z);
		if (showEdgeWeights) drawEdgeWeightLabel(g2, edge, (sx + 2 * cx + ex) / 4, (sy + 2 * cy + ey) / 4, z);
	}

	private void drawNode(Graphics2D g2, double sx, double sy, double r, Color c, String label, double z) {
		g2.setColor(new Color(0, 0, 0, 30));
		g2.fill(new Ellipse2D.Double(sx - r + 2, sy - r + 2, 2 * r, 2 * r));
		g2.setColor(c);
		g2.fill(new Ellipse2D.Double(sx - r, sy - r, 2 * r, 2 * r));
		g2.setColor(c.darker());
		g2.setStroke(new BasicStroke(1.5f));
		g2.draw(new Ellipse2D.Double(sx - r, sy - r, 2 * r, 2 * r));
		boolean dark = isDarkColor(c);
		g2.setColor(dark ? LABEL_ON_DARK_COLOR : LABEL_COLOR);
		int fs = Math.max(9, (int) (11 * z));
		g2.setFont(new Font("SansSerif", Font.BOLD, fs));
		FontMetrics fm = g2.getFontMetrics();
		int tw = fm.stringWidth(label);
		if (tw < 2 * r - 4) {
			g2.drawString(label, (float) (sx - tw / 2.0), (float) (sy + fm.getAscent() / 2.0 - 1));
		} else {
			g2.setColor(LABEL_COLOR);
			g2.drawString(label, (float) (sx - tw / 2.0), (float) (sy + r + fm.getAscent() + 2));
		}
	}

	private void drawEdgeWeightLabel(Graphics2D g2, TransitionGraph.GraphEdge edge, double x, double y, double z) {
		String ws = formatWeight(edge.getWeight());
		g2.setFont(new Font("SansSerif", Font.PLAIN, Math.max(8, (int) (9 * z))));
		FontMetrics fm = g2.getFontMetrics();
		int tw = fm.stringWidth(ws), th = fm.getHeight(), pad = 2;
		g2.setColor(EDGE_WEIGHT_LABEL_BG);
		g2.fillRoundRect((int) (x - tw / 2.0 - pad), (int) (y - th / 2.0 - pad), tw + 2 * pad, th + 2 * pad, 4, 4);
		g2.setColor(EDGE_WEIGHT_LABEL_COLOR);
		g2.drawString(ws, (int) (x - tw / 2.0), (int) (y + fm.getAscent() / 2.0));
	}

	private String formatWeight(double w) {
		if (w == (long) w) return String.valueOf((long) w);
		if (w < 0.01) return String.format("%.4f", w);
		if (w < 1.0) return String.format("%.3f", w);
		return String.format("%.2f", w);
	}

	private void drawBadge(Graphics2D g2, double cx, double cy, double nr, int count, double z) {
		String t = String.valueOf(count);
		g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(8, (int) (9 * z))));
		FontMetrics fm = g2.getFontMetrics();
		int tw = fm.stringWidth(t), bw = Math.max(tw + 6, (int) (14 * z)), bh = fm.getHeight() + 2;
		double bx = cx + nr * 0.6, by = cy - nr * 0.6 - bh / 2.0;
		g2.setColor(BADGE_COLOR);
		g2.fillRoundRect((int) bx, (int) by, bw, bh, bh, bh);
		g2.setColor(Color.WHITE);
		g2.setStroke(new BasicStroke(1.0f));
		g2.drawRoundRect((int) bx, (int) by, bw, bh, bh, bh);
		g2.setColor(BADGE_TEXT_COLOR);
		g2.drawString(t, (int) (bx + (bw - tw) / 2.0), (int) (by + fm.getAscent() + 1));
	}

	private void drawArrowHead(Graphics2D g2, double x, double y, double angle, double size) {
		double hw = size * 0.45;
		double cosA = Math.cos(angle), sinA = Math.sin(angle);
		Path2D arrow = new Path2D.Double();
		arrow.moveTo(x, y);
		arrow.lineTo(x - size * cosA + hw * sinA, y - size * sinA - hw * cosA);
		arrow.lineTo(x - size * cosA - hw * sinA, y - size * sinA + hw * cosA);
		arrow.closePath();
		Stroke old = g2.getStroke();
		g2.setStroke(new BasicStroke(1.0f));
		g2.fill(arrow);
		g2.setStroke(old);
	}

	private void drawTooltip(Graphics2D g2, String text, Point pos, int canvasW, int canvasH) {
		String[] lines = text.split("\n");
		g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
		FontMetrics fm = g2.getFontMetrics();
		int maxW = 0;
		for (String l : lines) maxW = Math.max(maxW, fm.stringWidth(l));
		int totH = lines.length * fm.getHeight(), pad = 6;
		int tx = pos.x + 15, ty = pos.y + 15;
		if (tx + maxW + 2 * pad > canvasW) tx = pos.x - maxW - 2 * pad - 15;
		if (ty + totH + 2 * pad > canvasH) ty = pos.y - totH - 2 * pad - 15;
		g2.setColor(new Color(255, 255, 225, 240));
		g2.fillRoundRect(tx, ty, maxW + 2 * pad, totH + 2 * pad, 8, 8);
		g2.setColor(Color.DARK_GRAY);
		g2.setStroke(new BasicStroke(1.0f));
		g2.drawRoundRect(tx, ty, maxW + 2 * pad, totH + 2 * pad, 8, 8);
		g2.setColor(Color.BLACK);
		for (int i = 0; i < lines.length; i++)
			g2.drawString(lines[i], tx + pad, ty + pad + fm.getAscent() + i * fm.getHeight());
	}

	private boolean isDarkColor(Color c) {
		return (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue()) / 255.0 < 0.5;
	}

	// =========================================================================
	// Export
	// =========================================================================
	/**
	 * Compute tight bounding box around all visible graph elements,
	 * including node labels, badges, self-loops, and edge curves.
	 */
	private Rectangle2D computeGraphBounds() {
		if (displayNodes == null || displayNodes.isEmpty() || graph == null) return null;

		double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;

		// Create a temporary Graphics2D for font metrics
		BufferedImage tmpImg = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D tmpG2 = tmpImg.createGraphics();
		tmpG2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		for (TransitionGraph.GraphNode n : displayNodes) {
			int r = computeNodeRadius(n.getPatternCount(), cachedMaxNodePC);
			double x = n.getX(), y = n.getY();

			// Node circle bounds
			double nodeLeft = x - r;
			double nodeRight = x + r;
			double nodeTop = y - r;
			double nodeBottom = y + r;

			// Check if label is drawn below the node (when it doesn't fit inside)
			String label = n.getItemLabel();
			int fs = Math.max(9, 11); // at scale 1.0
			tmpG2.setFont(new Font("SansSerif", Font.BOLD, fs));
			FontMetrics fm = tmpG2.getFontMetrics();
			int tw = fm.stringWidth(label);

			if (tw >= 2 * r - 4) {
				// Label drawn below the node
				double labelLeft = x - tw / 2.0;
				double labelRight = x + tw / 2.0;
				double labelBottom = y + r + fm.getAscent() + 2 + fm.getDescent();
				nodeLeft = Math.min(nodeLeft, labelLeft);
				nodeRight = Math.max(nodeRight, labelRight);
				nodeBottom = Math.max(nodeBottom, labelBottom);
			}

			// Badge bounds (drawn at top-right of node)
			if (showPatternCountBadge) {
				Integer cnt = patternCountsPerNode.get(n.getItemLabel());
				if (cnt != null && cnt > 0) {
					String t = String.valueOf(cnt);
					tmpG2.setFont(new Font("SansSerif", Font.BOLD, Math.max(8, 9)));
					FontMetrics bfm = tmpG2.getFontMetrics();
					int btw = bfm.stringWidth(t);
					int bw = Math.max(btw + 6, 14);
					int bh = bfm.getHeight() + 2;
					double bx = x + r * 0.6;
					double by = y - r * 0.6 - bh / 2.0;
					nodeRight = Math.max(nodeRight, bx + bw);
					nodeTop = Math.min(nodeTop, by);
				}
			}

			// Self-loop bounds
			for (TransitionGraph.GraphEdge edge : displayEdges) {
				if (!shouldDrawEdge(edge)) continue;
				if (edge.getSourceItem().equals(n.getItemLabel()) 
						&& edge.getTargetItem().equals(n.getItemLabel())) {
					double ls = r + 18;
					nodeTop = Math.min(nodeTop, y - ls * 2);
					nodeLeft = Math.min(nodeLeft, x - ls / 2.0);
					nodeRight = Math.max(nodeRight, x + ls / 2.0);
				}
			}

			if (nodeLeft < minX) minX = nodeLeft;
			if (nodeRight > maxX) maxX = nodeRight;
			if (nodeTop < minY) minY = nodeTop;
			if (nodeBottom > maxY) maxY = nodeBottom;
		}

		// Account for curved edges that may extend beyond node bounds
		for (TransitionGraph.GraphEdge edge : displayEdges) {
			if (!shouldDrawEdge(edge)) continue;
			if (edge.getSourceItem().equals(edge.getTargetItem())) continue; // already handled

			TransitionGraph.GraphNode src = graph.getNodes().get(edge.getSourceItem());
			TransitionGraph.GraphNode tgt = graph.getNodes().get(edge.getTargetItem());
			if (src == null || tgt == null) continue;

			double co = computeCurveOffset(edge);
			if (Math.abs(co) > 1.0) {
				double x1 = src.getX(), y1 = src.getY();
				double x2 = tgt.getX(), y2 = tgt.getY();
				double ang = Math.atan2(y2 - y1, x2 - x1);
				double mx = (x1 + x2) / 2, my = (y1 + y2) / 2;
				double cx = mx + (-Math.sin(ang)) * co;
				double cy = my + Math.cos(ang) * co;

				// Control point of the curve
				if (cx < minX) minX = cx;
				if (cx > maxX) maxX = cx;
				if (cy < minY) minY = cy;
				if (cy > maxY) maxY = cy;
			}

			// Arrow heads extend beyond target node
			double arrowSize = computeArrowSize(edge.getWeight(), cachedMaxWeight);
			int tr = computeNodeRadius(tgt.getPatternCount(), cachedMaxNodePC);
			double ang = Math.atan2(tgt.getY() - src.getY(), tgt.getX() - src.getX());
			double ax = tgt.getX() - tr * Math.cos(ang);
			double ay = tgt.getY() - tr * Math.sin(ang);
			double arrowExtend = arrowSize + 2;
			if (ax - arrowExtend < minX) minX = ax - arrowExtend;
			if (ax + arrowExtend > maxX) maxX = ax + arrowExtend;
			if (ay - arrowExtend < minY) minY = ay - arrowExtend;
			if (ay + arrowExtend > maxY) maxY = ay + arrowExtend;
		}

		tmpG2.dispose();

		if (minX > maxX) return null;

		// Add a small safety margin (2px at export scale)
		double margin = 2.0;
		return new Rectangle2D.Double(minX - margin, minY - margin,
				maxX - minX + 2 * margin, maxY - minY + 2 * margin);
	}

	public void exportToPNG(File file, double scaleFactor, int padding, boolean transparent)
			throws IOException {
		if (graph == null || displayNodes == null || displayEdges == null)
			throw new IOException("No graph to export.");

		Rectangle2D bounds = computeGraphBounds();
		if (bounds == null) throw new IOException("No visible nodes to export.");

		int imgW = (int) Math.ceil(bounds.getWidth() * scaleFactor) + 2 * padding;
		int imgH = (int) Math.ceil(bounds.getHeight() * scaleFactor) + 2 * padding;

		if (imgW <= 0 || imgH <= 0) throw new IOException("Computed image size is invalid.");
		if ((long) imgW * imgH > 100_000_000L)
			throw new IOException("Image would be too large (" + imgW + "x" + imgH + "). Reduce the scale factor.");

		int imgType = transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
		BufferedImage img = new BufferedImage(imgW, imgH, imgType);
		Graphics2D g2 = img.createGraphics();

		if (!transparent) { g2.setColor(BACKGROUND_COLOR); g2.fillRect(0, 0, imgW, imgH); }

		double exportPanX = -bounds.getX() * scaleFactor + padding;
		double exportPanY = -bounds.getY() * scaleFactor + padding;

		String savedHN = hoveredNode; String savedHE = hoveredEdgeKey;
		hoveredNode = null; hoveredEdgeKey = null;

		renderGraph(g2, scaleFactor, exportPanX, exportPanY, imgW, imgH, false);

		hoveredNode = savedHN; hoveredEdgeKey = savedHE;

		g2.dispose();
		ImageIO.write(img, "PNG", file);
	}

	public void exportToPNG(File file) throws IOException { 
		exportToPNG(file, 2.0, 20, false);  // Changed padding from 60 to 20
	}

	public void showExportDialog(JFrame parentFrame, File file) throws IOException {
		if (graph == null || displayNodes == null || displayEdges == null)
			throw new IOException("No graph to export.");

		Rectangle2D bounds = computeGraphBounds();
		if (bounds == null) throw new IOException("No visible nodes to export.");

		JDialog dialog = new JDialog(parentFrame, "Export PNG Options", true);
		dialog.setLocationRelativeTo(parentFrame);

		JPanel content = new JPanel(new BorderLayout(10, 10));
		content.setBorder(new EmptyBorder(15, 15, 15, 15));

		JPanel fields = new JPanel(new GridBagLayout());
		GridBagConstraints g = new GridBagConstraints();
		g.insets = new Insets(4, 4, 4, 4);
		g.fill = GridBagConstraints.HORIZONTAL;
		g.anchor = GridBagConstraints.WEST;

		g.gridx = 0; g.gridy = 0; g.weightx = 0;
		fields.add(new JLabel("Quality preset:"), g);
		String[] presets = {"Standard (1x)", "High (2x)", "Very High (3x)", "Ultra (4x)", "Custom..."};
		JComboBox<String> presetCombo = new JComboBox<>(presets);
		presetCombo.setSelectedIndex(1);
		g.gridx = 1; g.weightx = 1.0;
		fields.add(presetCombo, g);

		g.gridx = 0; g.gridy = 1; g.weightx = 0;
		fields.add(new JLabel("Scale factor:"), g);
		JSpinner scaleSp = new JSpinner(new SpinnerNumberModel(2.0, 0.5, 10.0, 0.5));
		scaleSp.setEditor(new JSpinner.NumberEditor(scaleSp, "0.0"));
		scaleSp.setEnabled(false);
		g.gridx = 1; g.weightx = 1.0;
		fields.add(scaleSp, g);

		g.gridx = 0; g.gridy = 2; g.weightx = 0;
		fields.add(new JLabel("Padding (px):"), g);
		JSpinner padSp = new JSpinner(new SpinnerNumberModel(20, 0, 500, 5));
		padSp.setEditor(new JSpinner.NumberEditor(padSp, "#"));
		g.gridx = 1; g.weightx = 1.0;
		fields.add(padSp, g);

		g.gridx = 0; g.gridy = 3; g.weightx = 0; g.gridwidth = 2;
		JCheckBox transpCb = new JCheckBox("Transparent background", false);
		fields.add(transpCb, g);
		g.gridwidth = 1;

		// Info label showing graph bounds and output size
		g.gridx = 0; g.gridy = 4; g.gridwidth = 2;
		JLabel sizeLabel = new JLabel();
		sizeLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
		fields.add(sizeLabel, g);

		g.gridy = 5;
		JLabel boundsLabel = new JLabel();
		boundsLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
		boundsLabel.setForeground(Color.GRAY);
		fields.add(boundsLabel, g);
		g.gridwidth = 1;

		final Rectangle2D fBounds = bounds;
		boundsLabel.setText(String.format("Graph bounds: %.0f \u00d7 %.0f px (at 1x)",
				fBounds.getWidth(), fBounds.getHeight()));

		Runnable updateSize = () -> {
			double sc = ((Number) scaleSp.getValue()).doubleValue();
			int pad = ((Number) padSp.getValue()).intValue();
			int w = (int) Math.ceil(fBounds.getWidth() * sc) + 2 * pad;
			int h = (int) Math.ceil(fBounds.getHeight() * sc) + 2 * pad;
			sizeLabel.setText("Output: " + w + " \u00d7 " + h + " px");
		};

		presetCombo.addActionListener(e -> {
			int idx = presetCombo.getSelectedIndex();
			if (idx < 4) { 
				scaleSp.setValue(new double[]{1.0, 2.0, 3.0, 4.0}[idx]); 
				scaleSp.setEnabled(false); 
			} else {
				scaleSp.setEnabled(true);
			}
			updateSize.run();
		});
		scaleSp.addChangeListener(e -> updateSize.run());
		padSp.addChangeListener(e -> updateSize.run());
		updateSize.run();

		content.add(fields, BorderLayout.CENTER);

		JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		final boolean[] confirmed = {false};
		JButton okBtn = new JButton("Export");
		okBtn.addActionListener(e -> { confirmed[0] = true; dialog.dispose(); });
		JButton cancelBtn = new JButton("Cancel");
		cancelBtn.addActionListener(e -> dialog.dispose());
		btns.add(okBtn); btns.add(cancelBtn);
		content.add(btns, BorderLayout.SOUTH);

		dialog.setContentPane(content);
		dialog.pack();
		dialog.setMinimumSize(dialog.getPreferredSize());
		dialog.setResizable(false);
		dialog.setVisible(true);

		if (!confirmed[0]) throw new IOException("CANCELLED");

		exportToPNG(file, ((Number) scaleSp.getValue()).doubleValue(),
				((Number) padSp.getValue()).intValue(), transpCb.isSelected());
	}

	// =========================================================================
	// Scrollable
	// =========================================================================

	@Override
	public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }

	@Override
	public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) { return 20; }

	@Override
	public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) {
		return o == SwingConstants.VERTICAL ? r.height - 20 : r.width - 20;
	}
	
	/**
	 * Recalculate canvas size and revalidate for scroll pane updates.
	 * Called after layout recomputation.
	 */
	public void recalculateAndRevalidate() {
		recalculateCanvasSize();
		revalidate();
		repaint();
	}

	@Override
	public boolean getScrollableTracksViewportWidth() {
		// Only track viewport if the canvas fits within it
		if (getParent() instanceof javax.swing.JViewport) {
			javax.swing.JViewport vp = (javax.swing.JViewport) getParent();
			return calculatedCanvasSize.width <= vp.getWidth();
		}
		return true;
	}

	@Override
	public boolean getScrollableTracksViewportHeight() {
		// Only track viewport if the canvas fits within it
		if (getParent() instanceof javax.swing.JViewport) {
			javax.swing.JViewport vp = (javax.swing.JViewport) getParent();
			return calculatedCanvasSize.height <= vp.getHeight();
		}
		return true;
	}
}