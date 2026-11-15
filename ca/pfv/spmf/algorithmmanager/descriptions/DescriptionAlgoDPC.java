package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.clustering.distanceFunctions.DistanceEuclidian;
import ca.pfv.spmf.algorithms.clustering.distanceFunctions.DistanceFunction;
import ca.pfv.spmf.algorithms.clustering.dpc.AlgoDPC;
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
 * This class describes the DPC algorithm parameters. It is designed to be
 * used by the graphical and command line interface.
 * 
 * @see AlgoDPC
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoDPC extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoDPC() {
	}

	@Override
	public String getName() {
		return "DPC";
	}

	@Override
	public String getAlgorithmCategory() {
		return "CLUSTERING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/DPC_clusters.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
        // The parameters of DPC
        double dc = getParamAsDouble(parameters[0]);
        int rhoMin = getParamAsInteger(parameters[1]);
        double deltaMin = getParamAsDouble(parameters[2]);
        
		String separator;
		if (parameters.length > 3 && "".equals(parameters[3]) == false) {
			separator = getParamAsString(parameters[3]);
		} else {
			separator = " ";
		}

        // Apply the algorithm
        AlgoDPC algo = new AlgoDPC();

        DistanceFunction distanceFunction = new DistanceEuclidian(); 

        // Run the DPC algorithm
        algo.runAlgorithm(inputFile, distanceFunction, separator, rhoMin, deltaMin, dc);

        // Print the statistics
        algo.printStatistics();

        // Save the clusters to the output file
        algo.saveToFile(outputFile);
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {

		DescriptionOfParameter[] parameters = new DescriptionOfParameter[4];
		parameters[0] = new DescriptionOfParameter("dc", "(e.g. 1)", Double.class, false);
		parameters[1] = new DescriptionOfParameter("rhoMin", "(e.g. 150)", Integer.class, false);
		parameters[2] = new DescriptionOfParameter("deltaMin", "(e.g. 1)", Double.class, false);
		parameters[3] = new DescriptionOfParameter("separator", "(default: ' ')", String.class, true);
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Philippe Fournier-Viger";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[] { "Database of instances", "Database of double vectors" };
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[] { "Clusters"};
	}

	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
}
