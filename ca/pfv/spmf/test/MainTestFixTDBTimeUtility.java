package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.lhui.AlgoLHUIMiner;
import ca.pfv.spmf.tools.other_dataset_tools.fix_tdb_utility_time.AlgoFixTDBTimeUtility;

/**
 * Example of how to fix a transaction database with utility and time information
 * @author Philippe Fournier-Viger
 * @see AlgoLHUIMiner
 */
public class MainTestFixTDBTimeUtility {

	public static void main(String[] args) throws IOException {
		
		// Input file 
		String inputFile = fileToPath("DB_broken.txt");
		
		// Output file 
		String outputFile = "output.txt";
		
		AlgoFixTDBTimeUtility   algo = new AlgoFixTDBTimeUtility();
		algo.runAlgorithm(inputFile, outputFile);
		algo.printStats();
	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestFixTDBTimeUtility.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}

}
