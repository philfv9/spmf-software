package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

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
* Do not remove copyright and license information from files.
*/

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.frequentpatterns.pftree.AlgoPFTree;

/**
 * This class describes the PFTree algorithm parameters.
 * It is designed to be used by the graphical and command line interface.
 *
 * @see AlgoPFTree
 * @author Philippe Fournier-Viger, 2026
 */
public class DescriptionAlgoPFTree extends DescriptionOfAlgorithm {

    /**
     * Default constructor.
     */
    public DescriptionAlgoPFTree() {
    }

    @Override
    public String getName() {
        return "PFTree";
    }

    @Override
    public String getAlgorithmCategory() {
        return "PERIODIC PATTERN MINING";
    }

    @Override
    public String getURLOfDocumentation() {
        return "http://www.philippe-fournier-viger.com/spmf/PFTREE.php";
    }

    @Override
    public void runAlgorithm(String[] parameters, String inputFile, String outputFile)
            throws IOException {
        int maxPeriodicity = getParamAsInteger(parameters[0]);
        double minSupport = getParamAsDouble(parameters[1]);

        AlgoPFTree algo = new AlgoPFTree();
        algo.runAlgorithm(inputFile, outputFile, maxPeriodicity, minSupport);
        algo.printStats();
    }

    @Override
    public DescriptionOfParameter[] getParametersDescription() {
        DescriptionOfParameter[] parameters = new DescriptionOfParameter[2];
        parameters[0] = new DescriptionOfParameter("Maximum periodicity",
                "(e.g. 4 transactions)", Integer.class, false);
        parameters[1] = new DescriptionOfParameter("Minimum support (%)",
                "(e.g. 50%)", Double.class, false);
        return parameters;
    }

    @Override
    public String getImplementationAuthorNames() {
        return "Philippe Fournier-Viger";
    }

    @Override
    public String[] getInputFileTypes() {
        return new String[]{"Database of instances", "Transaction database",
                "Simple transaction database"};
    }

    @Override
    public String[] getOutputFileTypes() {
        return new String[]{"Patterns", "Frequent patterns", "Periodic patterns",
                "Periodic frequent patterns", "Periodic frequent itemsets"};
    }

    @Override
    public AlgorithmType getAlgorithmType() {
        return AlgorithmType.DATA_MINING;
    }
}