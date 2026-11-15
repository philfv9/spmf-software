package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.frequentpatterns.ppfpgrowth.AlgoPPFPgrowth;

/*
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. SPMF is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright (c) 2026 Illyes Hammimid, Farid Nouioua et al.
 * 
 * Do not remove copyright and license information
 */

/**
 * This class describes the PPFP-growth algorithm (probabilistic periodic-frequent
 * patterns in uncertain transaction databases) for the graphical and command
 * line interfaces of SPMF.
 * <p>
 * Note: SPMF already contains an algorithm named "PPFP" (productive periodic
 * frequent patterns, Nofong 2016), which is a different algorithm; the final
 * name must be agreed with the SPMF maintainer.
 *
 * @see AlgoPPFPgrowth
 * @author Illyes Hamimid
 */
public class DescriptionAlgoPPFPgrowth extends DescriptionOfAlgorithm {

    /** Default constructor */
    public DescriptionAlgoPPFPgrowth() {
    }

    @Override
    public String getName() {
        return "PPFP-growth";
    }

    @Override
    public String getAlgorithmCategory() {
        return "PERIODIC PATTERN MINING";
    }

    @Override
    public String getURLOfDocumentation() {
        // to be assigned by the SPMF maintainer when the documentation page is created
        return "http://www.philippe-fournier-viger.com/spmf/PPFPGrowth_probabilistic.php";
    }

    @Override
    public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
        int maxPer = getParamAsInteger(parameters[0]);
        double minSup = getParamAsDouble(parameters[1]);
        double minProbability = getParamAsDouble(parameters[2]);

        AlgoPPFPgrowth algorithm = new AlgoPPFPgrowth();
        if (parameters.length >= 4 && "".equals(parameters[3]) == false) {
            algorithm.setMaximumPatternLength(getParamAsInteger(parameters[3]));
        }
        algorithm.runAlgorithm(inputFile, outputFile, maxPer, minSup, minProbability);
        algorithm.printStats();
    }

    @Override
    public DescriptionOfParameter[] getParametersDescription() {
        DescriptionOfParameter[] parameters = new DescriptionOfParameter[4];
        parameters[0] = new DescriptionOfParameter("Maximum periodicity", "(e.g. 4 transactions)", Integer.class, false);
        parameters[1] = new DescriptionOfParameter("Minimum expected support (%)", "(e.g. 0.35 or 35%)", Double.class, false);
        parameters[2] = new DescriptionOfParameter("Minimum probability of periodicity", "(e.g. 0.6)", Double.class, false);
        parameters[3] = new DescriptionOfParameter("Max pattern length", "(e.g. 3 items)", Integer.class, true);
        return parameters;
    }

    @Override
    public String getImplementationAuthorNames() {
        return "Illyes Hammimid, Farid Nouioua et al.";
    }

    @Override
    public String[] getInputFileTypes() {
        return new String[] { "Database of instances", "Transaction database", "Uncertain transaction database" };
    }

    @Override
    public String[] getOutputFileTypes() {
        return new String[] { "Patterns", "Periodic patterns", "Uncertain patterns",
                "Probabilistic periodic frequent itemsets" };
    }

    @Override
    public AlgorithmType getAlgorithmType() {
        return AlgorithmType.DATA_MINING;
    }
}
