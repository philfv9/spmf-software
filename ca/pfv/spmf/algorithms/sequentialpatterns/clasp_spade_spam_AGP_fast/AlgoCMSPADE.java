package ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.idLists.IDList;
import ca.pfv.spmf.datastructures.collections.map.AMapIntToInt;
import ca.pfv.spmf.datastructures.collections.map.AMapIntToObject;
import ca.pfv.spmf.tools.MemoryLogger;

/*
 * Copyright Antonio Gomariz Peñalver 2013, modified by Philippe Fournier-Viger,
 * 2013
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
 * This is an implementation of the CM-SPADE algorithm, proposed by Philippe
 * Fournier-Viger et al. (2014). CM-SPADE is a improved version of SPADE, which
 * was proposed by M. J. Zaki in 2001.
 * <p>
 * All the output handling, minimum-support computation, counters,
 * insertClassByPrefix(), getPatterns(), recycleIdList() and join() are
 * inherited from the {@link AbstractVerticalAlgorithm} class. This class only
 * keeps what is actually specific to CM-SPADE: the co-occurrence maps used to
 * prune candidates, and the execute()/generateCandidates() pair that consults
 * them.
 * <p>
 * NOTE: This implementation saves the pattern to a file as soon as they are
 * found or can keep the pattern into memory, depending on what the user choose.
 *
 * @author agomariz
 */
public class AlgoCMSPADE extends AbstractVerticalAlgorithm {

	/** Maximum number of candidate patterns a single join round can emit. */
	private static final int MAX_CANDIDATES_PER_JOIN = 3;

	/**
	 * Co-occurrence map used to prune candidates using before-relation. Built once.
	 */
	private AMapIntToObject<AMapIntToInt> coocMapAfter;

	/**
	 * Co-occurrence map used to prune candidates using equals-relation. Built once.
	 */
	private AMapIntToObject<AMapIntToInt> coocMapEquals;

	/** Time (ms) spent building the co-occurrence maps */
	private long coocMapBuildTime;

	/** Minimum number of items that an output pattern must contain (default 0) */
	private int minPatternLength = 0;

	/** Maximum number of items that an output pattern may contain (default infinity) */
	private int maxPatternLength = Integer.MAX_VALUE;

	/**
	 * Constructor
	 *
	 * @param support Minimum support (from 0 up to 1)
	 * @param dfs     Flag for indicating if we want a depth first search; if false,
	 *                breadth-first search
	 */
	public AlgoCMSPADE(double support, boolean dfs) {
		super(support, dfs, false);
	}

	/**
	 * Sets the minimum number of items that a pattern must contain to be output.
	 *
	 * @param minimumPatternLength the minimum pattern length (default 0)
	 */
	public void setMinimumPatternLength(int minimumPatternLength) {
		this.minPatternLength = minimumPatternLength;
	}

	/**
	 * Sets the maximum number of items that a pattern may contain to be output.
	 *
	 * @param maximumPatternLength the maximum pattern length (default infinity)
	 */
	public void setMaximumPatternLength(int maximumPatternLength) {
		this.maxPatternLength = maximumPatternLength;
	}

	/**
	 * Checks whether a given pattern length satisfies the minimum and maximum
	 * pattern length constraints.
	 *
	 * @param length the number of items contained in the pattern
	 * @return true if the length respects both constraints
	 */
	private boolean isLengthValid(int length) {
		// A pattern is valid if it is not shorter than the minimum and not longer than the maximum
		return length >= minPatternLength && length <= maxPatternLength;
	}

	/**
	 * Run the CM-SPADE algorithm with a single thread.
	 * 
	 * @param database                  Sequence database taken as input
	 * @param keepPatterns              Flag indicating if we want to keep the
	 *                                  output in memory or not
	 * @param verbose                   Flag for debugging purposes
	 * @param outputFilePath            Output file path; null means keep in memory
	 * @param outputSequenceIdentifiers if true, sequence identifiers will be output
	 *                                  for each pattern
	 * @throws IOException if an I/O error occurs
	 */
	public void runAlgorithm(SequenceDatabase database, boolean keepPatterns, boolean verbose, String outputFilePath,
			boolean outputSequenceIdentifiers) throws IOException {
		initializeOutput(outputFilePath, outputSequenceIdentifiers);
		computeAbsoluteMinimumSupport(database.size());
		MemoryLogger.getInstance().reset();
		start = System.currentTimeMillis();
		runSPADE(database, dfs, keepPatterns, verbose);
		end = System.currentTimeMillis();
		finishOutput();
	}

	/**
	 * Run the CM-SPADE algorithm using multiple threads.
	 *
	 * @param database                  Sequence database taken as input
	 * @param keepPatterns              Flag indicating if we want to keep the
	 *                                  output in memory or not
	 * @param verbose                   Flag for debugging purposes
	 * @param outputFilePath            Output file path; null means keep in memory
	 * @param outputSequenceIdentifiers if true, sequence identifiers will be output
	 *                                  for each pattern
	 * @throws IOException if an I/O error occurs
	 */
	public void runAlgorithmParallelized(SequenceDatabase database, boolean keepPatterns, boolean verbose,
			String outputFilePath, boolean outputSequenceIdentifiers) throws IOException {
		initializeOutput(outputFilePath, outputSequenceIdentifiers);
		computeAbsoluteMinimumSupport(database.size());
		MemoryLogger.getInstance().reset();

		start = System.currentTimeMillis();
		runSPADEParallelized(database, dfs, keepPatterns, verbose);
		end = System.currentTimeMillis();
		finishOutput();
	}

	/**
	 * The actual method for extracting frequent sequences using co-occurrence maps
	 * to prune the search space.
	 *
	 * @param database     The original database
	 * @param dfs          Flag for indicating if we want a depth first search; if
	 *                     false, breadth-first search
	 * @param keepPatterns flag indicating if we are interested in keeping the
	 *                     output of the algorithm
	 * @param verbose      Flag for debugging purposes
	 */
	protected void runSPADE(SequenceDatabase database, boolean dfs, boolean keepPatterns, boolean verbose) {
		frequentItems = database.frequentItems();
		Collection<Pattern> size1sequences = getPatterns(frequentItems);
		// Only save the size-1 patterns if their length respects the constraints
		if (keepPatterns && isLengthValid(1)) {
			savePatterns(size1sequences);
		}

		// NEW-CODE-PFV 2013
		// Map: key: item value: another item that followed the first item + support
		// Sized based on the number of frequent items instead of a fixed guess, to
		// reduce the number of resize/rehash operations while building the maps.
		int coocMapCapacity = Math.max(16, frequentItems.size() * 2);
		AMapIntToObject<AMapIntToInt> coocMapAfter = new AMapIntToObject<>(coocMapCapacity);
		AMapIntToObject<AMapIntToInt> coocMapEquals = new AMapIntToObject<>(coocMapCapacity);
		long coocStart = System.currentTimeMillis();
		buildCoocMaps(database, coocMapAfter, coocMapEquals, coocMapCapacity);
		coocMapBuildTime = System.currentTimeMillis() - coocStart;
		if (verbose) {
			System.out.println("Co-occurrence maps built in " + coocMapBuildTime + " ms");
		}

		database = null;

		EquivalenceClass rootClass = new EquivalenceClass(null, new ArrayList<EquivalenceClass>(frequentItems));

		this.coocMapAfter = coocMapAfter;
		this.coocMapEquals = coocMapEquals;

		// Single-threaded run: use the plain counters directly instead of the atomic
		// ones
		this.parallelMode = false;
		// The pattern counter only accounts for size-1 patterns if they respect the constraints
		this.numberOfFrequentPatterns = isLengthValid(1) ? frequentItems.size() : 0;
		this.intersectionCounter = 0;

		// Single-threaded run: one shared candidate buffer for the whole search
		// (reused across every recursion node instead of reallocated per node), and
		// bitsets can be dropped as soon as a member's row of joins is done, unless
		// the caller wants sequence ids printed in the output.
		Pattern[] candidateBuffer = new Pattern[MAX_CANDIDATES_PER_JOIN];
		boolean canDiscardBitsets = !outputSequenceIdentifiers;

		// The root class members are all size-1 patterns
		execute(rootClass, dfs, keepPatterns, canDiscardBitsets, candidateBuffer, 1);

		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * Dispatches to the depth-first or breadth-first exploration strategy.
	 *
	 * @param eq                the equivalence class to explore
	 * @param dfs               true for depth-first search, false for breadth-first
	 * @param keepPatterns      whether patterns should be output at all
	 * @param canDiscardBitsets whether a member's pattern bitset may be released
	 *                          once its row of joins is done
	 * @param candidateBuffer   scratch buffer reused across every join in this
	 *                          branch (must not be shared across threads)
	 * @param currentLength     number of items contained in the members of eq
	 */
	private void execute(EquivalenceClass eq, boolean dfs, boolean keepPatterns, boolean canDiscardBitsets,
			Pattern[] candidateBuffer, int currentLength) {
		if (dfs) {
			executeDfs(eq, keepPatterns, canDiscardBitsets, candidateBuffer, currentLength);
		} else {
			executeBfs(eq, keepPatterns, canDiscardBitsets, candidateBuffer, currentLength);
		}
	}

	/**
	 * Explores an equivalence class depth-first, recursing into each member right
	 * after generating its extensions.
	 */
	private void executeDfs(EquivalenceClass eq, boolean keepPatterns, boolean canDiscardBitsets,
			Pattern[] candidateBuffer, int currentLength) {
		List<EquivalenceClass> eqMembers = eq.getClassMembers();
		if (eqMembers == null || eqMembers.isEmpty()) {
			return;
		}

		// Save parameters once per equivalence class, exactly like the original
		// execute() did, instead of re-reading the fields on every row.
		final int minSupport = minSupAbsolute;
		final AMapIntToObject<AMapIntToInt> coocAfter = coocMapAfter;
		final AMapIntToObject<AMapIntToInt> coocEquals = coocMapEquals;
		final boolean useCooc = coocAfter != null || coocEquals != null;

		boolean anyPatternCreated = false;
		for (int i = eqMembers.size() - 1; i >= 0; i--) {
			EquivalenceClass child_X = eqMembers.get(i);

			boolean createdThisRow = generateExtensionsForRow(eqMembers, i, keepPatterns, candidateBuffer, minSupport,
					coocAfter, coocEquals, useCooc, currentLength);
			anyPatternCreated = anyPatternCreated || createdThisRow;

			eqMembers.remove(i);
			// child_X has just been removed from its parent's member list: every pair
			// (child_X, Y) it could ever take part in has already been processed above,
			// so its id-list will never be read again.
			finishMember(child_X, canDiscardBitsets);

			// Explore child patterns, whose members are one item longer
			if (anyPatternCreated) {
				executeDfs(child_X, keepPatterns, canDiscardBitsets, candidateBuffer, currentLength + 1);
			}
		}
	}

	/**
	 * Explores an equivalence class breadth-first, generating every member's
	 * extensions before recursing into any of them.
	 */
	private void executeBfs(EquivalenceClass eq, boolean keepPatterns, boolean canDiscardBitsets,
			Pattern[] candidateBuffer, int currentLength) {
		List<EquivalenceClass> eqMembers = eq.getClassMembers();
		if (eqMembers == null || eqMembers.isEmpty()) {
			return;
		}

		final int minSupport = minSupAbsolute;
		final AMapIntToObject<AMapIntToInt> coocAfter = coocMapAfter;
		final AMapIntToObject<AMapIntToInt> coocEquals = coocMapEquals;
		final boolean useCooc = coocAfter != null || coocEquals != null;

		boolean anyPatternCreated = false;
		for (int i = eqMembers.size() - 1; i >= 0; i--) {
			boolean createdThisRow = generateExtensionsForRow(eqMembers, i, keepPatterns, candidateBuffer, minSupport,
					coocAfter, coocEquals, useCooc, currentLength);
			anyPatternCreated = anyPatternCreated || createdThisRow;
		}

		// Always release id-lists/bitsets for this level once the join phase is
		// done, whether or not any extension was found (the original code only did
		// this when anyPatternCreated was true, leaking id-lists otherwise).
		for (int i = eqMembers.size() - 1; i >= 0; i--) {
			finishMember(eqMembers.get(i), canDiscardBitsets);
		}

		if (anyPatternCreated) {
			for (int i = eqMembers.size() - 1; i >= 0; i--) {
				EquivalenceClass child = eqMembers.get(i);
				// Children members are one item longer than the current level
				executeBfs(child, keepPatterns, canDiscardBitsets, candidateBuffer, currentLength + 1);
				eqMembers.remove(i);
			}
		} else {
			eqMembers.clear();
		}
	}

	/**
	 * Releases a member's id-list and (when safe) its pattern's appearing-sequences
	 * bitset once its row of joins is done.
	 *
	 * @param member            the equivalence class whose row has just been fully
	 *                          processed
	 * @param canDiscardBitsets whether the pattern bitset may be released too
	 */
	private void finishMember(EquivalenceClass member, boolean canDiscardBitsets) {
		recycleIdList(member);
		if (canDiscardBitsets) {
			member.getClassIdentifier().discardAppearingSequences();
		}
	}

	/**
	 * Joins one equivalence class member (row {@code i}) against every earlier
	 * sibling, using the co-occurrence maps to skip pairs that cannot possibly
	 * produce a frequent extension, and registers any frequent extension found.
	 *
	 * @param currentLength number of items contained in the members being joined
	 * @return true if at least one frequent extension was created for this row
	 */
	private boolean generateExtensionsForRow(List<EquivalenceClass> eqMembers, int i, boolean keepPatterns,
			Pattern[] candidateBuffer, int minSupport, AMapIntToObject<AMapIntToInt> coocAfter,
			AMapIntToObject<AMapIntToInt> coocEquals, boolean useCooc, int currentLength) {

		// Any extension created here will contain one more item than the current members
		int newLength = currentLength + 1;
		if (newLength > maxPatternLength) {
			// Extending further would exceed the maximum pattern length: prune this row entirely
			return false;
		}

		EquivalenceClass child_X = eqMembers.get(i);
		boolean anyPatternCreated = false;

		// NEW CODE-PFV-2013
		// itemXInt/eq1 are only needed when co-occurrence pruning is active, since
		// they drive which of the equals/after maps below are actually worth
		// consulting for this X (see the per-pair block below). 
		int itemXInt = 0;
		boolean eq1 = false;
		AMapIntToInt cmapX_equals = null;
		AMapIntToInt cmapX_after = null;
		if (useCooc) {
			Pattern xIdentifier = child_X.getClassIdentifier();
			itemXInt = xIdentifier.getLastItemId();
			eq1 = xIdentifier.getLastEqualRelation();
			// Primitive-key lookup: no Integer boxing here
			cmapX_equals = (coocEquals == null) ? null : coocEquals.get(itemXInt);
			cmapX_after = (coocAfter == null) ? null : coocAfter.get(itemXInt);
		}
		// END NEW CODE

		for (int j = i; j >= 0; j--) {
			EquivalenceClass child_Y = eqMembers.get(j);

			// NEW CODE-PFV-2013
			boolean doNotExploreXY = false;
			boolean doNotExploreYX = false;
			boolean doNotExploreX_Y = false;
			boolean doNotExploreY_X = false;

			if (useCooc) {
				Pattern yIdentifier = child_Y.getClassIdentifier();
				int itemYInt = yIdentifier.getLastItemId();
				boolean eq2 = yIdentifier.getLastEqualRelation();

				/*
				 * generateCandidates() only ever reads the "equals" flags when both last
				 * elements are i-extensions or both are s-extensions, and only ever reads the
				 * "after" flags unless both are i-extensions.
				 */
				boolean needEquals = eq1 == eq2;
				boolean needAfter = !(eq1 && eq2);

				if (needEquals && coocEquals != null) {

					AMapIntToInt cmapY_equals = coocEquals.get(itemYInt);
					// AMapIntToInt.get() returns -1 (primitive sentinel) for a missing key
					int count1 = (cmapX_equals == null) ? -1 : cmapX_equals.get(itemYInt);
					int count2 = (cmapY_equals == null) ? -1 : cmapY_equals.get(itemXInt);
					doNotExploreXY = count1 < minSupport;
					doNotExploreYX = count2 < minSupport;
				}
				if (needAfter && coocAfter != null) {
					if (!eq1 && !eq2) {
						// both s-extensions: both before-relation directions are potentially used
						AMapIntToInt cmapY_after = coocAfter.get(itemYInt);
						int count1 = (cmapX_after == null) ? -1 : cmapX_after.get(itemYInt);
						int count2 = (cmapY_after == null) ? -1 : cmapY_after.get(itemXInt);
						doNotExploreX_Y = count1 < minSupport;
						doNotExploreY_X = count2 < minSupport;
					} else if (eq1) {
						// mixed, X is the i-extension: only the X-before-Y direction is ever read
						int count1 = (cmapX_after == null) ? -1 : cmapX_after.get(itemYInt);
						doNotExploreX_Y = count1 < minSupport;
					} else {
						// mixed, Y is the i-extension: only the Y-before-X direction is ever read
						AMapIntToInt cmapY_after = coocAfter.get(itemYInt);
						int count2 = (cmapY_after == null) ? -1 : cmapY_after.get(itemXInt);
						doNotExploreY_X = count2 < minSupport;
					}
				}

				// The decision to skip the whole pair must only be based on the flags that
				// are actually meaningful for this (eq1, eq2) combination, since the other
				// flags are intentionally left at their default (false) above.
				boolean shouldSkipPair;
				if (eq1 && eq2) {
					shouldSkipPair = doNotExploreXY && doNotExploreYX;
				} else if (eq1 != eq2) {
					shouldSkipPair = eq1 ? doNotExploreX_Y : doNotExploreY_X;
				} else {
					shouldSkipPair = doNotExploreXY && doNotExploreYX && doNotExploreX_Y && doNotExploreY_X;
				}

				if (shouldSkipPair) {
					continue;
				}
			}
			// END NEW-CODE-PFV-2013

			int candidateCount = generateCandidates(child_X.getClassIdentifier(), child_Y.getClassIdentifier(),
					minSupport, doNotExploreXY, doNotExploreYX, doNotExploreX_Y, doNotExploreY_X, candidateBuffer);

			for (int c = 0; c < candidateCount; c++) {
				Pattern extension = candidateBuffer[c];
				IDList newIdList = join(extension, child_X, child_Y, minSupport);
				incrementJoinCounter();
				if (newIdList != null) {
					int candidateSupport = newIdList.getSupport();
					if (candidateSupport >= minSupport) {
						anyPatternCreated = true;
						newIdList.setAppearingSequences(extension);
						// The support is already known from the join: cache it on the pattern
						// so that output (writeToFile/toStringToFile) never needs to fall back
						// to a full BitSet.cardinality() popcount scan.
						extension.setCachedSupport(candidateSupport);

						// Only output and count the pattern if its length respects the constraints
						boolean lengthValid = isLengthValid(newLength);
						if (keepPatterns && lengthValid) {
							keepPattern(extension);
						}

						EquivalenceClass newEq = new EquivalenceClass(extension);
						newEq.setIdList(newIdList);

						if (lengthValid) {
							incrementPatternCounter();
						}

						insertClassByPrefix(newEq, child_X, child_Y);
					} else {
						// The candidate did not meet the minimum support: return its id-list
						// to its reuse pool.
						newIdList.recycle();
					}
				}
			}
		}
		return anyPatternCreated;
	}

	/**
	 * Generates candidate patterns from the two patterns given as parameters,
	 * writing them into a caller-supplied reusable buffer instead of allocating a
	 * new array on every call.
	 *
	 * @param pattern1        the first pattern
	 * @param pattern2        the second pattern
	 * @param minSupport      the minimum absolute support
	 * @param doNotExploreXY  if true, skip the XY equal-relation candidate
	 * @param doNotExploreYX  if true, skip the YX equal-relation candidate
	 * @param doNotExploreX_Y if true, skip the X before Y candidate
	 * @param doNotExploreY_X if true, skip the Y before X candidate
	 * @param candidatesOut   reusable buffer (length >= 3) that receives the
	 *                        generated candidates
	 * @return the number of candidates written into candidatesOut, starting at
	 *         index 0
	 */
	private int generateCandidates(Pattern pattern1, Pattern pattern2, int minSupport, boolean doNotExploreXY,
			boolean doNotExploreYX, boolean doNotExploreX_Y, boolean doNotExploreY_X, Pattern[] candidatesOut) {

		// Compute the support of the join with a word-level AND/popcount over each
		// pattern's cached long[] view of its appearing-in bitset.
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

		// Read last-element fields as primitives directly from the pattern's cache
		int lastItemId1 = pattern1.getLastItemId();
		boolean eq1 = pattern1.getLastEqualRelation();
		int lastItemId2 = pattern2.getLastItemId();
		boolean eq2 = pattern2.getLastEqualRelation();

		// Single primitive long comparison .
		int patternComparison = pattern1.compareTo(pattern2);

		if (!eq1 && !eq2) {
			// Both last elements are s-extensions
			if (lastItemId1 != lastItemId2) {
				// Create equal-relation candidate
				Pattern newCandidate_equalRelation = null;
				if (patternComparison < 0 && !doNotExploreXY) {
					newCandidate_equalRelation = pattern1.cloneAndAppend(lastItemId2, true);
				} else if (!doNotExploreYX) {
					newCandidate_equalRelation = pattern2.cloneAndAppend(lastItemId1, true);
				}
				if (newCandidate_equalRelation != null) {
					candidatesOut[count++] = newCandidate_equalRelation;
				}

				// Create Y before X candidate
				if (!doNotExploreY_X) {
					candidatesOut[count++] = pattern2.cloneAndAppend(lastItemId1, false);
				}
			}
			// Create X before Y candidate
			if (!doNotExploreX_Y) {
				candidatesOut[count++] = pattern1.cloneAndAppend(lastItemId2, false);
			}

		} else if (eq1 && eq2) {
			// Both last elements are i-extensions: only one equal-relation candidate
			if (patternComparison < 0 && !doNotExploreXY) {
				candidatesOut[count++] = pattern1.cloneAndAppend(lastItemId2, true);
			} else if (patternComparison > 0 && !doNotExploreYX) {
				candidatesOut[count++] = pattern2.cloneAndAppend(lastItemId1, true);
			}

		} else {
			// Mixed: one i-extension, one s-extension — only one before-relation candidate
			Pattern newCandidate_BeforeRelation = null;
			if (eq1 && !doNotExploreX_Y) {
				newCandidate_BeforeRelation = pattern1.cloneAndAppend(lastItemId2, false);
			} else if (!doNotExploreY_X) {
				newCandidate_BeforeRelation = pattern2.cloneAndAppend(lastItemId1, false);
			}
			if (newCandidate_BeforeRelation != null) {
				candidatesOut[count++] = newCandidate_BeforeRelation;
			}
		}
		assert count <= MAX_CANDIDATES_PER_JOIN;
		return count;
	}

	/**
	 * Per-item entry used while building the equals co-occurrence map. It pairs a
	 * reusable "seen" bitset with the id of the sequence for which it was last
	 * reset, so that the whole map can be kept alive for the entire database scan
	 * instead of being cleared (and its bitsets reallocated) once per sequence.
	 */
	private static final class SeenEntry {
		/**
		 * Items already counted as co-occurring with this entry's item in the current
		 * sequence.
		 */
		private final BitSet seenItems = new BitSet();
		/**
		 * Id of the sequence seenItems was last reset for; -1 before the first use
		 * (sequence ids start at 1).
		 */
		private int lastSequenceId = -1;
	}

	/**
	 * Builds the co-occurrence maps (after and equals) from the database in a
	 * single efficient pass.
	 *
	 * @param database            the sequence database to scan
	 * @param coocMapAfter        map to populate with after co-occurrence counts
	 * @param coocMapEquals       map to populate with equals (same itemset)
	 *                            co-occurrence counts
	 * @param innerMapBucketCount initial bucket count used for each per-item
	 *                            AMapIntToInt, sized from the number of frequent
	 *                            items to keep collision lists short
	 */
	private void buildCoocMaps(SequenceDatabase database, AMapIntToObject<AMapIntToInt> coocMapAfter,
			AMapIntToObject<AMapIntToInt> coocMapEquals, int innerMapBucketCount) {
		// alreadySeenA/alreadySeenB_after are reused across all sequences (cleared
		// instead of reallocated)
		BitSet alreadySeenA = new BitSet();
		AMapIntToObject<SeenEntry> alreadySeenB_equals = new AMapIntToObject<SeenEntry>(innerMapBucketCount);
		BitSet alreadySeenB_after = new BitSet();

		List<Sequence> sequences = database.getSequences();
		for (int s = 0; s < sequences.size(); s++) {
			Sequence seq = sequences.get(s);
			// Calculate the sequence id
			int seqId = s + 1;
			List<Itemset> itemsets = seq.getItemsets();
			int numItemsets = itemsets.size();

			alreadySeenA.clear();

			for (int i = 0; i < numItemsets; i++) {
				Itemset itemsetA = itemsets.get(i);
				int itemsetASize = itemsetA.size();

				for (int j = 0; j < itemsetASize; j++) {
					// itemId is now a plain int — no unboxing needed
					int itemA = itemsetA.get(j);

					// equals co-occurrence: items in same itemset
					SeenEntry seenEntry = alreadySeenB_equals.get(itemA);
					if (seenEntry == null) {
						seenEntry = new SeenEntry();
						alreadySeenB_equals.put(itemA, seenEntry);
					}
					if (seenEntry.lastSequenceId != seqId) {
						// first time this item is touched in the current sequence: reset its bitset
						seenEntry.seenItems.clear();
						seenEntry.lastSequenceId = seqId;
					}
					BitSet equalSet = seenEntry.seenItems;

					AMapIntToInt mapCoocItemEquals = coocMapEquals.get(itemA);

					for (int k = j + 1; k < itemsetASize; k++) {
						int itemB = itemsetA.get(k);
						if (!equalSet.get(itemB)) {
							equalSet.set(itemB);
							if (mapCoocItemEquals == null) {
								mapCoocItemEquals = new AMapIntToInt(innerMapBucketCount);
								coocMapEquals.put(itemA, mapCoocItemEquals);
							}
							mapCoocItemEquals.getAndIncreaseValueBy(itemB, 1);
						}
					}

					// after co-occurrence: items in later itemsets, count once per sequence per
					// itemA
					if (!alreadySeenA.get(itemA)) {
						alreadySeenA.set(itemA);
						alreadySeenB_after.clear();
						AMapIntToInt mapCoocItemAfter = coocMapAfter.get(itemA);

						for (int k = i + 1; k < numItemsets; k++) {
							Itemset itemsetB = itemsets.get(k);
							int itemsetBSize = itemsetB.size();
							for (int m = 0; m < itemsetBSize; m++) {
								int itemB = itemsetB.get(m);
								if (!alreadySeenB_after.get(itemB)) {
									alreadySeenB_after.set(itemB);
									if (mapCoocItemAfter == null) {
										mapCoocItemAfter = new AMapIntToInt(innerMapBucketCount);
										coocMapAfter.put(itemA, mapCoocItemAfter);
									}
									mapCoocItemAfter.getAndIncreaseValueBy(itemB, 1);
								}
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Returns a string with statistics about the last algorithm execution.
	 *
	 * @return the statistics string
	 */
	public String printStatistics() {
		StringBuilder sb = new StringBuilder(500);
		sb.append("=============  CM-SPADE v.2.67 -- Algorithm - STATISTICS =============\n Total time ~ ");
		sb.append(getRunningTime());
		sb.append(" ms\n");
		sb.append(" Co-occurrence maps building time ~ ");
		sb.append(coocMapBuildTime);
		sb.append(" ms\n");
		sb.append(" Frequent sequences count : ");
		sb.append(numberOfFrequentPatterns);
		sb.append('\n');
		sb.append(" Join count : ");
		sb.append(intersectionCounter);
		sb.append('\n');
		sb.append(" Max memory (mb):");
		sb.append(MemoryLogger.getInstance().getMaxMemory());
		sb.append('\n');
		sb.append(getOutputDescription());
		sb.append("\n===================================================\n");
		return sb.toString();
	}

	/**
	 * Returns the number of join operations performed by the algorithm.
	 *
	 * @return the join count
	 */
	public int getIntersectionCounter() {
		return intersectionCounter;
	}

	/**
	 * Returns the time spent building the co-occurrence maps, in milliseconds.
	 *
	 * @return the co-occurrence map building time in milliseconds
	 */
	public long getCoocMapBuildTime() {
		return coocMapBuildTime;
	}

	/**
	 * The actual method for extracting frequent sequences starting from 2-patterns,
	 * resolving each equivalence class independently in parallel, one task per
	 * top-level frequent item.
	 *
	 * @param database     The original database
	 * @param dfs          Flag for indicating if we want a depth first search; if
	 *                     false, breadth-first search
	 * @param keepPatterns flag indicating if we are interested in keeping the
	 *                     output of the algorithm
	 * @param verbose      Flag for debugging purposes
	 */
	protected void runSPADEParallelized(SequenceDatabase database, boolean dfs, boolean keepPatterns, boolean verbose) {
		frequentItems = database.frequentItems();
		Collection<Pattern> size1Sequences = getPatterns(frequentItems);
		List<EquivalenceClass> size2EquivalenceClasses = database.getSize2FrequentSequences(minSupAbsolute);
		Collection<Pattern> size2Sequences = getPatterns(size2EquivalenceClasses);

		// Only save patterns of a length that respects the constraints
		if (keepPatterns) {
			if (isLengthValid(1)) {
				savePatterns(size1Sequences);
			}
			if (isLengthValid(2)) {
				savePatterns(size2Sequences);
			}
		}

		// Build the co-occurrence maps from the still-intact database
		int coocMapCapacity = Math.max(16, frequentItems.size() * 2);
		AMapIntToObject<AMapIntToInt> coocMapAfter = new AMapIntToObject<>(coocMapCapacity);
		AMapIntToObject<AMapIntToInt> coocMapEquals = new AMapIntToObject<>(coocMapCapacity);
		long coocStart = System.currentTimeMillis();
		buildCoocMaps(database, coocMapAfter, coocMapEquals, coocMapCapacity);
		coocMapBuildTime = System.currentTimeMillis() - coocStart;
		if (verbose) {
			System.out.println("Co-occurrence maps built in " + coocMapBuildTime + " ms");
		}

		// Install the maps BEFORE any worker thread is started.
		this.coocMapAfter = coocMapAfter;
		this.coocMapEquals = coocMapEquals;

		// Multi-threaded run: execute() must use the atomic counters below, since
		// several worker threads update them concurrently.
		this.parallelMode = true;
		// The initial pattern count only accounts for patterns respecting the length constraints
		int initialPatternCount = (isLengthValid(1) ? size1Sequences.size() : 0)
				+ (isLengthValid(2) ? size2Sequences.size() : 0);
		frequentPatternsCounter.set(initialPatternCount);
		joinCounter.set(0);

		database = null;
		size2EquivalenceClasses = null;

		final boolean canDiscardBitsets = !outputSequenceIdentifiers;

		ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		List<Future<?>> tasks = new ArrayList<Future<?>>();

		try {
			while (!frequentItems.isEmpty()) {
				final EquivalenceClass frequentAtom = frequentItems.remove(frequentItems.size() - 1);
				if (verbose) {
					System.out.println("Exploring " + frequentAtom);
				}
				tasks.add(pool.submit(() -> {
					// Each task/thread gets its own candidate buffer: it must never be shared
					// across concurrently running branches.
					Pattern[] localBuffer = new Pattern[MAX_CANDIDATES_PER_JOIN];
					// frequentAtom's members are the size-2 sequences built above
					execute(frequentAtom, dfs, keepPatterns, canDiscardBitsets, localBuffer, 2);
					recycleIdList(frequentAtom);
				}));
				MemoryLogger.getInstance().checkMemory();
			}
			pool.shutdown();

			for (Future<?> task : tasks) {
				// Propagate any exception thrown by execute() in a worker thread
				task.get();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Interrupted while waiting for CM-SPADE search tasks", e);
		} catch (ExecutionException e) {
			throw new RuntimeException("A parallel CM-SPADE search task failed", e.getCause());
		} finally {
			pool.shutdownNow();
			try {
				if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
					System.err
							.println("CM-SPADE: worker pool did not fully terminate within the shutdown grace period.");
				}
			} catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
			}
		}

		numberOfFrequentPatterns = frequentPatternsCounter.get();
		intersectionCounter = joinCounter.get();
		MemoryLogger.getInstance().checkMemory();
	}
}