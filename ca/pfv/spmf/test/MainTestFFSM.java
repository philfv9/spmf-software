// =============================================================================
package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.graph_mining.ffsm.AlgoFFSM;

/**
 * Example of how to use the FFSM algorithm from the source code and output the
 * result to a file.
 * 
 */
public class MainTestFFSM {

	public static void main(String[] arg) throws IOException {

		// set the input and output file path
		String input = fileToPath("contextTKG.txt");
		String output = ".//output.txt";

		// minimum support threshold (fraction of graphs, e.g. 0.9 = 90%)
		double minSupport = 0.9;

		// Output the ids of graphs containing each frequent subgraph
		boolean outputGraphIds = false;

		// Run FFSM
		AlgoFFSM algo = new AlgoFFSM();
		algo.runAlgorithm(input, output, minSupport, outputGraphIds);

		// Print statistics
		algo.printStats();
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestFFSM.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}