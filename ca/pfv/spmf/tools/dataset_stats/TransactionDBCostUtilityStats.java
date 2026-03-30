package ca.pfv.spmf.tools.dataset_stats;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/* This file is copyright (c) Cheng-Wei Wu et al.
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
 * This class is used to calculate statistics about a transaction database with
 * utility and cost information. 
 * 
 * @author Philippe Fournier-Viger
 */

public class TransactionDBCostUtilityStats {

	/**
	 * Constructor
	 */
	public TransactionDBCostUtilityStats() {

	}

	/**
	 * Calculate information about a database with utility information
	 * 
	 * @param inputPath an input file path
	 * @throws IOException
	 */
	public void runAlgorithm(String inputPath) throws IOException {

		/** The total utility in this database */
		long totalUtility = 0;

		/** the number of transactions in this database */
		int databaseSize = 0;

		/** the maximum ID of items in this database */
		int maxID = 0;

		/** the sum of all transaction length for this database */
		int sumAllLength = 0;

		/** the average transaction length in this database */
		double avgLength = 0.0;

		/** the maximum length of transactions in this database */
		int maxLength = 0;

		/** the set of all items in this database */
		Set<Integer> allItem = new HashSet<Integer>();
		
		/** the list of cost values */
		List<Integer> costValues = new ArrayList<Integer>();
		
		/** the list of utility values */
		List<Integer> utilityValues = new ArrayList<Integer>();

		BufferedReader br;
		String line = ""; // get the String by reading from BufferedReader

		br = new java.io.BufferedReader(new java.io.FileReader(inputPath));

		while (true) {
			line = br.readLine();
			if (line == null) {
				break;
			}
			// if the line is a comment, is empty or is a
			// kind of metadata
			if (line.isEmpty() == true || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {
				continue;
			}
			databaseSize++;
			// divide into 3 parts : 1.itemsets 2.transaction utility 3.cost of each item
			String[]  tokens1 = line.split(":");
			// divide itemsets into items
			String[] items = tokens1[0].split(" ");
			int utility = Integer.parseInt(tokens1[1]);
			totalUtility += utility;
			utilityValues.add(utility);
			
			sumAllLength += items.length;
			if (maxLength < items.length) {
				maxLength = items.length;
			}

			for (int i = 0; i < items.length; i++) {
				int num = Integer.parseInt(items[i]);
				if (num > maxID) {
					maxID = num;
				}
				allItem.add(num);
			}
			

			// divide itemsets into items
			String[] costs = tokens1[2].split(" ");
			for(String costString : costs) {
				int cost = Integer.parseInt(costString);
				costValues.add(cost);
			}
		}
		br.close();

		avgLength = (int) ((double) sumAllLength / databaseSize * 100) / 100.0; // accurate to the second decimal place

		// Print the calculated information to the console
		System.out.println("---------- Cost-Utility Transaction Database Information----------");
		System.out.println("Number of transations : " + String.valueOf(databaseSize));
		System.out.println("Total utility : " + String.valueOf(totalUtility));
		System.out.println("Number of distinct items : " + String.valueOf(allItem.size()));
		System.out.println("Maximum Id of item : " + String.valueOf(maxID));
		System.out.println("Average length of transaction : " + String.valueOf(avgLength));
		System.out.println("Maximum length of transaction : " + String.valueOf(maxLength));
		double density  = (avgLength / allItem.size()) * 100d;
//		System.out.println(costValues);
		System.out.println("Average cost per item: "
				+ BasicStatsFunctions.calculateMean(costValues) + " standard deviation: "
				+ BasicStatsFunctions.calculateStdDeviation(costValues) + " variance: "
				+ BasicStatsFunctions.calculateVariance(costValues));
		System.out.println("Average utility per transaction: "
				+ BasicStatsFunctions.calculateMean(utilityValues) + " standard deviation: "
				+ BasicStatsFunctions.calculateStdDeviation(utilityValues) + " variance: "
				+ BasicStatsFunctions.calculateVariance(utilityValues));
		System.out.println("Database density: " + density + " %");
	}

}
