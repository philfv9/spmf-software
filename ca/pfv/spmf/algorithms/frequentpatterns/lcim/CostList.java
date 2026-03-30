package ca.pfv.spmf.algorithms.frequentpatterns.lcim;

/* This file is copyright (c) 2008-2021 Philippe Fournier-Viger
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class represents a CostList as used by the algorithm.
 *
 * @see AlgoLCIM
 * @author Philippe Fournier-Viger, M. Saqib Nawaz
 */
public class CostList {
	/** the last item added to this costlist*/
	Integer item;
	
	/** the utility of this itemset */
	long utility = 0;
	
	/** the cost of this itemset */
	long cost = 0;
	
	/** the list of transaction ids of this itemset */
	List<Integer> tids = new ArrayList<Integer>();
	
	/** the list of cost values of this itemset */
	List<Integer> costs = new ArrayList<Integer>();
	
	/** the calculated ACB lower bound on the cost for this itemset. */
	double lowerbound = -1;

	/** A buffer that is used to save memory when calculating the lower bound*/
	static List<Integer> costBuffer = new ArrayList<Integer>();

	/**
	 * Constructor.
	 * 
	 * @param item the item that is used for this utility list
	 */
	public CostList(Integer item) {
		this.item = item;
	}

	/**
	 * Method to add an element to this utility list and update the sums at the same
	 * time.
	 */
	public void addElement(int tid, int utility, int cost) {
		this.utility += utility;
		this.cost += cost;
		tids.add(tid);
		costs.add(cost);
	}

	/**
	 * Get the support of the itemset represented by this utility-list
	 * 
	 * @return the support as a number of trnsactions
	 */
	public int getSupport() {
		return tids.size();
	}

	/**
	 * Get the utility
	 * 
	 * @return the utility
	 */
	public long getUtility() {
		return utility;
	}

	/**
	 * Get average utility
	 * 
	 * @return the average utility
	 */
	public double getAverageUtility() {
		return utility / (double) tids.size();
	}

	/**
	 * Get average cost
	 * 
	 * @return the average cost
	 */
	public double getAverageCost() {
		return cost / (double) tids.size();
	}

	@Override
	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("[item:" + item);
		buffer.append(" cost:" + cost);
		buffer.append(" utility:" + utility);
		buffer.append(" tids: " + tids);
		if (tids.size() > 0) {
			buffer.append(" support: " + getSupport());
			buffer.append(" avgcost: " + getAverageCost());
			buffer.append(" avgutility: " + getAverageUtility());
		}
		buffer.append("]");
		return buffer.toString();
	}

	/**
	 * Get the lower bound on the cost
	 * 
	 * @param minsup minimum support
	 * @return the lower bound
	 */
	public double getCostLowerBound(int minsup) {
		if (lowerbound == -1) {
			costBuffer.clear();
			costBuffer.addAll(costs);
			costBuffer.sort(Collections.reverseOrder());
			lowerbound = 0;
			for (int i = 0; i < minsup && i < costBuffer.size(); i++) {
				lowerbound += costBuffer.get(i);
			}
			lowerbound = lowerbound / (double) tids.size();

		}
		return lowerbound;
	}

}
