package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.File;
import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.frequentpatterns.hui_miner.AlgoMLHUIMiner;
/* This file is copyright (c) 2008-2020 Philippe Fournier-Viger
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
 * This class describes the MLHUI-Miner algorithm parameters. 
 * It is designed to be used by the graphical and command line interface.
 * 
 * @see AlgoMLHUIMiner
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoMLHUIMiner extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoMLHUIMiner(){
	}

	@Override
	public String getName() {
		return "MLHUIMiner";
	}

	@Override
	public String getAlgorithmCategory() {
		return "HIGH-UTILITY PATTERN MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/MLHUIMiner_cross.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		double minutil = getParamAsDouble(parameters[0]);
		
		// Taxonomy file
		String taxonomyFilename = parameters[1];

		File file = new File(inputFile);
		String taxonomyPath;
		if (file.getParent() == null) {
			taxonomyPath = taxonomyFilename;
		} else {
			taxonomyPath = file.getParent() + File.separator + taxonomyFilename;
		}
		
		// Applying the algorithm
		AlgoMLHUIMiner algo = new AlgoMLHUIMiner();
		algo.runAlgorithm(inputFile, taxonomyPath, outputFile, minutil);
		algo.printStatistics();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
        
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[2];
		parameters[0] = new DescriptionOfParameter("minutil", "(e.g. 60)", Double.class, false);
		parameters[1] = new DescriptionOfParameter("taxonomy file", "(e.g. taxonomy_CLHMiner.txt)", String.class, false);
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Fournier-Viger et al.";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[]{"Database of instances","Transaction database", "Transaction database with utility values", "Transaction database with utility values and taxonomy"};
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[]{"Patterns",  "High-utility patterns","High-utility itemsets", "Multi-Level High-utility itemsets"};
	}
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
}
