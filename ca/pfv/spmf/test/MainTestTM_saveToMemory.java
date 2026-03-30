package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.tm.AlgoTM;
import ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemsets;
/* This file is copyright (c) 2008-2015 Philippe Fournier-Viger
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
/**
 * Example of how to use the TM algorithm and keep results in memory.
 * 
 * @author Philippe Fournier-Viger
 */
public class MainTestTM_saveToMemory {

    public static void main(String[] args) throws IOException {
        
        // Input file path
        String input = fileToPath("contextPasquier99.txt");
        
        // Minimum support threshold (as a ratio)
        double minsup = 0.4; // 40%
        
        // Create the algorithm object
        AlgoTM algorithm = new AlgoTM();
        
        // Optional: set the compression threshold of the algorithm for performance adjustment
//        algorithm.setCompressionThreshold(2.0);
        
        // Run the algorithm (output = null means save to memory)
        Itemsets frequentItemsets = algorithm.runAlgorithm(input, null, minsup);
        
        // Print statistics
        algorithm.printStats();
        
        // Print the frequent itemsets
        frequentItemsets.printItemsets(algorithm.getTransactionCount());
    }

    public static String fileToPath(String filename) throws UnsupportedEncodingException {
        URL url = MainTestTM_saveToMemory.class.getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
    }
}