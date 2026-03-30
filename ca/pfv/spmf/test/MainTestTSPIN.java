package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.sppgrowth.AlgoTSPIN;

/**
 * Example of how to use the TSPIN algorithm 
 * from the source code and save the result to file.
 * @author Ying Wang, Philippe Fournier-Viger, 2020
 */
public class MainTestTSPIN {

	public static void main(String[] args) throws IOException{
		
		String inputFile = fileToPath("contextTSPIN.txt");
		String outputFile = "output.txt";
        
        int maxPer = 5;
        int maxLa = 1;
        int k = 3;

        AlgoTSPIN algo = new AlgoTSPIN(maxPer,maxLa,k,false);
        algo.runAlgorithm(inputFile,outputFile);
        algo.printStats();
		
	}
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestTSPIN.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
