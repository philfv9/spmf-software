package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.frequentpatterns.grimp.AlgoGrimp;
import ca.pfv.spmf.algorithms.frequentpatterns.grimp.CrossoverVariant;
import ca.pfv.spmf.algorithms.frequentpatterns.grimp.MutationVariant;

/**
 * This class describes the GRIMP algorithm parameters.
 * It is designed to be used by the graphical and command line interface.
 *
 * GRIMP: A Genetic Algorithm for Compression-based Pattern Mining.
 *
 * @see AlgoGrimp
 * @author M. Zohaib Nawaz, M. Saqib Nawaz, Philippe Fournier-Vige
 */
public class DescriptionAlgoGRIMP extends DescriptionOfAlgorithm {

    /** Default constructor */
    public DescriptionAlgoGRIMP() {
    }

    @Override
    public String getName() {
        return "GRIMP";
    }

    @Override
    public String getAlgorithmCategory() {
        return "FREQUENT ITEMSET MINING";
    }

    @Override
    public String getURLOfDocumentation() {
        return "http://www.philippe-fournier-viger.com/spmf/GRIMP.php";
    }

    @Override
    public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {

        // Parameters (in order):
        // 0: number of patterns
        // 1: number of iterations
        // 2: crossover variant
        // 3: mutation variant
        // 4: max pattern length

        int patternCount = getParamAsInteger(parameters[0]);
        int iterationCount = 
                (parameters.length > 1 && !"".equals(parameters[1]))
                ? getParamAsInteger(parameters[1])
                : 10;

        CrossoverVariant crossoverVariant =
                (parameters.length > 2 && !"".equals(parameters[2]))
                        ? CrossoverVariant.valueOf(parameters[2])
                        : CrossoverVariant.SINGLE;

        MutationVariant mutationVariant =
                (parameters.length > 3 && !"".equals(parameters[3]))
                        ? MutationVariant.valueOf(parameters[3])
                        : MutationVariant.SINGLE;

        int maxPatternLength =
                (parameters.length > 4 && !"".equals(parameters[4]))
                        ? getParamAsInteger(parameters[4])
                        : 5;

        boolean debug = false;

        AlgoGrimp algorithm = new AlgoGrimp();

        algorithm.runAlgorithm(
                inputFile,
                outputFile,
                patternCount,
                iterationCount,
                crossoverVariant,
                mutationVariant,
                maxPatternLength,
                debug);

        algorithm.printStats();
    }

    @Override
    public DescriptionOfParameter[] getParametersDescription() {

        DescriptionOfParameter[] parameters = new DescriptionOfParameter[5];

        parameters[0] = new DescriptionOfParameter(
                "Number of patterns",
                "(e.g. 4)",
                Integer.class,
                false);

        parameters[1] = new DescriptionOfParameter(
                "Iteration count",
                "(e.g. 10)",
                Integer.class,
                true);

        parameters[2] = new DescriptionOfParameter(
                "Crossover variant",
                "(e.g. SINGLE)",
                String.class,
                true);

        parameters[3] = new DescriptionOfParameter(
                "Mutation variant",
                "(e.g. SINGLE)",
                String.class,
                true);

        parameters[4] = new DescriptionOfParameter(
                "Max pattern length",
                "(e.g. 5)",
                Integer.class,
                true);
        return parameters;
    }

    @Override
    public String getImplementationAuthorNames() {
        return "M. Zohaib Nawaz, M. Saqib Nawaz, Philippe Fournier-Viger";
    }

    @Override
    public String[] getInputFileTypes() {
        return new String[]{"Database of instances",
                "Simple transaction database",
                "Transaction database"
                
        };
    }

    @Override
    public String[] getOutputFileTypes() {
        return new String[]{
                "Patterns",
                "Frequent patterns",
                "Frequent itemsets"
        };
    }

    @Override
    public AlgorithmType getAlgorithmType() {
        return AlgorithmType.DATA_MINING;
    }
}
