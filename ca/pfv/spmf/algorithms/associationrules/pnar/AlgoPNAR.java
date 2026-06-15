package ca.pfv.spmf.algorithms.associationrules.pnar;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ca.pfv.spmf.tools.MemoryLogger;

/*
This file is copyright (c) 2008-2026 Philippe Fournier-Viger
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
Do not remove this copyright and license information.
*/
/**
 * Implementation of the PNAR algorithm for mining positive and negative
 * association rules from transaction databases.
 *
 * <p>
 * Reference: Cornelis et al. (2006). Mining positive and negative association
 * rules from large databases. CIS 2006, pp. 613-618.
 * </p>
 *
 * <p>
 * Four types of rules are generated:
 * </p>
 * <ul>
 * <li>R1: X ==&gt; Y (positive antecedent, positive consequent)</li>
 * <li>R2: NOT(X) ==&gt; NOT(Y)</li>
 * <li>R3: X ==&gt; NOT(Y)</li>
 * <li>R4: NOT(X) ==&gt; Y</li>
 * </ul>
 *
 * <p>
 * Frequent positive itemsets can be mined using either AprioriTID or ECLAT.
 * </p>
 *
 * @author Philippe Fournier-Viger, 2026
 */
public class AlgoPNAR {

	/** Implemented frequent itemset mining algorithms */
	public enum MiningAlgorithm {
		APRIORI_TID, ECLAT
	}

	/** Timestamp when the algorithm started. */
	private long startTimestamp;

	/** Timestamp when the algorithm finished. */
	private long endTimestamp;

	/** Total number of rules discovered. */
	private int ruleCount;

	/** Number of transactions in the database (before merging). */
	int databaseSize;

	/** The transaction database after recoding and merging. */
	List<int[]> database;

	/** Weight of each transaction in the database after merging. */
	private int[] transactionWeights;

	/**
	 * Support cache for all itemsets whose support has been computed. Key: an
	 * IntArrayKey wrapping the sorted recoded itemset.
	 */
	Map<IntArrayKey, Integer> mapItemsetSupportCache;

	/**
	 * Support cache for frequent positive itemsets only. Key: an IntArrayKey
	 * wrapping the sorted recoded itemset.
	 */
	Map<IntArrayKey, Integer> mapFrequentPositiveSupport;

	/** Support of each item (indexed by recoded item id). */
	private int[] singletonSupport;

	/**
	 * List of frequent positive itemsets grouped by size. Index k contains frequent
	 * k-itemsets (recoded ids).
	 */
	List<List<int[]>> frequentPositive;

	/** Flat list of all frequent positive itemsets across all sizes. */
	private List<int[]> allFrequentItemsets;

	/** Minimum support threshold as an absolute transaction count. */
	int minsuppAbsolute;

	/** Minimum confidence threshold in [0,1]. */
	private double minconf;

	/** Whether to generate R1 rules (X ==&gt; Y). Default: true. */
	private boolean generateR1 = true;

	/** Whether to generate R2 rules (NOT(X) ==&gt; NOT(Y)). Default: true. */
	private boolean generateR2 = true;

	/** Whether to generate R3 rules (X ==&gt; NOT(Y)). Default: true. */
	private boolean generateR3 = true;

	/** Whether to generate R4 rules (NOT(X) ==&gt; Y). Default: true. */
	private boolean generateR4 = true;

	/** The frequent itemset mining algorithm to use. Default: ECLAT. */
	private MiningAlgorithm miningAlgorithm = MiningAlgorithm.ECLAT;

	/** Writer for outputting rules to a file, or null if saving in memory. */
	private BufferedWriter writer;

	/** In-memory rule container, used when output path is null. */
	private RulesPNAR rules;

	/** Reusable buffer for formatting rule output lines. */
	private StringBuilder outputBuffer;

	/**
	 * Inverted index where [x][] is the list of transaction indices where item x
	 * appears.
	 */
	private int[][] invertedIndex;

	/** Scratch array reused by unionInto to avoid allocation per call. */
	private int[] unionScratch;

	/**
	 * First ping-pong buffer reused by computeSupportViaIndex to avoid allocation.
	 */
	private int[] tidListCurrent;

	/**
	 * Second ping-pong buffer reused by computeSupportViaIndex to avoid allocation.
	 */
	private int[] tidListOther;

	/** Number of frequent items. */
	private int frequentItemCount;

	/** Maps original item ids to recoded item ids, or -1 if infrequent. */
	private int[] originalToRecoded;

	/** Maps recoded item ids back to original item ids for output. */
	private int[] recodedToOriginal;

	/**
	 * For APRIORITID: tidlist store: maps each frequent itemset key to its tidlist.
	 */
	private Map<IntArrayKey, int[]> aprioriTidLists;

	/**
	 * For ECLAT: Reusable prefix buffer, shared across all recursive calls to
	 * eclatProcessEquivalenceClass.
	 */
	private int[] eclatPrefixBuffer;

	/** The constructor. */
	public AlgoPNAR() {
	}

	/**
	 * Sets whether to generate R1 rules (X ==&gt; Y).
	 *
	 * @param generateR1 true to generate, false to skip
	 */
	public void setGenerateR1(boolean generateR1) {
		this.generateR1 = generateR1;
	}

	/**
	 * Sets whether to generate R2 rules (NOT(X) ==&gt; NOT(Y)).
	 *
	 * @param generateR2 true to generate, false to skip
	 */
	public void setGenerateR2(boolean generateR2) {
		this.generateR2 = generateR2;
	}

	/**
	 * Sets whether to generate R3 rules (X ==&gt; NOT(Y)).
	 *
	 * @param generateR3 true to generate, false to skip
	 */
	public void setGenerateR3(boolean generateR3) {
		this.generateR3 = generateR3;
	}

	/**
	 * Sets whether to generate R4 rules (NOT(X) ==&gt; Y).
	 *
	 * @param generateR4 true to generate, false to skip
	 */
	public void setGenerateR4(boolean generateR4) {
		this.generateR4 = generateR4;
	}

	/**
	 * Sets the frequent itemset mining algorithm to use.
	 *
	 * @param algo the algorithm to use
	 */
	public void setMiningAlgorithm(MiningAlgorithm algo) {
		this.miningAlgorithm = algo;
	}

	/**
	 * Runs the PNAR algorithm.
	 *
	 * @param input   path to the input file
	 * @param output  path to the output file, or null to return rules in memory
	 * @param minsupp relative minimum support in [0,1]
	 * @param minconf minimum confidence in [0,1]
	 * @return the discovered rules, or null if writing to file
	 * @throws IOException if an I/O error occurs
	 */
	public RulesPNAR runAlgorithm(String input, String output, double minsupp, double minconf) throws IOException {
		// Save start time and reset memory logger
		startTimestamp = System.currentTimeMillis();
		MemoryLogger.getInstance().reset();

		this.minconf = minconf;
		ruleCount = 0;

		// If write to memory, initialize structure, otherwise prepare filewriter
		if (output == null) {
			writer = null;
			rules = new RulesPNAR("POSITIVE AND NEGATIVE ASSOCIATION RULES");
		} else {
			writer = new BufferedWriter(new FileWriter(output));
			rules = null;
		}
		outputBuffer = new StringBuilder(256);

		// Step 1: Load, recode, merge database, build inverted index
		loadAndPrepareDatabase(input, minsupp);

		// Step 2: Mine all frequent positive itemsets
		mapFrequentPositiveSupport = new HashMap<>();
		mapItemsetSupportCache = new HashMap<>();
		frequentPositive = new ArrayList<>();

		if (miningAlgorithm == MiningAlgorithm.ECLAT)
			runEclat();
		else
			runAprioriTID();

		// Free memory no longer needed after frequent itemset mining
		aprioriTidLists = null;
		eclatPrefixBuffer = null;
		MemoryLogger.getInstance().checkMemory();

		// Collect all frequent itemsets into a flat list and find the max size
		int maxItemsetSize = 0;
		allFrequentItemsets = new ArrayList<>();
		for (List<int[]> level : frequentPositive) {
			if (level == null)
				continue;
			for (int[] itemset : level) {
				allFrequentItemsets.add(itemset);
				if (itemset.length > maxItemsetSize)
					maxItemsetSize = itemset.length;
			}
		}

		// Allocate shared scratch buffers sized for the largest possible union
		unionScratch = new int[maxItemsetSize * 2 + 2];
		tidListCurrent = new int[database.size()];
		tidListOther = new int[database.size()];

		// Step 3: Generate L(P3) for R2
		List<MixedItemset> lP3 = null;
		if (generateR2) {
			lP3 = generateLP3();
			MemoryLogger.getInstance().checkMemory();
		}

		// Step 4: Generate L(P34) for R3/R4
		List<PairItemset> lP34 = null;
		if (generateR3 || generateR4) {
			lP34 = generateLP34();
			MemoryLogger.getInstance().checkMemory();
		}

		// Step 5: Generate all valid rules
		if (generateR1)
			generatePositiveRules();
		if (generateR2 && lP3 != null)
			generateR2Rules(lP3);
		if (generateR3 && lP34 != null)
			generateR3Rules(lP34);
		if (generateR4 && lP34 != null)
			generateR4Rules(lP34);

		if (writer != null)
			writer.close();
		outputBuffer = null;

		endTimestamp = System.currentTimeMillis();
		MemoryLogger.getInstance().checkMemory();
		return rules;
	}

	/** Prints execution statistics to the console. */
	public void printStats() {
		System.out.println("============= PNAR v. 2.66 - STATS =============");
		System.out.println(" Frequent itemset miner : " + miningAlgorithm);
		System.out.println(" Number of rules found  : " + ruleCount);
		System.out.println(" Database size          : " + databaseSize + " transactions");
		System.out.println(" Max memory             : " + MemoryLogger.getInstance().getMaxMemory() + " MB");
		System.out.println(" Total time             : " + (endTimestamp - startTimestamp) + " ms");
		System.out.println("=========================================");
	}

	/**
	 * Reads the transaction database, recodes frequent items, strips infrequent
	 * items, merges identical transactions, builds the inverted index, and
	 * populates singletonSupport — all in optimized passes.
	 *
	 * @param filePath path to the SPMF-format input file
	 * @param minsupp  relative minimum support in [0,1]
	 * @throws IOException if an I/O error occurs
	 */
	private void loadAndPrepareDatabase(String filePath, double minsupp) throws IOException {

		// Pass 1: Read raw transactions and count item support
		List<int[]> rawTransactions = new ArrayList<>();
		Map<Integer, Integer> itemSupport = new HashMap<>();

		try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
			String line;
			while ((line = reader.readLine()) != null) {
				// Ignore metadata lines
				if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@')
					continue;

				// Split the transaction (line) into items
				String[] tokens = line.trim().split("\\s+");
				int[] raw = new int[tokens.length];
				for (int i = 0; i < tokens.length; i++) {
					raw[i] = Integer.parseInt(tokens[i]);
					itemSupport.merge(raw[i], 1, Integer::sum);
				}
				rawTransactions.add(raw);
				databaseSize++;
			}
		}

		// Calculate the absolute minimum support
		minsuppAbsolute = (int) Math.ceil(minsupp * databaseSize);

		// Keep all the frequent items and find the maximum frequent item name
		List<Map.Entry<Integer, Integer>> freqEntries = new ArrayList<>();
		int maxOriginalId = 0;
		for (Map.Entry<Integer, Integer> e : itemSupport.entrySet()) {
			if (e.getValue() >= minsuppAbsolute) {
				freqEntries.add(e);
				if (e.getKey() > maxOriginalId)
					maxOriginalId = e.getKey();
			}
		}

		// Sort frequent items by ascending frequency, breaking ties by item id
		freqEntries.sort((a, b) -> {
			int cmp = a.getValue() - b.getValue();
			return cmp != 0 ? cmp : a.getKey() - b.getKey();
		});

		// Rename (recode) items by ascending order of frequency
		frequentItemCount = freqEntries.size();

		originalToRecoded = new int[maxOriginalId + 1];
		Arrays.fill(originalToRecoded, -1);
		recodedToOriginal = new int[frequentItemCount];
		singletonSupport = new int[frequentItemCount];

		for (int positionItem = 0; positionItem < frequentItemCount; positionItem++) {
			Map.Entry<Integer, Integer> e = freqEntries.get(positionItem);
			originalToRecoded[e.getKey()] = positionItem;
			recodedToOriginal[positionItem] = e.getKey();
			singletonSupport[positionItem] = e.getValue();
		}

		// Pass 2: Recode transactions, discard infrequent items, sort for merging
		List<int[]> recodedTransactions = new ArrayList<>(rawTransactions.size());
		for (int[] raw : rawTransactions) {
			int count = 0;
			for (int item : raw)
				if (item < originalToRecoded.length && originalToRecoded[item] >= 0)
					count++;
			if (count == 0)
				continue;

			int[] recoded = new int[count];
			int idx = 0;
			for (int item : raw)
				if (item < originalToRecoded.length && originalToRecoded[item] >= 0)
					recoded[idx++] = originalToRecoded[item];

			Arrays.sort(recoded);
			recodedTransactions.add(recoded);
		}

		// Sort transactions lexicographically to enable efficient duplicate merging
		recodedTransactions.sort((a, b) -> {
			int minLen = Math.min(a.length, b.length);
			for (int i = 0; i < minLen; i++)
				if (a[i] != b[i])
					return a[i] - b[i];
			return a.length - b.length;
		});

		// Pass 3: Merge identical transactions and build inverted index
		database = new ArrayList<>(recodedTransactions.size());
		int[] tempW = new int[recodedTransactions.size()];
		int mergedCount = 0;

		invertedIndex = new int[frequentItemCount][];
		@SuppressWarnings("unchecked")
		List<Integer>[] tidBuilders = new List[frequentItemCount];
		for (int i = 0; i < frequentItemCount; i++)
			tidBuilders[i] = new ArrayList<>();

		if (!recodedTransactions.isEmpty()) {
			int[] current = recodedTransactions.get(0);
			int weight = 1;

			for (int i = 1; i < recodedTransactions.size(); i++) {
				int[] next = recodedTransactions.get(i);
				if (Arrays.equals(current, next)) {
					weight++;
				} else {
					int tid = database.size();
					database.add(current);
					tempW[mergedCount++] = weight;
					for (int item : current)
						tidBuilders[item].add(tid);
					current = next;
					weight = 1;
				}
			}
			// Process the last transaction
			int tid = database.size();
			database.add(current);
			tempW[mergedCount++] = weight;
			for (int item : current)
				tidBuilders[item].add(tid);
		}

		transactionWeights = Arrays.copyOf(tempW, mergedCount);

		for (int item = 0; item < frequentItemCount; item++) {
			List<Integer> tl = tidBuilders[item];
			invertedIndex[item] = new int[tl.size()];
			for (int j = 0; j < tl.size(); j++)
				invertedIndex[item][j] = tl.get(j);
		}
	}

	/**
	 * Returns the weighted support of a tidlist (sum of transaction weights).
	 *
	 * @param tidList sorted array of merged transaction indices
	 * @return total weighted support
	 */
	private int weightedLength(int[] tidList) {
		int sum = 0;
		for (int tid : tidList)
			sum += transactionWeights[tid];
		return sum;
	}

	// =========================================================
	// AprioriTID
	// =========================================================

	/**
	 * Mines all frequent positive itemsets using AprioriTID.
	 *
	 * <p>
	 * The support of a candidate k-itemset formed by joining (k-1)-itemsets A and B
	 * is computed by intersecting tidlist(A) with tidlist(B), reusing the
	 * already-computed parent tidlists from the previous level. Results are stored
	 * in {@code frequentPositive} and {@code mapFrequentPositiveSupport}.
	 * </p>
	 */
	void runAprioriTID() {
		aprioriTidLists = new HashMap<>();

		// Level 1: seed all singleton tidlists from the inverted index
		List<int[]> level1 = new ArrayList<>(frequentItemCount);
		for (int item = 0; item < frequentItemCount; item++) {
			int[] singleton = new int[] { item };
			level1.add(singleton);
			aprioriTidLists.put(new IntArrayKey(singleton), invertedIndex[item]);
		}
		frequentPositive.add(null); // index 0 unused
		frequentPositive.add(level1); // index 1: singletons

		for (int k = 2;; k++) {
			List<int[]> prev = frequentPositive.get(k - 1);
			if (prev.isEmpty())
				break;
			List<int[]> levelK = aprioriTIDLevel(prev, k);
			if (levelK.isEmpty())
				break;
			frequentPositive.add(levelK);
		}
	}

	/**
	 * Generates frequent k-itemsets from frequent (k-1)-itemsets using AprioriTID:
	 * the candidate tidlist = intersect(tidlist(A), tidlist(B)) for each
	 * join-compatible parent pair (A, B).
	 *
	 * <p>
	 * The IntArrayKey for A is built once per outer iteration; the key for B is
	 * built only after all pruning checks pass, deferring the allocation.
	 * Infrequent candidates do not have their tidlist stored, since by the downward
	 * closure property they can never seed a frequent superset.
	 * </p>
	 *
	 * @param prevLevel sorted list of frequent (k-1)-itemsets
	 * @param k         target itemset size
	 * @return frequent k-itemsets in lexicographic order
	 */
	private List<int[]> aprioriTIDLevel(List<int[]> prevLevel, int k) {
		List<int[]> levelK = new ArrayList<>();
		int size = prevLevel.size();

		for (int i = 0; i < size; i++) {
			int[] a = prevLevel.get(i);
			IntArrayKey keyA = new IntArrayKey(a);
			int[] tidA = aprioriTidLists.get(keyA);

			outer: for (int j = i + 1; j < size; j++) {
				int[] b = prevLevel.get(j);

				// A and B must share the same prefix of length k-2
				if (k >= 3) {
					for (int m = 0; m < k - 2; m++) {
						if (b[m] > a[m]) {
							j = size; // No further b can share the prefix with a
							continue outer;
						}
						if (b[m] < a[m])
							continue outer;
					}
				}
				if (a[k - 2] >= b[k - 2])
					continue;

				int[] candidate = Arrays.copyOf(a, k);
				candidate[k - 1] = b[k - 2];

				// All (k-1)-subsets must be frequent (trivially satisfied for k==2)
				if (k > 2 && !allSubsetsFrequent(candidate))
					continue;

				// Intersect parent tidlists — deferred until after all pruning
				int[] tidB = aprioriTidLists.get(new IntArrayKey(b));
				int[] intersection = intersectTidLists(tidA, tidB);
				int sup = weightedLength(intersection);

				if (sup >= minsuppAbsolute) {
					IntArrayKey ck = new IntArrayKey(candidate);
					mapFrequentPositiveSupport.put(ck, sup);
					mapItemsetSupportCache.put(ck, sup);
					aprioriTidLists.put(ck, intersection);
					levelK.add(candidate);
				}
			}
		}
		return levelK;
	}

	/**
	 * Returns true if all (k-1)-subsets of the candidate are frequent. Only called
	 * for candidates of size k &gt;= 3.
	 *
	 * @param candidate the candidate itemset (length &gt;= 3)
	 * @return true if all (k-1)-subsets are in {@code mapFrequentPositiveSupport}
	 */
	boolean allSubsetsFrequent(int[] candidate) {
		int k = candidate.length;
		int[] subset = new int[k - 1];
		for (int skip = 0; skip < k; skip++) {
			int idx = 0;
			for (int m = 0; m < k; m++)
				if (m != skip)
					subset[idx++] = candidate[m];
			if (!mapFrequentPositiveSupport.containsKey(new IntArrayKey(subset)))
				return false;
		}
		return true;
	}

	// =========================================================
	// ECLAT
	// =========================================================

	/**
	 * Mines all frequent positive itemsets using ECLAT.
	 */
	void runEclat() {
		frequentPositive.add(null); // index 0 unused
		frequentPositive.add(new ArrayList<>()); // index 1: singletons

		// Register all frequent singletons
		for (int item = 0; item < frequentItemCount; item++)
			frequentPositive.get(1).add(new int[] { item });

		// Reusable prefix buffer shared across all recursive ECLAT calls
		eclatPrefixBuffer = new int[frequentItemCount];

		for (int i = 0; i < frequentItemCount; i++) {
			int[] tidI = invertedIndex[i];
			int suppI = singletonSupport[i];

			List<Integer> suffixItems = new ArrayList<>();
			List<int[]> suffixTidsets = new ArrayList<>();

			for (int j = i + 1; j < frequentItemCount; j++) {
				int[] intersection = intersectTidLists(tidI, invertedIndex[j]);
				int sup = weightedLength(intersection);
				if (sup >= minsuppAbsolute) {
					suffixItems.add(j);
					suffixTidsets.add(intersection);
				}
			}

			if (!suffixItems.isEmpty()) {
				eclatPrefixBuffer[0] = i;
				eclatProcessEquivalenceClass(eclatPrefixBuffer, 1, suppI, suffixItems, suffixTidsets);
			}
		}
	}

	/**
	 * Recursively processes an equivalence class to discover and register all
	 * frequent itemsets whose prefix is prefix[0..prefixLength-1].
	 *
	 * @param prefix        shared prefix buffer (written to by all recursive calls)
	 * @param prefixLength  number of valid elements in prefix
	 * @param supportPrefix weighted support of the prefix itemset
	 * @param suffixItems   suffix items of the equivalence class
	 * @param suffixTidsets tidlists corresponding to each suffix item
	 */
	private void eclatProcessEquivalenceClass(int[] prefix, int prefixLength, int supportPrefix,
			List<Integer> suffixItems, List<int[]> suffixTidsets) {

		int classSize = suffixItems.size();

		// Base case: single extension
		if (classSize == 1) {
			registerFrequentItemset(prefix, prefixLength, suffixItems.get(0), weightedLength(suffixTidsets.get(0)));
			return;
		}

		// Base case: two extensions — register both and check their combination
		if (classSize == 2) {
			int itemI = suffixItems.get(0);
			int[] tidsetI = suffixTidsets.get(0);
			int itemJ = suffixItems.get(1);
			int[] tidsetJ = suffixTidsets.get(1);
			registerFrequentItemset(prefix, prefixLength, itemI, weightedLength(tidsetI));
			registerFrequentItemset(prefix, prefixLength, itemJ, weightedLength(tidsetJ));
			int[] tidIJ = intersectTidLists(tidsetI, tidsetJ);
			int supIJ = weightedLength(tidIJ);
			if (supIJ >= minsuppAbsolute) {
				prefix[prefixLength] = itemI;
				registerFrequentItemset(prefix, prefixLength + 1, itemJ, supIJ);
			}
			return;
		}

		// Single-path optimisation: if all suffix items have the same support as the
		// prefix, then every non-empty subset of the suffix is frequent with that
		// support (upward closure of identical tidsets), so enumerate all directly.
		boolean allSameAsPrefix = true;
		for (int k = 0; k < classSize; k++) {
			if (weightedLength(suffixTidsets.get(k)) != supportPrefix) {
				allSameAsPrefix = false;
				break;
			}
		}
		if (allSameAsPrefix) {
			eclatSaveSinglePath(prefix, prefixLength, suffixItems, supportPrefix);
			return;
		}

		// General case: pairwise intersections
		for (int i = 0; i < classSize; i++) {
			int itemI = suffixItems.get(i);
			int[] tidsetI = suffixTidsets.get(i);
			int supI = weightedLength(tidsetI);

			registerFrequentItemset(prefix, prefixLength, itemI, supI);

			List<Integer> nextItems = new ArrayList<>();
			List<int[]> nextTidsets = new ArrayList<>();

			for (int j = i + 1; j < classSize; j++) {
				int[] tidIJ = intersectTidLists(tidsetI, suffixTidsets.get(j));
				int supIJ = weightedLength(tidIJ);
				if (supIJ >= minsuppAbsolute) {
					nextItems.add(suffixItems.get(j));
					nextTidsets.add(tidIJ);
				}
			}

			if (!nextItems.isEmpty()) {
				prefix[prefixLength] = itemI;
				eclatProcessEquivalenceClass(prefix, prefixLength + 1, supI, nextItems, nextTidsets);
			}
		}

		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * Single-path optimisation: all non-empty subsets of {@code suffixItems} share
	 * the same weighted support. Enumerates all 2^n - 1 non-empty subsets via
	 * bitmask and registers each one without further intersections.
	 *
	 * @param prefix       shared prefix buffer
	 * @param prefixLength valid elements in prefix before this suffix
	 * @param suffixItems  items of the equivalence class (all same support)
	 * @param support      the common weighted support
	 */
	private void eclatSaveSinglePath(int[] prefix, int prefixLength, List<Integer> suffixItems, int support) {
		int n = suffixItems.size();
		for (long mask = 1L; mask < (1L << n); mask++) {
			int newPrefixLen = prefixLength;
			int lastItem = -1;
			for (int b = 0; b < n; b++) {
				if ((mask & (1L << b)) != 0L) {
					if (lastItem >= 0)
						prefix[newPrefixLen++] = lastItem;
					lastItem = suffixItems.get(b);
				}
			}
			registerFrequentItemset(prefix, newPrefixLen, lastItem, support);
		}
	}

	/**
	 * Registers one frequent itemset — prefix[0..prefixLength-1] plus suffixItem —
	 * in the support maps and in {@code frequentPositive}.
	 *
	 * @param prefix       shared prefix buffer
	 * @param prefixLength length of the current prefix
	 * @param suffixItem   last item of the itemset
	 * @param support      weighted support of the itemset
	 */
	private void registerFrequentItemset(int[] prefix, int prefixLength, int suffixItem, int support) {
		int totalLen = prefixLength + 1;
		int[] itemset = new int[totalLen];
		System.arraycopy(prefix, 0, itemset, 0, prefixLength);
		itemset[prefixLength] = suffixItem;

		IntArrayKey ck = new IntArrayKey(itemset);
		mapFrequentPositiveSupport.put(ck, support);
		mapItemsetSupportCache.put(ck, support);

		while (frequentPositive.size() <= totalLen)
			frequentPositive.add(new ArrayList<>());
		frequentPositive.get(totalLen).add(itemset);
	}

	/**
	 * Intersects two sorted tidlists and returns the result as a new array of
	 * exactly the right length.
	 *
	 * @param a first sorted tidlist
	 * @param b second sorted tidlist
	 * @return sorted array of tids present in both a and b
	 */
	private int[] intersectTidLists(int[] a, int[] b) {
		int[] result = new int[Math.min(a.length, b.length)];
		int i = 0, j = 0, k = 0;
		while (i < a.length && j < b.length) {
			int ta = a[i], tb = b[j];
			if (ta < tb)
				i++;
			else if (ta > tb)
				j++;
			else {
				result[k++] = ta;
				i++;
				j++;
			}
		}
		return Arrays.copyOf(result, k);
	}

	// =========================================================
	// L(P3): canonical frequent NOT(X) NOT(Y) pairs for R2
	// =========================================================

	/**
	 * Generates canonical frequent NOT(X) NOT(Y) pairs for R2 rule generation.
	 *
	 * <p>
	 * Only unordered pairs {X, Y} with X != Y are generated here; both directions
	 * (NOT(X)==&gt;NOT(Y) and NOT(Y)==&gt;NOT(X)) are checked during rule
	 * generation in {@link #generateR2Rules(List)}.
	 * </p>
	 *
	 * <p>
	 * Formula: supp(NOT(X) AND NOT(Y)) = |D| - supp(X) - supp(Y) + supp(X∪Y)
	 * </p>
	 *
	 * @return list of frequent NOT(X) NOT(Y) pairs
	 */
	private List<MixedItemset> generateLP3() {
		List<MixedItemset> lP3 = new ArrayList<>();
		int n = allFrequentItemsets.size();

		for (int xi = 0; xi < n; xi++) {
			int[] X = allFrequentItemsets.get(xi);
			int suppX = getSupportOfPositiveItemset(X);

			for (int yi = xi + 1; yi < n; yi++) {
				int[] Y = allFrequentItemsets.get(yi);

				// Both X and Y must be disjoint for a valid negative AR
				if (hasOverlap(X, Y))
					continue;

				int suppY = getSupportOfPositiveItemset(Y);
				int suppXuY = getSupportViaKey(unionScratch, unionInto(unionScratch, X, Y));
				int suppNotXNotY = databaseSize - suppX - suppY + suppXuY;

				if (suppNotXNotY >= minsuppAbsolute) {
					MixedItemset mis = new MixedItemset(X, Y);
					mis.support = suppNotXNotY;
					lP3.add(mis);
				}
			}
		}
		return lP3;
	}

	// =========================================================
	// L(P34): ordered frequent (X,Y) pairs for R3 and R4
	// =========================================================

	/**
	 * Generates ordered frequent (X, Y) pairs for R3 and R4 rule generation.
	 *
	 * <p>
	 * For each unordered disjoint pair {X, Y}, both directed pairs (X,Y) and (Y,X)
	 * are emitted whenever the corresponding mixed support meets the minimum
	 * support threshold. The union support {@code supp(X∪Y)} is computed only once
	 * per unordered pair.
	 * </p>
	 *
	 * <p>
	 * For R3 (X==&gt;NOT(Y)): needs supp(X AND NOT(Y)) = supp(X) - supp(X∪Y)
	 * </p>
	 * <p>
	 * For R4 (NOT(X)==&gt;Y): needs supp(NOT(X) AND Y) = supp(Y) - supp(X∪Y)
	 * </p>
	 *
	 * @return list of frequent ordered (X, Y) pairs
	 */
	private List<PairItemset> generateLP34() {
		List<PairItemset> lP34 = new ArrayList<>();
		int n = allFrequentItemsets.size();

		for (int xi = 0; xi < n; xi++) {
			int[] X = allFrequentItemsets.get(xi);
			int suppX = getSupportOfPositiveItemset(X);

			for (int yi = xi + 1; yi < n; yi++) {
				int[] Y = allFrequentItemsets.get(yi);

				if (hasOverlap(X, Y))
					continue;

				int suppY = getSupportOfPositiveItemset(Y);
				int suppXuY = getSupportViaKey(unionScratch, unionInto(unionScratch, X, Y));
				int suppXNotY = suppX - suppXuY; // supp(X AND NOT(Y))
				int suppNotXY = suppY - suppXuY; // supp(NOT(X) AND Y)

				// Emit (X, Y) if needed for R3 (suppXNotY) or R4 (suppNotXY)
				if (suppXNotY >= minsuppAbsolute || suppNotXY >= minsuppAbsolute)
					lP34.add(new PairItemset(X, Y, suppXNotY, suppNotXY));

				// Emit (Y, X) with swapped roles if needed for R3 or R4 in that direction
				if (suppNotXY >= minsuppAbsolute || suppXNotY >= minsuppAbsolute)
					lP34.add(new PairItemset(Y, X, suppNotXY, suppXNotY));
			}
		}
		return lP34;
	}

	// =========================================================
	// Rule generation
	// =========================================================

	/**
	 * Generates R1 rules (X ==&gt; Y) from frequent positive itemsets. All
	 * bipartitions of each frequent k-itemset (k &gt;= 2) are enumerated via
	 * bitmask.
	 *
	 * @throws IOException if an I/O error occurs
	 */
	private void generatePositiveRules() throws IOException {
		for (int k = 2; k < frequentPositive.size(); k++) {
			List<int[]> levelK = frequentPositive.get(k);
			if (levelK == null)
				continue;

			for (int[] lk : levelK) {
				int n = lk.length;
				int suppLk = getSupportOfPositiveItemset(lk);
				long fullMask = (1L << n) - 1L;
				int[] antBuf = new int[n];
				int[] conBuf = new int[n];

				// Enumerate all non-trivial bipartitions via bitmask
				for (long mask = 1L; mask < fullMask; mask++) {
					int antLen = 0, conLen = 0;
					for (int i = 0; i < n; i++) {
						if ((mask & (1L << i)) != 0L)
							antBuf[antLen++] = lk[i];
						else
							conBuf[conLen++] = lk[i];
					}
					int suppAnt = getSupportViaKey(antBuf, antLen);
					if (suppAnt <= 0)
						continue;

					double conf = (double) suppLk / suppAnt;
					if (conf >= minconf)
						saveRule(Arrays.copyOf(antBuf, antLen), false, Arrays.copyOf(conBuf, conLen), false, suppLk,
								conf);
				}
			}
		}
	}

	/**
	 * Generates R2 rules (NOT(X) ==&gt; NOT(Y)) from canonical frequent NOT(X)
	 * NOT(Y) pairs. Both directions are generated from each unordered pair.
	 *
	 * <p>
	 * A rule NOT(X)==&gt;NOT(Y) is valid if:
	 * </p>
	 * <ol>
	 * <li>supp(NOT(X) AND NOT(Y)) &gt;= minsup</li>
	 * <li>supp(X) &gt;= minsup and supp(Y) &gt;= minsup (guaranteed by
	 * construction)</li>
	 * <li>conf(NOT(X)==&gt;NOT(Y)) &gt;= minconf</li>
	 * <li>The rule is minimal w.r.t. the negative parts</li>
	 * </ol>
	 *
	 * @param lP3 canonical frequent NOT(X) NOT(Y) pairs
	 * @throws IOException if an I/O error occurs
	 */
	private void generateR2Rules(List<MixedItemset> lP3) throws IOException {
		for (MixedItemset mis : lP3) {
			// mis.negative = X, mis.positive = Y (both negated in the rule NOT(X)=>NOT(Y))
			int[] X = mis.negative;
			int[] Y = mis.positive;
			int suppNotXNotY = mis.support;
			int suppX = getSupportOfPositiveItemset(X);
			int suppY = getSupportOfPositiveItemset(Y);

			// Direction 1: NOT(X) ==> NOT(Y)
			int suppNotX = databaseSize - suppX;
			if (suppNotX > 0 && isMinimalNegAntR2(X, Y, suppY) && isMinimalNegConR2(X, Y, suppX)) {
				double conf = (double) suppNotXNotY / suppNotX;
				if (conf >= minconf)
					saveRule(X, true, Y, true, suppNotXNotY, conf);
			}

			// Direction 2: NOT(Y) ==> NOT(X)
			int suppNotY = databaseSize - suppY;
			if (suppNotY > 0 && isMinimalNegAntR2(Y, X, suppX) && isMinimalNegConR2(Y, X, suppY)) {
				double conf = (double) suppNotXNotY / suppNotY;
				if (conf >= minconf)
					saveRule(Y, true, X, true, suppNotXNotY, conf);
			}
		}
	}

	/**
	 * Returns true if no proper subset X' of X yields supp(NOT(X') AND NOT(Y))
	 * &gt;= minsuppAbsolute.
	 *
	 * <p>
	 * This implements the minimality condition (condition 4 of the paper) for the
	 * negative antecedent of an R2 rule.
	 * </p>
	 *
	 * @param X     antecedent itemset being negated
	 * @param Y     consequent itemset being negated
	 * @param suppY absolute support of Y
	 * @return true if the antecedent is minimal
	 */
	private boolean isMinimalNegAntR2(int[] X, int[] Y, int suppY) {
		int n = X.length;
		if (n == 1)
			return true;
		int[] xpBuf = new int[n - 1];
		int[] uBuf = new int[n - 1 + Y.length];
		for (long mask = 1L; mask < (1L << n) - 1L; mask++) {
			int xpLen = 0;
			for (int i = 0; i < n; i++)
				if ((mask & (1L << i)) != 0L)
					xpBuf[xpLen++] = X[i];
			int suppXp = getSupportViaKey(xpBuf, xpLen);
			int suppXpY = getSupportViaKey(uBuf, unionInto(uBuf, xpBuf, xpLen, Y, Y.length));
			if (databaseSize - suppXp - suppY + suppXpY >= minsuppAbsolute)
				return false;
		}
		return true;
	}

	/**
	 * Returns true if no proper subset Y' of Y yields supp(NOT(X) AND NOT(Y'))
	 * &gt;= minsuppAbsolute.
	 *
	 * <p>
	 * This implements the minimality condition (condition 4 of the paper) for the
	 * negative consequent of an R2 rule.
	 * </p>
	 *
	 * @param X     antecedent itemset being negated
	 * @param Y     consequent itemset being negated
	 * @param suppX absolute support of X
	 * @return true if the consequent is minimal
	 */
	private boolean isMinimalNegConR2(int[] X, int[] Y, int suppX) {
		int n = Y.length;
		if (n == 1)
			return true;
		int[] ypBuf = new int[n - 1];
		int[] uBuf = new int[X.length + n - 1];
		for (long mask = 1L; mask < (1L << n) - 1L; mask++) {
			int ypLen = 0;
			for (int i = 0; i < n; i++)
				if ((mask & (1L << i)) != 0L)
					ypBuf[ypLen++] = Y[i];
			int suppYp = getSupportViaKey(ypBuf, ypLen);
			int suppXYp = getSupportViaKey(uBuf, unionInto(uBuf, X, X.length, ypBuf, ypLen));
			if (databaseSize - suppX - suppYp + suppXYp >= minsuppAbsolute)
				return false;
		}
		return true;
	}

	/**
	 * Generates R3 rules (X ==&gt; NOT(Y)) from ordered frequent (X, Y) pairs.
	 *
	 * <p>
	 * A rule X==&gt;NOT(Y) is valid if supp(X AND NOT(Y)) &gt;= minsup,
	 * conf(X==&gt;NOT(Y)) &gt;= minconf, and the negative consequent is minimal.
	 * </p>
	 *
	 * @param lP34 ordered frequent (X, Y) pairs
	 * @throws IOException if an I/O error occurs
	 */
	private void generateR3Rules(List<PairItemset> lP34) throws IOException {
		for (PairItemset pair : lP34) {
			if (pair.suppXNotY < minsuppAbsolute)
				continue;
			int[] X = pair.X;
			int[] Y = pair.Y;
			if (!isMinimalNegConR3(X, Y))
				continue;
			int suppX = getSupportOfPositiveItemset(X);
			if (suppX <= 0)
				continue;
			double conf = (double) pair.suppXNotY / suppX;
			if (conf >= minconf)
				saveRule(X, false, Y, true, pair.suppXNotY, conf);
		}
	}

	/**
	 * Returns true if no proper subset Y' of Y yields supp(X AND NOT(Y')) &gt;=
	 * minsuppAbsolute.
	 *
	 * <p>
	 * This implements the minimality condition (condition 4 of the paper) for the
	 * negative consequent of an R3 rule.
	 * </p>
	 *
	 * @param X positive antecedent
	 * @param Y consequent being negated
	 * @return true if the consequent is minimal
	 */
	private boolean isMinimalNegConR3(int[] X, int[] Y) {
		int n = Y.length;
		if (n == 1)
			return true;
		int suppX = getSupportOfPositiveItemset(X);
		int[] ypBuf = new int[n - 1];
		int[] uBuf = new int[X.length + n - 1];
		for (long mask = 1L; mask < (1L << n) - 1L; mask++) {
			int ypLen = 0;
			for (int i = 0; i < n; i++)
				if ((mask & (1L << i)) != 0L)
					ypBuf[ypLen++] = Y[i];
			int suppXYp = getSupportViaKey(uBuf, unionInto(uBuf, X, X.length, ypBuf, ypLen));
			if (suppX - suppXYp >= minsuppAbsolute)
				return false;
		}
		return true;
	}

	/**
	 * Generates R4 rules (NOT(X) ==&gt; Y) from ordered frequent (X, Y) pairs.
	 *
	 * <p>
	 * A rule NOT(X)==&gt;Y is valid if supp(NOT(X) AND Y) &gt;= minsup,
	 * conf(NOT(X)==&gt;Y) &gt;= minconf, and the negative antecedent is minimal.
	 * </p>
	 *
	 * @param lP34 ordered frequent (X, Y) pairs
	 * @throws IOException if an I/O error occurs
	 */
	private void generateR4Rules(List<PairItemset> lP34) throws IOException {
		for (PairItemset pair : lP34) {
			if (pair.suppNotXY < minsuppAbsolute)
				continue;
			int[] X = pair.X;
			int[] Y = pair.Y;
			if (!isMinimalNegAntR4(X, Y))
				continue;
			int suppNotX = databaseSize - getSupportOfPositiveItemset(X);
			if (suppNotX <= 0)
				continue;
			double conf = (double) pair.suppNotXY / suppNotX;
			if (conf >= minconf)
				saveRule(X, true, Y, false, pair.suppNotXY, conf);
		}
	}

	/**
	 * Returns true if no proper subset X' of X yields supp(NOT(X') AND Y) &gt;=
	 * minsuppAbsolute.
	 *
	 * <p>
	 * This implements the minimality condition (condition 4 of the paper) for the
	 * negative antecedent of an R4 rule.
	 * </p>
	 *
	 * @param X antecedent being negated
	 * @param Y positive consequent
	 * @return true if the antecedent is minimal
	 */
	private boolean isMinimalNegAntR4(int[] X, int[] Y) {
		int n = X.length;
		if (n == 1)
			return true;
		int suppY = getSupportOfPositiveItemset(Y);
		int[] xpBuf = new int[n - 1];
		int[] uBuf = new int[n - 1 + Y.length];
		for (long mask = 1L; mask < (1L << n) - 1L; mask++) {
			int xpLen = 0;
			for (int i = 0; i < n; i++)
				if ((mask & (1L << i)) != 0L)
					xpBuf[xpLen++] = X[i];
			int suppXpY = getSupportViaKey(uBuf, unionInto(uBuf, xpBuf, xpLen, Y, Y.length));
			if (suppY - suppXpY >= minsuppAbsolute)
				return false;
		}
		return true;
	}

	/**
	 * Saves a discovered rule to the output file or in-memory container. Recoded
	 * item ids are converted to original ids before output.
	 *
	 * @param ant        recoded antecedent items
	 * @param antNegated true if the antecedent is negated
	 * @param con        recoded consequent items
	 * @param conNegated true if the consequent is negated
	 * @param support    absolute support
	 * @param conf       confidence
	 * @throws IOException if an I/O error occurs
	 */
	private void saveRule(int[] ant, boolean antNegated, int[] con, boolean conNegated, int support, double conf)
			throws IOException {
		ruleCount++;
		int[] antOrig = toOriginalIds(ant);
		int[] conOrig = toOriginalIds(con);

		if (writer != null) {
			outputBuffer.setLength(0);
			appendSideToBuffer(outputBuffer, antOrig, antNegated);
			outputBuffer.append(" ==> ");
			appendSideToBuffer(outputBuffer, conOrig, conNegated);
			outputBuffer.append(" #SUP: ").append(support);
			outputBuffer.append(" #CONF: ").append(conf);
			writer.write(outputBuffer.toString());
			writer.newLine();
		} else {
			rules.addRule(new RulePNAR(antOrig, antNegated, conOrig, conNegated, support, conf));
		}
	}

	/**
	 * Converts recoded item ids to original item ids, sorted ascending.
	 *
	 * @param recodedItems recoded item ids
	 * @return original item ids sorted ascending
	 */
	private int[] toOriginalIds(int[] recodedItems) {
		int[] result = new int[recodedItems.length];
		for (int i = 0; i < recodedItems.length; i++)
			result[i] = recodedToOriginal[recodedItems[i]];
		Arrays.sort(result);
		return result;
	}

	/**
	 * Appends one side of a rule to the StringBuilder. Negated items are printed as
	 * NOT(...).
	 *
	 * @param sb      target buffer
	 * @param items   original-id items
	 * @param negated true if this side is negated
	 */
	private void appendSideToBuffer(StringBuilder sb, int[] items, boolean negated) {
		if (negated)
			sb.append("NOT(");
		for (int i = 0; i < items.length; i++) {
			if (i > 0)
				sb.append(' ');
			sb.append(items[i]);
		}
		if (negated)
			sb.append(')');
	}

	/**
	 * Returns the absolute support of a positive itemset (recoded ids). Uses a
	 * direct array lookup for singletons and the HashMap cache or tidlist
	 * intersection for larger itemsets.
	 *
	 * @param itemset sorted recoded itemset
	 * @return absolute support count
	 */
	int getSupportOfPositiveItemset(int[] itemset) {
		if (itemset.length == 1)
			return singletonSupport[itemset[0]];
		IntArrayKey k = new IntArrayKey(itemset);
		Integer cached = mapItemsetSupportCache.get(k);
		if (cached != null)
			return cached;
		int count = computeSupportViaIndex(itemset);
		mapItemsetSupportCache.put(k, count);
		return count;
	}

	/**
	 * Returns the support for the first {@code len} elements of a scratch array.
	 * Uses a direct array lookup for singletons and the HashMap cache or tidlist
	 * intersection for larger itemsets.
	 *
	 * @param scratch sorted source array
	 * @param len     number of valid elements
	 * @return absolute support count
	 */
	private int getSupportViaKey(int[] scratch, int len) {
		if (len == 1)
			return singletonSupport[scratch[0]];
		IntArrayKey key = new IntArrayKey(scratch, len);
		Integer cached = mapItemsetSupportCache.get(key);
		if (cached != null)
			return cached;
		int count = computeSupportViaIndex(key.items);
		mapItemsetSupportCache.put(key, count);
		return count;
	}

	/**
	 * Computes weighted support of an arbitrary itemset via tidlist intersection,
	 * using ping-pong buffers to avoid allocation. Seeds from the item with the
	 * shortest tidlist to minimise intersection work.
	 *
	 * <p>
	 * Algorithm: Find the item with the shortest tidlist (fewest transactions),
	 * copy it to a work buffer, then intersect it with each other item's tidlist in
	 * sequence using two alternating buffers (ping-pong) to avoid allocating a new
	 * array on each intersection. After all intersections, sum the transaction
	 * weights of the remaining tids.
	 * </p>
	 *
	 * @param itemset sorted recoded itemset
	 * @return absolute weighted support
	 */
	private int computeSupportViaIndex(int[] itemset) {
		int[] seedTidList = null;
		int seedItem = -1;
		int minLen = Integer.MAX_VALUE;

		// Find the item with the shortest tidlist to minimise intersection work
		for (int item : itemset) {
			int[] tl = invertedIndex[item];
			if (tl.length == 0)
				return 0;
			if (tl.length < minLen) {
				minLen = tl.length;
				seedTidList = tl;
				seedItem = item;
			}
		}

		// Copy the smallest tidlist into the first ping-pong buffer
		System.arraycopy(seedTidList, 0, tidListCurrent, 0, minLen);
		int curLen = minLen;
		int[] cur = tidListCurrent;
		int[] other = tidListOther;

		// Intersect with every other item's tidlist using two-pointer merge
		for (int item : itemset) {
			if (item == seedItem)
				continue;
			int[] tl = invertedIndex[item];
			int i = 0, j = 0, k = 0;
			while (i < curLen && j < tl.length) {
				int a = cur[i], b = tl[j];
				if (a < b)
					i++;
				else if (a > b)
					j++;
				else {
					other[k++] = a;
					i++;
					j++;
				}
			}
			curLen = k;
			// Swap ping-pong buffers
			int[] tmp = cur;
			cur = other;
			other = tmp;
		}

		// Accumulate weighted support over the final intersection
		int support = 0;
		for (int i = 0; i < curLen; i++)
			support += transactionWeights[cur[i]];
		return support;
	}

	/**
	 * Writes the sorted union of arrays {@code a} and {@code b} into {@code dest}.
	 * {@code dest} must have length &gt;= a.length + b.length.
	 *
	 * <p>
	 * Delegates to the full version, passing the full lengths of both arrays.
	 * </p>
	 *
	 * @param dest destination array
	 * @param a    first sorted array
	 * @param b    second sorted array
	 * @return number of distinct elements written
	 */
	static int unionInto(int[] dest, int[] a, int[] b) {
		return unionInto(dest, a, a.length, b, b.length);
	}

	/**
	 * Writes the sorted union of the first {@code aLen} elements of {@code a} and
	 * the first {@code bLen} elements of {@code b} into {@code dest}. {@code dest}
	 * must have length &gt;= aLen + bLen.
	 *
	 * <p>
	 * Algorithm: Two-pointer sorted merge. Scan both arrays left-to-right, copying
	 * the smaller element each step. On equality, copy once (union, not multiset).
	 * At the end, copy any remaining elements from the non-empty array.
	 * </p>
	 *
	 * @param dest destination array
	 * @param a    first source array
	 * @param aLen valid elements in a
	 * @param b    second source array
	 * @param bLen valid elements in b
	 * @return number of distinct elements written to dest
	 */
	static int unionInto(int[] dest, int[] a, int aLen, int[] b, int bLen) {
		int i = 0, j = 0, k = 0;
		while (i < aLen && j < bLen) {
			int av = a[i], bv = b[j];
			if (av < bv) {
				dest[k++] = av;
				i++;
			} else if (av > bv) {
				dest[k++] = bv;
				j++;
			} else {
				dest[k++] = av;
				i++;
				j++;
			}
		}
		while (i < aLen)
			dest[k++] = a[i++];
		while (j < bLen)
			dest[k++] = b[j++];
		return k;
	}

	/**
	 * Returns true if two sorted int arrays share at least one common element.
	 *
	 * <p>
	 * Algorithm: Two-pointer scan, similar to set intersection but stops as soon as
	 * a common element is found (early exit for efficiency).
	 * </p>
	 *
	 * @param a first sorted array
	 * @param b second sorted array
	 * @return true if they share at least one element
	 */
	static boolean hasOverlap(int[] a, int[] b) {
		int i = 0, j = 0;
		while (i < a.length && j < b.length) {
			if (a[i] < b[j])
				i++;
			else if (a[i] > b[j])
				j++;
			else
				return true;
		}
		return false;
	}

	/**
	 * Returns the number of transactions in the database.
	 *
	 * @return database size
	 */
	public int getDatabaseSize() {
		return databaseSize;
	}

}