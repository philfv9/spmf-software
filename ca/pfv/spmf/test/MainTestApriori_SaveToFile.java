package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.apriori_fast.AlgoAprioriFAST;

/**
 * Example showing file-based usage of the AprioriFAST algorithm.
 * 
 * @author Philippe Fournier-Viger
 */
public class MainTestApriori_SaveToFile {

    public static void main(String[] args) throws IOException {
        
        // Input file path
        String input = fileToPath("contextPasquier99.txt");
        
        // Output file path
        String output = "output.txt";
        
        // Minimum support threshold
        double minSupport = 0.4;
        
        // Create the algorithm
        AlgoAprioriFAST algo = new AlgoAprioriFAST();
        
        // Use this method to set the maximum pattern length
        algo.setMaximumPatternLength(800);  
        
		// There is a branch count parameter for the internal hash-tree used by Apriori
		// If you dont know what it is, just let it at the value 30. It is a good value.
		int branchCount = 30;
		
        // Run the algorithm
        algo.runAlgorithm(minSupport, input, output, branchCount);
        
        // Print stats about the algorithm executionoutput.txt
        algo.printStats();
    }
    
    public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestApriori_SaveToFile.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}