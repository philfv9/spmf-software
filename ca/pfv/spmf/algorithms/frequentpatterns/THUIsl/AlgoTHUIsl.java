package ca.pfv.spmf.algorithms.frequentpatterns.THUIsl;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;

/* This file is copyright (c) 2024 Srikumar Krishnamoorthy
 * 
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */



import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PriorityQueue;
import java.util.Set;
/**
* Implementation of the THUI (Top-k High-Utility Itemset) algorithm
* adapted for supervised learning.
*
* This algorithm mines top-k high-utility itemsets using
* information gain as an additional constraint for feature selection.
* It supports EUCS-based pruning and leaf pruning optimizations.
*
* Main functionalities:
* <ul>
*   <li>Mine high-utility itemsets with IG filtering</li>
*   <li>Apply pruning strategies (EUCS, Leaf)</li>
*   <li>Perform feature selection (top-k by IG)</li>
*   <li>Generate sparse transaction-pattern matrices (train/test)</li>
* </ul>
*
* Typical usage:
* AlgoTHUIsl algo = new AlgoTHUIsl(topK);
* algo.runAlgorithm(input, output, ...);
* algo.applyPatterns(inputTest, output, ...);
*
* @author Srikumar Krishnamoorthy
*/
public class AlgoTHUIsl {// THUI for supervised learning

	double maxMemory = 0;
	long startTimestamp = 0;
	long endTimestamp = 0;
	int huiCount = 0; // the number of HUI generated
	int huiCountDistinct = 0; // the number of HUI generated (after duplicate removal)
	int candidateCount = 0;
	int totalItems = 0;
	int B;// bin size

	List<Float> itemTWU;
	List<Double> ytrain_reg;

	float minUtility = 0;
	double minIG = 0;
	int topkstatic = 0;
	String dsName = null;

	// BufferedWriter writerStats = null;
	DataOutputStream writerTidSparse = null;
	DataOutputStream writerTidSparseTest = null;

	BufferedWriter writerUtilFS = null;
	BufferedWriter writerUtilFSmapped = null;// mapped to original column and bin names

	int totalTrans = 0;
	double trainDensity = 0, testDensity = 0;

	PriorityQueue<Pattern> kPatterns = new PriorityQueue<Pattern>();
	PriorityQueue<Float> leafPruneUtils = null;

	boolean debug = false;
	int totalItem = 0;
	final int BUFFERS_SIZE = 200;
	private int[] itemsetBuffer = null;
	int fsK = 0;// number of features/predictors to select
	int huiMaxLen = -1;

	private java.text.DecimalFormat df = new java.text.DecimalFormat("#.00");
	Map<Integer, Map<Integer, Item>> mapFMAP = null;
	private StringBuilder buffer = new StringBuilder(32);

	Map<Integer, Map<Integer, Float>> mapLeafMAP = null;
	float riuRaiseValue = 0, leafRaiseValue = 0;
	int leafMapSize = 0;

	float[] totUtil;
	int[] ej;
	int[] pos;
	float[] twu;

	boolean EUCS_PRUNE = false;
	boolean LEAF_PRUNE = true;
	String inputNS = "";
	int totalTransNS = 0;
	int totalTransTest = 0;

	int droppedULcount = 0, igFilterCount = 0;
	double igThreshold;

	int PATTERN_SIZE_ORIG = 0;
	int PATTERN_SIZE_DISTINCT = 0;
	int PATTERN_SIZE_DISJUNCTIVE = 0;
	int PATTERN_SIZE_FS = 0;

	class Pair {
		int item = 0;
		float utility = 0;

		Pair(int item, float utility) {
			this.item = item;
			this.utility = utility;
		}

		public String toString() {
			return "[" + item + "," + utility + "]";
		}
	}

	class PairComparator implements Comparator<Pair> {
		@Override
		public int compare(Pair o1, Pair o2) {
			return compareItems(o1.item, o2.item);
		}
	}

	class UtilComparator implements Comparator<UtilityList> {
		@Override
		public int compare(UtilityList o1, UtilityList o2) {
			return compareItems(o1.item, o2.item);
		}
	}

	private int compareItems(int item1, int item2) {
		float r1 = itemTWU.get(item1 - 1);
		float r2 = itemTWU.get(item2 - 1);
		if (r1 < r2)
			return -1;// asc order of TWU
		else if (r1 > r2)
			return 1;
		else
			return 0;
	}

	public AlgoTHUIsl(int top) {
		this.topkstatic = top;
	}

	String inputFile;

	public void runAlgorithm(String input, String output, int fsK, int B, int L, double G, String dsName, int foldNo,
			double imbWeights, int numRows, int numIntCols, int numFloatCols, int numCatCols, int numClasses)
			throws IOException {

		startTimestamp = System.currentTimeMillis();
		maxMemory = 0;
		itemsetBuffer = new int[BUFFERS_SIZE];

		itemTWU = new ArrayList<Float>();
		List<List<ItemUtility>> transactions = new ArrayList<List<ItemUtility>>();
		Map<Integer, String> itemMap = new HashMap<Integer, String>();
		List<Integer> ytrain = new ArrayList<Integer>();
		// String outputTidSparseFile =
		// output+dsName+"_tid_sparse_"+String.valueOf(foldNo)+".bin";
		String outputTidSparseFile = output + dsName + "_tid_sparse.bin";

		// String outputUtilFileFS =
		// output+dsName+"_util_fs_"+String.valueOf(foldNo)+".txt";
		String outputUtilFileFS = output + dsName + "_util_fs.bin";

		// String outputUtilFileFSMapped =
		// output+dsName+"_util_fs_mapped_"+String.valueOf(foldNo)+".txt";
		String outputUtilFileFSMapped = output + dsName + "_util_fs_mapped.txt";

		// String outputStats = output+dsName + "_stats.txt";//fold no not required for
		// stats - each fold results written to the same file (final average across
		// folds computed in python)

		writerUtilFS = new BufferedWriter(new FileWriter(outputUtilFileFS));
		writerUtilFSmapped = new BufferedWriter(new FileWriter(outputUtilFileFSMapped));

		// enable for storing train density information
		// if (foldNo>1) writerStats = new BufferedWriter(new FileWriter(outputStats,
		// true));//append to file
		// else writerStats = new BufferedWriter(new FileWriter(outputStats,
		// false));//create new file

		this.EUCS_PRUNE = true;
		this.fsK = fsK;// number of features/predictors to select
		this.huiMaxLen = L;
		this.igThreshold = G;
		if (dsName != null)
			this.dsName = dsName;

		inputFile = input;
		if (this.huiMaxLen > 1 && EUCS_PRUNE) {// disable EUCS for constrained top-k mining with maxlen<2
			mapFMAP = new HashMap<Integer, Map<Integer, Item>>();
		}
		if (this.huiMaxLen == -1 && LEAF_PRUNE) {
			mapLeafMAP = new HashMap<Integer, Map<Integer, Float>>();
			leafPruneUtils = new PriorityQueue<Float>();
		}

		TransactionGenerator tg = new TransactionGenerator();
		minUtility = tg.prepare_data(input, dsName, B, imbWeights, numRows, transactions, itemTWU, itemMap, ytrain,
				this.topkstatic, numIntCols, numFloatCols, numCatCols, numClasses);
		riuRaiseValue = minUtility;

		List<UtilityList> listOfUtilityLists = new ArrayList<UtilityList>();
		UtilityList[] itemUtilityList = new UtilityList[itemTWU.size()];
		for (int mi = 0; mi < itemTWU.size(); mi++) {
			if (itemTWU.get(mi) >= minUtility) {
				UtilityList uList = new UtilityList(mi + 1);
				itemUtilityList[mi] = uList;
				listOfUtilityLists.add(uList);
			}
		}
		Collections.sort(listOfUtilityLists, new UtilComparator());
		totalItems = listOfUtilityLists.size();

		// second iteration of transaction database
		float remainingUtility = 0;
		float newTWU = 0;
		String key = null;
		Integer kTid;
		int tid = 0;
		for (int ti = 0; ti < transactions.size(); ti++) {
			List<ItemUtility> itemPairs = (List<ItemUtility>) transactions.get(ti);
			remainingUtility = 0;
			newTWU = 0;
			List<Pair> revisedTransaction = new ArrayList<Pair>();
			for (ItemUtility p : itemPairs) {
				if (itemTWU.get(p.item - 1) >= minUtility) {
					revisedTransaction.add(new Pair(p.item, p.utility));
					remainingUtility += p.utility;
					newTWU += p.utility;
				}
			}
			Collections.sort(revisedTransaction, new PairComparator());
			remainingUtility = 0;
			for (int i = revisedTransaction.size() - 1; i >= 0; i--) {
				Pair pair = revisedTransaction.get(i);
				UtilityList utilityListOfItem = itemUtilityList[pair.item - 1];
				Element element = new Element(tid, pair.utility, remainingUtility);
				utilityListOfItem.addElement(element);

				if (this.huiMaxLen > 1 && EUCS_PRUNE)
					updateEUCSprune(i, pair, revisedTransaction, newTWU);
				if (this.huiMaxLen == -1 && LEAF_PRUNE)
					updateLeafprune(i, pair, revisedTransaction, listOfUtilityLists);
				remainingUtility += pair.utility;
			}
			tid++; // increase tid number for next transaction
		}
		totalTrans = tid;

		// compute entropy, information gain of each utility list (parent is the ytrain)
		for (UtilityList u : listOfUtilityLists) {
			u.setStats(null, ytrain, numClasses);
		}

		if (this.huiMaxLen > 1 && EUCS_PRUNE) {// disable EUCS for constrained top-k mining with maxlen<2
			raisingThresholdCUDOptimize(this.topkstatic);
			removeEntry();
		}

		// perform additional pruning - eucs/leaf prune, if applicable
		if (this.huiMaxLen == -1 && LEAF_PRUNE) {// disable LEAF_PRUNE for constrained top-k mining with maxlen!='all'
													// or -1 case
			raisingThresholdLeaf(listOfUtilityLists);
			setLeafMapSize();
			removeLeafEntry();
			leafPruneUtils = null;
		}
		leafRaiseValue = minUtility;

		// run iterative thui exploration
		checkMemory();
		thui(itemsetBuffer, 0, null, listOfUtilityLists, ytrain, numClasses, igThreshold);
		checkMemory();

		writeResultTofile(foldNo, outputTidSparseFile, outputUtilFileFS, itemMap);
		endTimestamp = System.currentTimeMillis();
		printStats(foldNo);
	}

	private void updateEUCSprune(int i, Pair pair, List<Pair> revisedTransaction, float newTWU) {
		Map<Integer, Item> mapFMAPItem = mapFMAP.get(pair.item);
		if (mapFMAPItem == null) {
			mapFMAPItem = new HashMap<Integer, Item>();
			mapFMAP.put(pair.item, mapFMAPItem);
		}
		for (int j = i + 1; j < revisedTransaction.size(); j++) {
			if (pair.item == revisedTransaction.get(j).item)
				continue;// kosarak dataset has duplicate items
			Pair pairAfter = revisedTransaction.get(j);
			Item twuItem = mapFMAPItem.get(pairAfter.item);
			if (twuItem == null)
				twuItem = new Item();
			twuItem.twu += newTWU;
			twuItem.utility += (float) pair.utility + pairAfter.utility;
			mapFMAPItem.put(pairAfter.item, twuItem);
		}
	}

	private void updateLeafprune(int i, Pair pair, List<Pair> revisedTransaction, List<UtilityList> ULs) {
		float cutil = (float) pair.utility;
		int followingItemIdx = getTWUindex(pair.item, ULs) + 1;
		Map<Integer, Float> mapLeafItem = mapLeafMAP.get(followingItemIdx);
		if (mapLeafItem == null) {
			mapLeafItem = new HashMap<Integer, Float>();
			mapLeafMAP.put(followingItemIdx, mapLeafItem);
		}
		for (int j = i - 1; j >= 0; j--) {
			if (pair.item == revisedTransaction.get(j).item)
				continue;// kosarak dataset has duplicate items
			Pair pairAfter = revisedTransaction.get(j);

			if (ULs.get(--followingItemIdx).item != pairAfter.item)
				break;
			Float twuItem = mapLeafItem.get(followingItemIdx);
			if (twuItem == null)
				twuItem = (float) 0.0;// new Float(0);
			cutil += pairAfter.utility;
			twuItem += cutil;
			mapLeafItem.put(followingItemIdx, twuItem);
		}
	}

	private int getTWUindex(int item, List<UtilityList> ULs) {
		for (int i = ULs.size() - 1; i >= 0; i--)
			if (ULs.get(i).item == item)
				return i;
		return -1;
	}

	private void setLeafMapSize() {
		for (Entry<Integer, Map<Integer, Float>> entry : mapLeafMAP.entrySet())
			leafMapSize += entry.getValue().keySet().size();
	}

	private void thui(int[] prefix, int prefixLength, UtilityList pUL, List<UtilityList> ULs, List<Integer> ytrain,
			int num_classes, double igThreshold) throws IOException {
		for (int i = ULs.size() - 1; i >= 0; i--) {
			if (ULs.get(i).getUtils() >= minUtility && ULs.get(i).getUtils() > 0) // remove zero utility cases
				if (huiMaxLen == -1 || prefixLength + 1 <= huiMaxLen)// filter by maxLen; with -1 no filter is applied
					if (ULs.get(i).ig > igThreshold)
						save(prefix, prefixLength, ULs.get(i));
		}
		if (huiMaxLen != -1 && prefixLength + 1 >= huiMaxLen)
			return;
		for (int i = ULs.size() - 2; i >= 0; i--) {// last item is a single item, and hence no extension
			checkMemory();
			UtilityList X = ULs.get(i);
			if (X.ig <= igThreshold) {
				igFilterCount++;
				continue;
			}

			if (X.sumIutils + X.sumRutils >= minUtility && X.sumIutils > 0) {// the utility value of zero cases can be
																				// safely ignored, as it is unlikely to
																				// generate a HUI; besides the lowest
																				// min utility will be 1
				if (EUCS_PRUNE) {
					Map<Integer, Item> mapTWUF = mapFMAP.get(X.item);
					if (mapTWUF == null)
						continue;
				}
				List<UtilityList> exULs = new ArrayList<UtilityList>();
				for (int j = i + 1; j < ULs.size(); j++) {
					UtilityList Y = ULs.get(j);
					if (Y.ig <= igThreshold) {
						igFilterCount++;
						continue;
					}

					candidateCount++;
					UtilityList exul = construct(pUL, X, Y);
					if (exul != null) {
						exul.setStats(pUL, ytrain, num_classes);

						// same tid ULs discarded, they would lead to same set of patterns; distinct
						// pattern filter
						boolean matchFound = false;
						for (UtilityList exi : exULs) {
							if (exi.tidsMatch(exul.elements)) {
								matchFound = true;
								break;
							}
						}
						if (matchFound) {// do not add if match found
							droppedULcount++;
							continue;
						}
						// distinct pattern filter

						if (exul.ig > igThreshold)
							exULs.add(exul);
						else
							igFilterCount++;
					}
				}
				prefix[prefixLength] = X.item;
				thui(prefix, prefixLength + 1, X, exULs, ytrain, num_classes, igThreshold);
			}
		}
	}

	private UtilityList construct(UtilityList P, UtilityList px, UtilityList py) {
		UtilityList pxyUL = new UtilityList(py.item);
		float totUtil = px.sumIutils + px.sumRutils;
		int ei = 0, ej = 0, Pi = -1;

		Element ex = null, ey = null, e = null;
		while (ei < px.elements.size() && ej < py.elements.size()) {
			if (px.elements.get(ei).tid > py.elements.get(ej).tid) {
				++ej;
				continue;
			} // px not present, py pres
			if (px.elements.get(ei).tid < py.elements.get(ej).tid) {// px present, py not present
				totUtil = totUtil - px.elements.get(ei).iutils - px.elements.get(ei).rutils;
				if (totUtil < minUtility) {
					return null;
				}
				++ei;
				++Pi;// if a parent is present, it should be as large or larger than px; besides the
						// ordering is by tid
				continue;
			}
			ex = px.elements.get(ei);
			ey = py.elements.get(ej);

			if (P == null) {
				pxyUL.addElement(new Element(ex.tid, ex.iutils + ey.iutils, ey.rutils));
			} else {
				while (Pi < P.elements.size() && P.elements.get(++Pi).tid < ex.tid)
					;
				e = P.elements.get(Pi);

				pxyUL.addElement(new Element(ex.tid, ex.iutils + ey.iutils - e.iutils, ey.rutils));
			}
			++ei;
			++ej;
		}
		while (ei < px.elements.size()) {
			totUtil = totUtil - px.elements.get(ei).iutils - px.elements.get(ei).rutils;
			if (totUtil < minUtility) {
				return null;
			}
			++ei;
		}
		return pxyUL;
	}

	private List<String> getPatternsWithItemsMapped(List<Pattern> lpSelected, Map<Integer, String> itemMap)
			throws IOException {

		List<String> procPatterns = new ArrayList<String>();// processed patterns
		java.text.DecimalFormat df4 = new java.text.DecimalFormat("#.0000");
		java.text.DecimalFormat df8 = new java.text.DecimalFormat("#.00000000");
		for (int pi = 0; pi < lpSelected.size(); pi++) {
			Pattern p = lpSelected.get(pi);

			StringBuilder bufferUtil = new StringBuilder();
			bufferUtil.append(df4.format(p.utility));
			bufferUtil.append(" ");
			bufferUtil.append("'");

			String[] items = p.prefix.split(" ");
			for (int i = 0; i < items.length; i++) {
				bufferUtil.append(itemMap.get(Integer.parseInt(items[i])));
				if (i < items.length - 1)
					bufferUtil.append(", ");
			}
			bufferUtil.append("'");
			bufferUtil.append(" ");
			bufferUtil.append(df8.format(p.ig));

			procPatterns.add(bufferUtil.toString());
		}
		return procPatterns;
	}

	private void writePatterns(String fileName, List<Pattern> lpSelected) {

		try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(fileName));
				DataOutputStream dos = new DataOutputStream(bos)) {

			for (Pattern p : lpSelected) {
				String[] numbers = p.prefix.split(" ");
				dos.writeInt(numbers.length); // Write the count of integers in this line
				for (String number : numbers) {
					dos.writeInt(Integer.parseInt(number));
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void writeResultTofile(int foldNo, String outputTidSparseFile, String outputUtilFileFS,
			Map<Integer, String> itemMap) throws IOException {

		if (kPatterns.size() == 0)
			return;

		List<Pattern> lp = new ArrayList<Pattern>();
		Map<String, Integer> mapDuplicatePatterns = new HashMap<String, Integer>();
		Map<String, List<String>> mapDuplicatePatternsList = new HashMap<String, List<String>>();
		do {
			Pattern pattern = kPatterns.poll();
			huiCount++;
			lp.add(pattern);
		} while (kPatterns.size() > 0);
		Collections.sort(lp, new Comparator<Pattern>() {
			public int compare(Pattern o1, Pattern o2) {
				// return comparePatternsUtility(o1, o2);//sorted in descending order of utility
				// values //sorting by IG is superior
				return comparePatternsIG(o1, o2);// sorted in descending order of information gain values
			}
		});

		// check duplicate patterns after sorting - retaining the one with highest IG
		// values
		List<Integer> patternsToRemove = new ArrayList<Integer>();
		for (int i = lp.size() - 1; i >= 0; i--) {
			StringBuilder dupTidList = new StringBuilder();
			for (Integer tid : lp.get(i).tidList) {
				dupTidList.append(tid);
				dupTidList.append(" ");
			}
			String key = dupTidList.toString();
			Integer val = mapDuplicatePatterns.get(key);
			if (val != null) {// a duplicate pattern found (the one that exists in same set of transactions)
				patternsToRemove.add(i);// remove pattern; remove pattern later in the list as patterns are sorted in
										// descending order and traversal done in reverse order
				mapDuplicatePatterns.put(key, i);// update the key to the current value, to handle other duplicates that
													// come after this transaction
				continue;
			} else {
				mapDuplicatePatterns.put(key, i);// new pattern, add to dictionary
			}
		}

		PATTERN_SIZE_ORIG = lp.size();
		for (int i = 0; i < patternsToRemove.size(); i++)
			lp.remove(lp.get(patternsToRemove.get(i)));
		minUtility = lp.get(lp.size() - 1).utility;
		minIG = lp.get(lp.size() - 1).ig;
		PATTERN_SIZE_DISTINCT = lp.size();

		Map<Integer, List<Integer>> mapTransPatternNew = new HashMap<Integer, List<Integer>>();

		java.text.DecimalFormat df4 = new java.text.DecimalFormat("#.0000");
		java.text.DecimalFormat df8 = new java.text.DecimalFormat("#.00000000");

		huiCountDistinct = lp.size();// patternCntr;

		// feature selection based on top IG values
		List<Pattern> lpSelected = new ArrayList<Pattern>();
		int finalSize = lp.size();
		if (fsK != -1 && fsK < lp.size())
			finalSize = fsK;// patterns are sorted in desc order of IG values, select the top-fsK patterns
		for (int pi = 0; pi < finalSize; pi++)
			lpSelected.add(lp.get(pi));
		minUtility = lp.get(finalSize - 1).utility;
		minIG = lp.get(finalSize - 1).ig;
		// feature selection based on top IG

		// get filtered feature - transaction matrices - start
		writePatterns(outputUtilFileFS, lpSelected);

		int patternCntrNew = 0;
		for (int pi = 0; pi < lpSelected.size(); pi++) {
			Pattern pattern = lpSelected.get(pi);
			for (Integer tid : pattern.tidList) {
				List<Integer> patternList = mapTransPatternNew.get(tid - 1);
				if (patternList == null)
					patternList = new ArrayList<Integer>();
				patternList.add(patternCntrNew);
				mapTransPatternNew.put(tid - 1, patternList);
			}
			patternCntrNew += 1;
		}
		huiCountDistinct = patternCntrNew;
		PATTERN_SIZE_FS = patternCntrNew;
		writerUtilFS.close();
		// get filtered feature - end
		int row = 0, col = 0;
		int nnz = 0;
		for (int currTid = 0; currTid < totalTrans; currTid++)
			nnz += (mapTransPatternNew.get(currTid) != null ? mapTransPatternNew.get(currTid).size() : 0);

		try {
			writerTidSparse = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(outputTidSparseFile)));
			writerTidSparse.writeInt(totalTrans);
			writerTidSparse.writeInt(patternCntrNew);
			writerTidSparse.writeInt(nnz); // Number of non-zero entries

			for (int currTid = 0; currTid < totalTrans; currTid++) {
				List<Integer> pids = mapTransPatternNew.get(currTid);
				if (pids == null)
					continue;
				for (int pid = 0; pid < patternCntrNew; pid++) {
					if (pids.contains(pid)) {
						writerTidSparse.writeInt(currTid);
						writerTidSparse.writeInt(pid);
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (writerTidSparse != null) {
				try {
					writerTidSparse.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		/*
		 * enable for storing the train density trainDensity =
		 * (double)(nnz)*100/(totalTrans)/patternCntrNew;//percentage of non-zero
		 * entries StringBuilder bufferStats = new StringBuilder(); if (foldNo==1)
		 * bufferStats.append("trainDensity\n");
		 * bufferStats.append(df4.format(trainDensity));
		 * writerStats.write(bufferStats.toString()); writerStats.newLine();
		 * writerStats.close();
		 */

		List<String> procPatterns = getPatternsWithItemsMapped(lpSelected, itemMap);// get processed patterns - pattern
																					// integer id mapped to actual
																					// column and bin names
		writerUtilFSmapped.write("utility pattern ig\n");
		for (String p : procPatterns) {
			writerUtilFSmapped.write(p);
			writerUtilFSmapped.newLine();
		}
		writerUtilFSmapped.close();

		String asciiFile = outputTidSparseFile.substring(0, outputTidSparseFile.length() - 4) + "_ascii.txt";
		writeAsciiTransactionFile(asciiFile, mapTransPatternNew, lpSelected, itemMap);
	}

	private void writeAsciiTransactionFile(String outputAsciiFile, Map<Integer, List<Integer>> mapTransPatternNew,
			List<Pattern> lpSelected, Map<Integer, String> itemMap) throws IOException {

		BufferedWriter writerAscii = null;
		try {
			writerAscii = new BufferedWriter(new FileWriter(outputAsciiFile));
			for (int tid = 0; tid < totalTrans; tid++) {
				List<Integer> pids = mapTransPatternNew.get(tid);
				if (pids == null || pids.isEmpty())
					continue;

				StringBuilder transactionLine = new StringBuilder();
				int cntr = 0;
				for (Integer pid : pids) {
					cntr += 1;
					Pattern pattern = lpSelected.get(pid);
					// prefix contains space-separated item IDs
					String[] items = pattern.prefix.trim().split("\\s+");
					for (String itemStr : items) {

						if (itemStr.isEmpty())
							continue;
						Integer itemId = Integer.parseInt(itemStr);
						String mappedItem = itemMap.get(itemId);// show actual transformed columns
						// String mappedItem = String.valueOf(itemId);//show itemCntr or id only
						if (mappedItem != null) {
							transactionLine.append(mappedItem);
						}
					}
					if (cntr <= pids.size() - 1)
						transactionLine.append(", ");
				}
				writerAscii.write(transactionLine.toString().trim());
				writerAscii.newLine();
			}
		} finally {
			if (writerAscii != null)
				writerAscii.close();
		}
	}

	private int comparePatternsUtility(Pattern p1, Pattern p2) {
		if (p1.utility < p2.utility)
			return 1;// descending order of utility values
		else if (p1.utility > p2.utility)
			return -1;
		else
			return 0;
	}

	private int comparePatternsIG(Pattern p1, Pattern p2) {
		if (p1.ig < p2.ig)
			return 1;// descending order of IG values
		else if (p1.ig > p2.ig)
			return -1;
		else
			return comparePatternsUtility(p1, p2);
	}

	private void raisingThresholdCUDOptimize(int k) {
		PriorityQueue<Float> ktopls = new PriorityQueue<Float>();
		float value = 0L;
		for (Entry<Integer, Map<Integer, Item>> entry : mapFMAP.entrySet()) {
			for (Entry<Integer, Item> entry2 : entry.getValue().entrySet()) {
				value = entry2.getValue().utility;
				if (value >= minUtility) {
					if (ktopls.size() < k)
						ktopls.add(value);
					else if (value > ktopls.peek()) {
						ktopls.add(value);
						do {
							ktopls.poll();
						} while (ktopls.size() > k);
					}
				}
			}
		}
		if ((ktopls.size() > k - 1) && (ktopls.peek() > minUtility))
			minUtility = ktopls.peek();
		ktopls.clear();
	}

	private void addToLeafPruneUtils(float value) {
		if (leafPruneUtils.size() < this.topkstatic)
			leafPruneUtils.add(value);
		else if (value > leafPruneUtils.peek()) {
			leafPruneUtils.add(value);
			do {
				leafPruneUtils.poll();
			} while (leafPruneUtils.size() > this.topkstatic);
		}
	}

	private void raisingThresholdLeaf(List<UtilityList> ULs) {
		float value = 0L;
		// LIU-Exact
		for (Entry<Integer, Map<Integer, Float>> entry : mapLeafMAP.entrySet()) {
			for (Entry<Integer, Float> entry2 : entry.getValue().entrySet()) {
				value = entry2.getValue();
				if (value >= minUtility) {
					addToLeafPruneUtils(value);
				}
			}
		}
		// LIU-LB
		for (Entry<Integer, Map<Integer, Float>> entry : mapLeafMAP.entrySet()) {
			for (Entry<Integer, Float> entry2 : entry.getValue().entrySet()) {
				value = entry2.getValue();
				if (value >= minUtility) {

					int end = entry.getKey() + 1;// master contains the end reference 85 (leaf)
					int st = entry2.getKey();// local map contains the start reference 76-85 (76 as parent)
					float value2 = 0L;
					// all entries between st and end processed, there will be go gaps in-between
					// (only leaf with consecutive entries inserted in mapLeafMAP)

					for (int i = st + 1; i < end - 1; i++) {// exclude the first and last e.g. 12345 -> 1345,1245,1235
															// estimates
						value2 = value - ULs.get(i).getUtils();
						if (value2 >= minUtility)
							addToLeafPruneUtils(value2);
						for (int j = i + 1; j < end - 1; j++) {
							value2 = value - ULs.get(i).getUtils() - ULs.get(j).getUtils();
							if (value2 >= minUtility)
								addToLeafPruneUtils(value2);
							for (int k = j + 1; k + 1 < end - 1; k++) {
								value2 = value - ULs.get(i).getUtils() - ULs.get(j).getUtils() - ULs.get(k).getUtils();
								if (value2 >= minUtility)
									addToLeafPruneUtils(value2);
							}
						}
					}
				}
			}
		}
		for (UtilityList u : ULs) {// add all 1 items
			value = u.getUtils();
			if (value >= minUtility)
				addToLeafPruneUtils(value);
		}
		if ((leafPruneUtils.size() > this.topkstatic - 1) && (leafPruneUtils.peek() > minUtility))
			minUtility = leafPruneUtils.peek();
	}

	private void removeEntry() {
		for (Entry<Integer, Map<Integer, Item>> entry : mapFMAP.entrySet()) {
			for (Iterator<Map.Entry<Integer, Item>> it = entry.getValue().entrySet().iterator(); it.hasNext();) {
				Map.Entry<Integer, Item> entry2 = it.next();
				if (entry2.getValue().twu < minUtility) {
					it.remove();
				}
			}
		}
	}

	private void removeLeafEntry() {
		for (Entry<Integer, Map<Integer, Float>> entry : mapLeafMAP.entrySet()) {
			for (Iterator<Map.Entry<Integer, Float>> it = entry.getValue().entrySet().iterator(); it.hasNext();) {
				Map.Entry<Integer, Float> entry2 = it.next();
				it.remove();
			}
		}
	}

	private void save(int[] prefix, int length, UtilityList X) {

		kPatterns.add(new Pattern(prefix, length, X, candidateCount));
		if (kPatterns.size() > this.topkstatic) {
			if (X.getUtils() >= minUtility) {
				do {
					kPatterns.poll();
				} while (kPatterns.size() > this.topkstatic);
			}
			minUtility = kPatterns.peek().utility;
		}
	}

	private void checkMemory() {
		double currentMemory = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024d / 1024d;
		if (currentMemory > maxMemory) {
			maxMemory = currentMemory;
		}
	}

	private void printStats(int foldNo) throws IOException {
		java.text.DecimalFormat df = new java.text.DecimalFormat("#.00");
		java.text.DecimalFormat df4 = new java.text.DecimalFormat("#.0000");
		java.text.DecimalFormat df10 = new java.text.DecimalFormat("#.0000000000");
		System.out.println(
				"=======================================  THUI (Supervised Learning) ALGORITHM - STATS ======================================= ");

		if (inputFile.equals("") || !inputFile.contains(".txt"))
			inputFile = ".txt";
		File f = new File(inputFile);
		String tmp = f.getName();
		tmp = tmp.substring(0, tmp.lastIndexOf('.'));

		if (dsName == null)
			System.out.println(" Dataset: " + tmp);
		else
			System.out.println(" Dataset: " + tmp + " (" + dsName + ")" + " Total time: ~ "
					+ (endTimestamp - startTimestamp) + " ms " + " Memory: ~ " + df.format(maxMemory) + " MB");
		System.out.println(" Data size: " + totalTrans + " Total items: " + totalItems);
		System.out.println(" huiMaxLen: " + this.huiMaxLen + " topK: " + this.topkstatic + " fsK: " + this.fsK
				+ " igThreshold: " + this.igThreshold);
		System.out.println(" High-utility itemsets count: " + huiCount + " candidates: " + candidateCount);
		System.out.println(" Pattern sizes (orig, distinct, fs size): " + PATTERN_SIZE_ORIG + " "
				+ PATTERN_SIZE_DISTINCT + " " + PATTERN_SIZE_FS);
		System.out.println(" Final minimum utility: " + minUtility);
		System.out.println(" Total time: ~ " + (endTimestamp - startTimestamp) + " ms " + " Memory: ~ "
				+ df.format(maxMemory) + " MB");
		System.out.println(
				"=======================================================================================================");
	}

	// test data related
	private List<Set<Integer>> readMinedPatterns(String fileName) {
		List<Set<Integer>> patterns = new ArrayList<Set<Integer>>();
		try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(fileName));
				DataInputStream dis = new DataInputStream(bis)) {
			while (dis.available() > 0) {
				int count = dis.readInt(); // read the count of integers (pattern elements) in this line
				Set<Integer> integerSet = new HashSet<Integer>();
				for (int i = 0; i < count; i++) {
					integerSet.add(dis.readInt());
				}
				patterns.add(integerSet);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return patterns;
	}

	private List<String> getMinedPatterns(String fileName) throws IOException {

		List<String> patterns = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
			br.readLine();

			String line;
			while ((line = br.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty())
					continue;
				String[] parts = line.split(" ", 3);
				if (parts.length >= 2) {
					String pattern = parts[1].replace("'", ""); // remove single quotes
					patterns.add(pattern);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return patterns;
	}

	public void applyPatterns(String input, String output, String dsName, int foldNo, int numRows, int numIntCols,
			int numFloatCols, int numCatCols) throws IOException {

		TransactionGenerator tg = new TransactionGenerator();
		List<List<Integer>> testTransactions = new ArrayList<List<Integer>>();
		tg.prepare_data_apply(input, dsName, foldNo, numRows, testTransactions, numIntCols, numFloatCols, numCatCols);

		// String outputTidSparseFileTest =
		// output+dsName+"_tid_sparse_test_"+String.valueOf(foldNo)+".bin";
		String outputTidSparseFileTest = output + dsName + "_tid_sparse_test.bin";
		String outputTidSparseFileTestTxt = output + dsName + "_tid_sparse_test_ascii.txt";

		// read mined patterns
		// String patternFile = output+dsName+"_util_fs_"+String.valueOf(foldNo)+".txt";
		String patternFile = output + dsName + "_util_fs.bin";
		List<Set<Integer>> minedPatterns = readMinedPatterns(patternFile);

		// test data - transform data based on identified patterns (from training data)
		List<Integer> rowids = new ArrayList<Integer>();
		List<Integer> colids = new ArrayList<Integer>();
		int ti = 0;
		int nnz = 0;
		for (List<Integer> titems : testTransactions) {
			for (int pi = 0; pi < minedPatterns.size(); pi++) {
				Set<Integer> p = minedPatterns.get(pi);
				if (titems.containsAll(p)) { // if pattern p is a subset of transaction
					rowids.add(ti);
					colids.add(pi);
					nnz += 1;
				}
			}
			ti += 1;
		}
		try {
			writerTidSparseTest = new DataOutputStream(
					new BufferedOutputStream(new FileOutputStream(outputTidSparseFileTest)));
			writerTidSparseTest.writeInt(testTransactions.size());
			writerTidSparseTest.writeInt(minedPatterns.size());
			writerTidSparseTest.writeInt(nnz); // Number of non-zero entries
			for (int rid = 0; rid < rowids.size(); rid++) {
				writerTidSparseTest.writeInt(rowids.get(rid));
				writerTidSparseTest.writeInt(colids.get(rid));
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (writerTidSparseTest != null) {
				try {
					writerTidSparseTest.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		String patternFileName = output + dsName + "_util_fs_mapped.txt";
		List<String> minedP = getMinedPatterns(patternFileName);

		// ascii format output file
		try {
			BufferedWriter writerTidSparseTest = new BufferedWriter(new FileWriter(outputTidSparseFileTestTxt));
			Map<Integer, List<String>> transactionMap = new HashMap<>();
			for (int i = 0; i < rowids.size(); i++) {
				int row = rowids.get(i);
				String col = minedP.get(colids.get(i));
				transactionMap.computeIfAbsent(row, k -> new ArrayList<>()).add(col);
			}
			for (Integer row : transactionMap.keySet()) {
				List<String> items = transactionMap.get(row);
				int cntr = 0;
				for (String item : items) {
					cntr += 1;
					writerTidSparseTest.write(item);
					if (cntr <= items.size() - 1)
						writerTidSparseTest.write(", ");
				}
				writerTidSparseTest.newLine();
			}
			writerTidSparseTest.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		if (testTransactions.size() > 0)
			testDensity = (double) (nnz) * 100 / testTransactions.size() / minedPatterns.size();// percentage of
																								// non-zero entries

		System.out.println("Output file " + outputTidSparseFileTest + " generated");
		/*
		 * Enable for storing test density String outputStats = output+dsName+
		 * "_stats_test.txt"; if (foldNo>1) writerStats = new BufferedWriter(new
		 * FileWriter(outputStats, true));//append to file else writerStats = new
		 * BufferedWriter(new FileWriter(outputStats, false));//create new file
		 * 
		 * java.text.DecimalFormat df4 = new java.text.DecimalFormat("#.0000");
		 * StringBuilder bufferStats = new StringBuilder(); if (foldNo==1)
		 * bufferStats.append("testDensity\n");
		 * bufferStats.append(df4.format(testDensity));
		 * writerStats.write(bufferStats.toString()); writerStats.newLine();
		 * writerStats.close();
		 */
	}
}
