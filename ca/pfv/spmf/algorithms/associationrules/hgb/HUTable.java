/* This file is copyright (c) Jayakrushna Sahoo
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

package ca.pfv.spmf.algorithms.associationrules.hgb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *This is a table for itemsets 
 *
 *  @see AlgoFHIM_and_HUCI
 * @author Jayakrushna Sahoo
 */
public class HUTable {

	public final List<List<Itemset>> levels = new ArrayList<>();
	Map<Itemset, Integer> mapSupp = new HashMap<>();
	Map<Itemset, Boolean> mapKey = new HashMap<>();
	Map<Itemset, Boolean> mapClosed = new HashMap<>();

	public void addHuighUtilityItemset(Itemset itemset) {
		while (levels.size() <= itemset.size()) {
			levels.add(new ArrayList<>());

		}
		levels.get(itemset.size()).add(itemset);
	}

	public List<Itemset> getLevelFor(int i) {
		if (i + 1 == levels.size()) {
			List<Itemset> newList = new ArrayList<>();
			levels.add(newList);
			return newList;
		}
		return levels.get(i);
	}

	public List<List<Itemset>> getLevels() {
		return levels;
	}
}
