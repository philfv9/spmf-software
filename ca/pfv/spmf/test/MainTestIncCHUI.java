package ca.pfv.spmf.test;

import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.incchui.AlgoIncCHUI;

/**
 * Example of how to use the IncCHUI algorithm for incremental high-utility itemset mining,
 * from the source code.
 * 
 * @see AlgoIncCHUI
 * @author Philippe Fournier-Viger, 2015
 */
public class MainTestIncCHUI {

	public static void main(String[] arg) throws IOException {

		// Initialize the algorithm
		AlgoIncCHUI algo = new AlgoIncCHUI();

		// Set the minimum utility threshold
		int  min_utility = 30;
		
		// Hash table size for this algorithm
        int hashtablesize = 50000;
        
		// 1) Apply the algorithm on a first file containing transactions
		System.out.println("1) Run the algorithm on the first file");

		String input1 = fileToPath("DB_UtilityIncremental1.txt");
		
		// scan the database to count the number of lines
		// for our test purpose
		int linecount1 = countLines(input1);

		algo.runAlgorithm(input1, min_utility, 0, linecount1, hashtablesize);
		algo.printStats();
		
		// Print the number of HUIs found until now to the console
		int realHUICount =  algo.getRealCHUICount();
		System.out.println("NUMBER OF HUI FOUND: " + realHUICount);
		
		// PRINT THE HUIs FOUND
//		algo.printHUIs();

		String output1 = ".//output1.txt";
		algo.writeCHUIsToFile(output1);

		// 2) Apply the algorithm on a second file containing transactions
		System.out.println("\n 2) Run the algorithm on the second file");
		
		// Applying the algorithm
		String input2 = fileToPath("DB_UtilityIncremental2.txt");
		
		// scan the database to count the number of lines
		// for our test purpose
		int linecount2 = countLines(input2);
		algo.runAlgorithm(input2, min_utility, 0, linecount1+linecount2, hashtablesize);
		algo.printStats();
		
		// Print the number of HUIs found until now to the console
		realHUICount =  algo.getRealCHUICount();
		System.out.println("NUMBER OF HUI FOUND: " + realHUICount);
		
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
		URL url = MainTestIncCHUI.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
