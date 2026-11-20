package ca.pfv.spmf.algorithms.sequentialpatterns.suchain;

/* This file is copyright (c) 2026 Philippe Fournier-Viger
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
 *
 * Do not remove copyright and license information.
 */

/**
 * This class represents a single element of the utility-list used inside the
 * sequence-utility (SU)-Chain structure of the SUCHain algorithm.
 * <br/>
 * As described in the SUCHain paper, each element stores four fields: the
 * concatenation position, the maximum utility at that concatenation position,
 * the utility of the remaining sequence and a pointer to the next concatenation
 * position in the utility-list.
 *
 * @see AlgoSUChain
 * @see SUChain
 * @author Philippe Fournier-Viger
 */
class SUElement {

	/** the concatenation position of this element */
	int position;
	/** the maximum utility at this concatenation position */
	int maxUtility;
	/** the utility of the remaining sequence s/t,pi (excluding the irrelevant items pruned by the IPS on the path from the root) */
	int remainingUtility;
	/** the pointer to the next concatenation position, or -1 if none */
	int next;

	/**
	 * Constructor
	 * @param position the concatenation position
	 * @param maxUtility the maximum utility at this concatenation position
	 * @param remainingUtility the utility of the remaining sequence
	 * @param next the pointer to the next concatenation position
	 */
	SUElement(int position, int maxUtility, int remainingUtility, int next) {
		this.position = position;
		this.maxUtility = maxUtility;
		this.remainingUtility = remainingUtility;
		this.next = next;
	}
}
