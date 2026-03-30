package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.neclatclosed.AlgoNEclatClosed;

/**
 * Example of how to use NEClatClosed algorithm from the source code.
 * @see AlgoNEclatClosed
 * @author Philippe Fournier-Viger 
 */
public class MainTestNECLATClosed {

	public static void main(String [] arg) throws IOException{
		
		String input = fileToPath("contextPasquier99.txt");
		String output = ".//output.txt";
		double minsup = 0.5;  
		
		// Applying the  algorithm
		AlgoNEclatClosed algorithm = new AlgoNEclatClosed();
		algorithm.runAlgorithm(input, minsup, output);
	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestNECLATClosed.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
