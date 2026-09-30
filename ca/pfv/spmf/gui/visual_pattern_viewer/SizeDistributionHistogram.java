package ca.pfv.spmf.gui.visual_pattern_viewer;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.JPanel;
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
 * A compact bar chart of the pattern-size distribution of the visible
 * patterns, shown in the sidebar of the Visual Pattern Viewer.
 */
class SizeDistributionHistogram extends JPanel {

	private static final long serialVersionUID = 1L;

	private static final int SUBTITLE_H = 16;
	private static final int CHART_H = 92;
	private static final int LABEL_H = 16;
	private static final int MAX_BAR_W = 36;
	private static final int BAR_GAP = 3;

	private final TreeMap<Integer, Integer> distribution = new TreeMap<>();
	private int maxCount;
	private int totalPatterns;
	private int hoveredBarIndex = -1;
	private double avgSize;
	private int minSize;
	private int maxSize;

	// Geometry computed for the current size
	private int barW;
	private int barGap;
	private int startX;
	private int chartTop;
	private int chartBottom;

	SizeDistributionHistogram() {
		setOpaque(false);
		setPreferredSize(new Dimension(200, SUBTITLE_H + 4 + CHART_H + LABEL_H));
		ToolTipManager.sharedInstance().registerComponent(this);
		addMouseMotionListener(new MouseMotionAdapter() {
			@Override
			public void mouseMoved(MouseEvent e) {
				int h = getBarIndexAt(e.getX(), e.getY());
				if (h != hoveredBarIndex) {
					hoveredBarIndex = h;
					repaint();
				}
			}
		});
		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseExited(MouseEvent e) {
				if (hoveredBarIndex != -1) {
					hoveredBarIndex = -1;
					repaint();
				}
			}
		});
	}

	/**
	 * Recomputes the distribution from the visible patterns of the panel.
	 */
	void updateData(PatternsPanel panel) {
		distribution.clear();
		maxCount = 0;
		totalPatterns = 0;
		hoveredBarIndex = -1;
		avgSize = 0;
		minSize = 0;
		maxSize = 0;
		List<Integer> sizes = (panel == null) ? null : panel.getVisiblePatternSizes();
		if (sizes != null && !sizes.isEmpty()) {
			totalPatterns = sizes.size();
			long sum = 0;
			minSize = Integer.MAX_VALUE;
			maxSize = Integer.MIN_VALUE;
			for (int sz : sizes) {
				distribution.merge(sz, 1, Integer::sum);
				sum += sz;
				minSize = Math.min(minSize, sz);
				maxSize = Math.max(maxSize, sz);
			}
			avgSize = (double) sum / totalPatterns;
			for (int c : distribution.values()) {
				maxCount = Math.max(maxCount, c);
			}
		}
		repaint();
	}

	/** @return a short summary such as "sizes 1 – 5", or "" if no data */
	String getSummaryText() {
		if (totalPatterns == 0) {
			return "";
		}
		return minSize == maxSize ? "size " + minSize : "sizes " + minSize + " \u2013 " + maxSize;
	}

	private void computeGeometry() {
		Insets in = getInsets();
		int left = in.left;
		int dw = getWidth() - in.left - in.right;
		int n = Math.max(1, distribution.size());
		barGap = BAR_GAP;
		barW = (dw - (n - 1) * barGap) / n;
		if (barW < 3) {
			barGap = 1;
			barW = Math.max(2, (dw - (n - 1)) / n);
		}
		barW = Math.min(barW, MAX_BAR_W);
		startX = left + Math.max(0, (dw - (n * barW + (n - 1) * barGap)) / 2);
		chartTop = in.top + SUBTITLE_H + 4;
		chartBottom = getHeight() - in.bottom - LABEL_H;
	}

	private int getBarIndexAt(int mx, int my) {
		if (distribution.isEmpty()) {
			return -1;
		}
		computeGeometry();
		if (my < chartTop || my > chartBottom || mx < startX) {
			return -1;
		}
		int idx = (mx - startX) / (barW + barGap);
		return idx < distribution.size() ? idx : -1;
	}

	@Override
	public String getToolTipText(MouseEvent e) {
		int idx = getBarIndexAt(e.getX(), e.getY());
		if (idx < 0) {
			return null;
		}
		int i = 0;
		for (Map.Entry<Integer, Integer> entry : distribution.entrySet()) {
			if (i++ == idx) {
				double pct = totalPatterns > 0 ? 100.0 * entry.getValue() / totalPatterns : 0;
				return String.format("<html><b>Size %d:</b> %d pattern%s (%.1f%%)</html>", entry.getKey(),
						entry.getValue(), entry.getValue() != 1 ? "s" : "", pct);
			}
		}
		return null;
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		try {
			ViewerStyle.applyRenderingHints(g2);
			Insets in = getInsets();
			int left = in.left;
			int right = getWidth() - in.right;
			computeGeometry();

			// Subtitle
			g2.setFont(ViewerStyle.smallFont());
			FontMetrics sfm = g2.getFontMetrics();
			if (totalPatterns > 0) {
				String sub = "min " + minSize + "  \u00B7  avg " + ViewerStyle.formatNumber(Math.round(avgSize * 100) / 100.0)
						+ "  \u00B7  max " + maxSize;
				g2.setColor(ViewerStyle.TEXT_SECONDARY);
				g2.drawString(sub, left + (right - left - sfm.stringWidth(sub)) / 2, in.top + sfm.getAscent());
			}

			if (distribution.isEmpty()) {
				g2.setFont(ViewerStyle.baseFont().deriveFont(Font.ITALIC));
				g2.setColor(ViewerStyle.TEXT_SECONDARY);
				FontMetrics fm = g2.getFontMetrics();
				String msg = "No patterns to summarize";
				g2.drawString(msg, left + (right - left - fm.stringWidth(msg)) / 2,
						chartTop + (chartBottom - chartTop) / 2 + fm.getAscent() / 2);
				return;
			}

			int chartH = chartBottom - chartTop;
			int labelTop = chartTop + sfm.getAscent() + 2; // room for count labels
			int usableH = Math.max(10, chartH - (labelTop - chartTop));

			g2.setColor(ViewerStyle.AXIS);
			g2.drawLine(left, chartBottom, right, chartBottom);

			Font countFont = ViewerStyle.smallFont().deriveFont(Font.BOLD);
			FontMetrics cfm = g2.getFontMetrics(countFont);
			int lastLabelRight = Integer.MIN_VALUE;
			int idx = 0;
			for (Map.Entry<Integer, Integer> entry : distribution.entrySet()) {
				int cnt = entry.getValue();
				int bh = maxCount > 0 ? Math.max(2, (int) Math.round((double) cnt / maxCount * usableH)) : 2;
				int bx = startX + idx * (barW + barGap);
				int by = chartBottom - bh;
				boolean hovered = idx == hoveredBarIndex;

				if (hovered) {
					g2.setColor(ViewerStyle.ACCENT_SOFT);
					g2.fillRect(bx - barGap / 2 - 1, chartTop, barW + barGap + 2, chartH);
				}
				g2.setColor(hovered ? ViewerStyle.ACCENT_DARK : ViewerStyle.ACCENT);
				int r = Math.min(4, barW / 2);
				if (bh > r) {
					g2.fillRoundRect(bx, by, barW, bh, 2 * r, 2 * r);
					g2.fillRect(bx, by + r, barW, bh - r);
				} else {
					g2.fillRect(bx, by, barW, bh);
				}

				// Count above the bar (if it fits)
				String cs = String.valueOf(cnt);
				int cw = cfm.stringWidth(cs);
				if (cw <= barW + barGap + 2 || hovered) {
					g2.setFont(countFont);
					g2.setColor(ViewerStyle.TEXT_PRIMARY);
					g2.drawString(cs, bx + (barW - cw) / 2, by - 3);
				}

				// Size label under the bar, skipping labels that would overlap
				g2.setFont(ViewerStyle.smallFont());
				String ss = String.valueOf(entry.getKey());
				int lw = sfm.stringWidth(ss);
				int lx = bx + (barW - lw) / 2;
				if (lx >= lastLabelRight + 4 || hovered) {
					g2.setColor(hovered ? ViewerStyle.ACCENT_DARK : ViewerStyle.TEXT_SECONDARY);
					g2.drawString(ss, lx, chartBottom + sfm.getAscent() + 2);
					lastLabelRight = lx + lw;
				}
				idx++;
			}
		} finally {
			g2.dispose();
		}
	}
}