package ca.pfv.spmf.algorithms.frequentpatterns.lthui_miner;

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
public class Period {
	
	/** begin index of bin */
	public int beginIndex;
	
	/** end index of bin */
	public int endIndex;

	/**
	 * set begin and end index of bin for trend
	 * 
	 * @param begin
	 * @param end
	 */
	public void set(int beginIndex,int endIndex) {
		this.beginIndex = beginIndex;
		this.endIndex = endIndex;
	}

	/**
	 * Constructor
	 */
	public Period() {

	}

	/**
	 * Constructor
	 * @param begin begin index
	 * @param end  end index
	 */
	public Period(int begin, int end) {
		set(begin, end);
	}

}
