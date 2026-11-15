package ca.pfv.spmf.algorithms.frequentpatterns.vmhaui;

/* This is an implementation of the VMHAUI algorithm.
*
* Copyright (c) 2026 VMHAUI
*
* This file is part of the SPMF DATA MINING SOFTWARE * (http://www.philippe-fournier-viger.com/spmf).
*
*
* SPMF is free software: you can redistribute it and/or modify it under the * terms of the GNU General Public License as published by the Free Software * Foundation, either version 3 of the License, or (at your option) any later * version. *
*
* SPMF is distributed in the hope that it will be useful, but WITHOUT ANY * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR * A PARTICULAR PURPOSE. See the GNU General Public License for more details. *
*
* You should have received a copy of the GNU General Public License along with * SPMF. If not, see <http://www.gnu.org/licenses/>.
*
* @author Philippe Fournier-Viger
* 
* Do not remove copyright and license information.
*/

/**
 * A vertical sum utility vector (vsuv) shared by all the NVUV-list elements that
 * have the same node identifier, as described in the optimization of the paper.
 */
class UtilityVector {
	
	/** the ranks of the items on the path from a node up to the root, in ascending order */
	final int[] ranks;
	
	/** the column utilities of these items over the transactions of the node */
	final long[] utils;

	/**
	 * Constructor.
	 * @param ranks the ranks of the items of the path
	 * @param utils the column utilities of the items of the path
	 */
	public UtilityVector(int[] ranks, long[] utils) {
		this.ranks = ranks;
		this.utils = utils;
	}
}