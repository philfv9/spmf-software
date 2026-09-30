package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.sequentialpatterns.prefixspan.AlgoPrefixSpan;
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
 * This class describes the PrefixSpan algorithm parameters. It is designed to be used by the graphical and command line interface.
 * 
 * @see AlgoPrefixSpan
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoPrefixSpan extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoPrefixSpan(){
	}

	/**
	 * Returns the name of this algorithm.
	 * @return the algorithm name as a string
	 */
	@Override
	public String getName() {
		return "PrefixSpan";
	}

	/**
	 * Returns the category of this algorithm.
	 * @return the algorithm category as a string
	 */
	@Override
	public String getAlgorithmCategory() {
		return "SEQUENTIAL PATTERN MINING";
	}

	/**
	 * Returns the URL of the documentation for this algorithm.
	 * @return the documentation URL as a string
	 */
	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/PrefixSpan.php";
	}

	/**
	 * Runs the PrefixSpan algorithm using the given parameters, input file and output file.
	 * @param parameters an array of string parameters for the algorithm
	 * @param inputFile the input file path
	 * @param outputFile the output file path
	 * @throws IOException if an error occurs while reading/writing files
	 */
	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {

		// Get the parameter "minsup"
		double minsup = getParamAsDouble(parameters[0]);
		
		AlgoPrefixSpan algo = new AlgoPrefixSpan();

		// Get the optional parameter for minimum pattern length
		if (parameters.length >=2 && "".equals(parameters[1]) == false) {
			algo.setMinimumPatternLength(getParamAsInteger(parameters[1]));
		}
		
		// Get the optional parameter for maximum pattern length
		if (parameters.length >=3 && "".equals(parameters[2]) == false) {
			algo.setMaximumPatternLength(getParamAsInteger(parameters[2]));
		}
		
		// Get the optional parameter to show sequence ids
		if (parameters.length >=4 && "".equals(parameters[3]) == false) {
			algo.setShowSequenceIdentifiers(getParamAsBoolean(parameters[3]));
		}
		
		// Run the algorithm
		algo.runAlgorithm(inputFile, minsup, outputFile);
		algo.printStatistics();
	}

	/**
	 * Returns the description of the parameters accepted by this algorithm.
	 * @return an array of parameter descriptions
	 */
	@Override
	public DescriptionOfParameter[] getParametersDescription() {
        
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[4];
		parameters[0] = new DescriptionOfParameter("Minsup (%)", "(e.g. 0.4 or 40%)", Double.class, false);
		parameters[1] = new DescriptionOfParameter("Min pattern length", "(e.g. 1 items)", Integer.class, true);
		parameters[2] = new DescriptionOfParameter("Max pattern length", "(e.g. 10 items)", Integer.class, true);
		parameters[3] = new DescriptionOfParameter("Show sequence ids?", "(default: false)", Boolean.class, true);
		return parameters;
	}

	/**
	 * Returns the names of the authors of this implementation.
	 * @return the author names as a string
	 */
	@Override
	public String getImplementationAuthorNames() {
		return "Philippe Fournier-Viger";
	}

	/**
	 * Returns the types of input files supported by this algorithm.
	 * @return an array of input file type names
	 */
	@Override
	public String[] getInputFileTypes() {
		return new String[]{"Database of instances","Sequence database", "Simple sequence database"};
	}

	/**
	 * Returns the types of output files produced by this algorithm.
	 * @return an array of output file type names
	 */
	@Override
	public String[] getOutputFileTypes() {
		return new String[]{"Patterns", "Sequential patterns", "Frequent sequential patterns"};
	}
//
//	@Override
//	String[] getSpecialInputFileTypes() {
//		return null; //new String[]{"ARFF"};
//	}
	
	/**
	 * Returns the type of this algorithm.
	 * @return the algorithm type
	 */
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
	
}