package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.apriori_fast.AlgoAprioriFAST;
import ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemsets;

/**
 * Example showing how to use the AprioriFAST algorithm
 * and save the result to memory.
 * 
 * @author Philippe Fournier-Viger
 */
public class MainTestApriori_SaveToMemory {

	public static void main(String[] args) throws IOException {

		// Input file path
		String input = fileToPath("contextPasquier99.txt");

		// Output file path - set to null to save to memory
		String output = null;

		// Minimum support threshold
		double minSupport = 0.4;

		// Create the algorithm
		AlgoAprioriFAST algo = new AlgoAprioriFAST();

		// Use this method to set the maximum pattern length (optional)
		algo.setMaximumPatternLength(800);
		
		// There is a branch count parameter for the internal hash-tree used by Apriori
		// If you dont know what it is, just let it at the value 30. It is a good value.
		int branchCount = 30;

		// Run the algorithm and get the result in memory
		Itemsets result = algo.runAlgorithm(minSupport, input, output, branchCount);

		// Print stats about the algorithm execution
		algo.printStats();

		// Print the frequent itemsets found
		result.printItemsets(algo.getDatabaseSize());
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestApriori_SaveToMemory.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}