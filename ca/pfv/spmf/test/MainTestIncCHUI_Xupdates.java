package ca.pfv.spmf.test;

import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.incchui.AlgoIncCHUI;

/**
 * Example of how to use the IncCHUI algorithm from the source code by processing a single file
 * in several parts (updates).
 * 
 * @AlgoIncCHUI
 * @author Philippe Fournier-Viger, 2014
 */
public class MainTestIncCHUI_Xupdates {

	public static void main(String[] arg) throws IOException {

		String input = fileToPath("DB_Utility.txt");  // retails_utilityPFV
		int min_utility = 30;
		
		// the number of updates to be performed
		int numberOfUpdates = 2;
		
		// the size of the hash table used by the algorithm
		int hashtablesize = 5000;
		
		// scan the database to count the number of lines
		// for our test purpose
		int linecount = countLines(input);
		
		double addedratio = 1d / ((double) numberOfUpdates);
		int linesForeEachUpdate = (int)(addedratio * linecount);
		
		// Apply the algorithm several times
		AlgoIncCHUI algo = new AlgoIncCHUI();
		int firstLine = 0;		
		for(int i = 0; i < numberOfUpdates; i++){
			int lastLine = firstLine + linesForeEachUpdate;
			//

			// Applying the algorithm
			// If this is the last update, we make sure to run until the last line
			if(i == numberOfUpdates -1) {
				System.out.println("" + i + ") Run the algorithm using line " + firstLine + " to before line " + linecount + " of the input database.");
				algo.runAlgorithm(input, min_utility, firstLine, linecount, hashtablesize);
			}else {
				// If this is not the last update
				System.out.println("" + i + ") Run the algorithm using line " + firstLine + " to before line " + lastLine + " of the input database.");
				algo.runAlgorithm(input, min_utility, firstLine, lastLine, hashtablesize);
			}
			algo.printStats();
			
			firstLine = lastLine;
		}
		
		// Print the number of HUIs found until now to the console
		int realHUICount =  algo.getRealCHUICount();
		System.out.println("NUMBER OF CHUI FOUND: " + realHUICount);
		
		// WE CAN ALSO WRITE ALL THE HUIs found until now to a file at any time with
		// the following code
		String output = ".//output.txt";
		algo.writeCHUIsToFile(output);
	}

	/**
	 * This methods counts the number of lines in a text file.
	 * @param filepath the path to the file
	 * @return the number of lines as an int
	 * @throws IOException Exception if error reading/writting file
	 */
	public static int countLines(String filepath) throws IOException {
		LineNumberReader reader = new LineNumberReader(new FileReader(filepath));
		while(reader.readLine() != null) {}
		int count = reader.getLineNumber();
		reader.close();
		return count;
	}

	public static String fileToPath(String filename)
			throws UnsupportedEncodingException {
		URL url = MainTestIncCHUI_Xupdates.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
