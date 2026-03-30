package ca.pfv.spmf.algorithms.associationrules.TopKRules_and_TNR;

/* This file is copyright (c) 2008-2012 Philippe Fournier-Viger
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
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.PriorityQueue;

import ca.pfv.spmf.datastructures.bitsetpool.BitSetPool;
import ca.pfv.spmf.tools.MemoryLogger;

/**
 * TopKClassRules is an algorithm for mining the TOP-K association rules from a 
 * transaction database using a pattern growth approach and several optimizations. 
 * This is a modification of the original TopKRules algorithm that is designed for 
 * mining rules with fixed items as consequent (what is called class association rules). 
 * The TopKRule algorithm was proposed in this paper:
 * <br/><br/>
 * 
 * Fournier-Viger, P., Wu, C.-W., Tseng, V. S. (2012). Mining Top-K Association Rules. 
 * Proceedings of the 25th Canadian Conf. on Artificial Intelligence (AI 2012), 
 * Springer, LNAI 7310, pp. 61-73.
 * 
 * @author Philippe Fournier-Viger, 2012
 * @see AlgoTopKRules
 */
public class AlgoTopKClassRules {
	
	/** start time of latest execution */
	private long timeStart = 0;  
	
	/** end time of latest execution */
	private long timeEnd = 0;  
	
	/** minimum confidence */
	private double minConfidence;  
	
	/** parameter k */
	private int k = 0;          
	
	/** a transaction database */
	private Database database;   

	/** minimum support that will be raised during the search */
	private int minsuppRelative;
	
	/** a vertical representation of the database 
	 * [item], IDs of transaction containing the item */
	private BitSet[] tableItemTids;  
	
	/** a table indicating the support of each item
	 * [item], support
	 */
	private int[] tableItemCount; 
	
	/** the top k rules found until now */
	private PriorityQueue<ClassRuleG> kRules; 
	
	/** the candidates for expansion */
	private PriorityQueue<ClassRuleG> candidates; 

	/** the maximum number of candidates at the same time during the last execution */
	private int maxCandidateCount = 0;
	
	/** the maximum size of the antecedent of rules (optional) */
	private int maxAntecedentSize = Integer.MAX_VALUE;
	
	/** the list of items that can be used as consequent for all rules */
	private int[] itemToBeUsedAsConsequent;
	 
	/**
	 * An optional parameter to set a maximum value for the rules to be found, as a
	 * relative value (number of transactions)
	 */
	private int maxSupportRelative = Integer.MAX_VALUE;

	/**
	 * An optional parameter to set a maximum value for the rules to be found, as a
	 * percentage
	 */
	private double maxSupport = Double.MAX_VALUE;
	
	/**
	 * Pool of reusable BitSets to minimize allocation overhead.
	 */
	private BitSetPool pool;

	/**
	 * Default constructor
	 */
	public AlgoTopKClassRules() {
		// Initialize BitSet pool with 64 BitSets for efficient memory management
		this.pool = new BitSetPool(64);
	}

	/**
	 * Run the algorithm.
	 * @param k the value of k.
	 * @param minConfidence the minimum confidence threshold.
	 * @param database the database.
	 * @param itemToBeUsedAsConsequent items that must be the consequent of all rules
	 */
	public void runAlgorithm(int k, double minConfidence, Database database, int[] itemToBeUsedAsConsequent) {
		// reset statistics
		MemoryLogger.getInstance().reset();
		maxCandidateCount = 0;
		
		// Reset BitSet pool for this run
		pool.reset();
		
		// save parameters
		this.minConfidence = minConfidence;
		this.database = database;
		this.k = k;
		this.itemToBeUsedAsConsequent = itemToBeUsedAsConsequent;
		
		// calculate the max support if the user decides to use this optional parameter
		// by multiplying by the database size
		maxSupportRelative = (int) Math.ceil(maxSupport * database.getTransactions().size());

		// prepare internal variables and structures
		this.minsuppRelative = 1;
		tableItemTids = new BitSet[database.maxItem + 1];
		tableItemCount = new int[database.maxItem + 1];
		kRules = new PriorityQueue<ClassRuleG>();
		candidates = new PriorityQueue<ClassRuleG>(new Comparator<ClassRuleG>() {
			@Override
			public int compare(ClassRuleG o1, ClassRuleG o2) {
				return -(o1.compareTo(o2));
			}
		});

		// record the start time
		timeStart = System.currentTimeMillis(); 
		
		if (maxAntecedentSize >= 1) {
			// perform the first database scan to generate vertical database representation
			scanDatabase(database);
			 
			// start the generation of rules
			start();
		}
		
		// record the end time
		timeEnd = System.currentTimeMillis(); 
	}

	/**
	 * Start the rule generation.
	 */
	private void start() {
		// Generate rules with one item in the antecedent and one item in the consequent
		
		// for each item I in the database
		for (int itemI = 0; itemI <= database.maxItem; itemI++) {
			// if the item is not frequent according to the current minsup threshold, skip it
			if (tableItemCount[itemI] < minsuppRelative) {
				continue;
			}
			// Get the bitset corresponding to item I
			BitSet tidsI = tableItemTids[itemI];

			// for each item J that can be used as consequent
			for (int itemJ : itemToBeUsedAsConsequent) {
				// skip if same item
				if (itemI == itemJ) {
					continue;
				}
				// if the item is not frequent according to the current minsup threshold, skip it
				if (tableItemCount[itemJ] < minsuppRelative) {
					continue;
				}
				// Get the bitset corresponding to item J
				BitSet tidsJ = tableItemTids[itemJ];

				// Calculate the transaction IDs shared by I and J using bitset AND
				BitSet commonTids = cloneBitSet(tidsI);
				commonTids.and(tidsJ);
				int support = commonTids.cardinality();
				
				// If the rule I ==> J has enough support
				if (support >= minsuppRelative) {
					// generate rule I ==> J and remember it for future possible expansions
					generateRuleSize11(itemI, tidsI, itemJ, tidsJ, commonTids, support);
				}
			}
		}
	
		// Recursively expand rules in the candidates set to find more rules
		while (candidates.size() > 0) {
			// Take the rule with the highest support first
			ClassRuleG rule = candidates.poll();
			
			// if there are no more candidates with enough support, stop
			if (rule.getAbsoluteSupport() < minsuppRelative) {
				break;
			}
			
			// Try to expand the rule by left expansion
			expandL(rule);
		}
	}

	/**
	 * This method tests the rule I ==> J for its confidence
	 * and records it for future expansions.
	 * 
	 * @param item1 an item I
	 * @param tid1 the set of IDs of transactions containing item I (BitSet)
	 * @param item2 an item J (the consequent)
	 * @param tid2 the set of IDs of transactions containing item J (BitSet)
	 * @param commonTids the set of IDs of transactions containing I and J (BitSet)
	 * @param cardinality the cardinality of "commonTids"
	 */
	private void generateRuleSize11(Integer item1, BitSet tid1, int item2,
			BitSet tid2, BitSet commonTids, int cardinality) {
		// Create the rule I ==> J
		Integer[] itemset1 = new Integer[] { item1 };
		ClassRuleG ruleLR = new ClassRuleG(itemset1, item2, cardinality, tid1,
				commonTids, item1); 
		
		// calculate the confidence
		double confidenceIJ = ((double) cardinality) / tableItemCount[item1];
		
		// if rule i->j has minimum confidence
		if (confidenceIJ >= minConfidence) {
			save(ruleLR, cardinality);
		}
		
		// register the rule as a candidate for future expansion
		if (ruleLR.getItemset1().length < maxAntecedentSize) {
			registerAsCandidate(ruleLR);
		}
	}

	/**
	 * Register a given rule in the set of candidates for future expansions.
	 * 
	 * @param rule the given rule
	 */
	private void registerAsCandidate(ClassRuleG rule) {
		candidates.add(rule);

		// record the maximum number of candidates for statistics
		if (candidates.size() > maxCandidateCount) {
			maxCandidateCount = candidates.size();
		}
		// check the memory usage
		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * Try to expand a rule by left expansion (adding items to the antecedent).
	 * Uses vertical tidset intersections for efficient computation.
	 * 
	 * @param rule the rule to expand
	 */
	private void expandL(ClassRuleG rule) {
		// Check if antecedent size limit is reached
		if (rule.getItemset1().length >= maxAntecedentSize) {
			return;
		}

		// Get the consequent item for comparison
		int consequentItem = rule.getItemset2();

		// Iterate over candidate items using vertical representation
		// Only consider items larger than maxLeft to maintain lexicographical order
		for (int candidateItem = rule.maxLeft + 1; candidateItem <= database.maxItem; candidateItem++) {
			// Skip the consequent item
			if (candidateItem == consequentItem) {
				continue;
			}
			
			// Skip items that don't meet current minsup
			if (tableItemCount[candidateItem] < minsuppRelative) {
				continue;
			}

			BitSet candidateTids = tableItemTids[candidateItem];
			if (candidateTids == null) {
				continue;
			}

			// Compute tidset of expanded rule using vertical intersection
			// tids(X ∪ {candidate} → Y) = tids(X → Y) ∩ tids(candidate)
			BitSet expandedRuleTids = cloneBitSet(rule.common);
			expandedRuleTids.and(candidateTids);
			int expandedSupport = expandedRuleTids.cardinality();

			// Only consider if expanded rule meets minsup
			if (expandedSupport < minsuppRelative) {
				continue;
			}

			// Calculate tidset for new antecedent (X ∪ {candidate})
			BitSet newAntecedentTids = cloneBitSet(rule.tids1);
			newAntecedentTids.and(candidateTids);
			int newAntecedentSupport = newAntecedentTids.cardinality();

			if (newAntecedentSupport == 0) {
				continue;
			}

			// Create new antecedent by adding candidate item
			Integer[] newAntecedent = new Integer[rule.getItemset1().length + 1];
			System.arraycopy(rule.getItemset1(), 0, newAntecedent, 0, rule.getItemset1().length);
			newAntecedent[rule.getItemset1().length] = candidateItem;

			// New maxLeft is the candidate item (since candidateItem > rule.maxLeft)
			int newMaxLeft = candidateItem;

			// Calculate confidence of expanded rule
			double confidence = ((double) expandedSupport) / newAntecedentSupport;

			// Create the expanded rule
			ClassRuleG expandedRule = new ClassRuleG(newAntecedent, rule.getItemset2(),
					expandedSupport, newAntecedentTids, expandedRuleTids, newMaxLeft);

			// Save if confidence threshold is met
			if (confidence >= minConfidence) {
				save(expandedRule, expandedSupport);
			}

			// Register for further expansion if size limit not reached
			if (expandedRule.getItemset1().length < maxAntecedentSize) {
				registerAsCandidate(expandedRule);
			}
		}
	}
	
	/**
	 * Save a rule to the current set of top-k rules.
	 * 
	 * @param rule the rule to be saved
	 * @param support the support of the rule
	 */
	private void save(ClassRuleG rule, int support) {
		// If the user is using the maximum threshold to find rare rules, we should
		// not save results having a too high support (above maxSup)
		if (support > maxSupportRelative) {
			return;
		}
		
		// Add the rule to the set of top-k rules
		kRules.add(rule);
		
		// if the size becomes larger than k
		if (kRules.size() > k) {
			// if the support of the rule that we added is higher than
			// the minimum support, we need to remove lower-support rules
			if (support > this.minsuppRelative) {
				// remove rules with lowest support until only k rules are left
				do {
					kRules.poll();
				} while (kRules.size() > k);
			}
			// raise the minimum support to the lowest support in the set of top-k rules
			this.minsuppRelative = kRules.peek().getAbsoluteSupport();
		}
	}

	/**
	 * Method to scan the database to create the vertical database.
	 * 
	 * @param database a database of type Database
	 */
	private void scanDatabase(Database database) {
		// for each transaction
		for (int j = 0; j < database.getTransactions().size(); j++) {
			Transaction transaction = database.getTransactions().get(j);
			// for each item in the current transaction
			for (Integer item : transaction.getItems()) {
				// update the tidset of this item
				if (tableItemTids[item] == null) {
					tableItemTids[item] = pool.createDedicated();
				}
				tableItemTids[item].set(j);
				// update the support of this item
				tableItemCount[item]++;
			}
		}
	}
	
	/**
	 * Clone a BitSet using a dedicated instance from the pool.
	 * This creates a long-lived BitSet suitable for storage in data structures.
	 * 
	 * @param original the BitSet to clone
	 * @return a new dedicated BitSet with the same bits set as the original
	 */
	private BitSet cloneBitSet(BitSet original) {
		BitSet copy = pool.createDedicated();
		copy.or(original);
		return copy;
	}
	
	/**
	 * Print statistics about the last algorithm execution.
	 */
	public void printStats() {
		System.out.println("=============  TOP-K CLASS RULES SPMF v.2.28 - STATS =============");
		System.out.println("Minsup : " + minsuppRelative);
		// if the user has been using the maxsupport optional parameter:
		if (maxSupportRelative < Integer.MAX_VALUE) {
			System.out.println("Maxsup : " + maxSupportRelative);
		}
		System.out.println("Rules count: " + kRules.size());
		System.out.println("Memory : " + MemoryLogger.getInstance().getMaxMemory() + " mb");
		System.out.println("Total time : " + (timeEnd - timeStart) + " ms");
		System.out.println("===================================================");
	}
	
	/**
	 * Write the rules found to an output file.
	 * 
	 * @param path the path to the output file
	 * @throws IOException exception if an error while writing the file
	 */
	public void writeResultTofile(String path) throws IOException {
		BufferedWriter writer = new BufferedWriter(new FileWriter(path));
		
		if (kRules.size() > 0) {
			// sort the rules before printing
			Object[] rules = kRules.toArray();
			Arrays.sort(rules);  
			
			// for each rule
			for (Object ruleObj : rules) {
				ClassRuleG rule = (ClassRuleG) ruleObj;
				
				StringBuilder buffer = new StringBuilder();
				buffer.append(rule.toString());
				buffer.append(" #SUP: ");
				buffer.append(rule.getAbsoluteSupport());
				buffer.append(" #CONF: ");
				buffer.append(rule.getConfidence());
				writer.write(buffer.toString());
				writer.newLine();
			}
		}
		writer.close();
	}
	
	/**
	 * Set the number of items that a rule antecedent should contain (optional).
	 * 
	 * @param maxAntecedentSize the maximum number of items
	 */
	public void setMaxAntecedentSize(int maxAntecedentSize) {
		this.maxAntecedentSize = maxAntecedentSize;
	}
	
	/**
	 * Set a maximum support for the rules to be found (to find rare rules).
	 * 
	 * @param maxSupport the maximum support as a percentage (0 to 1)
	 */
	public void setMaxSupport(double maxSupport) {
		this.maxSupport = maxSupport;
	}
}