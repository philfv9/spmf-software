package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.sequentialpatterns.prefixSpan_AGP_fast.AlgoPrefixSpan_AGP;
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
 * This class describes the PrefixSpan algorithm parameters (version implemented
 * by Antonio G.P.). It is designed to be used by the graphical and command line
 * interface.
 * 
 * @see AlgoPrefixSpan_AGP
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoPrefixSpanAGP extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoPrefixSpanAGP() {
	}

	@Override
	public String getName() {
		return "PrefixSpan_AGP";
	}

	@Override
	public String getAlgorithmCategory() {
		return "SEQUENTIAL PATTERN MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/PrefixSpan.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		double minSupport = getParamAsDouble(parameters[0]);

		// set optional minimum pattern length if provided
		if (parameters.length >= 2 && "".equals(parameters[1]) == false) {
			// parameter handled below via algo
		}

		AlgoPrefixSpan_AGP algo = new AlgoPrefixSpan_AGP(minSupport);

		// set optional minimum pattern length
		if (parameters.length >= 2 && "".equals(parameters[1]) == false) {
			algo.setMinimumPatternLength(getParamAsInteger(parameters[1]));
		}
		// set optional maximum pattern length
		if (parameters.length >= 3 && "".equals(parameters[2]) == false) {
			algo.setMaximumPatternLength(getParamAsInteger(parameters[2]));
		}

		boolean outputSeqIdentifiers = false;
		if (parameters.length >= 4 && "".equals(parameters[3]) == false) {
			outputSeqIdentifiers = getParamAsBoolean(parameters[3]);
		}

		/*
		 * if("".equals(parameters[1]) == false){
		 * algo.setMaximumPatternLength(getParamAsInteger(parameters[1])); }
		 */
		ca.pfv.spmf.algorithms.sequentialpatterns.prefixSpan_AGP_fast.SequenceDatabase sd = new ca.pfv.spmf.algorithms.sequentialpatterns.prefixSpan_AGP_fast.SequenceDatabase();
		sd.loadFile(inputFile, minSupport);

		algo.runAlgorithm(sd, true, false, outputFile, outputSeqIdentifiers);
		System.out.println(algo.printStatistics());
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {

		DescriptionOfParameter[] parameters = new DescriptionOfParameter[4];
		parameters[0] = new DescriptionOfParameter("Minsup (%)", "(e.g. 0.4 or 40%)", Double.class, false);
		parameters[1] = new DescriptionOfParameter("Min pattern length", "(e.g. 1 items)", Integer.class, true);
		parameters[2] = new DescriptionOfParameter("Max pattern length", "(e.g. 10 items)", Integer.class, true);
		parameters[3] = new DescriptionOfParameter("Show sequence ids?", "(default: false)", Boolean.class, true);
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Antonio Gomariz Penalver";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[] { "Database of instances", "Sequence database", "Simple sequence database" };
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[] { "Patterns", "Sequential patterns", "Frequent sequential patterns" };
	}

	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}

}