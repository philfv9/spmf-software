package ca.pfv.spmf.algorithms.episodes.poerm;

/* This file is copyright (c) 2021  CHEN YANGMING, Philippe Fournier-Viger
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
* This is an auxiliary data structure of the "POERM and POERM-ALL" algorithm, 
* This is used to record the parameter settings of the algorithm <br/>
* <br/>
*
* Paper: Mining Partially-Ordered Episode Rules in an Event Sequence
* @see POERMALL
* @see AlgoPOERM
*/
public class POERParas {
	/** the min support of POERM algorithm*/
	private int minSupport;
	
	/** the XSpan of POERM algorithm*/
	private int xSpan;
	
	/** the YSpan of POERM algorithm*/
	private int ySpan;
	
	/** the min confidence of POERM algorithm*/
	private double minConfidence;
	
	/** the XYSpan of POERM algorithm*/
	private int xySpan;
	
	/** If the input file does not contain timestamps, 
	 *        then set this variable to true to automatically assign timestamps as 1,2,3...
	 */
	boolean selfIncrement;
	
	/**
	 *  Constructor
	 * @param minSupport minimum support threshold
	 * @param xSpan  xspan
	 * @param ySpan  yspan
	 * @param minConfidence minimum confidence threshold
	 * @param xySpan  xyspan
	 * @param selfIncrement If the input file does not contain timestamps, 
	 *  then set this variable to true to automatically assign timestamps as 1,2,3...
	 */
	public POERParas(int minSupport, int xSpan, int ySpan, double minConfidence, int xySpan, boolean selfIncrement) {
		super();
		this.minSupport = minSupport;
		this.xSpan = xSpan;
		this.ySpan = ySpan;
		this.minConfidence = minConfidence;
		this.xySpan = xySpan;
		this.selfIncrement = selfIncrement;
	}
	
	public boolean isSelfIncrement() {
		return selfIncrement;
	}

	public void setSelfIncrement(boolean selfIncrement) {
		this.selfIncrement = selfIncrement;
	}

	public int getMinSupport() {
		return minSupport;
	}

	public void setMinSupport(int minSupport) {
		this.minSupport = minSupport;
	}

	public int getXSpan() {
		return xSpan;
	}

	public void setXSpan(int xSpan) {
		this.xSpan = xSpan;
	}

	public int getYSpan() {
		return ySpan;
	}

	public void setYSpan(int ySpan) {
		this.ySpan = ySpan;
	}

	public double getMinConfidence() {
		return minConfidence;
	}

	public void setMinConfidence(double minConfidence) {
		this.minConfidence = minConfidence;
	}

	public int getXYSpan() {
		return xySpan;
	}
	
	public int getWinlen() {
		return xySpan;
	}
	
	public void setXYSpan(int xySpan) {
		this.xySpan = xySpan;
	}
}
