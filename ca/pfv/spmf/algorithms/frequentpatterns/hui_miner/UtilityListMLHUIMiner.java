package ca.pfv.spmf.algorithms.frequentpatterns.hui_miner;

/* This file is copyright (c) 2008-2013 Philippe Fournier-Viger
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
import java.util.List;

/**
 * This class represents a UtilityList as used by the HUI-Miner algorithm.
 *
 * @see AlgoHUIMiner
 * @see Element
 * @author Philippe Fournier-Viger
 */
public class UtilityListMLHUIMiner {
	// Integer item; // the item
	List<Integer> item = new ArrayList<Integer>();
	double sumIutils = 0; // the sum of item utilities
	double sumRutils = 0; // the sum of remaining utilities
	List<ElementMLHUIMiner> elements = new ArrayList<ElementMLHUIMiner>(); // the elements

	public UtilityListMLHUIMiner() {

	}

	/**
	 * Constructor.
	 * @param item the item that is used for this utility list
	 */
	public UtilityListMLHUIMiner(List<Integer> item){
		this.item = item;
		
	}

	/**
	 * Method to add an element to this utility list and update the sums at the same
	 * time.
	 */
	public void addElement(ElementMLHUIMiner element) {

		int flag = 0;

		int support = elements.size();

		for (int i = 0; i < support; i++) {
			if (elements.get(i).tid == element.tid) {
				ElementMLHUIMiner temp = new ElementMLHUIMiner(element.tid, elements.get(i).iutils + element.iutils,
						elements.get(i).rutils + element.rutils);
				elements.set(i, temp);
				flag = 1;
				break;
			}
		}
		if (flag == 0) {
			elements.add(element);

		}

		sumIutils += element.iutils;
		sumRutils += element.rutils;

	}

	public void deleteElement(ElementMLHUIMiner element) {
		sumIutils -= element.iutils;
		sumRutils -= element.rutils;
		elements.remove(element);
	}

	/**
	 * Get the support of the itemset represented by this utility-list
	 * 
	 * @return the support as a number of trnsactions
	 */
	public int getSupport() {
		return elements.size();
	}
}
