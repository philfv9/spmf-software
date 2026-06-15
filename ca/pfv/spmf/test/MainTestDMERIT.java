package ca.pfv.spmf.test;
/*
 * This file is copyright (c) 2008-2013 Philippe Fournier-Viger
 *
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 * SPMF is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see http://www.gnu.org/licenses/.
 * Do not remove copyright or license information from this file.
 */
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.dmerit.AlgoDMERIT;

/**
 * Example of how to use the dMERIT+ algorithm in source code.
 *
 * @author Philippe Fournier-Viger
 */
public class MainTestDMERIT {

    /**
     * Main method to run the dMERIT+ algorithm on a sample product database.
     *
     * @param arg command-line arguments (not used)
     * @throws NumberFormatException if the file contains invalid numbers
     * @throws IOException           if an error occurs reading or writing files
     */
    public static void main(String[] arg) throws NumberFormatException, IOException {

        // path to the input database
        String input = fileToPath("contextVME.txt");

        // path for saving the erasable itemsets found
        String output = ".//output.txt";

        // a threshold of 15%
        double threshold = 0.15;

        // apply the dMERIT+ algorithm
        AlgoDMERIT algo = new AlgoDMERIT();

        // uncomment the following line to set the maximum pattern length
        // algo.setMaximumPatternLength(2);

        algo.runAlgorithm(input, output, threshold);
        algo.printStats();
    }

    /**
     * Convert a filename to its full file-system path using the class loader.
     *
     * @param filename the name of the file
     * @return the full path to the file as a string
     * @throws UnsupportedEncodingException if UTF-8 encoding is not supported
     */
    public static String fileToPath(String filename) throws UnsupportedEncodingException {
        URL url = MainTestDMERIT.class.getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
    }
}