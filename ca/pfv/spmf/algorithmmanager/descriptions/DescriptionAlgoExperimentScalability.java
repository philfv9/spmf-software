package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.gui.experiments.ExperimenterScalabilityWindow;
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
 * Describes the scalability experiment tool for the SPMF GUI.
 *
 * @see ExperimenterScalabilityWindow
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoExperimentScalability
        extends DescriptionOfAlgorithm {

    public DescriptionAlgoExperimentScalability() {
    }

    @Override
    public String getName() {
        return "Performance_experiment_scalability";
    }

    @Override
    public String getAlgorithmCategory() {
        return "TOOLS - RUN EXPERIMENTS";
    }

    @Override
    public String getURLOfDocumentation() {
        return "http://www.philippe-fournier-viger.com/spmf/"
             + "ExperimenterScalability.php";
    }

    /**
     * Opens the scalability window.
     * This MUST open ExperimenterScalabilityWindow and nothing else.
     */
    @Override
    public void runAlgorithm(String[] parameters,
            String inputFile, String outputFile) throws IOException {

        ExperimenterScalabilityWindow w =
                new ExperimenterScalabilityWindow();
        w.setVisible(true);
        w.setTitle("Scalability Experiment");
    }

    @Override
    public DescriptionOfParameter[] getParametersDescription() {
        return new DescriptionOfParameter[0];
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