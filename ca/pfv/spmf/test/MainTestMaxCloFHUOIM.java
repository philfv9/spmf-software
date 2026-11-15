package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.huo.AlgoMaxCloFHUOIM;

/**
 * Example of how to use the MaxCloFHUOIM algorithm
 * from the source code.
 * @author Philippe Fournier-Viger, 2026
 */
public class MainTestMaxCloFHUOIM {

	public static void main(String [] arg) throws IOException{

		// the input file path
		String input = fileToPath("DB_UtilityOccupancy.txt");
		
		// the output file path for saving maximal itemsets
		String outputMaximal = "output.txt";
		
		// If the following string is set to a file name, the algorithm will
		//  output all the closed itemsets to that file in addition to saving the maximal itemsets
//		 String outputClosed = "closed.txt";
		String outputClosed = null;

		double min_support = 0.3;  // One of the paper's five transactions
		double min_utility_occupancy = 0.3;  // 30% mean utility occupancy

		// Applying the MaxCloFHUOIM algorithm
		AlgoMaxCloFHUOIM algo = new AlgoMaxCloFHUOIM();
		algo.runAlgorithm(input, outputClosed, outputMaximal, min_support, min_utility_occupancy);
		algo.printStats();

	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestMaxCloFHUOIM.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
