package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.associationrules.hgb.AlgoFHIM_and_HUCI;
import ca.pfv.spmf.algorithms.associationrules.hgb.AlgoHGBAll;
import ca.pfv.spmf.algorithms.associationrules.hgb.HUTable;
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
 * This class describes the HGB_ALL algorithm parameters. 
 * It is designed to be used by the graphical and command line interface.
 * 
 * @see AlgoHGBAll
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoHGB_All extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoHGB_All(){
	}

	@Override
	public String getName() {
		return "HGB_All";
	}

	@Override
	public String getAlgorithmCategory() {
		return "HIGH-UTILITY PATTERN MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/HGB_rules_non.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		int minutil = getParamAsInteger(parameters[0]);
		double minconf = getParamAsDouble(parameters[1]);
		
		// Step 1 : Applying the HUIMiner algorithm
		AlgoFHIM_and_HUCI huci = new AlgoFHIM_and_HUCI();
		huci.runAlgorithmHUCIMiner(inputFile, null, minutil);
		HUTable table = huci.getTableHU();
		huci.printStats();
		
		// Step 2: Applying the HGB algorithm to generate high utility association rules
		AlgoHGBAll algoHGB = new AlgoHGBAll();
		algoHGB.runAlgorithm(table, minconf, minutil);
		algoHGB.writeRulesToFile(outputFile);
		algoHGB.printStats();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
        
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[2];
		parameters[0] = new DescriptionOfParameter("Minimum utility", "(e.g. 30)", Integer.class, false);
		parameters[1] = new DescriptionOfParameter("Minimum confidence", "(e.g. 0.5)", Double.class, false);
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Jayakrushna Sahoo";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[]{"Database of instances","Transaction database", "Transaction database with utility values"};
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[]{"Patterns",  "High-utility patterns","High-utility association rules"};
	}
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
}
