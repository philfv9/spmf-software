package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.graph_mining.tkg.cfgm.AlgoCFGM;

/**
 * Example of how to use the CFGM algorithm 
 * from the source code and output the result to a file.
 * @author Philippe Fournier-Viger 2023
 */
public class MainTestCFGM {

	public static void main(String [] arg) throws IOException, ClassNotFoundException{

		// set the input and output file path
		String input = fileToPath("contextTKG.txt");
		String output = ".//output.txt";

		// set the minimum support threshold
		double minSupport = 0.5;
		
		// The maximum number of edges for closed frequent subgraph patterns
		int maxNumberOfEdges = Integer.MAX_VALUE;
		
		// If true, closed single frequent vertices will be output
		boolean outputSingleFrequentVertices = false;
		
		// If true, a dot file will be output for visualization using GraphViz
		boolean outputDotFile = false;
		
		// Output the ids of graph containing each closed frequent subgraph
		boolean outputGraphIds = true;
		
		// Apply the algorithm 
		AlgoCFGM algo = new AlgoCFGM();
		algo.runAlgorithm(input, output, minSupport, outputSingleFrequentVertices, 
				outputDotFile, maxNumberOfEdges, outputGraphIds);
		
		// Print statistics about the algorithm execution
		algo.printStats();
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestCFGM.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}