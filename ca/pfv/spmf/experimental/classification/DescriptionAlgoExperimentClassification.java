package ca.pfv.spmf.experimental.classification;

/*
 * This file is copyright (c) 2024 Philippe Fournier-Viger
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
 */

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;

/**
 * This class describes the tool for running classification experiments,
 * comparing multiple classification algorithms using holdout or k-fold
 * cross-validation.
 *
 * @see ExperimenterForClassification
 * @author Philippe Fournier-Viger, 2024
 */
public class DescriptionAlgoExperimentClassification extends DescriptionOfAlgorithm {

    /**
     * Default constructor
     */
    public DescriptionAlgoExperimentClassification() {
    }

    @Override
    public String getName() {
        return "Classification_experiment";
    }

    @Override
    public String getAlgorithmCategory() {
        return "TOOLS - RUN EXPERIMENTS";
    }

    @Override
    public String getURLOfDocumentation() {
        return "https://www.philippe-fournier-viger.com/spmf/ClassificationExperimenter.php";
    }

    @Override
    public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
        // Open the classification experiment window
        ExperimenterClassificationWindow window = new ExperimenterClassificationWindow();
        window.setVisible(true);
        window.setTitle("Run a Classification Experiment");
    }

    @Override
    public DescriptionOfParameter[] getParametersDescription() {
        // No parameters needed - all configuration is done in the GUI
        DescriptionOfParameter[] parameters = new DescriptionOfParameter[0];
        return parameters;
    }

    @Override
    public String getImplementationAuthorNames() {
        return "Philippe Fournier-Viger";
    }

    @Override
    public String[] getInputFileTypes() {
        return null;
    }

    @Override
    public String[] getOutputFileTypes() {
        return null;
    }

    @Override
    public AlgorithmType getAlgorithmType() {
        return AlgorithmType.EXPERIMENT_TOOL;
    }
}