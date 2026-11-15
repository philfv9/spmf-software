package ca.pfv.spmf.algorithmmanager.descriptions;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.frequentpatterns.MRCPPS.AlgoMRCPPS;
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
 * This class describes the MRCPPS algorithm parameters. It is designed to be used by the graphical and command line interface.
 * 
 * @see AlgoMRCPPS
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoMRCPPS extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoMRCPPS(){
	}

	@Override
	public String getName() {
		return "MRCPPS";
	}

	@Override
	public String getAlgorithmCategory() {
		return "PERIODIC PATTERN MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/MRCPPS.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws Exception {

		int maxSup = getParamAsInteger(parameters[0]);

		// Max standard deviation
		double maxStd = getParamAsDouble(parameters[1]);

		// Minimum support
		double minBond = getParamAsDouble(parameters[2]);
		
		// Min RA
		double minRa = getParamAsDouble(parameters[3]);

		// Run the algorithm
		AlgoMRCPPS algorithm = new AlgoMRCPPS();
		algorithm.runAlgorithm(inputFile, outputFile, maxSup, maxStd, 
				minBond, minRa, true, false, 0);
		algorithm.printStats();
	}
	

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
        
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[4];
		parameters[0] = new DescriptionOfParameter("MaxSup ", "(e.g. 2)", Integer.class, false);
		parameters[1] = new DescriptionOfParameter("MaxStd ", "(default: 1)", Double.class, false);
		parameters[2] = new DescriptionOfParameter("MinBond ", "(e.g. 0.5)", Double.class, false);
		parameters[3] = new DescriptionOfParameter("MinRa ", "(e.g. 0.5)", Double.class, false);
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Yang Peng, Zhitian Li, Philippe Fournier-Viger";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[]{"Database of instances","Sequence database", "Simple sequence database"};
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[]{"Patterns", "Periodic patterns", "Periodic rare patterns", "Rare correlated itemsets common to multiple sequences"};
	}
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
}
