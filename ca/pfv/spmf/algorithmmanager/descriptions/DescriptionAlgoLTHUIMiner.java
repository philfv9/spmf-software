package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.frequentpatterns.lthui_miner.AlgoLTHUIMiner;

/**
 * This class describes the LTHUI-Miner algorithm parameters. 
 * It is designed to be used by the graphical and command line interface.
 * 
 * @see AlgoLTHUIMiner
 * @author Yanjun Yang, Philippe Fournier-Viger
 */
public class DescriptionAlgoLTHUIMiner extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoLTHUIMiner(){
	}

	@Override
	public String getName() {
		return "LTHUI-Miner";
	}

	@Override
	public String getAlgorithmCategory() {
		return "HIGH-UTILITY PATTERN MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/LTHUIMiner_algorithm.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		// Local minimum utility threshold
		int lminutil = getParamAsInteger(parameters[0]);
		
		// The length of a sliding window
		int winlen = getParamAsInteger(parameters[1]);
		
		// The length of a bin
		int binlen = getParamAsInteger(parameters[2]);
		
		// Minimum slope threshold, indicating increasing trends
		double minslope = getParamAsDouble(parameters[3]);
		
		// The start timestamp of a database. 
		// If set to -1, the timestamp of the first transaction in the database is used
		// However, in real database, it may not equal to the timestamp of the first transaction in the database
		long databaseStartTimestamp = -1;
		if (parameters.length >=5 && "".equals(parameters[4]) == false) {
			databaseStartTimestamp = Long.parseLong(getParamAsString(parameters[4]));
		}
		
		// If true, then output period with the index of bins, otherwise, output period with timestamp
		boolean outputIndex = false;
		if (parameters.length >=5 && "".equals(parameters[5]) == false) {
			outputIndex = getParamAsBoolean(parameters[5]);
		}
		
		AlgoLTHUIMiner lthuiminer = new AlgoLTHUIMiner();
		lthuiminer.runAlgorithm(inputFile, outputFile, lminutil, winlen, binlen, minslope, databaseStartTimestamp, outputIndex);
		lthuiminer.printStats();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
        
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[6];
		parameters[0] = new DescriptionOfParameter("Minimum utility", "(e.g. 20)", Integer.class, false);
		parameters[1] = new DescriptionOfParameter("Sliding window length", "(e.g. 9)", Integer.class, false);
		parameters[2] = new DescriptionOfParameter("Bin length", "(e.g. 3)", Integer.class, false);
		parameters[3] = new DescriptionOfParameter("Minimum slope", "(e.g. 5)", Double.class, false);
		parameters[4] = new DescriptionOfParameter("The start timestamp of the database", "", Long.class, true);
		parameters[5] = new DescriptionOfParameter("Whether to output period with the bin index", "", Boolean.class, true);
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Yanjun Yang, Philippe Fournier-Viger";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[]{"Database of instances","Transaction database", "Transaction database with utility values and time"};
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[]{"Patterns",  "High-utility patterns","Locally trending high-utility itemsets"};
	}
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
}
