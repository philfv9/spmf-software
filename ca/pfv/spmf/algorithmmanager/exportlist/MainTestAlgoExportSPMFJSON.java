package ca.pfv.spmf.algorithmmanager.exportlist;

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
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * Example of how to use the AlgoExportSPMFJSON utility in source code.
 * This program generates a "spmf.json" file containing metadata
 * about all algorithms supported in the SPMF library.
 *
 * @author Philippe 
 */
public class MainTestAlgoExportSPMFJSON {

    public static void main(String[] args) throws Exception {

        // Output file path
        String outputFile = "./spmf.json";

        // Create instance of exporter
        AlgoExportSPMFJSON algo = new AlgoExportSPMFJSON();

        // Execute exporter (parameters and inputFile are unused)
        algo.runAlgorithm(outputFile);

        System.out.println("=== Export completed ===");
        System.out.println("SPMF algorithm metadata saved to: " + outputFile);
    }

}
