package ca.pfv.spmf.algorithms.associationrules.hgb;
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

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ca.pfv.spmf.tools.MemoryLogger;

/**
 * Algorithm to generate high utility association rules from the paper 
 * "An efficient approach for mining association rules from high utility itemsets" by Sahoo et al. (2015)
 *  @see AlgoFHIM_and_HUCI
 * @author Jayakrushna Sahoo
 */
public class AlgoHGB {
	/**
	 * Table of closed patterns and generator
	 */
	private HUClosedTable closedPatternsAndGenerators;
	/** The rules that are found */
	private Rules rules;
	/** The minimum utility threhsold */
	private int minutility;
	/** the minimum confidence */
	private double uminconf;
	/** The total number of generators  */
	private int genCount;
	/** Runtime of last execution of this algorithm*/
	private long runtime;
	/** Memory usage of last execution */
	private double maxMemory;
	/** Number of rules */
	private int ruleCount;
	

	/** Constructor
	 * 
	 */
	public AlgoHGB() { 
	}

	/** 
	 * The total number of generators 
	 */
	public void totalgen() {
		System.out.println("Total number of generators = " + genCount);
	}

	/**
	 * Run the algorithm
	 * @param closedPatternsAndGenerators a table containing closed itemsets and generators
	 * @param minutility the minimum utility threshold
	 * @param uminconf the minimum confidence threshold
	 * @return
	 */
	public Rules runAlgorithm(HUClosedTable closedPatternsAndGenerators, int minutility, double uminconf) {
		this.uminconf = uminconf;
		this.closedPatternsAndGenerators = closedPatternsAndGenerators;
		rules = new Rules("HGB Basis of association rules");
		ruleCount = 0;
		// this.objectsCount = nbobjects;
		this.minutility = minutility;
		MemoryLogger.getInstance().reset();
		long start = System.currentTimeMillis();
		
		// 3 For each closed frequent itemset t.
		for (List<Itemset> level : closedPatternsAndGenerators.levels) {
			for (Itemset itemset : level) {
				if (closedPatternsAndGenerators.mapGenerators != null) {
					if (!closedPatternsAndGenerators.mapGenerators.get(itemset).isEmpty()) {
						for (Itemset gen : closedPatternsAndGenerators.mapGenerators.get(itemset)) {
							genCount++;
						}
					}
					if (itemset.size() > 1) {
						processItemset(itemset);
					}
				}
			}
		}

		MemoryLogger.getInstance().checkMemory();

		runtime = start = System.currentTimeMillis() - start;
		
		maxMemory =  MemoryLogger.getInstance().getMaxMemory();
		

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
	 * Process an itemset
	 * @param theItemset the itemset
	 */
	private void processItemset(Itemset theItemset) {
		Set<Itemset> lSmallestPremise = new HashSet<>(); // 9
		for (int j = 0; j <= theItemset.size(); j++) {
			for (Itemset i1 : closedPatternsAndGenerators.levels.get(j)) {
				if (theItemset.getItems().containsAll(i1.getItems())) {
					Set<Itemset> Rand = new HashSet<>();
					boolean find = false;
					if (closedPatternsAndGenerators.mapGenerators.get(i1).isEmpty()) {
						int share = 0;
						for (int l = 0; l < theItemset.size(); l++) {
							if (i1.contains(theItemset.get(l))) {
								share += theItemset.getItemsUtilities().get(l);// bug fixed
							}
						}

						if (((double) share / (double) i1.acutility) >= uminconf) {
							boolean thereIsSmaller = false;
							for (Itemset l : lSmallestPremise) {
								if (i1.getItems().containsAll(l.getItems()) && i1.size() != l.size()) {
									thereIsSmaller = true;
									break;
								}
							}
							if (thereIsSmaller == false) {
								lSmallestPremise.add(i1);// 13
							}
						}
					} else {
						for (Itemset genI1 : closedPatternsAndGenerators.mapGenerators.get(i1)) {
							int share = 0;
							for (int l = 0; l < theItemset.size(); l++) {
								if (genI1.contains(theItemset.get(l))) {
									share += theItemset.getItemsUtilities().get(l); // fixed
								}
							}

							if (((double) share / (double) genI1.acutility) >= uminconf) {
								boolean thereIsSmaller = false;
								for (Itemset l : lSmallestPremise) {
									if (genI1.getItems().containsAll(l.getItems()) && genI1.size() != l.size()) {
										thereIsSmaller = true;
										break;
									}
								}
								if (thereIsSmaller == false) {
									lSmallestPremise.add(genI1);// 13
								}
							} else {
								Rand.add(genI1);
								find = true;
							}
						}
					}

					if (find == true) {
						for (Itemset g : Rand) {
							Set<Itemset> H1 = new HashSet<>();
							Itemset lk = theItemset.cloneItemSetMinusAnItemset(g);
							for (int s = 0; s < lk.size(); s++) {
								Integer item = lk.get(s);
								Itemset itemset = new Itemset();
								itemset.addItem(item);
								H1.add(itemset);
							}
							int k = H1.size();
							Set<Itemset> H1_for_recursion = new HashSet<>();
							for (Itemset hm_P_1 : H1) {
								Itemset itemset_Lk_union_hm_P_1 = g.union(hm_P_1);
								int share = 0;
								int uti = shareUtility(itemset_Lk_union_hm_P_1);
								for (int l = 0; l < theItemset.size(); l++) {
									if (itemset_Lk_union_hm_P_1.contains(theItemset.get(l))) {
										share += theItemset.getItemsUtilities().get(l);
									}
								}

								if (uti >= minutility) {
									if ((uti != 0) && (share / uti >= uminconf)) {
										boolean thereIsSmaller = false;
										for (Itemset l : lSmallestPremise) {
											if (itemset_Lk_union_hm_P_1.getItems().containsAll(l.getItems())
													&& itemset_Lk_union_hm_P_1.size() != l.size()) {
												thereIsSmaller = true;
												break;
											}
										}
										if (thereIsSmaller == false) {
											lSmallestPremise.add(itemset_Lk_union_hm_P_1);// 13
										}
									}
								} else {
									H1_for_recursion.add(hm_P_1);// for recursion
								}
							}

							// call apGenRules
							apGenrules(k, 1, lk, H1_for_recursion, lSmallestPremise, g, theItemset);

						}
					}
				}
			}
		}

		for (Itemset gs : lSmallestPremise) {

			Itemset i_gs = new Itemset();
			for (Integer item : theItemset.getItems()) {
				if (gs.contains(item) == false) {
					i_gs.getItems().add(item);
				}
			}
			if (i_gs.getItems().isEmpty() == false) {
				i_gs.sort();
				int share = 0;
				for (int l = 0; l < theItemset.size(); l++) {
					if (gs.contains(theItemset.get(l))) {
						share += theItemset.getItemsUtilities().get(l);
					}
				}
				int uti;
				if (gs.acutility == 0) {
					uti = shareUtility(gs);
				} else {
					uti = gs.acutility;
				}
				// System.out.println(share+"------"+uti);
				Rule rule = new Rule(gs, i_gs, theItemset.acutility, (double) share / uti, theItemset, uti);
				rules.addRule(rule); // 18
				ruleCount++;
			}
		}
	}

	/**
	 * Algorithm to generate association rules
	 * @param k
	 * @param m
	 * @param lk
	 * @param Hm
	 * @param lSmallestPremise
	 * @param g
	 * @param i
	 */
	private void apGenrules(int k, int m, Itemset lk, Set<Itemset> Hm, Set<Itemset> lSmallestPremise, Itemset g,
			Itemset i) {

		if (k > m + 1) {
			Set<Itemset> Hm_plus_1 = generateCandidateSizeK(Hm, lk);
			Set<Itemset> Hm_plus_1_for_recursion = new HashSet<>();
			for (Itemset hm_P_1 : Hm_plus_1) {
				Itemset itemset_Lk_union_hm_P_1 = g.union(hm_P_1);
				int share = 0;
				int uti = shareUtility(itemset_Lk_union_hm_P_1);
				for (int l = 0; l < i.size(); l++) {
					if (itemset_Lk_union_hm_P_1.contains(i.get(l))) {
						share += i.getItemsUtilities().get(l);// bug fixed
					}
				}

				if (uti >= minutility) {
					if ((uti != 0) && (share / uti >= uminconf)) {
						boolean thereIsSmaller = false;
						for (Itemset l : lSmallestPremise) {
							if (itemset_Lk_union_hm_P_1.getItems().containsAll(l.getItems())
									&& itemset_Lk_union_hm_P_1.size() != l.size()) {
								thereIsSmaller = true;
								break;
							}
						}
						if (thereIsSmaller == false) {
							lSmallestPremise.add(itemset_Lk_union_hm_P_1);// 13
						}
//		
					}
				} else {
					Hm_plus_1_for_recursion.add(hm_P_1);// for recursion
				}
			}
			apGenrules(k, m + 1, lk, Hm_plus_1_for_recursion, lSmallestPremise, g, i);
		}
	}

	/**
	 * Share utility 
	 * @param itemsetToTest an itemset
	 * @return an integer
	 */
	private int shareUtility(Itemset itemsetToTest) {
		int share = 0;
		Set<Itemset> smallclosedset = new HashSet<>();
		boolean found = false;
		for (List<Itemset> list : closedPatternsAndGenerators.levels) {
			if (list.isEmpty() || list.get(0).size() < itemsetToTest.size()) {
				continue;
			}
			for (Itemset itemset : list) {
				if (itemsetToTest.includedIn(itemset)) {
					smallclosedset.add(itemset);
					found = true;

				}
			}
			if (found == true)
				break;
		}
		Itemset sc = new Itemset();
		int k = 2000000;
		for (Itemset H : smallclosedset) {
			if (H.support < k) {
				sc = H;
				k = H.support;
			}
		}
		for (int l = 0; l < sc.size(); l++) {
			if (itemsetToTest.contains(sc.get(l))) {
				share += sc.getItemsUtilities().get(l);
			}
		}
		return share;

	}

	/**
	 * Algorithm to generate candidates
	 * @param levelK_1 the level K-1
	 * @param g an itemset
	 * @return a Set of itemsets.
	 */
	protected Set<Itemset> generateCandidateSizeK(Set<Itemset> levelK_1, Itemset g) {
		Set<Itemset> candidates = new HashSet<>();

		// For each itemset I1 and I2 of level k-1
		if (levelK_1.size() > 1) {
			loop1: for (Itemset itemset1 : levelK_1) {
				loop2: for (Itemset itemset2 : levelK_1) {
					for (int k = 0; k < itemset1.size(); k++) {
						// if they are the last items
						if (k == itemset1.size() - 1) {
							// the one from itemset1 should be smaller (lexical order)
							// and different from the one of itemset2
							if (itemset1.getItems().get(k) >= itemset2.get(k)) {
								continue loop1;
							}
						}
						// if they are not the last items, and
						else if (itemset1.getItems().get(k) < itemset2.get(k)) {
							continue loop2; // we continue searching
						} else if (itemset1.getItems().get(k) > itemset2.get(k)) {
							continue loop1; // we stop searching: because of lexical order
						}
					}
					// NOW COMBINE ITEMSET 1 AND ITEMSET 2
					Integer missing = itemset2.get(itemset2.size() - 1);

					if (missing != null) {

						Itemset candidate = new Itemset();
						for (int k = 0; k < itemset1.size() - 1; k++) {
							candidate.addItem(itemset1.get(k));

						}

						candidate.addItem(missing);
						Itemset itemset_Lk_union_hm_P_1 = g.union(candidate);
						int sup = findSupport(itemset_Lk_union_hm_P_1);
						if (sup == g.support) {
							candidate.sort();
							candidates.add(candidate);
						}

					}
				}
			}
		}
		return candidates;

	}

	/**
	 * Find the support of an itemset
	 * @param itemsetT the itemset
	 * @return the support
	 */
	private int findSupport(Itemset itemsetT) {
		Set<Itemset> smallclosedset = new HashSet<>();
		boolean found = false;

		for (List<Itemset> list : closedPatternsAndGenerators.levels) {
			if (list.isEmpty() || list.get(0).size() < itemsetT.size()) {
				continue; // it is not useful to consider itemsets that are smaller
							// than itemsetToTest.size
			}
			for (Itemset itemset : list) {

				if (itemsetT.includedIn(itemset)) {
					smallclosedset.add(itemset);
					found = true;

				}
			}
			if (found == true)
				break;

		}
		int supp = 2000000;
		for (Itemset H : smallclosedset) {
			if (H.support < supp) {

				supp = H.support;
			}
		}

		return supp;
	}

	/**
	 * Print statistics about the last algorithm execution
	 */
	public void printStats() {
		System.out.println("=============  HGB ALGORITHM - STATS =============");
		System.out.println(" Total time ~ " + runtime + " ms");
		System.out.println(" Memory ~ " + maxMemory + " MB");
//		System.out.println(" Candidate count : " + candidate);
		System.out.println(" High-utility association rule count : " + ruleCount);
		System.out.println("===================================================");
	}

}
