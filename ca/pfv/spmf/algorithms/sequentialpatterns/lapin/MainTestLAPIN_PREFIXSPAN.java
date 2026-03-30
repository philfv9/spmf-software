package ca.pfv.spmf.algorithms.sequentialpatterns.lapin;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.sequentialpatterns.lapin.AlgoLAPIN_LCI;
import ca.pfv.spmf.algorithms.sequentialpatterns.prefixspan.AlgoPrefixSpan;

/**
 * Example of how to use the LAPIN_LCI (a.k.a LAPIN-SPAM) algorithm in source code.
 * @author Philippe Fournier-Viger 2014
 */
public class MainTestLAPIN_PREFIXSPAN {

	public static void main(String [] arg) throws IOException{   
		String inputPath = fileToPath("contextPrefixSpan.txt");
		String outputPath = ".//output_lapin.txt";
		
		// Create an instance of the algorithm with minsup = 50 %
		AlgoLAPIN_LCI algo = new AlgoLAPIN_LCI(); 
		
		double minsup = 0.5; // we use a minimum support of 2 sequences.
		
		// execute the algorithm
		algo.runAlgorithm(inputPath, outputPath, minsup);    
		algo.printStatistics();
				
		// output file path
		String outputPath2 = ".//output_prefixspan.txt";

		// Create an instance of the algorithm with minsup = 50 %
		AlgoPrefixSpan algo2 = new AlgoPrefixSpan(); 
        
		// execute the algorithm
		algo2.runAlgorithm(inputPath, minsup, outputPath2);    
		algo2.printStatistics();
	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestLAPIN_PREFIXSPAN.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}