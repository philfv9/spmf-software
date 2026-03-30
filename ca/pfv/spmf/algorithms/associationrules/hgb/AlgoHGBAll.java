
package ca.pfv.spmf.algorithms.associationrules.hgb;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ca.pfv.spmf.tools.MemoryLogger;

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
/**
 * This is an implementation of the algorithm to derive all high utility association rules
 * @see AlgoFHIM_and_HUCI
 * @author Jayakrushna Sahoo
 */

public class AlgoHGBAll {
	/** The patterns */
	private HUTable patterns;
	/** The rules that are found */
	private Rules rules;
	/** The minimum utility threhsold */
	private int minutility;
	/** the minimum confidence */
	private double minconf;
	/** Runtime of last execution of this algorithm*/
	private long runtime;
	/** Memory usage of last execution */
	private double maxMemory;
	/** Number of rules */
	private int ruleCount;

	/**
	 * Constructor
	 */
	public AlgoHGBAll() {
		
	}

	/**
	 * Run the algorithm
	 * @param patterns patterns
	 * @param minconf minimum confidence
	 * @param minutility minimum utility
	 * @return the rules that are generated
	 */
	public Rules runAlgorithm(HUTable patterns, double minconf, int minutility) {
		this.minconf = minconf;
		this.minutility = minutility;
		this.patterns = patterns;

		MemoryLogger.getInstance().reset();

		long start = System.currentTimeMillis();
		
		ruleCount = 0;
		rules = new Rules("All high utility association rules");

		// For each hui of size >=2
		for (int k = 1; k < patterns.levels.size(); k++) {
			for (Itemset lk : patterns.levels.get(k)) {
				// For each hui of size >k
				for (int j = k + 1; j < patterns.levels.size(); j++) {
					Set<Itemset> H1 = new HashSet<>();
					for (Itemset itemsetSize1 : patterns.levels.get(j)) {
						if (itemsetSize1.getItems().containsAll(lk.getItems())) {
							H1.add(itemsetSize1);
						}
					}

					for (Itemset hm_P_1 : H1) {
						Itemset itemset_Lk_minus_hm_P_1 = hm_P_1.cloneItemSetMinusAnItemset(lk);
						int share = 0; // bug fixed
						for (int l = 0; l < hm_P_1.size(); l++) {
							if (lk.contains1(hm_P_1.get(l)) != -1) {
								share += hm_P_1.getItemsUtilities().get(l);
							}
						}

						double conf = ((double) share) / ((double) lk.acutility);
						if (conf >= minconf) {
							Rule rule = new Rule(lk, itemset_Lk_minus_hm_P_1, hm_P_1.acutility, conf, hm_P_1,
									lk.acutility);
							rules.addRule(rule);
							ruleCount++;
						}
					}

				}
			}
		}
		MemoryLogger.getInstance().checkMemory();

		runtime = start = System.currentTimeMillis() - start;
		
		maxMemory =  MemoryLogger.getInstance().getMaxMemory();
		
		System.out.println("Total number of HARs " + ruleCount);
		return rules;
	}
	
	/**
	 * Write the rules to an output file
	 * @param output the output file path
	 * @throws IOException if error while writing to file
	 */
	public void writeRulesToFile(String output) throws IOException{

		// we prepare the object for writing the output file
		BufferedWriter writer = new BufferedWriter(new FileWriter(output));

		// create a string buffer
		StringBuilder buffer = new StringBuilder();
		
		// For each rule level
		for(List<Rule> ruleList : rules.rules) {

			// For each rule
			for(Rule rule : ruleList) {
				// write the left side of the rule (the antecedent)
				Itemset antecedent = rule.getAntecedent();
				for (int i = 0; i < antecedent.size(); i++) {
					buffer.append(antecedent.getItems().get(i));
					if (i != antecedent.size() - 1) {
						buffer.append(" ");
					}
				}

				// write separator
				buffer.append(' ');
				buffer.append("==>");
				buffer.append(' ');

				// write the right side of the rule (the consequent)
				Itemset consequent = rule.getConsequent();
				for (int i = 0; i < consequent.size(); i++) {
					buffer.append(consequent.getItems().get(i));
					if (i != consequent.size() - 1) {
						buffer.append(" ");
					}
				}
//				// write support
//				buffer.append("\t#SUP: ");
//				buffer.append(rule.);
				// write confidence
				buffer.append(" #UTIL: ");   /// UTILITY
				buffer.append(rule.getUtility());

				buffer.append(" #AUTIL: ");  /// ANTECEDENT UTILITY
				buffer.append(rule.getAntecedentUtility());
				
				buffer.append(" #UCONF: ");  /// UTILITY CONFIDENCE
				buffer.append(rule.getConfidence());
				buffer.append(System.lineSeparator());
			}
			
		}

		writer.write(buffer.toString());
		
		// close the file
		writer.close();
	}

	/**
	 * Print statistics about the last algorithm execution
	 */
	public void printStats() {
		System.out.println("=============  HGB-ALL ALGORITHM - STATS =============");
		System.out.println(" Total time ~ " + runtime + " ms");
		System.out.println(" Memory ~ " + maxMemory + " MB");
		System.out.println(" High-utility association rule count : " + ruleCount);
		System.out.println("===================================================");
	}

	
}
