package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.File;
import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.frequentpatterns.witfwi.AlgoWIT_FWI_DIFF;

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

/**
 * This class describes the WIT-FWI-DIFF algorithm parameters.
 * It is designed to be used by the graphical and command line interface.
 *
 * @see AlgoWIT_FWI_DIFF
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoWIT_FWI_DIFF extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoWIT_FWI_DIFF() {
	}

	@Override
	public String getName() {
		return "WIT-FWI-DIFF";
	}

	@Override
	public String getAlgorithmCategory() {
		return "FREQUENT ITEMSET MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/WIT_FWI.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile)
			throws IOException {
		String inputWeights = parameters[0];
		double minWeightedSupport = getParamAsDouble(parameters[1]);

		File file = new File(inputFile);

		String weightsPath;
		if (file.getParent() == null) {
			weightsPath = inputWeights;
		} else {
			weightsPath = file.getParent() + File.separator + inputWeights;
		}

		// Applying the WIT-FWI-DIFF algorithm
		AlgoWIT_FWI_DIFF algorithm = new AlgoWIT_FWI_DIFF();
		algorithm.runAlgorithm(inputFile, weightsPath, outputFile, minWeightedSupport);
		algorithm.printStats();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[2];
		parameters[0] = new DescriptionOfParameter("Weight table file",
				"(weights_RWFIM.txt)", String.class, false);
		parameters[1] = new DescriptionOfParameter("Min weighted support",
				"(e.g. 0.40 or 40%)", Double.class, false);
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Philippe Fournier-Viger";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[]{"Transaction database", "Simple transaction database"};
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[]{"Patterns", "Frequent weighted itemsets"};
	}

	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
}