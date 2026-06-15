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

import java.io.UnsupportedEncodingException;
import java.net.URL;

/**
 * Example of how to run classification experiments programmatically
 * (without the GUI), comparing multiple classification algorithms
 * using holdout or k-fold cross validation.
 *
 * @author Philippe Fournier-Viger, 2024
 */
public class MainTestExperimenterForClassification {

    public static void main(String[] args) throws Exception {

        // The input dataset file
        String datasetPath = fileToPath("tennisExtended.txt");

        // The target class attribute name
        String targetClassName = "play";

        // The directory where results from the experiments will be stored
        String outputDirectory = "CLASSIFICATION_EXPERIMENTS";

        // A list of classification algorithm fully-qualified class names to compare
        String[] algorithmNames = new String[]{
            "ca.pfv.spmf.algorithms.classifiers.acac.AlgoACAC",
            "ca.pfv.spmf.algorithms.classifiers.accf.AlgoACCF"
        };

        // Create the experimenter
        ExperimenterForClassification experimenter = new ExperimenterForClassification();

        // ===== EXAMPLE 1: Holdout validation =====
        // Use 50% of the data for training, 50% for testing
        double holdoutPercentage = 0.5;
        System.out.println("===== HOLDOUT EXPERIMENT =====");
        experimenter.runHoldoutExperiment(algorithmNames, datasetPath,
                targetClassName, holdoutPercentage, outputDirectory + "/holdout");

        // ===== EXAMPLE 2: K-Fold cross validation =====
        // Use 10-fold cross validation
        int kFoldCount = 10;
        System.out.println("===== K-FOLD EXPERIMENT =====");
        experimenter.runKFoldExperiment(algorithmNames, datasetPath,
                targetClassName, kFoldCount, outputDirectory + "/kfold");
    }

    /**
     * Convert a file name to a path
     *
     * @param filename the file name
     * @return the path as a string
     * @throws UnsupportedEncodingException if encoding is not supported
     */
    public static String fileToPath(String filename) throws UnsupportedEncodingException {
        URL url = MainTestExperimenterForClassification.class.getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
    }
}