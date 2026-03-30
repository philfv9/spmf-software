package ca.pfv.spmf.algorithms.episodes.emma;

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
import ca.pfv.spmf.algorithms.episodes.general.FrequentEpisodes;
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
 * This is a implementation of the AFEM algorithm for frequent episode mining.
 * It is a version of MaxFEM that finds all frequent episodes instead of only
 * the maximal episodes.
 * 
 * @author Philippe Fournier-Viger, Peng yang, Saqib Nawaz et al.
 */

public class AlgoAFEM {
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
	
	/**
	 * The patterns that are found (if the user want to keep them into memory)
	 */
	private FrequentEpisodes freEpisodes = null;

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

	/** the number of maximal patterns */
	private int patternCount;

	/** boolean value to activate the debug mode */
	private final boolean DEBUG_MODE = false;
	
	private final boolean ACTIVATE_TEMPORAL_PRUNING = true;

	/**
	 * Construct
	 */
	public AlgoAFEM() {
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
	public FrequentEpisodes runAlgorithm(String input, String output, int minSupport, int maxWindow, boolean selfIncrement)
			throws IOException {
		// reset maximum
		MemoryLogger.getInstance().reset();

		this.minSupport = minSupport;
		this.maxWindow = maxWindow;
		this.selfIncrement = selfIncrement;

		this.patternCount = 0;
		this.fimaJointCount = 0;

		startTimestamp = System.currentTimeMillis();

		// Initialize a list to store the frequent itemsets
		frequentItemsets = new ArrayList<>();

		// Scan the file to sequence to find the frequent 1-items
		Set<Integer> frequentItemsName = scanDatabaseToDetermineFrequentItems(input);

		// Transform the TDB into the indexDB and maintain the locations of all frequent
		// 1-items in the index database
		scanDatabaseAgainToDetermineIndexDB(input, frequentItemsName);
	
		
		inMemoryDatabaseNoTime = null;
		inMemoryDatabaseTime = null;  

		// obtain all frequent itemsets without candidates
		for (int i = 0; i < frequentItemsName.size(); i++) {
			Itemset itemset = frequentItemsets.get(i);
			fimajoin(itemset, 1, frequentItemsets);
		}
		frequentItemsName = null;

		// Encode the database construction
		this.encodingTable = new EncodingTable();
		freEpisodes = new FrequentEpisodes();

		for (Itemset itemset : frequentItemsets) {
			List<int[]> events = new ArrayList<>(1);
			events.add(itemset.getName());
			EpisodeEMMA episode = new EpisodeEMMA(events, itemset.getSupport());
			freEpisodes.addFrequentEpisode(episode, 1);
			candidateCount++;

			List<int[]> boundlist = new ArrayList<>();
			for (int location : itemset.getLocationList()) {
				int[] bound = new int[] {indexDB.get(location)[1], indexDB.get(location)[1] };
				boundlist.add(bound);
			}
			encodingTable.addEpisodeAndBoundlist(episode, boundlist);

			// SAVE THE EPISODE !
		}

		frequentItemsets = null;
		this.indexDB = null;

		for (int i = 0; i < encodingTable.getTableLength(); i++) {
			// only do s-Extension
			serialJoins(encodingTable.getEpisodebyID(i), encodingTable.getBoundlistByID(i),1);
		}

		this.encodingTable = null;

		// record end time
		endTimestamp = System.currentTimeMillis();

		// check the memory usage
		MemoryLogger.getInstance().checkMemory();

		// save results to file
		if (output != null) {
			this.freEpisodes.saveToFile(output);
		}
		
		return freEpisodes;
	}



	/**
	 * Serial join (only do S-extention) because we first find all frequent itemsets
	 * 
	 * @param alpha          an episode
	 * @param alphaBoundlist the bound list of the episode
	 * @throws IOException
	 */
	private void serialJoins(EpisodeEMMA alpha, List<int[]> alphaBoundlist, int levelNum) throws IOException {
		for (int j = 0; j < encodingTable.getTableLength(); j++) {
			List<int[]> tempBoundlist;
			if(ACTIVATE_TEMPORAL_PRUNING) {
				tempBoundlist = temporalJoinWithPruning(alphaBoundlist, encodingTable.getBoundlistByID(j));
			}else {
				tempBoundlist = temporalJoin(alphaBoundlist, encodingTable.getBoundlistByID(j));
			}
			if (tempBoundlist != null && tempBoundlist.size() >= minSupport) {
				EpisodeEMMA beta = alpha.sExtension(encodingTable.getEpisodeNameByID(j), tempBoundlist.size());

				this.freEpisodes.addFrequentEpisode(beta, levelNum + 1);
				
				serialJoins(beta, tempBoundlist, levelNum + 1);
			}
		}
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
		System.out.println("=============  AFEM - STATS =============");
		System.out.println(" Candidates count : " + candidateCount);
		System.out.println(" The algorithm stopped at size : " + freEpisodes.getTotalLevelNum());
		System.out.println(" Frequent episodes count : " + this.freEpisodes.getFrequentEpisodesCount());
		System.out.println(" Maximum memory usage : " + MemoryLogger.getInstance().getMaxMemory() + " mb");
		System.out.println(" Total time ~ : " + (endTimestamp - startTimestamp) + " ms");
		System.out.println("===================================================");
	}

}
