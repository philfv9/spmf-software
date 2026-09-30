package ca.pfv.spmf.gui.visual_pattern_viewer.itemsets;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.swing.JPanel;

import ca.pfv.spmf.gui.visual_pattern_viewer.ViewerStyle;

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
 */
/**
 * A Swing panel that visualizes a single itemset as a compact "card": the items
 * are drawn as colored chips that wrap on several lines if needed, and the
 * measures are listed below in aligned columns (name, value, bar).
 *
 * @author Philippe Fournier-Viger
 */
public class ItemsetPanel extends JPanel {
	private static final long serialVersionUID = 5263914307631939188L;

	/** Padding between the card border and its content */
	private static final int PAD = 10;
	/** Horizontal padding inside a chip */
	private static final int CHIP_H_PAD = 9;
	/** Vertical padding inside a chip */
	private static final int CHIP_V_PAD = 3;
	/** Gap between two chips on the same line */
	private static final int CHIP_GAP = 5;
	/** Gap between two lines of chips */
	private static final int ROW_GAP = 4;
	/** Gap around the separator between items and measures */
	private static final int SECTION_GAP = 7;
	/** Gap between two measure rows */
	private static final int MEASURE_ROW_GAP = 3;
	/** Gap between the columns of the measure table */
	private static final int COL_GAP = 8;
	/** Height of a measure bar */
	private static final int BAR_HEIGHT = 7;
	/** Preferred width of a measure bar */
	private static final int BAR_PREF_WIDTH = 90;
	/** A bar narrower than this is not drawn */
	private static final int BAR_MIN_WIDTH = 30;
	/** Chips wrap when a line would be wider than this */
	private static final int MAX_CHIP_AREA_WIDTH = 340;
	/** Minimum width of the content area */
	private static final int MIN_CONTENT_WIDTH = 150;

	/** The itemset to display. */
	private final Itemset itemset;
	/** Min value of each measure (null if not numeric) */
	private final Map<String, Double> minValues;
	/** Max value of each measure (null if not numeric) */
	private final Map<String, Double> maxValues;

	/** True while the mouse is over the card */
	private boolean hover;
	/** Cached preferred size (does not depend on the component width) */
	private Dimension cachedPreferredSize;

	/**
	 * Result of laying out the chips of an itemset.
	 */
	private static final class ChipLayout {
		final List<Rectangle> rects = new ArrayList<>();
		final List<Integer> rowOfChip = new ArrayList<>();
		final List<Integer> rowWidths = new ArrayList<>();
		int height;
		int maxRowWidth;
	}

	/**
	 * Column widths of the measure table.
	 */
	private static final class MeasureLayout {
		int nameColWidth;
		int valueColWidth;
		int rowHeight;
		boolean anyBar;
		int count;

		int totalHeight() {
			return count == 0 ? 0 : count * rowHeight + (count - 1) * MEASURE_ROW_GAP;
		}

		int minWidth() {
			return nameColWidth + COL_GAP + valueColWidth + (anyBar ? COL_GAP + BAR_PREF_WIDTH : 0);
		}
	}

	/**
	 * Constructs a panel for visualizing the given itemset.
	 *
	 * @param itemset   the itemset to display (must not be null)
	 * @param minValues map indicating the min value of each numeric measure
	 * @param maxValues map indicating the max value of each numeric measure
	 */
	public ItemsetPanel(Itemset itemset, Map<String, Double> minValues, Map<String, Double> maxValues) {
		this.itemset = Objects.requireNonNull(itemset, "itemset must not be null");
		this.minValues = minValues == null ? Map.of() : minValues;
		this.maxValues = maxValues == null ? Map.of() : maxValues;
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

	// ------------------------------------------------------------------
	// Layout computations
	// ------------------------------------------------------------------

	private ChipLayout layoutChips(FontMetrics fm, int availableWidth) {
		ChipLayout cl = new ChipLayout();
		int chipH = fm.getHeight() + 2 * CHIP_V_PAD;
		int x = 0;
		int y = 0;
		int row = 0;
		int rowW = 0;
		for (String label : itemset.getItems()) {
			int chipW = fm.stringWidth(label) + 2 * CHIP_H_PAD;
			if (x > 0 && x + chipW > availableWidth) {
				cl.rowWidths.add(rowW);
				row++;
				x = 0;
				y += chipH + ROW_GAP;
			}
			cl.rects.add(new Rectangle(x, y, chipW, chipH));
			cl.rowOfChip.add(row);
			x += chipW + CHIP_GAP;
			rowW = x - CHIP_GAP;
		}
		if (!cl.rects.isEmpty()) {
			cl.rowWidths.add(rowW);
			cl.height = y + chipH;
		}
		for (int w : cl.rowWidths) {
			cl.maxRowWidth = Math.max(cl.maxRowWidth, w);
		}
		return cl;
	}

	private MeasureLayout layoutMeasures(FontMetrics nameFm, FontMetrics valueFm) {
		MeasureLayout ml = new MeasureLayout();
		ml.rowHeight = Math.max(nameFm.getHeight(), valueFm.getHeight());
		for (Map.Entry<String, String> e : itemset.getMeasures().entrySet()) {
			ml.count++;
			ml.nameColWidth = Math.max(ml.nameColWidth, nameFm.stringWidth(e.getKey()));
			ml.valueColWidth = Math.max(ml.valueColWidth, valueFm.stringWidth(ViewerStyle.formatValue(e.getValue())));
			if (barRatio(e.getKey(), e.getValue()) != null) {
				ml.anyBar = true;
			}
		}
		return ml;
	}

	/**
	 * @return the fill ratio of the bar for a measure, or null if no bar should
	 *         be drawn (non numeric value or unknown range)
	 */
	private Double barRatio(String name, String rawValue) {
		Double v = ViewerStyle.parseDouble(rawValue);
		Double min = minValues.get(name);
		Double max = maxValues.get(name);
		if (v == null || min == null || max == null) {
			return null;
		}
		if (max <= min) {
			return 1d;
		}
		return (v - min) / (max - min);
	}

	@Override
	public Dimension getPreferredSize() {
		if (cachedPreferredSize == null) {
			FontMetrics itemFm = getFontMetrics(ViewerStyle.itemFont());
			FontMetrics nameFm = getFontMetrics(ViewerStyle.measureNameFont());
			FontMetrics valueFm = getFontMetrics(ViewerStyle.measureValueFont());

			ChipLayout cl = layoutChips(itemFm, MAX_CHIP_AREA_WIDTH);
			MeasureLayout ml = layoutMeasures(nameFm, valueFm);

			int contentW = Math.max(MIN_CONTENT_WIDTH, Math.max(cl.maxRowWidth, ml.minWidth()));
			int contentH = cl.height;
			if (ml.count > 0) {
				contentH += SECTION_GAP + 1 + SECTION_GAP + ml.totalHeight();
			}
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

			FontMetrics itemFm = g2.getFontMetrics(ViewerStyle.itemFont());
			FontMetrics nameFm = g2.getFontMetrics(ViewerStyle.measureNameFont());
			FontMetrics valueFm = g2.getFontMetrics(ViewerStyle.measureValueFont());

			// --- Measures are anchored at the bottom of the card ---
			MeasureLayout ml = layoutMeasures(nameFm, valueFm);
			int measuresH = ml.totalHeight();
			int measuresTop = cardH - PAD - measuresH;

			// --- Items occupy the remaining area, vertically centered ---
			int chipsAreaTop = PAD;
			int chipsAreaBottom = (ml.count > 0) ? measuresTop - SECTION_GAP - 1 - SECTION_GAP : cardH - PAD;
			ChipLayout cl = layoutChips(itemFm, contentW);
			int chipsY = chipsAreaTop + Math.max(0, (chipsAreaBottom - chipsAreaTop - cl.height) / 2);

			g2.setFont(ViewerStyle.itemFont());
			List<String> items = itemset.getItems();
			for (int i = 0; i < cl.rects.size(); i++) {
				Rectangle r = cl.rects.get(i);
				int rowW = cl.rowWidths.get(cl.rowOfChip.get(i));
				int offsetX = contentX + Math.max(0, (contentW - rowW) / 2);
				Rectangle abs = new Rectangle(r.x + offsetX, r.y + chipsY, r.width, r.height);
				ViewerStyle.paintChip(g2, items.get(i), items.get(i), abs, itemFm);
			}

			if (ml.count == 0) {
				return;
			}

			// --- Separator ---
			g2.setColor(ViewerStyle.SEPARATOR);
			int sepY = measuresTop - SECTION_GAP - 1;
			g2.drawLine(contentX, sepY, contentX + contentW, sepY);

			// --- Measure rows: name | value | bar ---
			int y = measuresTop;
			int valueRight = contentX + ml.nameColWidth + COL_GAP + ml.valueColWidth;
			int barX = valueRight + COL_GAP;
			int barW = contentX + contentW - barX;
			int index = 0;
			for (Map.Entry<String, String> e : itemset.getMeasures().entrySet()) {
				String name = e.getKey();
				String raw = e.getValue();
				int baseline = y + (ml.rowHeight - nameFm.getHeight()) / 2 + nameFm.getAscent();

				g2.setFont(ViewerStyle.measureNameFont());
				g2.setColor(ViewerStyle.TEXT_SECONDARY);
				g2.drawString(name, contentX, baseline);

				Double ratio = barRatio(name, raw);
				g2.setFont(ViewerStyle.measureValueFont());
				g2.setColor(ViewerStyle.TEXT_PRIMARY);
				String value = ViewerStyle.formatValue(raw);
				if (ratio == null && ViewerStyle.parseDouble(raw) == null) {
					// Free text: use all the remaining width, left aligned
					int textX = contentX + ml.nameColWidth + COL_GAP;
					String fitted = fitString(valueFm, value, contentX + contentW - textX);
					g2.drawString(fitted, textX, baseline);
				} else {
					g2.drawString(value, valueRight - valueFm.stringWidth(value), baseline);
					if (ratio != null && barW >= BAR_MIN_WIDTH) {
						Color color = ViewerStyle.measureColor(name, index);
						int barY = y + (ml.rowHeight - BAR_HEIGHT) / 2;
						ViewerStyle.paintBar(g2, barX, barY, barW, BAR_HEIGHT, ratio, color);
					}
				}
				y += ml.rowHeight + MEASURE_ROW_GAP;
				index++;
			}
		} finally {
			g2.dispose();
		}
	}

	/**
	 * Truncates a string with an ellipsis so that it fits in maxWidth pixels.
	 */
	private static String fitString(FontMetrics fm, String s, int maxWidth) {
		if (fm.stringWidth(s) <= maxWidth) {
			return s;
		}
		String ellipsis = "\u2026";
		int end = s.length();
		while (end > 0 && fm.stringWidth(s.substring(0, end) + ellipsis) > maxWidth) {
			end--;
		}
		return end == 0 ? "" : s.substring(0, end) + ellipsis;
	}

	// ------------------------------------------------------------------
	// Tooltip
	// ------------------------------------------------------------------

	private String buildToolTip() {
		StringBuilder sb = new StringBuilder("<html><b>Itemset</b> (");
		sb.append(itemset.getItems().size()).append(itemset.getItems().size() == 1 ? " item)" : " items)");
		sb.append("<br>");
		StringBuilder items = new StringBuilder();
		for (String it : itemset.getItems()) {
			if (items.length() > 0) {
				items.append(", ");
			}
			items.append(ViewerStyle.escapeHtml(it));
		}
		sb.append(ViewerStyle.escapeHtml(ViewerStyle.abbreviate(items.toString(), 400)));
		if (!itemset.getMeasures().isEmpty()) {
			sb.append("<br><br>");
			for (Map.Entry<String, String> e : itemset.getMeasures().entrySet()) {
				sb.append("<b>").append(ViewerStyle.escapeHtml(e.getKey())).append(":</b> ")
						.append(ViewerStyle.escapeHtml(e.getValue().trim())).append("<br>");
			}
		}
		sb.append("</html>");
		return sb.toString();
	}

	/**
	 * Get the itemset
	 *
	 * @return the itemset
	 */
	public Itemset getItemset() {
		return itemset;
	}
}