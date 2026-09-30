package ca.pfv.spmf.gui.visual_pattern_viewer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Path2D;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.swing.AbstractButton;
import javax.swing.UIManager;

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
 * Centralized colors, fonts, number formatting and small painting helpers
 * shared by all pattern panels of the Visual Pattern Viewer, so that itemsets,
 * rules, sequential patterns and subgraphs have a consistent appearance.
 *
 * @author Philippe Fournier-Viger
 */
public final class ViewerStyle {

	private ViewerStyle() {
	}

	// ------------------------------------------------------------------
	// Colors
	// ------------------------------------------------------------------

	/** Background of the area containing the pattern cards */
	public static final Color CANVAS_BG = new Color(238, 240, 243);
	/** Background of a pattern card */
	public static final Color CARD_BG = Color.WHITE;
	/** Border of a pattern card */
	public static final Color CARD_BORDER = new Color(212, 216, 222);
	/** Border of a pattern card when the mouse is over it */
	public static final Color CARD_BORDER_HOVER = new Color(74, 144, 217);
	/** Soft shadow under a card */
	public static final Color CARD_SHADOW = new Color(0, 0, 0, 20);
	/** Thin separator line inside a card */
	public static final Color SEPARATOR = new Color(230, 232, 236);
	/** Main text color */
	public static final Color TEXT_PRIMARY = new Color(40, 44, 52);
	/** Secondary (label) text color */
	public static final Color TEXT_SECONDARY = new Color(112, 118, 128);
	/** Background track of a measure bar */
	public static final Color BAR_TRACK = new Color(230, 232, 236);
	/** Color of arrows (rule implication, sequence order) */
	public static final Color ARROW_COLOR = new Color(110, 118, 130);
	/** Color of graph edges */
	public static final Color EDGE_COLOR = new Color(150, 157, 168);
	/** Colors {fill, border, text} of chips belonging to a negated rule side */
	public static final Color[] NEGATED_CHIP_COLORS = { new Color(253, 235, 235), new Color(214, 92, 92),
			new Color(160, 30, 30) };
	/** Fill of the frame grouping the items of an itemset inside a sequence */
	public static final Color GROUP_FILL = new Color(247, 248, 250);
	/** Border of that frame */
	public static final Color GROUP_BORDER = new Color(196, 202, 210);

	/** Corner radius of cards */
	public static final int CARD_ARC = 12;
	/** Horizontal space reserved for the card shadow */
	public static final int SHADOW_DX = 2;
	/** Vertical space reserved for the card shadow */
	public static final int SHADOW_DY = 3;

	/** Distinct, readable colors for measure bars */
	private static final Color[] MEASURE_PALETTE = { new Color(66, 133, 244), // blue
			new Color(219, 68, 55), // red
			new Color(15, 157, 88), // green
			new Color(244, 160, 0), // amber
			new Color(156, 39, 176), // purple
			new Color(0, 150, 170), // teal
			new Color(255, 112, 67), // deep orange
			new Color(121, 85, 72), // brown
			new Color(63, 81, 181), // indigo
			new Color(233, 30, 99) // pink
	};

	/** Fixed colors for well-known measures so they look the same everywhere */
	private static final Map<String, Color> KNOWN_MEASURE_COLORS = new HashMap<>();
	static {
		KNOWN_MEASURE_COLORS.put("SUP", MEASURE_PALETTE[0]);
		KNOWN_MEASURE_COLORS.put("CONF", MEASURE_PALETTE[2]);
		KNOWN_MEASURE_COLORS.put("LIFT", MEASURE_PALETTE[4]);
		KNOWN_MEASURE_COLORS.put("UTIL", MEASURE_PALETTE[3]);
		KNOWN_MEASURE_COLORS.put("AUTIL", MEASURE_PALETTE[6]);
		KNOWN_MEASURE_COLORS.put("BOND", MEASURE_PALETTE[5]);
		KNOWN_MEASURE_COLORS.put("PVALUE", MEASURE_PALETTE[1]);
	}

	/**
	 * Returns the color to use for a measure bar.
	 *
	 * @param measureName   name of the measure (e.g. "SUP")
	 * @param fallbackIndex index of the measure in the pattern, used when the
	 *                      measure is not a well-known one
	 * @return a color
	 */
	public static Color measureColor(String measureName, int fallbackIndex) {
		Color c = measureName == null ? null : KNOWN_MEASURE_COLORS.get(measureName.toUpperCase(Locale.ROOT));
		if (c != null) {
			return c;
		}
		return MEASURE_PALETTE[Math.floorMod(fallbackIndex, MEASURE_PALETTE.length)];
	}

	/**
	 * Computes stable pastel colors for a "chip" (item label) from a key. The
	 * same key always yields the same color, so the same item is recognizable
	 * across all patterns.
	 *
	 * @param key the key (e.g. the item name)
	 * @return an array {fill, border, text}
	 */
	public static Color[] chipColors(String key) {
		int h = (key == null ? 0 : key.hashCode()) & 0x7fffffff;
		float hue = (float) ((h * 0.618033988749895) % 1.0);
		return new Color[] { Color.getHSBColor(hue, 0.16f, 0.985f), Color.getHSBColor(hue, 0.40f, 0.80f),
				Color.getHSBColor(hue, 0.70f, 0.38f) };
	}

	/**
	 * @param c     a color
	 * @param alpha alpha value 0..255
	 * @return the same color with the given alpha
	 */
	public static Color withAlpha(Color c, int alpha) {
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
	}

	// ------------------------------------------------------------------
	// Fonts (derived from the look and feel, computed lazily once)
	// ------------------------------------------------------------------

	private static Font baseFont;
	private static Font itemFont;
	private static Font measureNameFont;
	private static Font measureValueFont;
	private static Font smallFont;

	/** @return the base font of the current look and feel (plain) */
	public static synchronized Font baseFont() {
		if (baseFont == null) {
			Font f = UIManager.getFont("Label.font");
			if (f == null) {
				f = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
			}
			baseFont = f.deriveFont(Font.PLAIN);
		}
		return baseFont;
	}

	/** @return font used for items / symbols inside patterns */
	public static synchronized Font itemFont() {
		if (itemFont == null) {
			itemFont = baseFont().deriveFont(Font.PLAIN, Math.max(11f, baseFont().getSize2D()));
		}
		return itemFont;
	}

	/** @return font used for measure names */
	public static synchronized Font measureNameFont() {
		if (measureNameFont == null) {
			measureNameFont = baseFont().deriveFont(Font.BOLD, Math.max(10f, baseFont().getSize2D() - 1f));
		}
		return measureNameFont;
	}

	/** @return font used for measure values */
	public static synchronized Font measureValueFont() {
		if (measureValueFont == null) {
			measureValueFont = baseFont().deriveFont(Font.PLAIN, Math.max(10f, baseFont().getSize2D() - 1f));
		}
		return measureValueFont;
	}

	/** @return a small font for secondary information */
	public static synchronized Font smallFont() {
		if (smallFont == null) {
			smallFont = baseFont().deriveFont(Font.PLAIN, Math.max(9f, baseFont().getSize2D() - 2f));
		}
		return smallFont;
	}

	// ------------------------------------------------------------------
	// Text helpers
	// ------------------------------------------------------------------

	/**
	 * Parses a string as a double.
	 *
	 * @param s a string
	 * @return the value, or null if it is not a number
	 */
	public static Double parseDouble(String s) {
		if (s == null) {
			return null;
		}
		try {
			return Double.valueOf(s.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * Formats a raw measure value for display (numbers are shortened, other
	 * strings are returned trimmed).
	 *
	 * @param raw the raw value
	 * @return a display string
	 */
	public static String formatValue(String raw) {
		Double d = parseDouble(raw);
		return d == null ? (raw == null ? "" : raw.trim()) : formatNumber(d);
	}

	/**
	 * Formats a number compactly: integers without decimals, small numbers with
	 * up to 4 decimals (trailing zeros removed), very small numbers in
	 * scientific notation.
	 *
	 * @param v a value
	 * @return the formatted value
	 */
	public static String formatNumber(double v) {
		if (Double.isNaN(v) || Double.isInfinite(v)) {
			return String.valueOf(v);
		}
		if (v == Math.rint(v) && Math.abs(v) < 1e15) {
			return Long.toString((long) v);
		}
		double abs = Math.abs(v);
		if (abs < 1e-4) {
			return String.format(Locale.ROOT, "%.2e", v);
		}
		String pattern = abs < 1 ? "%.4f" : (abs < 100 ? "%.3f" : "%.2f");
		return stripTrailingZeros(String.format(Locale.ROOT, pattern, v));
	}

	private static String stripTrailingZeros(String s) {
		if (s.indexOf('.') < 0) {
			return s;
		}
		int end = s.length();
		while (end > 0 && s.charAt(end - 1) == '0') {
			end--;
		}
		if (end > 0 && s.charAt(end - 1) == '.') {
			end--;
		}
		return s.substring(0, end);
	}

	/**
	 * Shortens a string with an ellipsis if longer than maxChars.
	 */
	public static String abbreviate(String s, int maxChars) {
		if (s == null) {
			return "";
		}
		if (s.length() <= maxChars || maxChars < 2) {
			return s;
		}
		return s.substring(0, maxChars - 1) + "\u2026";
	}

	/**
	 * Escapes a string for inclusion in Swing HTML (tooltips).
	 */
	public static String escapeHtml(String s) {
		if (s == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder(s.length() + 16);
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
			case '&' -> sb.append("&amp;");
			case '<' -> sb.append("&lt;");
			case '>' -> sb.append("&gt;");
			case '"' -> sb.append("&quot;");
			default -> sb.append(c);
			}
		}
		return sb.toString();
	}

	// ------------------------------------------------------------------
	// Painting helpers
	// ------------------------------------------------------------------

	/**
	 * Enables anti-aliasing for shapes and text.
	 */
	public static void applyRenderingHints(Graphics2D g) {
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
	}

	/**
	 * Paints a rounded card with a soft shadow in the rectangle (x, y, w, h).
	 * The card itself occupies (x, y, w - SHADOW_DX, h - SHADOW_DY); the
	 * remaining pixels are used by the shadow.
	 */
	public static void paintCard(Graphics2D g, int x, int y, int w, int h, boolean hover) {
		int cw = w - SHADOW_DX;
		int ch = h - SHADOW_DY;
		if (cw <= 0 || ch <= 0) {
			return;
		}
		g.setColor(CARD_SHADOW);
		g.fillRoundRect(x + 1, y + 2, cw, ch, CARD_ARC, CARD_ARC);
		g.setColor(CARD_BG);
		g.fillRoundRect(x, y, cw, ch, CARD_ARC, CARD_ARC);
		g.setColor(hover ? CARD_BORDER_HOVER : CARD_BORDER);
		g.drawRoundRect(x, y, cw - 1, ch - 1, CARD_ARC, CARD_ARC);
		if (hover) {
			g.setColor(withAlpha(CARD_BORDER_HOVER, 70));
			g.drawRoundRect(x + 1, y + 1, cw - 3, ch - 3, CARD_ARC - 2, CARD_ARC - 2);
		}
	}

	/**
	 * Paints a pill-shaped chip with a centered label, using a stable color
	 * derived from colorKey. The graphics context must already have the desired
	 * font set.
	 */
	public static void paintChip(Graphics2D g, String label, String colorKey, Rectangle r, FontMetrics fm) {
		paintChip(g, label, chipColors(colorKey), r, fm);
	}

	/**
	 * Paints a pill-shaped chip with a centered label using explicit colors.
	 *
	 * @param colors an array {fill, border, text}
	 */
	public static void paintChip(Graphics2D g, String label, Color[] colors, Rectangle r, FontMetrics fm) {
		int arc = r.height;
		g.setColor(colors[0]);
		g.fillRoundRect(r.x, r.y, r.width, r.height, arc, arc);
		g.setColor(colors[1]);
		g.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1, arc, arc);
		g.setColor(colors[2]);
		int tx = r.x + (r.width - fm.stringWidth(label)) / 2;
		int ty = r.y + (r.height - fm.getHeight()) / 2 + fm.getAscent();
		g.drawString(label, tx, ty);
	}

	/**
	 * Paints a thin arrow with a filled head from (x1, y1) to (x2, y2).
	 */
	public static void paintArrow(Graphics2D g, int x1, int y1, int x2, int y2) {
		Stroke oldStroke = g.getStroke();
		g.setColor(ARROW_COLOR);
		g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		double angle = Math.atan2(y2 - y1, x2 - x1);
		double head = 7.0;
		double half = 3.5;
		// Shorten the shaft so it does not poke through the head
		int sx = (int) Math.round(x2 - (head - 2) * Math.cos(angle));
		int sy = (int) Math.round(y2 - (head - 2) * Math.sin(angle));
		g.drawLine(x1, y1, sx, sy);
		double bx = x2 - head * Math.cos(angle);
		double by = y2 - head * Math.sin(angle);
		Path2D.Double p = new Path2D.Double();
		p.moveTo(x2, y2);
		p.lineTo(bx + half * Math.sin(angle), by - half * Math.cos(angle));
		p.lineTo(bx - half * Math.sin(angle), by + half * Math.cos(angle));
		p.closePath();
		g.fill(p);
		g.setStroke(oldStroke);
	}

	/**
	 * Paints a horizontal progress-like bar.
	 *
	 * @param ratio fill ratio in [0, 1]
	 */
	public static void paintBar(Graphics2D g, int x, int y, int w, int h, double ratio, Color color) {
		if (w <= 0 || h <= 0) {
			return;
		}
		g.setColor(BAR_TRACK);
		g.fillRoundRect(x, y, w, h, h, h);
		double r = Math.max(0d, Math.min(1d, ratio));
		int fw = (int) Math.round(r * w);
		if (r > 0 && fw < h) {
			fw = h; // keep a visible dot for tiny values
		}
		if (fw > 0) {
			g.setColor(color);
			g.fillRoundRect(x, y, fw, h, h, h);
		}
	}
	
	// ------------------------------------------------------------------
	// Sidebar / charts
	// ------------------------------------------------------------------

	/** Background of the sidebar (same tone as the pattern canvas) */
	public static final Color SIDEBAR_BG = CANVAS_BG;
	/** Main accent color (bars, active filters, highlights) */
	public static final Color ACCENT = new Color(74, 144, 217);
	/** Darker accent (hover, big numbers) */
	public static final Color ACCENT_DARK = new Color(41, 105, 176);
	/** Very light accent used for highlighted areas */
	public static final Color ACCENT_SOFT = new Color(228, 239, 252);
	/** Grid lines of charts */
	public static final Color GRID_LINE = new Color(236, 238, 241);
	/** Axis lines of charts */
	public static final Color AXIS = new Color(196, 202, 210);

	private static Font sectionTitleFont;
	private static Font bigNumberFont;

	/** @return font for the title of sidebar sections */
	public static synchronized Font sectionTitleFont() {
		if (sectionTitleFont == null) {
			sectionTitleFont = baseFont().deriveFont(Font.BOLD, Math.max(11f, baseFont().getSize2D()));
		}
		return sectionTitleFont;
	}

	/** @return font for large numbers (statistics tiles) */
	public static synchronized Font bigNumberFont() {
		if (bigNumberFont == null) {
			bigNumberFont = baseFont().deriveFont(Font.BOLD, baseFont().getSize2D() + 7f);
		}
		return bigNumberFont;
	}

	/**
	 * Gives a button a compact, flat appearance consistent with the viewer.
	 */
	public static void styleButton(AbstractButton b) {
		b.setFocusPainted(false);
		b.setFont(measureValueFont());
		b.setMargin(new Insets(3, 10, 3, 10));
	}

	/**
	 * Returns a "nice" tick step (1, 2 or 5 times a power of ten) so that a range
	 * is split into about targetTicks intervals.
	 */
	public static double niceStep(double range, int targetTicks) {
		if (range <= 0 || targetTicks <= 0) {
			return 1;
		}
		double raw = range / targetTicks;
		double magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
		double norm = raw / magnitude;
		double nice = norm < 1.5 ? 1 : (norm < 3 ? 2 : (norm < 7 ? 5 : 10));
		return nice * magnitude;
	}
}