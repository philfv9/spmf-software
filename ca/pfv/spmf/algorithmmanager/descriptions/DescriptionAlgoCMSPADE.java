package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.AlgoCMSPADE;
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
 * This class describes the CM-SPADE algorithm parameters. It is designed to be
 * used by the graphical and command line interface.
 * 
 * @see AlgoCMSPADE
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoCMSPADE extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoCMSPADE() {
	}

	/**
	 * Get the name of the algorithm.
	 * @return the algorithm name as a string
	 */
	@Override
	public String getName() {
		return "CM-SPADE";
	}

	/**
	 * Get the category of the algorithm.
	 * @return the algorithm category as a string
	 */
	@Override
	public String getAlgorithmCategory() {
		return "SEQUENTIAL PATTERN MINING";
	}

	/**
	 * Get the URL of the documentation for this algorithm.
	 * @return the URL as a string
	 */
	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/CM-SPADE.php";
	}

	/**
	 * Run the CM-SPADE algorithm using the given parameters.
	 * @param parameters an array of parameters provided by the user
	 * @param inputFile the path of the input file
	 * @param outputFile the path of the output file
	 * @throws IOException if an error occurs while reading or writing files
	 */
	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {

		// choose the idlist implementation to be used
		IdListCreator idListCreator = IdListCreator_FatBitmap.getInstance();

		// get the minimum support threshold
		double minSupport = getParamAsDouble(parameters[0]);

		// get the optional minimum pattern length
		Integer minPatternLength = null;
		if (parameters.length >= 2 && "".equals(parameters[1]) == false) {
			minPatternLength = getParamAsInteger(parameters[1]);
		}

		// get the optional maximum pattern length
		Integer maxPatternLength = null;
		if (parameters.length >= 3 && "".equals(parameters[2]) == false) {
			maxPatternLength = getParamAsInteger(parameters[2]);
		}

		// get the optional parameter to show sequence identifiers in the output
		boolean outputSeqIdentifiers = false;
		if (parameters.length >= 4 && "".equals(parameters[3]) == false) {
			outputSeqIdentifiers = getParamAsBoolean(parameters[3]);
		}

		// create the algorithm instance
		AlgoCMSPADE algo = new AlgoCMSPADE(minSupport, true);

		// set the optional minimum and maximum pattern length constraints
		if (minPatternLength != null) {
			algo.setMinimumPatternLength(minPatternLength);
		}
		if (maxPatternLength != null) {
			algo.setMaximumPatternLength(maxPatternLength);
		}

		// load the sequence database
		ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.SequenceDatabase sd = new ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.SequenceDatabase(
				idListCreator);
		sd.loadFile(inputFile, minSupport);

		// run the algorithm and save the result to the output file
		algo.runAlgorithm(sd, true, false, outputFile, outputSeqIdentifiers);
		System.out.println(algo.printStatistics());
	}

	/**
	 * Get the list of parameters that can be provided to this algorithm.
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
	 * Get the names of the authors of this implementation.
	 * @return the author names as a string
	 */
	@Override
	public String getImplementationAuthorNames() {
		return "Antonio Gomariz Penalver & Philippe Fournier-Viger";
	}

	/**
	 * Get the types of input files accepted by this algorithm.
	 * @return an array of input file type descriptions
	 */
	@Override
	public String[] getInputFileTypes() {
		return new String[] { "Database of instances", "Sequence database", "Simple sequence database" };
	}

	/**
	 * Get the types of output files produced by this algorithm.
	 * @return an array of output file type descriptions
	 */
	@Override
	public String[] getOutputFileTypes() {
		return new String[] { "Patterns", "Sequential patterns", "Frequent sequential patterns" };
	}

	/**
	 * Get the type of this algorithm.
	 * @return the algorithm type
	 */
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
}