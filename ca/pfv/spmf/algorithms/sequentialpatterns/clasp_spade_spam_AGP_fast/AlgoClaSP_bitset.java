package ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.idLists.IDList;
import ca.pfv.spmf.tools.MemoryLogger;

/*
 * Copyright Antonio Gomariz Peñalver 2013, modified by Philippe Fournier-Viger.
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
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 *
 * Do not remove copyright and license.
 */
/**
 * Implementation of ClaSP (Gomariz et al., 2013) on top of the vertical (bitset
 * id-list) equivalence-class search shared with CM-SPADE.
 *
 * @author agomariz (original ClaSP), adapted to the vertical/bitset
 *         architecture shared with CM-SPADE.
 */
public class AlgoClaSP_bitset extends AbstractVerticalAlgorithm {

	/** Number of join (intersection) operations performed by the last run. */
	public long joinCount;

	/** Number of closed frequent sequences found by the last run. */
	protected int numberOfFrequentClosedPatterns;

	/** If false, this class behaves exactly like plain (CM-)SPADE. */
	private final boolean findClosedPatterns;

	/**
	 * Enables the online forward-pruning optimization (single-threaded runs only).
	 */
	private final boolean executePruningMethods;

	/** Maximum number of candidate patterns a single join round can emit. */
	private static final int MAX_CANDIDATES_PER_JOIN = 3;

	/**
	 * Every frequent pattern discovered so far, wrapped with its cached closedness
	 * data.
	 */
	private List<ClosedCandidate> discoveredPatterns;

	/**
	 * Online-pruning buckets, keyed by (support, sum of appearing sequence ids).
	 */
	private Map<SupportSumKey, List<ClosedCandidate>> matchingMap;

	/**
	 * Candidates proven non-closed, either online or during the final backward
	 * pass.
	 */
	private Set<ClosedCandidate> knownNonClosed;

	/**
	 * Maps an equivalence class not yet checked by {@link #isAvoidable} to its
	 * cached candidate.
	 */
	private Map<EquivalenceClass, ClosedCandidate> pendingCandidates;

	/**
	 * Builds a ClaSP instance.
	 *
	 * @param support               minimum support, relative (0..1)
	 * @param dfs                   true for depth-first search, false for
	 *                              breadth-first
	 * @param findClosedPatterns    if false, behaves like plain (CM-)SPADE
	 * @param executePruningMethods enables the online forward-pruning optimization
	 */
	public AlgoClaSP_bitset(double support, boolean dfs, boolean findClosedPatterns, boolean executePruningMethods) {
		super(support, dfs, true);
		this.findClosedPatterns = findClosedPatterns;
		this.executePruningMethods = executePruningMethods && findClosedPatterns;
	}

	/**
	 * Builds a ClaSP instance with closed-pattern mining and online pruning both
	 * enabled.
	 *
	 * @param support minimum support, relative (0..1)
	 * @param dfs     true for depth-first search, false for breadth-first
	 */
	public AlgoClaSP_bitset(double support, boolean dfs) {
		this(support, dfs, true, true);
	}

	/**
	 * Runs ClaSP single-threaded and writes/keeps the resulting patterns.
	 *
	 * @param database                  the input sequence database
	 * @param keepPatterns              whether patterns should be output at all
	 * @param verbose                   whether to print progress information
	 * @param outputFilePath            file to write patterns to, or null to keep
	 *                                  them in memory
	 * @param outputSequenceIdentifiers whether to output the ids of sequences
	 *                                  containing each pattern
	 * @throws IOException if writing the output file fails
	 */
	public void runAlgorithm(SequenceDatabase database, boolean keepPatterns, boolean verbose, String outputFilePath,
			boolean outputSequenceIdentifiers) throws IOException {
		initializeOutput(outputFilePath, outputSequenceIdentifiers);
		computeAbsoluteMinimumSupport(database.size());
		MemoryLogger.getInstance().reset();
		start = System.currentTimeMillis();
		runClaSP(database, dfs, keepPatterns, verbose);
		end = System.currentTimeMillis();
		finishOutput();
	}

	/**
	 * Drives the single-threaded search and the final closed-pattern backward pass.
	 *
	 * @param database     the input sequence database
	 * @param dfs          true for depth-first search, false for breadth-first
	 * @param keepPatterns whether patterns should be output at all
	 * @param verbose      whether to print progress information
	 */
	protected void runClaSP(SequenceDatabase database, boolean dfs, boolean keepPatterns, boolean verbose) {
		frequentItems = database.frequentItems();
		database = null;

		this.parallelMode = false;
		this.intersectionCounter = 0;
		this.numberOfFrequentPatterns = frequentItems.size();
		this.numberOfFrequentClosedPatterns = 0;

		List<ClosedCandidate> localDiscovered = null;

		if (!findClosedPatterns) {
			if (keepPatterns) {
				savePatterns(getPatterns(frequentItems));
			}
		} else {
			knownNonClosed = Collections.newSetFromMap(new IdentityHashMap<ClosedCandidate, Boolean>());
			discoveredPatterns = new ArrayList<ClosedCandidate>();
			if (executePruningMethods) {
				matchingMap = new HashMap<SupportSumKey, List<ClosedCandidate>>();
				pendingCandidates = new IdentityHashMap<EquivalenceClass, ClosedCandidate>();
			}
			for (EquivalenceClass item : frequentItems) {
				ClosedCandidate candidate = new ClosedCandidate(item.getClassIdentifier());
				discoveredPatterns.add(candidate);
				if (executePruningMethods) {
					pendingCandidates.put(item, candidate);
				}
			}
			localDiscovered = discoveredPatterns;
		}

		EquivalenceClass rootClass = new EquivalenceClass(null, frequentItems);
		SearchContext ctx = new SearchContext(localDiscovered, keepPatterns);
		execute(rootClass, dfs, ctx);

		MemoryLogger.getInstance().checkMemory();
		joinCount = intersectionCounter;

		if (findClosedPatterns) {
			List<ClosedCandidate> survivors = collectSurvivorPatterns();
			removeNonClosedPatterns(survivors, keepPatterns);
			matchingMap = null;
			knownNonClosed = null;
			pendingCandidates = null;
			discoveredPatterns = null;
		}

		MemoryLogger.getInstance().checkMemory();
	}

	// =========================================================================
	// Parallel run
	// =========================================================================

	/**
	 * Runs ClaSP with one task per top-level (length-1) branch and writes/keeps the
	 * resulting patterns.
	 *
	 * @param database                  the input sequence database
	 * @param keepPatterns              whether patterns should be output at all
	 * @param verbose                   whether to print progress information
	 * @param outputFilePath            file to write patterns to, or null to keep
	 *                                  them in memory
	 * @param outputSequenceIdentifiers whether to output the ids of sequences
	 *                                  containing each pattern
	 * @throws IOException if writing the output file fails
	 */
	public void runAlgorithmParallelized(SequenceDatabase database, boolean keepPatterns, boolean verbose,
			String outputFilePath, boolean outputSequenceIdentifiers) throws IOException {
		initializeOutput(outputFilePath, outputSequenceIdentifiers);
		computeAbsoluteMinimumSupport(database.size());
		MemoryLogger.getInstance().reset();
		start = System.currentTimeMillis();
		runClaSPParallelized(database, dfs, keepPatterns, verbose);
		end = System.currentTimeMillis();
		finishOutput();
	}

	/**
	 * Drives the parallel search (one task per length-1 root) and the final
	 * closed-pattern backward pass.
	 *
	 * @param database     the input sequence database
	 * @param dfs          true for depth-first search, false for breadth-first
	 * @param keepPatterns whether patterns should be output at all
	 * @param verbose      whether to print progress information
	 */
	protected void runClaSPParallelized(SequenceDatabase database, boolean dfs, boolean keepPatterns, boolean verbose) {
		frequentItems = database.frequentItems();
		database = null;

		this.parallelMode = true;
		frequentPatternsCounter.set(frequentItems.size());
		joinCounter.set(0);
		this.numberOfFrequentClosedPatterns = 0;

		if (!findClosedPatterns) {
			if (keepPatterns) {
				savePatterns(getPatterns(frequentItems));
			}
		} else {
			discoveredPatterns = new ArrayList<ClosedCandidate>();
			for (EquivalenceClass item : frequentItems) {
				discoveredPatterns.add(new ClosedCandidate(item.getClassIdentifier()));
			}
		}

		final List<EquivalenceClass> roots = new ArrayList<EquivalenceClass>(frequentItems);
		ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		List<Future<List<ClosedCandidate>>> tasks = new ArrayList<Future<List<ClosedCandidate>>>();

		try {
			for (final EquivalenceClass root : roots) {
				if (verbose) {
					System.out.println("Exploring " + root);
				}
				tasks.add(pool.submit(() -> {
					List<ClosedCandidate> local = findClosedPatterns ? new ArrayList<ClosedCandidate>() : null;
					SearchContext ctx = new SearchContext(local, keepPatterns);
					execute(root, dfs, ctx);
					recycleIdList(root);
					return local;
				}));
			}
			pool.shutdown();

			for (Future<List<ClosedCandidate>> task : tasks) {
				List<ClosedCandidate> local = task.get();
				if (findClosedPatterns && local != null) {
					discoveredPatterns.addAll(local);
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Interrupted while waiting for ClaSP search tasks", e);
		} catch (ExecutionException e) {
			throw new RuntimeException("A parallel ClaSP search task failed", e.getCause());
		} finally {
			pool.shutdownNow();
			try {
				if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
					System.err.println("ClaSP: worker pool did not fully terminate within the shutdown grace period.");
				}
			} catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
			}
		}

		numberOfFrequentPatterns = frequentPatternsCounter.get();
		joinCount = joinCounter.get();

		if (findClosedPatterns) {
			List<ClosedCandidate> survivors = collectSurvivorPatterns();
			removeNonClosedPatterns(survivors, keepPatterns);
			discoveredPatterns = null;
		}

		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * Dispatches to the depth-first or breadth-first exploration strategy.
	 *
	 * @param eq  the equivalence class to explore
	 * @param dfs true for depth-first search, false for breadth-first
	 * @param ctx per-branch scratch state
	 */
	private void execute(EquivalenceClass eq, boolean dfs, SearchContext ctx) {
		if (dfs) {
			executeDfs(eq, ctx);
		} else {
			executeBfs(eq, ctx);
		}
	}

	/**
	 * Explores an equivalence class depth-first, recursing into each member right
	 * after generating its extensions.
	 *
	 * @param eq  the equivalence class whose members are to be extended
	 * @param ctx per-branch scratch state
	 */
	private void executeDfs(EquivalenceClass eq, SearchContext ctx) {
		List<EquivalenceClass> eqMembers = eq.getClassMembers();
		if (eqMembers == null || eqMembers.isEmpty()) {
			return;
		}
		for (int i = eqMembers.size() - 1; i >= 0; i--) {
			EquivalenceClass child_X = eqMembers.get(i);
			generateExtensionsForRow(eqMembers, i, ctx);

			finishMember(child_X, ctx);
			if (ctx.onlinePruning) {
				isAvoidable(child_X);
			}
			executeDfs(child_X, ctx);
			eqMembers.set(i, null);
		}
	}

	/**
	 * Explores an equivalence class breadth-first, generating every member's
	 * extensions before recursing into any of them.
	 *
	 * @param eq  the equivalence class whose members are to be extended
	 * @param ctx per-branch scratch state
	 */
	private void executeBfs(EquivalenceClass eq, SearchContext ctx) {
		List<EquivalenceClass> eqMembers = eq.getClassMembers();
		if (eqMembers == null || eqMembers.isEmpty()) {
			return;
		}
		for (int i = eqMembers.size() - 1; i >= 0; i--) {
			generateExtensionsForRow(eqMembers, i, ctx);
		}
		for (int i = eqMembers.size() - 1; i >= 0; i--) {
			EquivalenceClass child = eqMembers.get(i);
			finishMember(child, ctx);
			if (ctx.onlinePruning) {
				isAvoidable(child);
			}
			executeBfs(child, ctx);
			eqMembers.set(i, null);
		}
	}

	/**
	 * Releases a member's id-list and (when safe) its pattern's appearing-sequences
	 * bitset once its row of joins is done.
	 *
	 * @param member the equivalence class whose row has just been fully processed
	 * @param ctx    the current branch's scratch state
	 */
	private void finishMember(EquivalenceClass member, SearchContext ctx) {
		recycleIdList(member);
		if (ctx.canDiscardBitsets) {
			member.getClassIdentifier().discardAppearingSequences();
		}
	}

	/**
	 * Joins one equivalence class member against every earlier sibling and
	 * registers any frequent extension found.
	 *
	 * @param eqMembers the current level's members
	 * @param i         index of the member being extended against its earlier
	 *                  siblings
	 * @param ctx       per-branch scratch state
	 */
	private void generateExtensionsForRow(List<EquivalenceClass> eqMembers, int i, SearchContext ctx) {
		final int minSupport = minSupAbsolute;
		EquivalenceClass child_X = eqMembers.get(i);

		for (int j = i; j >= 0; j--) {
			EquivalenceClass child_Y = eqMembers.get(j);

			int extensionCount = generateCandidates(child_X.getClassIdentifier(), child_Y.getClassIdentifier(),
					minSupport, ctx.candidateBuffer);

			for (int c = 0; c < extensionCount; c++) {
				Pattern extension = ctx.candidateBuffer[c];
				IDList newIdList = join(extension, child_X, child_Y, minSupport);
				incrementJoinCounter();
				if (newIdList != null) {
					int candidateSupport = newIdList.getSupport();
					if (candidateSupport >= minSupport) {
						newIdList.setAppearingSequences(extension);
						extension.setCachedSupport(candidateSupport);

						EquivalenceClass newEq = new EquivalenceClass(extension);
						newEq.setIdList(newIdList);
						incrementPatternCounter();
						insertClassByPrefix(newEq, child_X, child_Y);

						if (findClosedPatterns) {
							ClosedCandidate candidate = new ClosedCandidate(extension);
							ctx.localDiscovered.add(candidate);
							if (ctx.onlinePruning) {
								pendingCandidates.put(newEq, candidate);
							}
						}
						if (ctx.saveEagerly) {
							keepPattern(extension);
						}
					} else {
						newIdList.recycle();
					}
				}
			}
		}
	}

	/**
	 * Generates the SPADE/CM-SPADE candidate extensions obtainable by joining two
	 * equivalence class identifiers.
	 *
	 * @param pattern1      identifier of the first equivalence class
	 * @param pattern2      identifier of the second equivalence class
	 * @param minSupport    minimum support threshold (absolute)
	 * @param candidatesOut buffer (length &gt;= {@link #MAX_CANDIDATES_PER_JOIN})
	 *                      receiving the generated candidates
	 * @return the number of candidates written into {@code candidatesOut}
	 */
	private int generateCandidates(Pattern pattern1, Pattern pattern2, int minSupport, Pattern[] candidatesOut) {
		long[] w1 = pattern1.getAppearingInWords();
		long[] w2 = pattern2.getAppearingInWords();
		int n = Math.min(w1.length, w2.length);
		int joinSupport = 0;
		for (int idx = 0; idx < n; idx++) {
			joinSupport += Long.bitCount(w1[idx] & w2[idx]);
			if (joinSupport >= minSupport) {
				break;
			}
		}
		if (joinSupport < minSupport) {
			return 0;
		}

		int count = 0;
		int lastItemId1 = pattern1.getLastItemId();
		boolean eq1 = pattern1.getLastEqualRelation();
		int lastItemId2 = pattern2.getLastItemId();
		boolean eq2 = pattern2.getLastEqualRelation();
		int patternComparison = pattern1.compareTo(pattern2);

		if (!eq1 && !eq2) {
			if (lastItemId1 != lastItemId2) {
				Pattern newCandidate_equalRelation;
				if (patternComparison < 0) {
					newCandidate_equalRelation = pattern1.cloneAndAppend(lastItemId2, true);
				} else {
					newCandidate_equalRelation = pattern2.cloneAndAppend(lastItemId1, true);
				}
				candidatesOut[count++] = newCandidate_equalRelation;
				candidatesOut[count++] = pattern2.cloneAndAppend(lastItemId1, false);
			}
			candidatesOut[count++] = pattern1.cloneAndAppend(lastItemId2, false);
		} else if (eq1 && eq2) {
			Pattern newCandidate_equalRelation;
			if (patternComparison < 0) {
				newCandidate_equalRelation = pattern1.cloneAndAppend(lastItemId2, true);
			} else {
				newCandidate_equalRelation = pattern2.cloneAndAppend(lastItemId1, true);
			}
			candidatesOut[count++] = newCandidate_equalRelation;
		} else {
			Pattern newCandidate_BeforeRelation;
			if (eq1) {
				newCandidate_BeforeRelation = pattern1.cloneAndAppend(lastItemId2, false);
			} else {
				newCandidate_BeforeRelation = pattern2.cloneAndAppend(lastItemId1, false);
			}
			candidatesOut[count++] = newCandidate_BeforeRelation;
		}
		assert count <= MAX_CANDIDATES_PER_JOIN;
		return count;
	}

	/**
	 * Checks an equivalence class against previously registered equal-support
	 * candidates and marks non-closed ones.
	 *
	 * @param eq the equivalence class about to be recursed into
	 * @return true if {@code eq} was proven non-closed
	 */
	private boolean isAvoidable(EquivalenceClass eq) {
		ClosedCandidate candidate = pendingCandidates.remove(eq);
		if (candidate == null) {
			return false;
		}

		SupportSumKey key = candidate.key();
		List<ClosedCandidate> bucket = matchingMap.get(key);
		if (bucket == null) {
			bucket = new ArrayList<ClosedCandidate>();
			bucket.add(candidate);
			matchingMap.put(key, bucket);
			return false;
		}

		int prefixSize = candidate.pattern.size();
		for (int i = 0; i < bucket.size(); i++) {
			ClosedCandidate stored = bucket.get(i);
			int storedSize = stored.pattern.size();
			if (storedSize == prefixSize) {
				continue;
			}
			if (prefixSize < storedSize) {
				if (isSubpattern(candidate.itemsets, stored.itemsets)) {
					knownNonClosed.add(candidate);
					return true;
				}
			} else if (isSubpattern(stored.itemsets, candidate.itemsets)) {
				knownNonClosed.add(stored);
				int last = bucket.size() - 1;
				bucket.set(i, bucket.get(last));
				bucket.remove(last);
				i--;
			}
		}

		bucket.add(candidate);
		return false;
	}

	// =========================================================================
	// Backward pruning (always run)
	// =========================================================================

	/**
	 * Builds the list of surviving pattern candidates for the backward pass.
	 *
	 * @return a fresh list of the patterns not already ruled out by the online
	 *         check
	 */
	private List<ClosedCandidate> collectSurvivorPatterns() {
		List<ClosedCandidate> survivors = new ArrayList<ClosedCandidate>(discoveredPatterns.size());
		for (ClosedCandidate c : discoveredPatterns) {
			if (knownNonClosed == null || !knownNonClosed.contains(c)) {
				survivors.add(c);
			}
		}
		return survivors;
	}

	/**
	 * Groups surviving patterns by (support, sum of appearing sequence ids) and
	 * removes non-closed ones within each group.
	 *
	 * @param survivors    patterns not already ruled out by the online check
	 * @param keepPatterns whether the surviving closed patterns should be output
	 */
	private void removeNonClosedPatterns(List<ClosedCandidate> survivors, boolean keepPatterns) {
		Map<SupportSumKey, List<ClosedCandidate>> groups = new HashMap<SupportSumKey, List<ClosedCandidate>>();

		for (ClosedCandidate c : survivors) {
			SupportSumKey key = c.key();
			List<ClosedCandidate> list = groups.get(key);
			if (list == null) {
				list = new ArrayList<ClosedCandidate>();
				groups.put(key, list);
			}
			list.add(c);
		}

		numberOfFrequentClosedPatterns = 0;
		for (List<ClosedCandidate> group : groups.values()) {
			for (int i = 0; i < group.size(); i++) {
				ClosedCandidate ci = group.get(i);
				if (ci == null) {
					continue;
				}
				int si = ci.pattern.size();
				for (int j = i + 1; j < group.size(); j++) {
					ClosedCandidate cj = group.get(j);
					if (cj == null) {
						continue;
					}
					int sj = cj.pattern.size();
					if (si == sj) {
						continue;
					}
					if (si < sj) {
						if (isSubpattern(ci.itemsets, cj.itemsets)) {
							group.set(i, null);
							break;
						}
					} else if (isSubpattern(cj.itemsets, ci.itemsets)) {
						group.set(j, null);
					}
				}
			}
			for (ClosedCandidate c : group) {
				if (c == null) {
					continue;
				}
				numberOfFrequentClosedPatterns++;
				if (keepPatterns) {
					keepPattern(c.pattern);
				}
			}
		}
	}

	/**
	 * Sums up the bit positions set in a pattern's appearing-sequences bitset.
	 *
	 * @param p the pattern whose appearing sequence ids are to be summed
	 * @return the sum of the ids of every sequence the pattern appears in
	 */
	private static long computeSumOfAppearingSequenceIds(Pattern p) {
		long[] words = p.getAppearingInWords();
		long sum = 0;
		for (int w = 0; w < words.length; w++) {
			long bits = words[w];
			while (bits != 0) {
				int bit = Long.numberOfTrailingZeros(bits);
				sum += (long) w * 64L + bit;
				bits &= (bits - 1);
			}
		}
		return sum;
	}

	/**
	 * Splits a pattern's flat item sequence into itemsets, each sorted for later
	 * linear comparisons.
	 *
	 * @param p the pattern to decompose
	 * @return the pattern's itemsets, in order, each as a sorted {@code int[]}
	 */
	private static int[][] computeItemsets(Pattern p) {
		List<int[]> itemsets = new ArrayList<int[]>();
		List<Integer> current = null;
		int size = p.size();
		for (int i = 0; i < size; i++) {
			if (i == 0 || !p.getIthEqualRelation(i)) {
				if (current != null) {
					itemsets.add(toSortedArray(current));
				}
				current = new ArrayList<Integer>();
			}
			current.add(p.getIthItemId(i));
		}
		if (current != null) {
			itemsets.add(toSortedArray(current));
		}
		return itemsets.toArray(new int[itemsets.size()][]);
	}

	/**
	 * Converts a list of boxed item ids into a sorted primitive array.
	 *
	 * @param items the item ids to convert
	 * @return the same ids as a sorted {@code int[]}
	 */
	private static int[] toSortedArray(List<Integer> items) {
		int[] arr = new int[items.size()];
		for (int i = 0; i < arr.length; i++) {
			arr[i] = items.get(i);
		}
		Arrays.sort(arr);
		return arr;
	}

	/**
	 * Checks whether one pattern's itemsets form an order-preserving subsequence of
	 * another's.
	 *
	 * @param itemsetsP the candidate (shorter) subsequence's itemsets, each sorted
	 * @param itemsetsQ the candidate (longer) supersequence's itemsets, each sorted
	 * @return true if every itemset of {@code p} can be matched, in order, inside
	 *         {@code q}
	 */
	private static boolean isSubpattern(int[][] itemsetsP, int[][] itemsetsQ) {
		int qi = 0;
		for (int[] itemsetP : itemsetsP) {
			boolean found = false;
			while (qi < itemsetsQ.length) {
				boolean matches = containsAllSorted(itemsetsQ[qi], itemsetP);
				qi++;
				if (matches) {
					found = true;
					break;
				}
			}
			if (!found) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Checks whether every id of one sorted array occurs in another sorted array.
	 *
	 * @param container a sorted-ascending array of item ids
	 * @param subset    a sorted-ascending array of item ids to look for
	 * @return true if every id in {@code subset} is present in {@code container}
	 */
	private static boolean containsAllSorted(int[] container, int[] subset) {
		int ci = 0;
		for (int x : subset) {
			while (ci < container.length && container[ci] < x) {
				ci++;
			}
			if (ci >= container.length || container[ci] != x) {
				return false;
			}
			ci++;
		}
		return true;
	}

	/** Per-branch scratch state threaded through the recursive search. */
	private final class SearchContext {
		/** Candidate buffer reused across every join in this branch. */
		final Pattern[] candidateBuffer = new Pattern[MAX_CANDIDATES_PER_JOIN];
		/** Sink for newly discovered patterns, or null if closed mining is off. */
		final List<ClosedCandidate> localDiscovered;
		/**
		 * True if non-closed patterns should be written out as soon as they're found.
		 */
		final boolean saveEagerly;
		/**
		 * True if the online forward-pruning bookkeeping should run for this branch.
		 */
		final boolean onlinePruning;
		/**
		 * True if a member's pattern bitset may be released once its row of joins is
		 * done.
		 */
		final boolean canDiscardBitsets;

		/**
		 * Builds a search context for one top-level branch.
		 *
		 * @param localDiscovered the (possibly thread-private) discovery sink for this
		 *                        branch
		 * @param keepPatterns    whether patterns should be output at all
		 */
		SearchContext(List<ClosedCandidate> localDiscovered, boolean keepPatterns) {
			this.localDiscovered = localDiscovered;
			this.saveEagerly = keepPatterns && !findClosedPatterns;
			this.onlinePruning = !parallelMode && executePruningMethods;
			this.canDiscardBitsets = !findClosedPatterns || !outputSequenceIdentifiers;
		}
	}

	/** A discovered pattern together with its cached closedness-check data. */
	private static final class ClosedCandidate {
		/** The wrapped pattern. */
		final Pattern pattern;
		/** The pattern's support, cached at construction time. */
		final int support;
		/**
		 * The sum of the pattern's appearing sequence ids, cached at construction time.
		 */
		final long sumOfIds;
		/**
		 * The pattern's itemset decomposition, each itemset's item ids sorted
		 * ascending.
		 */
		final int[][] itemsets;

		/**
		 * Wraps a pattern, computing and caching its closedness-check data.
		 *
		 * @param pattern the pattern to wrap
		 */
		ClosedCandidate(Pattern pattern) {
			this.pattern = pattern;
			this.support = pattern.getCachedSupport();
			this.sumOfIds = computeSumOfAppearingSequenceIds(pattern);
			this.itemsets = computeItemsets(pattern);
		}

		/**
		 * Builds this candidate's bucket key.
		 *
		 * @return the (support, sumOfIds) key identifying this candidate's bucket
		 */
		SupportSumKey key() {
			return new SupportSumKey(support, sumOfIds);
		}
	}

	/** Composite bucket key combining a support and a sum of sequence ids. */
	private static final class SupportSumKey {
		/** The support component of this key. */
		private final int support;
		/** The sum-of-ids component of this key. */
		private final long sumOfIds;

		/**
		 * Builds a bucket key.
		 *
		 * @param support  the pattern's support
		 * @param sumOfIds the sum of the pattern's appearing sequence ids
		 */
		SupportSumKey(int support, long sumOfIds) {
			this.support = support;
			this.sumOfIds = sumOfIds;
		}

		/**
		 * Compares two keys for equality of both components.
		 *
		 * @param o the object to compare against
		 * @return true if {@code o} is a {@code SupportSumKey} with the same support
		 *         and sumOfIds
		 */
		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof SupportSumKey)) {
				return false;
			}
			SupportSumKey other = (SupportSumKey) o;
			return support == other.support && sumOfIds == other.sumOfIds;
		}

		/**
		 * Computes a hash code consistent with {@link #equals}.
		 *
		 * @return a hash code combining both components
		 */
		@Override
		public int hashCode() {
			return 31 * Long.hashCode(sumOfIds) + support;
		}
	}

	/** @return the total number of frequent patterns found by the last run */
	public int getNumberOfFrequentPatterns() {
		return numberOfFrequentPatterns;
	}

	/**
	 * @return the total number of frequent closed patterns found by the last run
	 */
	public int getNumberOfFrequentClosedPatterns() {
		return numberOfFrequentClosedPatterns;
	}

	/** @return a human-readable summary of the last run's statistics */
	public String printStatistics() {
		StringBuilder sb = new StringBuilder(200);
		sb.append("=============  ClaSP v. 2.67 - STATISTICS =============\n Total time ~ ");
		sb.append(getRunningTime());
		sb.append(" ms\n Frequent sequences count : ");
		sb.append(numberOfFrequentPatterns);
		if (findClosedPatterns) {
			sb.append("\n Frequent closed sequences count : ");
			sb.append(numberOfFrequentClosedPatterns);
		}
		sb.append("\n Join count : ");
		sb.append(joinCount);
		sb.append("\n Max memory (mb):");
		sb.append(MemoryLogger.getInstance().getMaxMemory());
		sb.append('\n');
		sb.append(getOutputDescription());
		sb.append("\n===================================================\n");
		return sb.toString();
	}
}