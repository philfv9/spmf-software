package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.huo.AlgoCloFHUOIM;

/**
 * Example of how to use the CloFHUOIM algorithm
 * from the source code.
 * @author Philippe Fournier-Viger, 2026
 */
public class MainTestCloFHUOIM {

	public static void main(String [] arg) throws IOException{

		String input = fileToPath("DB_UtilityOccupancy.txt");
		String output = "output.txt";

		double min_support = 0.3;  // One of the paper's five transactions
		double min_utility_occupancy = 0.3;  // 70% mean utility occupancy


		// Applying the CloFHUOIM algorithm
		AlgoCloFHUOIM algo = new AlgoCloFHUOIM();
		algo.runAlgorithm(input, output, min_support, min_utility_occupancy);
		algo.printStats();

	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestCloFHUOIM.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
