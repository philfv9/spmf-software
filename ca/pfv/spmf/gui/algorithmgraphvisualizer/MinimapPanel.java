package ca.pfv.spmf.gui.algorithmgraphvisualizer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Rectangle2D;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/*
 * Copyright (c) 2008-2025 Philippe Fournier-Viger. Part of SPMF (GPL v3).
 */
/**
 * Overview of the whole graph with the current viewport.
 * <ul>
 * <li>Drag the viewport rectangle to pan the main view.</li>
 * <li>Click elsewhere to centre the main view on that point.</li>
 * <li>Mouse wheel zooms the main view; double-click fits it.</li>
 * </ul>
 */
public class MinimapPanel extends JPanel {

	private static final long serialVersionUID = 1L;
	private static final int PAD = 8;
	private static final Font SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 10);

	private final List<AlgorithmNode> nodes;
	private final GraphPanel graphPanel;
	private final MouseAdapter mouse;

	// mapping used in the last paint (world → minimap)
	private double scale = 0, offsetX = 0, offsetY = 0;
	private Rectangle2D viewportRect;

	// relative viewport dragging
	private boolean draggingViewport;
	private double dragDx, dragDy;

	public MinimapPanel(List<AlgorithmNode> nodes, GraphPanel graphPanel) {
		this.nodes = nodes;
		this.graphPanel = graphPanel;

		setBackground(new Color(245, 245, 245));
		setBorder(BorderFactory.createTitledBorder("Overview"));
		setPreferredSize(new Dimension(220, 160));
		setToolTipText("Drag the rectangle to pan, click to centre, wheel to zoom, double-click to fit");

		mouse = new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (!SwingUtilities.isLeftMouseButton(e) || scale <= 0) {
					return;
				}
				if (viewportRect != null && viewportRect.contains(e.getPoint())) {
					draggingViewport = true;
					dragDx = e.getX() - viewportRect.getCenterX();
					dragDy = e.getY() - viewportRect.getCenterY();
				} else {
					draggingViewport = false;
					centerAt(e.getX(), e.getY());
				}
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				if (scale <= 0) {
					return;
				}
				if (draggingViewport) {
					centerAt(e.getX() - dragDx, e.getY() - dragDy);
				} else {
					centerAt(e.getX(), e.getY());
				}
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				draggingViewport = false;
			}

			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
					graphPanel.zoomToFit();
				}
			}

			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				Point center = new Point(graphPanel.getWidth() / 2, graphPanel.getHeight() / 2);
				graphPanel.zoomBy(Math.pow(1.1, -e.getPreciseWheelRotation()), center);
			}
		};
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
		addMouseWheelListener(mouse);
	}

	private void centerAt(double px, double py) {
		graphPanel.centerOn((px - offsetX) / scale, (py - offsetY) / scale);
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		Insets in = getInsets();
		int x0 = in.left + PAD, y0 = in.top + PAD;
		int availW = getWidth() - in.left - in.right - 2 * PAD;
		int availH = getHeight() - in.top - in.bottom - 2 * PAD;

		Rectangle2D bounds = graphPanel.getWorldBounds();
		if (bounds == null || availW <= 0 || availH <= 0) {
			scale = 0;
			viewportRect = null;
			g2.setColor(Color.GRAY);
			g2.setFont(SMALL);
			g2.drawString("Nothing to show", x0, y0 + 12);
			g2.dispose();
			return;
		}

		double margin = 40;
		double bw = bounds.getWidth() + 2 * margin, bh = bounds.getHeight() + 2 * margin;
		scale = Math.min(availW / bw, availH / bh);
		offsetX = x0 + (availW - bw * scale) / 2 - (bounds.getX() - margin) * scale;
		offsetY = y0 + (availH - bh * scale) / 2 - (bounds.getY() - margin) * scale;

		g2.clipRect(x0, y0, availW, availH);

		// nodes
		AlgorithmNode selected = graphPanel.getSelectedNode();
		int total = 0, visible = 0;
		for (AlgorithmNode n : nodes) {
			total++;
			if (!n.isVisible()) {
				continue;
			}
			visible++;
			int x = (int) (n.getX() * scale + offsetX);
			int y = (int) (n.getY() * scale + offsetY);
			if (n == selected) {
				g2.setColor(new Color(255, 210, 0));
				g2.fillOval(x - 5, y - 5, 10, 10);
			}
			g2.setColor(n.getColor());
			g2.fillOval(x - 2, y - 2, 5, 5);
		}

		// viewport
		Rectangle2D v = graphPanel.getVisibleWorldRect();
		viewportRect = new Rectangle2D.Double(v.getX() * scale + offsetX, v.getY() * scale + offsetY,
				v.getWidth() * scale, v.getHeight() * scale);
		g2.setColor(new Color(30, 100, 220, 30));
		g2.fill(viewportRect);
		g2.setColor(new Color(30, 100, 220, 190));
		g2.setStroke(new BasicStroke(1.5f));
		g2.draw(viewportRect);

		// counter
		g2.setFont(SMALL);
		g2.setColor(new Color(90, 90, 90));
		String txt = visible + " / " + total;
		g2.drawString(txt, x0 + availW - g2.getFontMetrics().stringWidth(txt) - 2, y0 + availH - 2);

		g2.dispose();
	}

	public void dispose() {
		removeMouseListener(mouse);
		removeMouseMotionListener(mouse);
		removeMouseWheelListener(mouse);
	}
}