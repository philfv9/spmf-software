package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.sequentialpatterns.sui.AlgoSUI;
/* This file is part of the SPMF DATA MINING SOFTWARE
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
* 
*  Copyright (c) Philipe Fournier-Viger et al.
*  
*  Do not remove copyright and license information.
*/
/**
 * Example of how to use the SUI algorithm from source code.
 * 
 * @author SUI Implementation
 */
public class MainTestSUI {

    public static void main(String[] args) throws IOException {

        // ===================== Input/Output Configuration =====================
        // Input file path (SPMF format)
        String inputFile = fileToPath("contextPrefixSpan.txt");

        // Output file path
        String outputPath = ".//output.txt";

        // ===================== Algorithm Parameters =====================
        // Minimum support threshold: percentage of sequences
        // (e.g., 0.4 means a pattern must appear in at least 40% of sequences)
        double minsup = 0.5;

        // ===================== Run Algorithm =====================
        // Create an instance of the SUI algorithm
        AlgoSUI algo = new AlgoSUI();
        
     // This optional parameter allows to specify the minimum pattern length:
//        algo.setMinimumPatternLength(1);  // optional

        // This optional parameter allows to specify the maximum pattern length:
//        algo.setMaximumPatternLength(2);  // optional

        // Execute the algorithm with the specified parameters
        algo.runAlgorithm(inputFile, outputPath, minsup);

        // Print statistics about the execution
        algo.printStatistics();
    }

    /**
     * Convert a filename to a full path, looking in the same package directory.
     *
     * @param filename the name of the file
     * @return the full file path as a string
     * @throws UnsupportedEncodingException if UTF-8 encoding is not supported
     */
    public static String fileToPath(String filename) throws UnsupportedEncodingException {
        URL url = MainTestSUI.class.getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
    }
}