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
import java.util.List;

/**
 * This class is a set of high utility association rules
 *  @see AlgoFHIM_and_HUCI
 *  @see AlgoHGB
 *  @see AlgoHGBAll
 * @author Jay
 */
public class Rules {
	/** A list of list containing the rules**/
	public final List<List<Rule>> rules = new ArrayList<>();
	/** The name of this set of rules */
	private final String name;
	/** The number of rules that are stored */
	private int count;

	/** Constructor
	 * 
	 * @param name the name of this set of rules
	 */
	public Rules(String name) {
		this.name = name;
	}

	/**
	 * Print the set of rules
	 */
	public void printRules() {
		System.out.println(" ------- " + name + " -------");
//		int i = 0;
//		for (Rule rule : rules) {
//			System.out.print("rule " + (i+1) +";");
//			rule.print();
//			//System.out.print("   Utility :  "+ rule.getSupportAbsolu()+"   ");
//                        System.out.print("   ant utili :  " + rule.antuti()+"  ");
//			System.out.print("   Utility-Confidence :  " + rule.getConfidence());
//                        //System.out.print("   support :  " + rule.getSupport()+"  ");
//                       // System.out.print(";"+rule.getSupportAbsolu()+";");
//                        //System.out.print(rule.antuti()+" ");
//			//System.out.print(rule.getConfidence()+";");
//                        //System.out.print(rule.getSupport());
//			System.out.println("");
//			i++;
//		}
		System.out.println("Total number of Rules = " + count);
		System.out.println(" --------------------------------");
	}

	/**
	 * Add a rule to this set of rules
	 * @param rule the rule to be added
	 */
	public void addRule(Rule rule) {
 
		while (rules.size() <= rule.getParent().size()) {
			rules.add(new ArrayList<>());
			// siz=true;
		}
		rules.get(rule.getParent().size()).add(rule);
		count++;
	}

	/**
	 * Get the number of levels
	 * @return the number of rules
	 */
	public int getLevelCount() {
		return rules.size();
	}

}
