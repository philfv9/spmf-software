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

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.frequentpatterns.chuim_maxchuim.AlgoCHUIM;

/**
 * This class describes the CHUIM algorithm for the SPMF interfaces.
 * The main output contains closed patterns.
 *
 * @see AlgoCHUIM
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoCHUIM extends DescriptionOfAlgorithm {

	/** Create the algorithm description with its default parameters. */
	public DescriptionAlgoCHUIM() {
	}

	/**
	 * Get the interface name of this algorithm.
	 *
	 * @return the algorithm name
	 */
	@Override
	public String getName() {
		return "C-HUIM";
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
		return "http://www.philippe-fournier-viger.com/spmf/CHUIM_closed.php";
	}

	/**
	 * Apply the algorithm with the supplied SPMF interface parameters.
	 *
	 * @param parameters the inclusive minimum utility
	 * @param inputFile  the original utility transaction file
	 * @param outputFile the closed output, with an additional .maximal sidecar when applicable
	 * @throws IOException if input, validation or output fails
	 */
	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		AlgoCHUIM algo = new AlgoCHUIM();
		long minUtility = getParamAsLong(parameters[0]);
		algo.runAlgorithm(inputFile, outputFile, minUtility);
		algo.printStats();
	}

	/**
	 * Get the mandatory parameter descriptions for the SPMF interfaces.
	 *
	 * @return the minimum-utility parameter
	 */
	@Override
	public DescriptionOfParameter[] getParametersDescription() {
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[1];
		parameters[0] = new DescriptionOfParameter("Minimum utility", "(e.g. 370)", Long.class, false);
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
		return new String[] {"Patterns", "High-utility patterns", "High-utility itemsets", "Closed high-utility itemsets"};
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
