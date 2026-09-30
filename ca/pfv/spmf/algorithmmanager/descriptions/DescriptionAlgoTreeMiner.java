package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.sequentialpatterns.treeminer.AlgoTreeMiner;

/* This file is copyright (c) 2008-2016 Philippe Fournier-Viger
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
 * This class describes the Tree-Miner algorithm parameters. It is designed to be used by the graphical and command line interface.
 * 
 * @see AlgoTreeMiner
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoTreeMiner extends DescriptionOfAlgorithm {

	/**
	 * Default constructor.
	 */
	public DescriptionAlgoTreeMiner() {
	}

	/**
	 * Get the algorithm name shown in the user interfaces.
	 * 
	 * @return the name
	 */
	@Override
	public String getName() {
		return "Tree-Miner";
	}

	/**
	 * Get the category where this algorithm is shown.
	 * 
	 * @return the category
	 */
	@Override
	public String getAlgorithmCategory() {
		return "SEQUENTIAL PATTERN MINING";
	}

	/**
	 * Get the URL of the documentation page.
	 * 
	 * @return the documentation URL
	 */
	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/TreeMiner.php";
	}

	/**
	 * Run the algorithm using parameters provided by the command line or GUI.
	 * 
	 * @param parameters the parameters of the algorithm
	 * @param inputFile  the input file path
	 * @param outputFile the output file path
	 * @throws IOException if an I/O error occurs
	 */
	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		// Create the algorithm instance.
		AlgoTreeMiner algo = new AlgoTreeMiner();

		// Optional parameter: minimum pattern length.
		if (parameters.length >= 2 && "".equals(parameters[1]) == false) {
			algo.setMinimumPatternLength(getParamAsInteger(parameters[1]));
		}

		// Optional parameter: maximum pattern length.
		if (parameters.length >= 3 && "".equals(parameters[2]) == false) {
			algo.setMaximumPatternLength(getParamAsInteger(parameters[2]));
		}

		// Run the algorithm.
		algo.runAlgorithm(inputFile, outputFile, getParamAsDouble(parameters[0]));
		algo.printStatistics();
	}

	/**
	 * Get the list of parameters for the GUI and command line interfaces.
	 * 
	 * @return the parameters description
	 */
	@Override
	public DescriptionOfParameter[] getParametersDescription() {
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[3];
		parameters[0] = new DescriptionOfParameter("Minsup (%)", "(e.g. 0.4 or 40%)", Double.class, false);
		parameters[1] = new DescriptionOfParameter("Min pattern length", "(e.g. 1 items)", Integer.class, true);
		parameters[2] = new DescriptionOfParameter("Max pattern length", "(e.g. 10 items)", Integer.class, true);
		return parameters;
	}

	/**
	 * Get the name of the author(s) of the implementation.
	 * 
	 * @return the author names
	 */
	@Override
	public String getImplementationAuthorNames() {
		return "Philippe Fournier-Viger";
	}

	/**
	 * Get the supported input file types.
	 * 
	 * @return the input file types
	 */
	@Override
	public String[] getInputFileTypes() {
		return new String[] { "Database of instances", "Sequence database", "Simple sequence database" };
	}

	/**
	 * Get the supported output file types.
	 * 
	 * @return the output file types
	 */
	@Override
	public String[] getOutputFileTypes() {
		return new String[] { "Patterns", "Sequential patterns", "Frequent sequential patterns" };
	}

	/**
	 * Get the algorithm type for the algorithm manager.
	 * 
	 * @return the algorithm type
	 */
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
}