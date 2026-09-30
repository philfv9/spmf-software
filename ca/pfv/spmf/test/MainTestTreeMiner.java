package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.sequentialpatterns.treeminer.AlgoTreeMiner;
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
*  Copyright (c) Philipe Fournier-Viger 
*  
*  Do not remove copyright and license information.
*/
/**
 * Example of how to use the Tree-Miner algorithm in source code.
 * @author Philippe Fournier-Viger
 */
public class MainTestTreeMiner {

    public static void main(String[] args) throws IOException {
        // input file
        String inputFile = fileToPath("contextPrefixSpan.txt");

        // output file path
        String outputPath = ".//output.txt";

        // minimum support threshold as a percentage of sequences (e.g. 0.5 = 50%)
        double minsup = 0.5;

        // Create an instance of the algorithm
        AlgoTreeMiner algo = new AlgoTreeMiner();
        
     // This optional parameter allows to specify the minimum pattern length:
        algo.setMinimumPatternLength(1);  // optional

        // This optional parameter allows to specify the maximum pattern length:
        algo.setMaximumPatternLength(2);  // optional

        // Set algorithm options
//        algo.enablePruning = true;  // enable/disable pruning optimizations

        // execute the algorithm
        algo.runAlgorithm(inputFile, outputPath, minsup);
        algo.printStatistics();
    }

    public static String fileToPath(String filename) throws UnsupportedEncodingException {
        URL url = MainTestTreeMiner.class.getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
    }
}