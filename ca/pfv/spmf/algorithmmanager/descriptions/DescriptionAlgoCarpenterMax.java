package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.frequentpatterns.carpenter.AlgoCarpenter;

/* This file is copyright (c) 2008-2025 Philippe Fournier-Viger
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
 * This class describes the Carpenter-Max algorithm parameters for mining maximal itemsets.
 * It is designed to be used by the graphical and command line interface.
 * 
 * Reference: F. Pan, G. Cong, A.K.H. Tung, J. Yang, and M. Zaki. Carpenter:
 * Finding Closed Patterns in Long Biological Datasets. Proc. 9th ACM SIGKDD
 * Int. Conf. on Knowledge Discovery and Data Mining (KDD 2003), 637-642.
 * 
 * @see AlgoCarpenter
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoCarpenterMax extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoCarpenterMax() {
	}

	@Override
	public String getName() {
		return "CarpenterMax";
	}

	@Override
	public String getAlgorithmCategory() {
		return "FREQUENT ITEMSET MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/CarpenterMax.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		// Get minimum support parameter
		double minsup = getParamAsDouble(parameters[0]);
		
		// Get minimum size constraint (optional, default = 1)
		int minSizeConstraint = 1;
		if (parameters.length >= 2 && !"".equals(parameters[1])) {
			minSizeConstraint = getParamAsInteger(parameters[1]);
		}
		
		// Get maximum size constraint (optional, default = Integer.MAX_VALUE)
		int maxSizeConstraint = Integer.MAX_VALUE;
		if (parameters.length >= 3 && !"".equals(parameters[2])) {
			maxSizeConstraint = getParamAsInteger(parameters[2]);
		}
		
		// Run the Carpenter algorithm for maximal itemsets
		AlgoCarpenter algo = new AlgoCarpenter();
		algo.runAlgorithm(inputFile, outputFile, minsup, minSizeConstraint, maxSizeConstraint, true);
		algo.printStats();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[3];
		parameters[0] = new DescriptionOfParameter("Minsup (%)", "(e.g. 0.4 or 40%)", Double.class, false);
		parameters[1] = new DescriptionOfParameter("Min pattern length", "(e.g. 1)", Integer.class, true);
		parameters[2] = new DescriptionOfParameter("Max pattern length", "(e.g. 10)", Integer.class, true);
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Philippe Fournier-Viger";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[] { "Database of instances", "Transaction database", "Simple transaction database" };
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[] { "Patterns", "Frequent patterns", "Maximal patterns", "Maximal itemsets", 
				"Frequent itemsets", "Frequent maximal itemsets" };
	}

	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
}