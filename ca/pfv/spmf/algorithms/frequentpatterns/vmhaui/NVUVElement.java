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
 * An element of an NVUV-list, made of the fields nid, mn and vsuv.
 */
class NVUVElement {
	
	/** the identifier of the TPUT node having the first item of the itemset */
	final int nid;
	
	/** the maximal number of remaining items after the last item of the itemset */
	final int mn;
	
	/** the vertical sum utility vector, shared with the other elements having the same nid */
	final UtilityVector vsuv;

	/**
	 * Constructor.
	 * @param nid the node identifier
	 * @param mn the maximal remaining item number
	 * @param vsuv the shared vertical sum utility vector
	 */
	public NVUVElement(int nid, int mn, UtilityVector vsuv) {
		this.nid = nid;
		this.mn = mn;
		this.vsuv = vsuv;
	}
}