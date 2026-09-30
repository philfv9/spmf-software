package ca.pfv.spmf.algorithms.sequentialpatterns.sui;

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
/* This file is part of the SPMF DATA MINING SOFTWARE
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
 * Copyright (c) Philipe Fournier-Viger et al.
 *
 * Do not remove copyright and license information.
 */
/**
 * Implementation of the SUI algorithm for mining frequent sequential patterns
 * from a sequence database, using Data-IDLists and Pseudo-IDLists.
 *
 * @author Philippe Fournier-Viger
 */
public class AlgoSUI {

	// ==================== Statistics ====================
	/** Timestamp (ms) when the algorithm started */
	private long startTime;
	/** Timestamp (ms) when the algorithm finished */
	private long endTime;
	/** Number of frequent patterns found */
	private int patternCount;
	/** Minimum support expressed as an absolute number of sequences */
	private int minSupport;
	/** Minimum support expressed as a percentage of the number of sequences */
	private double minSupportPercentage;

	// ==================== Optional constraints ====================
	/** Minimum number of items that a pattern must contain (default = 0) */
	private int minPatternLength = 0;
	/** Maximum number of items that a pattern can contain (default = infinity) */
	private int maxPatternLength = Integer.MAX_VALUE;

	// ==================== Output ====================
	/** Writer used to output the frequent patterns found */
	private BufferedWriter writer;

	// ==================== Internal state ====================
	/** Kept for clarity; not used directly */
	private int[] baseDataIds;
	/** Number of distinct items in the database */
	private int numItems;

	/** freqItems[rank] = the item value at the given rank (sorted order) */
	private int[] freqItems;
	/** vertDBs[rank] = the Data-IDList of the frequent 1-pattern at the given rank */
	private VertDB[] vertDBs;
	/** Number of frequent 1-items */
	private int numFreqItems;

	// =========================================================================
	// Vertical database (Data-IDList) stored as parallel primitive arrays
	// =========================================================================

	/**
	 * Compact representation of a Data-IDList using parallel int[] arrays,
	 * to avoid Row object allocation and Integer boxing.
	 */
	private static final class VertDB {
		/** Number of rows (i.e. number of sequences containing the pattern) */
		int support;
		/** dataId[i] = the data-id of row i */
		int[] dataId;
		/** tsid[i] = the sequence id (tsid) of row i */
		int[] tsid;
		/** positions[i] = the array of itemset positions for row i */
		int[][] positions;
		/** dataIdIndex[d] = row index for dataId d, or -1 if absent (O(1) lookup) */
		int[] dataIdIndex;
		/** Largest dataId stored, used to size dataIdIndex */
		int maxDataId;

		/**
		 * Create a new VertDB with arrays pre-allocated to the given capacity.
		 * @param capacity the initial number of rows to allocate for
		 */
		VertDB(int capacity) {
			dataId = new int[capacity];
			tsid = new int[capacity];
			positions = new int[capacity][];
		}

		/**
		 * Build the dataIdIndex reverse-lookup array from the current rows.
		 * Must be called once after all rows have been added.
		 */
		void finishBuilding() {
			// find the largest dataId to size the lookup array
			maxDataId = 0;
			for (int i = 0; i < support; i++) {
				if (dataId[i] > maxDataId)
					maxDataId = dataId[i];
			}
			dataIdIndex = new int[maxDataId + 1];
			Arrays.fill(dataIdIndex, -1);
			// populate the lookup array
			for (int i = 0; i < support; i++) {
				dataIdIndex[dataId[i]] = i;
			}
		}

		/**
		 * Find the row index for a given dataId in O(1).
		 * @param did the dataId to look up
		 * @return the row index, or -1 if not found
		 */
		int rowIndexByDataId(int did) {
			return (did >= 0 && did <= maxDataId) ? dataIdIndex[did] : -1;
		}
	}

	// =========================================================================
	// Pseudo-IDList stored as parallel int[] arrays
	// =========================================================================

	/**
	 * Compact representation of a Pseudo-IDList: each row references a row in
	 * the DIP (Data-IDList of a 1-pattern) plus a start offset in its positions.
	 */
	private static final class PseudoDB {
		/** Number of rows */
		int support;
		/** dataId[i] = the dataId into the DIP for row i */
		int[] dataId;
		/** startIndex[i] = the start offset within the DIP row's position array */
		int[] startIndex;
		/** The Data-IDList (DIP) of the 1-pattern this Pseudo-IDList refers to */
		VertDB dip;

		/**
		 * Create a new PseudoDB with arrays pre-allocated to the given capacity.
		 * @param capacity the initial number of rows to allocate for
		 * @param dip the underlying Data-IDList (DIP) that this Pseudo-IDList references
		 */
		PseudoDB(int capacity, VertDB dip) {
			this.dip = dip;
			dataId = new int[capacity];
			startIndex = new int[capacity];
		}
	}

	// =========================================================================
	// IDListEntry: either a VertDB or a PseudoDB, plus cached resolved arrays
	// =========================================================================

	/**
	 * An entry in an equivalence class: a pattern together with either a
	 * Data-IDList or a Pseudo-IDList, and lazily-resolved parallel arrays
	 * used to speed up join operations.
	 */
	private static final class Entry {
		/** True if this entry stores a Pseudo-IDList rather than a Data-IDList */
		boolean isPseudo;
		/** The Data-IDList, non-null only if !isPseudo */
		VertDB vdb;
		/** The Pseudo-IDList, non-null only if isPseudo */
		PseudoDB pdb;

		/** The pattern represented by this entry */
		Pattern pattern;

		/** True once the resolved arrays below have been computed */
		boolean resolved;
		/** Number of rows in the resolved arrays */
		int resolvedCount;
		/** rTsid[i] = tsid of resolved row i */
		int[] rTsid;
		/** rDataId[i] = dataId of resolved row i (used for s-extension joins) */
		int[] rDataId;
		/** rPositions[i] = position array of resolved row i */
		int[][] rPositions;
		/** rPosStart[i] = start offset within rPositions[i] */
		int[] rPosStart;
		/** rPosLen[i] = number of usable positions from rPosStart[i] in rPositions[i] */
		int[] rPosLen;

		/**
		 * Create an entry backed by a Data-IDList.
		 * @param vdb the Data-IDList
		 * @param pattern the pattern represented by this entry
		 */
		Entry(VertDB vdb, Pattern pattern) {
			this.isPseudo = false;
			this.vdb = vdb;
			this.pattern = pattern;
		}

		/**
		 * Create an entry backed by a Pseudo-IDList.
		 * @param pdb the Pseudo-IDList
		 * @param pattern the pattern represented by this entry
		 */
		Entry(PseudoDB pdb, Pattern pattern) {
			this.isPseudo = true;
			this.pdb = pdb;
			this.pattern = pattern;
		}

		/**
		 * Get the support (number of sequences) of this entry's pattern.
		 * @return the support count
		 */
		int getSupport() {
			return isPseudo ? pdb.support : vdb.support;
		}

		/**
		 * Populate the cached resolved arrays for this entry, if not already done.
		 * Subsequent calls are no-ops.
		 */
		void ensureResolved() {
			if (resolved)
				return;
			resolved = true;
			if (!isPseudo) {
				// Data-IDList: arrays can be referenced directly, no copy needed
				int n = vdb.support;
				resolvedCount = n;
				rTsid = vdb.tsid;
				rDataId = vdb.dataId;
				rPositions = vdb.positions;
				rPosStart = new int[n]; // all zeros
				rPosLen = new int[n];
				for (int i = 0; i < n; i++) {
					rPosLen[i] = vdb.positions[i].length;
				}
			} else {
				// Pseudo-IDList: resolve each row against its DIP
				PseudoDB p = pdb;
				VertDB dip = p.dip;
				int n = p.support;
				resolvedCount = n;
				rTsid = new int[n];
				rDataId = new int[n];
				rPositions = new int[n][];
				rPosStart = new int[n];
				rPosLen = new int[n];
				for (int i = 0; i < n; i++) {
					int did = p.dataId[i];
					int ri = dip.rowIndexByDataId(did);
					rDataId[i] = did;
					rTsid[i] = dip.tsid[ri];
					rPositions[i] = dip.positions[ri];
					rPosStart[i] = p.startIndex[i];
					rPosLen[i] = dip.positions[ri].length - p.startIndex[i];
				}
			}
		}
	}

	// =========================================================================
	// Main entry point
	// =========================================================================

	/**
	 * Run the SUI algorithm on an input sequence database.
	 *
	 * @param inputFile  path to the SPMF-format input file
	 * @param outputFile path to the output file
	 * @param minSupport minimum support as a percentage of the number of sequences (e.g. 0.5 = 50%)
	 * @throws IOException if an I/O error occurs
	 */
	public void runAlgorithm(String inputFile, String outputFile, double minSupport) throws IOException {
		this.minSupportPercentage = minSupport;
		this.patternCount = 0;

		writer = new BufferedWriter(new FileWriter(outputFile), 65536);
		writer.write("@FILETYPE=\"Frequent sequential patterns\"");
		writer.newLine();
		writer.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
		writer.newLine();
		
		startTime = System.currentTimeMillis();

		// Step 1: Build vertical database for frequent 1-patterns
		loadDatabaseAndBuild1PatternVertDBs(inputFile);

		// Build initial equivalence class (sorted by item rank),
		// respecting the maximum pattern length constraint
		List<Entry> freq1Entries = new ArrayList<>(numFreqItems);
		if (maxPatternLength >= 1) {
			boolean writeLength1 = (1 >= minPatternLength);
			for (int rank = 0; rank < numFreqItems; rank++) {
				int item = freqItems[rank];
				VertDB vdb = vertDBs[rank];
				Pattern pat = makePattern1(item);
				Entry e = new Entry(vdb, pat);
				freq1Entries.add(e);
				if (writeLength1) {
					writePattern(pat, vdb.support);
				}
			}
		}

		// Steps 2&3: Recursively mine all frequent patterns
		int[] scratch = new int[1024]; // reusable scratch buffer for intersections
		if (maxPatternLength >= 1 && !freq1Entries.isEmpty()) {
			mineEquivalenceClass(freq1Entries, scratch, 1);
		}

		endTime = System.currentTimeMillis();
		writer.close();
	}

	// =========================================================================
	// Optional constraints: minimum/maximum pattern length
	// =========================================================================

	/**
	 * Set the minimum number of items that a pattern must contain (optional, default = 0).
	 * @param minimumPatternLength the minimum pattern length
	 */
	public void setMinimumPatternLength(int minimumPatternLength) {
		this.minPatternLength = minimumPatternLength;
	}

	/**
	 * Set the maximum number of items that a pattern can contain (optional, default = infinity).
	 * @param maximumPatternLength the maximum pattern length
	 */
	public void setMaximumPatternLength(int maximumPatternLength) {
		this.maxPatternLength = maximumPatternLength;
	}

	// =========================================================================
	// Step 1: Database loading
	// =========================================================================

	/**
	 * Read the input file and build a Data-IDList (VertDB) for each frequent 1-pattern.
	 *
	 * @param inputFile path to the SPMF-format input file
	 * @throws IOException if an I/O error occurs
	 */
	private void loadDatabaseAndBuild1PatternVertDBs(String inputFile) throws IOException {

		// Map item -> packed sequence data: [tsid, pos, pos, ..., -1, tsid, pos, ..., -1, ...]
		Map<Integer, int[]> itemPackedData = new HashMap<>(256);
		Map<Integer, Integer> itemPackedLen = new HashMap<>(256);
		// Track the number of sequences containing each item
		Map<Integer, Integer> itemSeqCount = new HashMap<>(256);

		BufferedReader reader = new BufferedReader(new FileReader(inputFile), 65536);
		String line;
		int tsid = 0;

		while ((line = reader.readLine()) != null) {
			// skip empty or comment lines
			int lineLen = line.length();
			if (lineLen == 0)
				continue;
			char first = line.charAt(0);
			if (first == '#' || first == '%' || first == '@')
				continue;
			boolean allSpace = true;
			for (int ci = 0; ci < lineLen; ci++) {
				if (line.charAt(ci) != ' ') {
					allSpace = false;
					break;
				}
			}
			if (allSpace)
				continue;

			tsid++;
			int itemsetIndex = 1;

			// accumulate item positions for this sequence before appending to packed arrays
			Map<Integer, int[]> seqItemPositions = new HashMap<>(16);
			Map<Integer, Integer> seqItemPosLen = new HashMap<>(16);

			int pos = 0;
			while (pos <= lineLen) {
				// skip spaces
				while (pos < lineLen && line.charAt(pos) == ' ')
					pos++;
				if (pos >= lineLen)
					break;
				// parse an integer token (possibly negative)
				boolean negative = false;
				if (line.charAt(pos) == '-') {
					negative = true;
					pos++;
				}
				int val = 0;
				while (pos < lineLen && line.charAt(pos) >= '0') {
					val = val * 10 + (line.charAt(pos) - '0');
					pos++;
				}
				if (negative)
					val = -val;

				if (val == -2) {
					break; // end of sequence
				} else if (val == -1) {
					itemsetIndex++; // end of itemset
				} else {
					// record the position of this item in the current sequence
					int[] posArr = seqItemPositions.get(val);
					if (posArr == null) {
						posArr = new int[4];
						seqItemPositions.put(val, posArr);
						seqItemPosLen.put(val, 0);
					}
					int plen = seqItemPosLen.get(val);
					if (plen >= posArr.length) {
						posArr = Arrays.copyOf(posArr, posArr.length * 2);
						seqItemPositions.put(val, posArr);
					}
					posArr[plen] = itemsetIndex;
					seqItemPosLen.put(val, plen + 1);
				}
			}

			// append this sequence's data to the per-item packed arrays
			for (Map.Entry<Integer, int[]> e : seqItemPositions.entrySet()) {
				int item = e.getKey();
				int[] pArr = e.getValue();
				int pLen = seqItemPosLen.get(item);

				int[] packed = itemPackedData.get(item);
				int pOff = itemPackedLen.getOrDefault(item, 0);

				int need = 1 + pLen + 1; // tsid + positions + sentinel
				if (packed == null) {
					packed = new int[Math.max(need * 4, 16)];
				} else if (pOff + need > packed.length) {
					packed = Arrays.copyOf(packed, Math.max(packed.length * 2, pOff + need));
				}
				packed[pOff++] = tsid;
				for (int pi = 0; pi < pLen; pi++)
					packed[pOff++] = pArr[pi];
				packed[pOff++] = -1; // sentinel: end of this sequence's positions

				itemPackedData.put(item, packed);
				itemPackedLen.put(item, pOff);
				itemSeqCount.merge(item, 1, Integer::sum);
			}
		}
		reader.close();

		// convert the percentage of minimum support to an absolute number of sequences
		minSupport = (int) Math.ceil((minSupportPercentage * tsid) - 0.000001);

		// keep only frequent items, sorted by item value
		int[] allItems = new int[itemSeqCount.size()];
		int ai = 0;
		for (int item : itemSeqCount.keySet()) {
			if (itemSeqCount.get(item) >= minSupport) {
				allItems[ai++] = item;
			}
		}
		numFreqItems = ai;
		Arrays.sort(allItems, 0, numFreqItems);

		freqItems = new int[numFreqItems];
		vertDBs = new VertDB[numFreqItems];

		// build the Data-IDList (VertDB) for each frequent item
		for (int rank = 0; rank < numFreqItems; rank++) {
			int item = allItems[rank];
			freqItems[rank] = item;
			int seqCnt = itemSeqCount.get(item);
			int[] packed = itemPackedData.get(item);
			int packLen = itemPackedLen.get(item);

			VertDB vdb = new VertDB(seqCnt);
			vdb.support = seqCnt;
			int dataId = 1;
			int rowIdx = 0;
			int ppos = 0;

			// unpack the sequence data into rows of the VertDB
			while (ppos < packLen) {
				int curTsid = packed[ppos++];
				int posStart = ppos;
				int posCount = 0;
				while (ppos < packLen && packed[ppos] != -1) {
					ppos++;
					posCount++;
				}
				ppos++; // skip sentinel

				int[] posArr = Arrays.copyOfRange(packed, posStart, posStart + posCount);
				vdb.dataId[rowIdx] = dataId;
				vdb.tsid[rowIdx] = curTsid;
				vdb.positions[rowIdx] = posArr;
				rowIdx++;
				dataId++;
			}
			vdb.finishBuilding();
			vertDBs[rank] = vdb;
		}
	}

	// =========================================================================
	// Steps 2&3: Equivalence class recursion
	// =========================================================================

	/**
	 * Recursively mine an equivalence class of patterns sharing the same prefix,
	 * generating and writing all frequent extensions (s-extension and i-extension)
	 * while respecting the minimum/maximum pattern length constraints.
	 *
	 * @param equivClass    the entries of patterns sharing the same (k-1)-prefix
	 * @param scratch       reusable scratch buffer used for position-list intersections
	 * @param currentLength the number of items contained in each pattern of equivClass
	 * @throws IOException if an I/O error occurs
	 */
	private void mineEquivalenceClass(List<Entry> equivClass, int[] scratch, int currentLength) throws IOException {

		// stop if extending would exceed the maximum pattern length
		if (currentLength >= maxPatternLength) {
			return;
		}

		int newLength = currentLength + 1;
		boolean shouldWrite = (newLength >= minPatternLength);

		int n = equivClass.size();

		// pre-resolve all entries so join loops operate on raw arrays
		for (int i = 0; i < n; i++) {
			equivClass.get(i).ensureResolved();
		}

		for (int i = 0; i < n; i++) {
			Entry entryX = equivClass.get(i);
			boolean xIsIExt = entryX.pattern.isIExtension();
			int lastItemX = entryX.pattern.getLastItem();

			List<Entry> newEquivClass = new ArrayList<>();

			// try s-extension candidates (Y must be an s-ext pattern)
			for (int j = 0; j < n; j++) {
				Entry entryY = equivClass.get(j);
				if (entryY.pattern.isIExtension())
					continue;

				Entry z = generateSExtensionCandidate(entryX, entryY);
				if (z != null && z.getSupport() >= minSupport) {
					if (shouldWrite) {
						writePattern(z.pattern, z.getSupport());
					}
					newEquivClass.add(z);
				}
			}

			// try i-extension candidates (same extension type, larger last item)
			for (int j = i + 1; j < n; j++) {
				Entry entryY = equivClass.get(j);
				int lastItemY = entryY.pattern.getLastItem();

				if (entryY.pattern.isIExtension() != xIsIExt)
					continue;
				if (lastItemY <= lastItemX)
					continue;

				Entry z = generateIExtensionCandidate(entryX, entryY, scratch);
				if (z != null && z.getSupport() >= minSupport) {
					if (shouldWrite) {
						writePattern(z.pattern, z.getSupport());
					}
					newEquivClass.add(z);
				}
			}

			// recurse into the next equivalence class, if any candidates were found
			if (!newEquivClass.isEmpty()) {
				mineEquivalenceClass(newEquivClass, scratch, newLength);
			}
		}
	}

	// =========================================================================
	// Algorithm 1: S-extension candidate (Pseudo-IDList)
	// =========================================================================

	/**
	 * Generate the Pseudo-IDList for the s-extension of X with Y's last item,
	 * by merge-joining their resolved rows on tsid.
	 *
	 * @param entryX the prefix pattern entry
	 * @param entryY the s-ext pattern entry providing the item to append
	 * @return the new entry, or null if the resulting pattern has zero support
	 */
	private Entry generateSExtensionCandidate(Entry entryX, Entry entryY) {
		// Z's DIP is the Data-IDList of Y's last (1-pattern) item
		VertDB zDip = getDipVertDB(entryY);
		entryY.ensureResolved();

		int sizeX = entryX.resolvedCount;
		int sizeY = entryY.resolvedCount;
		int capacity = Math.min(sizeX, sizeY);

		PseudoDB mZ = new PseudoDB(capacity, zDip);

		int idxX = 0, idxY = 0;

		// merge-join on tsid
		while (idxX < sizeX && idxY < sizeY) {
			int tx = entryX.rTsid[idxX];
			int ty = entryY.rTsid[idxY];

			if (tx < ty) {
				idxX++;
			} else if (tx > ty) {
				idxY++;
			} else {
				// matching tsid: look for an occurrence of Y's item after X's first position
				int[] xPos = entryX.rPositions[idxX];
				int xStart = entryX.rPosStart[idxX];
				int xLen = entryX.rPosLen[idxX];

				if (xLen > 0) {
					int eX = xPos[xStart];

					int yDataId = entryY.rDataId[idxY];
					int yStartIndex = entryY.rPosStart[idxY];

					int dipRowIdx = zDip.rowIndexByDataId(yDataId);
					if (dipRowIdx >= 0) {
						int[] yFull = zDip.positions[dipRowIdx];
						int yFLen = yFull.length;
						// find the first position strictly after eX
						for (int k = yStartIndex; k < yFLen; k++) {
							if (yFull[k] > eX) {
								int sup = mZ.support;
								if (sup >= mZ.dataId.length) {
									mZ.dataId = Arrays.copyOf(mZ.dataId, sup * 2 + 1);
									mZ.startIndex = Arrays.copyOf(mZ.startIndex, sup * 2 + 1);
								}
								mZ.dataId[sup] = yDataId;
								mZ.startIndex[sup] = k;
								mZ.support++;
								break;
							}
						}
					}
				}
				idxX++;
				idxY++;
			}
		}

		if (mZ.support == 0)
			return null;

		Pattern Z = entryX.pattern.sExtend(entryY.pattern.getLastItem());
		return new Entry(mZ, Z);
	}

	// =========================================================================
	// Algorithm 2: I-extension candidate (Data-IDList)
	// =========================================================================

	/**
	 * Generate the Data-IDList for the i-extension of X with Y's last item,
	 * by merge-joining on tsid and intersecting effective position lists.
	 *
	 * @param entryX  the prefix pattern entry
	 * @param entryY  the pattern entry providing the item to add to the last itemset
	 * @param scratch reusable scratch buffer used for the position-list intersection
	 * @return the new entry, or null if the resulting pattern has zero support
	 */
	private Entry generateIExtensionCandidate(Entry entryX, Entry entryY, int[] scratch) {
		entryY.ensureResolved();

		int sizeX = entryX.resolvedCount;
		int sizeY = entryY.resolvedCount;
		int capacity = Math.min(sizeX, sizeY);

		VertDB mZ = new VertDB(capacity);
		int idxX = 0, idxY = 0, dataId = 1;

		// merge-join on tsid
		while (idxX < sizeX && idxY < sizeY) {
			int tx = entryX.rTsid[idxX];
			int ty = entryY.rTsid[idxY];

			if (tx < ty) {
				idxX++;
			} else if (tx > ty) {
				idxY++;
			} else {
				// matching tsid: intersect the effective position lists
				int[] xPos = entryX.rPositions[idxX];
				int xStart = entryX.rPosStart[idxX];
				int xLen = entryX.rPosLen[idxX];
				int[] yPos = entryY.rPositions[idxY];
				int yStart = entryY.rPosStart[idxY];
				int yLen = entryY.rPosLen[idxY];

				int intLen = intersectPrimitive(xPos, xStart, xLen, yPos, yStart, yLen, scratch);
				if (intLen > 0) {
					int rowIdx = mZ.support;
					mZ.dataId[rowIdx] = dataId;
					mZ.tsid[rowIdx] = tx;
					mZ.positions[rowIdx] = Arrays.copyOf(scratch, intLen);
					mZ.support++;
					dataId++;
				}
				idxX++;
				idxY++;
			}
		}

		if (mZ.support == 0)
			return null;

		// trim arrays to the actual number of rows
		mZ.dataId = Arrays.copyOf(mZ.dataId, mZ.support);
		mZ.tsid = Arrays.copyOf(mZ.tsid, mZ.support);
		mZ.positions = Arrays.copyOf(mZ.positions, mZ.support);
		mZ.finishBuilding();

		Pattern Z = entryX.pattern.iExtend(entryY.pattern.getLastItem());
		return new Entry(mZ, Z);
	}

	// =========================================================================
	// Primitive intersection (no boxing, no allocation in hot path)
	// =========================================================================

	/**
	 * Compute the intersection of two sorted int[] subarrays into an output buffer.
	 *
	 * @param a      first array
	 * @param aStart start offset in a
	 * @param aLen   number of elements from aStart
	 * @param b      second array
	 * @param bStart start offset in b
	 * @param bLen   number of elements from bStart
	 * @param out    output buffer (must be at least min(aLen, bLen) long)
	 * @return the number of elements written to out
	 */
	private static int intersectPrimitive(int[] a, int aStart, int aLen, int[] b, int bStart, int bLen, int[] out) {
		int i = aStart, aEnd = aStart + aLen;
		int j = bStart, bEnd = bStart + bLen;
		int k = 0;
		while (i < aEnd && j < bEnd) {
			int ea = a[i], eb = b[j];
			if (ea == eb) {
				out[k++] = ea;
				i++;
				j++;
			} else if (ea < eb) {
				i++;
			} else {
				j++;
			}
		}
		return k;
	}

	// =========================================================================
	// Helper: get DIP VertDB for an entry
	// =========================================================================

	/**
	 * Get the Data-IDList (DIP) of the 1-pattern for an entry's last item.
	 *
	 * @param entry the entry to inspect
	 * @return the DIP VertDB (the entry's own VertDB, or its PseudoDB's dip)
	 */
	private static VertDB getDipVertDB(Entry entry) {
		return entry.isPseudo ? entry.pdb.dip : entry.vdb;
	}

	// =========================================================================
	// Pattern construction helpers
	// =========================================================================

	/**
	 * Build a 1-pattern containing a single item.
	 * @param item the item value
	 * @return a new pattern with one itemset containing only the item
	 */
	private static Pattern makePattern1(int item) {
		Pattern p = new Pattern();
		List<Integer> itemset = new ArrayList<>(1);
		itemset.add(item);
		p.getItemsets().add(itemset);
		p.setIExtension(false);
		return p;
	}

	// =========================================================================
	// Output
	// =========================================================================

	/** Reusable buffer to avoid per-call allocation when writing patterns */
	private final StringBuilder sb = new StringBuilder(64);

	/**
	 * Write a frequent pattern and its support to the output file in SPMF format.
	 *
	 * @param pattern the pattern to write
	 * @param support the support of the pattern
	 * @throws IOException if an I/O error occurs
	 */
	private void writePattern(Pattern pattern, int support) throws IOException {
		sb.setLength(0);
		pattern.appendTo(sb);
		sb.append(" #SUP: ");
		sb.append(support);
		writer.write(sb.toString());
		writer.newLine();
		patternCount++;
	}

	// =========================================================================
	// Statistics
	// =========================================================================

	/** Print statistics about the last execution to the console. */
	public void printStatistics() {
		System.out.println("=============  SUI ALGORITHM v.2.67 - STATISTICS =============");
		System.out.println(" Total time ~ " + (endTime - startTime) + " ms");
		System.out.println(" Frequent sequences count : " + patternCount);
		System.out.println(" Min support (sequences)  : " + minSupport);
		System.out.println("========================================================");
	}

	/**
	 * Get the number of frequent patterns found by the last execution.
	 * @return the pattern count
	 */
	public int getPatternCount() {
		return patternCount;
	}

	/**
	 * Get the execution time of the last run.
	 * @return the execution time in milliseconds
	 */
	public long getExecutionTime() {
		return endTime - startTime;
	}
}