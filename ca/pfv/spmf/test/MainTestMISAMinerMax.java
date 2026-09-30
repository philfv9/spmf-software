package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.misaminer.AlgoMISAMiner;
/* This file is copyright (c) 2008-2024 Philippe Fournier-Viger
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
 * Example of how to use the MiSA-Miner algorithm from the source code, for
 *  maximal frequent itemset (MFI) mining.
 * 
 * @author Philippe Fournier-Viger
 */
public class MainTestMISAMinerMax {

    public static void main(String[] args) throws IOException {
        
        // Input file path
        String input = fileToPath("contextPasquier99.txt");
        
        // Minimum support threshold (as a percentage)
        double minsup = 0.4;
        
        // ============ Example : Mining Maximal Frequent Itemsets (MFI) ============
        String outputMFI = "output_MFI.txt";
        AlgoMISAMiner algoMFI = new AlgoMISAMiner();
        algoMFI.runAlgorithm(input, outputMFI, minsup, false);
        algoMFI.printStats();
    }

    public static String fileToPath(String filename) throws UnsupportedEncodingException {
        URL url = MainTestMISAMinerMax.class.getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
    }
}