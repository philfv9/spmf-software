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
 * Symbolic time interval
 * 
 * @author Philippe Fournier-Viger, Yuechun Li, 2023
 */
public class SymbolicTimeInterval extends TimeInterval {

	/** symbol */
	public final int symbol;

	/**
	 * Constructor
	 * 
	 * @param start  the start of the interval
	 * @param end    the end of the interval
	 * @param symbol the symbol of this interval
	 */
	public SymbolicTimeInterval(int start, int end, int symbol) {
		super(start, end);
		this.symbol = symbol;
	}
}
