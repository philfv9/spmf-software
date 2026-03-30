package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.sfui_uf.AlgoSFUI_UF;

/**
 * This example shows how 
 * SFUI-UF:Mining Skyline Frequent-Utility Itemsets with Utility Filtering
 * 
 * @author Wei Song,Chuanlong Zheng,Philippe Fournier-Viger,2021
 *
 */
public class MainTestSFUI_UF {

	public static void main(String[] arg) throws IOException {
		// the input file path
		String input = fileToPath("DB_Utility.txt");

		// the output file path
		String output = "output.txt";

		// create the algorithm
		AlgoSFUI_UF algorithm = new AlgoSFUI_UF();
		
		// Run the algorithm
		algorithm.runAlgorithm(input, output);
		
		// Print stats about the algorithm execution
		algorithm.printStats();

	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestSFUI_UF.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
