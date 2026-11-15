package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.sequentialpatterns.mapd_owsp.AlgoMAPD;
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
 * This class describes the MAPD algorithm parameters. It is designed to be used by the graphical and command line interface.
 * 
 * @see AlgoMAPD
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoMAPD extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoMAPD(){
	}

	@Override
	public String getName() {
		return "MAPD";
	}

	@Override
	public String getAlgorithmCategory() {
		return "EPISODE MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/MAPD_string.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {

		// Get the parameter "minsup"
		double minimumThreshold = getParamAsDouble(parameters[0]);
		int maxPatternLength = getParamAsInteger(parameters[1]);
		int minGap = getParamAsInteger(parameters[2]);
		int maxGap = getParamAsInteger(parameters[3]);
		char[] charset = parameters[4].toCharArray();
		
        AlgoMAPD algorithm = new AlgoMAPD();
        algorithm.runAlgorithm(inputFile,outputFile, minGap, maxGap, maxPatternLength, minimumThreshold, charset);
        algorithm.printStats();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
        
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[5];
		parameters[0] = new DescriptionOfParameter("Min. threshold", "(e.g. 0.2)", Double.class, false);
		parameters[1] = new DescriptionOfParameter("Max. pattern length", "(e.g. 3)", Integer.class, false);
		parameters[2] = new DescriptionOfParameter("Min. gap", "(e.g. 0)", Integer.class, false);
		parameters[3] = new DescriptionOfParameter("Max. gap", "(e.g. 3)", Integer.class, false);
		parameters[4] = new DescriptionOfParameter("Charset", "(e.g. actg)", String.class, false);
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Youxi Wu et al.";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[] {"Sequence", "String sequence"};
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[] { "Patterns", "Sequential patterns", "Frequent sequential patterns", "Sequential patterns with periodic wilcard gaps" };
	}

	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
}
