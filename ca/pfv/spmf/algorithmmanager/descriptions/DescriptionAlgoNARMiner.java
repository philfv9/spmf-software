package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;

/* This file is copyright (c) 2008-2016 Philippe Fournier-Viger
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
 * This class describes parameters of the NAR-Miner algorithm for mining 
 * Negative Association Rules (NARs) from a transaction database.
 * It is designed to be used by the graphical and command line interface.
 * 
 * The algorithm mines negative association rules of the form A => NOT B,
 * where A is a frequent itemset and B is an infrequent itemset of interest.
 * 
 * @see AlgoNARMiner
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoNARMiner extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoNARMiner(){
	}

	@Override
	public String getName() {
		return "NAR-Miner";
	}

	@Override
	public String getAlgorithmCategory() {
		return "ASSOCIATION RULE MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/NARMiner.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		double mfs = getParamAsDouble(parameters[0]);
		double mis = getParamAsDouble(parameters[1]);
		double minconf = getParamAsDouble(parameters[2]);
		
		ca.pfv.spmf.algorithms.associationrules.nar_miner.AlgoNARMiner algo = 
			new ca.pfv.spmf.algorithms.associationrules.nar_miner.AlgoNARMiner();
		algo.runAlgorithm(mfs, mis, minconf, inputFile, outputFile);
		algo.printStats();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
        
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[3];
		parameters[0] = new DescriptionOfParameter("Minimum Frequency Support (%)", 
			"(e.g. 0.4 or 40%)", 
			Double.class, false);
		parameters[1] = new DescriptionOfParameter("Maximum Infrequency Support (%)", 
			"(e.g. 0.2 or 20%)", 
			Double.class, false);
		parameters[2] = new DescriptionOfParameter("Minimum Confidence (%)", 
			"(e.g. 0.3 or 30%)", 
			Double.class, false);
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Philippe Fournier-Viger";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[]{"Database of instances","Transaction database", "Simple transaction database"};
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[]{"Patterns", "Association rules"};
	}

	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
}