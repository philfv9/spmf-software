package ca.pfv.spmf.test;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import ca.pfv.spmf.algorithms.frequentpatterns.krimp.AlgoKrimp;
import ca.pfv.spmf.algorithms.frequentpatterns.krimp.Itemset;

/**
 * Example of how to run the Krimp algorithm
 * @author Philippe Fournier-Viger, 2023
 */
public class MainTestAlgoKrimp {

    // A method to test the algorithm with a toy example
    public static void main(String[] args) throws IOException {
    	
    	// The path to a transaction database
    	String databaseFilePath = fileToPath("contextPasquier99.txt");
    	
    	// The path to a file containing a set of itemsets and their support values
    	String patternsFilePath = fileToPath("patterns60.txt");
    	
    	// An output file path
    	// (if you dont want to save the result to a file, it can be set to null)
    	String outputFilePath = "output.txt";

        // Apply the krimp algorithm to find the best code table
    	AlgoKrimp algo = new AlgoKrimp();
        List<Itemset> result = algo.runAlgorithm(databaseFilePath, patternsFilePath, outputFilePath);
        

//      // Print the itemsets and the size of the code table
      System.out.println("Itemsets found:");
      for (Itemset itemset : result) {
          System.out.println(Arrays.toString(itemset.items) + " support: " + itemset.support);
      }
        
        // Print statistics about the algorithm execution
        algo.printStats();

    }
    

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestAlgoKrimp.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
