package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.graph_mining.fsma.AlgoFSMA;

/**
 * This class describes the FSMA algorithm parameters. It is designed to be used by the graphical and command line interface.
 * 
 * @see AlgoFSMA
 * @author Chen Hui
 */
public class DescriptionAlgoFSMA extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoFSMA(){
	}

	@Override
	public String getName() {
		return "FSMA";
	}

	@Override
	public String getAlgorithmCategory() {
		return "GRAPH PATTERN MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/FSMA_subgraph_miner.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException, ClassNotFoundException {

		// set the minimum support threshold
		double minSupport = getParamAsDouble(parameters[0]);
		
		// Output the ids of graphs containing each frequent subgraph (optional, default true)
		boolean outputGraphIds = false;
		
		if (parameters.length >=2 && "".equals(parameters[1]) == false) {
			outputGraphIds = getParamAsBoolean(parameters[1]);
		}
		
		// Apply the algorithm 
		AlgoFSMA algo = new AlgoFSMA();
		algo.runAlgorithm(inputFile, outputFile, minSupport, outputGraphIds);
		
		// Print statistics about the algorithm execution
		algo.printStats();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
        
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[2];
		parameters[0] = new DescriptionOfParameter("Minsup (%)", "(e.g. 0.9 or 90%)", Double.class, false);
		parameters[1] = new DescriptionOfParameter("Output Graph IDs", "(e.g. true)", Boolean.class, true);
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Chen Hui";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[]{"Database of instances","Graph database", "Labeled graph database"};
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[]{"Patterns", "Subgraphs", "Frequent subgraphs"};
	}
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
}