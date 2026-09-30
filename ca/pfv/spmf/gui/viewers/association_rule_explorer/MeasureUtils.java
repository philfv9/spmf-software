package ca.pfv.spmf.gui.viewers.association_rule_explorer;

import java.awt.Color;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

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
 * Helpers for formatting, describing and coloring measure values.
 * 
 * @author Philippe Fournier-Viger
 */
final class MeasureUtils {

	private static final DecimalFormat PLAIN = new DecimalFormat("0.####",
			DecimalFormatSymbols.getInstance(Locale.US));
	private static final DecimalFormat SCIENTIFIC = new DecimalFormat("0.##E0",
			DecimalFormatSymbols.getInstance(Locale.US));

	private static final Color NEGATIVE = new Color(33, 102, 172); // blue
	private static final Color NEUTRAL = new Color(200, 200, 200); // gray
	private static final Color POSITIVE = new Color(178, 24, 43); // red

	private MeasureUtils() {
	}

	/** Compact display format. */
	static String format(double v) {
		if (Double.isNaN(v)) {
			return "-";
		}
		if (Double.isInfinite(v)) {
			return v > 0 ? "+Inf" : "-Inf";
		}
		double a = Math.abs(v);
		if (a != 0 && (a < 1e-3 || a >= 1e7)) {
			return SCIENTIFIC.format(v);
		}
		return PLAIN.format(v);
	}

	/** Format a fraction (0..1) as a percentage, e.g. 0.125 -> "12.5%". */
	static String percent(double fraction) {
		return format(fraction * 100) + "%";
	}

	/** Format for writing back to an SPMF file. */
	static String formatForFile(double v) {
		if (!Double.isInfinite(v) && !Double.isNaN(v) && v == Math.rint(v) && Math.abs(v) < 1e15) {
			return Long.toString((long) v);
		}
		return Double.toString(v);
	}

	/** Human-readable name of common SPMF measure tags. */
	static String prettyName(String measure) {
		if (measure == null) {
			return "";
		}
		switch (measure.toUpperCase(Locale.ROOT)) {
		case "SUP":
		case "SUPPORT":
			return "Support";
		case "RSUP":
			return "Support (%)";
		case "CONF":
		case "CONFIDENCE":
			return "Confidence";
		case "LIFT":
			return "Lift";
		case "UTIL":
		case "UTILITY":
			return "Utility";
		case "LEV":
		case "LEVERAGE":
			return "Leverage";
		case "CONV":
		case "CONVICTION":
			return "Conviction";
		case "ALLCONF":
		case "ALL_CONFIDENCE":
			return "All-confidence";
		case "KULC":
			return "Kulczynski";
		case "CHISQ":
			return "Chi-square";
		case "COS":
			return "Cosine";
		case "JACC":
			return "Jaccard";
		case "CASUAL":
			return "Casual confidence";
		case "IMO":
			return "Implication intensity";
		default:
			return measure;
		}
	}

	/** Short explanation of a measure (plain text, no HTML characters). */
	static String describe(String measure) {
		if (measure == null) {
			return "Unknown measure.";
		}
		switch (measure.toUpperCase(Locale.ROOT)) {
		case "SUP":
		case "SUPPORT":
			return "Support: number of transactions that contain both the antecedent and the consequent.";
		case "RSUP":
			return "Support (%): percentage of all transactions that contain the rule (support / number of transactions).";
		case "CONF":
		case "CONFIDENCE":
			return "Confidence: how often the consequent appears when the antecedent appears (0 to 1).";
		case "LIFT":
			return "Lift: how many times more likely the consequent is when the antecedent is present. "
					+ "1 = independent, above 1 = positive correlation, below 1 = negative correlation.";
		case "LEV":
		case "LEVERAGE":
			return "Leverage: observed support minus the support expected if antecedent and consequent were independent. 0 = independent.";
		case "CONV":
		case "CONVICTION":
			return "Conviction: how much more often the rule would be wrong if antecedent and consequent were independent. 1 = independent, higher = stronger.";
		case "UTIL":
		case "UTILITY":
			return "Utility: total utility (e.g. profit) of the transactions containing the rule.";
		case "ALLCONF":
		case "ALL_CONFIDENCE":
			return "All-confidence: the minimum of the confidence of this rule and of its inverse rule. "
					+ "A high value means the rule is strong in both directions.";
		case "KULC":
			return "Kulczynski: the average of the two confidences (this rule and its inverse rule).";
		case "CHISQ":
			return "Chi-square: statistical dependence between the antecedent and the consequent.";
		case "COS":
			return "Cosine: the geometric mean of the two confidences of the rule and its inverse.";
		case "JACC":
			return "Jaccard: fraction of transactions with the rule among the transactions containing any of its items.";
		default:
			return "Measure " + measure + " (see the documentation of the algorithm).";
		}
	}

	/**
	 * Value meaning "independence" for a measure (1 for lift, 0 for leverage),
	 * or NaN if the measure has no natural center.
	 */
	static double centerValue(String measure) {
		if (measure == null) {
			return Double.NaN;
		}
		switch (measure.toUpperCase(Locale.ROOT)) {
		case "LIFT":
		case "CONV":
		case "CONVICTION":
			return 1.0;
		case "LEV":
		case "LEVERAGE":
			return 0.0;
		default:
			return Double.NaN;
		}
	}

	static boolean isDiverging(String measure) {
		return !Double.isNaN(centerValue(measure));
	}

	/**
	 * Color of a value: diverging (blue - gray - red around the center value)
	 * for lift-like measures, blue-to-red sequential otherwise.
	 */
	static Color colorFor(double v, String measure, double[] range) {
		if (range == null || Double.isNaN(v)) {
			return Color.GRAY;
		}
		double c = centerValue(measure);
		if (!Double.isNaN(c)) {
			double span = Math.max(Math.max(range[1] - c, c - range[0]), 1e-12);
			double t;
			if (Double.isInfinite(v)) {
				t = v > 0 ? 1 : -1;
			} else {
				t = Math.max(-1, Math.min(1, (v - c) / span));
			}
			return t >= 0 ? lerp(NEUTRAL, POSITIVE, t) : lerp(NEUTRAL, NEGATIVE, -t);
		}
		double t;
		if (Double.isInfinite(v)) {
			t = v > 0 ? 1 : 0;
		} else if (range[1] - range[0] < 1e-12) {
			t = 0.5;
		} else {
			t = Math.max(0, Math.min(1, (v - range[0]) / (range[1] - range[0])));
		}
		return sequential(t);
	}

	/** Blue (0) -> green -> red (1). */
	static Color sequential(double t) {
		t = Math.max(0, Math.min(1, t));
		return Color.getHSBColor((float) ((1 - t) * 0.66), 0.85f, 0.9f);
	}

	/** Fraction of a value within a range, clamped to [0,1]. */
	static double fraction(double v, double[] range) {
		if (range == null || Double.isNaN(v)) {
			return 0;
		}
		if (Double.isInfinite(v)) {
			return v > 0 ? 1 : 0;
		}
		if (range[1] - range[0] < 1e-12) {
			return 1;
		}
		return Math.max(0, Math.min(1, (v - range[0]) / (range[1] - range[0])));
	}

	private static Color lerp(Color a, Color b, double t) {
		return new Color((int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
				(int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
				(int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
	}
}
