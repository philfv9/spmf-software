package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.PriorityQueue;

import ca.pfv.spmf.algorithms.frequentpatterns.apriori_simple.AlgoAprioriTopK;
import ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemset;

/**
 * Example of how to use APRIORI(top-K) algorithm from the source code.
 * @author Philippe Fournier-Viger (Copyright 2008)
 */
public class MainTestAprioriTopK_simple_saveToMemory {

	public static void main(String [] arg) throws IOException{

		String input = fileToPath("contextPasquier99.txt");
		String output = null;
		// Note : we here set the output file path to null
		// because we want that the algorithm save the 
		// result in memory for this example.
		
		int k = 9; // means a minsup of 2 transaction (we used a relative support)
		
		
		// Applying the Apriori algorithm
		AlgoAprioriTopK algorithm = new AlgoAprioriTopK();
		
		// Uncomment the following line to set the maximum pattern length (number of items per itemset, e.g. 3 )
//		apriori.setMaximumPatternLength(3);
		
		PriorityQueue<Itemset> result = algorithm.runAlgorithm(k, input, output);
		algorithm.printStats();
		for(Itemset itemset : result) {
			System.out.println("Itemset " + itemset + " support: " + itemset.getAbsoluteSupport());
		}
	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		System.out.println("filename : " + filename);
		URL url = MainTestAprioriTopK_simple_saveToMemory.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
