/* This file is copyright (c) 2020 Mourad Nouioua et al.
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
*/
package ca.pfv.spmf.algorithms.frequentpatterns.vhuqi;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.fhuqiminer.AlgoFHUQIMiner;
import ca.pfv.spmf.algorithms.frequentpatterns.fhuqiminer.EnumCombination;

/**
 * Class that shows how to run the FHUQI-Miner algorithm from the source code.
 * @author Mourad Nouioua et al. 2020
 */
public class MainTestVHUQI {

	/**
	 * @param args the command line arguments
	 */
	public static void main(String[] args) throws IOException {
		
		// The input file path of the file indicating the profit value of each item
		String inputFileProfitPath = fileToPath("HUQI_DB_profit.txt");
		
		// the input file path containing the transactions with quantities
		String inputFileDBPath = fileToPath("HUQI_DB.txt");
		
		// the output file path for writing the result
		String output = "output.txt";
		
		// The minimum utility threshold.  eg. 0.10 = 10 percent
		float percentage = 0.20f;
		
		// The related quantitative coefficient
		int coef = 3;
		
		// The combination method  (there are three possibilities )
//		EnumCombination combinationmethod = EnumCombination.COMBINEMIN;
//		EnumCombination combinationmethod = EnumCombination.COMBINEMIN;	
		EnumCombination combinationmethod = EnumCombination.COMBINEALL;
		
		// Run the algorithm
		AlgoVHUQI algo1 = new AlgoVHUQI();
		algo1.runAlgorithm(inputFileDBPath, inputFileProfitPath, percentage, coef, combinationmethod, output);
		algo1.printStatistics();
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestVHUQI.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}

}
