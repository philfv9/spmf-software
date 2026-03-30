package ca.pfv.spmf.algorithms.frequentpatterns.fuimtf;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;


/**
 * Example of how to use the FUIMTF-Tree algorithm .
 * The code is under the GPLv3 license.
 *Thanks for SPMF providing the source code of mHUIMiner. 
 */
public class MainTestFUIMTF {
	public static void main(String [] arg) throws IOException{
	
		String input = fileToPath("mypaper.txt");
		String output = "D://output1.txt";
		int min_utility = 110; // 
		double min_supDouble = 0.4;
		// Applying the TF-Free algorithm
		AlgoFUIMTF TF_Tree = new AlgoFUIMTF();
		TF_Tree.runAlgorithm(input, output, min_utility,min_supDouble);
		TF_Tree.printStats();
	}
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestFUIMTF.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
