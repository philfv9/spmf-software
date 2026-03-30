package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.associationrules.hgb.AlgoFHIM_and_HUCI;
import ca.pfv.spmf.algorithms.associationrules.hgb.AlgoHGB;
import ca.pfv.spmf.algorithms.associationrules.hgb.HUClosedTable;
import ca.pfv.spmf.algorithms.associationrules.hgb.Rules;

/**
 * Example of how to use the HGB algorithm from the source code.
 * 
 * @author Philippe Fournier-Viger, 2022
 */
public class MainTestHGB {

	public static void main(String[] arg) throws IOException {
		String input = fileToPath("DB_Utility.txt");
		String output = ".//output.txt";

		int min_utility = 30; 

		// Step 1 : Applying the HUIMiner algorithm
		AlgoFHIM_and_HUCI huci = new AlgoFHIM_and_HUCI();
		HUClosedTable results = huci.runAlgorithmHUCIMiner(input, null, min_utility);
		huci.printStats();
		
		// Step 2: Applying the HGB algorithm to generate high utility association rules
		double minconf = 0.5;
		
		AlgoHGB algoHGB = new AlgoHGB();
		Rules rules = algoHGB.runAlgorithm(results, min_utility, minconf);
		algoHGB.writeRulesToFile(output);
		algoHGB.printStats();

	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestHGB.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
