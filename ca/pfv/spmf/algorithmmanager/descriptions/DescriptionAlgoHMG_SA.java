
package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.sequentialpatterns.hmg.AlgoHMG;

/**
 * This class describes the HMG algorithm (Simulated Annealing variant) parameters.
 * It is designed to be used by the graphical and command line interface.
 * 
 * @see AlgoHMG
 * @author M. Zohaib Nawaz, M. Saqib Nawaz, Philippe Fournier-Viger et al.
 */
public class DescriptionAlgoHMG_SA extends DescriptionOfAlgorithm {

    public DescriptionAlgoHMG_SA() {
    }

    @Override
    public String getName() {
        return "HMG-SA";
    }

    @Override
    public String getAlgorithmCategory() {
        return "SEQUENTIAL PATTERN MINING";
    }

    @Override
    public String getURLOfDocumentation() {
        return "http://www.philippe-fournier-viger.com/spmf/HMG_heuristic.php";
    }

    @Override
    public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
        // Number of patterns to find
        int patterns = getParamAsInteger(parameters[0]);

        boolean spmfStyleOutput = getParamAsBoolean(parameters[1]);

        AlgoHMG hmg = new AlgoHMG();
        hmg.runSAAlgorithm(inputFile, patterns, outputFile, spmfStyleOutput);
        hmg.printStats();
    }

    @Override
    public DescriptionOfParameter[] getParametersDescription() {
        DescriptionOfParameter[] parameters = new DescriptionOfParameter[2];
        parameters[0] = new DescriptionOfParameter("Number of patterns", "(e.g. 2)", Integer.class, false);
        parameters[1] = new DescriptionOfParameter("SPMF style output", "true", Boolean.class, false);
        return parameters;
    }

    @Override
    public String getImplementationAuthorNames() {
        return "M. Zohaib Nawaz, M. Saqib Nawaz, Philippe Fournier-Viger et al.";
    }

    @Override
    public String[] getInputFileTypes() {
        return new String[] {"Genome sequences" };
    }

    @Override
    public String[] getOutputFileTypes() {
    	return new String[] { "Patterns", "Frequent string patterns" };
    }

    @Override
    public AlgorithmType getAlgorithmType() {
        return AlgorithmType.DATA_MINING;
    }
}
