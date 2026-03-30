package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import ca.pfv.spmf.algorithms.frequentpatterns.talkyg.AlgoTalkyG_Diffset;
import ca.pfv.spmf.input.transaction_database_list_integers.TransactionDatabase;
import ca.pfv.spmf.patterns.itemset_array_integers_with_tids_bitset.Itemset;
import ca.pfv.spmf.patterns.itemset_array_integers_with_tids_bitset.Itemsets;

/* This file is copyright (c) 2008-2015 Philippe Fournier-Viger
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
 * Example of how to use TalkyG-Diffset algorithm from the source code.
 * @author Philippe Fournier-Viger
 */
public class MainTestTalkyGDiffset_saveToMemory {

	public static void main(String [] arg) throws IOException{
		// Loading the binary context
		String input = fileToPath("contextZart.txt");  // the database
		
		double minsup = 0.4; // means a minsup of 2 transaction (we used a relative support)
		
		TransactionDatabase database = new TransactionDatabase();
		try {
			database.loadFile(input);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		// Applying the TalkyG algorithm
		AlgoTalkyG_Diffset algo = new AlgoTalkyG_Diffset();
		
		// This line allow to activate or deactivate some optimizations
		boolean useTriangularMatrixOptimization = true;
		
		// This line allows setting the internal hash table size, which can influence performance.
		int hashTableSize = 500;
		
		// Set a maximum pattern length (optional)
		algo.setMaximumPatternLength(10);
		
		Itemsets generators = algo.runAlgorithm(null, database, minsup, useTriangularMatrixOptimization, hashTableSize);
		algo.printStats();
		for(List<Itemset> genSizeK : generators.getLevels()) {
			for(Itemset itemset : genSizeK) {
				System.out.println(Arrays.toString(itemset.getItems()) + " #SUP: " + itemset.getAbsoluteSupport());
			}
		}
	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestTalkyGDiffset_saveToMemory.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
