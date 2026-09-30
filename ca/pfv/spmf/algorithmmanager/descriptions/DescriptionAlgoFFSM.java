package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.graph_mining.ffsm.AlgoFFSM;

/**
 * This class describes the FFSM algorithm parameters. It is designed to be used
 * by the graphical and command line interface.
 *
 * @see AlgoFFSM
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoFFSM extends DescriptionOfAlgorithm {

    /**
     * Default constructor
     */
    public DescriptionAlgoFFSM() {
    }

    @Override
    public String getName() {
        return "FFSM";
    }

    @Override
    public String getAlgorithmCategory() {
        return "GRAPH PATTERN MINING";
    }

    @Override
    public String getURLOfDocumentation() {
        return "http://www.philippe-fournier-viger.com/spmf/FFSM_the_subgraph_miner.php";
    }

    @Override
    public void runAlgorithm(String[] parameters, String inputFile, String outputFile)
            throws IOException {

        // set the minimum support threshold
        double minSupport = getParamAsDouble(parameters[0]);

        // Output the ids of graphs containing each frequent subgraph (optional, default true)
        boolean outputGraphIds = false;

        if (parameters.length >= 2 && "".equals(parameters[1]) == false) {
            outputGraphIds = getParamAsBoolean(parameters[1]);
        }

        // apply the algorithm
        AlgoFFSM algo = new AlgoFFSM();
        algo.runAlgorithm(inputFile, outputFile, minSupport, outputGraphIds);

        // print statistics about the algorithm execution
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
        return new String[]{"Database of instances", "Graph database", "Labeled graph database"};
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