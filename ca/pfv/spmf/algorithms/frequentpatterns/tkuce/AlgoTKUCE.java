package ca.pfv.spmf.algorithms.frequentpatterns.tkuce;

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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * This is an implementation of the "TKU-CE algorithm" for High-Utility Itemsets
 * Mining as described in the conference paper :
 * 
 * TKU-CE: Cross-Entropy Method for Mining Top-K High Utility Itemsets
 *
 * @author Wei Song, Lu Liu, and Chaomin Huang
 */

public class AlgoTKUCE {
	// variable for statistics
	/** the maximum memory usage*/ 
	double maxMemory = 0; 
	/** the time the algorithm started */
	long startTimestamp = 0;
	/** the time the algorithm terminated */
	long endTimestamp = 0; 
	/** the sample numbers */
	final int pop_size = 2000; 
	/**  the iterations of algorithm */
	final int iterations = 2000; 
	/** the size of transactions */
	int transactionCount; 
	/** the number of desired HUIs */
	int K;

	/** the minimum utility */
	int minUtility = 0;
	/**  the quantile parameter */
	final float alpha = (float) 0.2; 

	/** create a map to store the TWU of each item */
	Map<Integer, Integer> mapItemToTWU; 
	/** the items which has twu value more than minUtil */
	List<Integer> twuPattern;
	/** writer to write the output file */
	BufferedWriter writer = null; 
	/** probability vector */
	float[] p; 
	

	/** samples */
	List<Particle> population = new ArrayList<Particle>();
	/** the set of HUIs */
	List<HUI> huiSets = new ArrayList<HUI>();

	/**  a list to store database */
	List<List<Pair>> database = new ArrayList<List<Pair>>();
	/** the list of items */
	List<Item> Items;
	/** Top-k high utility itemsets */
	List<Particle> huiTopKBA = new ArrayList<Particle>(); 

	/** this class represent an item and its utility in a transaction
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
		/**  the sample */
		BitSet X; 
		/** fitness value of sample */
		int fitness; 

		/** Constructor of particle */
		public Particle() {
			X = new BitSet(twuPattern.size());
		}
		/** Constructor of particle 
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
				p = templist.get(m).intValue();
				i = 0;
				q = 0;
				temp = 0;
				sum = 0;

				while (q < database.get(p).size() && i < twuPattern.size()) {
					if (this.X.get(i)) {
						if (database.get(p).get(q).item == twuPattern.get(i)) {
							sum = sum + database.get(p).get(q).utility;
							++i;
							++q;
							++temp;
						} else {
							++q;
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
	public AlgoTKUCE() {
	}

	/**
	 * Run the algorithm
	 * 
	 * @param input  the input file path
	 * @param output the output file path
	 * @param k
	 * @throws IOException exception if error while writing the file
	 */

	public void runAlgorithm(String input, String output, int k) throws IOException {
		// save the k parameter
		this.K = k;
		// reset memory usage
		MemoryLogger.getInstance().reset();
		// reset start time
		startTimestamp = System.currentTimeMillis();
		// initialize object to write output to file
		writer = new BufferedWriter(new FileWriter(output));
		// create a map to store the TWU of each item
		mapItemToTWU = new HashMap<Integer, Integer>();
		// scan the database a first time to calculate the TWU of each item.
		BufferedReader myInput = null;
		String thisLine;
		try {
			// prepare the object for reading the file
			myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))));
			// for each line (transaction) until the end of file
			while ((thisLine = myInput.readLine()) != null) {
				// if the line is a comment, is empty or is a kind of metadata
				if (thisLine.isEmpty() == true || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
						|| thisLine.charAt(0) == '@') {
					continue;
				}
				++transactionCount;// Count the number of transactions in the database
				// split the transaction according to the : separator
				String split[] = thisLine.split(":");
				// the first part is the list of items
				String items[] = split[0].split(" ");
				// the second part is the transaction utility
				int transactionUtility = Integer.parseInt(split[1]);
				// for each item, we add the transaction utility to its TWU
				for (int i = 0; i < items.length; i++) {
					// convert item to integer
					Integer item = Integer.parseInt(items[i]);
					// get the current TWU of that item
					Integer twu = mapItemToTWU.get(item);
					// add the utility of the item in the current transaction to
					// its twu
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
		// SECOND DATABASE PASS TO CONSTRUCT THE DATABASE
		// OF 1-ITEMSETS HAVING TWU >= minutil (promising items)
		try {
			// prepare object for reading the file
			myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))));
			// variable to count the number of transaction
			// for each line (transaction) until the end of file
			while ((thisLine = myInput.readLine()) != null) {
				// if the line is a comment, is empty or is a kind of metadata
				if (thisLine.isEmpty() == true || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
						|| thisLine.charAt(0) == '@') {
					continue;
				}
				// split the line according to the separator
				String split[] = thisLine.split(":");
				// get the list of items
				String items[] = split[0].split(" ");
				// get the list of utility values corresponding to each item
				// for that transaction
				String utilityValues[] = split[2].split(" ");
				// Create a list to store items and its utility
				List<Pair> revisedTransaction = new ArrayList<Pair>();
				// for each item
				for (int i = 0; i < items.length; i++) {
					// / convert values to integers
					Pair pair = new Pair();
					pair.item = Integer.parseInt(items[i]);
					pair.utility = Integer.parseInt(utilityValues[i]);
					// if the item has enough utility
					if (mapItemToTWU.get(pair.item) >= minUtility) {
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
		twuPattern = new ArrayList<Integer>(mapItemToTWU.keySet());
		Collections.sort(twuPattern);
		// the probability vector
		p = new float[twuPattern.size()];
		Items = new ArrayList<Item>();
		for (Integer tempitem : twuPattern) {
			Items.add(new Item(tempitem.intValue()));
		}
		// bitmap cover representation
		for (int i = 0; i < database.size(); ++i) {
			for (int j = 0; j < Items.size(); ++j) {
				for (int m = 0; m < database.get(i).size(); ++m) {
					if (Items.get(j).item == database.get(i).get(m).item) {
						Items.get(j).TIDS.set(i);
					}
				}
			}
		}
		// check the memory usage
		MemoryLogger.getInstance().checkMemory();
		// Mine the database recursively
		if (twuPattern.size() > 0) {
			// initial population
			generatePop();
			int max_min;
			for (int i = 0; i < iterations; i++) {
				// Sort the N itemsets
				Collections.sort(population, new Comparator<Particle>() {
					public int compare(Particle itemset1, Particle itemset2) {
						return -(itemset1.fitness - itemset2.fitness);
					}
				});
				max_min = population.get(0).fitness - population.get(pop_size - 1).fitness;
				// stopping criterion
				if (max_min == 0) {
					break;
				}
				// update population and HUIset
				update();
			}

			for (int i = 0; i < K; ++i) {
				if (i <= huiTopKBA.size() - 1) {
					insert(huiTopKBA.get(i));
				}
			}

			// If there is a K + N th itemset, the utility is equal to the K th itemset.
			// add K + N th itemset into Top-k high utility itemsets, too
			int next = K;
			while (true) {
				if (huiTopKBA.get(next).fitness == huiTopKBA.get(next - 1).fitness) {
					insert(huiTopKBA.get(next));
					next += 1;
				} else {
					break;
				}
			}
		}

		writeOut();
		// check the memory usage again and close the file.
		MemoryLogger.getInstance().checkMemory();
		maxMemory = MemoryLogger.getInstance().getMaxMemory();
		// close output file
		writer.close();
		// record end time
		endTimestamp = System.currentTimeMillis();
	}

	/**
	 * This is the method to initialization
	 * 
	 */
	private void generatePop() {
		int i, j, k, temp;
		List<Integer> transList;
		for (i = 0; i < pop_size; ++i) {
			Particle tempParticle = new Particle(twuPattern.size());
			j = 0;
			// k is the count of 1 in probability vector
			k = (int) (Math.random() * twuPattern.size());
			while (j < k) {
				// roulette select the position of 1 in population
				temp = (int) (Math.random() * twuPattern.size()) + 1;
				if (!tempParticle.X.get(temp)) {
					j++;
					tempParticle.X.set(temp);
				}
			}
			// calculate utility
			transList = new ArrayList<Integer>();// Stores the collection of transactions in which the itemset resides
			isRBAIndividual(tempParticle, transList);
			tempParticle.calculateFitness(k, transList);
			// insert itemset into itemsets collection of current iteration
			population.add(i, tempParticle);
			// insert into sets of top-k high utility itemsets
			insertTopList(population.get(i));
		}

	}

	/**
	 * This is the method to update the set of top-k HUIs KH using the new sample
	 */
	private void update() {
		int[] num = new int[twuPattern.size()];
		for (int i = 0; i < alpha * pop_size; ++i) {
			for (int j = 0; j < twuPattern.size(); ++j) {
				if (population.get(i).X.get(j)) {
					num[j] += 1;
				}
			}
		}
		minUtility = population.get((int) (alpha * pop_size - 1)).fitness;
		for (int i = 0; i < twuPattern.size(); ++i) {
			p[i] = (float) (num[i] / (alpha * pop_size + 0.0));
		}
		List<Integer> transList;
		int k = 0;
		for (int i = 0; i < pop_size; ++i) {
			Particle tempParticle = new Particle(twuPattern.size());
			update_Particle(tempParticle);
			// calculate utility
			transList = new ArrayList<Integer>();//
			isRBAIndividual(tempParticle, transList);
			k = tempParticle.X.cardinality();
			tempParticle.calculateFitness(k, transList);
			population.add(i, tempParticle);
			insertTopList(population.get(i));
		}
	}

	private void update_Particle(Particle temp) {
		for (int i = 0; i < twuPattern.size(); ++i) {
			if (Math.random() < p[i]) {
				temp.X.set(i);
			}
		}
	}

	private void insertTopList(Particle tmp) {
		Particle temp = new Particle();
		temp.copyParticle(tmp);
		// When there is no element in the list, press it directly into the list
		if (huiTopKBA.size() == 0) {
			huiTopKBA.add(temp);
			return;
		}
		int max = 0;
		int min = K - 1;
		int mid = 0;
		if (huiTopKBA.size() < K) {
			min = huiTopKBA.size() - 1;
			// If the size of the list is not enough K
			// , and the utility value of temp is less than or equal to the minimum utility
			// value
			// , append it directly at the end.
			if (temp.fitness < huiTopKBA.get(min).fitness) {
				huiTopKBA.add(temp);
				return;
			}
		} else {
			// List size is greater than or equal to K
			if (temp.fitness < huiTopKBA.get(min).fitness) {
				return;
			}

		}
		// other
		while (max <= min) {
			mid = (max + min) / 2;
			if (temp.fitness > huiTopKBA.get(mid).fitness) {
				min = mid - 1;
			} else if (temp.fitness < huiTopKBA.get(mid).fitness) {
				max = mid + 1;
			} else {
				return;
			}
		}
		if (temp.fitness >= huiTopKBA.get(mid).fitness) {
			huiTopKBA.add(mid, temp);
		} else {
			huiTopKBA.add(mid + 1, temp);
		}
	}

	/**
	 * It is used to get the collection of transactions in which the itemset
	 * resides. If the itemset itself is unreasonable, it is automatically
	 * fine-tuned during the calculation. Make it reasonable and get the set of
	 * transactions in which it is located
	 */
	public boolean isRBAIndividual(Particle tempBAIndividual, List<Integer> list) {
		List<Integer> templist = new ArrayList<Integer>();
		for (int i = 0; i < twuPattern.size(); ++i) {
			if (tempBAIndividual.X.get(i)) {
				templist.add(i);
			}
		}
		if (templist.size() == 0) {
			return false;
		}
		BitSet tempBitSet = new BitSet(transactionCount);
		BitSet midBitSet = new BitSet(transactionCount);
		tempBitSet = (BitSet) Items.get(templist.get(0).intValue()).TIDS.clone();
		midBitSet = (BitSet) tempBitSet.clone();

		for (int i = 1; i < templist.size(); ++i) {
			tempBitSet.and(Items.get(templist.get(i).intValue()).TIDS);
			if (tempBitSet.cardinality() != 0) {
				midBitSet = (BitSet) tempBitSet.clone();
			} else {

				tempBitSet = (BitSet) midBitSet.clone();
				tempBAIndividual.X.clear(templist.get(i).intValue());
			}
		}

		if (tempBitSet.cardinality() == 0) {
			return false;
		} else {
			for (int m = 0; m < tempBitSet.length(); ++m) {
				if (tempBitSet.get(m)) {
					list.add(m);
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
			// if not exist same itemset in huiSets with tempParticle,insert it
			// into huiSets
			if (i == huiSets.size())
				huiSets.add(new HUI(temp.toString(), tempParticle.fitness));
		}
	}

	/**
	 * Method to write a high utility itemset to the output file.
	 * 
	 * @throws IOException
	 */
	private void writeOut() throws IOException {
		for (int i = 0; i < huiSets.size(); i++) {
			StringBuilder buffer = new StringBuilder();
			buffer.append(huiSets.get(i).itemset);
			// append the utility value
			buffer.append("#UTIL:");
			buffer.append(huiSets.get(i).fitness);
			writer.write(buffer.toString());
			writer.newLine();
		}
	}


	/**
	 * Print statistics about the latest execution to System.out.
	 */
	public void printStats() {
		System.out.println("============= TKU-CE Algorithm v 2.52 ==========");
		System.out.println(" Total time ~ " + (endTimestamp - startTimestamp) + " ms");
		System.out.println(" Memory ~ " + maxMemory + " MB");
		System.out.println(" High-utility itemsets count : " + huiSets.size());
		System.out.println("================================================");
	}
}
