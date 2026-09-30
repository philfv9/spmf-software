package ca.pfv.spmf.algorithms.sequentialpatterns.prefixSpan_AGP_fast;

import java.io.BufferedWriter;
import java.io.FileWriter;
/*
 * Copyright Antonio Gomariz Peñalver 2013
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
 */
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.function.Consumer;

import ca.pfv.spmf.datastructures.collections.list.ArrayListInt;
import ca.pfv.spmf.datastructures.collections.list.ListInt;
import ca.pfv.spmf.datastructures.collections.list.ListObject;
import ca.pfv.spmf.datastructures.collections.map.AMapIntToInt;
import ca.pfv.spmf.datastructures.collections.map.MapIntToInt;
import ca.pfv.spmf.datastructures.collections.map.MapIntToObject;
import ca.pfv.spmf.tools.MemoryLogger;

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
/**
 * This is an implementation of the PrefixSpan algorithm by Antonio Gomariz
 * Peñalver(AGP). PrefixSpan was proposed by Pei et al. 2001. This algorithm was
 * inspired in the implementation of SPMF and, from it, I changed and optimized
 * some things. <br/>
 * <br/>
 * 
 * NOTE: This implementation saves the pattern to a file as soon as they are
 * found or can keep the pattern into memory if no output path is provided by
 * the user.
 * 
 * @author Antonio Gomariz Peñalver, Philippe Fournier-Viger (cleanup and
 *         further optimizations in SPMF 2.67)
 */
public class AlgoPrefixSpan_AGP {

	/**
	 * the minimum support threshold as a value in [0,1]
	 */
	protected double minSupRelative;
	/**
	 * The minimum support relative threshold, i.e. the minimum number of sequences
	 * where the patterns have to be
	 */
	protected double minSupAbsolute;

	/** The minimum number of items a pattern must contain (default: 0). */
	private int minimumPatternLength = 0;

	/** The maximum number of items a pattern may contain (default: Integer.MAX_VALUE). */
	private int maximumPatternLength = Integer.MAX_VALUE;

	/**
	 * Writer used when saving results to a file (replaces SaverIntoFile)
	 */
	private BufferedWriter writer = null;

	/**
	 * In-memory pattern storage used when saving results to memory (replaces
	 * SaverIntoMemory)
	 */
	private Sequences patterns = null;

	/**
	 * Path of the output file (null if saving to memory)
	 */
	private String outputFilePath = null;

	/**
	 * Start and end points in order to calculate the overall time taken by the
	 * algorithm
	 */
	protected long start, end;
	/**
	 * Number of frequent patterns found by the algorithm
	 */
	private int numberOfFrequentPatterns = 0;

	// -----------------------------------------------------------------------
	// Fields used by the main PrefixSpan recursion (formerly held by the
	// separate RecursionPrefixSpan_AGP class)
	// -----------------------------------------------------------------------

	/**
	 * Consumer that handles saving of patterns, either to a file or to memory
	 */
	private Consumer<Pattern> patternSaver;

	/**
	 * Number of frequent items, once renumbered to a dense 0..n-1 range (see
	 * below). Bounds the size of every reusable array used in the recursion.
	 */
	private int numFrequentItems;

	/**
	 * appearances of the frequent items, indexed directly by their dense id
	 * (replaces the former Map<Integer,BitSet>, so lookups are a plain array access
	 * instead of a hashed, boxed one)
	 */
	private BitSet[] frequentItemsByRank;

	/**
	 * dense id -> original item id, only needed to translate a pattern back to its
	 * real item ids at the moment it is saved
	 */
	private int[] denseIdToOriginalItem;

	/**
	 * original item id -> dense id, only used once while building the initial
	 * pseudo-projected database
	 */
	private MapIntToInt itemRemap;

	/**
	 * true if every itemset of the database is a singleton, i.e. the database is
	 * actually a plain sequence of items rather than a sequence of itemsets. In
	 * that case the "postfix" (same itemset) relation between two items can never
	 * occur, which lets the pair-counting hot path use a smaller, simpler key space
	 * (see pairKey).
	 */
	private boolean itemSequenceMode;

	/**
	 * Reusable bitset used to track projection points already made for the sequence
	 * currently being projected. It is cleared and reused for every sequence
	 * instead of allocating a new Set. This scratch space is only used within a
	 * single, non-reentrant call to makePseudoProjections, so sharing one instance
	 * for the whole algorithm is safe regardless of recursion depth.
	 */
	private final BitSet projectionPointsSeen = new BitSet();

	// -----------------------------------------------------------------------
	// Object pools: makePseudoProjections used to allocate a brand new
	// PseudoSequenceDatabase (and one PseudoSequence per matching original
	// sequence) at every single node of the recursion. Since projections are
	// explored depth-first and released (see releaseDatabase) right after
	// the branch that used them is fully explored, a simple free-list pool
	// behaves like a stack that mirrors the recursion, instead of leaving
	// all of that churn to the garbage collector.
	// -----------------------------------------------------------------------

	private final ArrayDeque<PseudoSequenceDatabase> databasePool = new ArrayDeque<PseudoSequenceDatabase>();
	private final ArrayDeque<PseudoSequence> sequencePool = new ArrayDeque<PseudoSequence>();

	// -----------------------------------------------------------------------
	// Reusable, generation-stamped storage for the pair counting performed
	// at every node of the recursion (findAllFrequentPairs / addPair).
	// Instead of allocating a new HashMap<Integer,Pair> and HashSet<Integer>
	// on every single call, we keep one open-addressing hash table of Pair
	// "slots" per recursion depth (see PairCounterContext), keyed by the
	// dense item id (and the postfix flag).
	//
	// Earlier this table used to be a plain array pre-sized to the *whole*
	// frequent item domain for every depth, which meant memory usage was
	// O(depth * numFrequentItems) regardless of how many items actually ever
	// showed up that deep in the recursion. Since deeper projections almost
	// always only involve a small fraction of the item domain, that
	// pre-sizing wasted a lot of memory. The table below instead starts tiny
	// and grows (like a HashMap) only with the number of distinct items
	// actually seen at that depth.
	//
	// IMPORTANT: a single global set of slots for the whole algorithm is
	// NOT safe, because prefixSpanLoop recurses into deeper depths *while*
	// it is still iterating the pairs found at the current depth; a naive
	// shared table would let the inner call reclaim/clear a slot that the
	// outer call's Pair reference still points to. Giving each depth its
	// own context avoids this: since the recursion is depth-first, a given
	// depth's context is only read again after every deeper call has fully
	// returned, and deeper calls always use a context for a strictly larger
	// depth.
	// -----------------------------------------------------------------------

	/**
	 * Reusable pair-counting workspace for one recursion depth, implemented as an
	 * open-addressing hash table (linear probing, no tombstones since entries are
	 * never removed, only "logically" reset via the stamps below). Since a key's
	 * probe sequence only depends on the table's capacity (which never shrinks), a
	 * stale entry from an earlier call is always found again on the same probe
	 * sequence, so entries can be safely reused across calls without ever being
	 * cleared. See the note above for why contexts are kept one per depth instead
	 * of shared.
	 */
	private static final class PairCounterContext {
		int[] keys;
		Pair[] slots;
		int[] callStamp;
		long[] sequenceStamp;
		int[] touched;
		int touchedCount;
		int usedSlots;
		int callCounter;

		PairCounterContext() {
			int initialCapacity = 16;
			keys = new int[initialCapacity];
			Arrays.fill(keys, -1);
			slots = new Pair[initialCapacity];
			callStamp = new int[initialCapacity];
			sequenceStamp = new long[initialCapacity];
			touched = new int[initialCapacity];
		}
	}

	/**
	 * Pair-counting workspaces, indexed by recursion depth (k). Lazily populated
	 * and grown as the recursion goes deeper; cleared whenever the item renumbering
	 * changes (i.e. at the start of a run).
	 */
	private final List<PairCounterContext> pairContextsByDepth = new ArrayList<PairCounterContext>();

	/**
	 * Standard constructor. It takes the minimum support threshold (from 1 up to 0)
	 * and an abstraction creator
	 * 
	 * @param minsupRelative the relative minimum support threshold
	 */
	public AlgoPrefixSpan_AGP(double minsupRelative) {
		this.minSupRelative = minsupRelative;
	}

	/**
	 * Sets the minimum pattern length constraint.
	 *
	 * @param minimumPatternLength the minimum number of items a pattern must contain
	 */
	public void setMinimumPatternLength(int minimumPatternLength) {
		this.minimumPatternLength = minimumPatternLength;
	}

	/**
	 * Sets the maximum pattern length constraint.
	 *
	 * @param maximumPatternLength the maximum number of items a pattern may contain
	 */
	public void setMaximumPatternLength(int maximumPatternLength) {
		this.maximumPatternLength = maximumPatternLength;
	}

	/**
	 * Method that starts the execution of the algorithm.
	 * 
	 * @param database                  The original database in which we apply
	 *                                  PrefixSpan
	 * @param keepPatterns              Flag indicating if the user want to keep the
	 *                                  frequent patterns or he just want the amount
	 *                                  of them
	 * @param verbose                   Flag for debugging purposes
	 * @param outputFilePath            Path pointing out to the file where the
	 *                                  output, composed of frequent patterns, has
	 *                                  to be kept. If, conversely, this parameter
	 *                                  is null, we understand that the user wants
	 *                                  the output in the main memory
	 * @param outputSequenceIdentifiers if true, sequences ids will be output for
	 *                                  each pattern
	 * @throws IOException
	 */
	public void runAlgorithm(SequenceDatabase database, boolean keepPatterns, boolean verbose, String outputFilePath,
			boolean outputSequenceIdentifiers) throws IOException {
		// calculation of the absolute minimum support

		minSupAbsolute = (int) Math.ceil((minSupRelative * database.size()) - 0.0000001);
		if (this.minSupAbsolute == 0) { // protection
			this.minSupAbsolute = 1;
		}
		// reset the stats about memory usage
		MemoryLogger.getInstance().reset();
		// keeping the starting time
		start = System.currentTimeMillis();
		// Starting PrefixSpanAlgorithm
		prefixSpan(database, keepPatterns, verbose, outputFilePath, outputSequenceIdentifiers);
		// keeping the ending time
		end = System.currentTimeMillis();
		// Search for frequent patterns: Finished
		finishSaving();
	}

	/**
	 * Finalizes saving: closes file writer or sorts in-memory patterns. Replaces
	 * Saver.finish()
	 */
	private void finishSaving() {
		if (writer != null) {
			// Replaces SaverIntoFile.finish()
			try {
				writer.close();
			} catch (IOException e) {
				System.err.println("Error closing file writer: " + e.getMessage());
			}
		} else if (patterns != null) {
			// Replaces SaverIntoMemory.finish()
			patterns.sort();
		}
	}

	/**
	 * Method that executes the first steps before calling the actual main method of
	 * PrefixSpan. In particular, the original database is fully converted to a
	 * pseudosequece database, removing the infrequent items that appeared in the
	 * original dabase
	 * 
	 * @param database                  The original database
	 * @param keepPatterns              Flag indicating if the user want to keep the
	 *                                  frequent patterns or he just want the amount
	 *                                  of them
	 * @param verbose                   Flag for debugging purposes
	 * @param outputFilePath            Path pointing out to the file where the
	 *                                  output, composed of frequent patterns, has
	 *                                  to be kept. If, conversely, this parameter
	 *                                  is null, we understand that the user wants
	 *                                  the output in the main memory
	 * @param outputSequenceIdentifiers if true, sequences ids will be output for
	 *                                  each pattern
	 * @throws IOException
	 */
	protected void prefixSpan(SequenceDatabase database, boolean keepPatterns, boolean verbose, String outputFilePath,
			boolean outputSequenceIdentifiers) throws IOException {
		// If we do no have any file path
		if (outputFilePath == null) {
			// Replaces: saver = new SaverIntoMemory(outputSequenceIdentifiers)
			this.outputFilePath = null;
			this.patterns = new Sequences();
			this.patternSaver = p -> patterns.addSequence(p, p.size());
			this.writer = null;
		} else {
			// Replaces: saver = new SaverIntoFile(outputFilePath,
			// outputSequenceIdentifiers)
			this.outputFilePath = outputFilePath;
			this.patterns = null;
			this.writer = new BufferedWriter(new FileWriter(outputFilePath));
			writer.write("@FILETYPE=\"Frequent sequential patterns\"");
			writer.newLine();
			writer.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
			writer.newLine();
			
			this.patternSaver = p -> {
				try {
					writer.write(p.toStringToFile(outputSequenceIdentifiers));
					writer.newLine();
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			};
		}
		// Flag telling if every itemset of the database is a singleton, i.e.
		// the database is really just a sequence of items. This enables a
		// smaller/simpler key space for the pair-counting hot path below.
		this.itemSequenceMode = database.isItemSequenceDatabase();
		/*
		 * We build a dense 0..n-1 renumbering of the frequent items so that, from this
		 * point on, every item related lookup in the recursion (support bitsets, pair
		 * counting, ...) is a direct array access instead of a HashMap lookup with
		 * boxed Integer keys
		 */
		buildItemRenumbering(database.getFrequentItems());
		/*
		 * projection of the original database in order to obtain a pseudosequence
		 * database
		 */
		PseudoSequenceDatabase pseudoDatabase = projectInitialDatabase(database);
		itemRemap = null; // only needed for the initial projection above
		// We reset the frequent pattern counter and run the actual main loop
		numberOfFrequentPatterns = 0;
		executePrefixSpan(pseudoDatabase, keepPatterns, verbose);
		releaseDatabase(pseudoDatabase);
		// check the memory usage for statistics
		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * It builds a dense renumbering of the frequent items (0..n-1) and resets every
	 * reusable, size-bounded structure used by the hot path of the recursion
	 * (pair-counting contexts). Replacing the original items (which can be
	 * arbitrarily large or sparse) by a dense range lets the rest of the algorithm
	 * use plain arrays instead of HashMaps for item lookups.
	 * 
	 * @param originalFrequentItems map of original item -> sequence ids where it
	 *                              appears
	 */
	private void buildItemRenumbering(MapIntToObject<BitSet> originalFrequentItems) {
		ListInt sortedItems = new ArrayListInt();
		MapIntToObject<BitSet>.KeyIterator keyIterator = originalFrequentItems.iteratorForKeys();
		while (keyIterator.hasNext()) {
			sortedItems.add(keyIterator.next());
		}
		sortedItems.sortByIncreasingOrder();
		numFrequentItems = sortedItems.size();
		denseIdToOriginalItem = new int[numFrequentItems];
		frequentItemsByRank = new BitSet[numFrequentItems];
		itemRemap = new AMapIntToInt();
		for (int denseId = 0; denseId < numFrequentItems; denseId++) {
			int originalItem = sortedItems.get(denseId);
			denseIdToOriginalItem[denseId] = originalItem;
			frequentItemsByRank[denseId] = originalFrequentItems.get(originalItem);
			itemRemap.put(originalItem, denseId);
		}
		// the item renumbering just changed, so any previously accumulated
		// per-depth pair-counting context is now stale
		pairContextsByDepth.clear();
	}

	/**
	 * Method to get the outlined information about the search for frequent
	 * sequences by means of PrefixSpan algorithm as a string.
	 * 
	 * @return a string
	 */
	public String printStatistics() {
		StringBuilder sb = new StringBuilder(200);
		sb.append("=============  Algorithm - STATISTICS =============\n Total time ~ ");
		sb.append(getRunningTime());
		sb.append(" ms\n");
		sb.append(" Frequent sequences count : ");
		sb.append(numberOfFrequentPatterns);
		sb.append('\n');
		sb.append(" Max memory (mb):");
		sb.append(MemoryLogger.getInstance().getMaxMemory());
		sb.append('\n');
		sb.append(printSaver());
		sb.append('\n');
		sb.append("===================================================\n");
		return sb.toString();
	}

	/**
	 * Returns a string describing where/what was saved. Replaces Saver.print()
	 *
	 * @return descriptive string
	 */
	private String printSaver() {
		if (outputFilePath != null) {
			// Replaces SaverIntoFile.print()
			return "Content at file " + outputFilePath;
		} else {
			// Replaces SaverIntoMemory.print()
			return (patterns != null) ? patterns.toStringToFile(false) : "";
		}
	}

	/**
	 * Get the number of frequent patterns found.
	 * 
	 * @return the number of frequent patterns.
	 */
	public int getNumberOfFrequentPatterns() {
		return numberOfFrequentPatterns;
	}

	/**
	 * It gets the time spent by the algoritm in its execution.
	 * 
	 * @return the time spent (long).
	 */
	public long getRunningTime() {
		return (end - start);
	}

	/**
	 * It gets the absolute minimum support, i.e. the minimum number of database
	 * sequences where a pattern has to appear
	 * 
	 * @return the minimum support (double)
	 */
	public double getAbsoluteMinSupport() {
		return minSupAbsolute;
	}

	/**
	 * It projects the initial database converting each original sequence to
	 * pseudosequences (renumbered to their dense item ids) in order to enable the
	 * later pseudoprojections in the main loop of PrefixSpan
	 * 
	 * @param database The original Database
	 * @return the initial pseudosequence database
	 */
	private PseudoSequenceDatabase projectInitialDatabase(SequenceDatabase database) {
		PseudoSequenceDatabase initialContext = new PseudoSequenceDatabase();
		ListObject<Sequence> sequences = database.getSequences();
		// For each database sequence
		for (int i = 0; i < sequences.size(); i++) {
			Sequence sequence = sequences.get(i);
			// The new pseudosequences are optimized, since do not have the infrequent items
			// and their items have been renumbered to their dense id
			Sequence optimizedSequence = sequence.cloneSequenceMinusItems(itemRemap);
			if (optimizedSequence.size() != 0) {
				/*
				 * If after remove the infrequent items, we remove all the items of an original
				 * sequence, we insert an empty pseudosequence in order not to affect to the
				 * absolute minimum support
				 */
				PseudoSequence pseudoSequence = new PseudoSequence(optimizedSequence, 0, 0);
				initialContext.addSequence(pseudoSequence);
			}
		}
		return initialContext;
	}

	/**
	 * It clears all the attributes of AlgoPrefixSpan class
	 */
	public void clear() {
		frequentItemsByRank = null;
		denseIdToOriginalItem = null;
		itemRemap = null;
		pairContextsByDepth.clear();
		databasePool.clear();
		sequencePool.clear();
		clearSaver();
	}

	/**
	 * Clears saving resources. Replaces Saver.clear()
	 */
	private void clearSaver() {
		// Replaces SaverIntoFile.clear()
		writer = null;

		// Replaces SaverIntoMemory.clear()
		if (patterns != null) {
			patterns.clear();
			patterns = null;
		}
	}

	// -----------------------------------------------------------------------
	// Main PrefixSpan recursion (formerly the RecursionPrefixSpan_AGP class)
	// -----------------------------------------------------------------------

	/**
	 * It acquires a PseudoSequenceDatabase from the pool (or creates a new one if
	 * the pool is empty) instead of allocating a brand new wrapper (and its backing
	 * ArrayList) for every single projection performed during the recursion.
	 * 
	 * @return a ready-to-use, empty PseudoSequenceDatabase
	 */
	private PseudoSequenceDatabase acquireDatabase() {
		PseudoSequenceDatabase db = databasePool.poll();
		return (db != null) ? db : new PseudoSequenceDatabase();
	}

	/**
	 * It returns a PseudoSequenceDatabase (and every PseudoSequence it contains) to
	 * the pool so it can be reused by later projections. Must only be called once
	 * the branch of the recursion that database was created for has been fully
	 * explored.
	 * 
	 * @param db the database to release, may be null
	 */
	private void releaseDatabase(PseudoSequenceDatabase db) {
		if (db == null) {
			return;
		}
		ListObject<PseudoSequence> pseudoSequences = db.getPseudoSequences();
		for (int i = 0; i < pseudoSequences.size(); i++) {
			sequencePool.push(pseudoSequences.get(i));
		}
		db.clearKeepCapacity();
		databasePool.push(db);
	}

	/**
	 * It acquires a PseudoSequence from the pool, or creates a new, empty one if
	 * the pool is empty. The caller must initialize it through
	 * initFromProjection/initFromSequence before use.
	 * 
	 * @return a pooled or freshly created PseudoSequence
	 */
	private PseudoSequence acquireSequence() {
		PseudoSequence sequence = sequencePool.poll();
		return (sequence != null) ? sequence : new PseudoSequence();
	}

	/**
	 * It encodes a (item, postfix) combination into a key used by the pair-counting
	 * hash table. In item-sequence mode the postfix flag is always false (a
	 * singleton itemset can never yield a "same itemset" relation), so the item id
	 * itself is used directly, halving the size of the key space and avoiding the
	 * shift/or entirely.
	 */
	private int pairKey(int item, boolean postfix) {
		return itemSequenceMode ? item : ((item << 1) | (postfix ? 1 : 0));
	}

	/**
	 * It mixes a pair key so it spreads evenly over the hash table's capacity (a
	 * power of two), regardless of how the dense item ids happen to be distributed.
	 */
	private static int mixKey(int key) {
		int h = key * 0x9E3779B1;
		return h ^ (h >>> 16);
	}

	/**
	 * It finds the slot a key belongs to in the given context's hash table, i.e.
	 * either the slot already holding that key (possibly stale, from an earlier
	 * call at this depth) or the first free slot along its probe sequence. Since
	 * keys are never removed, this is deterministic and capacity-dependent only,
	 * which is what makes reusing stale slots across calls safe.
	 * 
	 * @param ctx the workspace whose table is searched
	 * @param key the pair key, see pairKey
	 * @return the physical slot index for that key
	 */
	private static int findSlotForKey(PairCounterContext ctx, int key) {
		int mask = ctx.keys.length - 1;
		int idx = mixKey(key) & mask;
		while (ctx.keys[idx] != -1 && ctx.keys[idx] != key) {
			idx = (idx + 1) & mask;
		}
		return idx;
	}

	/**
	 * It doubles the capacity of the given context's hash table and rehashes every
	 * key inserted so far into it. Only called when the table's load factor would
	 * otherwise get too high, so its amortized cost over many insertions stays O(1)
	 * per insertion, just like a standard growable HashMap.
	 * 
	 * @param ctx the workspace to grow
	 */
	private static void growContext(PairCounterContext ctx) {
		int oldCapacity = ctx.keys.length;
		int[] oldKeys = ctx.keys;
		Pair[] oldSlots = ctx.slots;
		int[] oldCallStamp = ctx.callStamp;
		long[] oldSequenceStamp = ctx.sequenceStamp;

		int newCapacity = oldCapacity * 2;
		ctx.keys = new int[newCapacity];
		Arrays.fill(ctx.keys, -1);
		ctx.slots = new Pair[newCapacity];
		ctx.callStamp = new int[newCapacity];
		ctx.sequenceStamp = new long[newCapacity];
		ctx.touched = new int[newCapacity];

		for (int i = 0; i < oldCapacity; i++) {
			if (oldKeys[i] == -1) {
				continue;
			}
			int idx = findSlotForKey(ctx, oldKeys[i]);
			ctx.keys[idx] = oldKeys[i];
			ctx.slots[idx] = oldSlots[i];
			ctx.callStamp[idx] = oldCallStamp[i];
			ctx.sequenceStamp[idx] = oldSequenceStamp[i];
		}
		// the physical slots touched by the call in progress moved around
		// during rehashing, so 'touched' has to be rebuilt from the stamps
		ctx.touchedCount = 0;
		for (int i = 0; i < newCapacity; i++) {
			if (ctx.keys[i] != -1 && ctx.callStamp[i] == ctx.callCounter) {
				ctx.touched[ctx.touchedCount++] = i;
			}
		}
	}

	/**
	 * It returns the pair-counting workspace for the given recursion depth,
	 * creating it lazily on first use. See PairCounterContext for why one workspace
	 * per depth is required for correctness.
	 * 
	 * @param depth the recursion depth (the "k" parameter of prefixSpanLoop)
	 * @return the workspace for that depth
	 */
	private PairCounterContext pairContextForDepth(int depth) {
		while (pairContextsByDepth.size() <= depth) {
			pairContextsByDepth.add(null);
		}
		PairCounterContext ctx = pairContextsByDepth.get(depth);
		if (ctx == null) {
			ctx = new PairCounterContext();
			pairContextsByDepth.set(depth, ctx);
		}
		return ctx;
	}

	/**
	 * It executes the actual PrefixSpan Algorithm
	 * 
	 * @param pseudoDatabase the pseudoprojected database (without infrequent items)
	 * @param keepPatterns   Flag indicating if the user wants to keep the results
	 *                       or he is just interested in the number of frequent
	 *                       patterns
	 * @param verbose        Flag for debugging purposes
	 */
	private void executePrefixSpan(PseudoSequenceDatabase pseudoDatabase, boolean keepPatterns, boolean verbose) {
		if (verbose) {
			System.out.println(numFrequentItems + " frequent items");
		}
		// Dense ids are already in ascending sorted order by construction, so
		// we can iterate them directly instead of sorting a key set on every run
		for (int item = 0; item < numFrequentItems; item++) {
			if (verbose) {
				System.out.println("Projecting item = " + denseIdToOriginalItem[item] + " (" + (item + 1) + "/"
						+ numFrequentItems + ")");
			}
			// We make a projection in the original database
			PseudoSequenceDatabase projectedContext = makePseudoProjections(item, pseudoDatabase, false, true);
			// And we create a new 1-pattern with that frequent item (translated back to its
			// original id)
			Pattern prefix = new Pattern(denseIdToOriginalItem[item], false);
			// And we insert it its appearances. frequentItemsByRank is read-only
			// after buildItemRenumbering, so the 1-pattern can share the bitset
			// reference instead of cloning it (one fewer allocation per frequent item)
			prefix.setAppearingIn(frequentItemsByRank[item]);
			// save pattern only if it satisfies the minimum pattern length
			if (keepPatterns && prefix.size() >= minimumPatternLength) {
				patternSaver.accept(prefix);
			}
			// We update the number of frequent patterns
			numberOfFrequentPatterns++;
			// recurse only if maximum pattern length allows going deeper
			if (projectedContext != null && projectedContext.size() >= minSupAbsolute
					&& maximumPatternLength > 1) {
				// And we call the main loop
				prefixSpanLoop(prefix, 2, projectedContext, keepPatterns, verbose);
			}
			releaseDatabase(projectedContext);
		}
	}

	/**
	 * It projects the database given as parameter
	 * 
	 * @param item          The item from which we make the projection
	 * @param database      The database where we make the projection
	 * @param equalRelation the relation of the item to project with respect to the
	 *                      previous item
	 * @param firstTime     Flag that points out if it the first time that
	 * @return The new projected database
	 */
	private PseudoSequenceDatabase makePseudoProjections(int item, PseudoSequenceDatabase database,
			boolean equalRelation, boolean firstTime) {
		// The projected pseudo-database, taken from the pool rather than allocated
		PseudoSequenceDatabase newProjectedDatabase = acquireDatabase();
		ListObject<PseudoSequence> pseudoSequences = database.getPseudoSequences();
		int numSequences = pseudoSequences.size();
		for (int sequenceIndex = 0; sequenceIndex < numSequences; sequenceIndex++) { // for each sequence
			PseudoSequence sequence = pseudoSequences.get(sequenceIndex);
			/*
			 * We guess the maximum size that our new projected database can achieve. If its
			 * number of potential sequences is less than the minimum support, we can stop
			 * projecting
			 */
			int potentialSize = newProjectedDatabase.size() + numSequences - sequenceIndex;
			if (potentialSize < minSupAbsolute) {
				releaseDatabase(newProjectedDatabase);
				return null;
			}
			/*
			 * Flag indicating if the current sequence has already been projected for the
			 * new projected database
			 */
			boolean alreadyProjected = false;
			// Initialization of the new projected sequence for the current one
			PseudoSequence newSequence = null;
			// Initialization of the number of projections done in the current sequence
			int numberOfProjections = 0;
			// Reused bitset keeping the projections already done for this sequence,
			// instead of allocating a new Set for every sequence
			projectionPointsSeen.clear();
			int projCount = sequence.numberOfProjectionsIncluded();
			// For all the existing projections in the current sequence
			for (int k = 0; k < projCount; k++) {
				int sequenceSize = sequence.size(k);
				// for each itemset of the sequence
				for (int i = 0; i < sequenceSize; i++) {
					// we get the index ofthe given item to project in current the itemset
					int index = sequence.indexOf(k, i, item);
					// If the item has been found and either is the first projection or it has the
					// required relation
					if (index != -1 && (firstTime || (sequence.isPostfix(k, i) == equalRelation))) {
						int itemsetSize = sequence.getSizeOfItemsetAt(k, i);
						// if the found item is not the last item of the itemset
						if (index != itemsetSize - 1) {
							// If this sequence has not been yet projected
							if (!alreadyProjected) {
								// A new pseudosequence is created starting from the next point to the found
								// item
								newSequence = acquireSequence();
								newSequence.initFromProjection(sequence, i, index + 1, k);
								// We keep the projection point
								projectionPointsSeen.set(sequence.getFirstItemset(k) + i);
								// If the new pseudosequence has more than one item
								if (newSequence.size(numberOfProjections) > 0) {
									// we increase the number of projections
									numberOfProjections++;
									// And we add the new projected sequence to the new database
									newProjectedDatabase.addSequence(newSequence);
								}
								/*
								 * We set the flag to true, indicating that the current sequence has been
								 * already projected
								 */
								alreadyProjected = true;
							} else {
								/*
								 * If the sequence is already projected and the projection point has not been
								 * previously used
								 */
								int projectionPoint = sequence.getFirstItemset(k) + i;
								if (!projectionPointsSeen.get(projectionPoint)) {
									projectionPointsSeen.set(projectionPoint);
									/*
									 * We make another projection in the same sequence previously projected, adding
									 * a new projection point
									 */
									newSequence.addProjectionPoint(k, sequence, i, index + 1);
								}
							}
							/*
							 * if the found item is the last item of the sequence and the item where it is,
							 * it is not the last itemset of the sequence
							 */
						} else if ((i != sequenceSize - 1)) {
							// and has not been yet projected
							if (!alreadyProjected) {
								/*
								 * We create a new projected sequence starting in the next itemset to where the
								 * item appeared
								 */
								newSequence = acquireSequence();
								newSequence.initFromProjection(sequence, i + 1, 0, k);
								// And we count the projection
								projectionPointsSeen.set(sequence.getFirstItemset(k) + i);
								// If there is any item in the new sequence
								if (newSequence.size(numberOfProjections) > 0) {
									// we increase the number of projections
									numberOfProjections++;
									// And we add the new projected sequence to the new database
									newProjectedDatabase.addSequence(newSequence);
								}
								/*
								 * We set the flag to true, indicating that the current sequence has been
								 * already projected
								 */
								alreadyProjected = true;
							} else {
								/*
								 * If the sequence is already projected and the projection point has not been
								 * previously used
								 */
								int projectionPoint = sequence.getFirstItemset(k) + i;
								if (!projectionPointsSeen.get(projectionPoint)) {
									projectionPointsSeen.set(projectionPoint);
									/*
									 * We make another projection in the same sequence previously projected, adding
									 * a new projection point
									 */
									newSequence.addProjectionPoint(k, sequence, i + 1, 0);
								}
							}
						}
					}
				}
			}
		}
		return newProjectedDatabase;
	}

	/**
	 * Method that executes the main loop of prefixSpan for all the patterns with a
	 * size greater than 1
	 * 
	 * @param prefix       prefix from which we made the projected database and
	 *                     where the frequent items that we find will be added
	 * @param k            size of patterns that are going to be generated
	 * @param context      prefix-projected databases
	 * @param keepPatterns flag indicating if we want to keep the output or we are
	 *                     interesting in just the number of frequent patterns
	 * @param verbose      flag for debuggin purposes
	 */
	private void prefixSpanLoop(Pattern prefix, int k, PseudoSequenceDatabase context, boolean keepPatterns,
			boolean verbose) {
		// find frequent items that appear in the given pseudosequence database,
		// using this depth's own reusable workspace (see PairCounterContext).
		PairCounterContext pairCtx = pairContextForDepth(k);
		findAllFrequentPairs(context.getPseudoSequences(), pairCtx);
		if (verbose) {
			StringBuilder tab = new StringBuilder();
			for (int i = 0; i < k - 2; i++) {
				tab.append('\t');
			}
			System.out.println(tab + "Projecting prefix = " + prefix);
			System.out.print(tab + "\tFound " + pairCtx.touchedCount + " frequent items in this projection\n");
		}
		// For each pair found,
		for (int t = 0; t < pairCtx.touchedCount; t++) {
			Pair pair = pairCtx.slots[pairCtx.touched[t]];
			// if the item is frequent.
			if (pair.getSupport() >= minSupAbsolute) {
				// create the new pattern (translating the dense id back to the original item
				// id)
				Pattern newPrefix = prefix.clonePattern();
				newPrefix.add(denseIdToOriginalItem[pair.getItem()], pair.isPostfix());
				// build the projected database with respect to this frequent item (the item
				// which forms the prefix)
				PseudoSequenceDatabase projection = makePseudoProjections(pair.getItem(), context, pair.isPostfix(),
						false);
				// We add its set of sequences where the prefix appear
				newPrefix.setAppearingIn((BitSet) (pair.getSequencesID().clone()));
				// save pattern only if it meets the minimum pattern length
				if (keepPatterns && newPrefix.size() >= minimumPatternLength) {
					patternSaver.accept(newPrefix);
				}
				// update the number of frequent patterns
				numberOfFrequentPatterns++;
				// recurse only if the pattern can still grow within the maximum length
				if (projection != null && projection.size() >= minSupAbsolute
						&& k < maximumPatternLength) {
					// We make a recursive call to the main method
					prefixSpanLoop(newPrefix, k + 1, projection, keepPatterns, verbose); // r�cursion
				}
				releaseDatabase(projection);
			}
		}
	}

	/**
	 * Method to find all frequent items in a database. This is for k> 1. Rather
	 * than allocating a new HashMap<Integer,Pair> and HashSet<Integer> on every
	 * single call (this method runs at every node of the recursion), we use the
	 * given depth's open-addressing hash table of Pair "slots" keyed by the (dense)
	 * item id. A slot belongs to the current call only if its stamp matches the
	 * current call counter, so the table never needs clearing.
	 * 
	 * The original code used a single k-loop with an inner "if (k > 0 && i > 0)
	 * continue loop1" branch evaluated for every itemset of every sequence: for the
	 * first projection (k=0) all itemsets are scanned, while for additional
	 * projections (k>0) only the first itemset matters (subsequent ones overlap
	 * with k=0's view). Splitting that into two separate loops removes the
	 * per-itemset branch from the hot path entirely.
	 * 
	 * @param sequences the sequences from the database
	 * @param ctx       the reusable workspace for the current recursion depth
	 */
	private void findAllFrequentPairs(ListObject<PseudoSequence> sequences, PairCounterContext ctx) {
		ctx.callCounter++;
		ctx.touchedCount = 0;

		int numSequences = sequences.size();
		for (int s = 0; s < numSequences; s++) {
			PseudoSequence sequence = sequences.get(s);
			int sequenceId = sequence.getId();
			int projCount = sequence.numberOfProjectionsIncluded();

			// k = 0: scan all itemsets of the first projection
			int size0 = sequence.size(0);
			for (int i = 0; i < size0; i++) {
				// we get the original itemset
				Itemset itemset = sequence.getItemset(i, 0);
				// We obtain the beginning of that itemset for our projection
				int beginning = sequence.getBeginningOfItemset(0, i);
				// In item-sequence mode a singleton itemset can never start a
				// postfix relation, so we skip computing it altogether
				boolean postfix = itemSequenceMode ? false : sequence.isPostfix(0, i);
				/*
				 * And for each item from the beginning we add a new Pair in order to find the
				 * frequent items in the projection*
				 */
				for (int j = beginning; j < itemset.size(); j++) {
					addPair(ctx, sequenceId, itemset.get(j), postfix);
				}
			}
			// k > 0: only the first itemset of each additional projection
			for (int k = 1; k < projCount; k++) {
				// we get the original itemset
				Itemset itemset = sequence.getItemset(0, k);
				// We obtain the beginning of that itemset for our projection
				int beginning = sequence.getBeginningOfItemset(k, 0);
				// In item-sequence mode a singleton itemset can never start a
				// postfix relation, so we skip computing it altogether
				boolean postfix = itemSequenceMode ? false : sequence.isPostfix(k, 0);
				/*
				 * And for each item from the beginning we add a new Pair in order to find the
				 * frequent items in the projection*
				 */
				for (int j = beginning; j < itemset.size(); j++) {
					addPair(ctx, sequenceId, itemset.get(j), postfix);
				}
			}
		}
	}

	/**
	 * It registers one occurrence of an (item, postfix) pair for the sequence
	 * currently being scanned, using the given depth's reusable, generation-stamped
	 * hash table instead of a HashMap/HashSet pair (see findAllFrequentPairs). The
	 * table is grown on demand, so its memory usage tracks the number of distinct
	 * items actually seen at this depth rather than the whole frequent item domain.
	 * 
	 * @param ctx        the workspace for the current recursion depth
	 * @param sequenceId the sequence where the pair was found
	 * @param item       the item (dense id)
	 * @param postfix    the postfix flag
	 */
	private void addPair(PairCounterContext ctx, int sequenceId, int item, boolean postfix) {
		int key = pairKey(item, postfix);
		int idx = findSlotForKey(ctx, key);
		if (ctx.keys[idx] == -1) {
			// a key that has never been inserted in this context before;
			// grow the table first if it would get too full
			if (ctx.usedSlots + 1 > (ctx.keys.length * 3) / 4) {
				growContext(ctx);
				idx = findSlotForKey(ctx, key);
			}
			ctx.keys[idx] = key;
			ctx.usedSlots++;
		}

		long sequenceStampKey = (((long) ctx.callCounter) << 32) | (sequenceId & 0xFFFFFFFFL);
		// If this sequence was already counted for this pair in this call, skip it
		if (ctx.sequenceStamp[idx] == sequenceStampKey) {
			return;
		}
		ctx.sequenceStamp[idx] = sequenceStampKey;

		// If this slot does not belong to the current call yet, (re)claim it
		if (ctx.callStamp[idx] != ctx.callCounter) {
			ctx.callStamp[idx] = ctx.callCounter;
			Pair pair = ctx.slots[idx];
			if (pair == null) {
				pair = new Pair(postfix, item);
				ctx.slots[idx] = pair;
			} else {
				pair.getSequencesID().clear();
			}
			ctx.touched[ctx.touchedCount++] = idx;
		}
		// we keep the sequence ID
		ctx.slots[idx].getSequencesID().set(sequenceId);
	}
}