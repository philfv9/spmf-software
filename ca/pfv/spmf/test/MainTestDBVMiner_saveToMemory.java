package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.dbvminer.AlgoDBVMiner;
import ca.pfv.spmf.input.transaction_database_list_integers.TransactionDatabase;
import ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemsets;
/* This file is copyright (c) 2008-2025 Philippe Fournier-Viger
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
 * Example of how to use DBVMiner algorithm from the source code with results save to memory.
 * @author Philippe Fournier-Viger - 2025
 */
public class MainTestDBVMiner_saveToMemory {

	public static void main(String [] arg) throws IOException{
		// Loading the binary context
		String input = fileToPath("contextPasquier99.txt");  // the database
		String output =  null; 
		
		double minsup = 0.4; // means a minsup of 2 transaction (we used a relative support)

		TransactionDatabase database = new TransactionDatabase();
		try {
			database.loadFile(input);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		// Applying the  algorithm
		AlgoDBVMiner algo = new AlgoDBVMiner();
			
//		// Uncomment the following line to set the maximum pattern length (number of items per itemset)
//		algo.setMaximumPatternLength(3);
		
//		// Set this variable to true to show the transaction identifiers where patterns appear in the output file
//		algo.setShowTransactionIdentifiers(true);
		
		Itemsets itemsets = algo.runAlgorithm(output, database, minsup);
		itemsets.printItemsets(database.size());
		algo.printStats();
		
	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestDBVMiner_saveToMemory.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
