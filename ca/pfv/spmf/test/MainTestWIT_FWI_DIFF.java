package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.witfwi.AlgoWIT_FWI_DIFF;
/* This file is copyright (c) 2008-2026 Philippe Fournier-Viger
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
/**
 * Example of how to use the WIT-FWI-DIFF algorithm from the source code.
 *
 * @author Philippe Fournier-Viger
 */
public class MainTestWIT_FWI_DIFF {

    public static void main(String[] arg) throws IOException {
        String inputDatabase = fileToPath("DB_RWFIM.txt");
        String inputWeights = fileToPath("weights_RWFIM.txt");
        String output = ".//output.txt";

        double minWeightedSupport = 0.40; // 40%

        // Applying the WIT-FWI-DIFF algorithm
        AlgoWIT_FWI_DIFF algo = new AlgoWIT_FWI_DIFF();
        algo.runAlgorithm(inputDatabase, inputWeights, output, minWeightedSupport);
        algo.printStats();
    }

    public static String fileToPath(String filename) throws UnsupportedEncodingException {
        URL url = MainTestWIT_FWI_DIFF.class.getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
    }
}