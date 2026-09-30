package ca.pfv.spmf.experimental.patternexplorer;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;

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
 * Do not remove copyright and license information
 */
/**
 * A compact, interactive bar chart of the pattern-size distribution of the
 * current result. Clicking a bar restricts the query to that size (clicking
 * again removes the restriction); dragging over several bars selects a size
 * range. When a size restriction is active, the chart shows the distribution
 * <i>before</i> that restriction and highlights the selected sizes, so the
 * user can always see the alternatives.
 * 
 * @author Philippe Fournier-Viger
 */
public class SizeDistributionChart extends JPanel {

	private static final long serialVersionUID = 1L;

	private static final int BAR_GAP = 3;
	private static final int LABEL_AREA = 16;
	private static final int TOP_AREA = 18;
	private static final int SIDE_PAD = 8;

	private final ExplorerController controller;

	/** size -> count (of the contextual result) */
	private TreeMap<Integer, Integer> distribution = new TreeMap<>();
	private int total = 0;
	private int maxCount = 0;
	private int minSize = 0;
	private int maxSize = 0;
	private double avgSize = 0;

	/** Current size restriction of the query */
	private int selectedMin = ExplorerQuery.NO_LIMIT;
	private int selectedMax = ExplorerQuery.NO_LIMIT;

	/** Bar geometry computed at paint time */
	private final List<Rectangle> bars = new ArrayList<>();
	private final List<Integer> barSizes = new ArrayList<>();

	private int hovered = -1;
	private int dragStart = -1;
	private int dragEnd = -1;

	/**
	 * Constructor
	 * 
	 * @param controller the controller
	 */
	public SizeDistributionChart(ExplorerController controller) {
		this.controller = controller;
		setBorder(ExplorerStyle.titledBorder("Pattern size"));
		setBackground(Color.WHITE);
		setPreferredSize(new Dimension(260, 200));
		setMinimumSize(new Dimension(150, 120));
		ToolTipManager.sharedInstance().registerComponent(this);

		MouseAdapter mouse = new MouseAdapter() {
			@Override
			public void mouseMoved(MouseEvent e) {
				int h = barIndexAt(e.getX(), e.getY());
				if (h != hovered) {
					hovered = h;
					setCursor(h >= 0 ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
					repaint();
				}
			}

			@Override
			public void mouseExited(MouseEvent e) {
				if (hovered != -1) {
					hovered = -1;
					repaint();
				}
			}

			@Override
			public void mousePressed(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e)) {
					dragStart = barIndexAt(e.getX(), e.getY());
					dragEnd = dragStart;
					repaint();
				}
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				if (dragStart >= 0) {
					int idx = nearestBarIndex(e.getX());
					if (idx != dragEnd) {
						dragEnd = idx;
						repaint();
					}
				}
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				if (dragStart >= 0 && SwingUtilities.isLeftMouseButton(e)) {
					int a = Math.min(dragStart, dragEnd);
					int b = Math.max(dragStart, dragEnd);
					int lo = barSizes.get(a);
					int hi = barSizes.get(b);
					dragStart = -1;
					dragEnd = -1;
					applySelection(lo, hi);
				}
				dragStart = -1;
				dragEnd = -1;
				repaint();
			}
		};
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
	}

	// ------------------------------------------------------------------
	// Data
	// ------------------------------------------------------------------

	/**
	 * Update the chart from a query result.
	 * 
	 * @param result the current result
	 */
	public void update(QueryResult result) {
		ExplorerQuery query = result.getQuery();
		selectedMin = query.getMinSize();
		selectedMax = query.getMaxSize();
		QueryResult base = result;
		if (selectedMin != ExplorerQuery.NO_LIMIT || selectedMax != ExplorerQuery.NO_LIMIT) {
			// Show the distribution without the size restriction (facet style)
			base = result.getCollection().evaluate(query.clearSizeRange());
		}
		distribution = base.getSizeDistribution();
		total = base.size();
		maxCount = 0;
		minSize = 0;
		maxSize = 0;
		avgSize = 0;
		long sum = 0;
		boolean first = true;
		for (Map.Entry<Integer, Integer> e : distribution.entrySet()) {
			int size = e.getKey();
			int count = e.getValue();
			if (first) {
				minSize = size;
				first = false;
			}
			maxSize = size;
			sum += (long) size * count;
			if (count > maxCount) {
				maxCount = count;
			}
		}
		avgSize = total == 0 ? 0 : (double) sum / total;
		hovered = -1;
		repaint();
	}

	private void applySelection(int lo, int hi) {
		if (lo == hi) {
			controller.toggleSize(lo);
			return;
		}
		ExplorerQuery current = controller.getCurrentQuery();
		if (current.getMinSize() == lo && current.getMaxSize() == hi) {
			controller.applyQuery(current.clearSizeRange());
		} else {
			controller.applyQuery(current.withSizeRange(lo, hi));
		}
	}

	private boolean isSelected(int size) {
		if (selectedMin == ExplorerQuery.NO_LIMIT && selectedMax == ExplorerQuery.NO_LIMIT) {
			return false;
		}
		return (selectedMin == ExplorerQuery.NO_LIMIT || size >= selectedMin)
				&& (selectedMax == ExplorerQuery.NO_LIMIT || size <= selectedMax);
	}

	// ------------------------------------------------------------------
	// Geometry
	// ------------------------------------------------------------------

	private void layoutBars() {
		bars.clear();
		barSizes.clear();
		if (distribution.isEmpty()) {
			return;
		}
		Insets ins = getInsets();
		int left = ins.left + SIDE_PAD;
		int right = getWidth() - ins.right - SIDE_PAD;
		int width = right - left;
		int top = ins.top + TOP_AREA;
		int bottom = getHeight() - ins.bottom - LABEL_AREA;
		int height = bottom - top;
		if (width <= 0 || height <= 0) {
			return;
		}
		int n = distribution.size();
		int totalGap = (n - 1) * BAR_GAP;
		int barWidth = Math.min(Math.max(4, (width - totalGap) / n), 40);
		int startX = left + (width - (n * barWidth + totalGap)) / 2;
		int idx = 0;
		for (Map.Entry<Integer, Integer> e : distribution.entrySet()) {
			int count = e.getValue();
			int barHeight = maxCount > 0 ? Math.max(2, (int) Math.round((double) count / maxCount * height)) : 2;
			int x = startX + idx * (barWidth + BAR_GAP);
			bars.add(new Rectangle(x, bottom - barHeight, barWidth, barHeight));
			barSizes.add(e.getKey());
			idx++;
		}
	}

	private int barIndexAt(int mx, int my) {
		layoutBars();
		Insets ins = getInsets();
		int top = ins.top + TOP_AREA;
		int bottom = getHeight() - ins.bottom - LABEL_AREA;
		if (my < top || my > bottom + LABEL_AREA) {
			return -1;
		}
		for (int i = 0; i < bars.size(); i++) {
			Rectangle r = bars.get(i);
			if (mx >= r.x - BAR_GAP / 2 && mx < r.x + r.width + BAR_GAP / 2) {
				return i;
			}
		}
		return -1;
	}

	private int nearestBarIndex(int mx) {
		layoutBars();
		if (bars.isEmpty()) {
			return -1;
		}
		int best = 0;
		int bestDist = Integer.MAX_VALUE;
		for (int i = 0; i < bars.size(); i++) {
			Rectangle r = bars.get(i);
			int center = r.x + r.width / 2;
			int dist = Math.abs(center - mx);
			if (dist < bestDist) {
				bestDist = dist;
				best = i;
			}
		}
		return best;
	}

	@Override
	public String getToolTipText(MouseEvent e) {
		int idx = barIndexAt(e.getX(), e.getY());
		if (idx < 0) {
			return "Click a bar to keep only the patterns of that size; drag to select a range of sizes";
		}
		int size = barSizes.get(idx);
		int count = distribution.getOrDefault(size, 0);
		double pct = total > 0 ? (double) count / total : 0;
		String action = isSelected(size) && selectedMin == selectedMax ? "click to remove the size restriction"
				: "click to keep only these patterns";
		return "<html><b>Size " + size + ":</b> " + ExplorerFormat.count(count, "pattern") + " ("
				+ ExplorerFormat.percent(pct) + ")<br><i>" + action + "</i></html>";
	}

	// ------------------------------------------------------------------
	// Painting
	// ------------------------------------------------------------------

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		Insets ins = getInsets();
		int left = ins.left + SIDE_PAD;
		int right = getWidth() - ins.right - SIDE_PAD;
		int width = right - left;
		int top = ins.top + TOP_AREA;
		int bottom = getHeight() - ins.bottom - LABEL_AREA;

		// Subtitle
		g2.setFont(ExplorerStyle.FONT_SMALL);
		g2.setColor(ExplorerStyle.MUTED);
		String subtitle;
		if (total == 0) {
			subtitle = "no pattern";
		} else if (minSize == maxSize) {
			subtitle = String.format("all patterns have %d item%s", minSize, minSize == 1 ? "" : "s");
		} else {
			subtitle = String.format("min=%d  avg=%.1f  max=%d  \u00B7 click / drag to filter", minSize, avgSize,
					maxSize);
		}
		FontMetrics fm = g2.getFontMetrics();
		g2.drawString(subtitle, left + Math.max(0, (width - fm.stringWidth(subtitle)) / 2),
				ins.top + fm.getAscent() + 2);

		layoutBars();
		if (bars.isEmpty()) {
			g2.setFont(ExplorerStyle.FONT_ITALIC);
			String msg = "No data";
			g2.drawString(msg, left + (width - g2.getFontMetrics().stringWidth(msg)) / 2, (top + bottom) / 2);
			g2.dispose();
			return;
		}

		// Axis
		g2.setColor(ExplorerStyle.AXIS);
		g2.drawLine(left, bottom, right, bottom);

		boolean anySelected = selectedMin != ExplorerQuery.NO_LIMIT || selectedMax != ExplorerQuery.NO_LIMIT;
		int dragLo = Math.min(dragStart, dragEnd);
		int dragHi = Math.max(dragStart, dragEnd);

		for (int i = 0; i < bars.size(); i++) {
			Rectangle r = bars.get(i);
			int size = barSizes.get(i);
			int count = distribution.getOrDefault(size, 0);
			boolean selected = isSelected(size);
			boolean inDrag = dragStart >= 0 && i >= dragLo && i <= dragHi;

			Color color;
			if (selected) {
				color = ExplorerStyle.SELECTED;
			} else if (anySelected) {
				color = ExplorerStyle.withAlpha(ExplorerStyle.ACCENT, 70);
			} else {
				color = ExplorerStyle.ACCENT;
			}
			if (i == hovered && dragStart < 0) {
				color = selected ? ExplorerStyle.SELECTED.brighter() : ExplorerStyle.ACCENT_HOVER;
			}
			g2.setColor(color);
			g2.fillRect(r.x, r.y, r.width, r.height);
			if (inDrag) {
				g2.setColor(ExplorerStyle.withAlpha(ExplorerStyle.SELECTED, 60));
				g2.fillRect(r.x - 1, top, r.width + 2, bottom - top);
				g2.setColor(ExplorerStyle.SELECTED);
				g2.drawRect(r.x - 1, top, r.width + 2, bottom - top);
			}

			// Count above the bar
			g2.setFont(ExplorerStyle.FONT_SMALL_BOLD);
			g2.setColor(new Color(60, 60, 60));
			FontMetrics fc = g2.getFontMetrics();
			String cs = ExplorerFormat.integer(count);
			int cw = fc.stringWidth(cs);
			int cy = r.y - 2;
			if (cy >= top + fc.getAscent() && cw <= r.width + BAR_GAP + 6) {
				g2.drawString(cs, r.x + (r.width - cw) / 2, cy);
			}

			// Size label below
			g2.setFont(selected ? ExplorerStyle.FONT_SMALL_BOLD : ExplorerStyle.FONT_SMALL);
			g2.setColor(selected ? ExplorerStyle.SELECTED.darker() : ExplorerStyle.MUTED);
			FontMetrics fl = g2.getFontMetrics();
			String ss = String.valueOf(size);
			int lw = fl.stringWidth(ss);
			if (lw <= r.width + BAR_GAP) {
				g2.drawString(ss, r.x + (r.width - lw) / 2, bottom + fl.getAscent() + 1);
			} else if (i == 0 || i == bars.size() - 1 || i % Math.max(1, bars.size() / 8) == 0) {
				g2.drawString(ss, r.x + (r.width - lw) / 2, bottom + fl.getAscent() + 1);
			}
		}
		g2.dispose();
	}
}