package ca.pfv.spmf.algorithms.frequentpatterns.uhmine;

/* This file is copyright (c) 2008-2015 Philippe Fournier-Viger

 This file is part of the SPMF DATA MINING SOFTWARE
 (http://www.philippe-fournier-viger.com/spmf).
 SPMF is free software: you can redistribute it and/or modify it under the
 terms of the GNU General Public License as published by the Free Software
 Foundation, either version 3 of the License, or (at your option) any later
 version.
 SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 You should have received a copy of the GNU General Public License along with
 SPMF. If not, see http://www.gnu.org/licenses/.
 */

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.uhmine.AlgoUHMine;
import ca.pfv.spmf.algorithms.frequentpatterns.uhmine.UncertainTransactionDatabaseUHMine;

/**
 * Example of how to use the UH-Mine algorithm from source code.
 *
 * @author Philippe Fournier-Viger, 2015
 */
public class MainTestUHMine_saveToFile {

    public static void main(String[] arg) throws IOException {

        // load the uncertain transaction database
        UncertainTransactionDatabaseUHMine database =
                new UncertainTransactionDatabaseUHMine();
        try {
            database.loadFile(fileToPath("contextUncertain.txt"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        database.printDatabase();

        // path of the output file
        String output = ".//output.txt";

        // apply the UH-Mine algorithm with minimum expected support = 10%
        AlgoUHMine algo = new AlgoUHMine();

        // Uncomment the following line to set the maximum pattern length
        // algo.setMaximumPatternLength(2);

        algo.runAlgorithm(database, 0.1, output);
        algo.printStats();
    }

    /**
     * Convert a resource filename to its full file path.
     *
     * @param filename the filename of a resource in the same package as this class
     * @return the full file path
     * @throws UnsupportedEncodingException if the encoding is not supported
     */
    public static String fileToPath(String filename) throws UnsupportedEncodingException {
        URL url = MainTestUHMine_saveToFile.class.getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
    }
}