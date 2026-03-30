package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.tools.dataset_stats.IntervalSeqDatabaseStats;

/**
 * This class describes the algorithm to calculate stats for an interval sequence database. It is designed to be used by the graphical and command line interface.
 * 
 * @see IntervalSeqDatabaseStats
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoCalculateStatsIntervalSequenceDB extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoCalculateStatsIntervalSequenceDB(){
	}

	@Override
	public String getName() {
		return "Calculate_stats_for_an_interval_sequence_database";
	}

	@Override
	public String getAlgorithmCategory() {
		return "TOOLS - STATS CALCULATORS";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/Calculating_interval_stats.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		IntervalSeqDatabaseStats algo = new IntervalSeqDatabaseStats();
		algo.runAlgorithm(inputFile);
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
        
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[0];
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Philippe Fournier-Viger";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[]{"Database of instances","Time interval sequence database", "Simple time interval sequence database"};
	}

	@Override
	public String[] getOutputFileTypes() {
		return null;
	}
//
//	@Override
//	String[] getSpecialInputFileTypes() {
//		return null; //new String[]{"ARFF"};
//	}
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_STATS_CALCULATOR;
	}
}
