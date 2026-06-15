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
*
* Do not remove copyright or license information from this file.
*/

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.wfim.AlgoWFIM;

/**
 * Example of how to use the WFIM algorithm from the source code.
 *
 * @see AlgoWFIM
 * @author Philippe Fournier-Viger
 */
public class MainTestWFIM {

    /**
     * Main method.
     *
     * @param arg command-line arguments (not used)
     * @throws IOException if a file operation fails
     */
    public static void main(String[] arg) throws IOException {

        String inputDatabase = fileToPath("DB_RWFIM.txt");
        String inputWeights  = fileToPath("weights_RWFIM.txt");
        String output        = ".//output_WFIM.txt";

        double minSupRatio = 0.40;   // 40%
        double minWeight   = 0.5;    // no minimum weight constraint

        AlgoWFIM algo = new AlgoWFIM();
        algo.runAlgorithm(inputDatabase, inputWeights, output, minSupRatio, minWeight);
        algo.printStats();
    }

    /**
     * Converts a filename to its full path using the class resource loader.
     *
     * @param filename the filename to resolve
     * @return the decoded absolute path
     * @throws UnsupportedEncodingException if UTF-8 decoding fails
     */
    public static String fileToPath(String filename) throws UnsupportedEncodingException {
        URL url = MainTestWFIM.class.getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
    }
}