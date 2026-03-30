package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;
/* This file is copyright (c) 2008-2022 Philippe Fournier-Viger
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

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.frequentpatterns.srpfpm.AlgoSRPFPM;

/**
 * This class describes the SRPFPM algorithm parameters.
 * It is designed to be used by the graphical and command line interface.
 * 
 * @see AlgoSRPFPM
 * @author Vincent M. Nofong (modified from Philippe Fournier-Viger's implementation of FPGrowth)
 */
public class DescriptionAlgoSRPFPM extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoSRPFPM(){
	}

	@Override
	public String getName() {
		return "SRPFPM";
	}

	@Override
	public String getAlgorithmCategory() {
		return "PERIODIC PATTERN MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/period_SRPFPM_itemset.php";
	} // 22819

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		double minsup = getParamAsDouble(parameters[0]);
		double minPeriod = getParamAsInteger(parameters[1]);  // transactions
		double maxPeriod = getParamAsInteger(parameters[2]);  // transactions
		double minAverage = getParamAsInteger(parameters[3]);  // transactions
		double maxAverage = getParamAsInteger(parameters[4]);  // transactions
		AlgoSRPFPM algorithm = new AlgoSRPFPM();
		
		if (parameters.length >=2 && "".equals(parameters[1]) == false) {
			algorithm.setMaximumPatternLength(getParamAsInteger(parameters[1]));
		}
		if (parameters.length >=3 && "".equals(parameters[2]) == false) {
			algorithm.setMinimumPatternLength(getParamAsInteger(parameters[2]));
		}
		algorithm. runAlgorithm(inputFile, outputFile, minsup, minAverage, maxAverage, minPeriod, maxPeriod);
		algorithm.printStats();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {

		DescriptionOfParameter[] parameters = new DescriptionOfParameter[7];
		parameters[0] = new DescriptionOfParameter("Minsup (%)", "(e.g. 0.4 or 40%)", Double.class, false);
		parameters[1] = new DescriptionOfParameter("Minimum periodicity", "(e.g. 1 transactions)", Integer.class, false);
		parameters[2] = new DescriptionOfParameter("Maximum periodicity", "(e.g. 3 transactions)", Integer.class, false);
		parameters[3] = new DescriptionOfParameter("Minimum average periodicity", "(e.g. 1 transactions)", Integer.class, false);
		parameters[4] = new DescriptionOfParameter("Maximum average periodicity", "(e.g. 2 transactions)", Integer.class, false);
		// optional parameters
		parameters[5] = new DescriptionOfParameter("Minimum number of items", "(e.g. 1 items)", Integer.class, true);
		parameters[6] = new DescriptionOfParameter("Maximum number of items", "(e.g. 5 items)", Integer.class, true);
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Vincent M. Nofong modified from Philippe Fournier-Viger";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[]{"Database of instances","Transaction database", "Simple transaction database"};
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[]{"Patterns", "Frequent patterns", "Frequent itemsets"};
	}
	
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
	
}
