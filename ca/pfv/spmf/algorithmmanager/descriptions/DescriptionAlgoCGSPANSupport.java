package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.graph_mining.tkg.AlgoCGSPANSupport;

/**
 * This class describes the CGSPAN algorithm parameters when support threshold is used. It is designed to be used by the graphical and command line interface.
 * 
 * @see AlgoCGSPANSupport
 * @author Zevin Shaul et al.
 */
public class DescriptionAlgoCGSPANSupport extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoCGSPANSupport(){
	}

	@Override
	public String getName() {
		return "CGSPANSupport";
	}

	@Override
	public String getAlgorithmCategory() {
		return "GRAPH PATTERN MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/closed_cgspan_mining.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException, ClassNotFoundException, InterruptedException {

		// set the minimum support threshold
		double minSupport = getParamAsDouble(parameters[0]);
		
		int maxNumberOfEdges = Integer.MAX_VALUE;
		
		// The maximum number of edges for frequent subgraph patterns
		if (parameters.length >=2 && "".equals(parameters[1]) == false) {
			maxNumberOfEdges = getParamAsInteger(parameters[1]);
		}
		
		// If true, single frequent vertices will be output
		boolean outputSingleFrequentVertices = true;
		
		if (parameters.length >=3 && "".equals(parameters[2]) == false) {
			outputSingleFrequentVertices = getParamAsBoolean(parameters[2]);
		}
		
		// If true, a dot file will be output for visualization using GraphViz
		boolean outputDotFile = false;
		
		if (parameters.length >=4 && "".equals(parameters[3]) == false) {
			outputDotFile = getParamAsBoolean(parameters[3]);
		}
		
		// Output the ids of graph containing each frequent subgraph
		boolean outputGraphIds = true;
		
		if (parameters.length >=5 && "".equals(parameters[4]) == false) {
			outputGraphIds = getParamAsBoolean(parameters[4]);
		}
		
		// For the detection of failure
		boolean detectFailure = true;
		
		if (parameters.length >=6 && "".equals(parameters[5]) == false) {
			detectFailure = getParamAsBoolean(parameters[5]);
		}

		// For the detection of failure
		boolean outputDebug = false;
		if (parameters.length >=7 && "".equals(parameters[6]) == false) {
			outputDebug = getParamAsBoolean(parameters[6]);
		}
		
		// Apply the algorithm 
		AlgoCGSPANSupport algo = new AlgoCGSPANSupport();
		algo.setDetectEarlyTerminationFailure(detectFailure);
		algo.setDebugMode(outputDebug);
		algo.runAlgorithm(inputFile, outputFile, minSupport, outputSingleFrequentVertices, 
				outputDotFile, maxNumberOfEdges, outputGraphIds);
		
		// Print statistics about the algorithm execution
		algo.printStats();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
        
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[7];
		parameters[0] = new DescriptionOfParameter("Minsup (%)", "(e.g. 0.9 or 90%)", Double.class, false);
		parameters[1] = new DescriptionOfParameter("Max Number of Edges", "(e.g. 2)", Integer.class, true);
		parameters[2] = new DescriptionOfParameter("Output Single Vertices", "(e.g. true)", Boolean.class, true);
		parameters[3] = new DescriptionOfParameter("Output DOT file", "(e.g. false)", Boolean.class, true);
		parameters[4] = new DescriptionOfParameter("Output Graph IDs", "(e.g. true)", Boolean.class, true);
		parameters[5] = new DescriptionOfParameter("Failure dectection", "(e.g. true)", Boolean.class, true);
		parameters[6] = new DescriptionOfParameter("Output Debug Info", "(e.g. false)", Boolean.class,  true);
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Zevin Shaul et al.";
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
