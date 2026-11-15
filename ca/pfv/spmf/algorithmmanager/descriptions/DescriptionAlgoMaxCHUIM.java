/* This file is copyright (c) 2026 Philippe Fournier-Viger
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
 * Do not remove copyright and license information.
 */

package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.File;
import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.frequentpatterns.chuim_maxchuim.AlgoMaxCHUIM;

/**
 * This class describes the MaxCHUIM algorithm for the SPMF interfaces.
 * The main output contains closed patterns with a maximal sidecar by default,
 * or only maximal patterns when the optional output parameter is true.
 *
 * @see AlgoMaxCHUIM
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoMaxCHUIM extends DescriptionOfAlgorithm {

	/** Create the algorithm description with its default parameters. */
	public DescriptionAlgoMaxCHUIM() {
	}

	/**
	 * Get the interface name of this algorithm.
	 *
	 * @return the algorithm name
	 */
	@Override
	public String getName() {
		return "MaxC-HUIM";
	}

	/**
	 * Get the interface category of this algorithm.
	 *
	 * @return the high-utility mining category
	 */
	@Override
	public String getAlgorithmCategory() {
		return "HIGH-UTILITY PATTERN MINING";
	}

	/**
	 * Get the reference describing closed and maximal high-utility patterns.
	 *
	 * @return the DOI of the supplied reference paper
	 */
	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/MaxCHUIM.php";
	}

	/**
	 * Apply the algorithm with the supplied SPMF interface parameters.
	 *
	 * @param parameters the inclusive minimum utility and optional maximal-only flag
	 * @param inputFile  the original utility transaction file
	 * @param outputFile the maximal itemset file path
	 * @throws IOException if input, validation or output fails
	 */
	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		AlgoMaxCHUIM algo = new AlgoMaxCHUIM();
		long minUtility = getParamAsLong(parameters[0]);
		String closedItemsetPath = null;
		if(parameters.length > 1 && !parameters[1].isEmpty()) {
			closedItemsetPath = getParamAsString(parameters[1]);
			File file = new File(inputFile);
			if (file.getParent() != null) {
				closedItemsetPath = file.getParent() + File.separator + closedItemsetPath;
			}
		}
		algo.runAlgorithm(inputFile, closedItemsetPath, outputFile, minUtility);
		algo.printStats();
	}

	/**
	 * Get the threshold and optional output parameter descriptions for the SPMF interfaces.
	 *
	 * @return the minimum utility and optional maximal-only flag
	 */
	@Override
	public DescriptionOfParameter[] getParametersDescription() {
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[2];
		parameters[0] = new DescriptionOfParameter("Minimum utility", "(e.g. 370)", Long.class, false);
		parameters[1] = new DescriptionOfParameter("Closed itemset file", "(e.g. closed.txt)", String.class, true);
		return parameters;
	}

	/**
	 * Get the author credited for the implementation.
	 *
	 * @return the implementation author name
	 */
	@Override
	public String getImplementationAuthorNames() {
		return "Philippe Fournier-Viger";
	}

	/**
	 * Get the accepted input format hierarchy.
	 *
	 * @return the utility transaction formats
	 */
	@Override
	public String[] getInputFileTypes() {
		return new String[] {"Database of instances", "Transaction database", "Transaction database with utility values"};
	}

	/**
	 * Get the produced concise pattern format hierarchy.
	 *
	 * @return the output pattern formats
	 */
	@Override
	public String[] getOutputFileTypes() {
		return new String[] {"Patterns", "High-utility patterns", "High-utility itemsets", "Maximal high-utility itemsets"};
	}

	/**
	 * Get the SPMF interface type of this algorithm.
	 *
	 * @return a mining algorithm or pattern postprocessor type
	 */
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
}
