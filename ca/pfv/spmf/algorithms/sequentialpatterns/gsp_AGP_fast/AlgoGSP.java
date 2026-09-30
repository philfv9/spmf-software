package ca.pfv.spmf.algorithms.sequentialpatterns.gsp_AGP_fast;
/*
 * Copyright Antonio Gomariz Peñalver 2013
 * 
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SPMF is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPMF.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Do not remove copyright and license information
 */
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import ca.pfv.spmf.datastructures.collections.map.AMapIntToObject;
import ca.pfv.spmf.tools.MemoryLogger;

/**
 * GSP algorithm implementation. Srikant and Agrawal 1996.
 *
 * Items flowing through the mining process are renamed dense ids (ascending by
 * support); they are translated back to original values only when a level is
 * committed to the output structure. See SequenceDatabase for details.
 *
 * Progressive database projection (computeActiveItemMask +
 * projectToActiveItems) shrinks the database between levels so that later, more
 * expensive scans work over less data.
 *
 * Two processing flows are supported, selected once per run from
 * SequenceDatabase.isPureItemSequences(): - itemset flow: general case,
 * itemsets may contain more than one item per itemset. - item-sequence flow:
 * every itemset in the database has exactly one item (the common case for
 * public datasets).
 * 
 * @author Antonio Gomariz Peñalver,  Philippe Fournier-Viger (some optimizations)
 */
public class AlgoGSP {

	/** Relative minimum support */
	protected double minSupRelative;
	/** Absolkute minimum support */
	protected int minSupAbsolute;
	/** Patterns */
	protected Sequences patterns;
	/** Start time of the last run */
	protected long start;
	/** End time of the last run */
	protected long end;

	/** Flag to remember if sorted */
	private boolean isSorted;

	/** Number of frequent pattenrs found in the last run */
	private int numberOfFrequentPatterns;

	/** Flag true if only one item per itemset */
	private boolean itemSequenceMode;

	/** Writer object to save patterns to file */
	BufferedWriter writer = null;

	/** If true, sequence identifiers are written to file */
	boolean outputSequenceIdentifiers = false;

	/** Minimum number of items that a kept pattern must contain */
	private int minimumPatternLength = 0;

	/** Maximum number of items that a kept pattern can contain */
	private int maximumPatternLength = Integer.MAX_VALUE;

	/**
	 * Creates a new instance of the GSP algorithm with the given relative minimum
	 * support threshold.
	 *
	 * @param minSupRelative the minimum support threshold expressed as a fraction
	 *                       of the number of sequences in the database (between 0
	 *                       and 1)
	 */
	public AlgoGSP(double minSupRelative) {
		this.minSupRelative = minSupRelative;
		this.isSorted = false;
	}

	/**
	 * Sets the minimum number of items that a pattern must contain to be kept.
	 *
	 * @param minimumPatternLength the minimum pattern length (default 0)
	 */
	public void setMinimumPatternLength(int minimumPatternLength) {
		this.minimumPatternLength = minimumPatternLength;
	}

	/**
	 * Sets the maximum number of items that a pattern can contain to be kept.
	 *
	 * @param maximumPatternLength the maximum pattern length (default infinity)
	 */
	public void setMaximumPatternLength(int maximumPatternLength) {
		this.maximumPatternLength = maximumPatternLength;
	}

	/**
	 * Runs the GSP algorithm on the given sequence database.
	 *
	 * @param database                  the input sequence database
	 * @param keepPatterns              if true, discovered patterns are kept in
	 *                                  memory (ignored when writing to a file)
	 * @param verbose                   if true, progress information is printed to
	 *                                  the standard output
	 * @param outputFilePath            path of the file where patterns should be
	 *                                  written, or null to keep them in memory
	 * @param outputSequenceIdentifiers if true, the sequence identifiers where each
	 *                                  pattern occurs are included in the output
	 * @return the structure holding the frequent sequential patterns (empty if
	 *         patterns were streamed to a file instead)
	 * @throws IOException if an error occurs while writing to the output file
	 */
	public Sequences runAlgorithm(SequenceDatabase database, boolean keepPatterns, boolean verbose,
			String outputFilePath, boolean outputSequenceIdentifiers) throws IOException {
		this.outputSequenceIdentifiers = outputSequenceIdentifiers;
		clear();

		patterns = new Sequences("FREQUENT SEQUENTIAL PATTERNS");

		this.minSupAbsolute = (int) Math.ceil(minSupRelative * database.size() - 1e-10);
		if (this.minSupAbsolute == 0) {
			this.minSupAbsolute = 1;
		}

		MemoryLogger.getInstance().reset();
		start = System.currentTimeMillis();

		try {
			if (outputFilePath != null) {
				writer = new BufferedWriter(new FileWriter(outputFilePath));
				writer.write("@FILETYPE=\"Frequent sequential patterns\"");
				writer.newLine();
				writer.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
				writer.newLine();
			}
			runGsp(database, keepPatterns, verbose);
		} finally {
			end = System.currentTimeMillis();
			if (writer != null) {
				writer.close();
			}
		}

		return patterns;
	}

	/**
	 * Executes the main level-wise GSP mining loop: starting from frequent
	 * 1-sequences, repeatedly generates candidates of increasing length, counts
	 * their support, projects the database to remaining active items, and commits
	 * each level's frequent patterns to the output as soon as that level is
	 * complete.
	 *
	 * @param database     the input sequence database (mutated in place via
	 *                     projection)
	 * @param keepPatterns if true, discovered patterns are kept in memory
	 * @param verbose      if true, progress information is printed
	 * @throws IOException if an error occurs while writing a level to the output
	 *                     file
	 */
	protected void runGsp(SequenceDatabase database, boolean keepPatterns, boolean verbose) throws IOException {

		itemSequenceMode = database.isPureItemSequences();

		// When patterns are neither kept in memory nor streamed to a file
		// (keepPatterns == false and no output file), remapping every
		// discovered pattern back to its original item ids and inserting it
		// into `patterns` would be pure wasted work: it is thrown away again
		// right after via finalizeLevel(). This flag lets that whole
		// remap/store step be skipped in that case; the observable result is
		// identical either way, since `patterns` ends up empty.
		boolean shouldStorePatterns = keepPatterns || writer != null;

		AMapIntToObject<Set<Pattern>> indexationMap = new AMapIntToObject<>();
		int[] itemMapping = database.getItemMapping();

		List<Pattern> frequentItems = database.frequentItems();

		// Keep only the length-1 patterns respecting the length constraints for output
		List<Pattern> outputFrequentItems = filterByLength(frequentItems);
		numberOfFrequentPatterns += outputFrequentItems.size();
		if (shouldStorePatterns) {
			patterns.addSequences(remapForOutput(outputFrequentItems, itemMapping), 1);
		}
		// Level 1 is now complete: write it to file (or free it) right away
		finalizeLevel(1, keepPatterns);

		Set<Pattern> frequentSet = new LinkedHashSet<>((int) (frequentItems.size() / 0.75f) + 1);
		frequentSet.addAll(frequentItems);

		if (itemMapping != null) {
			database.projectToActiveItems(computeActiveItemMask(frequentSet, itemMapping.length), 2);
		}

		int k = 1;
		while (frequentSet != null && !frequentSet.isEmpty()) {
			k++;

			// Stop growing patterns once the maximum pattern length is reached
			if (k > maximumPatternLength) {
				break;
			}

			if (verbose) {
				System.out.println("k=" + k);
				System.out.println("generating candidates...");
			}

			List<Pattern> candidateSet = generateCandidates(frequentSet, k, minSupAbsolute, indexationMap);
			frequentSet = null;
			if (candidateSet == null) {
				break;
			}

			if (verbose) {
				System.out.println(candidateSet.size() + "  Candidates have been created!");
				System.out.println("checking frequency...");
			}

			MemoryLogger.getInstance().checkMemory();

			frequentSet = countSupport(database, candidateSet, minSupAbsolute, indexationMap);

			if (verbose) {
				System.out.println(frequentSet.size() + " frequent patterns\n");
			}

			MemoryLogger.getInstance().checkMemory();

			// Keep only the patterns of this level respecting the length constraints
			List<Pattern> outputFrequentSet = filterByLength(frequentSet);
			numberOfFrequentPatterns += outputFrequentSet.size();

			if (itemMapping != null && !frequentSet.isEmpty()) {
				database.projectToActiveItems(computeActiveItemMask(frequentSet, itemMapping.length), k + 1);
			}

			if (shouldStorePatterns) {
				patterns.addSequences(remapForOutput(outputFrequentSet, itemMapping), k);
			}

			// This level is now complete: write it to file (or free it) right away
			if (!frequentSet.isEmpty()) {
				finalizeLevel(k, keepPatterns);
			}
		}

		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * Writes the given level to the output file if a writer is set, or frees it
	 * from memory if patterns are not being kept. Called as soon as a level is
	 * fully computed, so the level identifier passed to the underlying storage
	 * always matches the level that was just added, regardless of why the mining
	 * loop later stops.
	 *
	 * @param level        the level to write and/or free
	 * @param keepPatterns if true and no writer is set, the level is kept in
	 *                     memory instead of being freed
	 * @throws IOException if an error occurs while writing to the output file
	 */
	private void finalizeLevel(int level, boolean keepPatterns) throws IOException {
		if (writer != null) {
			writeLevelToFile(level);
		} else if (!keepPatterns) {
			patterns.delete(level);
		}
	}

	// -----------------------------------------------------------------------
	// Active-item mask
	// -----------------------------------------------------------------------

	/**
	 * Builds a boolean mask marking every item that appears in at least one pattern
	 * of frequentSet, so the database can be pruned of everything else.
	 *
	 * @param frequentSet the frequent patterns whose items must be marked active
	 * @param numItems    the size of the item id space (mask array length)
	 * @return a boolean array where index i is true if item i occurs in some
	 *         pattern of frequentSet
	 */
	private static boolean[] computeActiveItemMask(Collection<Pattern> frequentSet, int numItems) {
		boolean[] active = new boolean[numItems];
		for (Pattern pattern : frequentSet) {
			int size = pattern.size();
			for (int i = 0; i < size; i++) {
				active[pattern.getItem(i)] = true;
			}
		}
		return active;
	}

	/**
	 * Keeps only the patterns whose length lies within the configured minimum and
	 * maximum pattern length bounds.
	 *
	 * @param source the patterns to filter
	 * @return a new list with only the patterns respecting the length bounds
	 */
	private List<Pattern> filterByLength(Collection<Pattern> source) {
		List<Pattern> filtered = new ArrayList<>(source.size());
		for (Pattern pattern : source) {
			int size = pattern.size(); // number of items contained in the pattern
			if (size >= minimumPatternLength && size <= maximumPatternLength) {
				filtered.add(pattern);
			}
		}
		return filtered;
	}

	/**
	 * Translates every pattern's items from internal dense ids back to their
	 * original values, ready for output.
	 *
	 * @param source      the patterns to translate
	 * @param itemMapping the renamed-id -> original-item mapping, or null if no
	 *                    renaming was applied (in which case the patterns are
	 *                    simply copied as-is)
	 * @return a new list with the (possibly) remapped patterns
	 */
	private static List<Pattern> remapForOutput(Collection<Pattern> source, int[] itemMapping) {
		if (itemMapping == null) {
			return new ArrayList<>(source);
		}
		List<Pattern> result = new ArrayList<>(source.size());
		for (Pattern pattern : source) {
			result.add(pattern.remapItems(itemMapping));
		}
		return result;
	}

	/**
	 * Generates the candidate k-sequences from the frequent (k-1)-sequences,
	 * applying the appropriate join and pruning strategy depending on k and the
	 * current matching mode (itemset flow vs. item-sequence flow).
	 *
	 * @param frequentSet        the frequent patterns of length k-1
	 * @param k                  the length of the candidates to generate
	 * @param minSupportAbsolute the absolute minimum support threshold
	 * @param indexationMap      index of frequent (k-1)-patterns by their first
	 *                           item, used to speed up the join for k &gt; 2
	 * @return the list of generated candidates, or null if none were produced
	 */
	private List<Pattern> generateCandidates(Set<Pattern> frequentSet, int k, int minSupportAbsolute,
			AMapIntToObject<Set<Pattern>> indexationMap) {

		List<Pattern> prunedCandidates;
		BitSet scratch = new BitSet();

		if (k > 2) {
			List<Pattern> candidateSet = new ArrayList<>(frequentSet.size());
			for (Pattern frequentPattern1 : frequentSet) {
				int currentItem = frequentPattern1.getItem(1);
				Set<Pattern> matching = indexationMap.get(currentItem);
				if (matching != null) {
					for (Pattern frequentPattern2 : matching) {
						Pattern candidate = itemSequenceMode
								? generateCandidateItemsOnly(frequentPattern1, frequentPattern2, minSupportAbsolute,
										scratch)
								: generateCandidate(frequentPattern1, frequentPattern2, minSupportAbsolute, scratch);
						if (candidate != null) {
							candidateSet.add(candidate);
						}
					}
				}
			}

			if (!candidateSet.isEmpty()) {
				prunedCandidates = prunedSubset(candidateSet, frequentSet);
			} else {
				return null;
			}
		} else {
			// k == 2: combine every pair of frequent 1-sequences
			List<Pattern> frequentList = new ArrayList<>(frequentSet);
			int size = frequentList.size();
			// Rough capacity hint (most pairs yield 1-3 candidates) to reduce
			// ArrayList resize churn without over-allocating for large inputs.
			prunedCandidates = new ArrayList<>(Math.max(16, size * 2));
			for (int i = 0; i < size; i++) {
				Pattern frequentPattern1 = frequentList.get(i);
				for (int j = i; j < size; j++) {
					if (itemSequenceMode) {
						generateSize2CandidatesItemsOnly(frequentPattern1, frequentList.get(j), minSupportAbsolute,
								scratch, prunedCandidates);
					} else {
						generateSize2Candidates(frequentPattern1, frequentList.get(j), minSupportAbsolute, scratch,
								prunedCandidates);
					}
				}
			}
		}

		if (prunedCandidates.isEmpty()) {
			return null;
		}
		return prunedCandidates;
	}

	/**
	 * Computes the intersection of two sequence-id sets, returning the scratch
	 * BitSet (filled with the result) if cardinality >= minSupport, or null
	 * otherwise. Stops as soon as the intersection is provably too small, driven by
	 * the smaller of the two sets to detect failure sooner.
	 *
	 * @param setA       the first occurrence-set
	 * @param cardA      the cardinality of setA
	 * @param setB       the second occurrence-set
	 * @param cardB      the cardinality of setB
	 * @param minSupport the minimum required intersection size
	 * @param scratch    a reusable BitSet that will hold the intersection result
	 * @return scratch filled with the intersection if it meets minSupport, or null
	 *         if the intersection is (or is guaranteed to be) too small
	 */
	private static BitSet intersectAtLeast(BitSet setA, int cardA, BitSet setB, int cardB, int minSupport,
			BitSet scratch) {
		BitSet driver = setA;
		BitSet other = setB;
		int remaining = cardA;
		if (cardB < cardA) {
			driver = setB;
			other = setA;
			remaining = cardB;
		}
		if (remaining < minSupport) {
			return null;
		}

		scratch.clear();
		int matched = 0;
		for (int bit = driver.nextSetBit(0); bit >= 0; bit = driver.nextSetBit(bit + 1)) {
			remaining--;
			if (other.get(bit)) {
				scratch.set(bit);
				matched++;
			}
			if (matched + remaining < minSupport) {
				return null;
			}
		}
		return matched >= minSupport ? scratch : null;
	}

	/**
	 * Attempts to join pattern1 (prefix) and pattern2 (suffix) into a new
	 * k-candidate. Returns null if the suffix-prefix overlap does not match, or if
	 * the intersection of their occurrence sets falls below minSupport. Used for
	 * the itemset flow, where the same-itemset relation must line up.
	 *
	 * @param pattern1   the prefix (frequent) pattern
	 * @param pattern2   the suffix (frequent) pattern
	 * @param minSupport the absolute minimum support threshold
	 * @param scratch    a reusable BitSet used for the occurrence-set intersection
	 * @return the joined candidate pattern, or null if the join is not valid or
	 *         does not meet minSupport
	 */
	private Pattern generateCandidate(Pattern pattern1, Pattern pattern2, int minSupport, BitSet scratch) {
		int size1 = pattern1.size();
		for (int i = 0; i < size1 - 1; i++) {
			int item1 = pattern1.getItem(i + 1);
			int item2 = pattern2.getItem(i);
			if (item1 != item2) {
				return null;
			}
			if (i > 0 && pattern1.isEqualRelation(i + 1) != pattern2.isEqualRelation(i)) {
				return null;
			}
		}

		BitSet intersection = intersectAtLeast(pattern1.getAppearingIn(), pattern1.getSupport(),
				pattern2.getAppearingIn(), pattern2.getSupport(), minSupport, scratch);
		if (intersection == null) {
			return null;
		}

		Pattern newPattern = pattern1.clonePattern();
		newPattern.add(pattern2.getLastItem(), pattern2.isLastEqualRelation());
		newPattern.setPossibleSequences((BitSet) intersection.clone());
		return newPattern;
	}

	/**
	 * Same join as generateCandidate(), specialized for the item-sequence flow
	 * where every relation is "strictly after", so the relation compatibility check
	 * can be skipped entirely.
	 *
	 * @param pattern1   the prefix (frequent) pattern
	 * @param pattern2   the suffix (frequent) pattern
	 * @param minSupport the absolute minimum support threshold
	 * @param scratch    a reusable BitSet used for the occurrence-set intersection
	 * @return the joined candidate pattern, or null if the join is not valid or
	 *         does not meet minSupport
	 */
	private Pattern generateCandidateItemsOnly(Pattern pattern1, Pattern pattern2, int minSupport, BitSet scratch) {
		int size1 = pattern1.size();
		for (int i = 0; i < size1 - 1; i++) {
			if (pattern1.getItem(i + 1) != pattern2.getItem(i)) {
				return null;
			}
		}

		BitSet intersection = intersectAtLeast(pattern1.getAppearingIn(), pattern1.getSupport(),
				pattern2.getAppearingIn(), pattern2.getSupport(), minSupport, scratch);
		if (intersection == null) {
			return null;
		}

		Pattern newPattern = pattern1.clonePattern();
		newPattern.add(pattern2.getLastItem(), false);
		newPattern.setPossibleSequences((BitSet) intersection.clone());
		return newPattern;
	}

	/**
	 * Fills outSubpattern with the pattern obtained by removing element at index
	 * from extension. The output pattern is reset before being populated so a
	 * single instance can be reused across calls.
	 *
	 * @param extension     the pattern to remove one element from
	 * @param index         the index of the element to remove
	 * @param outSubpattern a reusable Pattern instance that receives the result
	 */
	private void fillSubpattern(Pattern extension, int index, Pattern outSubpattern) {
		outSubpattern.reset();
		int size = extension.size();
		boolean overrideNextRelation = false;
		int nextIndex = index + 1;

		for (int i = 0; i < size; i++) {
			if (i == index) {
				if (index == 0 || !extension.isEqualRelation(i)) {
					overrideNextRelation = true;
				}
				continue;
			}
			if (i == nextIndex) {
				boolean relation = overrideNextRelation ? false : extension.isEqualRelation(i);
				outSubpattern.add(extension.getItem(i), relation);
			} else {
				outSubpattern.add(extension.getItem(i), extension.isEqualRelation(i));
			}
		}
	}

	/**
	 * Generates all size-2 candidates from two frequent 1-sequences, appending
	 * survivors directly to output. Skips all three if the occurrence-set
	 * intersection falls below minSupport. Used for the itemset flow, where a
	 * same-itemset combination of the two items is also a valid candidate.
	 *
	 * @param pat1       the first frequent 1-sequence
	 * @param pat2       the second frequent 1-sequence
	 * @param minSupport the absolute minimum support threshold
	 * @param scratch    a reusable BitSet used for the occurrence-set intersection
	 * @param output     the list that generated candidates are appended to
	 */
	private void generateSize2Candidates(Pattern pat1, Pattern pat2, int minSupport, BitSet scratch,
			List<Pattern> output) {
		BitSet intersection = intersectAtLeast(pat1.getAppearingIn(), pat1.getSupport(), pat2.getAppearingIn(),
				pat2.getSupport(), minSupport, scratch);
		if (intersection == null) {
			return;
		}

		int item1 = pat1.getItem(0);
		int item2 = pat2.getItem(0);

		// All candidates from this pair share the same possible-sequence set.
		BitSet possibleSequences = (BitSet) intersection.clone();

		Pattern candidate1 = new Pattern(item1, item2, false);
		candidate1.setPossibleSequences(possibleSequences);
		output.add(candidate1);

		if (item1 != item2) {
			Pattern candidate2 = new Pattern(item2, item1, false);
			candidate2.setPossibleSequences(possibleSequences);
			output.add(candidate2);

			int smallest = Math.min(item1, item2);
			int greatest = Math.max(item1, item2);
			Pattern candidate3 = new Pattern(smallest, greatest, true);
			candidate3.setPossibleSequences(possibleSequences);
			output.add(candidate3);
		}
	}

	/**
	 * Same as generateSize2Candidates(), specialized for the item-sequence flow:
	 * since every itemset has exactly one item, a same-itemset combination of two
	 * distinct items could never match, so it is not generated.
	 *
	 * @param pat1       the first frequent 1-sequence
	 * @param pat2       the second frequent 1-sequence
	 * @param minSupport the absolute minimum support threshold
	 * @param scratch    a reusable BitSet used for the occurrence-set intersection
	 * @param output     the list that generated candidates are appended to
	 */
	private void generateSize2CandidatesItemsOnly(Pattern pat1, Pattern pat2, int minSupport, BitSet scratch,
			List<Pattern> output) {
		BitSet intersection = intersectAtLeast(pat1.getAppearingIn(), pat1.getSupport(), pat2.getAppearingIn(),
				pat2.getSupport(), minSupport, scratch);
		if (intersection == null) {
			return;
		}

		int item1 = pat1.getItem(0);
		int item2 = pat2.getItem(0);

		BitSet possibleSequences = (BitSet) intersection.clone();

		Pattern candidate1 = new Pattern(item1, item2, false);
		candidate1.setPossibleSequences(possibleSequences);
		output.add(candidate1);

		if (item1 != item2) {
			Pattern candidate2 = new Pattern(item2, item1, false);
			candidate2.setPossibleSequences(possibleSequences);
			output.add(candidate2);
		}
	}

	/**
	 * Returns the subset of candidateSet whose every internal subpattern (obtained
	 * by removing one element not at position 0 or size-1) appears in frequentSet.
	 * Subpatterns at the two boundary positions are skipped because they are the
	 * exact prefix/suffix used to build the candidate and are already known to be
	 * frequent.
	 *
	 * @param candidateSet the candidates to filter
	 * @param frequentSet  the frequent patterns of the previous level, used to
	 *                     check that every internal subpattern is frequent
	 * @return the subset of candidateSet that passes the Apriori-style prune
	 */
	private List<Pattern> prunedSubset(List<Pattern> candidateSet, Set<Pattern> frequentSet) {
		List<Pattern> candidatePatterns = new ArrayList<>(candidateSet.size());
		Pattern scratch = new Pattern();

		for (Pattern candidate : candidateSet) {
			boolean isInfrequent = false;
			int size = candidate.size();

			for (int i = 1; i <= size - 2; i++) {
				fillSubpattern(candidate, i, scratch);
				if (!frequentSet.contains(scratch)) {
					isInfrequent = true;
					break;
				}
			}

			if (!isInfrequent) {
				candidatePatterns.add(candidate);
			}
		}
		return candidatePatterns;
	}

	/**
	 * Counts, for every candidate, in which sequences of the database it occurs,
	 * and keeps only the candidates meeting minSupportAbsolute. Frequent survivors
	 * are also indexed by their first item into indexationMap, to speed up the next
	 * level's candidate generation.
	 *
	 * @param database           the sequence database to scan
	 * @param candidateSet       the candidates whose support must be counted
	 * @param minSupportAbsolute the absolute minimum support threshold
	 * @param indexationMap      index (cleared and repopulated here) mapping an
	 *                           item to the frequent candidates starting with it
	 * @return the set of candidates that are frequent
	 */
	private Set<Pattern> countSupport(SequenceDatabase database, List<Pattern> candidateSet, int minSupportAbsolute,
			AMapIntToObject<Set<Pattern>> indexationMap) {
		indexationMap.clear();

		if (candidateSet == null || candidateSet.isEmpty()) {
			return new LinkedHashSet<>();
		}

		List<Sequence> sequences = database.getSequences();
		Set<Pattern> result = new LinkedHashSet<>((int) (candidateSet.size() / 0.75f) + 1);

		int candidateCount = candidateSet.size();
		for (int i = 0; i < candidateCount; i++) {
			Pattern candidate = candidateSet.get(i);
			BitSet possibleSequences = candidate.getPossibleSequences();

			for (int seqId = possibleSequences.nextSetBit(0); seqId >= 0; seqId = possibleSequences
					.nextSetBit(seqId + 1)) {
				boolean matched = itemSequenceMode ? matchInSequenceItemsOnly(candidate, sequences.get(seqId))
						: matchInSequence(candidate, sequences.get(seqId));
				if (matched) {
					// seqIds here are guaranteed distinct (iterating set bits),
					// so the uniqueness guard in addAppearance is unnecessary.
					candidate.addAppearanceUnchecked(seqId);
				}
			}

			if (candidate.getSupport() >= minSupportAbsolute) {
				result.add(candidate);
				putInIndexationMap(candidate, indexationMap);
			}
		}

		return result;
	}

	/**
	 * Checks whether candidate occurs in sequence as a GSP subsequence.
	 *
	 * Two O(1) guards reject clearly-incompatible sequences immediately. The actual
	 * match uses the cached group boundary array from Pattern.getGroupBoundaries()
	 * so the group structure is computed once per candidate, not once per
	 * (candidate, sequence) pair.
	 *
	 * Used for the itemset flow, where itemsets may hold several items.
	 *
	 * @param candidate the candidate pattern to look for
	 * @param sequence  the database sequence to search in
	 * @return true if candidate occurs in sequence, false otherwise
	 */
	private boolean matchInSequence(Pattern candidate, Sequence sequence) {
		int k = candidate.size();
		if (sequence.getLength() < k || sequence.size() < candidate.getMinItemsetsRequired()) {
			return false;
		}

		int[] bounds = candidate.getGroupBoundaries();
		int lastItemsetIndex = -1;
		for (int g = 0; g < bounds.length - 1; g++) {
			int foundItemsetIndex = sequence.findItemsetContainingAll(candidate, bounds[g], bounds[g + 1],
					lastItemsetIndex + 1);
			if (foundItemsetIndex < 0) {
				return false;
			}
			lastItemsetIndex = foundItemsetIndex;
		}
		return true;
	}

	/**
	 * Checks whether candidate occurs in sequence as a plain, order-preserving
	 * subsequence of items. Valid only for the item-sequence flow, where every
	 * itemset in the database has exactly one item, so a sequence's flat item array
	 * already represents its temporal item order directly, and matching reduces to
	 * the classic greedy subsequence scan.
	 *
	 * @param candidate the candidate pattern to look for
	 * @param sequence  the database sequence to search in
	 * @return true if candidate occurs in sequence, false otherwise
	 */
	private boolean matchInSequenceItemsOnly(Pattern candidate, Sequence sequence) {
		int k = candidate.size();
		if (sequence.getLength() < k) {
			return false;
		}

		int[] flatItems = sequence.getFlatItems();
		int candidateIndex = 0;
		for (int p = 0; p < flatItems.length && candidateIndex < k; p++) {
			if (flatItems[p] == candidate.getItem(candidateIndex)) {
				candidateIndex++;
			}
		}
		return candidateIndex == k;
	}

	/**
	 * Indexes a frequent candidate by its first item, so that the next level's
	 * candidate generation can quickly find join partners.
	 *
	 * @param entry         the frequent pattern to index
	 * @param indexationMap the index to insert into
	 */
	private void putInIndexationMap(Pattern entry, AMapIntToObject<Set<Pattern>> indexationMap) {
		int item = entry.getItem(0);
		Set<Pattern> correspondence = indexationMap.get(item);
		if (correspondence == null) {
			correspondence = new LinkedHashSet<>();
			indexationMap.put(item, correspondence);
		}
		correspondence.add(entry);
	}

	/**
	 * Writes every pattern of the given level to the output file and then frees the
	 * level's memory. Patterns are written one at a time through the
	 * already-buffered writer rather than accumulated into one big
	 * StringBuilder/String first, so a very large level does not transiently double
	 * its own memory footprint just to be flushed out right after.
	 *
	 * @param level the level index to write and clear
	 * @throws IOException if an error occurs while writing to the file
	 */
	private void writeLevelToFile(int level) throws IOException {
		List<Pattern> levelPatterns = patterns.getLevel(level);
		if (writer != null) {
			for (Pattern seq : levelPatterns) {
				writer.write(seq.toStringToFile(outputSequenceIdentifiers));
				writer.write('\n');
			}
		}
		patterns.delete(level);
	}

	/**
	 * Builds a human-readable summary of the run (timing, pattern count, memory
	 * usage, and optionally the patterns themselves).
	 *
	 * @return the statistics report as a string
	 */
	public String printStatistics() {
		if (!isSorted) {
			patterns.sort();
			isSorted = true;
		}
		StringBuilder sb = new StringBuilder(200);
		sb.append("=============  Algorithm GSP v2.67 - STATISTICS =============\n Total time ~ ");
		sb.append(runningTime());
		sb.append(" ms\n");
		sb.append(" Frequent sequences count : ");
		sb.append(numberOfFrequentPatterns);
		sb.append('\n');
		sb.append(" Max memory (mb):");
		sb.append(MemoryLogger.getInstance().getMaxMemory());
		sb.append('\n');
		if (writer == null) {
			sb.append(patterns.toString());
		}
		sb.append("===================================================\n");
		return sb.toString();
	}

	/**
	 * Builds the SPMF-format textual representation of every kept pattern, suitable
	 * for saving to a file.
	 *
	 * @return the patterns formatted in SPMF output format
	 */
	public String printedOutputToSaveInFile() {
		if (!isSorted) {
			patterns.sort();
			isSorted = true;
		}
		return patterns.toStringToFile(outputSequenceIdentifiers);
	}

	/**
	 * Returns the total number of frequent patterns found across all levels.
	 *
	 * @return the number of frequent patterns
	 */
	public int getNumberOfFrequentPatterns() {
		return numberOfFrequentPatterns;
	}

	/**
	 * Returns the patterns held in memory, or null if they were streamed to a file
	 * instead.
	 *
	 * @return the string representation of the patterns, or null if a file writer
	 *         was used
	 */
	public String getPatterns() {
		if (writer == null) {
			return patterns.toString();
		}
		return null;
	}

	/**
	 * Returns the total running time of the last run.
	 *
	 * @return the running time in milliseconds
	 */
	public long runningTime() {
		return end - start;
	}

	/**
	 * Returns the absolute minimum support threshold used for the last run.
	 *
	 * @return the absolute minimum support (a sequence count)
	 */
	public int getMinSupAbsolut() {
		return minSupAbsolute;
	}

	/**
	 * Resets the algorithm's internal state so it can be reused for a new run. In
	 * particular, the writer reference is cleared here: without this, a later run
	 * started without an output file would otherwise inherit the previous run's
	 * already-closed BufferedWriter and misbehave.
	 */
	public void clear() {
		if (patterns != null) {
			patterns.clear();
		}
		numberOfFrequentPatterns = 0;
		isSorted = false;
		writer = null;
	}
}