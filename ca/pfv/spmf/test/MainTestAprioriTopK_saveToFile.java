package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.apriori_fast.AlgoAprioriFAST_TopK;

/**
 * Example of how to use APRIORI(top-k) algorithm from the source code.
 * @author Philippe Fournier-Viger (Copyright 2008)
 */
public class MainTestAprioriTopK_saveToFile {

	public static void main(String [] arg) throws IOException{

		String input = fileToPath("contextPasquier99.txt");
		String output = ".//output.txt";  // the path for saving the frequent itemsets found
		
		int k = 9; // means a minsup of 2 transaction (we used a relative support)
		
		// There is a branch count parameter for the internal hash-tree used by Apriori
		// If you dont know what it is, just let it at the value 30. It is a good value.
		int branchCount = 30;
		
		// Applying the Apriori algorithm
		AlgoAprioriFAST_TopK algo = new AlgoAprioriFAST_TopK();
		
		// Uncomment the following line to set the maximum pattern length (number of items per itemset)
//		algo.setMaximumPatternLength(3);

		algo.runAlgorithm(k, input, output, branchCount);
		algo.printStats();
	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestAprioriTopK_saveToFile.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
