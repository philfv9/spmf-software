package ca.pfv.spmf.algorithms.sequentialpatterns.fasttirp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/* This file is copyright (c) 2008-2015 Philippe Fournier-Viger, Yuechun Li
* 
* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
* 
* SPMF is free software: you can redistribute it and/or modify it under the
* terms of the GNU General Public License as published by the Free Software
* Foundation, either version 3 of the License, or (at your option) any later
* version.
* 
* SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
* A PARTICULAR PURPOSE. See the GNU General Public License for more details.
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
*/
/**
 * A table from the vertical database *
 * 
 * @author Philippe Fournier-Viger, Yuechun Li, 2023
 */
public class Table {
	/** The symbols */
	final int[] symbols;

	/** The lines from the table */
	private List<Line> lines = new ArrayList<>();

	/** Vertical support */
	private int verticalSupport = 0;

	/** Sum of durations */
	private int sumOfDuration = 0;

	/**
	 * Constructor
	 * 
	 * @param symbol the symbol
	 */
	public Table(int symbol) {
		symbols = new int[] { symbol };
	}

	/**
	 * Constructor
	 * 
	 * @param symbol the symbol
	 */
	public Table(int[] symbolsP, int[] symbolsF) {
		// Create the new array of symbols
		symbols = new int[symbolsP.length + 1];

		// Copy the symbols from P
		System.arraycopy(symbolsP, 0, symbols, 0, symbolsP.length);

		// Copy the symbol from F in the last position
		symbols[symbols.length - 1] = symbolsF[0];
	}

	/**
	 * Get the vertical support
	 * 
	 * @return the support
	 */
	public int getVerticalSupport() {
		return verticalSupport;
	}

	/**
	 * Get the mean duration
	 * 
	 * @return the mean duration
	 */
	public double getMeanDuration() {
		return sumOfDuration / (double) lines.size();
	}

	/**
	 * Get the mean horizontal support
	 * 
	 * @return the mean horizontal support
	 */
	public double getMeanHorizontalSupport() {
		return lines.size() / (double) getVerticalSupport();
	}

	/**
	 * Add a line
	 * 
	 * @param line a line
	 */
	public void addLine(Line line) {

		sumOfDuration += line.end - line.start;

		// If the table is empty, we increase the support by 1
		// because it is a new SID
		if (lines.isEmpty()) {
			verticalSupport = 1;
		} else {
			final int sidBefore = lines.get(lines.size() - 1).sid;

			if (sidBefore != line.sid) {
				verticalSupport++;
			}
		}

		// Add the line to the table
		lines.add(line);
	}

	/**
	 * Get the number of lines
	 * 
	 * @return the number of lines
	 */
	public int size() {
		return lines.size();
	}

	/**
	 * Get the line at some index
	 * 
	 * @return the line
	 */
	public Line getLine(int index) {
		return lines.get(index);
	}

	/**
	 * Get a string representation of this object
	 * 
	 * @return a string
	 */
	@Override
	public String toString() {
		return toString(true);
	}

	/**
	 * Get a string representation of this object
	 * 
	 * @param showLines if true, show the lines
	 * @return a string
	 */
	public String toString(boolean showLines) {
		StringBuffer buffer = new StringBuffer(40);

		buffer.append(Arrays.toString(symbols));

		// Vertical support
		buffer.append(" #SUP: ").append(getVerticalSupport());

		// Mean duration
		buffer.append(" #MD: ").append(getMeanDuration());

		// Mean horizontal support
		buffer.append(" #MHS: ").append(getMeanHorizontalSupport()).append(System.lineSeparator());

		// Lines from the table
		if (showLines) {
			for (Line line : lines) {
				buffer.append(line.toString()).append(System.lineSeparator());
			}
		}

		return buffer.toString();
	}

}
