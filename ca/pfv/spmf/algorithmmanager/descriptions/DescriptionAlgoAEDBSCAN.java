package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.clustering.dbscan.AlgoAEDBSCAN;

/**
 * This class describes the AEDBScan algorithm parameters. It is designed to be
 * used by the graphical and command line interface.
 * 
 * @see AlgoAEDBSCAN
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoAEDBSCAN extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoAEDBSCAN() {
	}

	@Override
	public String getName() {
		return "AEDBScan";
	}

	@Override
	public String getAlgorithmCategory() {
		return "CLUSTERING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/AEDBScan_clustering.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		int minPts = getParamAsInteger(parameters[0]);

		String separator;
		if (parameters.length > 1 && "".equals(parameters[1]) == false) {
			separator = getParamAsString(parameters[1]);
		} else {
			separator = " ";
		}

		// Apply the algorithm
		AlgoAEDBSCAN algo = new AlgoAEDBSCAN();
		algo.runAlgorithm(inputFile, minPts, separator);
		algo.printStatistics();
		algo.saveToFile(outputFile);
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {

		DescriptionOfParameter[] parameters = new DescriptionOfParameter[2];
		parameters[0] = new DescriptionOfParameter("minPts", "(e.g. 100)", Integer.class, false);
		parameters[1] = new DescriptionOfParameter("separator", "(default: ' ')", String.class, true);
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
