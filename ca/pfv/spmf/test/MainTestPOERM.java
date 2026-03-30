package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.episodes.poerm.AlgoPOERM;


/**
 * Example of how to use the POERM algorithm from the source code
 * @author Chen YangMing, Philippe Fournier-Viger
 * @see AlgoPOERM
 */
public class MainTestPOERM {

	public static void main(String[] args) throws IOException {
		// the min support of POERM algorithm
		int minSupport = 2;
		
		// the XSpan of POERM algorithm
		int xSpan = 2;
		
		// the YSpan of POERM algorithm
		int ySpan = 2;
		
		// the min confidence of POERM algorithm
		double minConfidence = 0.5;
		
		// the XYSpan of POERM algorithm
		int xySpan = 3;
		
		// Input file 
		String inputFile = fileToPath("contextEMMA.txt");
		
		// If the input file does not contain timestamps, then set this variable to true
        // to automatically assign timestamps as 1,2,3...
		boolean selfIncrement = false;
		
		// Output file 
		String outputFile = "output.txt";
		
		AlgoPOERM poerm = new AlgoPOERM();
		poerm.runAlgorithm(inputFile, minSupport, xSpan, ySpan, minConfidence, xySpan, selfIncrement);
		poerm.writeRule2File(outputFile);
		poerm.printStats();
	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestPOERM.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
