package ca.pfv.spmf.algorithms.frequentpatterns.HUIM_SA_HC;

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

/* This file is copyright (c) Saqib Nawaz, Philippe Fournier-Viger et al.
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
*/

/**
 *This is an implementation of the high utility itemset mining algorithm
 * based on Simulated annealing. 
 * 
 * More details can be found in this paper: <br/><br/>
 * 	Nawaz, M.S., Fournier-Viger, P., Yun, U., Wu, Y., Song, W. (2021). 
 * Mining High Utility Itemsets with Hill Climbing and Simulated Annealing. 
 * ACM Transactions on Management Information Systems
 * 
 * @author Saqib Nawaz
 */
public class AlgoHUIMSA {
	/** the maximum memory usage */
	double maxMemory = 0; 
	/** the time the algorithm started */
	long startTimestamp = 0;
	/**  the time the algorithm terminated */
	long endTimestamp = 0; 
	/** the total num of transactions */
	int transactionCount = 0;
	
	/** the size of population */
	final int pop_size = 30;
	// final int max_iter = 2000;// maximum iterations
	/** temperature */
	double temprature = 100000;
	/** minimum temperature */
	double min_temp = .00001;
	/** alpha */
	double alpha = 0.9993;

	Map<Integer, Integer> mapItemToTWU;
	Map<Integer, Integer> mapItemToTWU0;
	List<Integer> twuPattern;// the items which has twu value more than minUtil

	BufferedWriter writer = null; // writer to write the output file

	// this class represent an item and its utility in a transaction
	class Pair {
		int item = 0;
		int utility = 0;
	}

	// this class represent the chromosome
	class ChroNode implements Comparable {
		BitSet chromosome;// the chromosome
		int fitness;// fitness value of chromosome
		double rfitness;// select chromosomes to crossover
		int rank;// the rank of chromosome's fitness in population

		public ChroNode() {
			chromosome = new BitSet();
		}

		public ChroNode(int length) {
			chromosome = new BitSet(length);
		}

		// deepcopy ChroNode
		public void deepcopy(ChroNode tempChroNode) {
			chromosome = (BitSet) tempChroNode.chromosome.clone();
			fitness = tempChroNode.fitness;
			rfitness = tempChroNode.rfitness;

			rank = tempChroNode.rank;
		}

		// calculate fitness of itemset
		public void calculateFitness(int k, List<Integer> templist) {
			if (k == 0)
				return;
			int i, j, p, q, temp, m;

			int sum, fitness = 0;
			for (m = 0; m < templist.size(); m++) { // m�������񼯺�
				p = templist.get(m).intValue(); // ��p����database�е�transaction
				i = 0;
				j = 0;
				q = 0;
				temp = 0;
				sum = 0;
				// use j to scan bit=1 in tempGroup.X, use q to scan every transaction,
				// use i to scan transaction.X
				while (q < database.get(p).size() && i < this.chromosome.length()) {
					if (this.chromosome.get(i)) {
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
			// TODO Auto-generated method stub
			return -(fitness - ((ChroNode) o).fitness);
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

	// use Item to create bitmap
	class Item {
		int item;
		BitSet TIDS;

		public Item() {
			TIDS = new BitSet(database.size());
		}

		public Item(int item) {
			TIDS = new BitSet(database.size());
			this.item = item;
		}
	}

	List<Double> percentHUIChroNode;
	List<ChroNode> huiBA = new ArrayList<ChroNode>();
	List<ChroNode> population = new ArrayList<ChroNode>();// populations
	List<ChroNode> subPopulation = new ArrayList<ChroNode>();// son of populations
	List<HUI> huiSets = new ArrayList<HUI>();// the set of HUIs chromosome
	// Create a list to store database
	List<List<Pair>> database = new ArrayList<List<Pair>>();
	List<Double> percentage = new ArrayList<Double>();// the portation of twu value of each
														// 1-HTWUIs in sum of twu value
	List<Item> Items;// bitmap database representation

	/**
	 * Default constructor
	 */
	public AlgoHUIMSA() {
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

		Items = new ArrayList<Item>();

		for (Integer tempitem : twuPattern) {
			Items.add(new Item(tempitem.intValue()));
		}

		// scan database to create bitmap
		for (int i = 0; i < database.size(); ++i) {
			for (int j = 0; j < Items.size(); ++j) {
				for (int k = 0; k < database.get(i).size(); ++k) {
					if (Items.get(j).item == database.get(i).get(k).item) {
						Items.get(j).TIDS.set(i);
					}
				}
			}
		}
		// check the memory usage
		checkMemory();
		int i = 0;
		// Mine the database recursively
		if (twuPattern.size() > 0) {
			int num1, num2, num3, num4, tempD, tempA, tempB, tempC;

			pop_Init(minUtility);
			double T = temprature;
			double T_min = min_temp;

			while (T > T_min) {
				subPopulation = neighbor(minUtility);
				i++;
				T = T * alpha;
				if (huiBA.size() != 0) {
					percentHUIChroNode = roulettePercentHUIBA();
					num1 = rouletteSelectHUIBA(percentHUIChroNode);
					num2 = rouletteSelectHUIBA(percentHUIChroNode);
					tempA = (int) (Math.random() * pop_size);
					tempB = (int) (Math.random() * pop_size);
					population.get(tempA).deepcopy(huiBA.get(num1));
					population.get(tempB).deepcopy(huiBA.get(num2));
					num3 = rouletteSelectHUIBA(percentHUIChroNode);
					tempC = (int) (Math.random() * pop_size);
					population.get(tempC).deepcopy(huiBA.get(num3));
					// num4 = rouletteSelectHUIBA(percentHUIChroNode);
					// tempD = (int)(Math.random()*pop_size);
					// population.get(tempC).deepcopy(huiBA.get(num4));
				}
				calculateRfitness();

				subPopulation.addAll(population);
				rankData(subPopulation);
				for (int j = 0; j < population.size(); j++) {
					population.set(j, subPopulation.get(j));
				}
				subPopulation.clear();
//				if (i % 200 == 0) {
//					System.out.println(i + "-update end. HUIs No. is " + huiSets.size());
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

	private void pop_Init(int minUtility)//
	{
		int i = 0, j, k, temp;
		List<Integer> transList;
		// initial percentage according to the twu value of 1-HTWUIs
		percentage = roulettePercent();

		while (i < pop_size) {
			// generate a chromosome
			ChroNode tempNode = new ChroNode(twuPattern.size());
			// initial chromosome
			j = 0;
			// k is the count of 1 in particle
			k = (int) (Math.random() * twuPattern.size());

			while (j < k) {
				// roulette select the position of 1 in population
				temp = select(percentage);
				if (!tempNode.chromosome.get(temp)) {
					j++;
					tempNode.chromosome.set(temp);
				}
			}
			// calculate the fitenss of chromosome
			transList = new ArrayList<Integer>();
			pev_Check(tempNode, transList);
			tempNode.calculateFitness(k, transList);

			tempNode.rank = 0;
			population.add(tempNode);
			if (tempNode.fitness >= minUtility && tempNode.chromosome.cardinality() > 0) {
				insert(tempNode);
				addHuiBA(tempNode);
			}
			i++;
		}
	}

	/**
	 * check itemset is promising or unpromising
	 * 
	 * @param tempBAIndividual
	 * @param list
	 * @return
	 */

	public boolean pev_Check(ChroNode tempBAIndividual, List<Integer> list) {
		List<Integer> templist = new ArrayList<Integer>();// �惦�����0��λ��
		for (int i = 0; i < tempBAIndividual.chromosome.length(); ++i) {
			if (tempBAIndividual.chromosome.get(i)) {
				templist.add(i);
			}
		}
		if (templist.size() == 0) {
			return false;
		}
		BitSet tempBitSet = new BitSet(database.size());
		BitSet midBitSet = new BitSet(database.size());
		tempBitSet = (BitSet) Items.get(templist.get(0).intValue()).TIDS.clone();
		midBitSet = (BitSet) tempBitSet.clone();// ��¼�м���

		// item��λͼ���������������ʹ��itemset�������item��������itemset��ȥ����item
		for (int i = 1; i < templist.size(); ++i) {
			tempBitSet.and(Items.get(templist.get(i).intValue()).TIDS);
			if (tempBitSet.cardinality() != 0) {
				midBitSet = (BitSet) tempBitSet.clone();
			} else {
				tempBitSet = (BitSet) midBitSet.clone();
				tempBAIndividual.chromosome.clear(templist.get(i).intValue());
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

	private void rankData(List<ChroNode> tempPop) {
		int i;
		Collections.sort(tempPop);

		for (i = 0; i < tempPop.size() - 1; i++) {
			tempPop.get(i).rank = i + 1;
		}
	}

	/**
	 * 
	 * @param minUtility minimum utility threshold
	 * @return
	 */
	private List<ChroNode> neighbor(int minUtility) {
		// double pm, rankNum;// pm is ranked mutation rate
		List<Integer> transList;
		List<Integer> abc;
		for (int i = 0; i < pop_size; i++) {
			int temp = (int) (Math.random() * twuPattern.size());
			if (population.get(i).chromosome.get(temp)) {
				population.get(i).chromosome.clear(temp);
			} else {
				population.get(i).chromosome.set(temp);
			}
			// get the number of 1 in chromosome
			int k = population.get(i).chromosome.cardinality();
			// calculate the fitness of chromosome
			transList = new ArrayList<Integer>();
			pev_Check(population.get(i), transList);
			population.get(i).calculateFitness(k, transList);
			// insert chromosome has higher utility into huiSets
			if (population.get(i).fitness >= minUtility && population.get(i).chromosome.cardinality() > 0) {
				insert(population.get(i));
				addHuiBA(population.get(i));
				// System.out.println(population.get(i));
			} // else
			{
				double ar;
				ar = acceptance_probability(temprature);
				// Math.exp(temprature/(1+temprature));
				// System.out.println(ar);
				if (ar > range(2.8, 3.2))
				// (Math.random() * (3.8 - 3.2)) + 3.));
				{
					insert(population.get(i));
					addHuiBA(population.get(i));
				}
			}
		}
		return subPopulation;
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

	/**
	 * Method to roulette select chromosome to replace the two ChroNode of
	 * population
	 * 
	 * @param percentage
	 * @return
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

	public void calculateRfitness() {
		int sum = 0;
		int temp = 0;
		// �ϼ���Ӧֵ
		for (int i = 0; i < population.size(); ++i) {
			sum = sum + population.get(i).fitness;
		}
		// ��������
		for (int i = 0; i < population.size(); ++i) {
			temp = temp + population.get(i).fitness;
			population.get(i).rfitness = temp / (sum + 0.0);
		}
	}

	/**
	 * xor(itemset1,itemset2)
	 * 
	 * @param gBest
	 * @param tempBAIndividual
	 * @return
	 */
	/**
	 * /** Method to initial percentage
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
	 * Method to roulette select chromosome
	 * 
	 * @param percentage the portation of twu value of each 1-HTWUIs in sum of twu
	 *                   value
	 * @return the position of 1
	 */
	private int select(List<Double> percentage) {
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
	 * Method to inseret tempChroNode to huiSets
	 * 
	 * @param tempChroNode the chromosome to be inserted
	 */
	private void insert(ChroNode tempChroNode) {
		int i;
		StringBuilder temp = new StringBuilder();
		for (i = 0; i < twuPattern.size(); i++) {
			if (tempChroNode.chromosome.get(i)) {
				temp.append(twuPattern.get(i));
				temp.append(' ');
			}
		}
		// huiSets is null
		if (huiSets.size() == 0) {
			huiSets.add(new HUI(temp.toString(), tempChroNode.fitness));
		} else {
			// huiSets is not null, judge whether exist an itemset in huiSets
			// same with tempChroNode
			for (i = 0; i < huiSets.size(); i++) {
				if (temp.toString().equals(huiSets.get(i).itemset)) {
					break;
				}
			}
			// if not exist same itemset in huiSets with tempChroNode,insert it
			// into huiSets
			if (i == huiSets.size())
				huiSets.add(new HUI(temp.toString(), tempChroNode.fitness));
		}
	}

	/**
	 * add hui ChroNode to huiBA
	 * 
	 * @param tempBAIndividual
	 */
	private void addHuiBA(ChroNode tempBAIndividual) {
		ChroNode tmpBAIndividual = new ChroNode();
		tmpBAIndividual.deepcopy(tempBAIndividual);
		BitSet tmpBitSet;
		if (huiBA.size() != 0) {
			for (int i = 0; i < huiBA.size(); ++i) {
				tmpBitSet = (BitSet) (tmpBAIndividual.chromosome.clone());
				tmpBitSet.xor(huiBA.get(i).chromosome);
				if (tmpBitSet.cardinality() == 0) {
					return;
				}
			}
		}
		huiBA.add(tmpBAIndividual);
	}

	/**
	 * Method to calculate the range for comparison with AR
	 * 
	 * @param max and min range values * @return random number in beteen range
	 */
	private double range(double max, double min) {
		double range = ((Math.random() * (max - min)) + min);
		return range;
	}

	/**
	 * Method to calculate the acceptance rate
	 * 
	 * @param temprature * @return acceptance rate
	 */
	private double acceptance_probability(double temp) {
		double ar = Math.exp(temp / (1 + temp));
		return ar;
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
			if (i != huiSets.size() - 1) {
				buffer.append(System.lineSeparator());
			}
		}
		// write to file
		writer.write(buffer.toString());
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
		System.out.println("=============  HUIM-SA ALGORITHM - STATS =============");
		System.out.println(" Total time ~ " + (endTimestamp - startTimestamp) + " ms");
		System.out.println(" Memory ~ " + maxMemory + " MB");
		System.out.println(" High-utility itemsets count : " + huiSets.size());
		System.out.println("===================================================");
	}
}
