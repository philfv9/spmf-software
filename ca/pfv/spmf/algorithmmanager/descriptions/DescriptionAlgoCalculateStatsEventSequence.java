package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.tools.dataset_stats.EventSequenceStats;

/**
 * This class describes the algorithm to calculate stats for an event database. It is designed to be used by the graphical and command line interface.
 * 
 * @see EventSequenceStats
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoCalculateStatsEventSequence extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoCalculateStatsEventSequence(){
	}

	@Override
	public String getName() {
		return "Calculate_stats_for_an_event_sequence";
	}

	@Override
	public String getAlgorithmCategory() {
		return "TOOLS - STATS CALCULATORS";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/Calc_stats_event_seq.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		EventSequenceStats algo = new EventSequenceStats();
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
		return new String[] { "Database of instances", "Transaction database", "Transaction database with timestamps" };
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
