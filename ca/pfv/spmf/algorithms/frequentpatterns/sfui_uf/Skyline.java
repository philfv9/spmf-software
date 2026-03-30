package ca.pfv.spmf.algorithms.frequentpatterns.sfui_uf;

import java.util.ArrayList;
import java.util.List;
/*This file is copyright (c) 2021 Wei Song et al.
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
 * This class represents a skyline point, which contains itemSet, frequency and utility
 * 
 * @author Wei Song,Chuanlong Zheng,Philippe Fournier-Viger
 *
 */
public class Skyline {
	String itemSet; // the itemset
	int frequent; // the frequency of itemset
	int utility; // the utility of itemset

}

class SkylineList {
	// skylinelist store different itemsets that have same frequency and same utility.
	List<Skyline> skylinelist = new ArrayList<>();

	public Skyline get(int index) {
		return skylinelist.get(index);
	}

	public void add(Skyline e) {
		skylinelist.add(e);
	}

	public void remove(int index) {
		skylinelist.remove(index);
	}

	public int size() {
		return skylinelist.size();
	}
}
