package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.clustering.dbscan.AlgoDBSCAN;
import ca.pfv.spmf.algorithms.clustering.distanceFunctions.DistanceEuclidian;
import ca.pfv.spmf.algorithms.clustering.distanceFunctions.DistanceFunction;
import ca.pfv.spmf.algorithms.clustering.dpc.AlgoDPC;

/**
 * This class describes the DPC algorithm parameters. It is designed to be
 * used by the graphical and command line interface.
 * 
 * @see AlgoDBSCAN
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
		return new String[] { "Clusters", "Density-based clusters" };
	}

	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
}
