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
package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.File;
import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.sequentialpatterns.flosm_fgensm_gfscr.AlgoGFSCR;

/**
 * This class describes the GFS-CR algorithm parameters.
 * 
 * @see AlgoGFSCR
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoGFSCR extends DescriptionOfAlgorithm {

	/**
	 * Default constructor.
	 */
	public DescriptionAlgoGFSCR() {
	}

	/**
	 * Returns the name of the algorithm.
	 *
	 * @return the algorithm name
	 */
	@Override
	public String getName() {
		return "GFS-CR";
	}

	/**
	 * Returns the category of the algorithm.
	 *
	 * @return the algorithm category
	 */
	@Override
	public String getAlgorithmCategory() {
		return "SEQUENTIAL PATTERN MINING";
	}

	/**
	 * Returns the URL of the documentation for the algorithm.
	 *
	 * @return the documentation URL
	 */
	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/GFS-CR_seqpat.php";
	}

	/**
	 * Runs the GFS-CR algorithm on a closed sequence file and a generator sequence
	 * file.
	 *
	 * @param parameters the algorithm parameters provided by the interface
	 * @param inputFile  the path of the input file containing the frequent closed
	 *                   sequences
	 * @param outputFile the path of the output file
	 * @throws IOException if an error occurs while reading or writing files
	 */
	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		if (parameters.length < 1) {
			throw new IOException("GFS-CR requires the path of the frequent generator sequence file as "
					+ "the first parameter and the frequent closed sequence file as the input file.");
		}

		File file = new File(inputFile);
		String inputGenerators = file.getParent() + File.separator + parameters[0];
		
		//  patternsFrequentSequentialGenerators.txt
		// C:\Users\Phil\Desktop\SPMF2026\test_files\patternsClosedSequentialPatterns.txt

		AlgoGFSCR algo = new AlgoGFSCR();
		if (parameters.length >= 2 && "".equals(parameters[1]) == false) {
			algo.setMinimumPatternLength(getParamAsInteger(parameters[1]));
		}
		if (parameters.length >= 3 && "".equals(parameters[2]) == false) {
			algo.setMaximumPatternLength(getParamAsInteger(parameters[2]));
		}
		algo.runAlgorithm(inputFile, inputGenerators, outputFile);
		algo.printStats();

	}

	/**
	 * Returns the descriptions of all parameters accepted by the algorithm.
	 *
	 * @return the array of parameter descriptions
	 */
	@Override
	public DescriptionOfParameter[] getParametersDescription() {
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[3];
		parameters[0] = new DescriptionOfParameter("Frequent generator file", "(e.g. generators.txt)", String.class,
				false);
		parameters[1] = new DescriptionOfParameter("Min pattern length", "(e.g. 1 items)", Integer.class, true);
		parameters[2] = new DescriptionOfParameter("Max pattern length", "(e.g. 10 items)", Integer.class, true);
		return parameters;
	}

	/**
	 * Returns the name of the implementation author.
	 *
	 * @return the author name
	 */
	@Override
	public String getImplementationAuthorNames() {
		return "Philippe Fournier-Viger";
	}

	/**
	 * Returns the input file types accepted by the algorithm.
	 *
	 * @return the array of input file type names
	 */
	@Override
	public String[] getInputFileTypes() {
		return new String[] { "Patterns", "Sequential patterns", "Frequent closed sequential patterns" };
	}

	/**
	 * Returns the output file types produced by the algorithm.
	 *
	 * @return the array of output file type names
	 */
	@Override
	public String[] getOutputFileTypes() {
		return new String[] { "Patterns", "Sequential patterns", "Frequent sequential patterns" };
	}

	/**
	 * Returns the algorithm type classification.
	 *
	 * @return the algorithm type
	 */
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
}
