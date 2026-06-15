package ca.pfv.spmf.algorithms.frequentpatterns.fhuqiminer;

/* This file is copyright (c) 2020 Mourad Nouioua et al.
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
* Do not remove the copyright and license information from this file.
*/

import java.io.File;
import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.frequentpatterns.fhuqiminer.chuqiminer.AlgoCHUQIMiner;

/**
 * This class describes the CHUQI-Miner algorithm parameters.
 * It is designed to be used by the graphical and command line interface.
 * 
 * @see AlgoCHUQIMiner
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoCHUQIMiner extends DescriptionOfAlgorithm {

	/**
	 * Default constructor.
	 */
	public DescriptionAlgoCHUQIMiner() {
	}

	@Override
	public String getName() {
		return "CHUQI-Miner";
	}

	@Override
	public String getAlgorithmCategory() {
		return "HIGH-UTILITY PATTERN MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/CHUQI-Miner.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile,
			String outputFile) throws IOException {

		String inputProfitFile = getParamAsString(parameters[0]);

		File file = new File(inputFile);
		if (file.getParent() != null) {
			inputProfitFile = file.getParent() + File.separator + inputProfitFile;
		}

		float minUtility = getParamAsFloat(parameters[1]);

		double minBond = getParamAsDouble(parameters[2]);

		int relativeCoefficient = getParamAsInteger(parameters[3]);

		EnumCombination method =
				EnumCombination.valueOf(getParamAsString(parameters[4]));

		AlgoCHUQIMiner algo = new AlgoCHUQIMiner();
		algo.runAlgorithm(inputFile, inputProfitFile, minUtility,
				minBond, relativeCoefficient, method, outputFile);
		algo.printStatistics();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {

		DescriptionOfParameter[] parameters =
				new DescriptionOfParameter[5];

		parameters[0] = new DescriptionOfParameter(
				"Profit table",
				"(e.g. dbHUQI_p.txt)",
				String.class,
				false);

		parameters[1] = new DescriptionOfParameter(
				"Minimum utility (%)",
				"(e.g. 20)",
				Float.class,
				false);

		parameters[2] = new DescriptionOfParameter(
				"Minimum bond",
				"(e.g. 0.2)",
				Double.class,
				false);

		parameters[3] = new DescriptionOfParameter(
				"Relative coefficient",
				"(e.g. 3)",
				Integer.class,
				false);

		parameters[4] = new DescriptionOfParameter(
				"Method",
				"(e.g. COMBINEALL)",
				String.class,
				false);

		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Nouioua et al.";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[] {
				"Database of instances",
				"Transaction database",
				"Transaction database with utility values (HUQI)"
		};
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[] {
				"Patterns",
				"High-utility patterns",
				"Correlated quantitative high utility itemsets"
		};
	}

	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
}