package ca.pfv.spmf.experiments.scalability;

import java.io.UnsupportedEncodingException;
import java.net.URL;

/*
 * This file is copyright (c) 2021 Philippe Fournier-Viger
 *
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */

/**
 * Example showing how to use {@link ExperimenterForScalability}
 * programmatically (without the GUI).
 *
 * <p>One dataset file is provided. The experimenter automatically
 * creates subset files containing the first N% of data lines and
 * runs each algorithm against each subset.
 *
 * <p>"##" does NOT appear anywhere in this file.
 *
 * @author Philippe Fournier-Viger
 */
public class MainTestExperimenterForScalability {

    public static void main(String[] args) throws Exception {

        // ── 1. Algorithms to compare ─────────────────────────────────
        String[] algorithmNames = new String[] {
                "Eclat",
                "Apriori"
        };

        // ── 2. Fixed algorithm parameters ────────────────────────────
        // These are passed unchanged to every algorithm on every run.
        String[] fixedParams = new String[] { "0.4"};

        // ── 3. Single input dataset ───────────────────────────────────
        // The experimenter reads this file and creates subsets.
        String inputFile = fileToPath("contextPasquier99.txt");

        // ── 4. Subset sizes as percentages ───────────────────────────
        // Each value must be an integer between 1 and 100.
        // The experimenter will run algorithms on the first 20%,
        // then the first 40%, ..., then the full 100% of the data.
        String[] percentages = new String[] {
                "20", "40", "60", "80", "100"
        };

        // ── 5. Output directory ───────────────────────────────────────
        String outputDirectory = "SCALABILITY_EXPERIMENTS";

        // ── 6. Timeout per run (milliseconds) ────────────────────────
        int timeoutInMilliseconds = 120000; // 2 minutes

        // ── 7. Options ────────────────────────────────────────────────
        boolean compareOutputSizes   = true;
        boolean showCommand          = false;
        boolean generateLatexFigures = true;

        // ── 8. Create the experimenter and run ────────────────────────
        ExperimenterForScalability experimenter =
                new ExperimenterForScalability();

        // Adjust to the actual location of spmf.jar on your machine
        experimenter.setSPMFJarFilePath(
                "C:\\Users\\Phil\\Desktop\\spmf.jar");

        // String used in result tables when a run times out
        experimenter.setTimeoutCodeS("-");

        // Run — this is the only method you need to call
        experimenter.runScalabilityExperiment(
                algorithmNames,
                fixedParams,
                inputFile,
                percentages,
                outputDirectory,
                timeoutInMilliseconds,
                compareOutputSizes,
                showCommand,
                generateLatexFigures);
    }

    public static String fileToPath(String filename)
            throws UnsupportedEncodingException {
        URL url = MainTestExperimenterForScalability.class
                .getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
    }
}