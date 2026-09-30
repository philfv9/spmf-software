package ca.pfv.spmf.experimental.patternexplorer;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

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
 * Number formatting helpers used by the Interactive Pattern Explorer.
 * 
 * @author Philippe Fournier-Viger
 */
public final class ExplorerFormat {

	private static final DecimalFormatSymbols SYMBOLS = DecimalFormatSymbols.getInstance(Locale.US);
	private static final DecimalFormat INTEGER = new DecimalFormat("#,##0", SYMBOLS);
	private static final DecimalFormat ONE_DECIMAL = new DecimalFormat("#,##0.0", SYMBOLS);
	private static final DecimalFormat THREE_DECIMALS = new DecimalFormat("0.###", SYMBOLS);
	private static final DecimalFormat FIVE_DECIMALS = new DecimalFormat("0.#####", SYMBOLS);
	private static final DecimalFormat SCIENTIFIC = new DecimalFormat("0.###E0", SYMBOLS);

	private ExplorerFormat() {
	}

	/** @return an integer with thousands separators */
	public static String integer(long value) {
		return INTEGER.format(value);
	}

	/** @return a compact human readable representation of a number */
	public static String number(double value) {
		if (Double.isNaN(value)) {
			return "\u2013";
		}
		if (Double.isInfinite(value)) {
			return value > 0 ? "\u221E" : "-\u221E";
		}
		double abs = Math.abs(value);
		if (value == Math.rint(value) && abs < 1e15) {
			return INTEGER.format((long) value);
		}
		if (abs >= 1e7) {
			return SCIENTIFIC.format(value);
		}
		if (abs >= 1000) {
			return ONE_DECIMAL.format(value);
		}
		if (abs >= 0.01) {
			return THREE_DECIMALS.format(value);
		}
		if (abs >= 1e-5) {
			return FIVE_DECIMALS.format(value);
		}
		return SCIENTIFIC.format(value);
	}

	/** @return a fraction (0..1) formatted as a percentage */
	public static String percent(double fraction) {
		if (Double.isNaN(fraction)) {
			return "\u2013";
		}
		return ONE_DECIMAL.format(fraction * 100.0) + "%";
	}

	/** @return "n word" or "n words" */
	public static String count(long n, String singular) {
		return integer(n) + " " + (n == 1 ? singular : singular + "s");
	}
}