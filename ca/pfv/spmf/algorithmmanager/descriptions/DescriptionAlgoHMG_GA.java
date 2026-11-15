
package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.sequentialpatterns.hmg.AlgoHMG;
import ca.pfv.spmf.algorithms.sequentialpatterns.hmg.HMGCrossoverVariant;
import ca.pfv.spmf.algorithms.sequentialpatterns.hmg.HMGMutationVariant;
/* This file is copyright (c) M. Zohaib Nawaz, M. Saqib Nawaz, Philippe Fournier-Viger et al.
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
 * This class describes the HMG algorithm (Genetic Algorithm variant) parameters.
 * It is designed to be used by the graphical and command line interface.
 * 
 * @see AlgoHMG
 * @author M. Zohaib Nawaz, M. Saqib Nawaz, Philippe Fournier-Viger et al.
 */
public class DescriptionAlgoHMG_GA extends DescriptionOfAlgorithm {

    public DescriptionAlgoHMG_GA() {
    }

    @Override
    public String getName() {
        return "HMG-GA";
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
        // Parameters
        int generations = getParamAsInteger(parameters[0]);
        int patterns = getParamAsInteger(parameters[1]);
        HMGCrossoverVariant crossover = HMGCrossoverVariant.valueOf(parameters[2].toUpperCase());
        HMGMutationVariant mutation = HMGMutationVariant.valueOf(parameters[3].toUpperCase());
        
        boolean spmfStyleOutput = getParamAsBoolean(parameters[4]);

        AlgoHMG hmg = new AlgoHMG();
        hmg.runGAAlgorithm(inputFile, generations, patterns, outputFile, crossover, mutation, spmfStyleOutput);
        hmg.printStats();
    }

    @Override
    public DescriptionOfParameter[] getParametersDescription() {
        DescriptionOfParameter[] parameters = new DescriptionOfParameter[5];
        parameters[0] = new DescriptionOfParameter("Number of generations", "(e.g. 30)", Integer.class, false);
        parameters[1] = new DescriptionOfParameter("Number of patterns", "(e.g. 2)", Integer.class, false);
        parameters[2] = new DescriptionOfParameter("Crossover variant", "SINGLE", String.class, false);
        parameters[3] = new DescriptionOfParameter("Mutation variant", "SINGLE", String.class, false);
        parameters[4] = new DescriptionOfParameter("SPMF style output", "false", Boolean.class, false);
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
