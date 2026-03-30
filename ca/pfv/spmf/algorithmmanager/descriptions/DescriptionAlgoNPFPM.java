package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;
/* This file is copyright (c) 2008-2022 Philippe Fournier-Viger
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

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.frequentpatterns.npfpm.AlgoNPFPM;

/**
 * This class describes the NPFPM algorithm parameters.
 * It is designed to be used by the graphical and command line interface.
 * 
 * @see AlgoNPFPM
 * @author Vincent M. Nofong (modified from Philippe Fournier-Viger's implementation of FPGrowth)
 */
public class DescriptionAlgoNPFPM extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoNPFPM(){
	}

	@Override
	public String getName() {
		return "NPFPM";
	}

	@Override
	public String getAlgorithmCategory() {
		return "PERIODIC PATTERN MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/periodic_NPFPM.php";
	} // 22819

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		double minsup = getParamAsDouble(parameters[0]);
		double periodicity = getParamAsDouble(parameters[1]);
		double difference = getParamAsDouble(parameters[2]);
		AlgoNPFPM algorithm = new AlgoNPFPM();
		
		if (parameters.length >=4 && "".equals(parameters[3]) == false) {
			algorithm.setMaximumPatternLength(getParamAsInteger(parameters[3]));
		}
		if (parameters.length >=5 && "".equals(parameters[4]) == false) {
			algorithm.setMinimumPatternLength(getParamAsInteger(parameters[4]));
		}
		algorithm.runAlgorithm(inputFile, outputFile, minsup, periodicity, difference);
		algorithm.printStats();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
        
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[5];
		parameters[0] = new DescriptionOfParameter("Minsup (%)", "(e.g. 0.1 or 10%)", Double.class, false);
		parameters[1] = new DescriptionOfParameter("Periodicity ", "(e.g. 2)", Double.class, false);
		parameters[2] = new DescriptionOfParameter("Difference ", "(e.g. 1.5)", Double.class, false);
		parameters[3] = new DescriptionOfParameter("Max pattern length", "(e.g. 2 items)", Integer.class, true);
		parameters[4] = new DescriptionOfParameter("Min pattern length", "(e.g. 2 items)", Integer.class, true);
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Vincent M. Nofong modified from Philippe Fournier-Viger";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[]{"Database of instances","Transaction database", "Simple transaction database"};
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[]{"Periodic patterns", "Periodic frequent patterns", "Non-redundant Periodic frequent itemsets"};
	}
	
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
	
}
