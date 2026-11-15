package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.huo.AlgoHUOMIL;

/**
 * Example of how to use the HUOMIL algorithm
 * from the source code.
 * @author Philippe Fournier-Viger, 2026
 */
public class MainTestHUOMIL {

	public static void main(String [] arg) throws IOException{

		String input = fileToPath("DB_UtilityOccupancy.txt");
		String output = "output.txt";

		double min_support = 0.3;  // 30% of the transactions
		double min_utility_occupancy = 0.3;  // 30% mean utility occupancy

		// Applying the HUOMIL algorithm
		AlgoHUOMIL huomil = new AlgoHUOMIL();
		huomil.runAlgorithm(input, output, min_support, min_utility_occupancy);
		huomil.printStats();

	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestHUOMIL.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
