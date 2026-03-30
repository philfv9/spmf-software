package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.emsfui_d.AlgoEMSFUI_D;

/**
 * Example of how to run the EMSFUI_D algorithm from the source code
 */
public class MainTestEMSFUI_D {

	public static void main(String [] arg) throws IOException{		
		String input = fileToPath("contextHUIM.txt");
		String output = ".output.txt";
		// Applying the  algorithm
		AlgoEMSFUI_D EMSFUI_D = new AlgoEMSFUI_D();
		EMSFUI_D.runAlgorithm(input, output);
		EMSFUI_D.printStats();
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestEMSFUI_D.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
