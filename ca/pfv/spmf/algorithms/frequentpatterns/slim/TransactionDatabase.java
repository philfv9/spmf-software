package ca.pfv.spmf.algorithms.frequentpatterns.slim;

/* Copyright (c) 2008-2013 Philippe Fournier-Viger
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
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This class represents a transaction database (a.k.a. binary context),
 * implemented as a list of arrays of integers. It can read a transaction
 * database directly from a file. See the ca.pfv.spmf.test folder for some
 * examples of files that can be read.
 * 
 * @author Philipe-Fournier-Viger
 */
public class TransactionDatabase {

	// the list of transactions
	private final List<Transaction> transactions = new ArrayList<Transaction>();

	/**
	 * Method to load a file containing a transaction database into memory
	 * 
	 * @param path the path of the file
	 * @throws IOException exception if error reading the file
	 */
	public void loadFile(String path) throws IOException {
		String thisLine; // variable to read each line
		BufferedReader myInput = null; // object to read the file
		try {
			FileInputStream fin = new FileInputStream(new File(path));
			myInput = new BufferedReader(new InputStreamReader(fin));
			// for each line
			while ((thisLine = myInput.readLine()) != null) {
				// if the line is not a comment, is not empty or is not other
				// kind of metadata
				if (thisLine.isEmpty() == false && thisLine.charAt(0) != '#' && thisLine.charAt(0) != '%'
						&& thisLine.charAt(0) != '@') {
					// split the line according to spaces and then
					// call "addTransaction" to process this line.
					addTransaction(thisLine.split(" "));
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (myInput != null) {
				myInput.close();
			}
		}
	}

	/**
	 * This method process a line from a file that is read.
	 * 
	 * @param tokens the items contained in this line
	 */
	private void addTransaction(String itemsString[]) {
		// create an empty transaction
		int[] itemset = new int[itemsString.length];
		// for each item in this line
		for (int i = 0; i < itemsString.length; i++) {
			// convert from string to int
			itemset[i] = Integer.parseInt(itemsString[i]);
		}

		// Because the items may not be sorted in a transaction, we sort them by
		// lexicographical order
		Arrays.sort(itemset);

		// add the transactions to the list of all transactions in this database.
		transactions.add(new Transaction(itemset));
	}

	/**
	 * Method to print the content of the transaction database to the console.
	 */
	public void printDatabase() {
		System.out.println("===================  TRANSACTION DATABASE ===================");
		int count = 0;
		// for each transaction
		for (Transaction transaction : transactions) { // pour chaque objet
			System.out.print(count + ":  ");
			print(transaction.items); // print the transaction
			count++;
		}
	}

	/**
	 * Method to print a transaction to System.out.
	 * 
	 * @param itemset a transaction
	 */
	private void print(int[] itemset) {
		StringBuilder r = new StringBuilder();
		// for each item in this transaction
		for (int item : itemset) {
			// append the item to the StringBuilder
			r.append(item);
			r.append(' ');
		}
		System.out.println(r); // print to System.out
	}

	/**
	 * Get the number of transactions in this transaction database.
	 * 
	 * @return the number of transactions.
	 */
	public int size() {
		return transactions.size();
	}

	/**
	 * Get the list of transactions in this database
	 * 
	 * @return A list of transactions (a transaction is a list of Integer).
	 */
	public List<Transaction> getTransactions() {
		return transactions;
	}
}
