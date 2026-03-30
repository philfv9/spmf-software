package ca.pfv.spmf.algorithms.associationrules.TopKRules_and_TNR;

/* This file is copyright (c) 2008-2024 Philippe Fournier-Viger

This file is part of the SPMF DATA MINING SOFTWARE
(http://www.philippe-fournier-viger.com/spmf).
SPMF is free software: you can redistribute it and/or modify it under the
terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later
version.
SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
A PARTICULAR PURPOSE. See the GNU General Public License for more details.
You should have received a copy of the GNU General Public License along with
SPMF. If not, see http://www.gnu.org/licenses/.
*/
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import ca.pfv.spmf.algorithms.ArraysAlgos;
import ca.pfv.spmf.tools.MemoryLogger;

/**
 * FTARM (Fast Top-K Association Rule Miner) is an enhanced version of ETARM
 * for mining the TOP-K association rules from a transaction database using
 * Rule Generation Property Pruning (RGPP).
 * 
 * <p>
 * FTARM adds three novel propositions on top of ETARM:
 * 
 * <p>
 * <b>Proposition 1 (Initialization of minsup):</b> Instead of starting with minsup=1,
 * FTARM analyzes the internal relationships between items to initialize minsup
 * to a higher value based on Property 5 and Property 6, allowing faster pruning
 * of the search space before rule expansions begin.
 * 
 * <p>
 * <b>Proposition 2 (Remove useless items):</b> After initializing minsup, items with
 * support less than minsup are removed from the database before rule expansion,
 * reducing the number of candidate rules generated.
 * 
 * <p>
 * <b>Proposition 3 (Update largest item in real time):</b> When minsup is raised during
 * execution, MaxItem is updated to the largest item satisfying the current minsup
 * threshold, enabling more aggressive pruning during rule expansions.
 * 
 * <p>
 * <b>Key Properties Used:</b>
 * <ul>
 * <li><b>Property 5:</b> Given m items, the total number of rules that can be generated
 * is S = Σ(j=1 to m-1) C(m,j) * (2^(m-j) - 1). This is used to calculate the minimum
 * number of items needed to generate k rules.</li>
 * <li><b>Property 6:</b> For a set of items I, the minimum support Ms of rules generated
 * from I equals |tids(i1 ∩ i2 ∩ ... ∩ im)|, and the minimum confidence Mc equals
 * Ms / Max{|tids(i)|} for all items i in I.</li>
 * </ul>
 * 
 * <p>
 * <b>Algorithm Steps:</b>
 * <ol>
 * <li>Scan database to build vertical representation (tidsets for each item)</li>
 * <li>Initialize minsup using Properties 5 and 6 (Proposition 1)</li>
 * <li>Remove items with support less than minsup (Proposition 2)</li>
 * <li>Generate all rules with one item in antecedent and one in consequent</li>
 * <li>Recursively expand rules using left and right expansions</li>
 * <li>Update MaxItem when minsup is raised (Proposition 3)</li>
 * </ol>
 * 
 * <p>
 * <b>Thread Safety:</b> This class is NOT thread-safe. Each thread should 
 * use its own instance.
 * 
 * <p>
 * <b>Limitations:</b> 
 * <ul>
 * <li>Combinatorial calculations limited to prevent overflow (see MAX_ITEMS_FOR_COMBINATORIAL_CALCULATION)</li>
 * <li>May return fewer than k rules if insufficient rules exist in database</li>
 * </ul>
 * 
 * @author Philippe Fournier-Viger, 2024
 * @see AlgoETARM
 * @see AlgoTopKRules
 */
public class AlgoFTARM extends AlgoETARM {

    /**
     * Maximum number of items to consider for combinatorial rule count calculation.
     * This prevents overflow in binomial coefficient computation.
     * With m=30, the maximum number of rules is approximately 1.07 billion,
     * which is sufficient for most practical applications.
     */
    private static final int MAX_ITEMS_FOR_COMBINATORIAL_CALCULATION = 30;
    
    /**
     * The largest item in the database that satisfies current minsup threshold.
     * This value is updated dynamically when minsup is raised (Proposition 3).
     * Used to prune the search space during left and right expansions.
     */
    private int maxItem;
    
    /**
     * Flag indicating whether there are valid items to mine.
     * Set to false if all items are pruned during initialization.
     */
    private boolean hasValidItems;
    
    
    /**
     * Default constructor for the FTARM algorithm.
     * Initializes the algorithm by calling the parent ETARM constructor
     * and creates a BitSet pool for efficient memory management.
     */
    public AlgoFTARM() {
        super();
    }
    
    /**
     * Run the FTARM algorithm to find the top-k association rules.
     * 
     * <p>
     * This method implements the main FTARM algorithm as described in the paper.
     * It performs the following steps:
     * <ol>
     * <li>Validate input parameters</li>
     * <li>Initialize internal data structures (via parent)</li>
     * <li>Scan database to build vertical representation (via parent)</li>
     * <li>Initialize minsup and remove useless items (Propositions 1 & 2)</li>
     * <li>Generate and expand rules to find top-k (via parent's start())</li>
     * </ol>
     * 
     * @param k the value of k (number of rules to find). Must be positive.
     * @param minConfidence the minimum confidence threshold. Must be between 0 and 1.
     * @param database the transaction database to mine. Cannot be null or empty.
     * @throws IllegalArgumentException if any parameter is invalid
     */
    @Override
    public void runAlgorithm(int k, double minConfidence, Database database) {
        // Validate parameters (FTARM-specific validation)
        validateParameters(k, minConfidence, database);
        
        // Initialize FTARM-specific state
        this.hasValidItems = true;
        
        // Call parent's runAlgorithm which handles all initialization and calls start()
        super.runAlgorithm(k, minConfidence, database);
    }
    
    /**
     * Validate input parameters for the algorithm.
     * 
     * @param k the value of k
     * @param minConfidence the minimum confidence threshold
     * @param database the transaction database
     * @throws IllegalArgumentException if any parameter is invalid
     */
    private void validateParameters(int k, double minConfidence, Database database) {
        if (k <= 0) {
            throw new IllegalArgumentException("k must be positive, got: " + k);
        }
        if (minConfidence < 0.0 || minConfidence > 1.0) {
            throw new IllegalArgumentException("minConfidence must be between 0 and 1, got: " + minConfidence);
        }
        if (database == null || database.getTransactions() == null || database.getTransactions().isEmpty()) {
            throw new IllegalArgumentException("Database cannot be null or empty");
        }
    }

    /**
     * Start the rule generation process for FTARM.
     * 
     * <p>
     * This method overrides ETARM's start() to add FTARM-specific initialization
     * (Propositions 1 & 2) before delegating to the parent's rule generation logic.
     * 
     * <p>
     * Steps:
     * <ol>
     * <li>Initialize minsup and remove useless items (FTARM Propositions 1 & 2)</li>
     * <li>If valid items remain, delegate to parent's start() for rule generation</li>
     * </ol>
     */
    @Override
    protected void start() {
        // FTARM Step 2: Initialize minsup and remove useless items (Propositions 1 & 2)
        initializeMinSupportAndRemoveUselessItems();

        // FTARM Steps 3-5: Start rule generation with optimized search space
        // Only proceed if there are valid items to mine
        if (hasValidItems) {
            super.start();
        }
    }

    /**
     * FTARM: Initialize_Remove procedure (Algorithm 2 in the paper).
     * <p>
     * This procedure implements Propositions 1 and 2 of the FTARM algorithm:
     * <ol>
     * <li><b>Step 1:</b> Calculate minimum number of items m needed to generate k rules
     * using Property 5: S = Σ(j=1 to m-1) C(m,j) * (2^(m-j) - 1)</li>
     * <li><b>Step 2:</b> Sort items by support in descending order</li>
     * <li><b>Step 3:</b> Select top m items with highest support to form set I</li>
     * <li><b>Step 4:</b> Calculate Mc (minimum confidence) of set I using Property 6.
     * If Mc >= minconf, initialize minsup to Ms (minimum support of I)</li>
     * <li><b>Step 5:</b> Remove items with support < minsup and update maxItem</li>
     * </ol>
     * 
     * <p>
     * <b>Property 6 Formulas:</b>
     * <ul>
     * <li>Ms = |tids(i1 ∩ i2 ∩ ... ∩ im)| (intersection of all item tidsets)</li>
     * <li>Mc = Ms / Max{|tids(i)|} for all items i in I</li>
     * </ul>
     */
    private void initializeMinSupportAndRemoveUselessItems() {
        // Step 1: Calculate minimum number of items needed to generate k rules (Property 5)
        int minItemsNeeded = calculateMinItemsForKRules(k);

        // Step 2: Create list of items sorted by support in descending order
        List<ItemSupport> sortedItemList = new ArrayList<ItemSupport>();
        for (int item = 0; item <= database.maxItem; item++) {
            if (tableItemCount[item] > 0) {
                sortedItemList.add(new ItemSupport(item, tableItemCount[item]));
            }
        }

        // Sort by support descending
        Collections.sort(sortedItemList, new Comparator<ItemSupport>() {
            @Override
            public int compare(ItemSupport first, ItemSupport second) {
                return second.support - first.support; // descending order
            }
        });

        // Step 3 & 4: Select top m items and calculate Ms and Mc (Property 6)
        if (sortedItemList.size() >= minItemsNeeded && minItemsNeeded >= 2) {
            // Get top m items
            int actualTopCount = Math.min(minItemsNeeded, sortedItemList.size());
            List<ItemSupport> topItems = sortedItemList.subList(0, actualTopCount);

            // Calculate intersection of all m items for Ms
            // Calculate max support among m items for Mc denominator
            BitSet intersectionTids = pool.acquire();
            int maxSupportAmongTopItems = 0;
            boolean validIntersection = true;
            boolean firstItem = true;

            for (ItemSupport itemSup : topItems) {
                if (tableItemTids[itemSup.item] == null) {
                    validIntersection = false;
                    break;
                }
                if (firstItem) {
                    intersectionTids.or(tableItemTids[itemSup.item]);
                    firstItem = false;
                } else {
                    intersectionTids.and(tableItemTids[itemSup.item]);
                }
                maxSupportAmongTopItems = Math.max(maxSupportAmongTopItems, itemSup.support);
            }

            // Calculate Ms and Mc according to Property 6
            if (validIntersection && maxSupportAmongTopItems > 0) {
                // Ms = |tids(i1 ∩ i2 ∩ ... ∩ im)| (absolute support of intersection)
                int intersectionSupport = intersectionTids.cardinality();

                // Mc = Ms / Max{|tids(i)|} (Property 6 formula for minimum confidence)
                double minConfidenceEstimate = (double) intersectionSupport / maxSupportAmongTopItems;

                // If Mc >= minconf, initialize minsup to Ms
                if (minConfidenceEstimate >= minConfidence && intersectionSupport > 0) {
                    minsuppRelative = intersectionSupport;
                }
            }
            
            // Release temporary BitSet back to pool
            pool.release(intersectionTids);
        }

        // Step 5: Remove items with support < minsup and update maxItem (Propositions 2 & 3)
        maxItem = -1; // Will be updated to the largest valid item

        for (int item = database.maxItem; item >= 0; item--) {
            if (tableItemCount[item] >= minsuppRelative) {
                // This item is valid - keep it
                if (maxItem == -1) {
                    maxItem = item; // First valid item from the end is the largest
                }
            } else {
                // Remove this item (mark as invalid by setting count to 0 and tidset to null)
                tableItemCount[item] = 0;
                tableItemTids[item] = null;
            }
        }

        // If no valid maxItem found, mark that there are no valid items to mine
        if (maxItem == -1) {
            hasValidItems = false;
            maxItem = 0; // Defensive value, but hasValidItems controls flow
        }
    }

    /**
     * Calculate minimum number of items needed to generate k rules using Property 5.
     * 
     * <p>
     * Property 5 states that given m items, the total number of rules that can be
     * generated is: S = Σ(j=1 to m-1) C(m,j) * (2^(m-j) - 1)
     * 
     * <p>
     * This method finds the smallest m such that S >= k.
     * 
     * <p>
     * Examples:
     * <ul>
     * <li>m=2: S = C(2,1) * (2^1 - 1) = 2 * 1 = 2 rules</li>
     * <li>m=3: S = C(3,1)*(2^2-1) + C(3,2)*(2^1-1) = 3*3 + 3*1 = 12 rules</li>
     * <li>m=4: S = C(4,1)*(2^3-1) + C(4,2)*(2^2-1) + C(4,3)*(2^1-1) = 4*7 + 6*3 + 4*1 = 50 rules</li>
     * </ul>
     * 
     * @param targetRuleCount the number of rules desired (k)
     * @return the minimum number of items needed to potentially generate k rules
     */
    private int calculateMinItemsForKRules(int targetRuleCount) {
        // Count actual items in database first
        int totalItemCount = 0;
        for (int item = 0; item <= database.maxItem; item++) {
            if (tableItemCount[item] > 0) {
                totalItemCount++;
            }
        }

        // Edge case: if fewer than 2 items, return what we have
        if (totalItemCount < 2) {
            return totalItemCount;
        }

        // Find minimum m such that calculateTotalRulesFromItems(m) >= targetRuleCount
        // Cap at MAX_ITEMS_FOR_COMBINATORIAL_CALCULATION to prevent overflow
        for (int m = 2; m <= Math.min(totalItemCount, MAX_ITEMS_FOR_COMBINATORIAL_CALCULATION); m++) {
            long totalRules = calculateTotalRulesFromItems(m);
            if (totalRules >= targetRuleCount) {
                return m;
            }
        }

        // Return capped value
        return Math.min(totalItemCount, MAX_ITEMS_FOR_COMBINATORIAL_CALCULATION);
    }

    /**
     * Calculate total number of rules that can be generated from m items using Property 5.
     * 
     * <p>
     * Property 5 formula: S = Σ(j=1 to m-1) C(m,j) * (2^(m-j) - 1)
     * 
     * <p>
     * Where:
     * <ul>
     * <li>j = size of antecedent (from 1 to m-1)</li>
     * <li>C(m,j) = number of ways to choose j items for antecedent</li>
     * <li>2^(m-j) - 1 = number of non-empty subsets of remaining items for consequent</li>
     * </ul>
     * 
     * @param m number of items
     * @return total number of possible rules, or Long.MAX_VALUE if overflow occurs
     */
    private long calculateTotalRulesFromItems(int m) {
        long total = 0;

        for (int j = 1; j < m; j++) {
            long combinations = binomialCoefficient(m, j);
            long consequentCombinations = (1L << (m - j)) - 1; // 2^(m-j) - 1

            if (combinations > 0 && consequentCombinations > 0) {
                // Check for overflow in multiplication
                if (combinations > Long.MAX_VALUE / consequentCombinations) {
                    return Long.MAX_VALUE; // Overflow protection
                }

                long product = combinations * consequentCombinations;

                // Check for overflow in addition
                if (total > Long.MAX_VALUE - product) {
                    return Long.MAX_VALUE;
                }
                total += product;
            }
        }

        return total;
    }

    /**
     * Calculate binomial coefficient C(n, r) = n! / (r! * (n-r)!).
     * 
     * <p>
     * Uses an iterative approach to avoid factorial overflow.
     * The formula is computed as: C(n,r) = (n * (n-1) * ... * (n-r+1)) / (r * (r-1) * ... * 1)
     * 
     * <p>
     * Optimizations applied:
     * <ul>
     * <li>C(n,r) = C(n, n-r) when r > n-r, to minimize iterations</li>
     * <li>Early termination for edge cases (r=0, r=n)</li>
     * <li>Careful overflow detection during computation</li>
     * </ul>
     * 
     * <p>
     * <b>P2 Fix:</b> Improved precision by performing multiplication before division
     * and adding overflow checks at each step.
     * 
     * @param n total number of items
     * @param r number of items to choose
     * @return binomial coefficient C(n,r), or Long.MAX_VALUE if overflow occurs
     */
    private long binomialCoefficient(int n, int r) {
        if (r < 0 || r > n) {
            return 0;
        }
        if (r == 0 || r == n) {
            return 1;
        }
        // Optimization: C(n,r) = C(n, n-r), use smaller r
        if (r > n - r) {
            r = n - r;
        }

        long result = 1;
        for (int i = 0; i < r; i++) {
            // Check for overflow before multiplication
            if (result > Long.MAX_VALUE / (n - i)) {
                return Long.MAX_VALUE;
            }
            
            // Multiply first to maintain precision
            result = result * (n - i);
            
            // Then divide - this is guaranteed to be exact for binomial coefficients
            result = result / (i + 1);
        }

        return result;
    }

    /**
     * FTARM: Override generateRuleSize11 to use dynamic maxItem for expansion flag.
     * 
     * <p>
     * This method creates two rules from items I and J:
     * <ul>
     * <li>Rule I → J with confidence = support(I∪J) / support(I)</li>
     * <li>Rule J → I with confidence = support(I∪J) / support(J)</li>
     * </ul>
     * 
     * <p>
     * The key FTARM enhancement is using the dynamic maxItem (which may be smaller
     * than database.maxItem after Proposition 2 pruning) to determine if left
     * expansion is possible.
     * 
     * @param item1 first item (I)
     * @param tid1 tidset of first item
     * @param item2 second item (J), must be > item1
     * @param tid2 tidset of second item
     * @param commonTids intersection of tid1 and tid2
     * @param cardinality support of {I, J}
     */
    @Override
    protected void generateRuleSize11(Integer item1, BitSet tid1, Integer item2,
            BitSet tid2, BitSet commonTids, int cardinality) {
        
        // Create the rule I ==> J (item1 ==> item2)
        Integer[] itemset1 = new Integer[1];
        itemset1[0] = item1;
        Integer[] itemset2 = new Integer[1];
        itemset2[0] = item2;
        RuleG ruleLR = new RuleG(itemset1, itemset2, cardinality, tid1,
                commonTids, item1, item2);

        // calculate the confidence
        double confidenceIJ = ((double) cardinality) / (tableItemCount[item1]);

        // if rule i->j has minimum confidence, save it
        if (confidenceIJ >= minConfidence) {
            save(ruleLR, cardinality);
        }

        // register the rule as a candidate for future expansion
        if (ruleLR.getItemset1().length < maxAntecedentSize ||
                ruleLR.getItemset2().length < maxConsequentSize) {
            // FTARM: Use dynamic maxItem instead of database.maxItem
            boolean canExpandLeft = (item1 < maxItem) &&
                    (ruleLR.getItemset1().length < maxAntecedentSize);
            registerAsCandidate(canExpandLeft, ruleLR);
        }

        // Create the rule J ==> I (item2 ==> item1)
        RuleG ruleRL = new RuleG(itemset2, itemset1, cardinality, tid2,
                commonTids, item2, item1);

        // calculate the confidence
        double confidenceJI = ((double) cardinality) / (tableItemCount[item2]);

        // if rule J->I has minimum confidence, save it
        if (confidenceJI >= minConfidence) {
            save(ruleRL, cardinality);
        }

        // register the rule as a candidate for future expansion
        if (ruleRL.getItemset1().length < maxAntecedentSize ||
                ruleRL.getItemset2().length < maxConsequentSize) {
            // FTARM: Use dynamic maxItem instead of database.maxItem
            boolean canExpandLeft = (item2 < maxItem) &&
                    (ruleRL.getItemset1().length < maxAntecedentSize);
            registerAsCandidate(canExpandLeft, ruleRL);
        }
    }

    /**
     * FTARM: Enhanced save procedure with MaxItem update (Algorithm 3 in the paper).
     * 
     * <p>
     * This procedure extends the parent's save() to implement Proposition 3:
     * when minsup is raised, MaxItem is updated to the largest item that still
     * satisfies the new minsup threshold.
     * 
     * @param rule the rule to save
     * @param support the absolute support of the rule
     */
    @Override
    protected void save(RuleG rule, int support) {
        int previousMinsup = this.minsuppRelative;

        // Call parent's save which handles adding to kRules and updating minsup
        super.save(rule, support);

        // FTARM Proposition 3: Update MaxItem when minsup is raised
        if (this.minsuppRelative > previousMinsup) {
            updateMaxItemForCurrentMinsup();
        }
    }

    /**
     * FTARM Proposition 3: Update MaxItem when minsup changes.
     * 
     * <p>
     * Scans items from the largest to smallest to find the first item
     * whose support is >= current minsup threshold. This item becomes
     * the new MaxItem.
     * 
     * <p>
     * Rationale: After minsup is raised, some items may no longer be frequent.
     * By updating MaxItem, we can avoid expanding rules with items that cannot
     * lead to frequent rules, thus pruning the search space more aggressively.
     */
    private void updateMaxItemForCurrentMinsup() {
        for (int item = database.maxItem; item >= 0; item--) {
            if (tableItemCount[item] >= minsuppRelative) {
                maxItem = item;
                return;
            }
        }
        // If no item found, keep current maxItem as fallback
    }
    
    /**
     * FTARM: Left expansion procedure using vertical tidset intersections.
     * 
     * <p>
     * Expands a rule by adding items to its antecedent (left side).
     * This method completely overrides the parent's expandL() to use
     * FTARM's dynamic maxItem for more aggressive pruning.
     * 
     * <p>
     * For a rule X → Y, this procedure:
     * <ol>
     * <li>Checks if antecedent size limit is reached - if so, return</li>
     * <li>Checks if maxLeft >= MaxItem - if so, no valid expansion possible (Proposition 3)</li>
     * <li>For each valid candidate item c (c > maxLeft, c <= MaxItem, c not in Y):
     * <ul>
     * <li>Computes tidset intersection using vertical representation</li>
     * <li>Creates new rule (X ∪ {c}) → Y</li>
     * <li>If confidence >= minConfidence, saves to top-k</li>
     * <li>If rule can be expanded further, adds to candidates</li>
     * </ul>
     * </li>
     * </ol>
     * 
     * <p>
     * <b>Key optimization:</b> Uses vertical tidset intersections instead of
     * horizontal transaction scans, and limits iteration to maxItem instead
     * of database.maxItem.
     * 
     * @param rule the rule to expand
     */
    @Override
    protected void expandL(RuleG rule) {
        // Check if antecedent size limit is reached
        if (rule.getItemset1().length >= maxAntecedentSize) {
            return;
        }

        // FTARM Proposition 3: Check against dynamic maxItem
        if (rule.maxLeft >= maxItem) {
            return;
        }

        // FTARM optimization: iterate only up to maxItem, not database.maxItem
        // This is the key performance difference from parent's expandL
        for (int candidateItem = rule.maxLeft + 1; candidateItem <= maxItem; candidateItem++) {
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

            // Calculate confidence of expanded rule
            double confidence = ((double) expandedSupport) / newAntecedentSupport;

            // Create the expanded rule
            RuleG expandedRule = new RuleG(newAntecedent, rule.getItemset2(),
                    expandedSupport, newAntecedentTids, expandedRuleTids,
                    candidateItem, rule.maxRight);

            // Save if confidence threshold is met
            if (confidence >= minConfidence) {
                save(expandedRule, expandedSupport);
            }

            // Register for further expansion if size limits not reached
            boolean canExpandMore = (expandedRule.getItemset1().length < maxAntecedentSize) ||
                    (expandedRule.getItemset2().length < maxConsequentSize);
            if (canExpandMore) {
                boolean canExpandLeft = (candidateItem < maxItem) &&
                        (expandedRule.getItemset1().length < maxAntecedentSize);
                registerAsCandidate(canExpandLeft, expandedRule);
            }
        }
    }

    /**
     * FTARM: Right expansion procedure using vertical tidset intersections.
     * 
     * <p>
     * Expands a rule by adding items to its consequent (right side).
     * This method completely overrides the parent's expandR() to use
     * FTARM's dynamic maxItem for more aggressive pruning.
     * 
     * <p>
     * For a rule X → Y, this procedure:
     * <ol>
     * <li>Checks if consequent size limit is reached - if so, return</li>
     * <li>Checks if current rule's confidence < minConfidence - if so, return</li>
     * <li>Checks if maxRight >= MaxItem - if so, no valid expansion possible (Proposition 3)</li>
     * <li>For each valid candidate item c (c > maxRight, c <= MaxItem, c not in X):
     * <ul>
     * <li>Computes tidset intersection using vertical representation</li>
     * <li>Creates new rule X → (Y ∪ {c})</li>
     * <li>If confidence >= minConfidence, saves to top-k and may add to candidates</li>
     * </ul>
     * </li>
     * </ol>
     * 
     * <p>
     * <b>Key optimization:</b> Uses vertical tidset intersections and limits 
     * iteration to maxItem instead of database.maxItem.
     * 
     * <p>
     * <b>Confidence anti-monotonicity:</b> Adding items to the consequent
     * can only decrease or maintain confidence, so we skip expansion if
     * current confidence is already below threshold.
     * 
     * @param rule the rule to expand
     */
    @Override
    protected void expandR(RuleG rule) {
        // Check if consequent size limit is reached
        if (rule.getItemset2().length >= maxConsequentSize) {
            return;
        }

        // Get antecedent support for confidence calculation
        int antecedentSupport = rule.tids1.cardinality();
        if (antecedentSupport == 0) {
            return;
        }

        // ETARM Proposition 2: Don't expand if current confidence < minconf
        double currentConfidence = ((double) rule.getAbsoluteSupport()) / antecedentSupport;
        if (currentConfidence < minConfidence) {
            return;
        }

        // FTARM Proposition 3: Check against dynamic maxItem
        if (rule.maxRight >= maxItem) {
            return;
        }

        // FTARM optimization: iterate only up to maxItem, not database.maxItem
        for (int candidateItem = rule.maxRight + 1; candidateItem <= maxItem; candidateItem++) {
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

            // Calculate confidence of expanded rule
            double confidence = ((double) expandedSupport) / antecedentSupport;

            // Create the expanded rule
            RuleG expandedRule = new RuleG(rule.getItemset1(), newConsequent,
                    expandedSupport, rule.tids1, expandedRuleTids,
                    rule.maxLeft, candidateItem);

            // Only save and register if rule meets confidence threshold
            if (confidence >= minConfidence) {
                save(expandedRule, expandedSupport);

                // Register for right expansion only if size limit not reached
                // and there's room to expand (candidateItem < maxItem)
                if (expandedRule.getItemset2().length < maxConsequentSize &&
                        candidateItem < maxItem) {
                    registerAsCandidate(false, expandedRule);
                }
            }
        }
    }

    /**
     * Print statistics about the last algorithm execution.
     * 
     * <p>
     * Displays:
     * <ul>
     * <li>Final minsup threshold reached</li>
     * <li>Number of top-k rules found</li>
     * <li>Maximum memory usage</li>
     * <li>Total execution time</li>
     * <li>Maximum number of candidates in queue at any point</li>
     * <li>Final MaxItem value (for debugging)</li>
     * </ul>
     */
    @Override
    public void printStats() {
        System.out.println("============= FTARM v.2.65 - STATS =============");
        System.out.println("Minsup : " + minsuppRelative);
        System.out.println("Rules count: " + kRules.size());
        System.out.println("Memory : " + MemoryLogger.getInstance().getMaxMemory() + " mb");
        System.out.println("Total time : " + (timeEnd - timeStart) + " ms");
        System.out.println("Max candidates: " + maxCandidateCount);
        System.out.println("MaxItem: " + maxItem);
        System.out.println("===================================================");
    }

    /**
     * Helper class to store an item and its support for sorting.
     * 
     * <p>
     * Used in {@link #initializeMinSupportAndRemoveUselessItems()} to sort items
     * by support in descending order when selecting top m items for Property 6 calculation.
     */
    private static class ItemSupport {
        /** The item identifier */
        final int item;
        /** The absolute support (count) of the item */
        final int support;

        /**
         * Constructor for ItemSupport.
         * 
         * @param item the item identifier
         * @param support the absolute support count of the item
         */
        ItemSupport(int item, int support) {
            this.item = item;
            this.support = support;
        }
    }
}