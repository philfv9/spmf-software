package ca.pfv.spmf.algorithms.sequentialpatterns.lapin;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import ca.pfv.spmf.datastructures.triangularmatrix.AbstractTriangularMatrix;
import ca.pfv.spmf.datastructures.triangularmatrix.SparseTriangularMatrix;
import ca.pfv.spmf.input.sequence_database_array_integers.SequenceDatabase;
import ca.pfv.spmf.tools.MemoryLogger;

/***
 * This is an implementation of the LAPIN algorithm (a.k.a LAPIN-SPAM or
 * LAPIN-LCI). This implementation tries to be faithful to the original
 * technical report. There is only a minor difference in how the I-Step is
 * performed. When an I-step is performed such thats the resulting last itemset
 * of the prefix would have 3 or more items, position lists are scanned to
 * ensure that only positions where the full itemset appear are considered. In
 * the original LAPIN-SPAM, position-lists are instead updated. But because this
 * would be consume too much memory, we took the design decision of doing it
 * differently.
 * 
 * The LAPIN-SPAM algorithm was originally described in this paper:
 * 
 * Zhenlu Yang and Masaru Kitsuregawa. LAPIN-SPAM: An improved algorithm for
 * mining sequential pattern In Proc. of Int'l Special Workshop on Databases For
 * Next Generation Researchers (SWOD'05) in conjunction with ICDE'05, pp. 8-11,
 * Tokyo, Japan, Apr. 2005.
 *
 * Copyright (c) 2008-2013 Philippe Fournier-Viger
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

public class AlgoLAPIN_LCI {

	/** for statistics **/
	private long totalTime;

	/** Number of patterns found **/
	private int patternCount;

	/** Minimum support threshold **/
	private int minsup = 0;

	/** Buffered writer for output **/
	BufferedWriter writer = null;

	/** Item-is-exist table, one per sequence **/
	Table[] tables = null;

	/** Position lists for SE (Sequence Extension) **/
	SEPositionList[] sePositionList;

	/** Position lists for IE (Item Extension) 2-itemsets **/
	IEPositionList[] iePositionList;

	/** Enable or disable debug mode **/
	final boolean DEBUG = false;

	/** Sequence database used for debugging **/
	SequenceDatabase seqDB = null;

	/** Matrix to count support for 2-itemsets **/
	private AbstractTriangularMatrix matrixPairCount;

	/** Path to the input file **/
	String input = null;

	/**
	 * Default constructor
	 */
	public AlgoLAPIN_LCI() {

	}

	/**
	 * Runs the sequence mining algorithm with the given parameters.
	 *
	 * @param input          the path to the input file
	 * @param outputFilePath the path to the output file
	 * @param minsupRel      the minimum support threshold as a percentage
	 * @throws IOException if an error occurs while writing to the file
	 */
	public void runAlgorithm(String input, String outputFilePath, double minsupRel) throws IOException {
		// Set input file path
		this.input = input;

		// Initialize the file writer for output
		writer = new BufferedWriter(new FileWriter(outputFilePath));

		// Reset pattern count
		patternCount = 0;

		// Reset memory usage logger
		MemoryLogger.getInstance().reset();

		// Record the start time
		long startTime = System.currentTimeMillis();

		// Run the core algorithm
		lapin(input, minsupRel);

		// Record the total execution time
		totalTime = System.currentTimeMillis() - startTime;

		// Close the output writer
		writer.close();
	}

	/**
	 * Run the LAPIN algorithm
	 * 
	 * @param input     the input file path
	 * @param minsupRel the minsup threshold as a percentage
	 */
	private void lapin(String input, double minsupRel) throws IOException {

		if (DEBUG) {
			System.out.println("=== First database scan to count number of sequences and support of single items ===");
		}

		// FIRST DATABASE SCAN:
		// This scan is to count the number of sequences, support of each item, and the
		// largest item ID.

		// Number of sequences
		int sequenceCount = 0;

		// Largest item ID
		int largestItemID = 0;

		// This map will store for each item (key) the first position where the item
		// appears in each sequence containing the item (value)
		Map<Integer, List<Position>> mapItemFirstOccurrences = new HashMap<Integer, List<Position>>();
		try {
			// Read the input file
			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))));
			String thisLine;
			// for each sequence of the input fiel
			while ((thisLine = reader.readLine()) != null) {

				if (thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%' || thisLine.charAt(0) == '@') {
					continue;
				}

				// we use a set to remember which item have been seen already
				Set<Integer> itemsAlreadySeen = new HashSet<Integer>();
				// to know the itemset number
				short itemsetID = 0;
				// for each token in this line
				for (String integer : thisLine.split(" ")) {
					// if it is the end of an itemset
					if ("-1".equals(integer)) {
						itemsetID++;
					} else if ("-2".equals(integer)) {
						// Increase the count of sequences from the input file
						sequenceCount++;
					} else {
						// otherwise, it is an item
						Integer item = Integer.valueOf(integer);
						// if this item was not seen already in that sequence
						if (itemsAlreadySeen.contains(item) == false) {
							// Get the list of positions of that item
							List<Position> list = mapItemFirstOccurrences.get(item);
							// if that list is null, create a new list
							if (list == null) {
								list = new ArrayList<Position>();
								mapItemFirstOccurrences.put(item, list);
							}
							// Add the position of the item in that sequence to the list of first positions
							// of that item
							Position position = new Position(sequenceCount, itemsetID);
							list.add(position);
							// Remember that we have seen this item
							itemsAlreadySeen.add(item);
							// Check if the item is the largest item until now
							if (item > largestItemID) {
								largestItemID = item;
							}
						}
					}
				}
			}
			reader.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		// Initialize the list of tables
		tables = new Table[sequenceCount];

		// Calculate absolute minimum support as a number of sequences
		minsup = (int) Math.ceil(minsupRel * sequenceCount);
		if (minsup == 0) {
			minsup = 1;
		}

		if (DEBUG) {
			System.out.println("Number of items: " + mapItemFirstOccurrences.size());
			System.out.println("Sequence count:  " + sequenceCount);
			System.out.println("Abs. minsup: " + minsup + " sequences");
			System.out.println("Rel. minsup: " + minsupRel + " %");
			System.out.println("=== Determining the frequent items ===");
		}

//		// For each frequent item,  save it and add it to the list of frequent items
		List<Integer> frequentItems = new ArrayList<Integer>();
		for (Entry<Integer, List<Position>> entry : mapItemFirstOccurrences.entrySet()) {
			// Get the support of this item
			List<Position> itemSupport = entry.getValue();
			// if the item is frequent
			if (itemSupport.size() >= minsup) {
				// Output the item and add it to the list of frequent items
				Integer item = entry.getKey();
				savePattern(item, itemSupport.size());
				frequentItems.add(item);
				if (DEBUG) {
					System.out.println(" Item " + item + " is frequent with support = " + itemSupport.size());
					System.out.println(" It appears at positions:");
					for (Position pos : itemSupport) {
						System.out.println("   " + pos);
					}
				}
			}
		}

		if (DEBUG) {
			System.out.println("=== Sort the list of frequent items===");
			System.out.println(frequentItems);
		}

		// sort the frequent items (useful when generating 2-IE-sequences, later on).
		Collections.sort(frequentItems);

		if (DEBUG) {
			System.out.println("=== Second database scan to construct item-is-exist tables ===");
		}

		// SECOND DATABASE SCAN:
		// Now we will read the database again to create the Item-is-exist-table
		// and SE-position-lists and count support of 2-IE-sequences
		matrixPairCount = new SparseTriangularMatrix(largestItemID + 1);

		// Initialise the IE position lists and SE position lists
		sePositionList = new SEPositionList[sequenceCount];
		iePositionList = new IEPositionList[sequenceCount];

		try {
			// Prepare to read the file
			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))));
			String thisLine;
			// For each sequence in the file
			int currentSequenceID = 0;
			while ((thisLine = reader.readLine()) != null) {
				if (thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%' || thisLine.charAt(0) == '@') {
					continue;
				}

				// (1) ------- PARSE THE SEQUENCE BACKWARD TO CREATE THE ITEM-IS-EXIST TABLE FOR
				// THATS SEQUENCE
				// AND COUNT THE SUPPORT OF 2-IE-Sequences

				// We will also use a structure to remember in which sequence we have seen each
				// pair of items
				// Note that in this structure, we will add +1 to the sid because by default the
				// matrix is filled with 0
				// and we don't want to think that the first sequence was already seen for all
				// pairs.
				AbstractTriangularMatrix matrixPairLastSeenInSID = new SparseTriangularMatrix(largestItemID + 1);

				// We count the number of positions (number of itemsets).
				// To do that we count the number of "-" symbols in the file.
				// We need to subtract 1 because the end of line "-2" contains "-".
				int positionCount = -1;
				for (char caracter : thisLine.toCharArray()) {
					if (caracter == '-') {
						positionCount++;
					}
				}

				// Now we will scan the sequence again.
				// This time we will remember which item were seen already
				Set<Integer> itemsAlreadySeen = new HashSet<Integer>();

				// During this scan, we will create the table for this sequence
				Table table = new Table();

				// To do that, we first create an initial position vector for that table
				BitSet currentBitset = new BitSet(mapItemFirstOccurrences.size()); // OK ?

				// This variable will be used to remember if a new item appeared in the current
				// itemset
				boolean seenNewItem = false;

				// We will scan the sequence backward, starting from the end because
				// we should not create a bit vector for all positions but for only
				// the positions that are different from the previous one.
				String[] tokens = thisLine.split(" ");
				// This is the number of itemsets
				int currentPosition = positionCount;
				// to keep the current itemset in memory
				List<Integer> currentItemset = new ArrayList<Integer>();
				// For each token in that sequence
				for (int i = tokens.length - 1; i >= 0; i--) {
					// get the token
					String token = tokens[i];

					// if we reached the end of an itemset
					if ("-1".equals(token)) {
						// update the triangular matrix for counting 2-IE-sequences
						// by comparing each pairs of items in the current itemset
						for (int k = 0; k < currentItemset.size(); k++) {
							Integer item1 = currentItemset.get(k);
							for (int m = k + 1; m < currentItemset.size(); m++) {
								Integer item2 = currentItemset.get(m);

								// if that pair is frequent
								int sid = matrixPairLastSeenInSID.getSupportForItems(item1, item2);
								// and if we have not seen this sequence yet
								if (sid != currentSequenceID + 1) {
									// increment support count of this pair
									matrixPairCount.incrementCount(item1, item2);
									// remember that we have seen this pair so that we don't count it again
									matrixPairLastSeenInSID.setSupport(item1, item2, currentSequenceID + 1);
								}
							}
						}
						currentItemset.clear();
						// Decrease the current index of the position (itemset) in the sequence
						currentPosition--;
						// if the bit vector has changed since previous position, then
						// we need to add a new bit vector to the table
						if (seenNewItem) {
							// create the position vector and add it to the item-is-exist table
							PositionVector vector = new PositionVector(currentPosition, (BitSet) currentBitset.clone());
							table.add(vector);
						}

					} else if ("-2".equals(token)) { // if end of sequence, nothing to do

					} else {
						// otherwise, it is an item
						Integer item = Integer.valueOf(token);
						if (mapItemFirstOccurrences.get(item).size() >= minsup) { // only for frequent items
							// if first time that we see this item
							if (itemsAlreadySeen.contains(item) == false) {
								// remember that we have seen a new item
								seenNewItem = true;
								// remember that we have seen this item
								itemsAlreadySeen.add(item);
								// add this item to the current bit vector
								currentBitset.set(item);
							}
							// add this item to the current itemset
							currentItemset.add(item);
						}
					}
				}

				// Lastly,
				// update the triangular matrix for counting 2-IE-sequences one more time
				// for the case where the pair is in first position of the sequence
				// by considering each pair of items in the last itemset.
				// This is done like it was done above, so I will not comment this part of the
				// code again.
				for (int k = 0; k < currentItemset.size(); k++) {
					Integer item1 = currentItemset.get(k);
					for (int m = k + 1; m < currentItemset.size(); m++) {
						Integer item2 = currentItemset.get(m);
						// if th
						int sid = matrixPairLastSeenInSID.getSupportForItems(item1, item2);
						if (sid != currentSequenceID + 1) {
							matrixPairCount.incrementCount(item1, item2);
							matrixPairLastSeenInSID.setSupport(item1, item2, currentSequenceID + 1);
						}
					}
				}

				// If a new item was seen
				// Add an extra row to the item-is-exist table that will be called -1 with all
				// items in this sequence
				if (seenNewItem) {
					PositionVector vector = new PositionVector(-1, (BitSet) currentBitset.clone());
					table.add(vector);
				}
//				
//				
//				// Initialize the IE lists and SE lists for that sequence
				// which will be filled with the next database scan.
				sePositionList[currentSequenceID] = new SEPositionList(itemsAlreadySeen);
				iePositionList[currentSequenceID] = new IEPositionList();

				if (DEBUG) {
					System.out.println("Table for sequence " + currentSequenceID + " : " + thisLine);
					System.out.println(table.toString());
				}
				// put the current table in the array of item-is-exist-tables
				tables[currentSequenceID] = table;
				// we will process the next sequence id
				currentSequenceID++;
			}
			reader.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		// THIRD SCAN TO
		// PARSE THE SEQUENCE FORWARD TO CREATE THE SE-POSITION LIST OF THAT SEQUENCE
		// AND IEPositionList for frequent 2-IE-SEQUENCES
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))));
			String thisLine;
			// For each sequence
			int currentSequenceID = 0;
			while ((thisLine = reader.readLine()) != null) {
				if (thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%' || thisLine.charAt(0) == '@') {
					continue;
				}

				// We will scan the sequence backward, starting from the end.
				String[] tokens = thisLine.split(" ");
				// to keep the current itemset in memory
				List<Integer> currentItemset = new ArrayList<Integer>();

				// this variable will be used to remember which itemset we are visiting
				short itemsetID = 0;
				// empty the object to track the current itemset (if it was used for the
				// previous sequence)
				currentItemset.clear();

				// for each token of the current sequence
				for (int i = 0; i < tokens.length; i++) {
					String token = tokens[i];

					// if we reached the end of an itemset
					if ("-1".equals(token)) {
						// if the current itemset contains more than one item
						if (currentItemset.size() > 1) {
							// update the position list for 2-IE-sequences
							for (int k = 0; k < currentItemset.size(); k++) {
								Integer item1 = currentItemset.get(k);
								for (int m = k + 1; m < currentItemset.size(); m++) {
									Integer item2 = currentItemset.get(m);
									// if the pair is frequent
									int support = matrixPairCount.getSupportForItems(item1, item2);
									if (support >= minsup) {
										iePositionList[currentSequenceID].register(item1, item2, itemsetID);
									}
								}
							}
						}
						// increase itemsetID
						itemsetID++;
						// clear itemset
						currentItemset.clear();
					} else if ("-2".equals(token)) {
						// if the end of a sequence, nothing special to do

					} else {
						// otherwise, the current token is an item
						Integer item = Integer.valueOf(token);
						// if the item is frequent
						if (mapItemFirstOccurrences.get(item).size() >= minsup) {
							// we add the current position to the item SE-position list
							sePositionList[currentSequenceID].register(item, itemsetID);
							// we add the item to the current itemset
							currentItemset.add(item);
						}
					}
				}

				iePositionList[currentSequenceID].sort(); // sort the IE-position list

				if (DEBUG) {
					System.out.println("SE Position list for sequence " + currentSequenceID);
					System.out.println(sePositionList[currentSequenceID]);
					System.out.println("IE Position list for sequence " + currentSequenceID);
					System.out.println(iePositionList[currentSequenceID]);
				}

				// update the sequence id for the next sequence
				currentSequenceID++;
			}
			reader.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (DEBUG) {
			System.out.println("=== Starting sequential pattern generation ===");
		}

		patternGeneration(mapItemFirstOccurrences, frequentItems);

		// Record the maximum memory usage
		MemoryLogger.getInstance().checkMemory();
		writer.close();
	}

	/**
	 * Generates sequential patterns using a prefix-based approach from the list of
	 * frequent items and their respective first occurrence positions (borders) in
	 * the sequence database.
	 * <p>
	 * This method performs pattern generation in two main steps:
	 * <ul>
	 * <li>For each frequent item, it starts with a prefix containing only that item
	 * and recursively explores longer patterns using sequence (S-) extensions.</li>
	 * <li>For each pair of frequent items where the second is lexicographically
	 * greater than the first, it explores patterns where both items appear in the
	 * same itemset (I-extension), and continues recursively from those 2-IE
	 * patterns.</li>
	 * </ul>
	 * 
	 * @param mapItemFirstOccurrences A map where each key is a frequent item and
	 *                                the corresponding value is a list of
	 *                                {@link Position} objects representing the
	 *                                positions of the first occurrence of the item
	 *                                in each sequence where it appears.
	 * @param frequentItems           A list of all frequent items (meeting the
	 *                                minimum support threshold), sorted in
	 *                                ascending order.
	 * @throws IOException If an error occurs while saving the patterns.
	 */
	private void patternGeneration(Map<Integer, List<Position>> mapItemFirstOccurrences, List<Integer> frequentItems)
			throws IOException {
		// For each frequent item, call the recursive method to explore larger patterns
		for (int i = 0; i < frequentItems.size(); i++) {

			// Get the item
			int item1 = frequentItems.get(i);

			// Get the border for that item
			List<Position> item1Border = mapItemFirstOccurrences.get(item1);
			if (DEBUG) {
				System.out.println("=== Considering item " + item1 + "\n  Border:");
				for (Position pos : item1Border) {
					System.out.println("    seq: " + pos.sid + "    itemset: " + pos.position);
				}
			}
			// if the border contains at least minsup sequence (if the item is frequent)
			if (item1Border.size() >= minsup) {
				
			    // Create prefix with the item
			    Prefix prefix = new Prefix(item1);
			    
			    // Recursively find s-extensions (TRUE so that I-extensions are disabled since 2-IE are handled separately)
			    genPatterns(prefix, item1Border, frequentItems, frequentItems, item1, true);
			}

			// For each frequent 2-IE sequences stating with item1, we will explore 2-IE
			// sequences by considering each frequent item larger than item1
			for (int k = i + 1; k < frequentItems.size(); k++) {
				// We consider item2
				int item2 = frequentItems.get(k);
				// Get the support of item1, item2
				int support = matrixPairCount.getSupportForItems(item1, item2);

				// if the pair {item1, item2} is frequent
				if (support >= minsup) {
					
					// get the list of position of item2
					List<Position> item2Border = mapItemFirstOccurrences.get(item2);
					
					// Create the border by using the 2-IE position list
					List<Position> ie12Border = new ArrayList<Position>();

					// We will loop over the border of item1 or item2 (the smallest one)
					List<Position> borderToUse = (item2Border.size() < item1Border.size()) ? item2Border : item1Border;
					
					// For each sequence of the border that we consider
					for (Position sequenceToUse : borderToUse) {
						
						// Get the sequence id
						int sid = sequenceToUse.sid;
						
						// For this sequence, we will get the position list of each item
						List<Short> listPosition1 = sePositionList[sid].getListForItem(item1);
						List<Short> listPosition2 = sePositionList[sid].getListForItem(item2);
						
						// if one of them is null, that means that both item1 and item2 do not appear in
						// that sequence
						// so we continue to the next sequence
						if (listPosition1 == null || listPosition2 == null) {
							continue;
						}
						// otherwise
						// find the first common position of item1 and item2 in the sequence
						int index1 = 0;
						int index2 = 0;

						// we do that by the following while loop
						while (index1 < listPosition1.size() && index2 < listPosition2.size()) {
							short position1 = listPosition1.get(index1);
							short position2 = listPosition2.get(index2);
							if (position1 < position2) {
								index1++;
							} else if (position1 > position2) {
								index2++;
							} else {
								// we have found the position, so we add it to the new border and
								// then stop because we do not want to add more than one position for
								// the same sequence in the new border
								ie12Border.add(new Position(sid, position1));
								break;
							}
						}
					}
					if (DEBUG) {
						System.out.println("=== Considering the 2-IE sequence {" + item1 + "," + item2
								+ "}  with support " + support);
						System.out.println("  Border of {" + item1 + "," + item2 + "}");
						for (Position pos : ie12Border) {
							System.out.println("    seq: " + pos.sid + "    itemset: " + pos.position);
						}
					}

					// finally, we create the prefix for the pattern {item1, item2}
					Prefix prefix = new Prefix(item1, item2);
					// save the pattern
					savePattern(prefix, support);
					// perform recursive call to extend that pattern
					genPatterns(prefix, ie12Border, frequentItems, frequentItems, item2, false); // false, to allow
																									// I-extension
				}
			}
		}
	}

	/**
	 * The main recursive method of LAPIN
	 * 
	 * @param prefix                     the current prefix
	 * @param prefix                     the prefix
	 * @param prefixBorder               a list of position that is the prefix
	 *                                   border
	 * @param in                         items that could be appended by i-extension
	 * @param sn                         items that could be appended by s-extension
	 * @param hasToBeGreaterThanForIStep
	 * @throws IOException if error while writing to file
	 */

	private void genPatterns(Prefix prefix, List<Position> prefixBorder, List<Integer> sn, List<Integer> in,
			int hasToBeGreaterThanForIStep, boolean doNotPerformIExtensions) throws IOException {
		
////		%%%%%%%%%%%
//		System.out.println("##### PREFIX "  + prefix);
//		if(prefix.size() == 2 && prefix.getItemsets().get(0).size() == 1 && prefix.getItemsets().get(0).get(0) == 2) {
//			System.out.println("Test");
//		}
//		System.out.println();
////		%%%%%%%%%%%

		// ====== S-STEPS ======
//			// Temporary variables (as described in the paper)
		List<Integer> sTemp = new ArrayList<Integer>();
		List<Integer> sTempSupport = new ArrayList<Integer>();
//			
//			// for each item in sn
		for (Integer item : sn) {
			// perform the S-STEP
			int support = calculateSupportSStep(item, prefixBorder);
			// if the support is higher than minsup
			if (support >= minsup) {
//					// record that item and pattern in temporary variables
				sTemp.add(item);
				sTempSupport.add(support);
			}
		}
		// for each pattern recorded for the s-step
		for (int k = 0; k < sTemp.size(); k++) {
			int item = sTemp.get(k);
			// create the new prefix
			Prefix prefixSStep = prefix.cloneSequence();
			List<Integer> itemset = new ArrayList<Integer>(1);
			itemset.add(item);
			prefixSStep.itemsets.add(itemset);

			// save the pattern to the file
			savePattern(prefixSStep, sTempSupport.get(k));

			// recursively try to extend that pattern
			List<Position> newBorder = recalculateBorderForSExtension(prefixBorder, item);

			// Recursive call
			genPatterns(prefixSStep, newBorder, sTemp, sTemp, item, false);
		}

		if (doNotPerformIExtensions) {
			return;
		}

		// ======== I STEPS =======
		// Temporary variables
		List<Integer> iTemp = new ArrayList<Integer>();
		List<List<Position>> iTempBorder = new ArrayList<List<Position>>();
//			
//			// for each item in in
		// the item has to be greater than the largest item
		// already in the last itemset of prefix.
		int index = Collections.binarySearch(in, hasToBeGreaterThanForIStep);
		for (int i = index; i < in.size(); i++) {
			Integer item = in.get(i);

			List<Integer> lastItemset = prefix.itemsets.get(prefix.itemsets.size() - 1);
//				Integer lastItem = lastItemset.get(lastItemset.size()-1);
			boolean willAddSecondItem = lastItemset.size() == 1;

			// AN OPTIMIZATION

			// perform the I-STEP
			int support = estimateSupportIStep(item, prefixBorder);

			// if the estimated support is higher than minsup
			if (support >= minsup) {

				// recalculate the border
				// in this case, the method takes the prefix border as input
				List<Position> newBorder = recalculateBorderForIExtension(lastItemset, prefixBorder,
						hasToBeGreaterThanForIStep, item, willAddSecondItem);

				// record that item and pattern in temporary variables
				if (newBorder.size() >= minsup) {
					iTemp.add(item);
					iTempBorder.add(newBorder);
				}
			}
		}

		// for each pattern recorded for the i-step
		for (int k = 0; k < iTemp.size(); k++) {
			int item = iTemp.get(k);
			// create the new prefix
			Prefix prefixIStep = prefix.cloneSequence();
			prefixIStep.itemsets.get(prefixIStep.size() - 1).add(item);

			// save the pattern
			List<Position> newBorder = iTempBorder.get(k);
			savePattern(prefixIStep, newBorder.size());
			// recursively try to extend that pattern
			genPatterns(prefixIStep, newBorder, sTemp, iTemp, item, false);
		}

		// check the memory usage
		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * Recalculate the prefix border following an i-extension
	 * 
	 * @param prefixLastItemset last itemset of the previous prefix
	 * @param prefixBorder      the previous prefix border
	 * @param item1             the last item
	 * @param item2             the item that will be appended
	 * @param willAddSecondItem if the item will be added to an itemset containing a
	 *                          single item
	 * @return the updated border
	 */
	private List<Position> recalculateBorderForIExtension(List<Integer> prefixLastItemset, List<Position> prefixBorder,
			int item1, int item2, boolean willAddSecondItem) {

		// Create the new border (a list of position)
		List<Position> newBorder = new ArrayList<Position>();

		// for each sequence where the prefix appeared
		for (Position previousPosition : prefixBorder) {
			int sid = previousPosition.sid;
			// get where the last two items of the prefix appeared
			int previousItemsetID = previousPosition.position;
			IEPositionList positionLists = iePositionList[sid];

			// find the position that is immediately larger or equal than the current one
			// by checking each position in the list of positions for the pair
			List<Short> listPositions = positionLists.getListForPair(item1, item2);
			if (listPositions != null) {
				// for each position
				loop: for (short pos : listPositions) {
					// if the position is larger or equal to the current one
					if (pos >= previousItemsetID) {
						// IMPORTANT:
						// if the prefix has two items in its last itemset,
						// then we also need to check that the full last itemset of prefix is at the
						// current position
						// This will not be done very optimally but it is it difficult to do a better
						// solution.
						if (willAddSecondItem == false) {
							// We take the SE position list of the current sequence
							SEPositionList plists = sePositionList[sid];
							// For each item of the last itemset of the prefix
							for (int i = 0; i < prefixLastItemset.size() - 1; i++) {
								// We check if that item appears at that position
								Integer itemX = prefixLastItemset.get(i);
								List<Short> plistX = plists.getListForItem(itemX);
								int index = Collections.binarySearch(plistX, pos);
								// if not, then we stop considering this position
								if (index < 0) {
									continue loop;
								}
							}
							// If the loop has finished, that means that all items from the last itemset of
							// the prefix have appeared at the position pos
						}
						// Then we add the position to the new border
						Position newPosition = new Position(sid, pos);
						newBorder.add(newPosition);
						// After that we will continue to the next sequence to continue creating the new
						// border
						break;
					}
				}
			}
		}

		return newBorder;

	}

	/**
	 * Estimate support of appending an item to the current prefix by I-extension
	 * 
	 * @param item       the item
	 * @param itemBorder the prefix border
	 * @return the estimated support (an upper bound)
	 */
	private int estimateSupportIStep(Integer item, List<Position> itemBorder) {
		// First we need to take the two last items
		int support = 0;

		// Iterate through each position in the item border
		for (Position pos : itemBorder) {
			Table table = tables[pos.sid];

			// Get the number of position vectors in the table for the sequence
			int numberOfVectors = table.positionVectors.size();

			// Scan the position vectors from the last to the first (they are ordered
			// backward in the table)
			for (int j = 0; j < numberOfVectors; j++) {
				PositionVector vector = table.positionVectors.get(j);

				// If the position is less than the current position in the border
				if (vector.position < pos.position) {

					// Check if the item is present in the current position vector's bitset
					if (vector.bitset.get(item)) {
						support++; // Increment support if item is found
					}

					break; // Stop after the first matching position
				}
			}
		}
		return support;
	}

	/**
	 * Calculate the support of the new prefix resulting from appending an item to
	 * the prefix by S-extension
	 * 
	 * @param item       the item
	 * @param itemBorder the prefix border
	 * @return the support
	 */
	private int calculateSupportSStep(Integer item, List<Position> itemBorder) {
		// Initialize a variable to count the support
		int support = 0;
		// For each sequence where there is a position in the border
		for (Position pos : itemBorder) {
			// get the Item-is-exist table corresponding to the sequence
			Table table = tables[pos.sid];

			// Get the number of vectors in that table
			int numberOfVectors = table.positionVectors.size();

			// We will scan the vectors to determine if the item appears after the
			// corresponding
			// position. If yes, we will increase the support by 1.
			// IMPORTANT: We scan the table starting from the last vectors because
			// vectors have been inserted in reverse order in the table.
			// Also note that we will skip the first vector that has the position -1 because
			// this vector was added for i-extension only and should not be considered for
			// s-extension

			// Thus, for each vector starting from the second-last one
			for (int j = numberOfVectors - 2; j >= 0; j--) {
				// Get the vector
				PositionVector vector = table.positionVectors.get(j);

				// if the position of this vector is larger or equal to the position that
				// we are searching for
				if (vector.position >= pos.position) {
					// check if the bit corresponding to the item is set to 1
					if (vector.bitset.get(item)) {
						// if yes, we increase the support by 1
						support += 1;
					}
					// and we don't need to continue looking at the vectors for that sequence
					break;
				}
			}
		}
		// return the calculated support
		return support;
	}

	/**
	 * Method to recalculate the border of a prefix after an S-extension with an
	 * item
	 * 
	 * @param prefixBorder the border of the prefix
	 * @param item         the item used to extend the prefix
	 * @return the updated list of positions representing the new border
	 */
	private List<Position> recalculateBorderForSExtension(List<Position> prefixBorder, int item) {

		// Initialize the list to store the new border positions
		List<Position> newBorder = new ArrayList<Position>();

		// Iterate over each position in the current prefix border
		for (Position previousPosition : prefixBorder) {
			// get the sequence id
			int sid = previousPosition.sid;

			// get the index of the itemset where the last item of the prefix appeared
			int previousItemsetID = previousPosition.position;

			// Get the SE position list for the sequence
			SEPositionList positionLists = sePositionList[sid];

			// Get the list of positions for the item in the sequence
			List<Short> listPositions = positionLists.getListForItem(item);

			// If the item appears in the sequence
			if (listPositions != null) {
				// Check if there's any position greater than the last itemset in the prefix
				for (short pos : listPositions) {
					if (pos > previousItemsetID) {
						// add the position to the new border
						newBorder.add(new Position(sid, pos));
						// and stop because we don't want to add more than one position for the same
						// sequence.
						break;
					}
				}
			}
		}

		// Return the newly calculated border
		return newBorder;
	}

	/**
	 * Save a pattern that has been found
	 * 
	 * @param item    the item
	 * @param support the support of the item
	 * @throws IOException if error while writing to file
	 */
	private void savePattern(Integer item, int support) throws IOException {
		// Increment the number of patterns found
		patternCount++;

		// Build the pattern string
		StringBuilder patternBuilder = new StringBuilder();
		patternBuilder.append(item).append(" -1 #SUP: ").append(support);

		// Write the pattern to the output file
		writer.write(patternBuilder.toString());
		writer.newLine();

		// Optionally print for debugging
		if (DEBUG) {
			System.out.println(patternBuilder);
		}
	}

	/**
	 * Save a prefix (pattern) to file
	 * 
	 * @param prefix  the prefix
	 * @param support the prefix support
	 * @throws IOException if error ocurrs when writing to file
	 */
	private void savePattern(Prefix prefix, int support) throws IOException {
		// increase the number of patterns found
		patternCount++;

		// Create a string buffer to store the pattern and its support as a string
		StringBuilder patternBuilder = new StringBuilder("");

		// for each itemset
		for (List<Integer> itemset : prefix.itemsets) {
			// for each item
			for (Integer item : itemset) {
				patternBuilder.append(item).append(' ');
			}
			// at the end of an itemset we put a -1
			patternBuilder.append("-1 ");
		}

		// then, append the support of the pattern:
		patternBuilder.append("#SUP: ").append(support);

		// write the buffer to the output file
		writer.write(patternBuilder.toString());
		writer.newLine();

		if (DEBUG) {
			System.out.println("OUTPUT PATTERN: " + patternBuilder.toString());
		}
	}

	/**
	 * Print statistics about the algorithm execution time
	 */
	public void printStatistics() {
		String lineSep = System.lineSeparator();
		StringBuilder stats = new StringBuilder(200);
		stats.append("=============  LAPIN - STATISTICS  =============").append(lineSep)
				.append("Total time (ms)             : ").append(totalTime).append(lineSep)
				.append("Frequent sequences count    : ").append(patternCount).append(lineSep)
				.append("Max memory usage (MB)       : ").append(MemoryLogger.getInstance().getMaxMemory())
				.append(lineSep).append("Minimum support (absolute)  : ").append(minsup).append(lineSep)
				.append("=================================================").append(lineSep);

		System.out.print(stats);
	}

}
