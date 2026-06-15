package ca.pfv.spmf.algorithmmanager.descriptions;

/* This file is copyright (c) 2008-2016 Philippe Fournier-Viger
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
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see http://www.gnu.org/licenses/.
 */

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.frequentpatterns.uhmine.AlgoUHMine;
import ca.pfv.spmf.algorithms.frequentpatterns.uhmine.UncertainTransactionDatabaseUHMine;

/**
 * This class describes the UH-Mine algorithm parameters.
 * It is designed to be used by the graphical and command line interface.
 *
 * @see AlgoUHMine
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoUHMine extends DescriptionOfAlgorithm {

    /**
     * Default constructor.
     */
    public DescriptionAlgoUHMine() {
    }

    /** @return the name of the algorithm */
    @Override
    public String getName() {
        return "UH-Mine";
    }

    /** @return the category of the algorithm */
    @Override
    public String getAlgorithmCategory() {
        return "FREQUENT ITEMSET MINING";
    }

    /** @return the URL of the documentation page for this algorithm */
    @Override
    public String getURLOfDocumentation() {
        return "http://www.philippe-fournier-viger.com/spmf/UHMINE_UNCERTAIN.php";
    }

    /**
     * Run the UH-Mine algorithm with the given parameters.
     * The expected support parameter is interpreted as an absolute threshold,
     * consistent with UApriori and UVEclat in SPMF.
     *
     * @param parameters array of parameters: [0] minimum expected support (absolute),
     *                   [1] optional maximum pattern length
     * @param inputFile  path to the uncertain transaction database
     * @param outputFile path to the output file
     * @throws IOException if an error occurs while reading or writing files
     */
    @Override
    public void runAlgorithm(String[] parameters, String inputFile,
                             String outputFile) throws IOException {
        double expectedsup = getParamAsDouble(parameters[0]);

        UncertainTransactionDatabaseUHMine database =
                new UncertainTransactionDatabaseUHMine();
        database.loadFile(inputFile);

        AlgoUHMine algorithm = new AlgoUHMine();

        if (parameters.length >= 2 && !"".equals(parameters[1])) {
            algorithm.setMaximumPatternLength(getParamAsInteger(parameters[1]));
        }

        algorithm.runAlgorithm(database, expectedsup, outputFile);
        algorithm.printStats();
    }

    /**
     * Get the description of each parameter accepted by UH-Mine.
     *
     * @return an array of parameter descriptions
     */
    @Override
    public DescriptionOfParameter[] getParametersDescription() {
        DescriptionOfParameter[] parameters = new DescriptionOfParameter[2];
        parameters[0] = new DescriptionOfParameter(
                "Expected support (%)", "(e.g. 0.1 or 10%)", Double.class, false);
        parameters[1] = new DescriptionOfParameter(
                "Max pattern length", "(e.g. 2 items)", Integer.class, true);
        return parameters;
    }

    /** @return the implementation author name */
    @Override
    public String getImplementationAuthorNames() {
        return "Philippe Fournier-Viger";
    }

    /** @return the accepted input file types */
    @Override
    public String[] getInputFileTypes() {
        return new String[]{
                "Database of instances",
                "Transaction database",
                "Uncertain transaction database"
        };
    }

    /** @return the produced output file types */
    @Override
    public String[] getOutputFileTypes() {
        return new String[]{
                "Patterns",
                "Frequent patterns",
                "Uncertain patterns",
                "Uncertain frequent itemsets"
        };
    }

    /** @return the algorithm type */
    @Override
    public AlgorithmType getAlgorithmType() {
        return AlgorithmType.DATA_MINING;
    }
}