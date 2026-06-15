package ca.pfv.spmf.test;

/* This file is copyright (c) 2008-2025 Philippe Fournier-Viger
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
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 */

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.associationrules.pnar.AlgoPNAR;
import ca.pfv.spmf.algorithms.associationrules.pnar.RulesPNAR;
import ca.pfv.spmf.algorithms.associationrules.pnar.AlgoPNAR.MiningAlgorithm;

/**
 * Example of how to use the PNAR algorithm from source code.
 *
 * @author Philippe Fournier-Viger, 2025
 */
public class MainTestPNAR {

    public static void main(String[] args) throws IOException {

        // --- Input / output paths ---
        String input  = fileToPath("contextIGB.txt");
        String output = null;   // set to null to keep in memory

        // --- Thresholds ---
        double minsupp = 0.3;   // minimum support  (30 % of transactions)
        double minconf = 0.6;  // minimum confidence (60%)

        // --- Rule type selection (all enabled by default) ---
        boolean generateR1 = true;   // X ==> Y
        boolean generateR2 = true;   // NOT(X) ==> NOT(Y)
        boolean generateR3 = true;   // X ==> NOT(Y)
        boolean generateR4 = true;   // NOT(X) ==> Y

        // --- Run PNAR ---
        AlgoPNAR algo = new AlgoPNAR();
        algo.setGenerateR1(generateR1);
        algo.setGenerateR2(generateR2);
        algo.setGenerateR3(generateR3);
        algo.setGenerateR4(generateR4);

        // Choice of mining algorithm
//        algo.setMiningAlgorithm(MiningAlgorithm.APRIORI_TID);
        algo.setMiningAlgorithm(MiningAlgorithm.ECLAT);
        
        // Run the algorithm
        RulesPNAR rules = algo.runAlgorithm(input, output, minsupp, minconf);
        
        // Print stats to console
        algo.printStats();

        // If saving to memory (output == null), print the rules
        if (output == null && rules != null) {
            rules.printRules();
        } else {
            System.out.println("Rules have been saved to: " + output);
        }
    }

    /**
     * Resolve a resource filename to an absolute file path.
     *
     * @param filename the name of the resource file
     * @return the absolute path as a String
     * @throws UnsupportedEncodingException if UTF-8 is not supported
     */
    public static String fileToPath(String filename) throws UnsupportedEncodingException {
        URL url = MainTestPNAR.class.getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
    }
}