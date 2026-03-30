package ca.pfv.spmf.algorithms.frequentpatterns.slim;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

/**
 * Example of how to run the SLIM algorithm
 * @author Philippe Fournier-Viger, 2023
 */
public class MainTestAlgoSLIM {

    // A method to test the algorithm with a toy example
    public static void main(String[] args) throws IOException {
    	
    	// The path to a transaction database
    	String databaseFilePath = fileToPath("contextPasquier99.txt");
    	
    	// An output file path
    	// (if you dont want to save the result to a file, it can be set to null)
    	String outputFilePath = "output.txt";
    	
        // Apply the algorithm to find the best code table
    	AlgoSLIM algo = new AlgoSLIM();
    	
        // OPTIONAL:  You can set a maximum number of iterations:
//        algo.setMaxIteration(100);
    	
        // OPTIONAL:  Display the iteration numbers in the console
//        algo.setDisplayIterationInConsole(true);
    	
        List<Itemset> result = algo.runAlgorithm(databaseFilePath, outputFilePath);
        
		// OPTIONAL: Print the itemsets in the console
		 System.out.println("Itemsets found:");
		 for (Itemset itemset : result) {
		 		System.out.println(Arrays.toString(itemset.items) + " support: " +
		 		itemset.getSupport());
		 }
        
        // Print statistics about the algorithm execution
        algo.printStats();


    }
    

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestAlgoSLIM.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
