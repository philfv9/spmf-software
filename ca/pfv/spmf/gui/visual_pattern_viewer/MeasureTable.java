package ca.pfv.spmf.gui.visual_pattern_viewer;

import java.awt.Color;
import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.Map;

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
 * Lays out and paints the measures of a pattern as a small table with three
 * aligned columns: measure name, value, and a bar showing where the value lies
 * between the minimum and maximum of that measure. Used at the bottom of every
 * pattern card so that all pattern types look the same.
 *
 * @author Philippe Fournier-Viger
 */
public final class MeasureTable {

	/** Gap around the separator line drawn above the table */
	public static final int SECTION_GAP = 7;

	private static final int ROW_GAP = 3;
	private static final int COL_GAP = 8;
	private static final int BAR_HEIGHT = 7;
	private static final int BAR_PREF_WIDTH = 90;
	private static final int BAR_MIN_WIDTH = 30;

	private final Map<String, String> measures;
	private final Map<String, Double> minValues;
	private final Map<String, Double> maxValues;
	private final FontMetrics nameFm;
	private final FontMetrics valueFm;
	private final int nameColWidth;
	private final int valueColWidth;
	private final int rowHeight;
	private final boolean anyBar;

	/**
	 * @param owner     component used to obtain font metrics
	 * @param measures  measure name → raw value (insertion order is kept)
	 * @param minValues measure name → min value (null entries allowed)
	 * @param maxValues measure name → max value (null entries allowed)
	 */
	public MeasureTable(Component owner, Map<String, String> measures, Map<String, Double> minValues,
			Map<String, Double> maxValues) {
		this.measures = measures == null ? Map.of() : measures;
		this.minValues = minValues == null ? Map.of() : minValues;
		this.maxValues = maxValues == null ? Map.of() : maxValues;
		this.nameFm = owner.getFontMetrics(ViewerStyle.measureNameFont());
		this.valueFm = owner.getFontMetrics(ViewerStyle.measureValueFont());
		this.rowHeight = Math.max(nameFm.getHeight(), valueFm.getHeight());

		int nameW = 0;
		int valueW = 0;
		boolean bar = false;
		for (Map.Entry<String, String> e : this.measures.entrySet()) {
			nameW = Math.max(nameW, nameFm.stringWidth(e.getKey()));
			valueW = Math.max(valueW, valueFm.stringWidth(ViewerStyle.formatValue(e.getValue())));
			if (barRatio(e.getKey(), e.getValue()) != null) {
				bar = true;
			}
		}
		this.nameColWidth = nameW;
		this.valueColWidth = valueW;
		this.anyBar = bar;
	}

	/** @return number of measure rows */
	public int getRowCount() {
		return measures.size();
	}

	/** @return height of the rows only (0 if there is no measure) */
	public int getHeight() {
		int n = measures.size();
		return n == 0 ? 0 : n * rowHeight + (n - 1) * ROW_GAP;
	}

	/**
	 * @return height of the whole section (separator, gaps and rows), i.e. what
	 *         a card must reserve at its bottom
	 */
	public int getSectionHeight() {
		return measures.isEmpty() ? 0 : SECTION_GAP + 1 + SECTION_GAP + getHeight();
	}

	/** @return width needed to show names, values and bars comfortably */
	public int getPreferredWidth() {
		if (measures.isEmpty()) {
			return 0;
		}
		return nameColWidth + COL_GAP + valueColWidth + (anyBar ? COL_GAP + BAR_PREF_WIDTH : 0);
	}

	/**
	 * @return the fill ratio of the bar for a measure, or null if no bar should
	 *         be drawn (non-numeric value or unknown range)
	 */
	public Double barRatio(String name, String rawValue) {
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

	/**
	 * Paints the separator and the rows so that the last row ends at
	 * {@code bottom}.
	 *
	 * @param x      left edge of the table
	 * @param bottom y coordinate of the bottom of the last row
	 * @param width  available width
	 */
	public void paintAtBottom(Graphics2D g2, int x, int bottom, int width) {
		if (measures.isEmpty()) {
			return;
		}
		int top = bottom - getHeight();
		int sepY = top - SECTION_GAP - 1;
		g2.setColor(ViewerStyle.SEPARATOR);
		g2.drawLine(x, sepY, x + width, sepY);
		paintRows(g2, x, top, width);
	}

	/**
	 * Paints the rows starting at (x, y).
	 */
	public void paintRows(Graphics2D g2, int x, int y, int width) {
		int valueRight = x + nameColWidth + COL_GAP + valueColWidth;
		int barX = valueRight + COL_GAP;
		int barW = x + width - barX;
		int index = 0;
		for (Map.Entry<String, String> e : measures.entrySet()) {
			String name = e.getKey();
			String raw = e.getValue();
			int baseline = y + (rowHeight - nameFm.getHeight()) / 2 + nameFm.getAscent();

			g2.setFont(ViewerStyle.measureNameFont());
			g2.setColor(ViewerStyle.TEXT_SECONDARY);
			g2.drawString(name, x, baseline);

			g2.setFont(ViewerStyle.measureValueFont());
			g2.setColor(ViewerStyle.TEXT_PRIMARY);
			String value = ViewerStyle.formatValue(raw);
			if (ViewerStyle.parseDouble(raw) == null) {
				// Free text: use the remaining width, left aligned
				int textX = x + nameColWidth + COL_GAP;
				g2.drawString(fitString(valueFm, value, x + width - textX), textX, baseline);
			} else {
				g2.drawString(value, valueRight - valueFm.stringWidth(value), baseline);
				Double ratio = barRatio(name, raw);
				if (ratio != null && barW >= BAR_MIN_WIDTH) {
					Color color = ViewerStyle.measureColor(name, index);
					int barY = y + (rowHeight - BAR_HEIGHT) / 2;
					ViewerStyle.paintBar(g2, barX, barY, barW, BAR_HEIGHT, ratio, color);
				}
			}
			y += rowHeight + ROW_GAP;
			index++;
		}
	}

	/**
	 * Truncates a string with an ellipsis so that it fits in maxWidth pixels.
	 */
	public static String fitString(FontMetrics fm, String s, int maxWidth) {
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

	/**
	 * @return the measures as HTML lines ("<b>NAME:</b> value<br>") for tooltips
	 */
	public static String toHtmlRows(Map<String, String> measures) {
		if (measures == null || measures.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, String> e : measures.entrySet()) {
			sb.append("<b>").append(ViewerStyle.escapeHtml(e.getKey())).append(":</b> ")
					.append(ViewerStyle.escapeHtml(e.getValue() == null ? "" : e.getValue().trim())).append("<br>");
		}
		return sb.toString();
	}
}