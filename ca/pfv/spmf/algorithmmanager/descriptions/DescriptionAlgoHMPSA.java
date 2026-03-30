package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.frequentpatterns.hmp.AlgoHMPSA;

/**
 * This class describes the HMPSA algorithm parameters.
 * It is designed to be used by the graphical and command line interface.
 *
 * HMPSA: HMP algorithm using Simulated Annealing.
 * 
 * @author Chen Enze, Philippe Fournier-Viger et al.
 * @see AlgoHMPSA
 */
public class DescriptionAlgoHMPSA extends DescriptionOfAlgorithm {

    /** Default constructor */
    public DescriptionAlgoHMPSA() {
    }

    @Override
    public String getName() {
        return "HMP-SA";
    }

    @Override
    public String getAlgorithmCategory() {
        return "FREQUENT ITEMSET MINING";
    }

    @Override
    public String getURLOfDocumentation() {
        return "http://www.philippe-fournier-viger.com/spmf/HMPSimulatedAnnealing.php";
    }

    @Override
    public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {

        // Parameters (in order):
        // 0: number of patterns
        // 1: initial temperature (optional)
        // 2: minimum temperature (optional)
        // 3: cooling rate (optional)

        int maxCodeTableSize = getParamAsInteger(parameters[0]);

        AlgoHMPSA algo = new AlgoHMPSA();

        if(parameters.length > 1 && !"".equals(parameters[1])){
        	// Set optional parameters
        	double initialTemperature = getParamAsDouble(parameters[1]);
            algo.setInitialTemperature(initialTemperature);
        }

        if (parameters.length > 2 && !"".equals(parameters[2])) {
            // Set optional minimum temperature
            double minTemperature = getParamAsDouble(parameters[2]);
            algo.setMinTemperature(minTemperature);
        }

        if (parameters.length > 3 && !"".equals(parameters[3])) {
            // Set optional cooling rate
            double coolingRate = getParamAsDouble(parameters[3]);
            algo.setCoolingRate(coolingRate);
        }

        // Run algorithm
        algo.runAlgorithm(inputFile, outputFile, maxCodeTableSize);

        // Print statistics
        algo.printStats();
    }

    @Override
    public DescriptionOfParameter[] getParametersDescription() {

        DescriptionOfParameter[] parameters = new DescriptionOfParameter[4];

        parameters[0] = new DescriptionOfParameter(
                "Max number of patterns",
                "(e.g. 4)",
                Integer.class,
                false);

        parameters[1] = new DescriptionOfParameter(
                "Initial temperature",
                "(e.g. 100.0)",
                Double.class,
                true);

        parameters[2] = new DescriptionOfParameter(
                "Minimum temperature",
                "(e.g. 0.1)",
                Double.class,
                true);

        parameters[3] = new DescriptionOfParameter(
                "Cooling rate",
                "(e.g. 0.8)",
                Double.class,
                true);

        return parameters;
    }

    @Override
    public String getImplementationAuthorNames() {
        return "Chen Enze, Philippe Fournier-Viger et al.";
    }

    @Override
    public String[] getInputFileTypes() {
        return new String[]{"Database of instances",
                "Simple transaction database",
                "Transaction database"};
    }

    @Override
    public String[] getOutputFileTypes() {
        return new String[]{
                "Patterns",
                "Frequent patterns",
                "Frequent itemsets"};
    }

    @Override
    public AlgorithmType getAlgorithmType() {
        return AlgorithmType.DATA_MINING;
    }
}
