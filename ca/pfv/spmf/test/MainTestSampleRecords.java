package ca.pfv.spmf.test;

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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.tools.other_dataset_tools.AlgoSampleRecords;

/**
 * Example of how to use the AlgoSampleRecords algorithm
 * from the source code.
 * 
 * @author Philippe Fournier-Viger
 * @see AlgoSampleRecords
 */
public class MainTestSampleRecords {

    public static void main(String[] args) throws IOException {
        
        // Input file path
        String inputFile = fileToPath("contextPasquier99.txt");
        
        // Output file path
        String outputFile = "output.txt";
        
        // Create an instance of the algorithm 
        AlgoSampleRecords algorithm = new AlgoSampleRecords();
        
        // Or create an instance of the algorithm with a fixed seed for reproducibility
//        AlgoSampleRecords algorithm = new AlgoSampleRecords(12345);
        
        // ===== Example 1: Sample a fixed number of records without replacement =====
        System.out.println("Example 1: Sample 3 records without replacement");
        algorithm.runAlgorithm(inputFile, outputFile, 3, false);
        algorithm.printStats();
        
        // ===== Example 2: Sample a fixed number of records with replacement =====
        System.out.println("\nExample 2: Sample 5 records with replacement");
        algorithm.runAlgorithm(inputFile, outputFile, 5, true);
        algorithm.printStats();
        
        // ===== Example 3: Sample a percentage of records =====
        System.out.println("\nExample 3: Sample 50% of records without replacement");
        algorithm.runAlgorithmPercentage(inputFile, outputFile, 0.5, false);
        algorithm.printStats();
        
        // ===== Example 4: Use reservoir sampling (memory efficient) =====
        System.out.println("\nExample 4: Reservoir sampling (3 records)");
        algorithm.runAlgorithmReservoir(inputFile, outputFile, 3);
        algorithm.printStats();
    }

    public static String fileToPath(String filename) 
            throws UnsupportedEncodingException {
        URL url = MainTestSampleRecords.class
                .getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
    }
}