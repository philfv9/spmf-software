package ca.pfv.spmf.algorithms.associationrules.TopKRules_and_TNR;

import java.util.BitSet;

import ca.pfv.spmf.algorithms.ArraysAlgos;
import ca.pfv.spmf.tools.MemoryLogger;

/**
 * ETARM (Efficient Top-k Association Rule Miner) is an enhanced version of
 * TopKRules for mining the TOP-K association rules from a transaction database
 * using a pattern growth approach and several optimizations.
 * 
 * ETARM brings two propositions:
 * - Proposition 1: If the largest item in the antecedent (consequent) of a rule 
 *   according to the lexicographical order is also the largest item in the database, 
 *   the rule antecedent (consequent) should not be expanded.
 * - Proposition 2: If the confidence of a rule is less than minconf, it should not 
 *   be expanded by right expansion, as the resulting rule will not be a top-k rule.
 * 
 * @author Philippe Fournier-Viger, 2012
 * @see AlgoTopKRules
 * @see AlgoFTARM
 */
public class AlgoETARM extends AlgoTopKRules {

	/**
	 * Default constructor
	 */
	public AlgoETARM() {
		super(); // Initialize parent class (including BitSetPool)
	}

	/**
	 * This method test the rules I ==> J and J ==> I for their confidence and
	 * record them for future expansions. ETARM: Implements Step 1.3 - checks if
	 * largest item in left side equals max item in database to set expansion flag.
	 * 
	 * @param item1       an item I
	 * @param tid1        the set of IDs of transaction containing item I (BitSet)
	 * @param item2       an item J
	 * @param tid2        the set of IDs of transaction containing item J (BitSet)
	 * @param commonTids  the set of IDs of transaction containing I and J (BitSet)
	 * @param cardinality the cardinality of "commonTids"
	 */
	@Override
	protected void generateRuleSize11(Integer item1, BitSet tid1, Integer item2, BitSet tid2, BitSet commonTids,
			int cardinality) {
		// Create the rule I ==> J (item1 ==> item2)
		Integer[] itemset1 = new Integer[1];
		itemset1[0] = item1;
		Integer[] itemset2 = new Integer[1];
		itemset2[0] = item2;
		RuleG ruleLR = new RuleG(itemset1, itemset2, cardinality, tid1, commonTids, item1, item2);

		// calculate the confidence
		double confidenceIJ = ((double) cardinality) / (tableItemCount[item1]);

		// ETARM Step 1.1: if rule i->j has minimum confidence, save it
		if (confidenceIJ >= minConfidence) {
			// save the rule in current top-k rules
			save(ruleLR, cardinality);
		}

		// register the rule as a candidate for future expansion
		if (ruleLR.getItemset1().length < maxAntecedentSize || ruleLR.getItemset2().length < maxConsequentSize) {
			// ETARM Step 1.3 / Proposition 1: If the largest item in the left hand side
			// according to the lexicographical order is equal to the largest
			// item in the database, an expansion flag for the rule is set to false
			// (indicating that the rule should only be expanded by right expansions).
			// Otherwise, the expansion flag is set to true (indicating that both sides can
			// be expanded).
			boolean canExpandLeft = (item1 < database.maxItem);
			registerAsCandidate(canExpandLeft, ruleLR);
		}

		// Create the rule J ==> I (item2 ==> item1)
		RuleG ruleRL = new RuleG(itemset2, itemset1, cardinality, tid2, commonTids, item2, item1);

		// calculate the confidence
		double confidenceJI = ((double) cardinality) / (tableItemCount[item2]);

		// ETARM Step 1.2: if rule J->I has minimum confidence, save it
		if (confidenceJI >= minConfidence) {
			// save the rule in current top-k rules
			save(ruleRL, cardinality);
		}

		// register the rule as a candidate for future expansion
		if (ruleRL.getItemset1().length < maxAntecedentSize || ruleRL.getItemset2().length < maxConsequentSize) {
			// ETARM Step 1.3 / Proposition 1: Check if largest item in left side equals max
			// item in database
			boolean canExpandLeft = (item2 < database.maxItem);
			registerAsCandidate(canExpandLeft, ruleRL);
		}
	}

	/**
	 * ETARM: EXPANDL procedure - Try to expand a rule by left expansion only.
	 * Implements Proposition 1 for left expansion.
	 * 
	 * <p>
	 * Uses vertical tidset intersections for efficient computation instead of
	 * horizontal transaction scanning.
	 * 
	 * @param rule the rule to expand
	 */
	protected void expandL(RuleG rule) {
		// Check if antecedent size limit is reached
		if (rule.getItemset1().length >= maxAntecedentSize) {
			return;
		}

		// ETARM Proposition 1: If maxLeft is the largest item in database, don't expand left
		if (rule.maxLeft >= database.maxItem) {
			return;
		}

		// Iterate over candidate items using vertical representation
		// Only consider items larger than maxLeft to maintain lexicographical order
		for (int candidateItem = rule.maxLeft + 1; candidateItem <= database.maxItem; candidateItem++) {
			// Skip items that don't meet current minsup
			if (tableItemCount[candidateItem] < minsuppRelative) {
				continue;
			}

			BitSet candidateTids = tableItemTids[candidateItem];
			if (candidateTids == null) {
				continue;
			}

			// Skip items that are already in the consequent
			if (ArraysAlgos.containsLEX(rule.getItemset2(), candidateItem, rule.maxRight)) {
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
			RuleG expandedRule = new RuleG(newAntecedent, rule.getItemset2(),
					expandedSupport, newAntecedentTids, expandedRuleTids,
					newMaxLeft, rule.maxRight);

			// Save if confidence threshold is met
			if (confidence >= minConfidence) {
				save(expandedRule, expandedSupport);
			}

			// Register for further expansion if size limits not reached
			if (expandedRule.getItemset1().length < maxAntecedentSize
					|| expandedRule.getItemset2().length < maxConsequentSize) {
				// ETARM Proposition 1: Check if can still expand left
				boolean canExpandLeft = (newMaxLeft < database.maxItem);
				registerAsCandidate(canExpandLeft, expandedRule);
			}
		}
	}

	/**
	 * ETARM: EXPANDR procedure - Try to expand a rule by right expansion only.
	 * Implements Proposition 1 and Proposition 2.
	 * 
	 * <p>
	 * Uses vertical tidset intersections for efficient computation instead of
	 * horizontal transaction scanning.
	 * 
	 * @param rule the rule to expand
	 */
	@Override
	protected void expandR(RuleG rule) {
		// Check if consequent size limit is reached
		if (rule.getItemset2().length >= maxConsequentSize) {
			return;
		}

		// ETARM Proposition 1: If the largest item in the consequent equals
		// the largest item in the database, the rule consequent should not be expanded.
		if (rule.maxRight >= database.maxItem) {
			return;
		}

		// Get antecedent support for confidence calculation
		int antecedentSupport = rule.tids1.cardinality();
		if (antecedentSupport == 0) {
			return;
		}

		// Iterate over candidate items using vertical representation
		// Only consider items larger than maxRight to maintain lexicographical order
		for (int candidateItem = rule.maxRight + 1; candidateItem <= database.maxItem; candidateItem++) {
			// Skip items that don't meet current minsup
			if (tableItemCount[candidateItem] < minsuppRelative) {
				continue;
			}

			BitSet candidateTids = tableItemTids[candidateItem];
			if (candidateTids == null) {
				continue;
			}

			// Skip items that are already in the antecedent
			if (ArraysAlgos.containsLEX(rule.getItemset1(), candidateItem, rule.maxLeft)) {
				continue;
			}

			// Compute tidset of expanded rule using vertical intersection
			// tids(X → Y ∪ {candidate}) = tids(X → Y) ∩ tids(candidate)
			BitSet expandedRuleTids = cloneBitSet(rule.common);
			expandedRuleTids.and(candidateTids);
			int expandedSupport = expandedRuleTids.cardinality();

			// Only consider if expanded rule meets minsup
			if (expandedSupport < minsuppRelative) {
				continue;
			}

			// Create new consequent by adding candidate item
			Integer[] newConsequent = new Integer[rule.getItemset2().length + 1];
			System.arraycopy(rule.getItemset2(), 0, newConsequent, 0, rule.getItemset2().length);
			newConsequent[rule.getItemset2().length] = candidateItem;

			// New maxRight is the candidate item (since candidateItem > rule.maxRight)
			int newMaxRight = candidateItem;

			// Calculate confidence of expanded rule
			double confidence = ((double) expandedSupport) / antecedentSupport;

			// Create the expanded rule
			RuleG expandedRule = new RuleG(rule.getItemset1(), newConsequent,
					expandedSupport, rule.tids1, expandedRuleTids,
					rule.maxLeft, newMaxRight);

			// ETARM Proposition 2: If the confidence of a rule is less than minconf,
			// it should not be expanded by right expansion, as the resulting
			// rule will not be a top-k rule (confidence can only decrease when
			// adding items to consequent)
			if (confidence >= minConfidence) {
				save(expandedRule, expandedSupport);

				// Only register for right expansion if consequent size limit not reached
				if (expandedRule.getItemset2().length < maxConsequentSize) {
					// expandLR = false because right-expanded rules only expand right
					registerAsCandidate(false, expandedRule);
				}
			}
		}
	}

	/**
	 * Print statistics about the last algorithm execution.
	 */
	@Override
	public void printStats() {
		System.out.println("============= ETARM SPMF v.2.65 - STATS =============");
		System.out.println("Minsup : " + minsuppRelative);
		System.out.println("Rules count: " + kRules.size());
		System.out.println("Memory : " + MemoryLogger.getInstance().getMaxMemory() + " mb");
		System.out.println("Total time : " + (timeEnd - timeStart) + " ms");
		System.out.println("===================================================");
	}
}