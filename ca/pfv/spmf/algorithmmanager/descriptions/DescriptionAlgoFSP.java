package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.graph_mining.fsp.AlgoFSP;
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
 * This class describes the FSP algorithm parameters. It is designed to be used by the graphical and command line interface.
 * 
 * @see AlgoFSP
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoFSP extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoFSP(){
	}

	@Override
	public String getName() {
		return "FSP";
	}

	@Override
	public String getAlgorithmCategory() {
		return "GRAPH PATTERN MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/FSP_subgraph.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException, ClassNotFoundException {

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
		
		// Apply the algorithm 
		AlgoFSP algo = new AlgoFSP();
		algo.runAlgorithm(inputFile, outputFile, minSupport, outputSingleFrequentVertices, 
				outputDotFile, maxNumberOfEdges, outputGraphIds);
		
		// Print statistics about the algorithm execution
		algo.printStats();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
        
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[5];
		parameters[0] = new DescriptionOfParameter("Minsup (%)", "(e.g. 0.9 or 90%)", Double.class, false);
		parameters[1] = new DescriptionOfParameter("Max Number of Edges", "(e.g. 2)", Integer.class, true);
		parameters[2] = new DescriptionOfParameter("Output Single Vertices", "(e.g. true)", Boolean.class, true);
		parameters[3] = new DescriptionOfParameter("Output DOT file", "(e.g. false)", Boolean.class, true);
		parameters[4] = new DescriptionOfParameter("Output Graph IDs", "(e.g. true)", Boolean.class, true);
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Chen Hui and Philippe Fournier-Viger";
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