package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.gui.experiments.ExperimenterScalabilityWindow;

/**
 * Describes the scalability experiment tool for the SPMF GUI.
 *
 * runAlgorithm() opens ExperimenterScalabilityWindow.
 * It does NOT open ExperimenterWindow (the old parameter-change window).
 *
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