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
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
* Do not remove copyright and license information from files.
*/

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.frequentpatterns.mtkpp.AlgoMTKPP;

/**
 * This class describes the MTKPP algorithm parameters for the graphical and command line interface.
 *
 * @see AlgoMTKPP
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoMTKPP extends DescriptionOfAlgorithm {

    /**
     * Default constructor.
     */
    public DescriptionAlgoMTKPP() {
    }

    @Override
    public String getName() {
        return "MTKPP";
    }

    @Override
    public String getAlgorithmCategory() {
        return "PERIODIC PATTERN MINING";
    }

    @Override
    public String getURLOfDocumentation() {
        return "http://www.philippe-fournier-viger.com/spmf/MTKPP.php";
    }

    @Override
    public void runAlgorithm(String[] parameters, String inputFile, String outputFile)
            throws IOException {
        int k = getParamAsInteger(parameters[0]);
        int maxPeriodicity = getParamAsInteger(parameters[1]);

        AlgoMTKPP algo = new AlgoMTKPP();

        if (parameters.length >= 3 && "".equals(parameters[2]) == false) {
            algo.setMinimumLength(getParamAsInteger(parameters[2]));
        }

        if (parameters.length >= 4 && "".equals(parameters[3]) == false) {
            algo.setMaximumLength(getParamAsInteger(parameters[3]));
        }

        algo.runAlgorithm(inputFile, outputFile, k, maxPeriodicity);
        algo.printStats();
    }

    @Override
    public DescriptionOfParameter[] getParametersDescription() {
        DescriptionOfParameter[] parameters = new DescriptionOfParameter[4];
        parameters[0] = new DescriptionOfParameter("K", "(e.g. 5 patterns)", Integer.class, false);
        parameters[1] = new DescriptionOfParameter("Maximum periodicity", "(e.g. 3 transactions)", Integer.class, false);
        parameters[2] = new DescriptionOfParameter("Minimum number of items", "(e.g. 1 items)", Integer.class, true);
        parameters[3] = new DescriptionOfParameter("Maximum number of items", "(e.g. 5 items)", Integer.class, true);
        return parameters;
    }

    @Override
    public String getImplementationAuthorNames() {
        return "Philippe Fournier-Viger";
    }

    @Override
    public String[] getInputFileTypes() {
        return new String[]{"Database of instances", "Transaction database", "Simple transaction database"};
    }

    @Override
    public String[] getOutputFileTypes() {
        return new String[]{"Patterns", "Frequent patterns", "Periodic patterns",
                "Periodic frequent patterns"};
    }

    @Override
    public AlgorithmType getAlgorithmType() {
        return AlgorithmType.DATA_MINING;
    }
}