package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.associationrules.pnar.AlgoPNAR;
import ca.pfv.spmf.algorithms.associationrules.pnar.AlgoPNAR.MiningAlgorithm;
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
 * This class describes parameters of the PNAR(AprioriTID) algorithm for generating positive
 * and negative association rules using the PNAR algorithm.
 * It is designed to be used by the graphical and command line interface.
 * 
 * @see AlgoPNAR
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoPNARAprioriTID extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoPNARAprioriTID(){
	}

	@Override
	public String getName() {
		return "PNAR-AprioriTID";
	}

	@Override
	public String getAlgorithmCategory() {
		return "ASSOCIATION RULE MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/PNAR_Rules.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		double minsup = getParamAsDouble(parameters[0]);
		double minconf = getParamAsDouble(parameters[1]);

		AlgoPNAR algo = new AlgoPNAR();

		if (parameters.length >= 3 && "".equals(parameters[2]) == false) {
			boolean generateR1 = getParamAsBoolean(parameters[2]);
			algo.setGenerateR1(generateR1);
		}
		if (parameters.length >= 4 && "".equals(parameters[3]) == false) {
			boolean generateR2 = getParamAsBoolean(parameters[3]);
			algo.setGenerateR2(generateR2);
		}
		if (parameters.length >= 5 && "".equals(parameters[4]) == false) {
			boolean generateR3 = getParamAsBoolean(parameters[4]);
			algo.setGenerateR3(generateR3);
		}
		if (parameters.length >= 6 && "".equals(parameters[5]) == false) {
			boolean generateR4 = getParamAsBoolean(parameters[5]);
			algo.setGenerateR4(generateR4);
		}

		algo.setMiningAlgorithm(MiningAlgorithm.APRIORI_TID);
		
		algo.runAlgorithm(inputFile, outputFile, minsup, minconf);
		algo.printStats();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[6];
		parameters[0] = new DescriptionOfParameter("Minsup (%)", "(e.g. 0.5 or 50%)", Double.class, false);
		parameters[1] = new DescriptionOfParameter("Minconf (%)", "(e.g. 0.6 or 60%)", Double.class, false);
		parameters[2] = new DescriptionOfParameter("Generate R1 X ==> Y", "(e.g. true or false)", Boolean.class, true);
		parameters[3] = new DescriptionOfParameter("Generate R2 NOT(X) ==> NOT(Y)", "(e.g. true or false)", Boolean.class, true);
		parameters[4] = new DescriptionOfParameter("Generate R3 X ==> NOT(Y)", "(e.g. true or false)", Boolean.class, true);
		parameters[5] = new DescriptionOfParameter("Generate R4 NOT(X) ==> Y", "(e.g. true or false)", Boolean.class, true);
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
		return new String[]{"Patterns", "Association rules", "Association rules"};
	}

	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
}