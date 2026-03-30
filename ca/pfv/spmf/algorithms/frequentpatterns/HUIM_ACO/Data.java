package ca.pfv.spmf.algorithms.frequentpatterns.HUIM_ACO;

/*
 * Copyright (c) 2020 Wei Song, Jiakai Nan
 * 
 * This file is part of the SPMF DATA MINING SOFTWARE *
 * (http://www.philippe-fournier-viger.com/spmf).
 * 
 * 
 * SPMF is free software: you can redistribute it and/or modify it under the *
 * terms of the GNU General Public License as published by the Free Software *
 * Foundation, either version 3 of the License, or (at your option) any later *
 * version. *
 * 
 * SPMF is distributed in the hope that it will be useful, but WITHOUT ANY *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * *
 * 
 * You should have received a copy of the GNU General Public License along with
 * * SPMF. If not, see <http://www.gnu.org/licenses/>..
 * 
 */
/** This class represnets the data from a transaction database, as used by the HUIM-ACO algorithm
 * 
 * @author Jiakai Nan, Wei Song
 * @see AlgoHUIMMACO
 *
 */

import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.*;

public class Data {
	/** Map of item to the TWU */
	private Map<Integer, Integer> itemAndTWU = null;
	/** the Pheromones betweenone one item to another item */
	private Integer[][] database;
	/** The transaction utility list */
	private Integer[] transactionUtilityList;
	/** The database high */
	private Integer databaseHigh;
	/** the items which has twu value more than minUtil */
	private List<Integer> HTWUs_1 = null;

	/**
	 * Constructor
	 */
	public Data() {
		this.itemAndTWU = new HashMap<Integer, Integer>();
	}

	/**
	 * Get the list of trnasaction utility
	 * 
	 * @return A list of utility values
	 */
	public Integer[] getTransactionUtilityList() {
		return transactionUtilityList;
	}

	/**
	 * Get the database high
	 * 
	 * @return an integer
	 */
	public Integer getDatabaseHigh() {
		return databaseHigh;
	}

	/**
	 * Get the map of items to their TWUs
	 * 
	 * @return a map with key: item value: TWU
	 */
	public Map<Integer, Integer> getItemAndTWU() {
		return itemAndTWU;
	}

	/**
	 * Get the datatabase
	 * 
	 * @return the database as a 2D array of integers
	 */
	public Integer[][] getDatabase() {
		return this.database;
	}

	/**
	 * Get the list of HTWUIS_1
	 * 
	 * @return the list of items
	 */
	public List<Integer> getHTWUs_1() {
		return HTWUs_1;
	}

	/**
	 * Sort the list of HTWUIs_1
	 */
	public void sortHTWUs_1() {
		Collections.sort(this.HTWUs_1);
	}

	/**
	 * Read the database to calculate the TWU values of items
	 * 
	 * @param input the input file path
	 * @throws IOException if error while reading the database
	 */
	public void calculateTwu(String input) throws IOException {
		// read the database from input
		LineNumberReader reader = null;
		// store each line of the database
		String thisLine = null;
		try {
			reader = new LineNumberReader(new FileReader(input));
			// for each transaction until the end of file
			while ((thisLine = reader.readLine()) != null) {

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

				// store

				// for each item,we add the transaction utility to its TWU
				for (int i = 0; i < items.length; i++) {
					// convert String item to Integer item
					Integer Item = Integer.parseInt(items[i]);
					// get the current TWU of that item
					Integer twu = this.itemAndTWU.get(Item);
					// add the transaction utility of the item to it's TWU
					twu = (twu == null) ? transactionUtility : twu + transactionUtility;
					this.itemAndTWU.put(Item, twu);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (reader != null) {
				reader.close();
			}
		}
	}

	/**
	 * Read the database to remove unpromising items (according to the TWU)
	 * 
	 * @param input       the input file path
	 * @param min_utility the minimum utility threshold
	 * @throws IOException if error while reading or writing the file
	 */
	public void removeLowTWU(String input, int min_utility) throws IOException {
		// read the database from input
		LineNumberReader reader = null;
		// store each line of the database
		String thisLine = null;
		this.HTWUs_1 = new ArrayList<Integer>(this.itemAndTWU.keySet());
//        int h = 0;
		try {
			reader = new LineNumberReader(new FileReader(input));
			for (this.databaseHigh = 0; (thisLine = reader.readLine()) != null; this.databaseHigh++) {

				if (thisLine.isEmpty() == true || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
						|| thisLine.charAt(0) == '@') {
					continue;
				}

				// split the line according to the separator
				String split[] = thisLine.split(":");
				// get the list of items
				String items[] = split[0].split(" ");
				// get the list of utility values corresponding to each item for that
				// transaction
				String utilitys[] = split[2].split(" ");

				// for each item in the transaction
//                System.out.println();
				for (int i = 0; i < items.length; i++) {
					// item and utility
					ItemUtility itemUtility = new ItemUtility(Integer.parseInt(items[i]),
							Integer.parseInt(utilitys[i]));
//                   System.out.print(" "+itemUtility.getItem()+" "+itemUtility.getUtility());
					// if the item has enough TWU
					if (itemAndTWU.get(itemUtility.getItem()) < min_utility) {
						HTWUs_1.remove(itemUtility.getItem());
					}
				}

				/*
				 * for(int u = 0; u < revisedTransaction.size(); u++){
				 * System.out.print("."+database.get(h).get(u).getUtility()); } h++;
				 */

			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (reader != null) {
				reader.close();
			}
		}
	}

	/**
	 * Read the database
	 * 
	 * @param input input file path
	 * @throws IOException exception if some error occurs while reading
	 */
	public void readDatabase(String input) throws IOException {
		this.database = new Integer[this.databaseHigh][HTWUs_1.size()];
		this.transactionUtilityList = new Integer[this.databaseHigh];
		// read the database from input
		LineNumberReader reader = null;
		// store each line of the database
		String thisLine = null;
		try {
			reader = new LineNumberReader(new FileReader(input));
			for (int transactionIndex = 0; (thisLine = reader.readLine()) != null; transactionIndex++) {

				if (thisLine.isEmpty() == true || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
						|| thisLine.charAt(0) == '@') {
					continue;
				}

				// split the line according to the separator
				String split[] = thisLine.split(":");
				// get the list of items
				String items[] = split[0].split(" ");
				// get the list of utility values corresponding to each item for that
				// transaction
				String utilitys[] = split[2].split(" ");

				this.transactionUtilityList[transactionIndex] = Integer.parseInt(split[1]);
				// store 1-itemsets having HTWU and its utility in the transaction
				List<ItemUtility> revisedTransaction = new ArrayList<ItemUtility>();
				// for each item in the transaction

				for (int i = 0; i < items.length; i++) {

					if (HTWUs_1.contains(Integer.parseInt(items[i]))) {
						/*
						 * System.out.print(Integer.parseInt(items[i])+" ");
						 * System.out.print(HTWUs_1.indexOf(Integer.parseInt(items[i]))+" ");
						 * System.out.println();
						 */
						database[transactionIndex][HTWUs_1.indexOf(Integer.parseInt(items[i]))] = Integer
								.parseInt(utilitys[i]);
					} else {
						this.transactionUtilityList[transactionIndex] -= Integer.parseInt(utilitys[i]);
					}

				}

				/*
				 * for(int u = 0; u < revisedTransaction.size(); u++){
				 * System.out.print("."+database.get(h).get(u).getUtility()); } h++;
				 */

			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (reader != null) {
				reader.close();
			}
		}
	}

}
