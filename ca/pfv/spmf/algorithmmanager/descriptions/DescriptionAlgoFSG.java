package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.graph_mining.fsg.AlgoFSG;
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
 * This class describes the FSG algorithm parameters. It is designed to be used
 * by the graphical and command line interface.
 *
 * @see AlgoFSG
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoFSG extends DescriptionOfAlgorithm {

    /**
     * Default constructor
     */
    public DescriptionAlgoFSG() {
    }

    @Override
    public String getName() {
        return "FSG";
    }

    @Override
    public String getAlgorithmCategory() {
        return "GRAPH PATTERN MINING";
    }

    @Override
    public String getURLOfDocumentation() {
        return "http://www.philippe-fournier-viger.com/spmf/FSG_fsm_mining.php";
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
        AlgoFSG algo = new AlgoFSG();
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