package ca.pfv.spmf.test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.PriorityQueue;

import ca.pfv.spmf.algorithms.frequentpatterns.fpgrowth.AlgoFPGrowthTOPK;
import ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemset;


/**
 * Example of how to use FPGrowth (top-k version) from the source code.
 * @author Philippe Fournier-Viger (Copyright 2008)
 */
public class MainTestFPGrowthTOPK_saveToMemory {

	public static void main(String [] arg) throws FileNotFoundException, IOException{
		// Loading the transaction database
		String input = fileToPath("contextPasquier99.txt");  // the database

		// we want to find the top-2 most frequent itemsets
		int k = 9;

		// Applying the FPGROWTH algorithmMainTestFPGrowth.java
		AlgoFPGrowthTOPK algo = new AlgoFPGrowthTOPK();
		
		// Uncomment the following line to set the maximum pattern length (number of items per itemset, e.g. 3 )
//		algo.setMaximumPatternLength(3);
		
		// Uncomment the following line to set the minimum pattern length (number of items per itemset, e.g. 2 )
//		algo.setMinimumPatternLength(2);
		
		// Run the algorithm
		// Note that here we use "null" as output file path because we want to keep the results into memory instead of saving to a file
		PriorityQueue<Itemset> result = algo.runAlgorithm(input, null, k);  
		// show the execution time and other statistics
		algo.printStats();
		
		// print the patterns to System.out
		algo.printStats();
		for(Itemset itemset : result) {
			System.out.println("Itemset " + itemset + " support: " + itemset.getAbsoluteSupport());
		}
	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestFPGrowthTOPK_saveToMemory.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
