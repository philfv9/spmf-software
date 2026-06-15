package ca.pfv.spmf.algorithmmanager.descriptions;

/*
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 * Copyright (c) [2026] [Konstantinos Malliaridis / Stefanos Ougiaroglou].
 *
 * This algorithm is an implementation of the HTK-Miner / HTK-negFIN algorithms.
 * If you use this code in your research, please cite the original manuscript:
 * [Malliaridis, K., & Ougiaroglou, S. (2026). Efficient techniques for retrieving
 * top-K Frequent itemsets. Expert Systems with Applications, 131250.].
 *
 * @Email konsmall@ihu.gr, stoug@ihu.gr
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.frequentpatterns.HTK_Miner.AlgoHTKnegFIN;

/**
 * This class describes the HTK-negFIN algorithm parameters and integrates it
 * into the SPMF graphical and command-line interface.
 *
 * @see AlgoHTKnegFIN
 * @author Konstantinos Malliaridis and Stefanos Ougiaroglou
 */
public class DescriptionAlgoHTKnegFIN extends DescriptionOfAlgorithm {

    /**
     * Default constructor.
     */
    public DescriptionAlgoHTKnegFIN() {
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return "HTK_NEGFIN";
    }

    /** {@inheritDoc} */
    @Override
    public String getAlgorithmCategory() {
        return "FREQUENT ITEMSET MINING";
    }

    /** {@inheritDoc} */
    @Override
    public String getURLOfDocumentation() {
        return "http://www.philippe-fournier-viger.com/spmf/HTKNEGFIN.php";
    }

    /**
     * Runs the HTK-negFIN algorithm with the parameters supplied by the SPMF
     * interface.
     *
     * @param parameters array of string parameters; {@code parameters[0]} is the
     *                   integer value of {@code k}; the optional
     *                   {@code parameters[1]} overrides the default space
     *                   delimiter
     * @param inputFile  path to the input transaction database
     * @param outputFile path to the file where results will be written
     * @throws IOException if the input file cannot be read or the output file
     *                     cannot be written
     */
    @Override
    public void runAlgorithm(String[] parameters, String inputFile, String outputFile)
            throws IOException {

        int k = getParamAsInteger(parameters[0]);
        String separator = " ";

        if (parameters.length >= 2 && !"".equals(parameters[1])) {
            separator = parameters[1];
        }

        // Apply the HTK-negFIN algorithm
        AlgoHTKnegFIN algorithm = new AlgoHTKnegFIN();
        algorithm.runAlgorithm(inputFile, outputFile, k, separator);
        algorithm.printStats();
    }

    /**
     * Returns the parameter descriptors used by the SPMF graphical interface to
     * build the parameter input form for this algorithm.
     *
     * @return array of {@link DescriptionOfParameter} objects describing each
     *         accepted parameter
     */
    @Override
    public DescriptionOfParameter[] getParametersDescription() {
        DescriptionOfParameter[] parameters = new DescriptionOfParameter[2];
        parameters[0] = new DescriptionOfParameter(
                "k (top-k itemsets)", "(e.g. 100)", Integer.class, false);
        parameters[1] = new DescriptionOfParameter(
                "Separator", "(e.g. space ' ')", String.class, true);
        return parameters;
    }

    /** {@inheritDoc} */
    @Override
    public String getImplementationAuthorNames() {
        return "Konstantinos Malliaridis and Stefanos Ougiaroglou";
    }

    /** {@inheritDoc} */
    @Override
    public String[] getInputFileTypes() {
        return new String[]{
            "Database of instances",
            "Transaction database",
            "Simple transaction database"
        };
    }

    /** {@inheritDoc} */
    @Override
    public String[] getOutputFileTypes() {
        return new String[]{
            "Patterns",
            "Frequent patterns",
            "Frequent itemsets"
        };
    }

    /** {@inheritDoc} */
    @Override
    public AlgorithmType getAlgorithmType() {
        return AlgorithmType.DATA_MINING;
    }
}