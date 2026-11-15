package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.ppfpgrowth.AlgoPPFPgrowth;

/*
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. SPMF is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright (c) Illyes Hamimid et al. 2026.
 * 
 * Do not remove the copyright and license information.
 */

/**
 * Example of how to use the PPFP-growth algorithm from the source code and
 * save the result to a file. The input is the example database of the PPFP
 * paper (items a..e encoded as 1..5), which must be on the classpath.
 * <p>
 * Expected output (minSup = 35%, maxPer = 4, minimum probability = 0.6):
 * <pre>
 * 1 #SUP: 4.2 #MAXPER: 3 #PROBPER: 1
 * 1 3 #SUP: 3.6 #MAXPER: 3 #PROBPER: 0.7168
 * 2 #SUP: 4.5 #MAXPER: 3 #PROBPER: 0.81
 * 3 #SUP: 7.6 #MAXPER: 2 #PROBPER: 0.9904128
 * </pre>
 * (the order of the lines may differ).
 */
public class MainTestPPFPgrowth_saveToFile {

    /**
     * Run the example.
     *
     * @param arg not used
     * @throws IOException if the input file cannot be read or the output file cannot be written
     */
    public static void main(String[] arg) throws IOException {
        String input = fileToPath("contextUncertainPPFP.txt");
        String output = ".//output.txt";

        int maxPer = 4;               // maximum periodicity, in transactions
        double minSup = 0.35;         // minimum expected support, as a fraction of the transactions
        double minProbability = 0.6;  // minimum probability of periodicity

        AlgoPPFPgrowth algorithm = new AlgoPPFPgrowth();
        
        // Uncomment to limit the number of items per pattern
        // algorithm.setMaximumPatternLength(2);
        algorithm.runAlgorithm(input, output, maxPer, minSup, minProbability);
        algorithm.printStats();
    }

    /**
     * Return the path of a file located on the classpath.
     *
     * @param filename the name of the file
     * @return the decoded path of the file
     * @throws UnsupportedEncodingException if UTF-8 is not supported
     */
    public static String fileToPath(String filename) throws UnsupportedEncodingException {
        URL url = MainTestPPFPgrowth_saveToFile.class.getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
    }
}
