package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.emsfui_b.AlgoEMSFUI_B;

/**
 * Example of how to run the EMSFUI_B algorithm from the source code
 */
public class MainTestEMSFUI_B {

	public static void main(String [] arg) throws IOException{
		
		String input = fileToPath("contextHUIM.txt");
		String output = "output.txt";
		// Applying the  algorithm
		AlgoEMSFUI_B EMSFUI_B = new AlgoEMSFUI_B();
		EMSFUI_B.runAlgorithm(input, output);
		EMSFUI_B.printStats();
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestEMSFUI_B.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
