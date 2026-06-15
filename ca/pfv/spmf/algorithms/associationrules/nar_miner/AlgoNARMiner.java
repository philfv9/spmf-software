package ca.pfv.spmf.algorithms.associationrules.nar_miner;

/* This file is copyright (c) Philippe Fournier-Viger
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
* License and copyright information should always be preserved.
*/

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import ca.pfv.spmf.algorithms.frequentpatterns.apriori_fast.AlgoAprioriFAST;
import ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemset;
import ca.pfv.spmf.tools.MemoryLogger;

/**
 * Implementation of the NAR-Miner algorithm for mining Negative Association
 * Rules (NARs) from a transaction database.
 * <p>
 * The algorithm operates in two phases:<br/>
 * (1) Mine frequent itemsets (FIs) and infrequent itemsets of interest (IIs)
 * using two thresholds: mfs (minimum frequency support) and mis (maximum
 * infrequency support). An itemset is a FI if support >= mfs, and an II if 0
 * &lt; support &le; mis. The constraint mis &lt; mfs must hold so that the two
 * regions are strictly disjoint.<br/>
 * (2) Generate negative rules A ==> NOT B from each II, where A is a frequent
 * proper subset of the II with minimum support. When several subsets tie on
 * minimum support, each produces an independent rule.
 * <p>
 * Output rules are written in SPMF format, sorted by interestingness
 * descending: {@code ant ==> NOT con #SUP: X #CONF: Y #INTER: Z}
 *
 * @see AlgoAprioriFAST
 * @author Philippe Fournier-Viger
 */
public class AlgoNARMiner {

	// ==================== STATISTICS ====================
	/** Maximum itemset size level reached during mining */
	protected int k;

	/** Total candidates generated across all levels */
	protected int totalCandidateCount = 0;

	/** Start time in milliseconds */
	protected long startTimestamp;

	/** End time in milliseconds */
	protected long endTimestamp;

	/** Number of frequent itemsets found */
	private int frequentItemsetCount;

	/** Number of infrequent itemsets of interest found */
	private int infrequentItemsetCount;

	/** Number of negative rules written to output */
	private int ruleCount;

	// ================= PARAMETERS =======================
	/**
	 * Absolute minimum frequency support (itemsets with support >= this are FIs)
	 */
	private int mfsRelative;

	/**
	 * Absolute maximum infrequency support (itemsets with 0 < support <= this are
	 * IIs)
	 */
	private int misRelative;

	/** Minimum confidence threshold for rule output */
	private double minConf;

	// ==================== DATABASE =======================
	/** Merged transaction database using internal item IDs (each row sorted) */
	private List<int[]> database = null;

	/** Multiplicity of each merged transaction, parallel to database */
	private int[] transactionWeights = null;

	/** Number of transactions in the original database */
	private int databaseSize = 0;

	/** Number of distinct frequent single items */
	private int frequentItemCount;

	// =================== ITEM RENAMING =================
	/** Maps internal item ID to original item ID */
	private int[] internalToOriginal;

	/** Maps original item ID to internal item ID (frequent items only) */
	private Map<Integer, Integer> originalToInternal;

	/**
	 * Absolute support of each frequent single item indexed by internal ID.
	 * supportOfItem[i] = support of internal item i.
	 */
	private int[] supportOfItem;

	// ======================== ITEMSETS =======================

	/**
	 * Map from frequent itemset to its support Key: IntArrayKey wrapping the sorted
	 * iitem array of the itemset Value: absolute support count.
	 */
	private Map<IntArrayKey, Integer> fiSupportMap;

	/** All infrequent itemsets of interest, each with its support. */
	private List<Itemset> infrequentItemsets;

	// ======================= OUTPUT ========================
	/** Output file writer; null means write to console */
	private BufferedWriter writer = null;

	/** Reusable buffer for formatting output lines */
	private final StringBuilder outputBuffer = new StringBuilder(512);

	/** Maximum itemset size the algorithm will explore */
	private int maxPatternLength = 1000;

	/** Maximum II size for subset enumeration. At size 25 there are already
	 *  ~33 million subsets per II, which is the practical tractability limit. */
	private static final int MAX_II_SIZE = 25;

	// =================== METHODS =========================
	/** Constructor */
	public AlgoNARMiner() {
	}

	/**
	 * Run the NAR-Miner algorithm.
	 *
	 * @param mfs     minimum frequency support as a fraction of |DB| in (0,1].
	 * @param mis     maximum infrequency support as a fraction of |DB| in (0,1).
	 *                Must be strictly less than mfs.
	 * @param minConf minimum confidence for rule output, in [0,1]
	 * @param input   path to the SPMF-format transaction database
	 * @param output  path to write results, or null to print to console
	 * @throws IOException              if an I/O error occurs
	 * @throws IllegalArgumentException if mis >= mfs
	 */
	public void runAlgorithm(double mfs, double mis, double minConf, String input, String output) throws IOException {

		if (mis >= mfs) {
			throw new IllegalArgumentException(
					"The maximum infrequency support must be smaller than " + "the minimum frequency support");
		}

		startTimestamp = System.currentTimeMillis();

		frequentItemsetCount = 0;
		infrequentItemsetCount = 0;
		ruleCount = 0;
		totalCandidateCount = 0;
		MemoryLogger.getInstance().reset();

		this.minConf = minConf;
		writer = (output != null) ? new BufferedWriter(new FileWriter(output)) : null;

		// -------------------------------------------------------
		// PHASE 1a — load database, count single-item supports
		// -------------------------------------------------------
		Map<Integer, Integer> mapItemCount = new HashMap<>();
		List<int[]> rawTransactions = new ArrayList<>(10000);
		List<Integer> parsedItems = new ArrayList<>(100);

		try (BufferedReader reader = new BufferedReader(new FileReader(input))) {
			String line;
			while ((line = reader.readLine()) != null) {
				// IGNORE LINES THAT ARE METADATA
				if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {
					continue;
				}
				parseLineToInts(line, parsedItems);
				int[] transaction = new int[parsedItems.size()];
				for (int i = 0; i < parsedItems.size(); i++) {
					int item = parsedItems.get(i);
					transaction[i] = item;
					mapItemCount.merge(item, 1, Integer::sum);
				}
				rawTransactions.add(transaction);
				databaseSize++;
			}
		}

		// Convert the two thresholds to relative thresholds
		mfsRelative = (int) Math.ceil(mfs * databaseSize);
		misRelative = (int) Math.ceil(mis * databaseSize);

		// -------------------------------------------------------
		// PHASE 1b — identify frequent items, assign internal IDs
		// -------------------------------------------------------
		List<Entry<Integer, Integer>> frequentItemsList = new ArrayList<>();
		for (Entry<Integer, Integer> e : mapItemCount.entrySet()) {
			if (e.getValue() >= mfsRelative)
				frequentItemsList.add(e);
		}
		mapItemCount = null;

		// Sort ascending by support; break ties by original ID
		frequentItemsList
				.sort(Comparator.comparingInt(Entry<Integer, Integer>::getValue).thenComparingInt(Entry::getKey));

		frequentItemCount = frequentItemsList.size();
		internalToOriginal = new int[frequentItemCount];
		originalToInternal = new HashMap<>(frequentItemCount * 2);
		supportOfItem = new int[frequentItemCount];

		// Store the frequent items with their new names and old names
		for (int i = 0; i < frequentItemCount; i++) {
			Entry<Integer, Integer> e = frequentItemsList.get(i);
			internalToOriginal[i] = e.getKey();
			originalToInternal.put(e.getKey(), i);
			supportOfItem[i] = e.getValue();
		}

		// Initialize collections
		fiSupportMap = new HashMap<>();
		infrequentItemsets = new ArrayList<>();

		// Create the set of frequent 1-itemsets
		for (int i = 0; i < frequentItemCount; i++) {
			int[] singleton = new int[] { i };
			fiSupportMap.put(new IntArrayKey(singleton), supportOfItem[i]);
			frequentItemsetCount++;
		}

		// If no frequent items, stop!
		if (frequentItemCount == 0) {
			finishAndClose();
			return;
		}

		// -------------------------------------------------------
		// PHASE 1c — recode transactions, merge identical ones
		// -------------------------------------------------------
		database = new ArrayList<>(rawTransactions.size());
		List<int[]> recodedTransactions = new ArrayList<>(rawTransactions.size());

		// For each transaction
		for (int[] transaction : rawTransactions) {
			int count = 0;

			// For each item in that transaction
			for (int item : transaction) {

				// If that item is frequent, increase the count of items for this transaction
				if (originalToInternal.containsKey(item))
					count++;
			}

			// If there are no frequent item, skip this transaction
			if (count == 0)
				continue;

			// Recode the transaction with new names
			int[] recoded = new int[count];
			int index = 0;

			// For each item
			for (int item : transaction) {
				// Convert to the new name
				Integer newId = originalToInternal.get(item);
				if (newId != null)
					recoded[index++] = newId;
			}

			// Sort the recoded transaction according to new names
			Arrays.sort(recoded);

			// Add the recoded transaction to the recoded database
			recodedTransactions.add(recoded);
		}
		rawTransactions = null;

		// Sort so that identical transactions are adjacent
		recodedTransactions.sort((a, b) -> {
			int minLen = Math.min(a.length, b.length);
			for (int i = 0; i < minLen; i++) {
				if (a[i] != b[i])
					return a[i] - b[i];
			}
			return a.length - b.length;
		});

		// Then merge identical transactions and assign a weight to transactions that
		// are merged
		List<Integer> weightsList = new ArrayList<>(recodedTransactions.size());
		if (!recodedTransactions.isEmpty()) {
			int[] current = recodedTransactions.get(0);
			int currentWeight = 1;
			// Loop over transactions
			for (int i = 1; i < recodedTransactions.size(); i++) {
				int[] next = recodedTransactions.get(i);

				// If the next transaction is the same as the current transaction
				if (Arrays.equals(current, next)) {
					// Increase the weight
					currentWeight++;
				} else {
					// Otherwise, add the current transaction with its weight.
					database.add(current);
					weightsList.add(currentWeight);
					current = next;
					currentWeight = 1;
				}
			}
			database.add(current);
			weightsList.add(currentWeight);
		}
		recodedTransactions = null;

		// Copy the weights to an array for fast access later on.
		transactionWeights = new int[weightsList.size()];
		for (int i = 0; i < weightsList.size(); i++) {
			transactionWeights[i] = weightsList.get(i);
		}

		// -------------------------------------------------------
		// PHASE 1d — Apriori-style level-wise mining (k >= 2)
		// -------------------------------------------------------
		// Create the first level using the frequent items
		List<int[]> Lk_1 = new ArrayList<>();
		for (int i = 0; i < frequentItemCount; i++) {
			Lk_1.add(new int[] { i });
		}

		// Then recursively mine itemsets, level by level
		k = 2;
		while (!Lk_1.isEmpty() && k <= maxPatternLength) {
			MemoryLogger.getInstance().checkMemory();

			List<int[]> Sk = generateCandidates(Lk_1);
			totalCandidateCount += Sk.size();
			
			// If no candidate, stop
			if (Sk.isEmpty())
				break;

			// Create the list of frequent items for this level
			List<int[]> Lk = new ArrayList<>();
			
			// For each candidate
			for (int[] candidate : Sk) {
				// Count its support
				int support = countSupport(candidate);

				if (support >= mfsRelative) {
					// Frequent itemset: add to Lk for next-level generation
					// and register in fiSupportMap for O(1) support lookup.
					Lk.add(candidate);
					fiSupportMap.put(new IntArrayKey(candidate), support);
					frequentItemsetCount++;
				} else if (support > 0 && support <= misRelative) {
					// Infrequent itemset of interest: store as a single object.
					infrequentItemsets.add(new Itemset(candidate, support));
					infrequentItemsetCount++;
				}
				// support == 0: absent everywhere, not interesting
			}

			Lk_1 = Lk;
			k++;
		}

		// -------------------------------------------------------
		// PHASE 2 — generate negative association rules
		// -------------------------------------------------------
		generateNegativeRules();

		// Finish and close
		finishAndClose();
	}

	/**
	 * Function called when the algorithm terminates.
	 * Check memory and time, and close the file.
	 * @throws IOException If some I/O error occurs.
	 */
	private void finishAndClose() throws IOException {
		endTimestamp = System.currentTimeMillis();
		MemoryLogger.getInstance().checkMemory();
		if (writer != null)
			writer.close();
	}

	// =========================================================
	// === CANDIDATE GENERATION ===
	// =========================================================

	/**
	 * Generate candidate itemsets of size k from frequent itemsets of size k-1
	 * using the Apriori join and prune steps.
	 *
	 * @param Lk_1 lexicographically sorted frequent itemsets of size k-1
	 * @return candidate itemsets of size k (unsorted)
	 */
	private List<int[]> generateCandidates(List<int[]> Lk_1) {
		List<int[]> candidates = new ArrayList<>();
		int size = Lk_1.size();

		// Loop over itemsets of the current level
		outerLoop: for (int i = 0; i < size; i++) {
			int[] itemset1 = Lk_1.get(i);

			// For each other itemset
			innerLoop: for (int j = i + 1; j < size; j++) {
				int[] itemset2 = Lk_1.get(j);
				int prefixLen = itemset1.length - 1;

				// Check if they are the same except the last item
				for (int p = 0; p < prefixLen; p++) {
					if (itemset1[p] != itemset2[p]) {
						if (itemset2[p] > itemset1[p])
							continue outerLoop;
						continue innerLoop;
					}
				}

				// And make sure the last item is smaller for the first itemset
				if (itemset2[prefixLen] <= itemset1[prefixLen])
					continue;

				// Then create a candidate by combining the two itemsets
				int[] candidate = Arrays.copyOf(itemset1, itemset1.length + 1);
				candidate[itemset1.length] = itemset2[itemset2.length - 1];

				// Do the Apriori pruning. If a subset is infrequent we can eliminate the
				// itemset.
				if (candidate.length == 2 || allSubsetsFrequent(candidate, Lk_1)) {
					candidates.add(candidate);
				}
			}
		}
		return candidates;
	}

	/**
	 * Check that every (k-1)-subset of candidate appears in the sorted list Lk_1.
	 *
	 * @param candidate sorted candidate itemset of size k
	 * @param Lk_1      sorted list of frequent itemsets of size k-1
	 * @return true if all subsets are present
	 */
	private boolean allSubsetsFrequent(int[] candidate, List<int[]> Lk_1) {
		int[] subset = new int[candidate.length - 1];
		for (int skip = 0; skip < candidate.length; skip++) {
			int idx = 0;
			for (int p = 0; p < candidate.length; p++) {
				if (p != skip)
					subset[idx++] = candidate[p];
			}
			if (!containsSorted(Lk_1, subset))
				return false;
		}
		return true;
	}

	/**
	 * Binary search for target in a lexicographically sorted list of same-length
	 * itemsets.
	 *
	 * @param sortedList sorted list of itemsets
	 * @param target     itemset to find
	 * @return true if found
	 */
	private boolean containsSorted(List<int[]> sortedList, int[] target) {
		int lo = 0, hi = sortedList.size() - 1;
		while (lo <= hi) {
			int mid = (lo + hi) >>> 1;
			int cmp = compareItemsets(sortedList.get(mid), target);
			if (cmp == 0)
				return true;
			if (cmp < 0)
				lo = mid + 1;
			else
				hi = mid - 1;
		}
		return false;
	}

	/**
	 * Lexicographic comparison of two itemsets.
	 *
	 * @param a first itemset
	 * @param b second itemset
	 * @return negative, zero, or positive
	 */
	private int compareItemsets(int[] a, int[] b) {
		int len = Math.min(a.length, b.length);
		for (int i = 0; i < len; i++) {
			if (a[i] != b[i])
				return a[i] - b[i];
		}
		return a.length - b.length;
	}

	/**
	 * Count the absolute support of a candidate by scanning the merged database.
	 *
	 * @param candidate sorted internal-ID itemset
	 * @return absolute support count
	 */
	private int countSupport(int[] candidate) {
		int support = 0;
		int dbSize = database.size();
		for (int t = 0; t < dbSize; t++) {
			int[] transaction = database.get(t);
			if (transaction.length >= candidate.length && isSubset(candidate, transaction)) {
				support += transactionWeights[t];
			}
		}
		return support;
	}

	/**
	 * Check whether candidate is a subset of transaction. Both arrays must be
	 * sorted ascending.
	 *
	 * @param candidate   sorted candidate
	 * @param transaction sorted transaction
	 * @return true if candidate is a subset of transaction
	 */
	private boolean isSubset(int[] candidate, int[] transaction) {
		int ci = 0, ti = 0;
		while (ci < candidate.length && ti < transaction.length) {
			if (candidate[ci] == transaction[ti]) {
				ci++;
				ti++;
			} else if (transaction[ti] < candidate[ci]) {
				ti++;
			} else {
				return false;
			}
		}
		return ci == candidate.length;
	}

	/**
	 * Retrieve the support of an itemset from fiSupportMap in O(1) average time.
	 *
	 * @param items sorted internal-ID itemset
	 * @return absolute support, or 0 if not a frequent itemset
	 */
	private int getSupportFromFIs(int[] items) {
		Integer sup = fiSupportMap.get(new IntArrayKey(items));
		return (sup != null) ? sup : 0;
	}

	/**
	 * Generate negative association rules from all IIs and write them sorted by
	 * interestingness descending.
	 * <p>
	 * For each II I (size &ge; 2):<br/>
	 * 1. Find the minimum support among all frequent proper non-empty subsets of
	 * I.<br/>
	 * 2. For every tied subset A, emit A =&gt; NOT (I\A) if confidence &ge;
	 * minConf.<br/>
	 * confidence = 1 - support(I) / support(A)<br/>
	 * interestingness = confidence / H(I), where H(I) is Shannon entropy over
	 * singleton support probabilities p(x) = support({x}) / |DB|.
	 *
	 * @throws IOException if an I/O error occurs
	 */
	private void generateNegativeRules() throws IOException {
		List<NegativeRule> rules = new ArrayList<>();

		// For each infrequent itemset
		for (Itemset ii : infrequentItemsets) {
			int[] I = ii.itemset;
			int supportI = ii.support;

			if (I.length < 2)
			    continue;

			if (I.length > MAX_II_SIZE) {
			    System.err.println("[NAR-Miner] Skipping II of size " + I.length
			            + " (exceeds practical enumeration limit of " + MAX_II_SIZE + ").");
			    continue;
			}

			// Pass 1: find minimum support among frequent proper non-empty subsets
			int minSupportA = Integer.MAX_VALUE;
			long numSubsets = 1L << I.length;

			// Generate the subsets
			for (long mask = 1L; mask < numSubsets - 1L; mask++) {
				int[] subset = buildSubset(I, mask);
				int subSup = getSupportFromFIs(subset);
				if (subSup >= mfsRelative && subSup < minSupportA) {
					minSupportA = subSup;
				}
			}
			
			// There should always be at least one subset that is frequent. If not this is a bug.
			assert minSupportA != Integer.MAX_VALUE : "II has no frequent proper subset (violates Apriori structure)";

			// Calculate the confidence
			double confidence = 1.0 - (double) supportI / (double) minSupportA;
			if (confidence < minConf)
				continue;

			// Calculate entropy  and interestingness
			double entropy = computeEntropy(I);
			double interestingness = (entropy <= 0.0) ? confidence : confidence / entropy;

			// Pass 2: Emit one rule for each antecedent that is tied at the minimum
			for (long mask = 1L; mask < numSubsets - 1L; mask++) {
				int[] A = buildSubset(I, mask);
				int subSup = getSupportFromFIs(A);
				if (subSup != minSupportA)
					continue;

				int[] B = setDifference(I, A);
				if (B.length == 0)
					continue;

				// Add the rule
				rules.add(new NegativeRule(A, B, supportI, minSupportA, confidence, interestingness));
			}
		}

		// Sort the rule by decreasing interestingness
		rules.sort((r1, r2) -> Double.compare(r2.interestingness, r1.interestingness));

		// Write the rules to file
		for (NegativeRule rule : rules) {
			writeRule(rule);
			ruleCount++;
		}
	}

	/**
	 * Build the subset of I selected by the given long bitmask. Bit i set means
	 * include I[i]. The result is sorted because I is sorted.
	 *
	 * @param I    sorted source itemset
	 * @param mask long bitmask (bit 0 = I[0], bit 1 = I[1], ...)
	 * @return sorted subset
	 */
	private int[] buildSubset(int[] I, long mask) {
		int count = Long.bitCount(mask);
		int[] subset = new int[count];
		int out = 0;
		for (int i = 0; i < I.length; i++) {
			if ((mask & (1L << i)) != 0L)
				subset[out++] = I[i];
		}
		return subset;
	}

	/**
	 * Compute A \ B (elements of A not in B). Both arrays must be sorted.
	 *
	 * @param A sorted itemset
	 * @param B sorted itemset to subtract
	 * @return sorted difference array
	 */
	private int[] setDifference(int[] A, int[] B) {
		int count = 0, bi = 0;
		for (int ai = 0; ai < A.length; ai++) {
			while (bi < B.length && B[bi] < A[ai])
				bi++;
			if (bi >= B.length || B[bi] != A[ai])
				count++;
		}
		int[] result = new int[count];
		int out = 0;
		bi = 0;
		for (int ai = 0; ai < A.length; ai++) {
			while (bi < B.length && B[bi] < A[ai])
				bi++;
			if (bi >= B.length || B[bi] != A[ai])
				result[out++] = A[ai];
		}
		return result;
	}

	/**
	 * Shannon entropy H(I) = -sum_x p(x)*log2(p(x)), p(x) = support({x})/|DB|.
	 *
	 * @param I itemset (internal IDs)
	 * @return entropy value >= 0
	 */
	private double computeEntropy(int[] I) {
		double entropy = 0.0;
		for (int item : I) {
			double p = (double) supportOfItem[item] / (double) databaseSize;
			if (p > 0.0)
				entropy -= p * (Math.log(p) / Math.log(2.0));
		}
		return entropy;
	}

	/**
	 * Parse a line into a list of non-negative integers without using
	 * String.split() to avoid regex overhead.
	 *
	 * @param line   input line
	 * @param result list to populate (cleared on entry)
	 */
	private void parseLineToInts(String line, List<Integer> result) {
		result.clear();
		int length = line.length(), number = 0;
		boolean isInt = false;
		for (int i = 0; i < length; i++) {
			char c = line.charAt(i);
			if (c >= '0' && c <= '9') {
				number = number * 10 + (c - '0');
				isInt = true;
			} else if ((c == ' ' || c == '\t') && isInt) {
				result.add(number);
				number = 0;
				isInt = false;
			}
		}
		if (isInt)
			result.add(number);
	}

	/**
	 * Write one rule to output in SPMF format: ant1 ant2 ==> NOT con1 con2 #SUP: X
	 * #CONF: Y #INTER: Z
	 *
	 * @param rule the rule to write
	 * @throws IOException if an I/O error occurs
	 */
	private void writeRule(NegativeRule rule) throws IOException {
		outputBuffer.setLength(0);

		// Write the antecendent
		int[] antOrig = toOriginalSorted(rule.antecedent);
		for (int i = 0; i < antOrig.length; i++) {
			if (i > 0)
				outputBuffer.append(' ');
			outputBuffer.append(antOrig[i]);
		}

		outputBuffer.append(" ==> NOT ");

		// Write the  consequent
		int[] conOrig = toOriginalSorted(rule.consequent);
		for (int i = 0; i < conOrig.length; i++) {
			if (i > 0)
				outputBuffer.append(' ');
			outputBuffer.append(conOrig[i]);
		}

		// Write the measures
		outputBuffer.append(" #SUP: ").append(rule.supportI);
		outputBuffer.append(" #CONF: ").append(String.format("%.4f", rule.confidence));
		outputBuffer.append(" #INTER: ").append(String.format("%.4f", rule.interestingness));

		String s = outputBuffer.toString();
		if (writer != null) {
			writer.write(s);
			writer.newLine();
		} else {
			System.out.println(s);
		}
	}

	/**
	 * Convert internal item IDs to original IDs, sorted ascending.
	 *
	 * @param internalItems internal item IDs
	 * @return sorted original item IDs
	 */
	private int[] toOriginalSorted(int[] internalItems) {
		int[] orig = new int[internalItems.length];
		for (int i = 0; i < internalItems.length; i++) {
			orig[i] = internalToOriginal[internalItems[i]];
		}
		Arrays.sort(orig);
		return orig;
	}

	/**
	 * Return the number of transactions in the input database.
	 *
	 * @return database size
	 */
	public int getDatabaseSize() {
		return databaseSize;
	}

	/**
	 * Print execution statistics to stdout.
	 */
	public void printStats() {
		System.out.println("============= NAR-MINER 2.66 - STATS =============");
		System.out.println(" General information:");
		System.out.println("    Database size              : " + databaseSize);
		System.out.println("    Candidates generated       : " + totalCandidateCount);
		System.out.println("    Frequent itemsets found    : " + frequentItemsetCount);
		System.out.println("    Infrequent itemsets found  : " + infrequentItemsetCount);
		System.out.println(" Negative rules generated   : " + ruleCount);
		System.out.println(" Maximum memory usage       : " + MemoryLogger.getInstance().getMaxMemory() + " mb");
		System.out.println(" Total time                 : " + (endTimestamp - startTimestamp) + " ms");
		System.out.println("=============================================");
	}
	
	

}