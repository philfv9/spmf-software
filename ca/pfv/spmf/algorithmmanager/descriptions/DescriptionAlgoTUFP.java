package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.frequentpatterns.tufp.AlgoTUFP;
import ca.pfv.spmf.algorithms.frequentpatterns.uapriori.UncertainTransactionDatabase;

/**
 * This class describes the TUFP algorithm parameters for the graphical and command line interface.
 *
 * @see AlgoTUFP
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoTUFP extends DescriptionOfAlgorithm {

    /**
     * Default constructor.
     */
    public DescriptionAlgoTUFP() {
    }

    @Override
    public String getName() {
        return "TUFP";
    }

    @Override
    public String getAlgorithmCategory() {
        return "FREQUENT ITEMSET MINING";
    }

    @Override
    public String getURLOfDocumentation() {
        return "http://www.philippe-fournier-viger.com/spmf/TUFP.php";
    }

    @Override
    public void runAlgorithm(String[] parameters, String inputFile, String outputFile)
            throws IOException {
        int k = getParamAsInteger(parameters[0]);

        UncertainTransactionDatabase database = new UncertainTransactionDatabase();
        database.loadFile(inputFile);

        AlgoTUFP algorithm = new AlgoTUFP(database);
        algorithm.runAlgorithm(k, outputFile);
        algorithm.printStats();
    }

    @Override
    public DescriptionOfParameter[] getParametersDescription() {
        DescriptionOfParameter[] parameters = new DescriptionOfParameter[1];
        parameters[0] = new DescriptionOfParameter("k", "(e.g. 10)", Integer.class, false);
        return parameters;
    }

    @Override
    public String getImplementationAuthorNames() {
        return "Philippe Fournier-Viger";
    }

    @Override
    public String[] getInputFileTypes() {
        return new String[]{"Database of instances", "Transaction database",
                "Uncertain transaction database"};
    }

    @Override
    public String[] getOutputFileTypes() {
        return new String[]{"Patterns", "Frequent patterns", "Uncertain patterns",
                "Uncertain frequent itemsets"};
    }

    @Override
    public AlgorithmType getAlgorithmType() {
        return AlgorithmType.DATA_MINING;
    }
}