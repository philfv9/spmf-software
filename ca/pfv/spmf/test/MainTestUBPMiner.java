package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.ubpminer.AlgoUBPMiner;

/** Small command-line example for running UBP-Miner on an SPMF utility file. */
public class MainTestUBPMiner {
    public static void main(String[] args) throws IOException {
        String input = args.length > 0 ? args[0] : fileToPath("DB_Utility.txt");
        String output = args.length > 1 ? args[1] : "output_ubp.txt";
        int minUtility = args.length > 2 ? Integer.parseInt(args[2]) : 30;
        int partitionSize = args.length > 3 ? Integer.parseInt(args[3]) : 63;

        AlgoUBPMiner algorithm = new AlgoUBPMiner();
        algorithm.runAlgorithm(input, output, minUtility, partitionSize);
        algorithm.printStats();
    }
    
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestHUIMiner.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
