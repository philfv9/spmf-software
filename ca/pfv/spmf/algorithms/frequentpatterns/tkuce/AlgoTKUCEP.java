package ca.pfv.spmf.algorithms.frequentpatterns.tkuce;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import ca.pfv.spmf.tools.MemoryLogger;

/*
 *  Copyright (c) 2021 Wei Song, Lu Liu, and Chaomin Huang
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
 */
/**
 * This is an implementation of the "TKU-CE+ algorithm" for Top-K High-Utility
 * Itemsets Mining as described in the conference paper :
 * <p>
 * Heuristically mining the top-k high-utility itemsets with cross-entropy
 * optimization
 *
 * @author Wei Song, Chuanlong Zheng, Chaomin Huang, and Lu Liu
 */

public class AlgoTKUCEP {
	// variable for statistics
	/** max memory usage */
	double maxMemory = 0;
	/** the time the algorithm started */
	long startTimestamp = 0;
	/** the time the algorithm terminated */
	long endTimestamp = 0;
	/** the sample count */
	final int sampleSize = 2000;
	/** the iterations of algorithm */
	final int maxIteration = 2000;
	/** the number of actual iteration */
	int actualIterations = 0;
	/** the size of transactions */
	int transactionCount = 0;
	/** the number of desired HUIs */
	static int K = 0;
	/** the critical utility value */
	int CUV = 0;
	/** the quantile parameter ρ */
	final float rho = (float) 0.2;
	/** the smooth factor */
	final float SF = (float) 0.2;
	/** a map that stores the utility of each item */
	Map<Integer, Integer> mapItemToU;
	/** create a map to store the TWU of each item */
	Map<Integer, Integer> mapItemToTWU;
	/** the items which has twu value more than minUtil */
	List<Integer> twuPattern;
	/** writer to write the output file */
	BufferedWriter writer = null;
	/** probability vector */
	float[] p;
	

	/** samples */
	List<Particle> samples = new ArrayList<Particle>();
	/** the set of HUIs */
	List<HUI> huiSets = new ArrayList<HUI>();

	/**  a list to store database */
	List<List<Pair>> database = new ArrayList<List<Pair>>();
	/** the list of items */
	List<Item> Items;
	/** Top-k high utility itemsets */
	List<Particle> TopKHuiParticle = new ArrayList<Particle>(); 

	/**
	 * this class represent an item and its utility in a transaction
	 * 
	 */
	class Pair {
		/** an item */
		int item = 0;
		/** the utility */
		int utility = 0;
	}

	/** this class represent the sample */
	class Particle {
		/** the sample */
		BitSet X;
		/** fitness value of sample */
		int fitness;

		/** Constructor of particle */
		public Particle() {
			X = new BitSet(twuPattern.size());
		}

		/**
		 * Constructor of particle
		 * 
		 * @param length the particle length
		 */
		public Particle(int length) {
			X = new BitSet(length);
		}
		/** Copy a particle
		 * 
		 * @param particle1 the particle
		 */
		public void copyParticle(Particle particle1) {
			this.X = (BitSet) particle1.X.clone();
			this.fitness = particle1.fitness;
		}

		/** Calculate the fitness
		 * 
		 * @param k  the k value
		 * @param templist a temporary list
		 */
		public void calculateFitness(int k, List<Integer> templist) {
			if (k == 0)
				return;
			int i, p, q, temp, m;
			int sum, fitness = 0;
			for (m = 0; m < templist.size(); m++) {
				p = templist.get(m);
				i = 0;
				q = 0;
				temp = 0;
				sum = 0;

				while (q < database.get(p).size() && i < twuPattern.size()) {
					if (this.X.get(i)) {
						// using a loop for solving the unordered datasets
						for (int t = 0; t < database.get(p).size(); t++) {
							if (database.get(p).get(t).item == twuPattern.get(i)) {
								sum = sum + database.get(p).get(t).utility;
								++i;
								++temp;
								break;
							}
						}
					} else {
						++i;
					}
				}
				if (temp == k) {
					fitness = fitness + sum;
				}
			}
			this.fitness = fitness;
		}
	}

	/**
	 * A high utility itemset	
	 */ 
	static class HUI {
		/** the itemset */
		String itemset;
		/** the itemset's fitness */
		int fitness;

		/**
		 * Constructor
		 * @param itemset an itemset
		 * @param fitness its fitness
		 */
		public HUI(String itemset, int fitness) {
			super();
			this.itemset = itemset;
			this.fitness = fitness;
		}
	}

	/**
	 * Class representing an item
	 */
	class Item {
		/** the item */
		int item;
		/** the item's bitset */
		BitSet TIDS;


		 /** Constructor with an item
		  * 
		  * @param item the item
		  */
		public Item(int item) {
			TIDS = new BitSet(transactionCount);
			this.item = item;
		}
	}



	/**
	 * Default constructor
	 */
	public AlgoTKUCEP() {
	}

	/**
	 * Run the algorithm
	 *
	 * @param input  the input file path
	 * @param output the output file path
	 * @throws IOException exception if error while writing the file
	 */

	public void runAlgorithm(String input, String output, int TopK) throws IOException {
		// reset memory usage
		MemoryLogger.getInstance().reset();
		// save the k parameter
		K = TopK;
		// initialization
		startTimestamp = System.currentTimeMillis();
		/** initialize object to write output to file */
		writer = new BufferedWriter(new FileWriter(output));
		mapItemToU = new HashMap<>();
		mapItemToTWU = new HashMap<>();
		// scan the database first time to calculate the TWU of each item.
		BufferedReader myInput = null;
		String thisLine;
		try {
			// prepare the object for reading the file
			myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))));
			// for each line (transaction) until the end of file
			while ((thisLine = myInput.readLine()) != null) {
				// if the line is a comment, is empty or is a kind of metadata
				if (thisLine.isEmpty() || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
						|| thisLine.charAt(0) == '@') {
					continue;
				}
				++transactionCount;// Count the number of transactions in the database
				// split the transaction according to the : separator
				String[] split = thisLine.split(":");
				// the first part is the list of items
				String[] items = split[0].split(" ");
				// the second part is the transaction utility
				int transactionUtility = Integer.parseInt(split[1]);
				// the third part is the list of utility
				String[] utilities = split[2].split(" ");
				// for each item, we add the transaction utility to its TWU
				for (int i = 0; i < items.length; i++) {
					// convert item to integer
					Integer item = Integer.parseInt(items[i]);
					Integer utility = Integer.parseInt(utilities[i]);
					Integer u = mapItemToU.get(item);
					// add the utility of the item
					u = (u == null) ? utility : u + utility;
					mapItemToU.put(item, u);
					// get the current TWU of the item
					Integer twu = mapItemToTWU.get(item);
					// add the utility of the item in the current transaction to its twu
					twu = (twu == null) ? transactionUtility : twu + transactionUtility;
					mapItemToTWU.put(item, twu);
				}
			}
		} catch (Exception e) {
			// catches exception if error while reading the input file
			e.printStackTrace();
		} finally {
			if (myInput != null) {
				myInput.close();
			}
		}

		// this function is for calculating the critical utility value
		calculateCUV(mapItemToU);

		twuPattern = new ArrayList<>();
		for (Entry<Integer, Integer> vo : mapItemToTWU.entrySet()) {
			Integer item = vo.getKey();
			if (mapItemToTWU.get(item) >= CUV) {
				twuPattern.add(item);
			}
		}
		// the probability vector
		p = new float[twuPattern.size()];
		// Collections.sort(twuPattern);
		// SECOND DATABASE PASS TO CONSTRUCT THE DATABASE OF 1-ITEMSETS HAVING TWU >=
		// minutil (promising items)
		try {
			// prepare object for reading the file
			myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))));

			// variable to count the number of transaction
			// for each line (transaction) until the end of file
			while ((thisLine = myInput.readLine()) != null) {
				// if the line is a comment, is empty or is a kind of metadata
				if (thisLine.isEmpty() || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
						|| thisLine.charAt(0) == '@') {
					continue;
				}
				// split the line according to the separator
				String[] split = thisLine.split(":");
				// get the list of items
				String[] items = split[0].split(" ");
				// get the list of utility values corresponding to each item
				// for that transaction
				String[] utilityValues = split[2].split(" ");
				// Create a list to store items and its utility
				List<Pair> revisedTransaction = new ArrayList<>();
				// for each item
				for (int i = 0; i < items.length; i++) {
					// convert values to integers
					Integer item = Integer.parseInt(items[i]);
					// if the item is contained
					if (mapItemToTWU.get(item) >= CUV) {
						Pair pair = new Pair();
						pair.item = Integer.parseInt(items[i]);
						pair.utility = Integer.parseInt(utilityValues[i]);
						// add it
						revisedTransaction.add(pair);
					}
				}
				database.add(revisedTransaction);
			}
		} catch (Exception e) {
			// to catch error while reading the input file
			e.printStackTrace();
		} finally {
			if (myInput != null) {
				myInput.close();
			}
		}
		Items = new ArrayList<>();
		for (Integer tempItem : twuPattern) {
			Items.add(new Item(tempItem));
		}
		// construct the database
		for (int i = 0; i < database.size(); ++i) {
			for (Item item : Items) {
				for (int k = 0; k < database.get(i).size(); ++k) {
					if (item.item == database.get(i).get(k).item) {
						item.TIDS.set(i);
					}
				}
			}
		}
		// check the memory usage
		MemoryLogger.getInstance().checkMemory();
		// Mine the database recursively
		if (twuPattern.size() > 0) {
			// generate sample
			generateSample(1.0f);
			// the end symbol
			float max_min;
			for (int i = 0; i < maxIteration; i++) {
				actualIterations++;
				samples.sort((itemset1, itemset2) -> -(itemset1.fitness - itemset2.fitness));
				max_min = samples.get(0).fitness - samples.get((int) (rho * sampleSize) - 1).fitness;
				float propotion = (max_min / samples.get(0).fitness);
				// stopping criterion
				if (max_min == 0) {
					break;
				}
				// update population and HUIset
				update((1 - SF) * propotion);
			}

			endTimestamp = System.currentTimeMillis();
			// add Top-K huis
			for (int i = 0; i < K; ++i) {
				if (i <= TopKHuiParticle.size() - 1) {
					insert(TopKHuiParticle.get(i));
				}
			}
		}
		// check the memory usage again and close the file.

		MemoryLogger.getInstance().checkMemory();
		maxMemory = MemoryLogger.getInstance().getMaxMemory();
		// record end time
		endTimestamp = System.currentTimeMillis();
		writeOut();
		// close output file
		writer.close();
	}

	/**
	 * this method is to calculate the critical utility value
	 *
	 * @param map the map stored the single item and its utility
	 */
	public void calculateCUV(Map<Integer, Integer> map) {
		if (map == null)
			return;
		int s = map.size();
		Collection<Integer> c = map.values();
		Object[] obj = c.toArray();
		Arrays.sort(obj, Collections.reverseOrder());
		s = Math.min(s, K);
		CUV = (int) obj[s - 1];
	}

	/**
	 * This is the method to initialization of sample
	 *
	 * @param proportion the proportion of smooth mutation in sample
	 */
	private void generateSample(float proportion) {
		int i, j, k, temp;
		List<Integer> transList;
		for (i = 0; i < (int) (proportion * sampleSize); ++i) {
			Particle tempParticle = new Particle(twuPattern.size());
			j = 0;
			// k is the count of 1 in probability vector
			k = (int) (Math.random() * twuPattern.size() + 1);
			while (j < k) {
				// roulette select the position of 1 in population
				temp = (int) (Math.random() * twuPattern.size());
				// if this position is not occupied
				if (!tempParticle.X.get(temp)) {
					j++;
					tempParticle.X.set(temp);
				}
			}
			// calculate fitness
			transList = new ArrayList<>();
			isRBAIndividual(tempParticle, transList);
			tempParticle.calculateFitness(k, transList);
			// insert itemset into itemsets collection of current iteration
			samples.add(i, tempParticle);
			// insert into sets of top-k high utility itemsets
			insertTopList(samples.get(i));
		}

	}

	/**
	 * This is the method to update the set of top-k HUIs and the probability vector
	 * using the new sample
	 *
	 * @param proportion the proportion of smooth mutation in sample
	 */
	private void update(float proportion) {
		int[] num = new int[twuPattern.size()];
		for (int i = 0; i < rho * sampleSize; ++i) {
			for (int j = 0; j < twuPattern.size(); ++j) {
				if (samples.get(i).X.get(j)) {
					num[j] += 1;
				}
			}
		}
		CUV = samples.get((int) (rho * sampleSize - 1)).fitness;
		for (int i = 0; i < twuPattern.size(); ++i) {
			p[i] = (float) (num[i] / (rho * sampleSize + 0.0));
		}
		List<Integer> transList;
		int k;
		for (int i = 0; i < (int) (proportion * sampleSize); ++i) {
			Particle tempParticle = new Particle(twuPattern.size());
			update_Particle(tempParticle);
			transList = new ArrayList<>();
			if (isRBAIndividual(tempParticle, transList)) {
				k = tempParticle.X.cardinality();
				tempParticle.calculateFitness(k, transList);
				if (tempParticle.fitness > CUV) {
					samples.add(i, tempParticle);
					insertTopList(samples.get(i));
				}
			}
		}
		generateSample(SF);

	}

	/**
	 * generate the particle by probability vector
	 *
	 * @param temp the temporary particle
	 */
	private void update_Particle(Particle temp) {
		for (int i = 0; i < twuPattern.size(); ++i) {
			if (Math.random() < p[i]) {
				temp.X.set(i);
			}
		}
	}

	/**
	 * insert Top-K HUIs list
	 *
	 * @param tmp the temporary particle
	 */
	private void insertTopList(Particle tmp) {
		Particle temp = new Particle();
		temp.copyParticle(tmp);
		if (TopKHuiParticle.size() == 0) {
			TopKHuiParticle.add(temp);
			return;
		}
		int max = 0;
		int min = K - 1;
		int mid = 0;
		// If the size of the list is not enough K
		// , and the utility value of temp is less than or equal to the minimum utility
		// value
		// , append it directly at the end.
		if (TopKHuiParticle.size() < K) {
			min = TopKHuiParticle.size() - 1;
			if (temp.fitness < TopKHuiParticle.get(min).fitness) {
				TopKHuiParticle.add(temp);
				return;
			}
		} else {
			if (temp.fitness < TopKHuiParticle.get(min).fitness) {
				return;
			}
		}
		// find the ordered position using binary search
		while (max <= min) {
			mid = (max + min) / 2;
			if (temp.fitness > TopKHuiParticle.get(mid).fitness) {
				min = mid - 1;
			} else if (temp.fitness < TopKHuiParticle.get(mid).fitness) {
				max = mid + 1;
			} else {
				break;
			}
		}
		int mid_start = mid, mid_end = mid;
		if (temp.fitness > TopKHuiParticle.get(mid).fitness) {
			TopKHuiParticle.add(mid, temp);
		} else if (temp.fitness < TopKHuiParticle.get(mid).fitness) {
			TopKHuiParticle.add(mid + 1, temp);
		} else {
			// if exists some particles with same fitness
			// and the TopKHuiParticle does not contain the new temporary particle
			// then add it to the TopKHuiParticle
			if (!TopKHuiParticle.contains(temp)) {
				while (TopKHuiParticle.get(mid_start).fitness == temp.fitness) {
					if (TopKHuiParticle.get(mid_start).X.equals(temp.X)
							|| TopKHuiParticle.get(mid_end).X.equals(temp.X)) {
						return;
					}
					mid_start--;
					// out of bound
					if (mid_start == -1) {
						break;
					}
				}
				while (TopKHuiParticle.get(mid_end).fitness == temp.fitness) {
					if (TopKHuiParticle.get(mid_end).X.equals(temp.X)) {
						return;
					}
					mid_end++;
					// out of bound
					if (mid_end == TopKHuiParticle.size()) {
						break;
					}
				}
				TopKHuiParticle.add(mid, temp);
			}
		}
	}

	/**
	 * It is used to get the collection of transactions in which the itemset
	 * resides. If the itemset itself is unreasonable, it is automatically
	 * fine-tuned during the calculation. Make it reasonable and get the set of
	 * transactions in which it is located
	 *
	 * @param tempParticle the temporary particle
	 * @param tempList     store the existed position of this particle
	 * @return a bool value representing the temporary particle is a reasonable
	 *         particle
	 */
	public boolean isRBAIndividual(Particle tempParticle, List<Integer> tempList) {
		List<Integer> templist = new ArrayList<>();
		for (int i = 0; i < twuPattern.size(); ++i) {
			if (tempParticle.X.get(i)) {
				templist.add(i);
			}
		}
		if (templist.size() == 0) {
			return false;
		}
		BitSet tempBitSet = new BitSet(transactionCount);
		BitSet midBitSet = new BitSet(transactionCount);
		tempBitSet = (BitSet) Items.get(templist.get(0)).TIDS.clone();
		midBitSet = (BitSet) tempBitSet.clone();
		for (int i = 1; i < templist.size(); ++i) {
			tempBitSet.and(Items.get(templist.get(i)).TIDS);
			if (tempBitSet.cardinality() != 0) {
				midBitSet = (BitSet) tempBitSet.clone();
			} else {

				tempBitSet = (BitSet) midBitSet.clone();
				tempParticle.X.clear(templist.get(i));
			}
		}
		if (tempBitSet.cardinality() == 0) {
			return false;
		} else {
			for (int m = 0; m < tempBitSet.length(); ++m) {
				if (tempBitSet.get(m)) {
					tempList.add(m);
				}
			}
			return true;
		}
	}

	/**
	 * Method to inseret tempItemset to huiSets
	 *
	 * @param tempParticle the itemset to be inserted
	 */
	private void insert(Particle tempParticle) {
		int i;
		StringBuilder temp = new StringBuilder();
		for (i = 0; i < twuPattern.size(); i++) {
			if (tempParticle.X.get(i)) {
				temp.append(twuPattern.get(i));
				temp.append(' ');
			}
		}
		// huiSets is null
		if (huiSets.size() == 0) {
			huiSets.add(new HUI(temp.toString(), tempParticle.fitness));
		} else {
			// huiSets is not null, judge whether exist an itemset in huiSets
			// same with tempParticle
			for (i = 0; i < huiSets.size(); i++) {
				if (temp.toString().equals(huiSets.get(i).itemset)) {
					break;
				}
			}
			// if not exist same itemset in huiSets with tempParticle,insert it into huiSets
			if (i == huiSets.size())
				huiSets.add(new HUI(temp.toString(), tempParticle.fitness));
		}
	}

	/**
	 * Method to write a high utility itemset to the output file.
	 *
	 * @throws IOException throw exception
	 */
	private void writeOut() throws IOException {
		for (HUI huiSet : huiSets) {
			String buffer = huiSet.itemset +
			// append the utility value
					"#UTIL:" + huiSet.fitness;
			writer.write(buffer);
			writer.newLine();
		}
	}

	/**
	 * Print statistics about the latest execution to System.out.
	 */
	public void printStats() {
		System.out.println("============ TKU-CE+ Algorithm v 2.52 ===========");
		System.out.println(" Total time: " + (endTimestamp - startTimestamp) + " ms");
		System.out.println(" Memory: " + maxMemory + " MB");
		System.out.println(" Actual iterations: " + actualIterations);
		System.out.println(" High-utility itemsets count: " + huiSets.size());
		System.out.println("=================================================");
	}
}
