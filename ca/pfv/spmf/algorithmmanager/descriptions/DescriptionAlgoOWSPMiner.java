package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.sequentialpatterns.mapd_owsp.AlgoOWSPMiner;
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
 * This class describes the OWSD-Miner algorithm parameters. It is designed to
 * be used by the graphical and command line interface.
 * 
 * @see AlgoOWSPMiner
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoOWSPMiner extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoOWSPMiner() {
	}

	@Override
	public String getName() {
		return "OWSPMiner";
	}

	@Override
	public String getAlgorithmCategory() {
		return "EPISODE MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/OWSPMiner_string.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {

		// Get the parameter "minsup"
		int minsup = getParamAsInteger(parameters[0]);
		char[] gapstr = parameters[1].toCharArray();
		int numberOfCharactersToRead = getParamAsInteger(parameters[2]);

		AlgoOWSPMiner algorithm = new AlgoOWSPMiner();
		algorithm.runAlgorithm(inputFile, outputFile, gapstr, minsup, numberOfCharactersToRead);
		algorithm.printStats();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {

		DescriptionOfParameter[] parameters = new DescriptionOfParameter[3];
		parameters[0] = new DescriptionOfParameter("Minsup", "(e.g. 2)", Integer.class, false);
		parameters[1] = new DescriptionOfParameter("Weak character set", "(e.g. a)", String.class, false);
		parameters[2] = new DescriptionOfParameter("Char count to read", "(e.g. 20000)", Integer.class, false);
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
		return new String[] { "Patterns", "Sequential patterns", "Frequent sequential patterns", "Self-adaptive one-off weak-gap strong sequential patterns"};
	}

	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
}
