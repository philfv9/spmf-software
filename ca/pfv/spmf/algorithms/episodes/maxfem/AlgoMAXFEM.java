package ca.pfv.spmf.algorithms.episodes.maxfem;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import ca.pfv.spmf.algorithms.ArraysAlgos;
import ca.pfv.spmf.tools.MemoryLogger;

/*
 * This file is part of the SPMF DATA MINING SOFTWARE *
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify it under the *
 * terms of the GNU General Public License as published by the Free Software *
 * Foundation, either version 3 of the License, or (at your option) any later *
 * version. SPMF is distributed in the hope that it will be useful, but WITHOUT
 * ANY * WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Copyright Peng Yang, Philippe Fournier-Viger, 2019
 */

/**
 * This is a implementation of the MAXFEM algorithm for maximal episode mining
 * 
 * @author Philippe Fournier-Viger, Peng yang et al.
 */

public class AlgoMAXFEM {
	/** start time of the latest execution */
	private long startTimestamp;

	/** end time of the latest execution */
	private long endTimestamp;

	/** the number of candidates */
	private int candidateCount;

	/**
	 * whether the timestamps need self increment as step of 1 for each transaction
	 */
	private boolean selfIncrement;

	/**
	 * a sequence database to store all events int[] : 0-> item, 1-> tid
	 */
	private List<int[]> indexDB;

	/** the frequent itemsets */
	private List<Itemset> frequentItemsets = null;

	/** the Encoding table */
	private EncodingTable encodingTable = null;

	/** the minimum support threshold */
	private int minSupport;

	/** the maximum window threshold */
	private int maxWindow;
	
	/** fimaJoin count */
	private int fimaJointCount;

	// ============ IN MEMORY DATABASE =====================/

	/** an in memory database with timestamps*/
	private List<TimeEventSet> inMemoryDatabaseTime = new ArrayList<TimeEventSet>();
	/** an in memory database without timestamps */
	private List<EventSet> inMemoryDatabaseNoTime = new ArrayList<EventSet>();

	/** a set of events with a timestamp */
	public class TimeEventSet extends EventSet {
		int timestamp;
	}
	/** a set of events without a timestamp */
	public class EventSet {
		int[] items;
	}

	// =================== NEW FOR MAXIMAL EPISODES

	/** STRUCTURE TO STORE MAXIMAL PATTERNS BY ASCENDING ORDER OF SUPPORT */
	private List<TreeSet<EpisodeMaxEPM>> maxPatterns = null;

	/** the number of maximal patterns */
	private int maxPatternCount;

	/** boolean value to activate the debug mode */
	private final boolean DEBUG_MODE = false;
	
	private final boolean ACTIVATE_TEMPORAL_PRUNING = true;

	/**
	 * Construct
	 */
	public AlgoMAXFEM() {
		// empty
	}

	/**
	 * Method to run the algorithm
	 * 
	 * @param input         a sequence
	 * @param output        the path of the output file to save the result or null
	 *                      if you want the result to be saved into memory
	 * @param minSupport    the minimum support threshold
	 * @param maxWindow     the maximum window size
	 * @param selfIncrement if true consecutive timestamps will be assigned to
	 *                      transactions. otherwise, timestamps from the input file
	 *                      will be used.
	 * @return the frequent episodes
	 * @throws IOException if error while reading or writing to file
	 */
	public void runAlgorithm(String input, String output, int minSupport, int maxWindow, boolean selfIncrement)
			throws IOException {
		// reset maximum
		MemoryLogger.getInstance().reset();

		this.minSupport = minSupport;
		this.maxWindow = maxWindow;
		this.selfIncrement = selfIncrement;

		this.maxPatternCount = 0;
		this.fimaJointCount = 0;

		startTimestamp = System.currentTimeMillis();

		// Initialize a list to store the frequent itemsets
		frequentItemsets = new ArrayList<>();

		// Scan the file to sequence to find the frequent 1-items
		Set<Integer> frequentItemsName = scanDatabaseToDetermineFrequentItems(input);

		// Transform the TDB into the indexDB and maintain the locations of all frequent
		// 1-items in the index database
		scanDatabaseAgainToDetermineIndexDB(input, frequentItemsName);
		
		// ========= NEW
		// Create an array to store the maximal patterns by size
		maxPatterns = new ArrayList<TreeSet<EpisodeMaxEPM>>(15);
		maxPatterns.add(null);
		maxPatterns.add(new TreeSet<EpisodeMaxEPM>());
		// ========= END NEW
		
		inMemoryDatabaseNoTime = null;
		inMemoryDatabaseTime = null;  

		// obatin all frequent itemsets without candidates
		for (int i = 0; i < frequentItemsName.size(); i++) {
			Itemset itemset = frequentItemsets.get(i);
			fimajoin(itemset, 1, frequentItemsets);
		}
		frequentItemsName = null;

		// Encode the database construction
		this.encodingTable = new EncodingTable();

		for (Itemset itemset : frequentItemsets) {
			List<int[]> events = new ArrayList<>(1);
			events.add(itemset.getName());
			EpisodeMaxEPM episode = new EpisodeMaxEPM(events, itemset.getSupport());
			candidateCount++;

			List<int[]> boundlist = new ArrayList<>();
			for (int location : itemset.getLocationList()) {
				int[] bound = new int[] {indexDB.get(location)[1], indexDB.get(location)[1] };
				boundlist.add(bound);
			}
			encodingTable.addEpisodeAndBoundlist(episode, boundlist);

			// SAVE THE EPISODE !
			saveEpisode(episode);
		}

		frequentItemsets = null;
		this.indexDB = null;

		for (int i = 0; i < encodingTable.getTableLength(); i++) {
			// only do s-Extension
			serialJoins(encodingTable.getEpisodebyID(i), encodingTable.getBoundlistByID(i));
		}

		this.encodingTable = null;

		// record end time
		endTimestamp = System.currentTimeMillis();

		// check the memory usage
		MemoryLogger.getInstance().checkMemory();

		// save results to file
		saveResultsToFile(output);
	}

	/**
	 * Save the maximal episodes to a file
	 * @param output the output file path
	 * @throws IOException if error while writing to file
	 */
	private void saveResultsToFile(String output) throws IOException {
		if (DEBUG_MODE) {
			System.out.println("====== RESULTS =======");
		}
		// Create a string buffer
		BufferedWriter writer = new BufferedWriter(new FileWriter(output));
		StringBuilder r = new StringBuilder("");
		// for each level (pattern having a same size)
		for (TreeSet<EpisodeMaxEPM> tree : maxPatterns) {
			if (tree == null) {
				continue;
			}
			// for each pattern
			for (EpisodeMaxEPM pattern : tree) {
				// write the pattern
				for (int[] itemset : pattern.getEvents()) {
//						r.append('(');
					for (int item : itemset) {
						String string = Integer.toString(item);
						r.append(string);
						r.append(' ');
					}
					r.append("-1 ");
				}

				// write the support of the pattern
				r.append("#SUP: ");
				r.append(pattern.support);

				if (DEBUG_MODE) {
					System.out.println(r.toString());
				}
				// write end of line
				r.append(System.lineSeparator());
			}
		}
		// write to file 
		writer.write(r.toString());
		writer.close();
	}

	/**
	 * Save a pattern of size > 1 to the output file.
	 *
	 * @param prefix the prefix
	 * @param bitmap its bitmap
	 * @throws IOException exception if error while writing to the file
	 * @return true if pattern is subsumed
	 */
	private boolean saveEpisode(EpisodeMaxEPM prefix) throws IOException {
		int length = prefix.realSize();
		
		// CHANGED ------
		if (DEBUG_MODE) {
			System.out.println("*Trying to save : " + prefix);
		}
		// WE COMPARE WITH LARGER PATTERNS FOR SUPER-PATTERN CHECKING
		// #################
		// IMPORTANT STRATEGY : FROM LARGER TO SMALLER......
		// ##################
		for (int i = maxPatterns.size() - 1; i > length; i--) {
			for (EpisodeMaxEPM pPrime : maxPatterns.get(i)) {
//    			System.out.println(pPrime.prefix.sumOfItems);
				// if the prefix pattern has a support higher or equal to the current pattern
				if (pPrime.sumOfOddItems + pPrime.sumOfEvenItems
						< prefix.sumOfOddItems + prefix.sumOfEvenItems) {
					break;
				}

				if (prefix.sumOfEvenItems <= pPrime.sumOfEvenItems 
						&& prefix.sumOfOddItems <= pPrime.sumOfOddItems
						&& strictlyContains(pPrime, prefix)) {
					return true;
				}

			}
		}

		// WE COMPARE WITH SMALLER PATTERNS FOR SUB-PATTERN CHECKING
		for (int i = 1; i < length && i < maxPatterns.size(); i++) {
			// for each pattern already found of size i

			// IMPORTANT : WE USE A DESCENDNIG ITERATOR...
			// HOWEVER' WE COULD USE THIS FOR PRUNING...
			Iterator<EpisodeMaxEPM> iter = maxPatterns.get(i).descendingIterator(); // DESCENDING ITERATOR !!!!!!!!!!
			while (iter.hasNext()) {
				EpisodeMaxEPM pPrime = iter.next();

				// CAN DO THIS BECAUSE OF DESCENDING ORDER
				if (pPrime.sumOfOddItems + pPrime.sumOfEvenItems >= prefix.sumOfOddItems + prefix.sumOfEvenItems) {
					break;
				}

				if (prefix.sumOfEvenItems >= pPrime.sumOfEvenItems && prefix.sumOfOddItems >= pPrime.sumOfOddItems
						&& strictlyContains(prefix, pPrime)) {
					maxPatternCount--; // DECREASE COUNT
					if (DEBUG_MODE) {
						System.out.println("REMOVE : " + pPrime);
					}
					iter.remove();
				}
			}
		}

		// OTHERWISE THE NEW PATTERN IS NOT SUBSUMMED
		while (maxPatterns.size() - 1 < length) {
			maxPatterns.add(new TreeSet<EpisodeMaxEPM>());
		}

		// save the pattern
		TreeSet<EpisodeMaxEPM> patternsOfSizeM = maxPatterns.get(length);
		patternsOfSizeM.add(prefix);

		// increase the pattern count
		maxPatternCount++; 

		// if in debug mode
		if (DEBUG_MODE) {
			System.out.println(" saved");
		}
		return false; // not subsumed
	}

	boolean strictlyContains(EpisodeMaxEPM pattern1, EpisodeMaxEPM pattern2) {
//		// if pattern2 is larger or equal in size, then it cannot be contained in pattern1
		if (pattern1.realSize() <= pattern2.realSize()) {
			return false;
		}

		// To see if pattern2 is strictly contained in pattern1,
		// we will search for each itemset i of pattern2 in pattern1 by advancing
		// in pattern 1 one itemset at a time.

		int i = 0; // position in pattern2
		int j = 0; // position in pattern1
		while (true) {
			// if the itemset at current position in pattern1 contains the itemset
			// at current position in pattern2
			if (ArraysAlgos.includedIn(pattern2.events.get(i), pattern1.events.get(j))) {
				// go to next itemset in pattern2
				i++;

				// if we reached the end of pattern2, then return true
				if (i == pattern2.size()) {
					return true;
				}
			}

			// go to next itemset in pattern1
			j++;

			// if we reached the end of pattern1, then pattern2 is not strictly included
			// in it, and return false
			if (j >= pattern1.size()) {
				return false;
			}

//			// lastly, for optimization, we check how many itemsets are left to be matched.
//			// if there is less itemsets left in pattern1 than in pattern2, then it will
//			// be impossible to get a total match, and so we return false.
			if ((pattern1.realSize() - j) < pattern2.realSize() - i) {
				return false;
			}
		}
	}

	/**
	 * Serial join (only do S-extention) because we first find all frequent itemsets
	 * 
	 * @param alpha          an episode
	 * @param alphaBoundlist the bound list of the episode
	 * @throws IOException
	 */
	private boolean serialJoins(EpisodeMaxEPM alpha, List<int[]> alphaBoundlist) throws IOException {
		boolean foundSomething = false;
		for (int j = 0; j < encodingTable.getTableLength(); j++) {
			List<int[]> tempBoundlist;
			if(ACTIVATE_TEMPORAL_PRUNING) {
				tempBoundlist = temporalJoinWithPruning(alphaBoundlist, encodingTable.getBoundlistByID(j));
			}else {
				tempBoundlist = temporalJoin(alphaBoundlist, encodingTable.getBoundlistByID(j));
			}
			if (tempBoundlist != null && tempBoundlist.size() >= minSupport) {
				EpisodeMaxEPM beta = alpha.sExtension(encodingTable.getEpisodeNameByID(j), tempBoundlist.size());
				foundSomething = true;

				boolean hasExtension = serialJoins(beta, tempBoundlist);

				if (!hasExtension) {
					saveEpisode(beta);
				}
			}
		}
		return foundSomething;
	}
	
	/**
	 * Perform an S-extension (a temporal join)
	 * 
	 * @param alphaBoundlist the boundlist of an episode
	 * @param fjBoundlist    the boundlist of an item
	 * @return
	 */
	private List<int[]> temporalJoinWithPruning(List<int[]> alphaBoundlist, List<int[]> fjBoundlist) {

		int remaining = alphaBoundlist.size();
		
		List<int[]> tempBoundlist = new ArrayList<>();
		for (int i = 0, j = 0; i < alphaBoundlist.size() && j < fjBoundlist.size();) {
			if(tempBoundlist.size() + remaining < minSupport) {
//				System.out.println("Prune");
				return null;
			}

//            [ts_i,te_i] and te_j  -> [ts_i,te_j] where te_j - ts_i < maxWindow and te_j > te_i
			if (fjBoundlist.get(j)[1] <= alphaBoundlist.get(i)[1]) {
				// the te_j are small than current te_i
				j++;
			} else if (fjBoundlist.get(j)[1] - alphaBoundlist.get(i)[0] >= maxWindow) {
				// the te_j are large than current te_i, but te_j - ts_i >= maxWindow
				i++;
				remaining--;
			} else {
				// the te_j are large than current ts_i and te_j -ts_i < maxWindow
				tempBoundlist.add(new int[] { alphaBoundlist.get(i)[0], fjBoundlist.get(j)[1] });
				// Each start point of alpha bound only can statisfy one within maxWindow
				// why not j++? because the j may combine with the next head of bound if they
				// statisfy the conditions
				i++;
				remaining--;
			}
		}
		this.candidateCount++;
		return tempBoundlist;
	}

	/**
	 * Perform an S-extension (a temporal join)
	 * 
	 * @param alphaBoundlist the boundlist of an episode
	 * @param fjBoundlist    the boundlist of an item
	 * @return
	 */
	private List<int[]> temporalJoin(List<int[]> alphaBoundlist, List<int[]> fjBoundlist) {
		this.candidateCount++;

		List<int[]> tempBoundlist = new ArrayList<>();
		for (int i = 0, j = 0; i < alphaBoundlist.size() && j < fjBoundlist.size();) {

//            [ts_i,te_i] and te_j  -> [ts_i,te_j] where te_j - ts_i < maxWindow and te_j > te_i
			if (fjBoundlist.get(j)[1] <= alphaBoundlist.get(i)[1]) {
				// the te_j are small than current te_i
				j++;
			} else if (fjBoundlist.get(j)[1] - alphaBoundlist.get(i)[0] >= maxWindow) {
				// the te_j are large than current te_i, but te_j - ts_i >= maxWindow
				i++;
			} else {
				// the te_j are large than current ts_i and te_j -ts_i < maxWindow
				tempBoundlist.add(new int[] { alphaBoundlist.get(i)[0], fjBoundlist.get(j)[1] });
				// Each start point of alpha bound only can statisfy one within maxWindow
				// why not j++? because the j may combine with the next head of bound if they
				// statisfy the conditions
				i++;
			}
		}
		return tempBoundlist;
	}

	/**
	 * Do a fima join
	 * 
	 * @param itemset       an itemset
	 * @param itemsetLength the length of the itemset
	 */
	private void fimajoin(Itemset itemset, int itemsetLength, List<Itemset> frequentItemsets) {
		 Map<Integer, List<Integer>> mapCurrentItemsLocationList = generatePListAndObtainFrequentItems(itemset.getLocationList());
		
		for (Entry<Integer, List<Integer>> entry : mapCurrentItemsLocationList.entrySet()) {
			List<Integer> locationList = entry.getValue();
			int support = locationList.size();
			if (support >= minSupport) {
				int item = entry.getKey();
				int[] newFreItemset = new int[itemsetLength + 1];
				System.arraycopy(itemset.getName(), 0, newFreItemset, 0, itemsetLength);
				newFreItemset[itemsetLength] = item;
				Itemset newItemset = new Itemset(newFreItemset, locationList);
				
				fimaJointCount++;

				fimajoin(newItemset, itemsetLength + 1, frequentItemsets);
				frequentItemsets.add(newItemset);
			} 
		}
	}

	/**
	 * Method to obtain the frequent itemsets
	 * 
	 * @param locationList a location list
	 * @return a map indicating the location lists (values) for some items (keys)
	 */
	private Map<Integer, List<Integer>> generatePListAndObtainFrequentItems(List<Integer> locationList) {
		Map<Integer, List<Integer>> mapCurrentItemsLocationList = new HashMap<Integer, List<Integer>>(); 
		
		for (int i = 0; i < locationList.size(); i++) {
			int index = locationList.get(i);
			int currentTid = indexDB.get(index)[1];

			// find following items that having same TID with currentTID
			index++;
			while (index < indexDB.size() && indexDB.get(index)[1] == currentTid) {
				int itemName = indexDB.get(index)[0];
				
				List<Integer> currentItemLocationList = mapCurrentItemsLocationList.get(itemName);
				if (currentItemLocationList == null) {

					currentItemLocationList = new ArrayList<>();
					currentItemLocationList.add(index);
					mapCurrentItemsLocationList.put(itemName, currentItemLocationList);
				} else {
					currentItemLocationList.add(index);
					mapCurrentItemsLocationList.put(itemName, currentItemLocationList);
				}
				index++;
			}
		}
		return mapCurrentItemsLocationList;
	}

	/**
	 * Method to get the horizontal database
	 * 
	 * @param input
	 * @param frequentItemsName
	 * @throws IOException
	 */
	private void scanDatabaseAgainToDetermineIndexDB(String input, Set<Integer> frequentItemsName) throws IOException {
		Map<Integer, List<Integer>> mapItemLocationList = new HashMap<>();
		
		this.indexDB = new ArrayList<>();

		int index = 0;
		if (selfIncrement) {
			for (int i = 0; i < inMemoryDatabaseNoTime.size(); i++) {
				EventSet eventSet = inMemoryDatabaseNoTime.get(i);
				int tid = i + 1;

				for (int j = 0; j < eventSet.items.length; j++) {
					int item = eventSet.items[j];
					Integer bigItem = item;

					if (!frequentItemsName.contains(bigItem)) {
						// if the item_name is not frequent item, skip it
						continue;
					}

					List<Integer> locationList = mapItemLocationList.get(bigItem);
					if (locationList == null) {
						locationList = new ArrayList<>();
						locationList.add(index);
						mapItemLocationList.put(bigItem, locationList);

						indexDB.add(new int[] { item, tid });
						index++;

					} else if (locationList.get(locationList.size() - 1) != index) {
						// maybe exist the same item in the one transaction
						locationList.add(index);
						mapItemLocationList.put(bigItem, locationList);

						indexDB.add(new int[] { item, tid });
						index++;
					}
				}
			}
		} else {
			for (int i = 0; i < inMemoryDatabaseTime.size(); i++) {
				TimeEventSet eventSet = inMemoryDatabaseTime.get(i);
			
				for (int j = 0; j < eventSet.items.length; j++) {
					int item = eventSet.items[j];
					Integer bigItem = item;


					if (!frequentItemsName.contains(bigItem)) {
						// if the item_name is not frequent item, skip it
						continue;
					}

					List<Integer> locationList = mapItemLocationList.get(bigItem);
					if (locationList == null) {
						locationList = new ArrayList<>();
						locationList.add(index);
						mapItemLocationList.put(bigItem, locationList);

						indexDB.add(new int[] { item, eventSet.timestamp });
						index++;

					} else if (locationList.get(locationList.size() - 1) != index) {
						// maybe exist the same item in the one transaction
						locationList.add(index);
						mapItemLocationList.put(bigItem, locationList);

						indexDB.add(new int[] { item, eventSet.timestamp });
						index++;
					}
				}
			}
		}

		// to add the locationList to corresponding frequent item
		for (int i = 0; i < frequentItemsets.size(); i++) {
			int itemName = frequentItemsets.get(i).getName()[0];
			frequentItemsets.get(i).setLocationList(mapItemLocationList.get(itemName));
		}
	}

	/**
	 * Scan the database to find the frequent items
	 * 
	 * @param input the file path of a database
	 * @return a Set of frequent items
	 * @throws IOException if error occurs while reading file.
	 */
	private Set<Integer> scanDatabaseToDetermineFrequentItems(String input) throws IOException {
		// read file
		BufferedReader reader = new BufferedReader(new FileReader(input));
		String line;

		// key: item, value: support
		Map<Integer, Integer> mapItemCount = new HashMap<>();

		if (selfIncrement) {
			inMemoryDatabaseNoTime = new ArrayList<EventSet>();
			
			while (((line = reader.readLine()) != null)) {

				// if the line is a comment, is empty or is a
				// kind of metadata
				if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {
					continue;
				}

				String[] lineSplited = line.split(" ");

				int[] events = new int[lineSplited.length];

				for (int i = 0; i < lineSplited.length; i++) {
					events[i] = Integer.parseInt(lineSplited[i]);
					Integer itemName = events[i];
					Integer itemSupport = mapItemCount.get(itemName);
					if (itemSupport == null) {
						mapItemCount.put(itemName, 1);
					} else {
						mapItemCount.put(itemName, itemSupport + 1);
					}
				}

				EventSet eventSet = new EventSet();
				eventSet.items = events;
				inMemoryDatabaseNoTime.add(eventSet);
			}
		} else {
			inMemoryDatabaseTime = new ArrayList<TimeEventSet>();
			//// the timestamp exist in file
			int currentTid = -1;

			while (((line = reader.readLine()) != null)) {
				if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {
					continue;
				}

				String[] lineSplited = line.split("\\|");

				String[] lineItems = lineSplited[0].split(" ");

				currentTid = Integer.parseInt(lineSplited[1]);

				int[] events = new int[lineItems.length];

				for (int i = 0; i < lineItems.length; i++) {
					events[i] = Integer.parseInt(lineItems[i]);
					Integer bigItem = events[i];
					Integer itemSupport = mapItemCount.get(bigItem);
					if (itemSupport == null) {
						mapItemCount.put(bigItem, 1);
					} else {
						mapItemCount.put(bigItem, itemSupport + 1);
					}
				}

				currentTid++;
				TimeEventSet eventSet = new TimeEventSet();
				eventSet.items = events;
				eventSet.timestamp = currentTid;
				inMemoryDatabaseTime.add(eventSet);
			}
		}

		// record the frequent items' name , to filter non frequent items later.
		Set<Integer> frequentItemsName = new HashSet<>();

		// We obtain all frequent items;
		for (Map.Entry<Integer, Integer> entry : mapItemCount.entrySet()) {
			if (entry.getValue() >= minSupport) {
				Itemset item = new Itemset(new int[] { entry.getKey()});
				frequentItemsets.add(item);
				frequentItemsName.add(entry.getKey());
			}
		}

		reader.close();

		return frequentItemsName;
	}

	/**
	 * Print statistics about the algorithm execution to System.out.
	 */
	public void printStats() {
		System.out.println("=============  MAX-FEM - STATS =============");
		System.out.println(" Maximal Episodes count : " + maxPatternCount);
		System.out.println(" Fima joint count : " + fimaJointCount);
		System.out.println(" Candidates count : " + candidateCount);
		System.out.println(" Maximum memory usage : " + MemoryLogger.getInstance().getMaxMemory() + " mb");
		System.out.println(" Total time ~ : " + (endTimestamp - startTimestamp) + " ms");
		System.out.println("===================================================");
	}

}
