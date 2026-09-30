package ca.pfv.spmf.gui.visual_pattern_viewer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;

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
 * Sidebar panel showing two "tiles" (number of patterns shown / total, number
 * of measures) and a small table with the min / average / max of each measure
 * over the currently visible patterns.
 *
 * @author Philippe Fournier-Viger
 */
class StatisticsPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private static final int TILE_H = 46;
	private static final int TILE_GAP = 8;
	private static final int TABLE_GAP = 10;
	private static final int HEADER_H = 16;
	private static final int ROW_H = 18;
	private static final int VALUE_COL_W = 66;

	private final List<String> measures;
	private int total;
	private int visible;
	private Map<String, DescriptiveStats> stats = Map.of();

	StatisticsPanel(Collection<String> measures) {
		this.measures = new ArrayList<>(measures);
		setOpaque(false);
	}

	/**
	 * Updates the displayed numbers.
	 *
	 * @param total   total number of patterns in the file
	 * @param visible number of patterns passing the current filter
	 * @param stats   statistics of each measure over the visible patterns
	 */
	void update(int total, int visible, Map<String, DescriptiveStats> stats) {
		this.total = total;
		this.visible = visible;
		this.stats = stats == null ? Map.of() : stats;
		repaint();
	}

	@Override
	public Dimension getPreferredSize() {
		int h = TILE_H;
		if (!measures.isEmpty()) {
			h += TABLE_GAP + HEADER_H + measures.size() * ROW_H;
		}
		return new Dimension(200, h);
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		try {
			ViewerStyle.applyRenderingHints(g2);
			int w = getWidth();
			int tileW = (w - TILE_GAP) / 2;

			boolean filtered = visible != total;
			String big = filtered ? visible + " / " + total : String.valueOf(total);
			String caption = filtered ? "patterns shown" : (total == 1 ? "pattern" : "patterns");
			paintTile(g2, 0, 0, tileW, big, caption);
			paintTile(g2, tileW + TILE_GAP, 0, w - tileW - TILE_GAP, String.valueOf(measures.size()),
					measures.size() == 1 ? "measure" : "measures");

			if (measures.isEmpty()) {
				return;
			}

			int y = TILE_H + TABLE_GAP;
			int valueCols = 3 * VALUE_COL_W;
			int nameW = Math.max(40, w - valueCols);
			FontMetrics hfm = g2.getFontMetrics(ViewerStyle.smallFont());
			g2.setFont(ViewerStyle.smallFont());
			g2.setColor(ViewerStyle.TEXT_SECONDARY);
			int hb = y + (HEADER_H - hfm.getHeight()) / 2 + hfm.getAscent();
			g2.drawString("MEASURE", 0, hb);
			String[] heads = { "MIN", "AVG", "MAX" };
			for (int i = 0; i < 3; i++) {
				int right = nameW + (i + 1) * VALUE_COL_W;
				g2.drawString(heads[i], right - hfm.stringWidth(heads[i]), hb);
			}
			y += HEADER_H;
			g2.setColor(ViewerStyle.SEPARATOR);
			g2.drawLine(0, y, w, y);

			FontMetrics nfm = g2.getFontMetrics(ViewerStyle.measureNameFont());
			FontMetrics vfm = g2.getFontMetrics(ViewerStyle.measureValueFont());
			for (int r = 0; r < measures.size(); r++) {
				String m = measures.get(r);
				if (r % 2 == 1) {
					g2.setColor(ViewerStyle.withAlpha(ViewerStyle.GRID_LINE, 160));
					g2.fillRect(0, y, w, ROW_H);
				}
				int baseline = y + (ROW_H - nfm.getHeight()) / 2 + nfm.getAscent();
				g2.setFont(ViewerStyle.measureNameFont());
				g2.setColor(ViewerStyle.TEXT_SECONDARY);
				g2.drawString(MeasureTable.fitString(nfm, m, nameW - 6), 0, baseline);

				DescriptiveStats s = stats.get(m);
				String[] vals = (s == null) ? new String[] { "\u2014", "\u2014", "\u2014" }
						: new String[] { ViewerStyle.formatNumber(s.min), ViewerStyle.formatNumber(s.mean),
								ViewerStyle.formatNumber(s.max) };
				g2.setFont(ViewerStyle.measureValueFont());
				g2.setColor(s == null ? ViewerStyle.TEXT_SECONDARY : ViewerStyle.TEXT_PRIMARY);
				for (int i = 0; i < 3; i++) {
					int right = nameW + (i + 1) * VALUE_COL_W;
					String v = MeasureTable.fitString(vfm, vals[i], VALUE_COL_W - 4);
					g2.drawString(v, right - vfm.stringWidth(v), baseline);
				}
				y += ROW_H;
			}
		} finally {
			g2.dispose();
		}
	}

	private static void paintTile(Graphics2D g2, int x, int y, int w, String big, String caption) {
		g2.setColor(ViewerStyle.ACCENT_SOFT);
		g2.fillRoundRect(x, y, w, TILE_H, 8, 8);

		Font f = ViewerStyle.bigNumberFont();
		FontMetrics fm = g2.getFontMetrics(f);
		while (fm.stringWidth(big) > w - 16 && f.getSize2D() > 11f) {
			f = f.deriveFont(f.getSize2D() - 1f);
			fm = g2.getFontMetrics(f);
		}
		g2.setFont(f);
		g2.setColor(ViewerStyle.ACCENT_DARK);
		g2.drawString(big, x + 8, y + 6 + fm.getAscent());

		g2.setFont(ViewerStyle.smallFont());
		g2.setColor(ViewerStyle.TEXT_SECONDARY);
		FontMetrics cfm = g2.getFontMetrics();
		g2.drawString(caption, x + 8, y + TILE_H - 6 - cfm.getDescent());
	}

	/** Kept for possible future use (color of the alternating rows). */
	@SuppressWarnings("unused")
	private static final Color ROW_ALT = ViewerStyle.GRID_LINE;
}