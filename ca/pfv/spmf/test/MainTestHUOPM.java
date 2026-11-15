package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.huo.AlgoHUOPM;

/**
 * Example of how to use the HUOPM algorithm
 * from the source code.
 * @author Philippe Fournier-Viger, 2026
 */
public class MainTestHUOPM {

	public static void main(String [] arg) throws IOException{

		String input = fileToPath("DB_UtilityOccupancy.txt");
		String output = "output.txt";

		double min_support = 0.3;  // 30% of the transactions
		double min_utility_occupancy = 0.3;  // 30% mean utility occupancy

		// Applying the HUOPM algorithm
		AlgoHUOPM huopm = new AlgoHUOPM();
		huopm.runAlgorithm(input, output, min_support, min_utility_occupancy);
		huopm.printStats();

	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestHUOPM.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
