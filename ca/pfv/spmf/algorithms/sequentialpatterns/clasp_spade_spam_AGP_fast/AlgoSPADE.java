package ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.idLists.IDList;
import ca.pfv.spmf.tools.MemoryLogger;

/*
 * Copyright Antonio Gomariz Peñalver 2013, modified by Philippe Fournier-Viger.
 * (license header unchanged)
 */
/**
 * Implementation of the SPADE algorithm (Zaki, 2001), now built on top of
 * {@link AbstractVerticalAlgorithm}, which holds all the output/bookkeeping
 * plumbing shared with CM-SPADE (and ClaSP/CM-ClaSP).
 *
 * @author agomariz
 */
public class AlgoSPADE extends AbstractVerticalAlgorithm {

	/** Join count published to callers once a run finishes. */
	public long joinCount;

	/** Maximum number of candidate patterns a single join round can emit. */
	private static final int MAX_CANDIDATES_PER_JOIN = 3;

	/** Minimum number of items that an output pattern must contain. */
	private int minPatternLength = 0;

	/** Maximum number of items that an output pattern may contain. */
	private int maxPatternLength = Integer.MAX_VALUE;

	/**
	 * Constructor of the class that calls CM-SPADE algorithm.
	 *
	 * @param support Minimum support (from 0 up to 1)
	 * @param dfs     Flag for indicating if we want a depth first search; if false,
	 *                breadth-first search
	 */
	public AlgoSPADE(double support, boolean dfs) {
		super(support, dfs, false);
	}

	/**
	 * Sets the minimum pattern length constraint.
	 * @param minPatternLength the minimum number of items that a pattern must contain
	 */
	public void setMinimumPatternLength(int minPatternLength) {
		this.minPatternLength = minPatternLength; // store the minimum length
	}

	/**
	 * Sets the maximum pattern length constraint.
	 * @param maxPatternLength the maximum number of items that a pattern may contain
	 */
	public void setMaximumPatternLength(int maxPatternLength) {
		this.maxPatternLength = maxPatternLength; // store the maximum length
	}

	/**
	 * Checks whether a pattern of the given length may be kept in the output.
	 * @param length the number of items in the pattern
	 * @return true if the length respects the minimum and maximum constraints
	 */
	private boolean isLengthAllowed(int length) {
		return length >= minPatternLength && length <= maxPatternLength; // check both bounds
	}

	/**
	 * Checks whether a pattern of the given length may still be extended by one
	 * more item without exceeding the maximum pattern length.
	 * @param length the number of items in the pattern
	 * @return true if extending the pattern is still allowed
	 */
	private boolean canExtend(int length) {
		return length < maxPatternLength; // extension would create length+1 items
	}

	/**
	 * Run the algorithm with a single thread.
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
	 * Run the algorithm using multiple threads.
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
		runSPADEFromSize2PatternsParallelized(database, dfs, keepPatterns, verbose);
		end = System.currentTimeMillis();
		finishOutput();
	}

	/**
	 * The actual method for extracting frequent sequences
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
		if (keepPatterns && isLengthAllowed(1)) {
			savePatterns(size1sequences);
		}

		database = null;

		EquivalenceClass rootClass = new EquivalenceClass(null, new ArrayList<EquivalenceClass>(frequentItems));

		this.parallelMode = false;
		// only count size-1 patterns if their length is within the allowed bounds
		this.numberOfFrequentPatterns = isLengthAllowed(1) ? frequentItems.size() : 0;
		this.intersectionCounter = 0;

		// Single-threaded run: one shared candidate buffer for the whole search,
		// and bitsets can be dropped as soon as a member's row of joins is done
		// (unless the caller wants sequence ids printed in the output).
		Pattern[] candidateBuffer = new Pattern[MAX_CANDIDATES_PER_JOIN];
		boolean canDiscardBitsets = !outputSequenceIdentifiers;

		execute(rootClass, dfs, keepPatterns, canDiscardBitsets, candidateBuffer, 1);

		MemoryLogger.getInstance().checkMemory();
		joinCount = intersectionCounter;
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
	 * @param length            the number of items in the patterns held by eq's members
	 */
	private void execute(EquivalenceClass eq, boolean dfs, boolean keepPatterns, boolean canDiscardBitsets,
			Pattern[] candidateBuffer, int length) {
		if (dfs) {
			executeDfs(eq, keepPatterns, canDiscardBitsets, candidateBuffer, length);
		} else {
			executeBfs(eq, keepPatterns, canDiscardBitsets, candidateBuffer, length);
		}
	}

	/**
	 * Explores an equivalence class depth-first, recursing into each member right
	 * after generating its extensions.
	 *
	 * @param length the number of items in the patterns held by eq's members
	 */
	private void executeDfs(EquivalenceClass eq, boolean keepPatterns, boolean canDiscardBitsets,
			Pattern[] candidateBuffer, int length) {
		List<EquivalenceClass> eqMembers = eq.getClassMembers();
		if (eqMembers == null || eqMembers.isEmpty()) {
			return;
		}
		if (!canExtend(length)) {
			// maximum pattern length reached: release resources without extending further
			for (int i = eqMembers.size() - 1; i >= 0; i--) {
				finishMember(eqMembers.get(i), canDiscardBitsets);
			}
			eqMembers.clear();
			return;
		}
		boolean anyPatternCreated = false;
		for (int i = eqMembers.size() - 1; i >= 0; i--) {
			EquivalenceClass child_X = eqMembers.get(i);

			boolean createdThisRow = generateExtensionsForRow(eqMembers, i, keepPatterns, candidateBuffer, length);
			anyPatternCreated = anyPatternCreated || createdThisRow;

			eqMembers.remove(i);
			finishMember(child_X, canDiscardBitsets);

			if (anyPatternCreated) {
				executeDfs(child_X, keepPatterns, canDiscardBitsets, candidateBuffer, length + 1);
			}
		}
	}

	/**
	 * Explores an equivalence class breadth-first, generating every member's
	 * extensions before recursing into any of them.
	 *
	 * @param length the number of items in the patterns held by eq's members
	 */
	private void executeBfs(EquivalenceClass eq, boolean keepPatterns, boolean canDiscardBitsets,
			Pattern[] candidateBuffer, int length) {
		List<EquivalenceClass> eqMembers = eq.getClassMembers();
		if (eqMembers == null || eqMembers.isEmpty()) {
			return;
		}
		if (!canExtend(length)) {
			// maximum pattern length reached: release resources without extending further
			for (int i = eqMembers.size() - 1; i >= 0; i--) {
				finishMember(eqMembers.get(i), canDiscardBitsets);
			}
			eqMembers.clear();
			return;
		}

		boolean anyPatternCreated = false;
		for (int i = eqMembers.size() - 1; i >= 0; i--) {
			boolean createdThisRow = generateExtensionsForRow(eqMembers, i, keepPatterns, candidateBuffer, length);
			anyPatternCreated = anyPatternCreated || createdThisRow;
		}

		// Always release id-lists/bitsets for this level once the join phase is
		// done, whether or not any extension was found (the original code only
		// did this when anyPatternCreated was true, leaking id-lists otherwise).
		for (int i = eqMembers.size() - 1; i >= 0; i--) {
			finishMember(eqMembers.get(i), canDiscardBitsets);
		}

		if (anyPatternCreated) {
			for (int i = eqMembers.size() - 1; i >= 0; i--) {
				EquivalenceClass child = eqMembers.get(i);
				executeBfs(child, keepPatterns, canDiscardBitsets, candidateBuffer, length + 1);
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
	 * Joins one equivalence class member against every earlier sibling and
	 * registers any frequent extension found.
	 *
	 * @param length the number of items in the patterns being joined (the
	 *               resulting extensions will contain length + 1 items)
	 * @return true if at least one frequent extension was created for this row
	 */
	private boolean generateExtensionsForRow(List<EquivalenceClass> eqMembers, int i, boolean keepPatterns,
			Pattern[] candidateBuffer, int length) {
		final int minSupport = minSupAbsolute;
		final int newLength = length + 1;
		EquivalenceClass child_X = eqMembers.get(i);
		boolean created = false;

		for (int j = i; j >= 0; j--) {
			EquivalenceClass child_Y = eqMembers.get(j);

			int extensionCount = generateCandidates(child_X.getClassIdentifier(), child_Y.getClassIdentifier(),
					minSupport, candidateBuffer);

			for (int c = 0; c < extensionCount; c++) {
				Pattern extension = candidateBuffer[c];
				IDList newIdList = join(extension, child_X, child_Y, minSupport);
				incrementJoinCounter();
				if (newIdList != null) {
					int candidateSupport = newIdList.getSupport();
					if (candidateSupport >= minSupport) {
						created = true;
						newIdList.setAppearingSequences(extension);
						extension.setCachedSupport(candidateSupport);

						// only expose/count patterns whose length respects the constraints
						if (isLengthAllowed(newLength)) {
							if (keepPatterns) {
								keepPattern(extension);
							}
							incrementPatternCounter();
						}

						EquivalenceClass newEq = new EquivalenceClass(extension);
						newEq.setIdList(newIdList);
						insertClassByPrefix(newEq, child_X, child_Y);
					} else {
						newIdList.recycle();
					}
				}
			}
		}
		return created;
	}

	/**
	 * Generates candidate patterns from the two patterns given as parameters,
	 * writing them into a caller-supplied reusable buffer instead of allocating a
	 * new array on every call.
	 *
	 * @param pattern1        the first pattern
	 * @param pattern2        the second pattern
	 * @param minSupport      the minimum absolute support
	 * @param candidatesOut   reusable buffer (length >= 3) that receives the
	 *                        generated candidates
	 * @return the number of candidates written into candidatesOut, starting at
	 *         index 0
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
	 * Run spade from size 2 sequences
	 * @param database the database
	 * @param dfs whether to perform DFS or BFS
	 * @param keepPatterns  if true, patterns are kept in memory instead of saved to file
	 * @param verbose if true, debugging information is displayed.
	 */
	protected void runSPADEFromSize2Sequences(SequenceDatabase database, boolean dfs, boolean keepPatterns,
			boolean verbose) {
		frequentItems = database.frequentItems();
		Collection<Pattern> size1Patterns = getPatterns(frequentItems);
		List<EquivalenceClass> size2Patterns = database.getSize2FrequentSequences(minSupAbsolute);
		Collection<Pattern> size2sequences = getPatterns(size2Patterns);

		if (keepPatterns) {
			// only save patterns whose length respects the constraints
			if (isLengthAllowed(1)) {
				savePatterns(size1Patterns);
			}
			if (isLengthAllowed(2)) {
				savePatterns(size2sequences);
			}
		}

		size2Patterns.clear();
		database.clear();
		size2Patterns = null;
		database = null;

		this.parallelMode = false;
		// only count patterns whose length is within the allowed bounds
		this.numberOfFrequentPatterns = (isLengthAllowed(1) ? size1Patterns.size() : 0)
				+ (isLengthAllowed(2) ? size2sequences.size() : 0);
		this.intersectionCounter = 0;

		// Sequential loop over top-level roots: one shared buffer is safe here.
		Pattern[] candidateBuffer = new Pattern[MAX_CANDIDATES_PER_JOIN];
		boolean canDiscardBitsets = !outputSequenceIdentifiers;

		while (!frequentItems.isEmpty()) {
			EquivalenceClass frequentAtomClass = frequentItems.remove(frequentItems.size() - 1);
			if (verbose) {
				System.out.println("Exploring... " + frequentAtomClass);
			}
			execute(frequentAtomClass, dfs, keepPatterns, canDiscardBitsets, candidateBuffer, 2);
			recycleIdList(frequentAtomClass);
			MemoryLogger.getInstance().checkMemory();
		}
		joinCount = intersectionCounter;
	}

	/**
	 * Run spade from size 2 sequences in multi-thread mode
	 * @param database the database
	 * @param dfs whether to perform DFS or BFS
	 * @param keepPatterns  if true, patterns are kept in memory instead of saved to file
	 * @param verbose if true, debugging information is displayed.
	 */
	protected void runSPADEFromSize2PatternsParallelized(SequenceDatabase database, boolean dfs, boolean keepPatterns,
			boolean verbose) {
		frequentItems = database.frequentItems();
		Collection<Pattern> size1Sequences = getPatterns(frequentItems);
		List<EquivalenceClass> size2EquivalenceClasses = database.getSize2FrequentSequences(minSupAbsolute);
		Collection<Pattern> size2Sequences = getPatterns(size2EquivalenceClasses);

		if (keepPatterns) {
			// only save patterns whose length respects the constraints
			if (isLengthAllowed(1)) {
				savePatterns(size1Sequences);
			}
			if (isLengthAllowed(2)) {
				savePatterns(size2Sequences);
			}
		}

		this.parallelMode = true;
		// only count patterns whose length is within the allowed bounds
		int initialCount = (isLengthAllowed(1) ? size1Sequences.size() : 0)
				+ (isLengthAllowed(2) ? size2Sequences.size() : 0);
		frequentPatternsCounter.set(initialCount);
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
					// Each task/thread gets its own candidate buffer: it must never be
					// shared across concurrently running branches.
					Pattern[] localBuffer = new Pattern[MAX_CANDIDATES_PER_JOIN];
					execute(frequentAtom, dfs, keepPatterns, canDiscardBitsets, localBuffer, 2);
					recycleIdList(frequentAtom);
				}));
				MemoryLogger.getInstance().checkMemory();
			}
			pool.shutdown();

			for (Future<?> task : tasks) {
				task.get();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Interrupted while waiting for SPADE search tasks", e);
		} catch (ExecutionException e) {
			throw new RuntimeException("A parallel SPADE search task failed", e.getCause());
		} finally {
			pool.shutdownNow();
			try {
				if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
					System.err.println("SPADE: worker pool did not fully terminate within the shutdown grace period.");
				}
			} catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
			}
		}

		numberOfFrequentPatterns = frequentPatternsCounter.get();
		joinCount = joinCounter.get();
		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * Print statistics about the latest algorithm execution.
	 * @return a String
	 */
	public String printStatistics() {
		StringBuilder sb = new StringBuilder(200);
		sb.append("=============  SPADE v.2.67 - STATISTICS =============\n Total time ~ ");
		sb.append(getRunningTime());
		sb.append(" ms\n Frequent sequences count : ");
		sb.append(numberOfFrequentPatterns);
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