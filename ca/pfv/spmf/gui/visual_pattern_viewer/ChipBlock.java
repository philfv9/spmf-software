package ca.pfv.spmf.gui.visual_pattern_viewer;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

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
 * Lays out a list of labels as "chips" (pills) that wrap on several rows when
 * a maximum width is exceeded, and paints them. Rows are centered inside the
 * block. A maximum width of 0 produces a vertical stack (one chip per row).
 * <p>
 * Coordinates are relative to the block origin, so the same layout can be
 * painted at any position.
 *
 * @author Philippe Fournier-Viger
 */
public final class ChipBlock {

	/** Horizontal padding inside a chip */
	public static final int H_PAD = 9;
	/** Vertical padding inside a chip */
	public static final int V_PAD = 3;
	/** Gap between two chips on the same row */
	public static final int GAP = 5;
	/** Gap between two rows */
	public static final int ROW_GAP = 4;

	private final List<String> labels;
	private final List<Rectangle> rects = new ArrayList<>();
	private final List<Integer> rowOfChip = new ArrayList<>();
	private final List<Integer> rowWidths = new ArrayList<>();
	private final int width;
	private final int height;

	private ChipBlock(FontMetrics fm, List<String> labels, int maxWidth) {
		this.labels = labels;
		int chipH = chipHeight(fm);
		int x = 0;
		int y = 0;
		int row = 0;
		int rowW = 0;
		int maxRowW = 0;
		for (String label : labels) {
			int chipW = fm.stringWidth(label) + 2 * H_PAD;
			if (x > 0 && x + chipW > maxWidth) {
				rowWidths.add(rowW);
				maxRowW = Math.max(maxRowW, rowW);
				row++;
				x = 0;
				y += chipH + ROW_GAP;
			}
			rects.add(new Rectangle(x, y, chipW, chipH));
			rowOfChip.add(row);
			x += chipW + GAP;
			rowW = x - GAP;
		}
		int h = 0;
		if (!rects.isEmpty()) {
			rowWidths.add(rowW);
			maxRowW = Math.max(maxRowW, rowW);
			h = y + chipH;
		}
		this.width = maxRowW;
		this.height = h;
	}

	/**
	 * Lays out chips that wrap when a row would be wider than maxWidth.
	 *
	 * @param fm       metrics of the font that will be used to paint
	 * @param labels   the labels
	 * @param maxWidth maximum row width in pixels (0 = vertical stack)
	 * @return the layout
	 */
	public static ChipBlock layout(FontMetrics fm, List<String> labels, int maxWidth) {
		return new ChipBlock(fm, labels == null ? List.of() : labels, maxWidth);
	}

	/**
	 * Lays out chips vertically, one per row.
	 */
	public static ChipBlock stack(FontMetrics fm, List<String> labels) {
		return layout(fm, labels, 0);
	}

	/**
	 * @return the height of one chip for the given font
	 */
	public static int chipHeight(FontMetrics fm) {
		return fm.getHeight() + 2 * V_PAD;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public boolean isEmpty() {
		return rects.isEmpty();
	}

	public int getRowCount() {
		return rowWidths.size();
	}

	/**
	 * Paints the chips with the block origin at (x, y). The graphics context
	 * must already have the chip font set.
	 *
	 * @param fixedColors colors {fill, border, text} used for every chip, or
	 *                    null to derive a stable color from each label
	 */
	public void paint(Graphics2D g, FontMetrics fm, int x, int y, Color[] fixedColors) {
		for (int i = 0; i < rects.size(); i++) {
			Rectangle r = rects.get(i);
			int rowW = rowWidths.get(rowOfChip.get(i));
			Rectangle abs = new Rectangle(x + r.x + (width - rowW) / 2, y + r.y, r.width, r.height);
			String label = labels.get(i);
			if (fixedColors != null) {
				ViewerStyle.paintChip(g, label, fixedColors, abs, fm);
			} else {
				ViewerStyle.paintChip(g, label, label, abs, fm);
			}
		}
	}
}