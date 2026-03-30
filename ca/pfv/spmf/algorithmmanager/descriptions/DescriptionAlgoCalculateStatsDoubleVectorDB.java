package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.tools.dataset_stats.DoubleVectorDBStats;

/**
 * This class describes the algorithm to calculate stats about a file containing instances (double vectors)
 * 
 * @see DoubleVectorDBStats
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoCalculateStatsDoubleVectorDB extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoCalculateStatsDoubleVectorDB() {
	}

	@Override
	public String getName() {
		return "Calculate_stats_for_double_vector_instance_file";
	}

	@Override
	public String getAlgorithmCategory() {
		return "TOOLS - STATS CALCULATORS";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/CalcStats_for_double_vectors.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		String separator = getParamAsString(parameters[0]);

		// Applying the algorithm
		DoubleVectorDBStats algorithm = new DoubleVectorDBStats();
		algorithm.runAlgorithm(inputFile, separator);
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {

		DescriptionOfParameter[] parameters = new DescriptionOfParameter[1];
		parameters[0] = new DescriptionOfParameter("Separator", "(e.g. , )", String.class, false);
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
		return null;
	}

	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_STATS_CALCULATOR;
	}
}
