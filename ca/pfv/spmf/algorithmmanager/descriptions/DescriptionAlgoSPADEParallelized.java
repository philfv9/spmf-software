package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.AlgoSPADE;
import ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.idLists.creators.IdListCreator;
import ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.idLists.creators.IdListCreator_FatBitmap;
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
 * This class describes the SPADE-parallelized algorithm parameters. It is
 * designed to be used by the graphical and command line interface.
 * 
 * @see AlgoSPADE
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoSPADEParallelized extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoSPADEParallelized() {
	}

	@Override
	public String getName() {
		return "SPADE_Parallelized";
	}

	@Override
	public String getAlgorithmCategory() {
		return "SEQUENTIAL PATTERN MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/SPADE.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		// use the fat bitmap id-list implementation
		IdListCreator idListCreator = IdListCreator_FatBitmap.getInstance();

		// get the minimum support threshold
		double minSupport = getParamAsDouble(parameters[0]);

		AlgoSPADE algo = new AlgoSPADE(minSupport, true);

		// set the optional minimum pattern length
		if (parameters.length >= 2 && "".equals(parameters[1]) == false) {
			algo.setMinimumPatternLength(getParamAsInteger(parameters[1]));
		}
		// set the optional maximum pattern length
		if (parameters.length >= 3 && "".equals(parameters[2]) == false) {
			algo.setMaximumPatternLength(getParamAsInteger(parameters[2]));
		}

		// check if sequence ids should be shown in the output
		boolean outputSeqIdentifiers = false;
		if (parameters.length >= 4 && "".equals(parameters[3]) == false) {
			outputSeqIdentifiers = getParamAsBoolean(parameters[3]);
		}

		// load the sequence database
		ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.SequenceDatabase sd = new ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.SequenceDatabase(
				 idListCreator);
		sd.loadFile(inputFile, minSupport);

		// run the algorithm
		algo.runAlgorithmParallelized(sd, true, false, outputFile, outputSeqIdentifiers);
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