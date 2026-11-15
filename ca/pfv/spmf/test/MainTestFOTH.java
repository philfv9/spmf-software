package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.foth.AlgoFOTH;

/** Example of how to use the FOTH algorithm from the source code. 
 * @author Philippe Fournier-Viger
 **/
public class MainTestFOTH {

    public static void main(String[] args) throws IOException {
    	// input file
        String input = fileToPath("DB_Utility.txt");
        
        // output file
        String output = "output.txt";
        
        // minimum support threshold
        long minUtility = 30;

        // Run the algorithm
        AlgoFOTH algorithm = new AlgoFOTH();
        algorithm.runAlgorithm(input, output, minUtility);
        
        // Print stats about the algorithm's execution
        algorithm.printStats();
    }
    
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestHUIMiner.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
