package ca.pfv.spmf.algorithms.frequentpatterns.fuimtwu;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
/**
 * Example of how to use the FUIMTWU-Tree algorithm 
 * Thanks for the SPMF library to provide the source code of mHUIMiner
 */
public class MainTestFUIMTWU {
	public static void main(String [] arg) throws IOException{		
		String input = fileToPath("mypaper.txt");
		String output = "D://output2.txt";
		int min_utility = 110;  // 
		double min_supDouble=0.4;
		// Applying the FUIMTWU-Tree algorithm
		AlgoFUIMTWU Twu_Tree = new AlgoFUIMTWU();
		Twu_Tree.runAlgorithm(input, output, min_utility,min_supDouble);
		Twu_Tree.printStats();
	}
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestFUIMTWU.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
