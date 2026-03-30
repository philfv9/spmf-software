package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
/* This file is copyright (c) 2025 Push Sra
* 
* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
* 
* SPMF is free software: you can redistribute it and/or modify it under the
* terms of the GNU General Public License as published by the Free Software
* Foundation, either version 3 of the License, or (at your option) any later
* version.
* 
* SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
* A PARTICULAR PURPOSE. See the GNU General Public License for more details.
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
*/

import ca.pfv.spmf.algorithms.frequentpatterns.rminer_sminer.AlgoRMiner;

/**
 * Example of how to use the AlgoRMiner algorithm 
 * from the source code.
 * This class demonstrates how to set up and run the
 * high utility itemset mining algorithm using the 
 * AlgoRMiner class.
 * @see AlgoRMiner
 */
public class MainTestRMiner {

    /**
     * Main method to run the AlgoRMiner algorithm.
     * @param args Command line arguments (not used).
     * @throws IOException If there is an error reading or writing files.
     */
    public static void main(String[] args) throws IOException {
        
        // Path to the input file (change the filename as needed)
        String input = fileToPath("DB_Utility.txt");
        // Uncomment and use the following lines if you want to use different input files
        // String input = fileToPath("chess.txt");
        // String input = fileToPath("retail.txt");
        
        // Path to the output file where results will be written
        String output = ".//output.txt";
        
        // Minimum utility threshold for high utility itemsets
        int minUtility = 30;  
        
        // Create an instance of AlgoRMiner
        AlgoRMiner algoRMiner = new AlgoRMiner();
        
        // Run the algorithm with the specified input, output, and minimum utility
        algoRMiner.runAlgorithm(input, output, minUtility);
        algoRMiner.printStats();
    }

    /**
     * Converts a filename to its file path.
     * @param filename The name of the file to be converted.
     * @return The file path as a String.
     * @throws UnsupportedEncodingException If encoding is not supported.
     */
    public static String fileToPath(String filename) throws UnsupportedEncodingException {
        // Get the URL of the file from the classpath
        URL url = MainTestRMiner.class.getResource(filename);
        
        // Decode and return the path of the file
        return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
    }
}
