package ca.pfv.spmf.test;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.dic.AlgoDIC;

/**
 * Example of how to use the DIC algorithm from the source code.
 * 
 * @author Philippe Fournier-Viger
 */
public class MainTestDIC {

    public static void main(String[] args) throws IOException {

        // Load the input file
        String input = fileToPath("contextPasquier99.txt");
        
        // Set the output file path
        String output = "output.txt";
        
        // Set minimum support threshold (40% = 0.4)
        // With 5 transactions, this means minSup = 2
        double minSupport = 0.4;
        
        // Set interval size M (number of transactions between adding new candidates)
        // Smaller M = more dynamic but more overhead
        // Larger M = more like Apriori
        int intervalSize = 2;
        
        // Create instance of the algorithm
        AlgoDIC algorithm = new AlgoDIC();
        
        // Run the algorithm
//        algorithm.runAlgorithm(input, output, minSupport);
        algorithm.runAlgorithm(input, output, minSupport, intervalSize);
        
        // Print statistics
        algorithm.printStats();
    }

    /**
     * Utility method to get the file path from resources
     * @param filename the filename
     * @return the file path as string
     * @throws UnsupportedEncodingException if encoding not supported
     */
    public static String fileToPath(String filename) throws UnsupportedEncodingException {
        URL url = MainTestDIC.class.getResource(filename);
        if (url == null) {
            // If not found in resources, try current directory
            return filename;
        }
        return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
    }
}