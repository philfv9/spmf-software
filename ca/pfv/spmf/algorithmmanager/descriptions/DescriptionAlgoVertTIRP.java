package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.sequentialpatterns.fasttirp.AlgoFastTIRP;
/* This file is copyright (c) 2008-2026 Philippe Fournier-Viger
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
 * Do not remove copyright and license information
 */
/**
 * This class describes the VertTIRP algorithm parameters. It is designed to be used by the graphical and command line interface.
 * 
 * @see AlgoFastTIRP
 * @author Philippe Fournier-Viger and Yuechun Li
 */
public class DescriptionAlgoVertTIRP extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoVertTIRP(){
	}

	@Override
	public String getName() {
		return "VertTIRP";
	}

	@Override
	public String getAlgorithmCategory() {
		return "SEQUENTIAL PATTERN MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/verttirp_time.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		AlgoFastTIRP algo = new AlgoFastTIRP();
		
		// Minimum vertical support
		double minVertSup = getParamAsDouble(parameters[0]);

		// Minimum horizontal support
		double minHorSup = getParamAsDouble(parameters[1]);

		// Epsilon
		int epsilon = getParamAsInteger(parameters[2]);

		// Minimum gap
		int mingap = getParamAsInteger(parameters[3]);

		// Maximum gap
		int maxgap = getParamAsInteger(parameters[4]);

		// Minimum duration
		int minDuration = 0;

		// Maximum duration
		int maxDuration = getParamAsInteger(parameters[6]);

		// Maximum pattern length
		int maxPatternLength = getParamAsInteger(parameters[5]);

		// If true, details about each matching sequence for each pattern will be
		// displayed
		boolean detailedOutput = getParamAsBoolean(parameters[7]);

		// execute the algorithm with minsup
		algo.runAlgorithm(inputFile, outputFile, minHorSup, minVertSup, epsilon, mingap, maxgap, minDuration, maxDuration,
				maxPatternLength, detailedOutput, false);
		
		algo.printStats();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
        
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[8];
		parameters[0] = new DescriptionOfParameter("MinVertSup (%)", "(e.g. 0.5 or 50%)", Double.class, false);
		parameters[1] = new DescriptionOfParameter("MinHorSup (%)", "(e.g. 0.1 or 10%)", Double.class, false);
		parameters[2] = new DescriptionOfParameter("Epsilon", "(e.g. 1)", Integer.class, false);
		parameters[3] = new DescriptionOfParameter("Min gap", "(e.g. 0)", Integer.class, false);
		parameters[4] = new DescriptionOfParameter("Max gap", "(e.g. 5)", Integer.class, false);
		parameters[5] = new DescriptionOfParameter("Max pattern length", "(e.g. 3)", Integer.class, false);
		parameters[6] = new DescriptionOfParameter("Max duration", "(e.g. 10)", Integer.class, false);
		parameters[7] = new DescriptionOfParameter("Detailed output", "(e.g. false)", Boolean.class, false);
		return parameters;
	}


	@Override
	public String getImplementationAuthorNames() {
		return "Philippe Fournier-Viger and Yuechun Li";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[]{"Database of instances","Time interval sequence database", "Simple time interval sequence database"};
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[]{"Patterns", "Sequential patterns", "Frequent time interval sequential patterns"};
	}
	
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
	
}
