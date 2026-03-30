package ca.pfv.spmf.algorithms.graph_mining.aerminer;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

/**
 * Example of how to use the AERMiner algorithm 
 * from the source code and output the result to a file.
 * @author Ganghuan He  2020
 */

public class MainTestAERMiner {

	public static void main(String [] arg) throws IOException, ClassNotFoundException{

		// The files are in directory 'DBLP', and minSup, minConf, minLift are 0.004, 0.3, 1.5

		// The input directory containing a dynamic attributed graph
//		String inputDirectory = fileToPath("DBLP") + File.separator;
		String inputDirectory = "D:\\SPMF_DYNAMIC_DATASETS\\AERMINER_datasets\\DBLP\\";
		
		// The output file path
		String outputPath = "output.txt";

        // minimum support
		double minsup = 0.05;
        
        // minimum confidence
        double minconf = 0.4;
        
        // minimum lift
        double minlift = 0.4;
                
		// Apply the algorithm 
		AlgoAERMiner algo = new AlgoAERMiner();
		algo.runAlgorithm(inputDirectory, outputPath, minsup, minconf, minlift);
		
		// Print statistics about the algorithm execution
		algo.printStats();
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestAERMiner.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
