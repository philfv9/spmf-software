package ca.pfv.spmf.algorithms.frequentpatterns.HUIM_SPSO;

/*
 * Copyright (c) 2020  Wei Song, Junya Li
 * 
 * This file is part of the SPMF DATA MINING SOFTWARE *
 * (http://www.philippe-fournier-viger.com/spmf).
 * 
 * SPMF is free software: you can redistribute it and/or modify it under the *
 * terms of the GNU General Public License as published by the Free Software *
 * Foundation, either version 3 of the License, or (at your option) any later *
 * version. *
 * 
 * SPMF is distributed in the hope that it will be useful, but WITHOUT ANY *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * * SPMF. If not, see <http://www.gnu.org/licenses/>.
 */
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
 * This is an implementation of the "HUIM-SPSO algorithm" for High-Utility Itemsets Mining
 * as described in the conference paper :
 * 
 * Discovering High Utility Itemsets Using Set-Based Particle Swarm Optimization
 * 
 * @author Wei Song, Junya Li
 */

public class AlgoHUIM_SPSO {
	// variable for statistics
	/** the maximum memory usage */
	double maxMemory = 0;
	/** the time the algorithm started */
	long startTimestamp = 0; 
	/** the time the algorithm terminated */
	long endTimestamp = 0; 
	/**  the size of populations */
	final int pop_size = 20;
	/** the iterations of algorithms */
	final int iterations = 10000;
	/** the iterations of algorithm */
	int transactionCount = 0;
	/** the parameter used in BPSO algorithm */
	final double w = 0.9;

	/**  create a map to store the TWU of each item */
	Map<Integer, Integer> mapItemToTWU;
	/** Used  mapItemToTWU0 to remove items whose TWU is smaller than minUtil */
	Map<Integer, Integer> mapItemToTWU0;

	/**  the items which has twu value more than minUtil */
	List<Integer> twuPattern;

	/**  writer to write the output file */
	BufferedWriter writer = null; 

	/** this class represent an item and its utility in a transaction */
	class Pair {
		int item = 0;
		int utility = 0;
	}

	/** this class represent the particles **/
	class Particle {
		/** the particle */
		BitSet X;
		/** fitness value of particle */
		int fitness;

		public Particle() {
			X = new BitSet(twuPattern.size());
		}

		public Particle(int length) {
			X = new BitSet(length);
		}

		// deepcopy particle
		public void copyParticle(Particle particle1) {
			this.X = (BitSet) particle1.X.clone();
			this.fitness = particle1.fitness;
		}

		// calculate fitness of itemset
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
					// rutil = rutil + database.get(p).get(q-1).rutil;
					fitness = fitness + sum;
				}
			}
			// tempGroup.rutil = rutil + fitness;
			this.fitness = fitness;
			// System.out.println("fitness:"+tempGroup.fitness+"
			// "+"rutil:"+tempGroup.rutil);
		}
	}

    /** this class represent the velocity of each particle	*/													
	class VelocityR {
		Particle XtoFitness;
		List<Double> rand;

		public VelocityR() {
			rand = new ArrayList<Double>();
			XtoFitness = new Particle();
		}

		public VelocityR(int length) {
			rand = new ArrayList<Double>();
			XtoFitness = new Particle();
			for (int i = 0; i < length; i++) {
				rand.add(i, 0.0);
			}
		}
	}

	class HUI {
		String itemset;
		int fitness;

		public HUI(String itemset, int fitness) {
			super();
			this.itemset = itemset;
			this.fitness = fitness;
		}

	}

	/** Bitmap Item Information Representation */
	class Item {
		int item;
		BitSet TIDS;

		public Item() {
			TIDS = new BitSet(transactionCount);
		}

		public Item(int item) {
			TIDS = new BitSet(transactionCount);
			this.item = item;
		}
	}

	/** the gBest particle in populations */
	Particle gBest;
	/** each pBest particle in populations, */
	List<Particle> pBest = new ArrayList<Particle>();
	/** populations*/
	List<Particle> population = new ArrayList<Particle>();
	/** the set of HUIs */
	List<HUI> huiSets = new ArrayList<HUI>();
	/** the portation of twu value of each 1-HTWUIs in sum of twu value */
	List<Double> percentage = new ArrayList<Double>();

	/** Create a list to store database */
	List<List<Pair>> database = new ArrayList<List<Pair>>();

	/** bitmap database representation */
	List<Item> Items;

	List<Particle> huiBA = new ArrayList<Particle>();

	List<Double> percentHUIBA;
	/** the velocity of each particle */
	List<VelocityR> V = new ArrayList<VelocityR>();
	/** The average bit edit distance  */
	List<Double> aveEd = new ArrayList<Double>();
	/** The maximal bit edit distance  */
	List<Integer> maxEd = new ArrayList<Integer>();

	/**
	 * Default constructor
	 */
	public AlgoHUIM_SPSO() {
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

//		System.out.println("twuPattern:" + twuPattern.size());
//		System.out.println(twuPattern);

		Items = new ArrayList<Item>();

		for (Integer tempitem : twuPattern) {
			Items.add(new Item(tempitem.intValue()));
		}
		// scan database to create bitmap
		for (int i = 0; i < database.size(); ++i) {// use i to scan every transaction 
			for (int j = 0; j < Items.size(); ++j) {
				for (int k = 0; k < database.get(i).size(); ++k) {
					if (Items.get(j).item == database.get(i).get(k).item) {
						Items.get(j).TIDS.set(i);
					}
				}
			}
		}
		// init pBest
		for (int i = 0; i < pop_size; ++i) {
			pBest.add(new Particle(twuPattern.size()));
		}
		// global Best
		gBest = new Particle(twuPattern.size());

		// check the memory usage
		checkMemory();
		// Mine the database recursively
		if (twuPattern.size() > 0) {
			// initial population
			generatePop(minUtility);

			for (int i = 0; i <= iterations; i++) {
				List<Integer> distance = new ArrayList<Integer>();// Create a list to store The bit edit distance between particles
				double temp = 0.0;
				int t = 0;
				for (int p = 0; p < pop_size; p++) {
					for (int q = 1; q < pop_size; q++) {
						distance.add(minEditDistance(population.get(p).X, population.get(q).X, twuPattern));
						temp = temp + distance.get(t);
						t++;//T is the length of distance
					}
				}
				Collections.sort(distance);
				maxEd.add(distance.get(t - 1));
				aveEd.add(temp / t);

				// update population and HUIset
				update(minUtility);
				if (huiBA.size() != 0) {
					percentHUIBA = roulettePercentHUIBA();
					int num = rouletteSelectHUIBA(percentHUIBA);
					gBest.copyParticle(huiBA.get(num));
				}
//				if (i % 1000 == 0) {
//					System.out.print(i + "-update end. HUIs No. is " + huiSets.size()+"  ");
//					System.out.print("aveEd=" + aveEd.get(i) + " ");
//					System.out.println("maxEd=" + maxEd.get(i));
//				}				
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
	private void generatePop(int minUtility)
	{
		int i, j, k, temp;

		List<Integer> transList;// Create a list to store TID
		// initial percentage according to the twu value of 1-HTWUIs
		percentage = roulettePercent();

//		System.out.println(percentage);

		for (i = 0; i < pop_size; i++) {
			// initial particles
			Particle tempParticle = new Particle(twuPattern.size());
			j = 0;
			// k is the count of 1 in particle
			k = (int) (Math.random() * twuPattern.size());
			while (j < k) {
				// roulette select the position of 1 in population
				temp = rouletteSelect(percentage);
				if (!tempParticle.X.get(temp)) {
					j++;
					tempParticle.X.set(temp);
				}
			}
			// calculate the fitness of each particle
			transList = new ArrayList<Integer>();
			isRBAIndividual(tempParticle, transList);
			tempParticle.calculateFitness(k, transList);

			// insert particle into population
			population.add(i, tempParticle);
			// initial pBest
			pBest.get(i).copyParticle(tempParticle);
			// update huiSets
			if (population.get(i).fitness >= minUtility) {
				insert(population.get(i));
				addHuiBA(population.get(i));
			}
			// update gBest
			if (i == 0) {
				gBest.copyParticle(pBest.get(i));
			} else {
				if (pBest.get(i).fitness > gBest.fitness) {
					gBest.copyParticle(pBest.get(i));
				}
			}
			// update velocity
			VelocityR tempV = new VelocityR(twuPattern.size());
			tempV.XtoFitness.copyParticle(tempParticle);
			for (int m = 0; m < twuPattern.size(); m++) {
				if (tempV.XtoFitness.X.get(m)) {
					tempV.rand.set(m, w * Math.random());
				}
			}
			V.add(i, tempV);
		}
	}
	
	/**
	 * Methos to calculate the bit edit distance between particles
	 * @param particle1
	 * @param particle2
	 * @param lit
	 * @return the bit edit distance between particle1 and  particle2
	 */
	public static int minEditDistance(BitSet particle1, BitSet particle2, List<Integer> lit) {
		if (particle1.length() == 0 || particle2.length() == 0) {
			return particle1.length() == 0 ? particle2.length() : particle1.length();
		}
		// initial arr
		int[][] arr = new int[lit.size() + 1][lit.size() + 1];
		for (int i = 0; i <= lit.size(); i++) {
			arr[i][0] = i;
		}
		for (int j = 0; j <= lit.size(); j++) {
			arr[0][j] = j;
		}
		for (int i = 1; i <= lit.size(); i++) {
			for (int j = 1; j <= lit.size(); j++) {
				if (particle1.get(i - 1) == particle2.get(j - 1)) {
					arr[i][j] = arr[i - 1][j - 1];
				} else {
					int replace = arr[i - 1][j - 1] + 1;
					int insert = arr[i - 1][j] + 1;
					int delete = arr[i][j - 1] + 1;
					int min = Math.min(replace, insert);
					min = Math.min(min, delete);
					arr[i][j] = min;
				}
			}
		}
		return arr[lit.size()][lit.size()];
	}

	/**
	 * It is used to get the collection of transactions in which the itemset resides. 
	 * If the itemset itself is unreasonable, it is automatically fine-tuned during the calculation.
	 * Make it reasonable and get the set of transactions in which it is located
	 * @param tempBAIndividual
	 * @param list
	 * @return
	 */ 
	public boolean isRBAIndividual(Particle tempBAIndividual, List<Integer> list) {
		List<Integer> templist = new ArrayList<Integer>();
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
	 * Methos to update particle, velocity, pBest and gBest
	 * 
	 * @param minUtility
	 */
	private void update(int minUtility) {
		Particle tempP = new Particle();
		Particle tempP1 = new Particle();
		Particle tempP3 = new Particle();
		Particle tempP2 = new Particle();
		List<Integer> list = new ArrayList<Integer>();
		List<Integer> list1 = new ArrayList<Integer>();
		double r1, r2, r3;
		int i, j, k, num, changeBit;
		r1 = Math.random();
		r2 = Math.random();
		for (i = 0; i < pop_size; i++) {
			// update velocity
			tempP1 = difSet(pBest.get(i), population.get(i));
			for (j = 0; j < twuPattern.size(); j++) {
				if (tempP1.X.get(j)) {
					V.get(i).XtoFitness.X.set(j);
					if (r1 > V.get(i).rand.get(j)) {
						V.get(i).rand.set(j, r1);
					}
				}
			}
			tempP3 = difSet(gBest, population.get(i));
			for (j = 0; j < twuPattern.size(); j++) {
				if (tempP3.X.get(j)) {
					V.get(i).XtoFitness.X.set(j);
					if (r2 > V.get(i).rand.get(j)) {
						V.get(i).rand.set(j, r2);
					}
				}
			}
			r3 = Math.random();
			tempP2.copyParticle(population.get(i));
			for (j = 0; j < twuPattern.size(); j++) {
				if (V.get(i).rand.get(j) < r3) {
					V.get(i).XtoFitness.X.clear(j);
					V.get(i).rand.set(j, 0.0);
				}
				if (V.get(i).XtoFitness.X.get(j)) {
					list.add(j);
				}
			}
			// update  particle
			num = (int) (twuPattern.size() * Math.random()) + 1;
			tempP.copyParticle(population.get(i));
			tempP.X.or(V.get(i).XtoFitness.X);
			for (j = 0; j < twuPattern.size(); j++) {
				if (tempP.X.get(j)) {
					list1.add(j);
				}
			}
			population.get(i).X.clear();
			if (num <= list.size()) {
				for (j = 0; j < num;) {
					changeBit = (int) (list.size() * Math.random());
					if (population.get(i).X.get(list.get(changeBit)) == false) {
						population.get(i).X.set(list.get(changeBit));
						j++;
					}
				}
			} else if (num <= list1.size()) {
				population.get(i).copyParticle(V.get(i).XtoFitness);
				for (int l = 0; l < num - list.size();) {
					changeBit = (int) (twuPattern.size() * Math.random());
					if ((tempP2.X.get(changeBit) == true) && (V.get(i).XtoFitness.X.get(changeBit) == false)) {
						population.get(i).X.set(changeBit);
						tempP2.X.set(changeBit, false);
						l++;
					}
				}
			} else {
				population.get(i).copyParticle(tempP);
				for (int l = 0; l < num - list1.size();) {
					changeBit = (int) (twuPattern.size() * Math.random());
					if ((population.get(i).X.get(changeBit) == true)) {
						continue;
					} else {
						population.get(i).X.set(changeBit);
					}
					l++;
				}
			}
			List<Integer> disList;
			List<Integer> transList;
			k = population.get(i).X.cardinality();
			// calculate the fitness of particle
			transList = new ArrayList<Integer>();
			isRBAIndividual(population.get(i), transList);
			population.get(i).calculateFitness(k, transList);

			// update pBest & gBest
			if (population.get(i).fitness > pBest.get(i).fitness) {
				pBest.get(i).copyParticle(population.get(i));
				if (pBest.get(i).fitness > gBest.fitness) {
					gBest.copyParticle(pBest.get(i));
				}
			}
			// update huiSets
			if (population.get(i).fitness >= minUtility) {
				insert(population.get(i));
				addHuiBA(population.get(i));
			}
			list.clear();
			list1.clear();
		}
	}

    /**
	 * Method to calculate the position difference:Position ? Position:
	 */
	private Particle difSet(Particle temp0, Particle temp1) {
		Particle tempBitSet = new Particle();
		Particle tmpBitSet = new Particle();
		tempBitSet.copyParticle(temp0);
		tmpBitSet.copyParticle(temp0);
		tempBitSet.X.and(temp1.X);
		for (int i = 0; i < tempBitSet.X.length(); ++i) {
			if (tempBitSet.X.get(i)) {
				tmpBitSet.X.clear(i);
			}
		}
		return tmpBitSet;
	}

	/**
	 *  Method to add hui Particle to huiBA
	 * @param tempBAIndividual
	 */
	private void addHuiBA(Particle tempBAIndividual) {
		Particle tmpBAIndividual = new Particle();
		tmpBAIndividual.copyParticle(tempBAIndividual);
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
	 * Method to initial percentage
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
		System.out.println("=============  HUIM-SPSO ALGORITHM v.2.48 - STATS =============");
		System.out.println(" Total time ~ " + (endTimestamp - startTimestamp) + " ms");
		System.out.println(" Memory ~ " + maxMemory + " MB");
		System.out.println(" High-utility itemsets count : " + huiSets.size());
		System.out.println("===================================================");
	}

}
