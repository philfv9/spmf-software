package ca.pfv.spmf.algorithms.frequentpatterns.lthui_miner;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/* This file is copyright (c) 2020  Yanjun Yang, Philippe Fournier-Viger
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
* This is an implementation of the "LTHUI-Miner" algorithm for locally trending high
* utility itemset mining as described in the conference paper : <br/>
* <br/>
*
* Fournier-Viger, P., Yang, Y., Lin, J. C.W., Frnda, J. (2020). Mining Locally 
* Trending High Utility Itemsets. Proc. 24th Pacific-Asia Conf. Knowledge Discovery 
* and Data Mining (PAKDD 2020), Springer, LNAI, pp.99-111.
* 
* @author Yanjun Yang, Philippe Fournier-Viger
* @see TrendUtilityList
*/
public class TrendUtilityList extends UtilityList {
	
	/**
	 * Constructor
	 * 
	 * @param item
	 *            an item
	 */
	public TrendUtilityList(Integer item, int numBin, int numWin) {
		super(item);
		utilBin = new int[numBin];
		rutilBin = new int[numBin];
		winRemain = new BitSet(numWin);
	}
	
	public TrendUtilityList(Integer item, int numBin) {
		super(item);
		utilBin = new int[numBin];
		rutilBin = new int[numBin];
	}

	/** to store the utility of each bin in the database of the itemset */
	int utilBin[];
	
	/** to store the remaining utility of each bin in the database of the itemset */
	int rutilBin[];
	
	/** to store whether the remaining utility upper-bound of sliding window is promising or not, 以sliding window的起始bin的index做该sliding window的记�? */
	BitSet winRemain;
	
	/**
	 * to store period that (iutitl)utility of the itemset is higher than
	 * threshold, and slope is higher than threshold
	 */
	ArrayList<Period> trendPeriod = new ArrayList<Period>();
	
	/** to store the average slope of each sliding window in the trend period */
	List<Double> trendSlope = new ArrayList<Double>();

	/** to store period that (iutil+rUtil) is higher */
	ArrayList<Period> rutilPeriod = new ArrayList<Period>();

}
