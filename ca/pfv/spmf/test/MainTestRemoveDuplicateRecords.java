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

import ca.pfv.spmf.tools.other_dataset_tools.AlgoRemoveDuplicateRecords;

/**
 * Example of how to use the AlgoRemoveDuplicateRecords algorithm
 * from the source code.
 * 
 * @author Philippe Fournier-Viger
 * @see AlgoRemoveDuplicateRecords
 */
public class MainTestRemoveDuplicateRecords {

    public static void main(String[] args) throws IOException {
        
        // Input file path
        String inputFile = fileToPath("contextPasquier99.txt");
        
        // Output file path
        String outputFile = "output.txt";
        
        // Create an instance of the algorithm
        AlgoRemoveDuplicateRecords algorithm = new AlgoRemoveDuplicateRecords();
        
        // Run the algorithm
        algorithm.runAlgorithm(inputFile, outputFile);
        
        // Print statistics
        algorithm.printStats();
    }

    public static String fileToPath(String filename) 
            throws UnsupportedEncodingException {
        URL url = MainTestRemoveDuplicateRecords.class
                .getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
    }
}