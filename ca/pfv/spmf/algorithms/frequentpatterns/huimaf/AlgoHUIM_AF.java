package ca.pfv.spmf.algorithms.frequentpatterns.huimaf;

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
import java.util.List;
import java.util.Map;

/**
 * This is an implementation of the "HUIM-AF algorithm" for High-Utility Itemsets Mining
 * as described in the conference paper :
 * 
 * Artificial Fish Swarm Algorithm for Mining High Utility Itemsets
 * 
 * @author Wei Song, Junya Li，and Chaomin Huang
 */
public class AlgoHUIM_AF {
	/** DEBUG MODE */
	boolean DEBUG_MODE = false;
	
	/**  the maximum memory usage */
	double maxMemory = 0; 
	/**the time the algorithm started */
	long startTimestamp = 0; 
	/** the time the algorithm terminated */
	long endTimestamp = 0; 
	/** the size of populations */
	final int pop_size = 20;
	/** the iterations of algorithms */
	final int iterations = 10000;
	/** the total num of transactions */
	int transactionCount = 0;
	/** map from item to TWU */
	Map<Integer, Integer> mapItemToTWU;
	/** map from item to TWU (zero */
	Map<Integer, Integer> mapItemToTWU0;

	/**  the items which has twu value more than minUtil */
	List<Integer> twuPattern;

	/** writer to write the output file */
	BufferedWriter writer = null; 

	/** this class represent an item and its utility in a transaction */
	class Pair {
		/** item */
		int item = 0;
		/** the utility */
		int utility = 0;
	}

	/** this class represent the particles **/
	class Fish implements Comparable {
		/** the  artificial fish */
		BitSet X;
		/**  fitness value of artificial fish */
		int fitness;

		/** Constructor */
		public Fish() {
			X = new BitSet(twuPattern.size());
		}

		/**
		 * Constructor
		 * @param length length for initialization
		 */
		public Fish(int length) {
			X = new BitSet(length);
		}

		/**
		 * Deep copy of a fish
		 * @param fish the fish
		 */
		public void copyFish(Fish fish1) {
			this.X = (BitSet) fish1.X.clone();
			this.fitness = fish1.fitness;
		}

	    /** calculate fitness of itemset 
	     * 
	     * @param k the parameter k
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
				while (q < database.get(p).size() && i < this.X.length()) {
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
		@Override
		public int compareTo(Object o) {
			return -(fitness-((Fish)o).fitness);
		}
	}

	/**
	 * A high utility itemset
	 */
	class HUI {
		/** the itemset		 */
		String itemset;
		/** the fitness */
		int fitness;

		/**
		 * Constructor
		 * @param itemset the itemset
		 * @param fitness its fitness
		 */
		public HUI(String itemset, int fitness) {
			super();
			this.itemset = itemset;
			this.fitness = fitness;
		}

	}

    /** Class of an item to create a bitmap */
	class Item {
		/** an item */
		int item;
		/** the transactions ids */
		BitSet TIDS;
		/** default constructor*/
		public Item() {
			TIDS = new BitSet(transactionCount);
		}
		/** constructor with an item
		 * 
		 * @param item the item
		 */
		public Item(int item) {
			TIDS = new BitSet(transactionCount);
			this.item = item;
		}
	}

	/** the step */
	int step;
	/** the try time */
	int tryTime = 3;
	/** crowding factor*/
	double delta = 0.9; 
	
	/** the visaul */
	int visaul;
	// Set variables to mark whether the operation is performed
	/** is swarm */
	boolean is_swarm = false;
	/** is fellow */
	boolean is_fellow = false;

	/** a fish population */
	List<Fish> fishpopulation = new ArrayList<Fish>();
	/** a subfish population */
	List<Fish> subfishpopulation = new ArrayList<Fish>();
	/** the set of high utility itemsets */
	List<HUI> huiSets = new ArrayList<HUI>();// the set of HUIs
	/** the proportion of TWU values of each 1-HTWUIs in sum of TWU value */
	List<Double> percentage = new ArrayList<Double>(); 

	/** a list representation of the database */
	List<List<Pair>> database = new ArrayList<List<Pair>>();

	/** the list of items */
	List<Item> Items;
	
	/** store the artificial fishes */
	List<Fish> huiBA = new ArrayList<Fish>();

	/** percentage HUIBA */
	List<Double> percentHUIBA;

	/**
	 * Default constructor
	 */
	public AlgoHUIM_AF() {
	}

	/**
	 * Run the algorithm
	 * 
	 * @param input      the input file path
	 * @param output     the output file path
	 * @param minUtility the minimum utility threshold
	 * @throws IOException exception if error while writing the file
	 */
	public void runAlgorithm(String input, String output, int minUtility) throws IOException {
		// reset maximum
		maxMemory = 0;

		startTimestamp = System.currentTimeMillis();

		writer = new BufferedWriter(new FileWriter(output));

		// We create a map to store the TWU of each item
		mapItemToTWU = new HashMap<Integer, Integer>();
		mapItemToTWU0 = new HashMap<Integer, Integer>();

		// We scan the database a first time to calculate the TWU of each item.
		BufferedReader myInput = null;
		String thisLine;
		try {
			// prepare the object for reading the file
			myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))));
			// for each line (transaction) until the end of file
			while ((thisLine = myInput.readLine()) != null) {
				// if the line is a comment, is empty or is a
				// kind of metadata
				if (thisLine.isEmpty() == true || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
						|| thisLine.charAt(0) == '@') {
					continue;
				}
				++transactionCount;

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
					Integer twu0 = mapItemToTWU0.get(item);
					// add the utility of the item in the current transaction to
					// its twu
					twu = (twu == null) ? transactionUtility : twu + transactionUtility;
					twu0 = (twu0 == null) ? transactionUtility : twu0 + transactionUtility;
					mapItemToTWU.put(item, twu);
					mapItemToTWU0.put(item, twu0);
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
				// if the line is a comment, is empty or is a
				// kind of metadata
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
				// Create a list to store items
				List<Integer> pattern = new ArrayList<Integer>();
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
						pattern.add(pair.item);
					} else {
						mapItemToTWU0.remove(pair.item);
					}
				}
				// Copy the transaction into database but
				// without items with TWU < minutility
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

		twuPattern = new ArrayList<Integer>(mapItemToTWU0.keySet());
		Collections.sort(twuPattern);
		if(DEBUG_MODE) {
			System.out.println("twuPattern:" + twuPattern.size());
			System.out.println(twuPattern);
		}


		Items = new ArrayList<Item>();

		for (Integer tempitem : twuPattern) {
			Items.add(new Item(tempitem.intValue()));
		}
		//scan database to create bitmap
		for (int i = 0; i < database.size(); ++i) {
			for (int j = 0; j < Items.size(); ++j) {
				for (int k = 0; k < database.get(i).size(); ++k) {
					if (Items.get(j).item == database.get(i).get(k).item) {
						Items.get(j).TIDS.set(i);
						// System.out.println("***************");
					}
				}
			}
		}
		// check the memory usage
		checkMemory();
		// Mine the database recursively
		if (twuPattern.size() > 0) {
			// initial population
			generatePop(minUtility);

			// update fish population and HUIset
			for (int j = 0; j < iterations; j++) {
				visaul = (int) (twuPattern.size() * 0.1) + 1;
				for (int i = 0; i < pop_size; i++) {
					is_swarm = false;
					is_fellow = false;
					Fish fellowFish = follow(i, minUtility);
					// If swarming is not performed,following is performed
					if (!is_fellow) {
						Fish swarmFish = swarm(i, minUtility);
						if (is_swarm) {
							subfishpopulation.add(i,swarmFish);
						}
					}else {
						subfishpopulation.add(i,fellowFish);
					}
					if (!is_swarm && !is_fellow) {
						Fish preyFish = prey(i, minUtility); 
						subfishpopulation.add(i,preyFish);
					}
					
				}
				subfishpopulation.addAll(fishpopulation);
				Collections.sort(subfishpopulation);
				for (int k = 0; k < fishpopulation.size();k++) {
					fishpopulation.set(k, subfishpopulation.get(k));
				}
				subfishpopulation.clear();
				if (DEBUG_MODE && j % 1000 == 0) {
					System.out.println(j + "-update end. HUIs No. is " + huiSets.size());
				}
			}
		}

		writeOut();
		// check the memory usage again and close the file.
		checkMemory();
		// close output file
		writer.close();
		// record end time
		endTimestamp = System.currentTimeMillis();
	}

	/**
	 * This is the method to initial population
	 * 
	 * @param minUtility minimum utility threshold
	 */
	private void generatePop(int minUtility)//
	{
		int i, j, k, temp;
		List<Integer> transList;
		// initial percentage according to the twu value of 1-HTWUIs
		percentage = roulettePercent();

		if(DEBUG_MODE) {
			System.out.println(percentage);
		}
		for (i = 0; i < pop_size; i++) {
			// initial particles
			Fish tempFish = new Fish(twuPattern.size());
			j = 0;
			// k is the count of 1 in particle
			k = (int) (Math.random() * twuPattern.size());
			while (j < k) {
				// roulette select the position of 1 in population
				temp = rouletteSelect(percentage);
				if (!tempFish.X.get(temp)) {
					j++;
					tempFish.X.set(temp);
				}
			}
			// calculate fitness of itemset
			transList = new ArrayList<Integer>();
			isRBAIndividual(tempFish, transList);
			tempFish.calculateFitness(k, transList);
			// insert particle into population
			fishpopulation.add(i, tempFish);
			// update huiSets
			if (fishpopulation.get(i).fitness >= minUtility) {
				insert(fishpopulation.get(i));
				addHuiBA(fishpopulation.get(i));
			}
			
		}
	}
	/*
	 * When the objective function value of the optimal position is greater than the objective function value of the current position and it is 
	 * not very crowded, the current position will move one step to the optimal neighbor fish; otherwise, the foraging behavior will be performed.
	 */
	private Fish follow(int i, int minUtility) {
		Fish newfollowfish = new Fish(twuPattern.size());
		newfollowfish.copyFish(fishpopulation.get(i));
		Fish bestFish = new Fish(twuPattern.size());
		bestFish.copyFish(fishpopulation.get(0));
		//Find the best follower
		for (int m = 0; m < pop_size; m++) {
			//bestFish = fishpopulation.get(m);
			List<Integer> disList = xorBAIndividual(fishpopulation.get(i), fishpopulation.get(m));
			int dis = disList.size();
			if (visaul > dis && fishpopulation.get(m).fitness > bestFish.fitness) {
				bestFish.copyFish(fishpopulation.get(m));
			}
		}
		
		List<Integer> dis1 = xorBAIndividual(bestFish, fishpopulation.get(i));
		step = (int) (dis1.size() * Math.random()) + 1;
		if (dis1.size() > 0) {
			for (int m = 0; m < step; ++m) {
				int changeBit = (int) (dis1.size() * Math.random());
				if (newfollowfish.X.get(dis1.get(changeBit))) {
					newfollowfish.X.clear(dis1.get(changeBit));
				} else {
					newfollowfish.X.set(dis1.get(changeBit));
				}
			}
			List<Integer> transList = new ArrayList<Integer>();
			isRBAIndividual(newfollowfish, transList);
			newfollowfish.calculateFitness(newfollowfish.X.cardinality(), transList);
			
			if (newfollowfish.fitness >= minUtility) {
				insert(newfollowfish);
				addHuiBA(newfollowfish);
			}
			is_fellow = true;
			return newfollowfish;
		}else {
			is_fellow = false;
			return null;
		}
	}

	// swam start
	private Fish swarm(int i, int minUtility) {
		List<Integer> count1 = new ArrayList<Integer>();
		List<Integer> count0 = new ArrayList<Integer>();
		Fish newswarmfish = new Fish(twuPattern.size());
		newswarmfish.copyFish(fishpopulation.get(i));
		Fish centerFish = new Fish(twuPattern.size());
		// How many neighboring individuals are in the visual range
		int num = 0;
		for (int j = 0; j < twuPattern.size(); j++) {
			count1.add(j, 0);
			count0.add(j, 0);
		}
		for (int m = 0; m < pop_size; m++) {
			List<Integer> disList = xorBAIndividual(newswarmfish, fishpopulation.get(m));
			int dis = disList.size();
			if (visaul > dis) {
				++num;
				for (int j = 0; j < twuPattern.size(); j++) {
					// Count the number of zeros and ones with different bits
					if (fishpopulation.get(m).X.get(j)) {
						count1.add(j,count1.get(j)+1);
					} else {
						count0.add(j,count0.get(j)+1);
					}
				}
			}
		}
		for (int j = 0; j < twuPattern.size(); j++) {
			if (count1.get(j) >= count0.get(j)) {
				centerFish.X.set(j, true);
			} else {
				centerFish.X.set(j, false);
			}
		}
		
		List<Integer> transList = new ArrayList<Integer>();
		isRBAIndividual(centerFish, transList);
		centerFish.calculateFitness(centerFish.X.cardinality(), transList);
		if (centerFish.fitness >= minUtility) {
			insert(centerFish);
			addHuiBA(centerFish);
		}
		
		if (centerFish.fitness > fishpopulation.get(i).fitness) {
			List<Integer> disList = xorBAIndividual(centerFish, fishpopulation.get(i));
			step = (int) (disList.size() * Math.random()) + 1;
			if (disList.size() > 0) {
				for (int m = 0; m < step; ++m) {
					int changeBit = (int) (disList.size() * Math.random());
					if (newswarmfish.X.get(disList.get(changeBit))) {
						newswarmfish.X.clear(disList.get(changeBit));
					} else {
						newswarmfish.X.set(disList.get(changeBit));
					}
				}
			}
			
			transList = new ArrayList<Integer>();
			isRBAIndividual(newswarmfish, transList);
			newswarmfish.calculateFitness(newswarmfish.X.cardinality(), transList);
			if (newswarmfish.fitness >= minUtility) {
				insert(newswarmfish);
				addHuiBA(newswarmfish);
			}
			is_swarm = true;
			return newswarmfish;
		} else {
			return null;
		}
	}

	// prey start
	private Fish prey(int i, int minUtility) {
		int changeBit;
		List<Integer> disList;
		List<Integer> transList = new ArrayList<Integer>();
		Fish tempFish = new Fish(twuPattern.size());
		tempFish.copyFish(fishpopulation.get(i));
		Fish newpreyfish = new Fish(twuPattern.size());
		newpreyfish.copyFish(fishpopulation.get(i));
		// The flag bit is used to indicate whether a better point is found within a specified number of times
		int flag = 0;
		for (int k = 0; k < tryTime; k++) 
		{
			step = (int) (Math.random() * visaul) + 1;
			for (int m = 0; m < step; ++m) {
				changeBit = (int) (twuPattern.size() * Math.random());
				if (tempFish.X.get(changeBit)) {
					tempFish.X.clear(changeBit);
				} else {
					tempFish.X.set(changeBit);
				}
			}
			isRBAIndividual(tempFish, transList);
			tempFish.calculateFitness(tempFish.X.cardinality(), transList);
			//  Check whether the value is greater than the threshold  
			if (tempFish.fitness >= minUtility) {
				insert(tempFish);
				addHuiBA(tempFish);
			}
				
			if (tempFish.fitness > newpreyfish.fitness) {
				flag = 1;
				newpreyfish.copyFish(tempFish);
			} 
			
			transList.clear();
			if (flag==1) {
				break;
			}
		}
		// If there's nothing better nearby, take a random walk
		if (flag==0) {
			step = (int) (Math.random() * visaul) + 1;
			for (int m = 0; m < step; ++m) {
				changeBit = (int) (twuPattern.size() * Math.random());
				if (fishpopulation.get(i).X.get(changeBit)) {
					newpreyfish.X.clear(changeBit);
				} else {
					newpreyfish.X.set(changeBit);
				}
			}
			isRBAIndividual(newpreyfish, transList);
			newpreyfish.calculateFitness(newpreyfish.X.cardinality(), transList);
			if (newpreyfish.fitness >= minUtility) {
				insert(newpreyfish);
				addHuiBA(newpreyfish);
		    }
		}
		return newpreyfish;
	}
		

	/**
	 * It is used to get the collection of transactions in which the itemset resides. 
	 * If the itemset itself is unreasonable, it is automatically fine-tuned during the calculation.
	 * Make it reasonable and get the set of transactions in which it is located
	 */
	public boolean isRBAIndividual(Fish tempBAIndividual, List<Integer> list) {
		List<Integer> templist = new ArrayList<Integer>();
		// int temp=0;
		for (int i = 0; i < tempBAIndividual.X.length(); ++i) {
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
	 * @param gBest
	 * @param tempBAIndividual
	 * @return
	 */
	private List<Integer> xorBAIndividual(Fish temp, Fish tempBAIndividual) {
		List<Integer> list = new ArrayList<Integer>();
		BitSet tmpBitSet = (BitSet) temp.X.clone();
		tmpBitSet.xor(tempBAIndividual.X);
		for (int i = 0; i < tmpBitSet.length(); ++i) {
			if (tmpBitSet.get(i)) {
				list.add(i);
			}
		}
		return list;
	}

	/**
	 * @param tempBAIndividual
	 */
	private void addHuiBA(Fish tempBAIndividual) {
		Fish tmpBAIndividual = new Fish();
		tmpBAIndividual.copyFish(tempBAIndividual);
		BitSet tmpBitSet;
		if (huiBA.size() != 0) {
			for (int i = 0; i < huiBA.size(); ++i) {
				tmpBitSet = (BitSet) (tmpBAIndividual.X.clone());
				tmpBitSet.xor(huiBA.get(i).X);
				if (tmpBitSet.cardinality() == 0) {
					return;
				}
			}
		}
		huiBA.add(tmpBAIndividual);
	}

	/**
	 * Roulette
	 * @return a list of doubles
	 */
	private List<Double> roulettePercentHUIBA() {
		double sum = 0;
		double tempsum = 0;
		double percent = 0.0;
		List<Double> percentHUIBA = new ArrayList<Double>();
		for (int i = 0; i < huiBA.size(); ++i) {
			sum += huiBA.get(i).fitness;
		}
		for (int i = 0; i < huiBA.size(); ++i) {
			tempsum += huiBA.get(i).fitness;
			percent = tempsum / sum;
			percentHUIBA.add(percent);
		}
		return percentHUIBA;

	}

	/**
	 * Roulette select HUIBA
	 * @param percentage a list of percentage
	 * @return an integer
	 */
	private int rouletteSelectHUIBA(List<Double> percentage) {
		int i, temp = 0;
		double randNum;
		randNum = Math.random();
		for (i = 0; i < percentage.size(); i++) {
			if (i == 0) {
				if ((randNum >= 0) && (randNum <= percentage.get(0))) {
					temp = 0;
					break;
				}
			} else if ((randNum > percentage.get(i - 1)) && (randNum <= percentage.get(i))) {
				temp = i;
				break;
			}
		}
		return temp;
	}

	/**
	 * Method to inseret tempParticle to huiSets
	 * @param tempParticle the particle to be inserted
	 */
	private void insert(Fish tempParticle) {
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
	 * Method to initial percentage
	 * 
	 * @return percentage
	 */
	private List<Double> roulettePercent() {
		int i;
		double sum = 0, tempSum = 0;
		double tempPercent;
		// calculate the sum of twu value of each 1-HTWUIs
		for (i = 0; i < twuPattern.size(); i++) {
			sum = sum + mapItemToTWU.get(twuPattern.get(i));
		}
		// calculate the portation of twu value of each item in sum
		for (i = 0; i < twuPattern.size(); i++) {
			tempSum = tempSum + mapItemToTWU.get(twuPattern.get(i));
			tempPercent = tempSum / (sum + 0.0);
			percentage.add(tempPercent);
		}
		return percentage;
	}

	/**
	 * Method to ensure the posotion of 1 in particle use roulette selection
	 * 
	 * @param percentage the portation of twu value of each 1-HTWUIs in sum of twu
	 *                   value
	 * @return the position of 1
	 */
	private int rouletteSelect(List<Double> percentage) {
		int i, temp = 0;
		double randNum;
		randNum = Math.random();
		for (i = 0; i < percentage.size(); i++) {
			if (i == 0) {
				if ((randNum >= 0) && (randNum <= percentage.get(0))) {
					temp = 0;
					break;
				}
			} else if ((randNum > percentage.get(i - 1)) && (randNum <= percentage.get(i))) {
				temp = i;
				break;
			}
		}
		return temp;
	}

	/**
	 * Method to write a high utility itemset to the output file.
	 * 
	 * @throws IOException
	 */
	private void writeOut() throws IOException {
		// Create a string buffer
		StringBuilder buffer = new StringBuilder();
		// append the prefix
		for (int i = 0; i < huiSets.size(); i++) {
			buffer.append(huiSets.get(i).itemset);
			// append the utility value
			buffer.append("#UTIL: ");
			buffer.append(huiSets.get(i).fitness);
			buffer.append(System.lineSeparator());
		}
		// write to file
		writer.write(buffer.toString());
		writer.newLine();
	}

	/**
	 * Method to check the memory usage and keep the maximum memory usage.
	 */
	private void checkMemory() {
		// get the current memory usage
		double currentMemory = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024d / 1024d;
		// if higher than the maximum until now
		if (currentMemory > maxMemory) {
			// replace the maximum with the current memory usage
			maxMemory = currentMemory;
		}
	}

	/**
	 * Print statistics about the latest execution to System.out.
	 */
	public void printStats() {
		System.out.println("=============  HUIM-AF ALGORITHM v.2.50 - STATS =============");
		System.out.println(" Total time ~ " + (endTimestamp - startTimestamp) + " ms");
		System.out.println(" Memory ~ " + maxMemory + " MB");
		System.out.println(" High-utility itemsets count : " + huiSets.size());
		System.out.println("===================================================");
	}

}
