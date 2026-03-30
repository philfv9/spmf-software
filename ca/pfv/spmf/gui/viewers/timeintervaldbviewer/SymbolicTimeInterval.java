package ca.pfv.spmf.gui.viewers.timeintervaldbviewer;

/*
 * Copyright (c) 2008-2024 Philippe Fournier-Viger
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
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 */
/**
 * A symbolic time interval as used by algorithms like FastTIRP and VertTIRP
 * 
 * @author Philippe Fournier-Viger, 2024
 */
public class SymbolicTimeInterval {

	/** symbol */
	public final int symbol;

	/** start time */
	public final int start;
	/** end time */
	public final int end;

	/**
	 * Constructor
	 * 
	 * @param symbol the symbol
	 * @param start  the start time
	 * @param end    the end time
	 */
	public SymbolicTimeInterval(int symbol, int start, int end) {
		this.symbol = symbol;
		this.start = start;
		this.end = end;
	}

	/**
	 * Get a string representation of this time interval
	 * 
	 * @return the string
	 */
	public String toString() {
		final StringBuffer buffer = new StringBuffer();
		buffer.append(symbol).append(' ');
		buffer.append('[').append(start).append(',').append(end).append(']');
		return buffer.toString();
	}
}
