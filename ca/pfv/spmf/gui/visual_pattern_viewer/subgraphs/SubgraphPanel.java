package ca.pfv.spmf.gui.visual_pattern_viewer.subgraphs;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.swing.JPanel;

import ca.pfv.spmf.gui.visual_pattern_viewer.MeasureTable;
import ca.pfv.spmf.gui.visual_pattern_viewer.ViewerStyle;
import ca.pfv.spmf.gui.visual_pattern_viewer.subgraphs.Subgraph.Edge;
import ca.pfv.spmf.gui.visual_pattern_viewer.subgraphs.Subgraph.Vertex;

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
 * SPMF is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPMF. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Do not remove copyright and license information.
 */
/**
 * Displays a subgraph pattern as a compact card. Vertices are circles arranged
 * on a circle whose size adapts to the number of vertices and to the length of
 * their labels (vertices with the same label share the same color), edges are
 * lines with their label at the midpoint, and the measures are listed below in
 * aligned columns.
 *
 * @author Philippe Fournier-Viger
 */
public class SubgraphPanel extends JPanel {

	private static final long serialVersionUID = 7312945670193847561L;

	/** Padding between the card border and its content */
	private static final int PAD = 10;
	/** Minimum radius of a vertex */
	private static final int MIN_VERTEX_RADIUS = 14;
	/** Horizontal space between the label and the circle border */
	private static final int VERTEX_TEXT_PAD = 6;
	/** Space kept between neighboring vertices on the layout circle */
	private static final int VERTEX_SPACING = 16;
	/** Extra room around the drawing */
	private static final int GRAPH_PAD = 4;
	/** Minimum width of the content area */
	private static final int MIN_CONTENT_WIDTH = 150;

	private static final BasicStroke EDGE_STROKE = new BasicStroke(1.5f);
	private static final BasicStroke VERTEX_STROKE = new BasicStroke(1.5f);

	/** The subgraph to display */
	private final Subgraph subgraph;
	/** Min values of the measures */
	private final Map<String, Double> minValues;
	/** Max values of the measures */
	private final Map<String, Double> maxValues;

	private boolean hover;
	private Dimension cachedPreferredSize;
	private MeasureTable measureTable;

	/** Positions and radii of the vertices, relative to the drawing origin */
	private static final class GraphLayout {
		final Map<Integer, Point> centers = new HashMap<>();
		final Map<Integer, Integer> radii = new HashMap<>();
		int size; // the drawing area is a square
	}

	/**
	 * Constructor
	 *
	 * @param subgraph  the subgraph to display (not null)
	 * @param minValues map of minimum measure values, or null
	 * @param maxValues map of maximum measure values, or null
	 */
	public SubgraphPanel(Subgraph subgraph, Map<String, Double> minValues, Map<String, Double> maxValues) {
		this.subgraph = Objects.requireNonNull(subgraph, "subgraph must not be null");
		this.minValues = minValues;
		this.maxValues = maxValues;
		setOpaque(false);
		setFont(ViewerStyle.itemFont());
		setToolTipText(buildToolTip());
		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				hover = true;
				repaint();
			}

			@Override
			public void mouseExited(MouseEvent e) {
				hover = false;
				repaint();
			}
		});
	}

	private MeasureTable measures() {
		if (measureTable == null) {
			measureTable = new MeasureTable(this, subgraph.getMeasures(), minValues, maxValues);
		}
		return measureTable;
	}

	private static String labelOf(Vertex v) {
		return v.getLabel() == null ? "" : v.getLabel();
	}

	// ------------------------------------------------------------------
	// Layout
	// ------------------------------------------------------------------

	private GraphLayout layoutGraph(FontMetrics fm) {
		GraphLayout layout = new GraphLayout();
		List<Vertex> vertices = subgraph.getVertices();
		int n = vertices.size();
		if (n == 0) {
			return layout;
		}

		int maxR = 0;
		for (Vertex v : vertices) {
			int r = Math.max(MIN_VERTEX_RADIUS, fm.stringWidth(labelOf(v)) / 2 + VERTEX_TEXT_PAD);
			layout.radii.put(v.getId(), r);
			maxR = Math.max(maxR, r);
		}

		if (n == 1) {
			layout.size = 2 * (maxR + GRAPH_PAD);
			layout.centers.put(vertices.get(0).getId(), new Point(layout.size / 2, layout.size / 2));
			return layout;
		}

		// Radius of the layout circle: enough room so neighbors do not touch,
		// and so an edge label fits between two vertices.
		double circumference = n * (2.0 * maxR + VERTEX_SPACING);
		double radius = Math.max(circumference / (2 * Math.PI), maxR + VERTEX_SPACING);
		if (!subgraph.getEdges().isEmpty()) {
			radius = Math.max(radius, maxR + 24);
		}
		int r = (int) Math.ceil(radius);
		layout.size = 2 * (r + maxR + GRAPH_PAD);
		int c = layout.size / 2;
		for (int i = 0; i < n; i++) {
			double angle = 2.0 * Math.PI * i / n - Math.PI / 2.0;
			int x = (int) Math.round(c + r * Math.cos(angle));
			int y = (int) Math.round(c + r * Math.sin(angle));
			layout.centers.put(vertices.get(i).getId(), new Point(x, y));
		}
		return layout;
	}

	@Override
	public Dimension getPreferredSize() {
		if (cachedPreferredSize == null) {
			FontMetrics fm = getFontMetrics(ViewerStyle.itemFont());
			GraphLayout layout = layoutGraph(fm);
			int contentW = Math.max(MIN_CONTENT_WIDTH, Math.max(layout.size, measures().getPreferredWidth()));
			int contentH = layout.size + measures().getSectionHeight();
			cachedPreferredSize = new Dimension(contentW + 2 * PAD + ViewerStyle.SHADOW_DX,
					contentH + 2 * PAD + ViewerStyle.SHADOW_DY);
		}
		return new Dimension(cachedPreferredSize);
	}

	@Override
	public Dimension getMinimumSize() {
		return getPreferredSize();
	}

	// ------------------------------------------------------------------
	// Painting
	// ------------------------------------------------------------------

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		try {
			ViewerStyle.applyRenderingHints(g2);
			int w = getWidth();
			int h = getHeight();
			ViewerStyle.paintCard(g2, 0, 0, w, h, hover);

			int cardW = w - ViewerStyle.SHADOW_DX;
			int cardH = h - ViewerStyle.SHADOW_DY;
			int contentX = PAD;
			int contentW = cardW - 2 * PAD;
			if (contentW <= 0 || cardH <= 2 * PAD) {
				return;
			}

			MeasureTable mt = measures();
			int bodyTop = PAD;
			int bodyBottom = cardH - PAD - mt.getSectionHeight();

			FontMetrics fm = g2.getFontMetrics(ViewerStyle.itemFont());
			GraphLayout layout = layoutGraph(fm);
			int ox = contentX + Math.max(0, (contentW - layout.size) / 2);
			int oy = bodyTop + Math.max(0, (bodyBottom - bodyTop - layout.size) / 2);

			paintEdges(g2, layout, ox, oy);
			paintVertices(g2, layout, ox, oy, fm);

			mt.paintAtBottom(g2, contentX, cardH - PAD, contentW);
		} finally {
			g2.dispose();
		}
	}

	private void paintEdges(Graphics2D g2, GraphLayout layout, int ox, int oy) {
		g2.setStroke(EDGE_STROKE);
		g2.setFont(ViewerStyle.smallFont());
		FontMetrics sfm = g2.getFontMetrics();
		for (Edge edge : subgraph.getEdges()) {
			Point a = layout.centers.get(edge.getSourceId());
			Point b = layout.centers.get(edge.getTargetId());
			if (a == null || b == null) {
				continue;
			}
			int x1 = ox + a.x;
			int y1 = oy + a.y;
			int x2 = ox + b.x;
			int y2 = oy + b.y;
			g2.setColor(ViewerStyle.EDGE_COLOR);
			g2.drawLine(x1, y1, x2, y2);

			String label = edge.getLabel();
			if (label != null && !label.isEmpty()) {
				int midX = (x1 + x2) / 2;
				int midY = (y1 + y2) / 2;
				int lw = sfm.stringWidth(label) + 6;
				int lh = sfm.getHeight();
				int lx = midX - lw / 2;
				int ly = midY - lh / 2;
				g2.setColor(ViewerStyle.CARD_BG);
				g2.fillRoundRect(lx, ly, lw, lh, lh, lh);
				g2.setColor(ViewerStyle.SEPARATOR);
				g2.drawRoundRect(lx, ly, lw - 1, lh - 1, lh, lh);
				g2.setColor(ViewerStyle.TEXT_SECONDARY);
				g2.drawString(label, lx + 3, ly + sfm.getAscent());
			}
		}
	}

	private void paintVertices(Graphics2D g2, GraphLayout layout, int ox, int oy, FontMetrics fm) {
		g2.setStroke(VERTEX_STROKE);
		g2.setFont(ViewerStyle.itemFont());
		for (Vertex v : subgraph.getVertices()) {
			Point c = layout.centers.get(v.getId());
			Integer r = layout.radii.get(v.getId());
			if (c == null || r == null) {
				continue;
			}
			String label = labelOf(v);
			Color[] colors = ViewerStyle.chipColors(label);
			int x = ox + c.x - r;
			int y = oy + c.y - r;
			int d = 2 * r;
			g2.setColor(colors[0]);
			g2.fillOval(x, y, d, d);
			g2.setColor(colors[1]);
			g2.drawOval(x, y, d, d);
			g2.setColor(colors[2]);
			int tx = ox + c.x - fm.stringWidth(label) / 2;
			int ty = oy + c.y + (fm.getAscent() - fm.getDescent()) / 2;
			g2.drawString(label, tx, ty);
		}
	}

	// ------------------------------------------------------------------
	// Tooltip
	// ------------------------------------------------------------------

	private String buildToolTip() {
		List<Vertex> vertices = subgraph.getVertices();
		List<Edge> edges = subgraph.getEdges();
		StringBuilder sb = new StringBuilder("<html><b>Subgraph</b> (");
		sb.append(vertices.size()).append(vertices.size() == 1 ? " vertex, " : " vertices, ");
		sb.append(edges.size()).append(edges.size() == 1 ? " edge)" : " edges)");

		Map<Integer, String> labelById = new HashMap<>();
		StringBuilder vs = new StringBuilder();
		for (Vertex v : vertices) {
			labelById.put(v.getId(), labelOf(v));
			if (vs.length() > 0) {
				vs.append(", ");
			}
			vs.append(v.getId()).append(':').append(labelOf(v));
		}
		sb.append("<br><b>Vertices:</b> ").append(ViewerStyle.escapeHtml(ViewerStyle.abbreviate(vs.toString(), 300)));

		if (!edges.isEmpty()) {
			StringBuilder es = new StringBuilder();
			for (Edge e : edges) {
				if (es.length() > 0) {
					es.append(", ");
				}
				es.append(labelById.getOrDefault(e.getSourceId(), "?")).append('\u2013')
						.append(labelById.getOrDefault(e.getTargetId(), "?"));
				if (e.getLabel() != null && !e.getLabel().isEmpty()) {
					es.append(" (").append(e.getLabel()).append(')');
				}
			}
			sb.append("<br><b>Edges:</b> ").append(ViewerStyle.escapeHtml(ViewerStyle.abbreviate(es.toString(), 300)));
		}
		if (!subgraph.getMeasures().isEmpty()) {
			sb.append("<br><br>").append(MeasureTable.toHtmlRows(subgraph.getMeasures()));
		}
		return sb.append("</html>").toString();
	}

	/**
	 * Returns the subgraph displayed by this panel.
	 *
	 * @return the subgraph
	 */
	public Subgraph getSubgraph() {
		return subgraph;
	}
}