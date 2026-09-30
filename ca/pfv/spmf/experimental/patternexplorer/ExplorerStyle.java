package ca.pfv.spmf.experimental.patternexplorer;

import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

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
 * Shared colors, fonts and small UI helpers of the Interactive Pattern
 * Explorer.
 * 
 * @author Philippe Fournier-Viger
 */
final class ExplorerStyle {

	static final Color ACCENT = new Color(46, 134, 193);
	static final Color ACCENT_HOVER = new Color(52, 152, 219);
	static final Color SELECTED = new Color(230, 126, 34);
	static final Color REQUIRED_BG = new Color(212, 237, 218);
	static final Color REQUIRED_FG = new Color(21, 87, 36);
	static final Color EXCLUDED_BG = new Color(248, 215, 218);
	static final Color EXCLUDED_FG = new Color(114, 28, 36);
	static final Color OTHER_BG = new Color(226, 232, 240);
	static final Color OTHER_FG = new Color(40, 55, 80);
	static final Color BORDER = new Color(200, 200, 200);
	static final Color MUTED = new Color(110, 110, 110);
	static final Color AXIS = new Color(160, 160, 160);
	static final Color TOOLBAR_BG = new Color(245, 245, 245);
	static final Color LINK = new Color(30, 100, 180);

	static final Font FONT_SMALL = new Font("SansSerif", Font.PLAIN, 10);
	static final Font FONT_SMALL_BOLD = new Font("SansSerif", Font.BOLD, 10);
	static final Font FONT_NORMAL = new Font("SansSerif", Font.PLAIN, 12);
	static final Font FONT_BOLD = new Font("SansSerif", Font.BOLD, 12);
	static final Font FONT_TITLE = new Font("SansSerif", Font.BOLD, 13);
	static final Font FONT_ITALIC = new Font("SansSerif", Font.ITALIC, 11);

	private static final List<Color> MEASURE_COLORS = List.of(new Color(46, 134, 193), new Color(192, 57, 43),
			new Color(39, 174, 96), new Color(142, 68, 173), new Color(230, 126, 34), new Color(22, 160, 133),
			new Color(211, 84, 0), new Color(41, 128, 185), new Color(127, 140, 141), new Color(155, 89, 182));

	private ExplorerStyle() {
	}

	/** @return a color for the i-th numeric measure */
	static Color measureColor(int index) {
		return MEASURE_COLORS.get(Math.abs(index) % MEASURE_COLORS.size());
	}

	/** @return the same color with a different alpha */
	static Color withAlpha(Color c, int alpha) {
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
	}

	/** @return a titled border in the style of the explorer */
	static Border titledBorder(String title) {
		TitledBorder border = BorderFactory.createTitledBorder(title);
		border.setTitleFont(FONT_BOLD);
		return BorderFactory.createCompoundBorder(border, BorderFactory.createEmptyBorder(2, 4, 4, 4));
	}

	/** @return a small gray label */
	static JLabel mutedLabel(String text) {
		JLabel label = new JLabel(text);
		label.setFont(FONT_SMALL);
		label.setForeground(MUTED);
		return label;
	}

	/** Makes a button compact */
	static void makeCompact(AbstractButton button) {
		button.setMargin(new Insets(2, 6, 2, 6));
		button.setFont(FONT_NORMAL);
		button.setFocusable(false);
	}
}