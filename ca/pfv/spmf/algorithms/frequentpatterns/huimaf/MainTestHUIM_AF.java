package ca.pfv.spmf.algorithms.frequentpatterns.huimaf;


import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

/**
 * Example of how to use the HUIM-AF algorithm 
 * from the source code.
 */
public class MainTestHUIM_AF {

	public static void main(String [] arg) throws IOException{
		
		// Input file path
		String input = fileToPath("DB_Utility.txt");

		// Output file path
		String output = "output.txt";

		// minimum utility threshold
		int min_utility = 40;
		
		// Create the algorithm
		AlgoHUIM_AF huim_fish = new AlgoHUIM_AF();
		
		// Run the algorithm
		huim_fish.runAlgorithm(input, output, min_utility);
		
		// Print statistics about the execution of the algorithm
		huim_fish.printStats();

	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestHUIM_AF.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
