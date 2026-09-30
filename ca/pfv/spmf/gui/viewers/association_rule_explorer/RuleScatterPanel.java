package ca.pfv.spmf.gui.viewers.association_rule_explorer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/*
 * Copyright (c) 2008-2026 Philippe Fournier-Viger
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
 * Do not remove copyright and license.
 */
/**
 * 
 * Scatter plot of rules. Each rule is a point; X, Y, color and size are
 * 
 * measures chosen by the user. Supports hover tooltips (grouping overlapping
 * 
 * points), click / rectangle selection, wheel zoom, right-drag pan,
 * 
 * double-click reset, and reference lines at the "independence" value of
 * 
 * lift-like measures.
 * 
 * @author Philippe Fournier-Viger
 */
public class RuleScatterPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	/** Notified when the user selects rules (possibly an empty list). */
	public interface SelectionListener {
		void rulesSelected(List<ARule> rules);
	}

	/** Notified when the user right-clicks. */
	public interface PopupHandler {
		void showPopup(MouseEvent e, List<ARule> rulesUnderCursor);
	}

	private static final int MARGIN_LEFT = 72;
	private static final int MARGIN_RIGHT = 125;
	private static final int MARGIN_TOP = 26;
	private static final int MARGIN_BOTTOM = 48;
	private static final Color GRID_COLOR = new Color(230, 230, 230);
	private static final Color DEFAULT_POINT_COLOR = new Color(70, 130, 180);
	private static final Color BAND_FILL = new Color(70, 130, 180, 40);
	private static final Color BAND_BORDER = new Color(70, 130, 180);
	private static final Stroke DASHED = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f,
			new float[] { 5f, 4f }, 0f);

	private List<ARule> rules = new ArrayList<>();
	private Map<String, double[]> ranges = new java.util.HashMap<>();
	private String xMeasure;
	private String yMeasure;
	private String colorMeasure;
	private String sizeMeasure;
	private boolean logX;
	private boolean logY;

	/** current visible range in (possibly log) data units; null = automatic */
	private double[] viewX;
	private double[] viewY;

	private final Set<ARule> selected = new HashSet<>();
	private ARule hoverRule;
	private int hoverIndex = -1;

	private Point dragStart;
	private Rectangle band;
	private boolean bandUsed;
	private boolean panning;
	private boolean panMoved;
	private Point panLast;

	private SelectionListener selectionListener;
	private PopupHandler popupHandler;

	private float[] sx = new float[0];
	private float[] sy = new float[0];
	private float[] radius = new float[0];
	private Rectangle lastArea = new Rectangle();

	public RuleScatterPanel() {
		setBackground(Color.WHITE);
		setToolTipText("");
		setFocusable(true);
		MouseAdapter mouse = new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				requestFocusInWindow();
				bandUsed = false;
				if (SwingUtilities.isLeftMouseButton(e) && !e.isPopupTrigger()) {
					dragStart = e.getPoint();
					band = null;
				} else if (SwingUtilities.isRightMouseButton(e) || SwingUtilities.isMiddleMouseButton(e)) {
					panning = true;
					panMoved = false;
					panLast = e.getPoint();
				}
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				if (panning) {
					pan(e.getX() - panLast.x, e.getY() - panLast.y);
					panLast = e.getPoint();
					panMoved = true;
				} else if (dragStart != null) {
					band = rectangle(dragStart, e.getPoint());
					repaint();
				}
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				if (panning) {
					panning = false;
					if (!panMoved && popupHandler != null && SwingUtilities.isRightMouseButton(e)) {
						popupHandler.showPopup(e, groupAt(e.getX(), e.getY()));
					}
					return;
				}
				if (band != null && (band.width > 3 || band.height > 3)) {
					select(rulesIn(band));
					bandUsed = true;
				}
				band = null;
				dragStart = null;
				repaint();
			}

			@Override
			public void mouseClicked(MouseEvent e) {
				if (!SwingUtilities.isLeftMouseButton(e)) {
					return;
				}
				if (bandUsed) {
					bandUsed = false;
					return;
				}
				if (e.getClickCount() == 2) {
					resetView();
					return;
				}
				List<ARule> group = groupAt(e.getX(), e.getY());
				// Ctrl/Shift-click: add to / remove from the current selection
				if (e.isControlDown() || e.isShiftDown()) {
					Set<ARule> newSelection = new LinkedHashSet<>(selected);
					if (group.isEmpty()) {
						// Ctrl-click on empty space keeps the selection
						return;
					}
					boolean changed = false;
					for (ARule r : group) {
						if (!newSelection.add(r)) {
							newSelection.remove(r);
						}
						changed = true;
					}
					if (changed) {
						select(new ArrayList<>(newSelection));
					}
				} else {
					select(group);
				}
			}

			@Override
			public void mouseMoved(MouseEvent e) {
				int i = hit(e.getX(), e.getY());
				if (i != hoverIndex) {
					hoverIndex = i;
					repaint();
				}
			}

			@Override
			public void mouseExited(MouseEvent e) {
				if (hoverIndex != -1) {
					hoverIndex = -1;
					repaint();
				}
			}

			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				zoom(e.getX(), e.getY(), Math.pow(1.2, e.getPreciseWheelRotation()));
			}
		};
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
		addMouseWheelListener(mouse);
	}

// ------------------------------------------------------------ API

	/** notified whenever the visible range changes (zoom, pan, reset) */
	private Runnable viewListener;

	public void setViewListener(Runnable listener) {
		this.viewListener = listener;
	}

	private void fireViewChanged() {
		if (viewListener != null) {
			viewListener.run();
		}
	}

	/** @return true if the user zoomed or panned away from the automatic view */
	public boolean isZoomed() {
		return viewX != null || viewY != null;
	}

	public void zoomIn() {
		zoomAtCenter(1 / 1.3);
	}

	public void zoomOut() {
		zoomAtCenter(1.3);
	}

	private void zoomAtCenter(double factor) {
		Rectangle a = plotArea();
		zoom(a.x + a.width / 2, a.y + a.height / 2, factor);
	}

	/** Select every rule currently drawn. */
	public void selectAll() {
		select(new ArrayList<>(rules));
	}

	public void setSelectionListener(SelectionListener listener) {
		this.selectionListener = listener;
	}

	public void setPopupHandler(PopupHandler handler) {
		this.popupHandler = handler;
	}

	public void setMeasureRanges(Map<String, double[]> ranges) {
		this.ranges = ranges;
		viewX = null;
		viewY = null;
		repaint();
		fireViewChanged();
	}

	public void setRules(List<ARule> rules) {
		this.rules = rules;
		sx = new float[0];
		sy = new float[0];
		radius = new float[0];
		hoverIndex = -1;
		if (hoverRule != null && !rules.contains(hoverRule)) {
			hoverRule = null;
		}
		selected.retainAll(new HashSet<>(rules));
		repaint();
	}

	public void setAxes(String xMeasure, String yMeasure, String colorMeasure, String sizeMeasure, boolean logX,
			boolean logY) {
		boolean axesChanged = !eq(xMeasure, this.xMeasure) || !eq(yMeasure, this.yMeasure) || logX != this.logX
				|| logY != this.logY;
		this.xMeasure = xMeasure;
		this.yMeasure = yMeasure;
		this.colorMeasure = colorMeasure;
		this.sizeMeasure = sizeMeasure;
		this.logX = logX;
		this.logY = logY;
		if (axesChanged) {
			viewX = null;
			viewY = null;
		}
		repaint();
	}

	public void setSelectedRules(Collection<ARule> rules) {
		selected.clear();
		selected.addAll(rules);
		repaint();
	}

	public List<ARule> getSelectedRules() {
		return new ArrayList<>(selected);
	}

	/** Highlight a rule (e.g. the one under the mouse in the table). */
	public void setHoverRule(ARule rule) {
		if (rule != hoverRule) {
			hoverRule = rule;
			repaint();
		}
	}

	/** Restore the automatic zoom. */
	public void resetView() {
		viewX = null;
		viewY = null;
		repaint();
		fireViewChanged();
	}

// ------------------------------------------------------------ interaction helpers

	private void select(List<ARule> list) {
		selected.clear();
		selected.addAll(list);
		repaint();
		if (selectionListener != null) {
			selectionListener.rulesSelected(list);
		}
	}

	private void zoom(int mx, int my, double factor) {
		Rectangle a = plotArea();
		if (xMeasure == null || yMeasure == null || !a.contains(mx, my)) {
			return;
		}
		ensureView();
		double dx = viewX[0] + (mx - a.x) / (double) a.width * (viewX[1] - viewX[0]);
		double dy = viewY[0] + (a.y + a.height - my) / (double) a.height * (viewY[1] - viewY[0]);
		viewX = new double[] { dx - (dx - viewX[0]) * factor, dx + (viewX[1] - dx) * factor };
		viewY = new double[] { dy - (dy - viewY[0]) * factor, dy + (viewY[1] - dy) * factor };
		repaint();
		fireViewChanged();
	}

	private void pan(int dpx, int dpy) {
		Rectangle a = plotArea();
		if (xMeasure == null || yMeasure == null || a.width <= 0 || a.height <= 0) {
			return;
		}
		ensureView();
		double shiftX = -dpx / (double) a.width * (viewX[1] - viewX[0]);
		double shiftY = dpy / (double) a.height * (viewY[1] - viewY[0]);
		viewX = new double[] { viewX[0] + shiftX, viewX[1] + shiftX };
		viewY = new double[] { viewY[0] + shiftY, viewY[1] + shiftY };
		repaint();
		fireViewChanged();
	}

	private static Rectangle rectangle(Point a, Point b) {
		return new Rectangle(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.abs(a.x - b.x), Math.abs(a.y - b.y));
	}

	/** Index of the point under (mx,my) or -1. */
	private int hit(int mx, int my) {
		if (!lastArea.contains(mx, my)) {
			return -1;
		}
		int n = Math.min(sx.length, rules.size());
		int best = -1;
		double bestDist = Double.MAX_VALUE;
		for (int i = 0; i < n; i++) {
			if (Float.isNaN(sx[i])) {
				continue;
			}
			double dx = sx[i] - mx;
			double dy = sy[i] - my;
			double d = Math.sqrt(dx * dx + dy * dy);
			if (d <= radius[i] + 3 && d < bestDist) {
				bestDist = d;
				best = i;
			}
		}
		return best;
	}

	/** All rules drawn at exactly the same place as the point under the mouse. */
	private List<ARule> groupAt(int mx, int my) {
		List<ARule> group = new ArrayList<>();
		int i = hit(mx, my);
		if (i < 0) {
			return group;
		}
		int n = Math.min(sx.length, rules.size());
		for (int j = 0; j < n; j++) {
			if (!Float.isNaN(sx[j]) && Math.abs(sx[j] - sx[i]) < 0.75f && Math.abs(sy[j] - sy[i]) < 0.75f) {
				group.add(rules.get(j));
			}
		}
		return group;
	}

	private List<ARule> rulesIn(Rectangle r) {
		List<ARule> list = new ArrayList<>();
		int n = Math.min(sx.length, rules.size());
		for (int i = 0; i < n; i++) {
			if (!Float.isNaN(sx[i]) && r.contains(sx[i], sy[i]) && lastArea.contains(sx[i], sy[i])) {
				list.add(rules.get(i));
			}
		}
		return list;
	}

// ------------------------------------------------------------ geometry

	private Rectangle plotArea() {
		return new Rectangle(MARGIN_LEFT, MARGIN_TOP, getWidth() - MARGIN_LEFT - MARGIN_RIGHT,
				getHeight() - MARGIN_TOP - MARGIN_BOTTOM);
	}

	private void ensureView() {
		if (viewX == null) {
			viewX = dataRange(xMeasure, logX);
		}
		if (viewY == null) {
			viewY = dataRange(yMeasure, logY);
		}
	}

	private static double transform(double v, boolean log) {
		if (!log) {
			return v;
		}
		return v > 0 ? Math.log10(v) : Double.NaN;
	}

	private double[] dataRange(String measure, boolean log) {
		double[] r = ranges.get(measure);
		double min = 0;
		double max = 1;
		if (r != null) {
			min = log ? Math.log10(Math.max(r[0], Double.MIN_NORMAL)) : r[0];
			max = log ? Math.log10(Math.max(r[1], Double.MIN_NORMAL)) : r[1];
		}
		if (max - min < 1e-12) {
			min -= log ? 0.5 : 1;
			max += log ? 0.5 : 1;
		}
		double pad = (max - min) * 0.06;
		return new double[] { min - pad, max + pad };
	}

	private void computeScreenCoordinates(Rectangle a) {
		int n = rules.size();
		if (sx.length != n) {
			sx = new float[n];
			sy = new float[n];
			radius = new float[n];
		}
		double[] sr = sizeMeasure == null ? null : ranges.get(sizeMeasure);
		float baseRadius = n > 1500 ? 3.0f : n > 500 ? 4.0f : 5.5f;
		for (int i = 0; i < n; i++) {
			ARule rule = rules.get(i);
			double xv = transform(rule.getMeasure(xMeasure), logX);
			double yv = transform(rule.getMeasure(yMeasure), logY);
			if (Double.isNaN(xv) || Double.isNaN(yv) || Double.isInfinite(xv) || Double.isInfinite(yv)) {
				sx[i] = Float.NaN;
				sy[i] = Float.NaN;
				continue;
			}
			sx[i] = (float) (a.x + (xv - viewX[0]) / (viewX[1] - viewX[0]) * a.width);
			sy[i] = (float) (a.y + a.height - (yv - viewY[0]) / (viewY[1] - viewY[0]) * a.height);
			radius[i] = sr == null ? baseRadius
					: (float) (3 + 9 * MeasureUtils.fraction(rule.getMeasure(sizeMeasure), sr));
		}
	}

// ------------------------------------------------------------ painting

	@Override
	protected void paintComponent(Graphics g0) {
		super.paintComponent(g0);
		Graphics2D g = (Graphics2D) g0.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		Rectangle a = plotArea();
		lastArea = a;
		if (xMeasure == null || yMeasure == null || a.width < 40 || a.height < 40) {
			g.setColor(Color.GRAY);
			g.drawString("Open a rule file with at least one numeric measure to see the plot.", 20, 30);
			g.dispose();
			return;
		}
		ensureView();
		drawGridAndAxes(g, a);
		drawReferenceLines(g, a);
		computeScreenCoordinates(a);

		Shape oldClip = g.getClip();
		g.setClip(a);
		double[] cr = colorMeasure == null ? null : ranges.get(colorMeasure);
		for (int i = 0; i < rules.size(); i++) {
			if (Float.isNaN(sx[i])) {
				continue;
			}
			Color c = colorMeasure == null ? DEFAULT_POINT_COLOR
					: MeasureUtils.colorFor(rules.get(i).getMeasure(colorMeasure), colorMeasure, cr);
			float r = radius[i];
			g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 180));
			g.fill(new Ellipse2D.Float(sx[i] - r, sy[i] - r, 2 * r, 2 * r));
			g.setColor(c.darker());
			g.draw(new Ellipse2D.Float(sx[i] - r, sy[i] - r, 2 * r, 2 * r));
		}
		// selected rules
		if (!selected.isEmpty()) {
			g.setColor(Color.BLACK);
			g.setStroke(new BasicStroke(2.2f));
			for (int i = 0; i < rules.size(); i++) {
				if (!Float.isNaN(sx[i]) && selected.contains(rules.get(i))) {
					float r = radius[i] + 3.5f;
					g.draw(new Ellipse2D.Float(sx[i] - r, sy[i] - r, 2 * r, 2 * r));
				}
			}
		}
		// hovered (mouse over plot or over table)
		int hoverFromTable = hoverRule == null ? -1 : rules.indexOf(hoverRule);
		g.setColor(Color.ORANGE);
		g.setStroke(new BasicStroke(2.5f));
		for (int i : new int[] { hoverIndex, hoverFromTable }) {
			if (i >= 0 && i < sx.length && !Float.isNaN(sx[i])) {
				float r = radius[i] + 5f;
				g.draw(new Ellipse2D.Float(sx[i] - r, sy[i] - r, 2 * r, 2 * r));
			}
		}
		g.setStroke(new BasicStroke(1f));
		// rubber band
		if (band != null) {
			g.setColor(BAND_FILL);
			g.fill(band);
			g.setColor(BAND_BORDER);
			g.draw(band);
		}
		g.setClip(oldClip);

		if (rules.isEmpty()) {
			g.setColor(Color.GRAY);
			String msg = "No rules match the current filters";
			FontMetrics fm = g.getFontMetrics();
			g.drawString(msg, a.x + a.width / 2 - fm.stringWidth(msg) / 2, a.y + a.height / 2);
		}

		drawLegends(g, a);

		// counts (top-left) and gesture hint (bottom)
		g.setColor(Color.DARK_GRAY);
		String counts = rules.size() + (rules.size() == 1 ? " rule" : " rules")
				+ (selected.isEmpty() ? "" : "  |  " + selected.size() + " selected");
		g.drawString(counts, a.x, a.y - 9);
		g.setFont(g.getFont().deriveFont(10.5f));
		g.setColor(Color.GRAY);
		String hint = "Hover: details   |   Click or drag: select (Ctrl/Shift-click adds)   |   Wheel: zoom   |   Right-drag: pan   |   Double-click: reset view";
		FontMetrics hfm = g.getFontMetrics();
		g.drawString(hint, a.x + a.width / 2 - hfm.stringWidth(hint) / 2, a.y + a.height + 56);
		g.dispose();
	}

	private void drawGridAndAxes(Graphics2D g, Rectangle a) {
		FontMetrics fm = g.getFontMetrics();
		double xStep = niceStep(viewX[1] - viewX[0], 7);
		for (long k = (long) Math.ceil(viewX[0] / xStep); k * xStep <= viewX[1] + xStep * 1e-9; k++) {
			double v = k * xStep;
			int x = (int) Math.round(a.x + (v - viewX[0]) / (viewX[1] - viewX[0]) * a.width);
			g.setColor(GRID_COLOR);
			g.drawLine(x, a.y, x, a.y + a.height);
			g.setColor(Color.DARK_GRAY);
			String s = tickLabel(v, logX);
			g.drawString(s, x - fm.stringWidth(s) / 2, a.y + a.height + 16);
		}
		double yStep = niceStep(viewY[1] - viewY[0], 7);
		for (long k = (long) Math.ceil(viewY[0] / yStep); k * yStep <= viewY[1] + yStep * 1e-9; k++) {
			double v = k * yStep;
			int y = (int) Math.round(a.y + a.height - (v - viewY[0]) / (viewY[1] - viewY[0]) * a.height);
			g.setColor(GRID_COLOR);
			g.drawLine(a.x, y, a.x + a.width, y);
			g.setColor(Color.DARK_GRAY);
			String s = tickLabel(v, logY);
			g.drawString(s, a.x - 6 - fm.stringWidth(s), y + fm.getAscent() / 2 - 1);
		}
		g.setColor(Color.BLACK);
		g.draw(a);

		Font old = g.getFont();
		g.setFont(old.deriveFont(Font.BOLD));
		FontMetrics bfm = g.getFontMetrics();
		String xt = MeasureUtils.prettyName(xMeasure) + (logX ? " (log scale)" : "");
		g.drawString(xt, a.x + a.width / 2 - bfm.stringWidth(xt) / 2, a.y + a.height + 38);
		String yt = MeasureUtils.prettyName(yMeasure) + (logY ? " (log scale)" : "");

		Graphics2D g2 = (Graphics2D) g.create();
		g2.rotate(-Math.PI / 2);
		g2.drawString(yt, -(a.y + a.height / 2 + bfm.stringWidth(yt) / 2), 16);
		g2.dispose();
		g.setFont(old);
	}

	/**
	 * Draw a dashed line at the "independence" value (e.g. lift = 1) when such a
	 * measure is on the X or Y axis.
	 */
	private void drawReferenceLines(Graphics2D g, Rectangle a) {
		Stroke oldStroke = g.getStroke();
		g.setStroke(DASHED);
		g.setColor(new Color(110, 110, 110));
		FontMetrics fm = g.getFontMetrics();

		double cx = MeasureUtils.centerValue(xMeasure);
		if (!Double.isNaN(cx)) {
			double v = transform(cx, logX);
			if (!Double.isNaN(v) && v >= viewX[0] && v <= viewX[1]) {
				int x = (int) Math.round(a.x + (v - viewX[0]) / (viewX[1] - viewX[0]) * a.width);
				g.drawLine(x, a.y, x, a.y + a.height);
				g.drawString(MeasureUtils.prettyName(xMeasure) + " = " + MeasureUtils.format(cx) + " (independent)",
						x + 4, a.y + fm.getAscent() + 2);
			}
		}
		double cy = MeasureUtils.centerValue(yMeasure);
		if (!Double.isNaN(cy)) {
			double v = transform(cy, logY);
			if (!Double.isNaN(v) && v >= viewY[0] && v <= viewY[1]) {
				int y = (int) Math.round(a.y + a.height - (v - viewY[0]) / (viewY[1] - viewY[0]) * a.height);
				g.drawLine(a.x, y, a.x + a.width, y);
				String label = MeasureUtils.prettyName(yMeasure) + " = " + MeasureUtils.format(cy) + " (independent)";
				g.drawString(label, a.x + a.width - fm.stringWidth(label) - 4, y - 4);
			}
		}
		g.setStroke(oldStroke);
	}

	/** Color legend (with the independence point marked) and size legend. */
	private void drawLegends(Graphics2D g, Rectangle a) {
		int lx = a.x + a.width + 28;
		int top = a.y;
		FontMetrics fm = g.getFontMetrics();
		Font old = g.getFont();

		double[] cr = colorMeasure == null ? null : ranges.get(colorMeasure);
		if (cr != null) {
			int barW = 14;
			int barH = sizeMeasure == null ? a.height : Math.max(40, a.height - 120);
			for (int y = 0; y <= barH; y++) {
				double t = 1.0 - (double) y / barH;
				double v = cr[0] + t * (cr[1] - cr[0]);
				g.setColor(MeasureUtils.colorFor(v, colorMeasure, cr));
				g.drawLine(lx, top + y, lx + barW, top + y);
			}
			g.setColor(Color.BLACK);
			g.drawRect(lx, top, barW, barH);
			g.drawString(MeasureUtils.format(cr[1]), lx + barW + 4, top + fm.getAscent());
			g.drawString(MeasureUtils.format(cr[0]), lx + barW + 4, top + barH);
			double c = MeasureUtils.centerValue(colorMeasure);
			if (!Double.isNaN(c) && c > cr[0] && c < cr[1]) {
				int y = top + (int) Math.round(barH * (1 - (c - cr[0]) / (cr[1] - cr[0])));
				g.drawLine(lx - 3, y, lx + barW + 3, y);
				g.drawString(MeasureUtils.format(c) + " = indep.", lx + barW + 4, y + fm.getAscent() / 2 - 1);
			} else if (Double.isNaN(c)) {
				g.drawString(MeasureUtils.format((cr[0] + cr[1]) / 2), lx + barW + 4,
						top + barH / 2 + fm.getAscent() / 2 - 1);
			}
			g.setFont(old.deriveFont(Font.BOLD));
			g.drawString(MeasureUtils.prettyName(colorMeasure), lx - 4, top - 9);
			g.setFont(old);
			top += barH + 34;
		}

		double[] sr = sizeMeasure == null ? null : ranges.get(sizeMeasure);
		if (sr != null) {
			g.setColor(Color.BLACK);
			g.setFont(old.deriveFont(Font.BOLD));
			g.drawString("Size: " + MeasureUtils.prettyName(sizeMeasure), lx - 4, top + fm.getAscent());
			g.setFont(old);
			int y = top + 22;
			for (double t : new double[] { 0, 0.5, 1 }) {
				float r = (float) (3 + 9 * t);
				g.setColor(DEFAULT_POINT_COLOR);
				g.draw(new Ellipse2D.Float(lx + 12 - r, y + 12 - r, 2 * r, 2 * r));
				g.setColor(Color.BLACK);
				g.drawString(MeasureUtils.format(sr[0] + t * (sr[1] - sr[0])), lx + 30,
						y + 12 + fm.getAscent() / 2 - 1);
				y += 28;
			}
		}
	}

	private static double niceStep(double range, int targetTicks) {
		if (range <= 0) {
			return 1;
		}
		double raw = range / targetTicks;
		double exp = Math.floor(Math.log10(raw));
		double base = raw / Math.pow(10, exp);
		double nice = base < 1.5 ? 1 : base < 3 ? 2 : base < 7 ? 5 : 10;
		return nice * Math.pow(10, exp);
	}

	private static String tickLabel(double v, boolean log) {
		if (Math.abs(v) < 1e-12) {
			v = 0;
		}
		return MeasureUtils.format(log ? Math.pow(10, v) : v);
	}

	private static boolean eq(String a, String b) {
		return a == null ? b == null : a.equals(b);
	}

	// ------------------------------------------------------------ tooltip

	@Override
	public String getToolTipText(MouseEvent e) {
		List<ARule> group = groupAt(e.getX(), e.getY());
		if (group.isEmpty()) {
			return null;
		}
		StringBuilder sb = new StringBuilder("<html>");
		if (group.size() == 1) {
			ARule rule = group.get(0);
			sb.append("<b>IF</b> ").append(escape(rule.getAntecedentText())).append(" <b>THEN</b> ")
					.append(escape(rule.getConsequentText()));
			for (Map.Entry<String, Double> m : rule.getMeasures().entrySet()) {
				sb.append("<br>").append(escape(MeasureUtils.prettyName(m.getKey()))).append(": ")
						.append(MeasureUtils.format(m.getValue()));
			}
		} else {
			sb.append("<b>").append(group.size()).append(" rules at this point</b> (click to select them all)");
			ARule first = group.get(0);
			sb.append("<br>").append(escape(MeasureUtils.prettyName(xMeasure))).append(" = ")
					.append(MeasureUtils.format(first.getMeasure(xMeasure))).append(", ")
					.append(escape(MeasureUtils.prettyName(yMeasure))).append(" = ")
					.append(MeasureUtils.format(first.getMeasure(yMeasure))).append("<br>");
			int shown = 0;
			for (ARule r : group) {
				if (shown == 10) {
					sb.append("&nbsp;&nbsp;... and ").append(group.size() - shown).append(" more");
					break;
				}
				sb.append("&nbsp;&nbsp;&bull; ").append(escape(r.toString())).append("<br>");
				shown++;
			}
		}
		return sb.append("</html>").toString();
	}

	private static String escape(String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
