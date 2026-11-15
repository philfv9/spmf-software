package ca.pfv.spmf.algorithms.frequentpatterns.foth;

/* This file is copyright (c) 2008-2024 Philippe Fournier-Viger
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
* 
* Do not remove copyright and license.
*/
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Random;

import ca.pfv.spmf.tools.MemoryLogger;

/**
 * This is an implementation of the FOTH algorithm (Fast sOrted iTemset searcH)
 * for mining high utility itemsets without the join operation, presented in this paper:
 * <br/><br/>
 * 
 * Yan, Niu, Zhang, Fournier-Viger, Ye, Min. Efficient high utility itemset
 * mining without the join operation. Information Sciences 681 (2024) 121218.
 * <br/><br/>
 * 
 * Note: The implementation is not the original implementation but tries to follow the paper
 * as closely as possible. Due to some ambiguities and design decisions, the implementation
 * has differences from the original.
 * 
 * Transactions are reduced to their promising items (TWU &gt;= minutil), items
 * are encoded as bits, and transactions having the same code are merged into
 * IndexSets (code, utilSum, vectors). The search then repeatedly splits the
 * mining area on a bit, propagates the IndexSets containing that bit to the
 * IndexSets without it, and prunes an area when the sum of its utilSum values
 * (U + RU) is lower than minutil.
 * <br/><br/>
 * 
 * In this implementation, IndexSets are integer identifiers into primitive
 * arrays, the codes are stored in one pool of long values, and one persistent
 * chained hash table is used to find the IndexSet having a given code (the
 * paper suggests a hash map instead of the red-black tree when there are not
 * too many IndexSets). The IndexSets of a mining area are kept in a binary
 * heap ordered by their highest undecided bit, so that the IndexSets
 * containing the cut bit are obtained without scanning the whole area for each
 * cut. This is equivalent to the loop on the cut point low of Algorithms 3 and
 * 4 of the paper, which visits the same mining areas.
 * <br/><br/>
 * 
 * The vectors of an IndexSet are pointers to the utilities of the undecided
 * items of the merged transactions. A pointer plays the role of the fixedPos
 * of a vector: the items before the pointer (the bits that are still
 * undecided) can be removed by the propagation, and the items after it are
 * fixed. The utility of a vector is not stored because the utilSum of the
 * IndexSet already contains it. Removing the highest undecided bit at each cut
 * is the step by step propagation of Algorithm 1 (base-1, base-2, ...), where
 * each sub-vector is generated exactly once.
 * <br/><br/>
 * 
 * When an upper IndexSet is not mined after being propagated, its vectors are
 * not copied: the smaller vector list is appended to the larger one, and the
 * utility of the cut item that was computed to decide if the lower area can be
 * pruned is reused during the propagation. A lower area whose vs is lower than
 * minutil is not built, because it cannot contain a high utility itemset.
 * 
 * @see MemoryLogger
 */
public class AlgoFOTH {
	
	/** Flag to indicate if debug information should be presented */
	private static final boolean DEBUG = false;

	/** size of the buffer used for reading the input file */
	private static final int READER_BUFFER_SIZE = 65536;

	/** size of the buffer used for writing the output file */
	private static final int WRITER_BUFFER_SIZE = 65536;

	/** initial number of IndexSet identifiers that can be stored */
	private static final int INITIAL_ID_CAPACITY = 1024;

	/** initial size of the vector array (pointers) of an id */
	private static final int INITIAL_VECTOR_CAPACITY = 2;

	/** the seed used to generate the random numbers used for hashing codes */
	private static final long HASH_SEED = 0x5DEECE66DL;

	/** the bytes written between the items and the utility of an itemset */
	private static final byte[] UTIL_MARKER = " #UTIL: ".getBytes();

	/** the bytes written at the end of a line */
	private static final byte[] NEWLINE = System.lineSeparator().getBytes();

	/** the number of high utility itemsets found */
	public int huiCount = 0;

	/** the number of IndexSets created */
	private long indexSetCount = 0;

	/** the number of mining areas visited */
	private long areaCount = 0;

	/** the number of mining areas pruned because vs is lower than minutil */
	private long prunedAreaCount = 0;

	/** start time of the last execution */
	private long startTimestamp = 0;

	/** end time of the last execution */
	private long endTimestamp = 0;

	/** the minimum utility threshold */
	private long minUtility;

	/** the number of promising items (the number of bits of a code) */
	private int numItems;

	/** the number of long values used to store one code */
	private int numWords;

	/** the item corresponding to each bit */
	private int[] itemOfBit;

	/** the TWU of each item, used only to sort the promising items */
	private long[] itemTwu;

	/** the random number associated with each bit, used for hashing codes */
	private int[] zobrist;

	/** the codes of the IndexSets, numWords long values for each id */
	private long[] idCodes;

	/** the hash of the code of each id */
	private int[] idHashes;

	/** the utilSum of each id */
	private long[] idUtils;

	/**
	 * the vectors of each id, as pointers into flat (next undecided item), the
	 * pointer of a vector playing the role of its fixedPos
	 */
	private int[][] idVectors;

	/** the number of vectors of each id */
	private int[] idVectorCounts;

	/** the next id in the hash chain of each id */
	private int[] idChain;

	/** the number of ids currently allocated (ids are allocated as a stack) */
	private int idCount;

	/** the number of ids that the arrays can currently contain */
	private int idCapacity;

	/** the heads of the chains of the persistent hash table */
	private int[] heads;

	/** the mask used to obtain the chain of a hash value */
	private int headMask;

	/** the utilities of the promising items of each transaction, highest bit first */
	private int[] flat;

	/** the number of values used in flat */
	private int flatSize;

	/**
	 * the stack of mining areas, each area being a heap of keys (highest undecided
	 * bit + 1 in the upper 32 bits, id in the lower 32 bits)
	 */
	private long[] arena;

	/**
	 * the utility of the cut item in each upper IndexSet, computed before the
	 * propagation when the lower area may be pruned
	 */
	private long[] removedCache;

	/** the stream where the output is written */
	private OutputStream outputStream;

	/** the buffer used for writing the output */
	private byte[] outputBuffer;

	/** the number of bytes currently in the output buffer */
	private int outputLength;
	
	/** peak memory of last execution */
	private double peakMemory;

	/**
	 * Run the algorithm. This is the booting function (Algorithm 4 of the paper):
	 * the database is reconstructed and the root mining area, which contains all
	 * the IndexSets, is mined. Mining the root area cuts it on each bit from the
	 * highest to the lowest, which corresponds to the loop of Algorithm 4.
	 * 
	 * @param input      the path of the input file
	 * @param output     the path of the output file
	 * @param minUtility the minimum utility threshold
	 * @throws IOException if an error occurs while reading or writing a file
	 */
	public void runAlgorithm(String input, String output, long minUtility) throws IOException {
		MemoryLogger.getInstance().reset();
		startTimestamp = System.currentTimeMillis();
		huiCount = 0;
		indexSetCount = 0;
		areaCount = 0;
		prunedAreaCount = 0;
		peakMemory = 0;
		this.minUtility = minUtility;

		outputStream = new FileOutputStream(output);
		outputBuffer = new byte[WRITER_BUFFER_SIZE];
		outputLength = 0;

		try {
			if (reconstruct(input)) {
				MemoryLogger.getInstance().checkMemory();
				removedCache = new long[16];
				// the root area contains all the IndexSets
				arena = new long[Math.max(16, idCount * 2)];
				long vs = 0;
				for (int id = 0; id < idCount; id++) {
					int top = previousSetBit(id, numItems - 1);
					arena[id] = ((long) (top + 1) << 32) | id;
					vs += idUtils[id];
				}
				heapify(arena, 0, idCount);
				mine(0, idCount, vs, false);
			}
		} finally {
			flushOutput();
			outputStream.close();
			outputStream = null;
			outputBuffer = null;
		}

		MemoryLogger.getInstance().checkMemory();
		peakMemory = MemoryLogger.getInstance().getMaxMemory();
		releaseMemory();
		endTimestamp = System.currentTimeMillis();
	}

	/**
	 * Reconstruct the database (Algorithm 2 of the paper): compute the TWU of
	 * items, remove the items having a TWU lower than minutil, sort the other
	 * items by decreasing TWU, encode them as bits, and merge the transactions
	 * having the same code into IndexSets.
	 * 
	 * @param input the path of the input file
	 * @return false if there is no promising item
	 * @throws IOException if an error occurs while reading the file
	 */
	private boolean reconstruct(String input) throws IOException {
		// first database scan: compute the TWU of each item
		long[] twu = new long[1024];
		int maxItem = -1;
		TransactionReader reader = new TransactionReader(input);
		try {
			while (reader.next()) {
				final int[] items = reader.items;
				final long transactionUtility = reader.transactionUtility;
				for (int j = 0; j < reader.length; j++) {
					int item = items[j];
					if (item >= twu.length) {
						twu = Arrays.copyOf(twu, Math.max(item + 1, twu.length * 2));
					}
					twu[item] += transactionUtility;
					if (item > maxItem) {
						maxItem = item;
					}
				}
			}
		} finally {
			reader.close();
		}

		// keep the promising items and sort them by decreasing TWU
		// (ties: larger item first). The i-th item is encoded by bit i.
		int promisingCount = 0;
		for (int item = 0; item <= maxItem; item++) {
			if (twu[item] >= minUtility && twu[item] > 0) {
				promisingCount++;
			}
		}
		numItems = promisingCount;
		if (numItems == 0) {
			return false;
		}
		int[] promising = new int[numItems];
		int position = 0;
		for (int item = 0; item <= maxItem; item++) {
			if (twu[item] >= minUtility && twu[item] > 0) {
				promising[position++] = item;
			}
		}
		itemTwu = twu;
		sortItems(promising, new int[numItems], 0, numItems);
		itemOfBit = promising;
		int[] bitOfItem = new int[maxItem + 1];
		Arrays.fill(bitOfItem, -1);
		for (int bit = 0; bit < numItems; bit++) {
			bitOfItem[promising[bit]] = bit;
		}
		itemTwu = null;

		numWords = (numItems + 63) >>> 6;
		Random random = new Random(HASH_SEED);
		zobrist = new int[numItems];
		for (int bit = 0; bit < numItems; bit++) {
			zobrist[bit] = random.nextInt();
		}

		idCapacity = 0;
		idCount = 0;
		idCodes = new long[0];
		idHashes = new int[0];
		idUtils = new long[0];
		idVectors = new int[0][];
		idVectorCounts = new int[0];
		idChain = new int[0];
		heads = new int[INITIAL_ID_CAPACITY];
		Arrays.fill(heads, -1);
		headMask = heads.length - 1;
		ensureIds(INITIAL_ID_CAPACITY);

		flat = new int[1 << 16];
		flatSize = 0;
		long[] keys = new long[64];

		// second database scan: build the IndexSets
		reader = new TransactionReader(input);
		try {
			while (reader.next()) {
				final int[] items = reader.items;
				final int[] utilities = reader.utilities;
				final int length = reader.length;
				if (keys.length < length) {
					keys = new long[Math.max(length, keys.length * 2)];
				}

				// keep the promising items with their bit and position
				int k = 0;
				long sum = 0;
				for (int j = 0; j < length; j++) {
					int bit = bitOfItem[items[j]];
					if (bit >= 0) {
						keys[k++] = ((long) bit << 32) | j;
						sum += utilities[j];
					}
				}
				if (k == 0) {
					continue;
				}
				Arrays.sort(keys, 0, k);

				// store the utilities, the item having the highest bit first
				if (flatSize + k > flat.length) {
					flat = Arrays.copyOf(flat, (int) Math.min(Integer.MAX_VALUE - 8,
							Math.max((long) flatSize + k, flat.length * 2L)));
				}
				final int start = flatSize;
				for (int q = k - 1; q >= 0; q--) {
					flat[flatSize++] = utilities[(int) (keys[q] & 0xFFFFFFFFL)];
				}

				// build the code directly in the first free slot of the pool
				ensureIds(idCount + 1);
				final int base = idCount * numWords;
				int hash = 0;
				for (int q = 0; q < k; q++) {
					int bit = (int) (keys[q] >>> 32);
					idCodes[base + (bit >>> 6)] |= 1L << bit;
					hash ^= zobrist[bit];
				}

				int found = find(hash, idCount, 0, 0L);
				if (found >= 0) {
					// same code: merge by appending the vector (no Tid comparison)
					for (int q = 0; q < k; q++) {
						idCodes[base + ((int) (keys[q] >>> 32) >>> 6)] = 0L;
					}
					addVector(found, start);
					idUtils[found] += sum;
				} else {
					int id = idCount++;
					idHashes[id] = hash;
					idUtils[id] = sum;
					idVectorCounts[id] = 0;
					indexSetCount++;
					link(id);
					addVector(id, start);
				}
			}
		} finally {
			reader.close();
		}
		return true;
	}

	/**
	 * Sort promising items by decreasing TWU, ties being broken by larger item
	 * first (merge sort on primitive values).
	 * 
	 * @param array  the items to be sorted
	 * @param buffer a buffer of the same size as the array
	 * @param low    the first position (inclusive)
	 * @param high   the last position (exclusive)
	 */
	private void sortItems(int[] array, int[] buffer, int low, int high) {
		if (high - low < 2) {
			return;
		}
		if (high - low <= 12) {
			for (int i = low + 1; i < high; i++) {
				int value = array[i];
				int j = i - 1;
				while (j >= low && before(value, array[j])) {
					array[j + 1] = array[j];
					j--;
				}
				array[j + 1] = value;
			}
			return;
		}
		int middle = (low + high) >>> 1;
		sortItems(array, buffer, low, middle);
		sortItems(array, buffer, middle, high);
		System.arraycopy(array, low, buffer, low, high - low);
		int a = low;
		int b = middle;
		int k = low;
		while (a < middle && b < high) {
			array[k++] = before(buffer[b], buffer[a]) ? buffer[b++] : buffer[a++];
		}
		while (a < middle) {
			array[k++] = buffer[a++];
		}
		while (b < high) {
			array[k++] = buffer[b++];
		}
	}

	/**
	 * Check if an item must be placed before another one in the order of items
	 * 
	 * @param a an item
	 * @param b another item
	 * @return true if a has a larger TWU than b (or the same TWU and a larger id)
	 */
	private boolean before(int a, int b) {
		return itemTwu[a] != itemTwu[b] ? itemTwu[a] > itemTwu[b] : a > b;
	}

	/**
	 * Mine a mining area (Algorithm 3 of the paper). The area is stored in the
	 * arena from position "from" as a heap of keys ordered by the highest
	 * undecided bit of each IndexSet. All the codes of the area contain the same
	 * decided bits (the prefix). At each iteration, the highest undecided bit
	 * (cut) splits the area: the IndexSets containing the cut bit (upper) are
	 * removed from the heap, propagated to the IndexSets without it (lower, see
	 * Algorithm 1), the upper area is mined recursively if its vs (U + RU) is at
	 * least minutil, and the loop continues with the lower area. The utilSum of
	 * an IndexSet is compared with minutil to output it once all its bits are
	 * decided. If the lower area cannot contain a high utility itemset, it is
	 * not built at all.
	 * 
	 * @param from      the position of the first key of the area in the arena
	 * @param size      the number of ids in the area
	 * @param vs        the sum of the utilSum of the area (U + RU)
	 * @param hasPrefix true if the codes of the area contain at least one decided bit
	 * @throws IOException if an error occurs while writing the output
	 */
	private void mine(int from, int size, long vs, boolean hasPrefix) throws IOException {
		final int mark = idCount;
		// the upper IndexSets are stored after the heap (they are never more
		// numerous than the IndexSets of the heap, and the heap never grows
		// beyond its initial size)
		final int upperFrom = from + size;
		ensureArena(upperFrom + size);

		while (size > 0) {
			areaCount++;
			if ((areaCount & 0x3FFF) == 0) {
				MemoryLogger.getInstance().checkMemory();
			}
			// the area cannot contain a high utility itemset
			if (vs < minUtility) {
				prunedAreaCount++;
				break;
			}
			final long[] list = arena;
			final int cut = (int) (list[from] >>> 32) - 1;
			// all the bits are decided: the area contains a single IndexSet
			if (cut < 0) {
				if (hasPrefix) {
					for (int i = from; i < from + size; i++) {
						int id = (int) list[i];
						if (idUtils[id] >= minUtility) {
							writeItemset(id);
						}
					}
				}
				break;
			}

			final int wordIndex = cut >>> 6;
			final long mask = 1L << cut;
			final int cutHash = zobrist[cut];
			final long cutTag = cut + 1;

			// extract the upper IndexSets (those whose highest undecided bit is cut)
			int upperSize = 0;
			long upperVs = 0;
			while (size > 0 && (list[from] >>> 32) == cutTag) {
				final long key = list[from];
				list[upperFrom + upperSize++] = key;
				upperVs += idUtils[(int) key];
				size--;
				if (size > 0) {
					siftDown(list, from, size, 0, list[from + size]);
				}
			}

			// compute the highest undecided bit of each upper IndexSet once
			for (int s = 0; s < upperSize; s++) {
				final int source = (int) list[upperFrom + s];
				final int newTop = previousSetBit(source, cut - 1);
				list[upperFrom + s] = ((long) (newTop + 1) << 32) | source;
			}

			// the upper area is mined only if it can contain a high utility itemset
			final boolean keepUpper = upperVs >= minUtility;

			// the lower area can only be pruned if it is low even before propagation,
			// in which case the exact utility removed with the cut item is computed
			// (it stops as soon as the lower area is known to be pruned, and it is
			// kept to be reused by the propagation)
			boolean propagate = true;
			boolean known = false;
			long preRemoved = 0;
			if (vs - upperVs < minUtility) {
				if (removedCache.length < upperSize) {
					removedCache = new long[Math.max(upperSize, removedCache.length * 2)];
				}
				final long slack = vs - minUtility;
				for (int s = 0; s < upperSize && propagate; s++) {
					final long key = list[upperFrom + s];
					final long utility = removedUtility((int) key, (int) (key >>> 32) - 1, hasPrefix);
					removedCache[s] = utility;
					preRemoved += utility;
					propagate = preRemoved <= slack;
				}
				known = propagate;
				if (!propagate && !keepUpper) {
					vs -= preRemoved;
					continue;
				}
			}

			if (propagate) {
				// one pass: propagate the upper IndexSets to the lower ones, and
				// move the pointers of the upper vectors past the cut item
				long removed = 0;
				for (int s = 0; s < upperSize; s++) {
					final long key = list[upperFrom + s];
					final int source = (int) key;
					final int newTop = (int) (key >>> 32) - 1;

					// without prefix, the empty itemset is never needed
					int target = -1;
					if (hasPrefix || newTop >= 0) {
						final int hash = idHashes[source] ^ cutHash;
						target = find(hash, source, wordIndex, mask);
						if (target < 0) {
							target = newId(source, wordIndex, mask, hash);
							siftUp(list, from, size++, ((long) (newTop + 1) << 32) | target);
						}
					}

					long sourceRemoved;
					if (newTop >= 0) {
						sourceRemoved = transferVectors(source, target, keepUpper, known,
								known ? removedCache[s] : 0L);
					} else {
						// no undecided item remains, so the pointers are not needed
						sourceRemoved = known ? removedCache[s] : removedUtility(source, newTop, hasPrefix);
					}
					if (target >= 0) {
						idUtils[target] += idUtils[source] - sourceRemoved;
					}
					removed += sourceRemoved;
				}

				// the new sum of the area is the old sum minus the utility of the cut item
				vs -= removed;
			} else {
				// the lower area is not built, only the upper pointers are moved
				for (int s = 0; s < upperSize; s++) {
					final long key = list[upperFrom + s];
					if ((key >>> 32) > 0) {
						final int source = (int) key;
						advanceVectors(idVectors[source], idVectorCounts[source]);
					}
				}
				vs -= preRemoved;
			}

			if (keepUpper) {
				heapify(list, upperFrom, upperSize);
				mine(upperFrom, upperSize, upperVs, true);
			}
		}

		releaseIds(mark);
	}

	/**
	 * Get the utility of the cut item in an upper IndexSet, which is the amount
	 * removed from its utilSum when it is propagated to the lower area.
	 * 
	 * @param source    the upper IndexSet
	 * @param newTop    the highest undecided bit of the IndexSet after the cut
	 * @param hasPrefix true if the area has decided bits
	 * @return the sum of the utilities of the cut item in the vectors
	 */
	private long removedUtility(int source, int newTop, boolean hasPrefix) {
		// the code only contains the cut item
		if (newTop < 0 && !hasPrefix) {
			return idUtils[source];
		}
		final int[] vectors = idVectors[source];
		final int count = idVectorCounts[source];
		final int[] utilities = flat;
		long sum = 0;
		for (int v = 0; v < count; v++) {
			sum += utilities[vectors[v]];
		}
		return sum;
	}

	/**
	 * Propagate the vectors of an upper IndexSet to a lower IndexSet, with the
	 * pointers moved past the cut item (merge of vectors of Algorithm 1: the
	 * vectors are appended and no Tid is compared). If the upper IndexSet is not
	 * needed after the propagation and the lower IndexSet has fewer vectors, the
	 * vector array of the upper IndexSet is taken by the lower IndexSet (and the
	 * old array of the lower IndexSet is given to the upper IndexSet) so that
	 * only the smaller list of vectors is copied.
	 * 
	 * @param source       the upper IndexSet
	 * @param target       the lower IndexSet
	 * @param keepSource   true if the pointers of the upper IndexSet must also be
	 *                     moved past the cut item because it is mined afterwards
	 * @param known        true if the utility of the cut item is already known
	 * @param knownRemoved the utility of the cut item (used only if known)
	 * @return the sum of the utilities of the cut item in the vectors
	 */
	private long transferVectors(int source, int target, boolean keepSource, boolean known, long knownRemoved) {
		final int count = idVectorCounts[source];
		final int targetCount = idVectorCounts[target];
		final int[] sourceVectors = idVectors[source];
		long removed = knownRemoved;
		if (!keepSource && targetCount < count) {
			final int[] oldTargetVectors = idVectors[target];
			if (known) {
				advanceVectors(sourceVectors, count);
			} else {
				removed = advanceAndSum(sourceVectors, count);
			}
			idVectors[target] = sourceVectors;
			idVectors[source] = oldTargetVectors;
			idVectorCounts[target] = count;
			if (targetCount > 0) {
				final int[] merged = reserveVectors(target, targetCount);
				System.arraycopy(oldTargetVectors, 0, merged, count, targetCount);
				idVectorCounts[target] = count + targetCount;
			}
			return removed;
		}
		final int[] targetVectors = reserveVectors(target, count);
		idVectorCounts[target] = targetCount + count;
		if (known) {
			copyVectors(sourceVectors, count, targetVectors, targetCount, keepSource);
		} else {
			removed = moveVectors(sourceVectors, count, targetVectors, targetCount, keepSource);
		}
		return removed;
	}

	/**
	 * Copy the vectors of an upper IndexSet to a lower IndexSet with the pointers
	 * moved past the cut item, and compute the utility of the cut item.
	 * 
	 * @param vectors       the vectors of the upper IndexSet
	 * @param count         the number of vectors
	 * @param targetVectors the vector array of the lower IndexSet
	 * @param offset        the position where the vectors are copied
	 * @param advance       true if the pointers of the upper IndexSet must also be
	 *                      moved past the cut item
	 * @return the sum of the utilities of the cut item in the vectors
	 */
	private long moveVectors(int[] vectors, int count, int[] targetVectors, int offset, boolean advance) {
		final int[] utilities = flat;
		long removed = 0;
		if (advance) {
			for (int v = 0; v < count; v++) {
				final int pointer = vectors[v];
				removed += utilities[pointer];
				vectors[v] = pointer + 1;
				targetVectors[offset + v] = pointer + 1;
			}
		} else {
			for (int v = 0; v < count; v++) {
				final int pointer = vectors[v];
				removed += utilities[pointer];
				targetVectors[offset + v] = pointer + 1;
			}
		}
		return removed;
	}

	/**
	 * Copy the vectors of an upper IndexSet to a lower IndexSet with the pointers
	 * moved past the cut item, when the utility of the cut item is already known.
	 * 
	 * @param vectors       the vectors of the upper IndexSet
	 * @param count         the number of vectors
	 * @param targetVectors the vector array of the lower IndexSet
	 * @param offset        the position where the vectors are copied
	 * @param advance       true if the pointers of the upper IndexSet must also be
	 *                      moved past the cut item
	 */
	private void copyVectors(int[] vectors, int count, int[] targetVectors, int offset, boolean advance) {
		if (advance) {
			for (int v = 0; v < count; v++) {
				final int pointer = vectors[v] + 1;
				vectors[v] = pointer;
				targetVectors[offset + v] = pointer;
			}
		} else {
			for (int v = 0; v < count; v++) {
				targetVectors[offset + v] = vectors[v] + 1;
			}
		}
	}

	/**
	 * Move the pointers of some vectors past the cut item
	 * 
	 * @param vectors the pointers
	 * @param count   the number of vectors
	 */
	private void advanceVectors(int[] vectors, int count) {
		for (int v = 0; v < count; v++) {
			vectors[v]++;
		}
	}

	/**
	 * Move the pointers of some vectors past the cut item, and compute the
	 * utility of the cut item.
	 * 
	 * @param vectors the pointers
	 * @param count   the number of vectors
	 * @return the sum of the utilities of the cut item in the vectors
	 */
	private long advanceAndSum(int[] vectors, int count) {
		final int[] utilities = flat;
		long removed = 0;
		for (int v = 0; v < count; v++) {
			final int pointer = vectors[v];
			removed += utilities[pointer];
			vectors[v] = pointer + 1;
		}
		return removed;
	}

	/**
	 * Turn a region of the arena into a max-heap of keys
	 * 
	 * @param list the arena
	 * @param from the first position of the region
	 * @param size the number of keys of the region
	 */
	private void heapify(long[] list, int from, int size) {
		for (int i = (size >>> 1) - 1; i >= 0; i--) {
			siftDown(list, from, size, i, list[from + i]);
		}
	}

	/**
	 * Move a key down a max-heap until the heap property is restored
	 * 
	 * @param list  the arena
	 * @param from  the first position of the heap
	 * @param size  the number of keys of the heap
	 * @param index the index where the key is inserted
	 * @param key   the key
	 */
	private void siftDown(long[] list, int from, int size, int index, long key) {
		while (true) {
			int child = 2 * index + 1;
			if (child >= size) {
				break;
			}
			if (child + 1 < size && list[from + child + 1] > list[from + child]) {
				child++;
			}
			if (list[from + child] <= key) {
				break;
			}
			list[from + index] = list[from + child];
			index = child;
		}
		list[from + index] = key;
	}

	/**
	 * Move a key up a max-heap until the heap property is restored
	 * 
	 * @param list  the arena
	 * @param from  the first position of the heap
	 * @param index the index where the key is inserted (the current heap size)
	 * @param key   the key
	 */
	private void siftUp(long[] list, int from, int index, long key) {
		while (index > 0) {
			int parent = (index - 1) >>> 1;
			if (list[from + parent] >= key) {
				break;
			}
			list[from + index] = list[from + parent];
			index = parent;
		}
		list[from + index] = key;
	}

	/**
	 * Write an itemset to the output. The itemset is the set of bits of the code
	 * of an IndexSet (decoded to the original items) and its utility is the
	 * utilSum of the IndexSet.
	 * 
	 * @param id the IndexSet
	 * @throws IOException if an error occurs while writing the output
	 */
	private void writeItemset(int id) throws IOException {
		final int base = id * numWords;
		boolean first = true;
		for (int w = 0; w < numWords; w++) {
			long word = idCodes[base + w];
			while (word != 0) {
				int bit = (w << 6) + Long.numberOfTrailingZeros(word);
				word &= word - 1;
				if (!first) {
					writeByte(' ');
				}
				first = false;
				writeNumber(itemOfBit[bit]);
			}
		}
		writeBytes(UTIL_MARKER);
		writeNumber(idUtils[id]);
		writeBytes(NEWLINE);
		huiCount++;
	}

	/**
	 * Get the highest set bit of the code of an IndexSet that is lower than or
	 * equal to a given bit.
	 * 
	 * @param id      the IndexSet
	 * @param fromBit the highest bit that can be returned
	 * @return the bit or -1 if there is none
	 */
	private int previousSetBit(int id, int fromBit) {
		if (fromBit < 0) {
			return -1;
		}
		final int base = id * numWords;
		int w = fromBit >>> 6;
		long word = idCodes[base + w] & (-1L >>> (63 - (fromBit & 63)));
		while (true) {
			if (word != 0) {
				return (w << 6) + 63 - Long.numberOfLeadingZeros(word);
			}
			if (--w < 0) {
				return -1;
			}
			word = idCodes[base + w];
		}
	}

	/**
	 * Allocate an IndexSet whose code is the code of a source IndexSet with one
	 * bit toggled, and link it into the hash table.
	 * 
	 * @param source    the IndexSet whose code is copied
	 * @param wordIndex the word containing the bit to be toggled
	 * @param mask      the mask of the bit to be toggled in that word
	 * @param hash      the hash of the new code
	 * @return the new id
	 */
	private int newId(int source, int wordIndex, long mask, int hash) {
		ensureIds(idCount + 1);
		final int id = idCount++;
		final int sourceBase = source * numWords;
		final int base = id * numWords;
		if (numWords == 1) {
			idCodes[base] = idCodes[sourceBase];
		} else {
			System.arraycopy(idCodes, sourceBase, idCodes, base, numWords);
		}
		idCodes[base + wordIndex] ^= mask;
		idHashes[id] = hash;
		idUtils[id] = 0;
		idVectorCounts[id] = 0;
		indexSetCount++;
		link(id);
		return id;
	}

	/**
	 * Append a vector to an IndexSet (the utilSum is updated by the caller)
	 * 
	 * @param id      the IndexSet
	 * @param pointer the position in flat of the next undecided item
	 */
	private void addVector(int id, int pointer) {
		final int[] vectors = reserveVectors(id, 1);
		vectors[idVectorCounts[id]++] = pointer;
	}

	/**
	 * Ensure that the vector array of an IndexSet can receive a given number of
	 * additional vectors. The count of vectors is not modified.
	 * 
	 * @param id    the IndexSet
	 * @param extra the number of vectors that will be appended
	 * @return the vector array of the IndexSet (possibly a new array)
	 */
	private int[] reserveVectors(int id, int extra) {
		int[] vectors = idVectors[id];
		final long needed = (long) idVectorCounts[id] + extra;
		if (vectors == null) {
			vectors = new int[(int) Math.max(INITIAL_VECTOR_CAPACITY, needed)];
			idVectors[id] = vectors;
		} else if (needed > vectors.length) {
			vectors = Arrays.copyOf(vectors,
					(int) Math.min(Integer.MAX_VALUE - 8, Math.max(needed, vectors.length * 2L)));
			idVectors[id] = vectors;
		}
		return vectors;
	}

	/**
	 * Find the IndexSet having the code of a probe IndexSet with one bit toggled.
	 * The comparison is made without building the code.
	 * 
	 * @param hash      the hash of the searched code
	 * @param probe     the IndexSet whose code is compared
	 * @param wordIndex the word containing the bit to be toggled
	 * @param mask      the mask of the bit to be toggled (0 if none)
	 * @return the id or -1 if there is none
	 */
	private int find(int hash, int probe, int wordIndex, long mask) {
		final long[] codes = idCodes;
		final int[] hashes = idHashes;
		final int[] chain = idChain;
		int id = heads[hash & headMask];
		if (numWords == 1) {
			final long expected = codes[probe] ^ mask;
			while (id >= 0) {
				if (hashes[id] == hash && codes[id] == expected) {
					return id;
				}
				id = chain[id];
			}
			return -1;
		}
		final int probeBase = probe * numWords;
		while (id >= 0) {
			if (hashes[id] == hash && sameCode(codes, id * numWords, probeBase, wordIndex, mask)) {
				return id;
			}
			id = chain[id];
		}
		return -1;
	}

	/**
	 * Check if the code stored at a position is equal to the code stored at the
	 * position of a probe with one bit toggled.
	 * 
	 * @param codes     the pool of codes
	 * @param base      the position of the code to be checked
	 * @param probeBase the position of the code of the probe
	 * @param wordIndex the word containing the bit to be toggled
	 * @param mask      the mask of the bit to be toggled (0 if none)
	 * @return true if the codes are equal
	 */
	private boolean sameCode(long[] codes, int base, int probeBase, int wordIndex, long mask) {
		if (codes[base + wordIndex] != (codes[probeBase + wordIndex] ^ mask)) {
			return false;
		}
		for (int w = 0; w < wordIndex; w++) {
			if (codes[base + w] != codes[probeBase + w]) {
				return false;
			}
		}
		for (int w = wordIndex + 1; w < numWords; w++) {
			if (codes[base + w] != codes[probeBase + w]) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Link an id (already counted in idCount) at the head of its chain. The hash
	 * table is doubled and rebuilt (in ascending id order) when it is full.
	 * 
	 * @param id the id
	 */
	private void link(int id) {
		if (idCount > heads.length) {
			int newLength = heads.length;
			while (newLength < idCount) {
				newLength <<= 1;
			}
			heads = new int[newLength];
			Arrays.fill(heads, -1);
			headMask = newLength - 1;
			for (int i = 0; i < idCount; i++) {
				int slot = idHashes[i] & headMask;
				idChain[i] = heads[slot];
				heads[slot] = i;
			}
			return;
		}
		int slot = idHashes[id] & headMask;
		idChain[id] = heads[slot];
		heads[slot] = id;
	}

	/**
	 * Remove an id from its chain. Ids are released in the reverse order of their
	 * allocation, so the id is normally at the head of its chain.
	 * 
	 * @param id the id
	 */
	private void unlink(int id) {
		final int slot = idHashes[id] & headMask;
		int current = heads[slot];
		if (current == id) {
			heads[slot] = idChain[id];
			return;
		}
		int previous = current;
		while (current != id) {
			previous = current;
			current = idChain[current];
		}
		idChain[previous] = idChain[id];
	}

	/**
	 * Release all the ids allocated after a mark. Their vector arrays are kept
	 * so that they can be reused.
	 * 
	 * @param mark the value of idCount to be restored
	 */
	private void releaseIds(int mark) {
		for (int id = idCount - 1; id >= mark; id--) {
			unlink(id);
		}
		idCount = mark;
	}

	/**
	 * Ensure that the arrays of ids can contain a given number of ids
	 * 
	 * @param needed the number of ids
	 */
	private void ensureIds(int needed) {
		if (needed <= idCapacity) {
			return;
		}
		final long limit = (Integer.MAX_VALUE - 8) / numWords;
		if (needed > limit) {
			throw new OutOfMemoryError("FOTH: too many IndexSets for the code pool");
		}
		final int newCapacity = (int) Math.min(limit,
				Math.max((long) needed, Math.max(INITIAL_ID_CAPACITY, idCapacity * 2L)));
		idCodes = Arrays.copyOf(idCodes, newCapacity * numWords);
		idHashes = Arrays.copyOf(idHashes, newCapacity);
		idUtils = Arrays.copyOf(idUtils, newCapacity);
		idVectors = Arrays.copyOf(idVectors, newCapacity);
		idVectorCounts = Arrays.copyOf(idVectorCounts, newCapacity);
		idChain = Arrays.copyOf(idChain, newCapacity);
		idCapacity = newCapacity;
	}

	/**
	 * Ensure that the arena can contain a given number of keys
	 * 
	 * @param needed the number of keys
	 */
	private void ensureArena(int needed) {
		if (needed > arena.length) {
			arena = Arrays.copyOf(arena,
					(int) Math.min(Integer.MAX_VALUE - 8, Math.max((long) needed, arena.length * 2L)));
		}
	}

	/**
	 * Write a byte to the output buffer
	 * 
	 * @param value the byte
	 * @throws IOException if an error occurs while writing
	 */
	private void writeByte(int value) throws IOException {
		if (outputLength == outputBuffer.length) {
			flushOutput();
		}
		outputBuffer[outputLength++] = (byte) value;
	}

	/**
	 * Write an array of bytes to the output buffer
	 * 
	 * @param bytes the bytes
	 * @throws IOException if an error occurs while writing
	 */
	private void writeBytes(byte[] bytes) throws IOException {
		if (outputLength + bytes.length > outputBuffer.length) {
			flushOutput();
		}
		System.arraycopy(bytes, 0, outputBuffer, outputLength, bytes.length);
		outputLength += bytes.length;
	}

	/**
	 * Write a number in decimal to the output buffer, without creating objects
	 * 
	 * @param number the number
	 * @throws IOException if an error occurs while writing
	 */
	private void writeNumber(long number) throws IOException {
		if (outputLength + 21 > outputBuffer.length) {
			flushOutput();
		}
		if (number < 0) {
			outputBuffer[outputLength++] = '-';
			number = -number;
		}
		int digits = 1;
		for (long t = number; t >= 10; t /= 10) {
			digits++;
		}
		int position = outputLength + digits;
		outputLength = position;
		do {
			outputBuffer[--position] = (byte) ('0' + number % 10);
			number /= 10;
		} while (number != 0);
	}

	/**
	 * Write the content of the output buffer to the output stream
	 * 
	 * @throws IOException if an error occurs while writing
	 */
	private void flushOutput() throws IOException {
		if (outputLength > 0) {
			outputStream.write(outputBuffer, 0, outputLength);
			outputLength = 0;
		}
	}

	/**
	 * Release the memory used by the data structures of the last execution
	 */
	private void releaseMemory() {
		idCodes = null;
		idHashes = null;
		idUtils = null;
		idVectors = null;
		idVectorCounts = null;
		idChain = null;
		heads = null;
		flat = null;
		arena = null;
		removedCache = null;
		zobrist = null;
		itemOfBit = null;
		itemTwu = null;
	}

	/**
	 * Print statistics about the latest execution to System.out.
	 */
	public void printStats() {
		System.out.println("=============  FOTH ALGORITHM  v.2.68- STATS =============");
		System.out.println(" Total time ~ " + (endTimestamp - startTimestamp) + " ms");
		System.out.println(" Memory ~ " + peakMemory + " MB");
		System.out.println(" High-utility itemsets count : " + huiCount);
		if(DEBUG) {
			System.out.println(" IndexSet count : " + indexSetCount);
			System.out.println(" Mining area count : " + areaCount);
			System.out.println(" Pruned mining area count : " + prunedAreaCount);
		}
		System.out.println("===================================================");
	}

	/**
	 * Reader of transactions in the format "items:TU:utilities" that reads bytes
	 * from a buffer and does not create a String or an object for each line.
	 * Lines that are empty or start with #, % or @ are skipped.
	 */
	private static final class TransactionReader {

		/** the stream being read */
		private final InputStream input;

		/** the buffer of bytes */
		private final byte[] buffer = new byte[READER_BUFFER_SIZE];

		/** the position of the next byte in the buffer */
		private int position = 0;

		/** the number of valid bytes in the buffer */
		private int limit = 0;

		/** the character that ended the last number that was read */
		private int terminator;

		/** the items of the last transaction */
		int[] items = new int[64];

		/** the utilities of the items of the last transaction */
		int[] utilities = new int[64];

		/** the number of items of the last transaction */
		int length;

		/** the transaction utility of the last transaction */
		long transactionUtility;

		/**
		 * Constructor
		 * 
		 * @param path the path of the file
		 * @throws IOException if the file cannot be opened
		 */
		TransactionReader(String path) throws IOException {
			input = new FileInputStream(path);
		}

		/**
		 * Read the next byte
		 * 
		 * @return the byte or -1 at the end of the file
		 * @throws IOException if an error occurs while reading
		 */
		private int read() throws IOException {
			if (position == limit) {
				limit = input.read(buffer, 0, buffer.length);
				position = 0;
				if (limit <= 0) {
					limit = 0;
					return -1;
				}
			}
			return buffer[position++] & 0xFF;
		}

		/**
		 * Read a number whose first character was already read. The character
		 * following the number is stored in terminator.
		 * 
		 * @param c the first character of the number
		 * @return the number
		 * @throws IOException if an error occurs while reading
		 */
		private long number(int c) throws IOException {
			boolean negative = false;
			if (c == '-') {
				negative = true;
				c = read();
			}
			long value = 0;
			while (c >= '0' && c <= '9') {
				value = value * 10 + (c - '0');
				c = read();
			}
			terminator = c;
			return negative ? -value : value;
		}

		/**
		 * Check if a character can start a number
		 * 
		 * @param c the character
		 * @return true if it is a digit or a minus sign
		 */
		private static boolean startsNumber(int c) {
			return (c >= '0' && c <= '9') || c == '-';
		}

		/**
		 * Read the next transaction
		 * 
		 * @return false if there is no more transaction
		 * @throws IOException if an error occurs while reading
		 */
		boolean next() throws IOException {
			int c = read();
			while (true) {
				while (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
					c = read();
				}
				if (c < 0) {
					return false;
				}
				if (c == '#' || c == '%' || c == '@') {
					while (c >= 0 && c != '\n') {
						c = read();
					}
					continue;
				}

				// items
				length = 0;
				while (c != ':' && c >= 0 && c != '\n') {
					if (startsNumber(c)) {
						if (length == items.length) {
							items = Arrays.copyOf(items, length * 2);
							utilities = Arrays.copyOf(utilities, length * 2);
						}
						items[length++] = (int) number(c);
						c = terminator;
					} else {
						c = read();
					}
				}
				if (c != ':') {
					continue;
				}

				// transaction utility
				transactionUtility = 0;
				c = read();
				while (c != ':' && c >= 0 && c != '\n') {
					if (startsNumber(c)) {
						transactionUtility = number(c);
						c = terminator;
					} else {
						c = read();
					}
				}
				if (c != ':') {
					continue;
				}

				// utilities of items
				int count = 0;
				c = read();
				while (c >= 0 && c != '\n') {
					if (startsNumber(c)) {
						int value = (int) number(c);
						if (count < length) {
							utilities[count] = value;
						}
						count++;
						c = terminator;
					} else {
						c = read();
					}
				}
				if (count < length) {
					length = count;
				}
				return true;
			}
		}

		/**
		 * Close the stream
		 * 
		 * @throws IOException if an error occurs while closing
		 */
		void close() throws IOException {
			input.close();
		}
	}
}