package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.rminer_sminer.AlgoScentedUtilityMiner;

/**
 * Example of how to use the AlgoScentedUtilityMiner algorithm 
 * from the source code.
 * This class demonstrates how to set up and run the
 * high utility itemset mining algorithm using the 
 * AlgoScentedUtilityMiner class.
 */
public class MainTestAlgoScentedUtilityMiner {

    /**
     * Main method to run the AlgoScentedUtilityMiner algorithm.
     * @param args Command line arguments (not used).
     * @throws IOException If there is an error reading or writing files.
     */
    public static void main(String[] args) throws IOException {
        
        // Path to the input file (change the filename as needed)
        String input = fileToPath("DB_Utility.txt");
        
        // Path to the output file where results will be written
        String output = ".//output.txt";
        
        // Uncomment and use the following line if you want to set a different utility threshold
         int minUtility = 30;  
        
        // Maximum reinduction count (set as needed)
        int maxReinductionCount = 10; 
        
        
        // Create an instance of AlgoScentedUtilityMiner
        AlgoScentedUtilityMiner algorithm = new AlgoScentedUtilityMiner();
        
        // Run the algorithm with the specified input, output, and minimum utility
        algorithm.runAlgorithm(input, output, minUtility, maxReinductionCount);
        algorithm.printStats();
    }

    /**
     * Converts a filename to its file path.
     * @param filename The name of the file to be converted.
     * @return The file path as a String.
     * @throws UnsupportedEncodingException If encoding is not supported.
     */
    public static String fileToPath(String filename) throws UnsupportedEncodingException {
        // Get the URL of the file from the classpath
        URL url = MainTestAlgoScentedUtilityMiner.class.getResource(filename);
        
        // Decode and return the path of the file
        return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
    }
}
