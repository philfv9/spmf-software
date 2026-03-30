package ca.pfv.spmf.tools.dataset_stats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import ca.pfv.spmf.input.product_transaction_database.ProductTransaction;
import ca.pfv.spmf.input.product_transaction_database.ProductTransactionDatabase;
/* This file is copyright (c) 2008-2012 Philippe Fournier-Viger
* 
* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
* 
* SPMF is free software: you can redistribute it and/or modify it under the
* terms of the GNU General Public License as published by the Free Software
* Foundation, either version 3 of the License, or (at your option) any later
* version.
* SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
* A PARTICULAR PURPOSE. See the GNU General Public License for more details.
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
*/
/**
 * This class read a product transaction database and calculates statistics
 * about this transactions database, then it prints the statistics to the console.
 * <br/><br/>
 * In this version this class reads the database into memory before calculating the
 * statistics. It could be optimized.

* @author Philippe Fournier-Viger
 */
public class ProductTDBStats {


	/**
	 * This method generates statistics for a product transaction database (a file)
	 * @param path the path to the file
	 * @throws Exception 
	 */
	public void runAlgorithm(String path) throws Exception {

		/////////////////////////////////////
		//  (1) First we will read the transaction database into memory.
		// (actually, we don't really need to read it into memory because it
		//  just require a single pass, but the code is more simple like that
		//  - it could be optimized, if necessary).
		///////////////////////////////////	
		
		ProductTransactionDatabase database = new ProductTransactionDatabase();
		database.loadFile(path);

		/////////////////////////////////////
		//  We finished reading the database into memory.
		//  We will BasicStatsFunctions.calculate statistics on this transaction database.
		///////////////////////////////////

		System.out.println("============  TRANSACTION DATABASE STATS ==========");
		System.out.println("Number of transactions : " + database.size());
		
		// we initialize some variables that we will use to generate the statistics
		int minItem = Integer.MAX_VALUE; // the largest id for items in the database
		int maxItem = 0; // the largest id for items in the database
		Set<Integer> items = new java.util.HashSet<Integer>();  // the set of all items
		List<Integer> sizes = new ArrayList<Integer>(); // the lengths of each transactions
		List<Integer> profits = new ArrayList<Integer>(); // the profits of each transactions
		
		// this map is used to store the number of times that each item
		// appear in the database.
		// the key is an item
		// the value is the number of items that the item appears
		HashMap<Integer, Integer> mapItemSupport = new HashMap<Integer, Integer>();
		
		
		// Loop on transactions from the database
		for (ProductTransaction transaction : database.getTransactions()) {
			// we add the size of this transaction to the list of sizes
			sizes.add(transaction.size());
			profits.add(transaction.getProfit());

			// Loop on items from this transaction
			for (int item : transaction.getItems()) {
				if(item > maxItem) {
					maxItem = item; 
				}
				if(item < minItem) {
					minItem = item; 
				}
				// If the item is not in the map already, we set count to 0
				Integer count = mapItemSupport.get(item);
				if (count == null) {
					count = 0;
				}
				mapItemSupport.put(item, count+1);
				// finally, we add the item to the set of items
				items.add(item);
			}
		}
		
		// put support of items into a list
		List<Integer> listSupportOfItems = new ArrayList<Integer>(mapItemSupport.values());
		
		// we print the statistics
		System.out.println("File " + path);
		System.out.println("Number of distinct items: " + items.size());
		System.out.println("Smallest item id: " + minItem);
		System.out.println("Largest item id: " + maxItem);
		double avgLength = BasicStatsFunctions.calculateMean(sizes);
		System.out.println("Average number of items per transaction: "
				+ avgLength + " standard deviation: "
				+ BasicStatsFunctions.calculateStdDeviation(sizes) + " variance: "
				+ BasicStatsFunctions.calculateVariance(sizes));
		System.out.println("Average profit per product (transaction): "
				+ BasicStatsFunctions.calculateMean(profits) + " standard deviation: "
				+ BasicStatsFunctions.calculateStdDeviation(profits) + " variance: "
				+ BasicStatsFunctions.calculateVariance(profits));
		System.out.println("Average item support in the database: "
				+ BasicStatsFunctions.calculateMean(listSupportOfItems) + " standard deviation: "
				+ BasicStatsFunctions.calculateStdDeviation(listSupportOfItems) + " variance: "
				+ BasicStatsFunctions.calculateVariance(listSupportOfItems)
				+ " min value: " + BasicStatsFunctions.calculateMinValue(listSupportOfItems)
				+ " max value: " + BasicStatsFunctions.calculateMaxValue(listSupportOfItems)
				);
		double density  = (avgLength /  items.size()) * 100d;
		System.out.println("Database density: " + density + " %");
	}

}
