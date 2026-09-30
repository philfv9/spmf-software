package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.etauim.AlgoETAUIM;
/**
 * Example of how to use the ETAUIM algorithm 
 */
public class MainTestETAUIM {
	public static void main(String [] arg) throws IOException{		
		String input = fileToPath("contextHAUIMiner.txt");
		String output = "output.txt";
		// Applying the ETAUIM algorithm
		int k=5;
		AlgoETAUIM algorithm = new AlgoETAUIM();
		algorithm.runAlgorithm(input, output,k);
		algorithm.printStats();
	}
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestETAUIM.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}