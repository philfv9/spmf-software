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
import ca.pfv.spmf.algorithms.frequentpatterns.huo.AlgoMaxCloFHUOIM;

/**
 * This class describes the MaxCloFHUOIM algorithm for the SPMF interfaces.
 *
 * @see AlgoMaxCloFHUOIM
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoMaxCloFHUOIM extends DescriptionOfAlgorithm {

	/** Create the algorithm description with its default parameters. */
	public DescriptionAlgoMaxCloFHUOIM() {
	}

	/**
	 * Get the interface name of this algorithm.
	 *
	 * @return the algorithm name
	 */
	@Override
	public String getName() {
		return "MaxCloFHUOIM";
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
	 * Get the reference describing closed and maximal utility-occupancy patterns.
	 *
	 * @return the DOI of the supplied reference paper
	 */
	@Override
	public String getURLOfDocumentation() {
		return "https://philippe-fournier-viger.com/spmf/maxclofhuoim.php";
	}

	/**
	 * Apply the algorithm with the supplied SPMF interface parameters.
	 *
	 * @param parameters the two thresholds and optional maximal-only flag
	 * @param inputFile  the original utility transaction file
	 * @param outputFile the closed output with a maximal sidecar, or the sole maximal output
	 * @throws IOException if input, validation or output fails
	 */
	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		AlgoMaxCloFHUOIM algo = new AlgoMaxCloFHUOIM();
		double minSupport = getParamAsDouble(parameters[0]);
		double minUtilityOccupancy = getParamAsDouble(parameters[1]);
		String closedItemsetPath = null;
		if(parameters.length > 2 && !parameters[2].isEmpty()) {
			closedItemsetPath = getParamAsString(parameters[2]);
			File file = new File(inputFile);
			if (file.getParent() != null) {
				closedItemsetPath = file.getParent() + File.separator + closedItemsetPath;
			}
		}
		algo.runAlgorithm(inputFile, closedItemsetPath, outputFile, minSupport, minUtilityOccupancy);
		algo.printStats();
	}

	/**
	 * Get the threshold and optional output parameter descriptions for the SPMF interfaces.
	 *
	 * @return the thresholds and optional maximal-only flag
	 */
	@Override
	public DescriptionOfParameter[] getParametersDescription() {
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[3];
		parameters[0] = new DescriptionOfParameter("Minimum support (%)", "(e.g. 0.3 or 30%)", Double.class, false);
		parameters[1] = new DescriptionOfParameter("Minimum utility occupancy", "(e.g. 0.3)", Double.class, false);
		parameters[2] = new DescriptionOfParameter("Closed itemset file", "(e.g. closed.txt)", String.class, true);
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
	 * @return transaction formats, or complete FHUOI pattern formats for postprocessing
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
		return new String[] {"Patterns", "High-utility patterns", "High utility-occupancy itemsets", "Maximal high utility-occupancy itemsets"};
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
