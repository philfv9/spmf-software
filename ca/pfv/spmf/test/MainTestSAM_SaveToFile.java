package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.sam.AlgoSAM;
/* This file is copyright (c) 2008-2025 Philippe Fournier-Viger

This file is part of the SPMF DATA MINING SOFTWARE
(http://www.philippe-fournier-viger.com/spmf).
SPMF is free software: you can redistribute it and/or modify it under the
terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later
version.
SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
A PARTICULAR PURPOSE. See the GNU General Public License for more details.
You should have received a copy of the GNU General Public License along with
SPMF. If not, see http://www.gnu.org/licenses/.
*/
/**
 * Example showing file-based usage of the SAM algorithm.
 * 
 * @author Philippe Fournier-Viger, 2025
 */
public class MainTestSAM_SaveToFile {

    public static void main(String[] args) throws IOException {
        
        // Input file path
        String input = fileToPath("contextPasquier99.txt");
        
        // Output file path
        String output = "output.txt";
        
        // Minimum support threshold
        double minSupport = 0.4;
        

        // Create the algorithm
        AlgoSAM algo = new AlgoSAM();
        
        // Use this method to set the maximum pattern length
//        algo.setMaximumPatternLength(8);  
        
        // Run the algorithm
        algo.runAlgorithm(input, output, minSupport);
        
        // Print stats about the algorithm executionoutput.txt
        algo.printStats();
    }
    
    public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestSAM_SaveToFile.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}