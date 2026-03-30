package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.associationrules.hgb.AlgoFHIM_and_HUCI;
import ca.pfv.spmf.algorithms.associationrules.hgb.AlgoHGBAll;
import ca.pfv.spmf.algorithms.associationrules.hgb.HUTable;
import ca.pfv.spmf.algorithms.associationrules.hgb.Rules;

/**
 * Example of how to use the HGB-ALL algorithm from the source code.
 * 
 * @author Philippe Fournier-Viger, 2022
 */
public class MainTestHGB_all {

	public static void main(String[] arg) throws IOException {
		String input = fileToPath("DB_Utility.txt");
		String output = ".//output.txt";

		int min_utility = 30; 

		// Step 1 : Applying the HUIMiner algorithm
		AlgoFHIM_and_HUCI huci = new AlgoFHIM_and_HUCI();
		 huci.runAlgorithmHUCIMiner(input, null, min_utility);
		HUTable table = huci.getTableHU();
		huci.printStats();
		
		// Step 2: Applying the HGB algorithm to generate high utility association rules
		double minconf = 0.5;
		
		AlgoHGBAll algoHGB = new AlgoHGBAll();
		Rules rules = algoHGB.runAlgorithm(table, minconf, min_utility);
		algoHGB.writeRulesToFile(output);
		algoHGB.printStats();

	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestHGB_all.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
