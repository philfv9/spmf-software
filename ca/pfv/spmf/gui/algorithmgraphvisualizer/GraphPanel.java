package ca.pfv.spmf.gui.algorithmgraphvisualizer;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ToolTipManager;

/*
 * Copyright (c) 2008-2025 Philippe Fournier-Viger. Part of SPMF (GPL v3).
 */
/**
 * Renders the algorithm graph with zoom/pan, groups (algorithms sharing the
 * same input/output types), five layouts, mouse + keyboard interaction and a
 * context menu.
 *
 * Layouts work in "world" coordinates centred on (0,0); a view transform maps
 * world to screen so nothing depends on the panel size at layout time.
 *
 * Keyboard (panel must have focus – click it): {@code +}/{@code -} zoom,
 * {@code 0} fit, arrows pan, {@code Esc} clear selection.
 */
public class GraphPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	public enum LayoutType {
		FORCE_DIRECTED("Force-directed"), CIRCULAR("Circular"), RADIAL("Radial by category"), GRID("Grid"),
		HIERARCHICAL("Rows by category");

		private final String label;

		LayoutType(String label) { this.label = label; }

		@Override
		public String toString() { return label; }
	}

	public interface GraphListener {
		/** node == null means the selection was cleared */
		void nodeSelected(AlgorithmNode node);

		/** positions or view (zoom/pan) changed */
		void graphChanged();

		/** the animated layout started or stopped */
		void layoutStateChanged(boolean running);
	}

	/** Group of visible nodes with identical clustering key. */
	public static final class Cluster {
		private final List<AlgorithmNode> members = new ArrayList<>();
		private double cx, cy;

		public List<AlgorithmNode> getMembers() { return members; }
		public int size() { return members.size(); }
	}

	private static final class Band {
		final String name;
		final Rectangle2D rect;
		final boolean shaded;

		Band(String name, Rectangle2D rect, boolean shaded) {
			this.name = name; this.rect = rect; this.shaded = shaded;
		}
	}

	private static final class WorldLabel {
		final String text;
		final double x, y;

		WorldLabel(String text, double x, double y) {
			this.text = text; this.x = x; this.y = y;
		}
	}

	// ---- visual constants ----
	private static final int NODE_RADIUS = 9;
	private static final double HULL_PADDING = 24;
	private static final double LABEL_MIN_ZOOM = 0.55;
	private static final double MIN_ZOOM = 0.04, MAX_ZOOM = 6.0;
	private static final float DIM_ALPHA = 0.18f;
	private static final Font LABEL_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 10);
	private static final Font LABEL_FONT_BOLD = new Font(Font.SANS_SERIF, Font.BOLD, 11);
	private static final Font BAND_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 13);
	private static final Color HIGHLIGHT = new Color(255, 210, 0);
	private static final Color HULL_FILL = new Color(90, 120, 170, 32);
	private static final Color HULL_BORDER = new Color(90, 120, 170, 110);
	private static final Color HULL_FILL_SEL = new Color(255, 190, 0, 60);
	private static final Color HULL_BORDER_SEL = new Color(225, 150, 0, 170);
	private static final Color GUIDE_COLOR = new Color(0, 0, 0, 25);

	// ---- layout spacing (world units) ----
	private static final double GRID_X = 120, GRID_Y = 70;
	private static final double CIRCLE_SPACING = 75;
	private static final double RADIAL_INNER = 160, RADIAL_RING_STEP = 62, RADIAL_SPACING = 72;
	private static final double HIER_X = 115, HIER_Y = 60, HIER_LABEL_WIDTH = 240, HIER_MAX_WIDTH = 1500;
	private static final double HIER_PAD = 18, HIER_GAP = 14;

	// ---- force-directed physics ----
	private static final double REPULSION = 12000;
	private static final double INTER_CLUSTER_REPULSION = 2.0;
	private static final double CLUSTER_ATTRACTION = 0.015;
	private static final double CENTERING = 0.0008;
	private static final double DAMPING = 0.8;
	private static final double MAX_VELOCITY = 12;
	private static final int MAX_ITERATIONS = 320;
	private static final int MIN_ITERATIONS = 60;
	private static final double CONVERGED_MOVEMENT = 0.08;
	private static final int FRAME_DELAY_MS = 25;

	// ---- data ----
	private final AlgorithmGraphModel model;
	private final List<AlgorithmNode> nodes;
	private GraphListener listener;

	// ---- options ----
	private boolean showLabels = true;
	private boolean showClusters = true;
	private boolean dimUnrelated = true;
	private boolean clusterByInput = true;
	private boolean clusterByOutput = true;

	// ---- clusters (cached) ----
	private final List<Cluster> clusters = new ArrayList<>();
	private final Map<AlgorithmNode, Cluster> nodeCluster = new HashMap<>();
	private boolean clustersDirty = true;

	// ---- view ----
	private double scale = 1.0, translateX = 0, translateY = 0;
	private boolean autoFit = true;

	// ---- interaction ----
	private AlgorithmNode selectedNode, hoveredNode, draggedNode;
	private Point lastMouse;
	private boolean panning, dragMoved;
	private final MouseAdapter mouseHandler;

	// ---- layout ----
	private LayoutType currentLayout = LayoutType.FORCE_DIRECTED;
	private Timer layoutTimer;
	private final List<Band> bands = new ArrayList<>();
	private final List<WorldLabel> worldLabels = new ArrayList<>();
	private final List<Line2D> guides = new ArrayList<>();
	private final Random random = new Random();

	public GraphPanel(AlgorithmGraphModel model) {
		this.model = model;
		this.nodes = model.getNodes();

		setBackground(Color.WHITE);
		setPreferredSize(new Dimension(800, 600));
		setFocusable(true);

		mouseHandler = createMouseHandler();
		addMouseListener(mouseHandler);
		addMouseMotionListener(mouseHandler);
		addMouseWheelListener(mouseHandler);
		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				if (autoFit) {
					zoomToFit();
				}
			}
		});
		installKeyBindings();
		ToolTipManager.sharedInstance().registerComponent(this);
	}

	// =====================================================================
	// Listener / options
	// =====================================================================

	public void setGraphListener(GraphListener listener) { this.listener = listener; }

	private void fireGraphChanged() {
		if (listener != null) {
			listener.graphChanged();
		}
	}

	private void fireLayoutState(boolean running) {
		if (listener != null) {
			listener.layoutStateChanged(running);
		}
	}

	public void setShowLabels(boolean b) { showLabels = b; repaint(); }
	public void setShowClusters(boolean b) { showClusters = b; repaint(); }
	public boolean isShowClusters() { return showClusters; }
	public void setDimUnrelated(boolean b) { dimUnrelated = b; repaint(); }
	public void setClusterByInput(boolean b) { clusterByInput = b; invalidateClusters(); repaint(); }
	public void setClusterByOutput(boolean b) { clusterByOutput = b; invalidateClusters(); repaint(); }

	public AlgorithmNode getSelectedNode() { return selectedNode; }
	public double getScale() { return scale; }
	public LayoutType getCurrentLayout() { return currentLayout; }
	public boolean isLayoutRunning() { return layoutTimer != null && layoutTimer.isRunning(); }

	public int getPinnedCount() {
		int n = 0;
		for (AlgorithmNode node : nodes) {
			if (node.isPinned()) {
				n++;
			}
		}
		return n;
	}

	public void unpinAll() {
		for (AlgorithmNode node : nodes) {
			node.setPinned(false);
		}
		repaint();
	}

	// =====================================================================
	// Selection
	// =====================================================================

	/** Select a node (or null to clear), highlight its group and notify the listener. */
	public void selectNode(AlgorithmNode node) {
		selectedNode = node;
		for (AlgorithmNode n : nodes) {
			n.setHighlighted(false);
		}
		if (node != null) {
			node.setHighlighted(true);
			for (AlgorithmNode m : getClusterMates(node)) {
				m.setHighlighted(true);
			}
		}
		repaint();
		if (listener != null) {
			listener.nodeSelected(node);
		}
	}

	public void clearSelection() {
		if (selectedNode != null) {
			selectNode(null);
		}
	}

	/** Re-apply highlighting / notify listener after filters or cluster settings changed. */
	public void refreshSelection() {
		if (selectedNode != null && !selectedNode.isVisible()) {
			selectNode(null);
		} else {
			selectNode(selectedNode);
		}
	}

	// =====================================================================
	// Clusters
	// =====================================================================

	public void invalidateClusters() { clustersDirty = true; }

	private String clusterKey(AlgorithmNode n) {
		if (!clusterByInput && !clusterByOutput) {
			return null;
		}
		return (clusterByInput ? n.getInputKey() : "*") + "\u0000" + (clusterByOutput ? n.getOutputKey() : "*");
	}

	private void ensureClusters() {
		if (!clustersDirty) {
			return;
		}
		clusters.clear();
		nodeCluster.clear();
		Map<String, Cluster> byKey = new LinkedHashMap<>();
		for (AlgorithmNode n : nodes) {
			if (!n.isVisible()) {
				continue;
			}
			String key = clusterKey(n);
			if (key == null) {
				continue;
			}
			Cluster c = byKey.computeIfAbsent(key, k -> new Cluster());
			c.members.add(n);
			nodeCluster.put(n, c);
		}
		clusters.addAll(byKey.values());
		updateCentroids();
		clustersDirty = false;
	}

	private void updateCentroids() {
		for (Cluster c : clusters) {
			double sx = 0, sy = 0;
			for (AlgorithmNode n : c.members) {
				sx += n.getX();
				sy += n.getY();
			}
			c.cx = sx / c.members.size();
			c.cy = sy / c.members.size();
		}
	}

	/** Number of groups with at least two members. */
	public int getClusterCount() {
		ensureClusters();
		int count = 0;
		for (Cluster c : clusters) {
			if (c.size() >= 2) {
				count++;
			}
		}
		return count;
	}

	/** Other visible nodes sharing the same clustering key, sorted by name. */
	public List<AlgorithmNode> getClusterMates(AlgorithmNode node) {
		ensureClusters();
		List<AlgorithmNode> mates = new ArrayList<>();
		Cluster c = nodeCluster.get(node);
		if (c != null) {
			for (AlgorithmNode m : c.members) {
				if (m != node) {
					mates.add(m);
				}
			}
			mates.sort(Comparator.comparing(AlgorithmNode::getName, String.CASE_INSENSITIVE_ORDER));
		}
		return mates;
	}

	// =====================================================================
	// View (zoom / pan)
	// =====================================================================

	private AffineTransform getViewTransform() {
		return new AffineTransform(scale, 0, 0, scale, translateX, translateY);
	}

	public Point2D toWorld(Point p) {
		return new Point2D.Double((p.x - translateX) / scale, (p.y - translateY) / scale);
	}

	/** Bounding box of visible nodes plus layout decorations, or null if nothing visible. */
	public Rectangle2D getWorldBounds() {
		Rectangle2D r = null;
		for (AlgorithmNode n : nodes) {
			if (!n.isVisible()) {
				continue;
			}
			if (r == null) {
				r = new Rectangle2D.Double(n.getX(), n.getY(), 0, 0);
			} else {
				r.add(n.getX(), n.getY());
			}
		}
		if (r != null) {
			for (Band b : bands) {
				r.add(b.rect);
			}
			for (WorldLabel l : worldLabels) {
				r.add(new Rectangle2D.Double(l.x - 90, l.y - 12, 180, 24));
			}
		}
		return r;
	}

	/** World rectangle currently visible on screen. */
	public Rectangle2D getVisibleWorldRect() {
		return new Rectangle2D.Double(-translateX / scale, -translateY / scale, getWidth() / scale,
				getHeight() / scale);
	}

	public void zoomToFit() {
		Rectangle2D b = getWorldBounds();
		int w = getWidth(), h = getHeight();
		if (b == null || w <= 0 || h <= 0) {
			return;
		}
		double margin = 70;
		double bw = b.getWidth() + 2 * margin, bh = b.getHeight() + 2 * margin;
		scale = clamp(Math.min(w / bw, h / bh), MIN_ZOOM, 1.5);
		translateX = w / 2.0 - b.getCenterX() * scale;
		translateY = h / 2.0 - b.getCenterY() * scale;
		autoFit = true;
		repaint();
		fireGraphChanged();
	}

	/** Zoom by a factor keeping the world point under {@code anchor} (screen coordinates) fixed. */
	public void zoomBy(double factor, Point anchor) {
		double newScale = clamp(scale * factor, MIN_ZOOM, MAX_ZOOM);
		if (newScale == scale) {
			return;
		}
		Point2D w = toWorld(anchor);
		scale = newScale;
		translateX = anchor.x - w.getX() * scale;
		translateY = anchor.y - w.getY() * scale;
		autoFit = false;
		repaint();
		fireGraphChanged();
	}

	public void pan(double dx, double dy) {
		translateX += dx;
		translateY += dy;
		autoFit = false;
		repaint();
		fireGraphChanged();
	}

	/** Centre the view on a world coordinate (used by the minimap). */
	public void centerOn(double wx, double wy) {
		translateX = getWidth() / 2.0 - wx * scale;
		translateY = getHeight() / 2.0 - wy * scale;
		autoFit = false;
		repaint();
		fireGraphChanged();
	}

	/** Centre on a node, zooming in if the view is currently far out. */
	public void revealNode(AlgorithmNode n) {
		if (n == null) {
			return;
		}
		if (scale < 0.9) {
			scale = 1.0;
		}
		centerOn(n.getX(), n.getY());
	}

	private static double clamp(double v, double min, double max) {
		return Math.max(min, Math.min(max, v));
	}

	// =====================================================================
	// Keyboard
	// =====================================================================

	private void installKeyBindings() {
		Point center = null; // computed lazily
		bind("PLUS", "zoomIn", () -> zoomBy(1.25, centerPoint()));
		bind("EQUALS", "zoomIn", () -> zoomBy(1.25, centerPoint()));
		bind("shift EQUALS", "zoomIn", () -> zoomBy(1.25, centerPoint()));
		bind("ADD", "zoomIn", () -> zoomBy(1.25, centerPoint()));
		bind("MINUS", "zoomOut", () -> zoomBy(0.8, centerPoint()));
		bind("SUBTRACT", "zoomOut", () -> zoomBy(0.8, centerPoint()));
		bind("0", "fit", this::zoomToFit);
		bind("NUMPAD0", "fit", this::zoomToFit);
		bind("LEFT", "panLeft", () -> pan(60, 0));
		bind("RIGHT", "panRight", () -> pan(-60, 0));
		bind("UP", "panUp", () -> pan(0, 60));
		bind("DOWN", "panDown", () -> pan(0, -60));
		bind("ESCAPE", "clearSelection", this::clearSelection);
		assert center == null;
	}

	private Point centerPoint() {
		return new Point(getWidth() / 2, getHeight() / 2);
	}

	private void bind(String stroke, String name, Runnable action) {
		KeyStroke ks = KeyStroke.getKeyStroke(stroke);
		if (ks == null) {
			return;
		}
		getInputMap(JComponent.WHEN_FOCUSED).put(ks, name);
		getActionMap().put(name, new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
				action.run();
			}
		});
	}

	// =====================================================================
	// Mouse interaction
	// =====================================================================

	private MouseAdapter createMouseHandler() {
		return new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				requestFocusInWindow();
				if (e.isPopupTrigger()) {
					showPopup(e);
					return;
				}
				if (!SwingUtilities.isLeftMouseButton(e)) {
					return;
				}
				lastMouse = e.getPoint();
				dragMoved = false;
				AlgorithmNode hit = findNodeAt(e.getPoint());
				if (hit != null) {
					draggedNode = hit;
					selectNode(hit);
				} else {
					panning = true;
					setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
				}
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				if (e.isPopupTrigger()) {
					showPopup(e);
				} else if (panning && !dragMoved && SwingUtilities.isLeftMouseButton(e)) {
					selectNode(null);
				}
				draggedNode = null;
				panning = false;
				lastMouse = null;
				updateCursor(e.getPoint());
			}

			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
					AlgorithmNode hit = findNodeAt(e.getPoint());
					if (hit == null) {
						zoomToFit();
					} else {
						revealNode(hit);
					}
				}
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				if (lastMouse == null) {
					return;
				}
				int dx = e.getX() - lastMouse.x, dy = e.getY() - lastMouse.y;
				if (dx != 0 || dy != 0) {
					dragMoved = true;
				}
				if (draggedNode != null) {
					Point2D w = toWorld(e.getPoint());
					draggedNode.setPosition(w.getX(), w.getY());
					draggedNode.stop();
					draggedNode.setPinned(true); // user placed it: keep it there
					repaint();
					fireGraphChanged();
				} else if (panning) {
					pan(dx, dy);
				}
				lastMouse = e.getPoint();
			}

			@Override
			public void mouseMoved(MouseEvent e) {
				AlgorithmNode hit = findNodeAt(e.getPoint());
				if (hit != hoveredNode) {
					hoveredNode = hit;
					repaint();
				}
				updateCursor(e.getPoint());
			}

			@Override
			public void mouseExited(MouseEvent e) {
				if (hoveredNode != null) {
					hoveredNode = null;
					repaint();
				}
			}

			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				zoomBy(Math.pow(1.1, -e.getPreciseWheelRotation()), e.getPoint());
			}
		};
	}

	private void updateCursor(Point p) {
		setCursor(findNodeAt(p) != null ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
				: Cursor.getDefaultCursor());
	}

	private AlgorithmNode findNodeAt(Point screen) {
		Point2D w = toWorld(screen);
		double hit = Math.max(NODE_RADIUS + 1, 7 / scale);
		double hit2 = hit * hit;
		for (int i = nodes.size() - 1; i >= 0; i--) { // top-most drawn wins
			AlgorithmNode n = nodes.get(i);
			if (n.isVisible() && n.distanceSq(w.getX(), w.getY()) <= hit2) {
				return n;
			}
		}
		return null;
	}

	private void showPopup(MouseEvent e) {
		AlgorithmNode n = findNodeAt(e.getPoint());
		JPopupMenu menu = new JPopupMenu();
		if (n != null) {
			selectNode(n);
			JMenuItem title = new JMenuItem(n.getName());
			title.setEnabled(false);
			menu.add(title);
			menu.addSeparator();
			menu.add(item(n.isPinned() ? "Unpin position" : "Pin position", () -> {
				n.setPinned(!n.isPinned());
				repaint();
			}));
			menu.add(item("Center on this node", () -> revealNode(n)));
			menu.add(item("Copy name", () -> Toolkit.getDefaultToolkit().getSystemClipboard()
					.setContents(new StringSelection(n.getName()), null)));
			String url = n.getDescription() == null ? null : n.getDescription().getURLOfDocumentation();
			JMenuItem doc = item("Open documentation", () -> AlgorithmDetailPanel.openInBrowser(this, url));
			doc.setEnabled(url != null && !url.trim().isEmpty());
			menu.add(doc);
		} else {
			menu.add(item("Fit to view", this::zoomToFit));
			JMenuItem clear = item("Clear selection", this::clearSelection);
			clear.setEnabled(selectedNode != null);
			menu.add(clear);
			JMenuItem unpin = item("Unpin all nodes", this::unpinAll);
			unpin.setEnabled(getPinnedCount() > 0);
			menu.add(unpin);
		}
		menu.show(this, e.getX(), e.getY());
	}

	private static JMenuItem item(String text, Runnable r) {
		JMenuItem mi = new JMenuItem(text);
		mi.addActionListener(e -> r.run());
		return mi;
	}

	@Override
	public String getToolTipText(MouseEvent e) {
		AlgorithmNode n = findNodeAt(e.getPoint());
		if (n == null) {
			return null;
		}
		StringBuilder sb = new StringBuilder("<html><b>").append(AlgorithmDetailPanel.esc(n.getName()))
				.append("</b><br>").append(AlgorithmDetailPanel.esc(n.getCategory()));
		sb.append("<br><span style='color:#555'>").append(AlgorithmDetailPanel.esc(n.getSignatureLabel()))
				.append("</span>");
		if (n.isTool()) {
			sb.append("<br><i>tool</i>");
		}
		if (n.isPinned()) {
			sb.append("<br><i>pinned</i>");
		}
		return sb.append("</html>").toString();
	}

	// =====================================================================
	// Painting
	// =====================================================================

	private static void setHints(Graphics2D g2) {
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		setHints(g2);
		if (getWorldBounds() == null) {
			g2.setColor(Color.GRAY);
			g2.setFont(BAND_FONT);
			String msg = "No algorithm matches the current filter";
			g2.drawString(msg, (getWidth() - g2.getFontMetrics().stringWidth(msg)) / 2, getHeight() / 2);
		} else {
			Rectangle2D cull = getVisibleWorldRect();
			cull = new Rectangle2D.Double(cull.getX() - 80, cull.getY() - 80, cull.getWidth() + 160,
					cull.getHeight() + 160);
			paintGraph(g2, getViewTransform(), scale, cull);
		}
		g2.dispose();
	}

	/**
	 * @param cull world rectangle outside of which nodes/labels are skipped, or null
	 */
	private void paintGraph(Graphics2D g2, AffineTransform view, double zoom, Rectangle2D cull) {
		g2.transform(view);
		drawDecorations(g2);
		if (showClusters) {
			drawClusters(g2);
		}
		drawNodes(g2, cull);
		if (showLabels) {
			drawLabels(g2, zoom, cull);
		}
		if (showClusters) {
			drawSelectedClusterLabel(g2);
		}
	}

	private boolean dimming() {
		return dimUnrelated && selectedNode != null;
	}

	private void drawDecorations(Graphics2D g2) {
		if (!guides.isEmpty()) {
			g2.setColor(GUIDE_COLOR);
			for (Line2D l : guides) {
				g2.draw(l);
			}
		}
		g2.setFont(BAND_FONT);
		FontMetrics fm = g2.getFontMetrics();
		for (Band b : bands) {
			g2.setColor(b.shaded ? new Color(0, 0, 0, 12) : new Color(0, 0, 0, 4));
			g2.fill(b.rect);
			g2.setColor(new Color(50, 50, 50));
			g2.drawString(fit(b.name, fm, HIER_LABEL_WIDTH - 24), (float) (b.rect.getX() + 12),
					(float) (b.rect.getY() + 22));
		}
		for (WorldLabel l : worldLabels) {
			String t = fit(l.text, fm, 180);
			float tw = fm.stringWidth(t);
			g2.setColor(new Color(255, 255, 255, 200));
			g2.fill(new Rectangle2D.Double(l.x - tw / 2 - 3, l.y - fm.getAscent(), tw + 6, fm.getHeight()));
			g2.setColor(new Color(50, 50, 50));
			g2.drawString(t, (float) (l.x - tw / 2), (float) l.y);
		}
	}

	private static String fit(String s, FontMetrics fm, double maxWidth) {
		if (fm.stringWidth(s) <= maxWidth) {
			return s;
		}
		String t = s;
		while (t.length() > 1 && fm.stringWidth(t + "\u2026") > maxWidth) {
			t = t.substring(0, t.length() - 1);
		}
		return t + "\u2026";
	}

	private void drawClusters(Graphics2D g2) {
		ensureClusters();
		Cluster selectedCluster = selectedNode == null ? null : nodeCluster.get(selectedNode);
		Composite normal = g2.getComposite();
		Composite dim = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, DIM_ALPHA);
		Stroke old = g2.getStroke();
		g2.setStroke(new BasicStroke(1.2f));
		for (Cluster c : clusters) {
			if (c.size() < 2) {
				continue;
			}
			boolean sel = c == selectedCluster;
			g2.setComposite(dimming() && !sel ? dim : normal);
			Shape hull = buildHullShape(c);
			g2.setColor(sel ? HULL_FILL_SEL : HULL_FILL);
			g2.fill(hull);
			g2.setColor(sel ? HULL_BORDER_SEL : HULL_BORDER);
			g2.draw(hull);
		}
		g2.setComposite(normal);
		g2.setStroke(old);
	}

	private void drawSelectedClusterLabel(Graphics2D g2) {
		if (selectedNode == null) {
			return;
		}
		Cluster c = nodeCluster.get(selectedNode);
		if (c == null || c.size() < 2) {
			return;
		}
		Rectangle2D b = buildHullShape(c).getBounds2D();
		g2.setFont(LABEL_FONT_BOLD);
		FontMetrics fm = g2.getFontMetrics();
		String text = fit(c.size() + " algorithms \u00b7 " + selectedNode.getSignatureLabel(), fm, 520);
		float tw = fm.stringWidth(text);
		float x = (float) (b.getCenterX() - tw / 2), y = (float) (b.getMinY() - 8);
		g2.setColor(new Color(255, 250, 225, 235));
		g2.fill(new Rectangle2D.Double(x - 4, y - fm.getAscent() - 1, tw + 8, fm.getHeight() + 2));
		g2.setColor(new Color(120, 80, 0));
		g2.drawString(text, x, y);
	}

	/** Convex hull of the cluster members, inflated by HULL_PADDING with rounded corners. */
	private Shape buildHullShape(Cluster c) {
		List<Point2D.Double> pts = new ArrayList<>();
		for (AlgorithmNode m : c.members) {
			pts.add(new Point2D.Double(m.getX(), m.getY()));
		}
		List<Point2D.Double> hull = convexHull(pts);
		Path2D.Double path = new Path2D.Double();
		for (int i = 0; i < hull.size(); i++) {
			Point2D.Double p = hull.get(i);
			if (i == 0) {
				path.moveTo(p.x, p.y);
			} else {
				path.lineTo(p.x, p.y);
			}
		}
		if (hull.size() >= 3) {
			path.closePath();
		}
		BasicStroke stroke = new BasicStroke((float) (2 * HULL_PADDING), BasicStroke.CAP_ROUND,
				BasicStroke.JOIN_ROUND);
		Area area = new Area(stroke.createStrokedShape(path));
		if (hull.size() >= 3) {
			area.add(new Area(path));
		}
		return area;
	}

	/** Andrew's monotone chain. */
	private static List<Point2D.Double> convexHull(List<Point2D.Double> input) {
		List<Point2D.Double> p = new ArrayList<>(input);
		p.sort((a, b) -> a.x != b.x ? Double.compare(a.x, b.x) : Double.compare(a.y, b.y));
		int n = p.size();
		if (n < 3) {
			return p;
		}
		Point2D.Double[] h = new Point2D.Double[2 * n];
		int k = 0;
		for (int i = 0; i < n; i++) {
			while (k >= 2 && cross(h[k - 2], h[k - 1], p.get(i)) <= 0) {
				k--;
			}
			h[k++] = p.get(i);
		}
		for (int i = n - 2, t = k + 1; i >= 0; i--) {
			while (k >= t && cross(h[k - 2], h[k - 1], p.get(i)) <= 0) {
				k--;
			}
			h[k++] = p.get(i);
		}
		return new ArrayList<>(Arrays.asList(h).subList(0, Math.max(1, k - 1)));
	}

	private static double cross(Point2D.Double o, Point2D.Double a, Point2D.Double b) {
		return (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x);
	}

	private void drawNodes(Graphics2D g2, Rectangle2D cull) {
		Stroke thin = new BasicStroke(1f), thick = new BasicStroke(2.5f);
		Composite normal = g2.getComposite();
		Composite dim = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, DIM_ALPHA);
		boolean dimming = dimming();
		Ellipse2D.Double circle = new Ellipse2D.Double();

		for (AlgorithmNode n : nodes) {
			if (!n.isVisible()) {
				continue;
			}
			double x = n.getX(), y = n.getY();
			if (cull != null && !cull.contains(x, y)) {
				continue;
			}
			boolean emphasized = n == selectedNode || n == hoveredNode || n.isHighlighted();
			g2.setComposite(dimming && !emphasized ? dim : normal);

			double r = NODE_RADIUS + (n == hoveredNode ? 2 : 0);
			if (n.isHighlighted()) {
				g2.setColor(HIGHLIGHT);
				circle.setFrame(x - r - 5, y - r - 5, 2 * (r + 5), 2 * (r + 5));
				g2.fill(circle);
			}
			g2.setColor(n.getColor());
			circle.setFrame(x - r, y - r, 2 * r, 2 * r);
			g2.fill(circle);

			g2.setStroke(n == selectedNode ? thick : thin);
			g2.setColor(n == selectedNode ? Color.BLACK : new Color(60, 60, 60));
			g2.draw(circle);

			if (n.isPinned()) { // small pin marker
				g2.setStroke(thin);
				g2.setColor(Color.WHITE);
				circle.setFrame(x + r * 0.55 - 3.5, y - r * 0.55 - 3.5, 7, 7);
				g2.fill(circle);
				g2.setColor(new Color(40, 40, 40));
				circle.setFrame(x + r * 0.55 - 2.5, y - r * 0.55 - 2.5, 5, 5);
				g2.fill(circle);
			}
		}
		g2.setComposite(normal);
		g2.setStroke(thin);
	}

	private void drawLabels(Graphics2D g2, double zoom, Rectangle2D cull) {
		boolean drawAll = zoom >= LABEL_MIN_ZOOM;
		boolean dimming = dimming();
		Composite normal = g2.getComposite();
		Composite dim = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, DIM_ALPHA);
		Rectangle2D.Double box = new Rectangle2D.Double();

		for (AlgorithmNode n : nodes) {
			if (!n.isVisible()) {
				continue;
			}
			if (cull != null && !cull.contains(n.getX(), n.getY())) {
				continue;
			}
			boolean emphasized = n == selectedNode || n == hoveredNode || n.isHighlighted();
			if (!drawAll && !emphasized) {
				continue;
			}
			g2.setComposite(dimming && !emphasized ? dim : normal);
			g2.setFont(emphasized ? LABEL_FONT_BOLD : LABEL_FONT);
			FontMetrics fm = g2.getFontMetrics();

			String label = n.getName();
			if (!emphasized && label.length() > 22) {
				label = label.substring(0, 20) + "\u2026";
			}
			double tw = fm.stringWidth(label);
			double x = n.getX() - tw / 2;
			double y = n.getY() + NODE_RADIUS + fm.getAscent() + 3;

			g2.setColor(new Color(255, 255, 255, 210));
			box.setRect(x - 2, y - fm.getAscent(), tw + 4, fm.getHeight());
			g2.fill(box);
			g2.setColor(Color.BLACK);
			g2.drawString(label, (float) x, (float) y);
		}
		g2.setComposite(normal);
	}

	/**
	 * Render the whole graph (independent of the current zoom) into an image whose
	 * larger side is at most {@code maxDimension} pixels; null if nothing visible.
	 */
	public BufferedImage renderImage(int maxDimension) {
		Rectangle2D b = getWorldBounds();
		if (b == null) {
			return null;
		}
		double margin = 80;
		double bw = b.getWidth() + 2 * margin, bh = b.getHeight() + 2 * margin;
		double s = Math.min(1.5, Math.min(maxDimension / bw, maxDimension / bh));
		int w = (int) Math.ceil(bw * s), h = (int) Math.ceil(bh * s);

		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2 = img.createGraphics();
		g2.setColor(Color.WHITE);
		g2.fillRect(0, 0, w, h);
		setHints(g2);
		AffineTransform t = new AffineTransform(s, 0, 0, s, -(b.getX() - margin) * s, -(b.getY() - margin) * s);
		paintGraph(g2, t, 1.0, null); // zoom=1 → all labels, no culling
		g2.dispose();
		return img;
	}

	// =====================================================================
	// Layouts
	// =====================================================================

	public void stopLayoutTimer() {
		if (layoutTimer != null && layoutTimer.isRunning()) {
			layoutTimer.stop();
			fireLayoutState(false);
		}
	}

	/**
	 * @param type  layout to apply
	 * @param reset force-directed only: true discards current positions and pins;
	 *              false continues from the current positions
	 */
	public void applyLayout(LayoutType type, boolean reset) {
		stopLayoutTimer();
		currentLayout = type;
		bands.clear();
		worldLabels.clear();
		guides.clear();
		clustersDirty = true;

		List<AlgorithmNode> visible = sortedVisibleNodes();
		if (visible.isEmpty()) {
			repaint();
			fireGraphChanged();
			return;
		}

		switch (type) {
		case CIRCULAR:
			layoutCircular(visible);
			break;
		case RADIAL:
			layoutRadial();
			break;
		case GRID:
			layoutGrid(visible);
			break;
		case HIERARCHICAL:
			layoutHierarchical();
			break;
		case FORCE_DIRECTED:
		default:
			startForceDirected(visible, reset);
			return;
		}
		for (AlgorithmNode n : visible) {
			n.stop();
		}
		zoomToFit();
	}

	/** Visible nodes ordered by SPMF category order, then name. */
	private List<AlgorithmNode> sortedVisibleNodes() {
		Map<String, Integer> catIndex = new HashMap<>();
		int i = 0;
		for (String c : model.getCategoryNames()) {
			catIndex.put(c, i++);
		}
		List<AlgorithmNode> visible = model.getVisibleNodes();
		visible.sort(Comparator
				.comparingInt((AlgorithmNode n) -> catIndex.getOrDefault(n.getCategory(), Integer.MAX_VALUE))
				.thenComparing(AlgorithmNode::getName, String.CASE_INSENSITIVE_ORDER));
		return visible;
	}

	private void layoutCircular(List<AlgorithmNode> visible) {
		int n = visible.size();
		double radius = Math.max(200, n * CIRCLE_SPACING / (2 * Math.PI));
		for (int i = 0; i < n; i++) {
			double angle = 2 * Math.PI * i / n - Math.PI / 2;
			visible.get(i).setPosition(radius * Math.cos(angle), radius * Math.sin(angle));
		}
	}

	private void layoutGrid(List<AlgorithmNode> visible) {
		int n = visible.size();
		int cols = Math.max(1, (int) Math.ceil(Math.sqrt(n * 1.6)));
		int rows = (int) Math.ceil((double) n / cols);
		double w = (cols - 1) * GRID_X, h = (rows - 1) * GRID_Y;
		for (int i = 0; i < n; i++) {
			visible.get(i).setPosition((i % cols) * GRID_X - w / 2, (i / cols) * GRID_Y - h / 2);
		}
	}

	/** Each category is an angular sector; nodes fill concentric rings inside it. */
	private void layoutRadial() {
		List<String> names = new ArrayList<>();
		List<List<AlgorithmNode>> groups = new ArrayList<>();
		int total = 0;
		for (Map.Entry<String, List<AlgorithmNode>> e : model.getCategories().entrySet()) {
			List<AlgorithmNode> vis = new ArrayList<>();
			for (AlgorithmNode n : e.getValue()) {
				if (n.isVisible()) {
					vis.add(n);
				}
			}
			if (!vis.isEmpty()) {
				vis.sort(Comparator.comparing(AlgorithmNode::getName, String.CASE_INSENSITIVE_ORDER));
				names.add(e.getKey());
				groups.add(vis);
				total += vis.size();
			}
		}
		int k = groups.size();
		if (k == 0) {
			return;
		}
		double gap = Math.min(0.06, Math.PI / (2.0 * k));
		double usable = 2 * Math.PI - k * gap;
		double minSpan = Math.min(0.12, usable / k);
		double[] span = new double[k];
		double sum = 0;
		for (int i = 0; i < k; i++) {
			span[i] = Math.max(minSpan, usable * groups.get(i).size() / (double) total);
			sum += span[i];
		}
		for (int i = 0; i < k; i++) {
			span[i] *= usable / sum;
		}

		double a0 = -Math.PI / 2;
		double globalMaxR = RADIAL_INNER;
		List<Double> boundaries = new ArrayList<>();
		for (int i = 0; i < k; i++) {
			List<AlgorithmNode> g = groups.get(i);
			double s = span[i];
			int placed = 0, ring = 0;
			double maxR = RADIAL_INNER;
			while (placed < g.size()) {
				double r = RADIAL_INNER + ring * RADIAL_RING_STEP;
				int capacity = Math.max(1, (int) Math.floor(r * s / RADIAL_SPACING));
				int count = Math.min(capacity, g.size() - placed);
				for (int j = 0; j < count; j++) {
					double angle = a0 + s * (j + 0.5) / count;
					g.get(placed + j).setPosition(r * Math.cos(angle), r * Math.sin(angle));
				}
				placed += count;
				maxR = r;
				ring++;
			}
			globalMaxR = Math.max(globalMaxR, maxR);
			double mid = a0 + s / 2, lr = maxR + 48;
			worldLabels.add(new WorldLabel(names.get(i), lr * Math.cos(mid), lr * Math.sin(mid)));
			boundaries.add(a0 - gap / 2);
			a0 += s + gap;
		}
		double r0 = RADIAL_INNER - 45, r1 = globalMaxR + 30;
		for (double a : boundaries) {
			guides.add(new Line2D.Double(r0 * Math.cos(a), r0 * Math.sin(a), r1 * Math.cos(a), r1 * Math.sin(a)));
		}
	}

	private void layoutHierarchical() {
		int perRow = Math.max(1, (int) (HIER_MAX_WIDTH / HIER_X));
		int widest = 1;
		for (List<AlgorithmNode> list : model.getCategories().values()) {
			int count = 0;
			for (AlgorithmNode n : list) {
				if (n.isVisible()) {
					count++;
				}
			}
			widest = Math.max(widest, Math.min(count, perRow));
		}
		double bandWidth = HIER_LABEL_WIDTH + widest * HIER_X;

		double y = 0;
		boolean shaded = false;
		for (Map.Entry<String, List<AlgorithmNode>> e : model.getCategories().entrySet()) {
			List<AlgorithmNode> vis = new ArrayList<>();
			for (AlgorithmNode n : e.getValue()) {
				if (n.isVisible()) {
					vis.add(n);
				}
			}
			if (vis.isEmpty()) {
				continue;
			}
			vis.sort(Comparator.comparing(AlgorithmNode::getName, String.CASE_INSENSITIVE_ORDER));
			int rows = (int) Math.ceil((double) vis.size() / perRow);
			double bandHeight = rows * HIER_Y + 2 * HIER_PAD;
			for (int i = 0; i < vis.size(); i++) {
				double x = (i % perRow) * HIER_X + HIER_X / 2;
				double yy = y + HIER_PAD + (i / perRow) * HIER_Y + HIER_Y / 2;
				vis.get(i).setPosition(x, yy);
			}
			bands.add(new Band(e.getKey(), new Rectangle2D.Double(-HIER_LABEL_WIDTH, y, bandWidth, bandHeight),
					shaded));
			shaded = !shaded;
			y += bandHeight + HIER_GAP;
		}
	}

	// ---- force-directed ----

	private void startForceDirected(List<AlgorithmNode> active, boolean reset) {
		if (reset) {
			for (AlgorithmNode n : active) {
				n.setPinned(false);
			}
			initializeGridWithJitter(active);
		} else {
			// keep current positions; place only nodes that never had one, near the others
			double cx = 0, cy = 0;
			int placedCount = 0;
			for (AlgorithmNode n : active) {
				if (n.isPlaced()) {
					cx += n.getX();
					cy += n.getY();
					placedCount++;
				}
			}
			if (placedCount > 0) {
				cx /= placedCount;
				cy /= placedCount;
			}
			for (AlgorithmNode n : active) {
				if (!n.isPlaced()) {
					double a = random.nextDouble() * 2 * Math.PI, r = 100 + random.nextDouble() * 300;
					n.setPosition(cx + r * Math.cos(a), cy + r * Math.sin(a));
				}
			}
		}
		autoFit = true;
		zoomToFit();

		layoutTimer = new Timer(FRAME_DELAY_MS, null);
		layoutTimer.addActionListener(new java.awt.event.ActionListener() {
			private int iteration = 0;

			@Override
			public void actionPerformed(ActionEvent e) {
				double movement = forceStep(active, iteration++);
				repaint();
				boolean done = iteration >= MAX_ITERATIONS
						|| (iteration >= MIN_ITERATIONS && movement < CONVERGED_MOVEMENT);
				if (done) {
					layoutTimer.stop();
					for (AlgorithmNode n : active) {
						n.stop();
					}
					if (autoFit) {
						zoomToFit();
					} else {
						fireGraphChanged();
					}
					fireLayoutState(false);
				} else if (iteration % 4 == 0) {
					if (autoFit) {
						zoomToFit();
					} else {
						fireGraphChanged();
					}
				}
			}
		});
		layoutTimer.start();
		fireLayoutState(true);
	}

	private void initializeGridWithJitter(List<AlgorithmNode> active) {
		int n = active.size();
		int cols = Math.max(1, (int) Math.ceil(Math.sqrt(n * 1.5)));
		int rows = (int) Math.ceil((double) n / cols);
		double spacing = 100;
		double w = (cols - 1) * spacing, h = (rows - 1) * spacing;
		for (int i = 0; i < n; i++) {
			AlgorithmNode node = active.get(i);
			node.setPosition((i % cols) * spacing - w / 2 + (random.nextDouble() - 0.5) * 60,
					(i / cols) * spacing - h / 2 + (random.nextDouble() - 0.5) * 60);
			node.stop();
		}
	}

	/**
	 * One simulation step: repulsion between all nodes, attraction to the group
	 * centroid, centring. Pinned and dragged nodes exert forces but do not move.
	 *
	 * @return average displacement of the moving nodes (convergence measure)
	 */
	private double forceStep(List<AlgorithmNode> active, int iteration) {
		ensureClusters();
		updateCentroids();
		double cooling = 1.0 - (double) iteration / MAX_ITERATIONS;
		int n = active.size();
		double[] fx = new double[n], fy = new double[n];

		for (int i = 0; i < n; i++) {
			AlgorithmNode a = active.get(i);
			Cluster ca = nodeCluster.get(a);
			for (int j = i + 1; j < n; j++) {
				AlgorithmNode b = active.get(j);
				double dx = a.getX() - b.getX(), dy = a.getY() - b.getY();
				double d2 = dx * dx + dy * dy;
				if (d2 < 1.0) { // coincident: random kick
					dx = random.nextDouble() - 0.5;
					dy = random.nextDouble() - 0.5;
					d2 = dx * dx + dy * dy;
				}
				double d = Math.sqrt(d2);
				double rep = REPULSION / d2;
				if (ca == null || ca != nodeCluster.get(b)) {
					rep *= INTER_CLUSTER_REPULSION;
				}
				double ux = dx / d * rep, uy = dy / d * rep;
				fx[i] += ux;
				fy[i] += uy;
				fx[j] -= ux;
				fy[j] -= uy;
			}
			if (ca != null && ca.size() > 1) {
				fx[i] += (ca.cx - a.getX()) * CLUSTER_ATTRACTION;
				fy[i] += (ca.cy - a.getY()) * CLUSTER_ATTRACTION;
			}
			fx[i] -= a.getX() * CENTERING;
			fy[i] -= a.getY() * CENTERING;
		}

		double maxVel = MAX_VELOCITY * cooling + 0.2;
		double totalMove = 0;
		int moving = 0;
		for (int i = 0; i < n; i++) {
			AlgorithmNode a = active.get(i);
			if (a == draggedNode || a.isPinned()) {
				a.stop();
				continue;
			}
			double vx = (a.getVx() + fx[i] * cooling) * DAMPING;
			double vy = (a.getVy() + fy[i] * cooling) * DAMPING;
			double v = Math.hypot(vx, vy);
			if (v > maxVel) {
				vx *= maxVel / v;
				vy *= maxVel / v;
				v = maxVel;
			}
			a.setVelocity(vx, vy);
			a.setPosition(a.getX() + vx, a.getY() + vy);
			totalMove += v;
			moving++;
		}
		return moving == 0 ? 0 : totalMove / moving;
	}

	// =====================================================================
	// Disposal
	// =====================================================================

	public void dispose() {
		stopLayoutTimer();
		removeMouseListener(mouseHandler);
		removeMouseMotionListener(mouseHandler);
		removeMouseWheelListener(mouseHandler);
		ToolTipManager.sharedInstance().unregisterComponent(this);
		listener = null;
	}
}