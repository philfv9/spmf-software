package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.apriori_fast.AlgoAprioriFASTRare;
import ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemsets;

/**
 * Example of how to use the APRIORI RARE algorithm, from the source code.
 * @author Philippe Fournier-Viger (Copyright 2008)
 */
public class MainTestAprioriRare_saveToMemory {

	public static void main(String [] arg) throws IOException{
		// Loading a binary context
		String inputFilePath = fileToPath("contextZart.txt");
		String outputFilePath = null;  
		// Note that we set the output file path to null because
		// we want to keep the result in memory instead of saving them
		// to an output file in this example.
		
		// the threshold that we will use:
		double minsup = 0.6;
		
		// There is a branch count parameter for the internal hash-tree used by Apriori
		// If you dont know what it is, just let it at the value 30. It is a good value.
		int branchCount = 30;
		
		// Applying the APRIORI-Inverse algorithm to find sporadic itemsets
		AlgoAprioriFASTRare apriori2 = new AlgoAprioriFASTRare();
		// apply the algorithm
		Itemsets patterns = apriori2.runAlgorithm(minsup, inputFilePath, outputFilePath, branchCount);
		int databaseSize = apriori2.getDatabaseSize();
		patterns.printItemsets(databaseSize); // print the result
		apriori2.printStats();

	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestAprioriRare_saveToMemory.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
