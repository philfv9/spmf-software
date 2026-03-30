package ca.pfv.spmf.algorithms.sequentialpatterns.fasttirp;

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
 * A time interval
 *
 * @author Philippe Fournier-Viger, Yuechun Li, 2023
 */
public class TimeInterval {

	/** start time */
	public final int start;
	/** end time */
	public final int end;

	/**
	 * Constructor
	 * 
	 * @param start the start time
	 * @param end   the end time
	 */
	public TimeInterval(int start, int end) {
		this.start = start;
		this.end = end;
	}

	/**
	 * Get the duration of the time interval
	 * 
	 * @return the duration as an integer
	 */
	public int getDuration() {
		return end - start;
	}

	/**
	 * Get a string representation of this object
	 * 
	 * @return a string
	 */
	@Override
	public String toString() {
		final StringBuffer buffer = new StringBuffer();
		buffer.append('[').append(start).append(',').append(end).append(']');
		return buffer.toString();
	}

}