package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.HUIM_SPSO.AlgoHUIM_SPSO;


/**
 * This is an example of how to run HUIM-SPSO
 * @see AlgoHUIM_SPSO
 *
 */
public class MainTestHUIM_SetPSO{
	public static void main(String[] arg) throws IOException {

		String input = fileToPath("contextHUIM.txt");
		
		String output = ".//output.txt";

		int min_utility = 40;  // 
		
		AlgoHUIM_SPSO huim_spso = new AlgoHUIM_SPSO();
		huim_spso.runAlgorithm(input, output, min_utility);
		huim_spso.printStats();
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestHUIM_SetPSO.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
