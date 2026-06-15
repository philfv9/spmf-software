package ca.pfv.spmf.experiments.scalability;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.ProcessBuilder.Redirect;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import ca.pfv.spmf.gui.preferences.PreferencesManager;

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
 *
 * SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * Runs scalability experiments by:
 * <ol>
 *   <li>Reading a single input dataset file.</li>
 *   <li>Writing temporary subset files containing the first N% of lines.</li>
 *   <li>Running each algorithm on each subset and recording time / memory /
 *       output size.</li>
 * </ol>
 *
 * No "##" placeholder is used. Parameters are fixed across all runs.
 *
 * @author Philippe Fournier-Viger
 */
public class ExperimenterForScalability {

    // ----------------------------------------------------------------
    // Configuration fields
    // ----------------------------------------------------------------

    /** Path to the SPMF jar */
    private String spmfJarPath = "spmf.jar";

    /** File that receives subprocess stdout + stderr */
    private String logFilePath = null;

    /** Text written in result tables when a run times out */
    private String timeoutCodeString = "TIMEOUT";

    /** Sentinel stored in result arrays for a timed-out run */
    private final int timeoutCode = -999;

    /** Formats doubles with two decimal places */
    private final DecimalFormat formatTwoDecimals;

    /** Formats doubles retaining all significant digits */
    private final DecimalFormat formatAllDecimals;

    // ----------------------------------------------------------------
    // Constructor
    // ----------------------------------------------------------------

    public ExperimenterForScalability() {
        formatTwoDecimals = (DecimalFormat) NumberFormat.getNumberInstance();
        formatTwoDecimals.applyPattern("#.##");
        formatAllDecimals = (DecimalFormat) NumberFormat.getNumberInstance();
    }

    // ----------------------------------------------------------------
    // Main entry point
    // ----------------------------------------------------------------

    /**
     * Run scalability experiments.
     *
     * <p>For each percentage value the method:
     * <ol>
     *   <li>Reads the first {@code pct}% of non-comment lines from
     *       {@code inputFile}.</li>
     *   <li>Writes those lines to a temporary file in {@code outputDirectory}.</li>
     *   <li>Runs every algorithm against that temporary file.</li>
     * </ol>
     *
     * @param algorithmNames          algorithms to compare (one or more)
     * @param params                  fixed algorithm parameters in order
     *                                (must NOT contain "##")
     * @param inputFile               the full dataset file
     * @param percentages             subset sizes, e.g. {"20", "40", "60",
     *                                "80", "100"}  (integer percent strings)
     * @param outputDirectory         directory where results are written
     * @param timeoutInMilliseconds   max wall-clock time per run
     * @param compareOutputSize       count lines in each output file
     * @param showCommand             print the exact command to stdout
     * @param generatePGFPLOTFigures  write a compilable LaTeX file
     * @throws Exception on bad inputs or I/O errors
     */
    public void runScalabilityExperiment(
            String[] algorithmNames,
            String[] params,
            String   inputFile,
            String[] percentages,
            String   outputDirectory,
            int      timeoutInMilliseconds,
            boolean  compareOutputSize,
            boolean  showCommand,
            boolean  generatePGFPLOTFigures) throws Exception {

        // ── Validate ────────────────────────────────────────────────
        if (algorithmNames == null || algorithmNames.length == 0) {
            throw new Exception("Provide at least one algorithm name.");
        }
        if (inputFile == null || inputFile.isEmpty()) {
            throw new Exception("Provide an input dataset file.");
        }
        if (percentages == null || percentages.length == 0) {
            throw new Exception(
                "Provide at least one percentage value "
                + "(e.g. \"20\", \"50\", \"100\").");
        }
        
     // Validate SPMF jar path
        File jarFile = new File(spmfJarPath);
        if (!jarFile.exists() || !jarFile.isFile()) {
            throw new Exception(
                "Invalid SPMF jar path: \"" + spmfJarPath + "\"\n"
              + "Make sure the file exists and the path is correct.\n"
              + "Current working directory: " + new File(".").getAbsolutePath()
            );
        }

        // "##" must not appear in params for a scalability experiment
        if (params != null) {
            for (String p : params) {
                if ("##".equals(p)) {
                    throw new Exception(
                        "Do not use '##' in a scalability experiment. "
                        + "All parameters are fixed; only the dataset "
                        + "subset size changes.");
                }
            }
        }

        // Parse and validate percentages
        int[] pctValues = new int[percentages.length];
        for (int i = 0; i < percentages.length; i++) {
            try {
                pctValues[i] = Integer.parseInt(percentages[i].trim());
            } catch (NumberFormatException e) {
                throw new Exception(
                    "Percentage value is not a valid integer: \""
                    + percentages[i] + "\"");
            }
            if (pctValues[i] <= 0 || pctValues[i] > 100) {
                throw new Exception(
                    "Percentage must be between 1 and 100, got: "
                    + pctValues[i]);
            }
        }

        // ── Prepare output directory and log file ───────────────────
        File outDir = new File(outputDirectory);
        outDir.mkdirs();

        String logFile = outputDirectory
                + File.separatorChar + "EXPERIMENT_LOG.txt";
        File logObj = new File(logFile);
        if (logObj.exists()) {
            logObj.delete();
        }
        setRedirectOutputPath(logFile);

        // ── Count data lines in the full dataset ────────────────────
        // We count only lines that are not empty and not comments/metadata
        // (lines starting with #, %, @).
        int totalLines = countDataLines(inputFile);
        if (totalLines == 0) {
            throw new Exception(
                "The input file contains no data lines: " + inputFile);
        }

        System.out.println(
            "**************************************************");
        System.out.println(
            "*****   RUNNING SCALABILITY EXPERIMENTS      *****");
        System.out.println(
            "**************************************************");
        System.out.println(" INPUT FILE       : " + inputFile);
        System.out.println(" TOTAL DATA LINES : " + totalLines);
        System.out.println(" OUTPUT DIRECTORY : " + outputDirectory);
        System.out.println(
            " ALGORITHMS       : " + Arrays.toString(algorithmNames));
        System.out.println(
            " FIXED PARAMETERS : " + Arrays.toString(params));
        System.out.println(
            " PERCENTAGES      : " + Arrays.toString(percentages));
        System.out.println();

        // ── Allocate result arrays ──────────────────────────────────
        final int A = algorithmNames.length;
        final int P = percentages.length;

        double[][] runtimes      = new double[A][P];
        double[][] memoryResults = new double[A][P];
        int[][]    outputSizes   = new int[A][P];

        // ── Build base command  ─────────────────────────────────────
        // Slots:
        //   0 = "java"
        //   1 = "-jar"
        //   2 = spmfJarPath
        //   3 = "run"
        //   4 = algorithm name      <- updated per algorithm
        //   5 = subset input file   <- updated per percentage
        //   6 = output file         <- updated per algorithm+percentage
        //   7+ = fixed params
        List<String> command = new ArrayList<>();
        command.add("java");
        command.add("-jar");
        command.add(spmfJarPath);
        command.add("run");
        command.add("ALGO_PLACEHOLDER");    // 4
        command.add("INPUT_PLACEHOLDER");   // 5
        command.add("OUTPUT_PLACEHOLDER");  // 6
        if (params != null) {
            for (String p : params) {
                command.add(p);
            }
        }

        // ── Run experiments ─────────────────────────────────────────
        int experimentCount = 1;

        for (int j = 0; j < P; j++) {

            int    pct        = pctValues[j];
            int    lineCount  = Math.max(1,
                                    (int) Math.round(totalLines * pct / 100.0));
            String subsetFile = outputDirectory
                    + File.separatorChar
                    + "subset_" + pct + "pct.txt";

            // Write the subset file once (shared by all algorithms)
            writeSubsetFile(inputFile, subsetFile, lineCount);
            System.out.println(
                " Subset " + pct + "% -> "
                + lineCount + " lines -> " + subsetFile);

            // Update the input slot in the command
            command.set(5, subsetFile);

            for (int m = 0; m < A; m++) {

                // Update algorithm name
                command.set(4, algorithmNames[m]);

                // Build and update output file path
                String resultFile = outputDirectory
                        + File.separatorChar
                        + algorithmNames[m] + "_" + pct + "pct.txt";
                command.set(6, resultFile);

                // Print experiment header
                System.out.println(
                    " *****  EXPERIMENT " + experimentCount++);
                System.out.println(
                    "   ALGORITHM  : " + algorithmNames[m]);
                System.out.println(
                    "   SUBSET     : " + pct + "%  ("
                    + lineCount + " lines)");

                if (true) {
                    StringBuilder sb =
                            new StringBuilder("   COMMAND: ");
                    for (String s : command) {
                        sb.append(s).append(' ');
                    }
                    System.out.println(sb);
                }

                // Launch subprocess
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectOutput(
                        Redirect.appendTo(new File(logFilePath)));
                pb.redirectError(
                        Redirect.appendTo(new File(logFilePath)));

                long startTime = System.currentTimeMillis();
                Process process = pb.start();
                boolean finished = process.waitFor(
                        timeoutInMilliseconds, TimeUnit.MILLISECONDS);
                long elapsed = System.currentTimeMillis() - startTime;

                if (!finished) {
                    System.out.println("   TIME: TIME-OUT");
                    process.destroyForcibly();
                    runtimes[m][j]      = timeoutCode;
                    memoryResults[m][j] = timeoutCode;
                    if (compareOutputSize) {
                        outputSizes[m][j] = timeoutCode;
                    }
                } else {
                    runtimes[m][j] = elapsed / 1000d;
                    memoryResults[m][j] =
                            PreferencesManager.getInstance()
                                             .getLastMemoryUsage();

                    System.out.println(
                        "   TIME: "
                        + formatTwoDecimals.format(runtimes[m][j])
                        + " s   MEMORY: "
                        + formatTwoDecimals.format(memoryResults[m][j])
                        + " MB");

                    if (compareOutputSize) {
                        outputSizes[m][j] =
                                calculateOutputFileSize(resultFile);
                        System.out.println(
                            "   OUTPUT SIZE: "
                            + outputSizes[m][j] + " lines");
                    }
                    System.out.println();
                }
            }
        }

        // ── Print and save results ──────────────────────────────────
        System.out.println();
        System.out.println(
            "**************************************************");
        System.out.println(
            "*****              RESULTS                   *****");
        System.out.println(
            "**************************************************");

        String result = buildResultString(
                inputFile, params, algorithmNames, percentages,
                A, P, runtimes, memoryResults, outputSizes,
                compareOutputSize);

        System.out.println(result);

        String resultPath = outputDirectory
                + File.separatorChar + "EXPERIMENT_RESULT.txt";
        try (BufferedWriter w =
                     new BufferedWriter(new FileWriter(resultPath))) {
            w.write(result);
        }

        // ── Generate LaTeX figures ──────────────────────────────────
        if (generatePGFPLOTFigures) {
            writeLatexFile(
                    outputDirectory, algorithmNames,
                    percentages, pctValues,
                    A, P, runtimes, memoryResults, outputSizes,
                    compareOutputSize);
        }
    }

    // ----------------------------------------------------------------
    // Result-string builder
    // ----------------------------------------------------------------

    private String buildResultString(
            String   inputFile,
            String[] params,
            String[] algorithmNames,
            String[] percentages,
            int A, int P,
            double[][] runtimes,
            double[][] memoryResults,
            int[][]    outputSizes,
            boolean    compareOutputSize) {

        StringBuffer buf = new StringBuffer();

        buf.append("SCALABILITY EXPERIMENT RESULTS")
           .append(System.lineSeparator());
        buf.append("INPUT FILE: ").append(inputFile)
           .append(System.lineSeparator());
        buf.append("FIXED PARAMETERS: ")
           .append(Arrays.toString(params))
           .append(System.lineSeparator());
        buf.append(System.lineSeparator());

        // Time table
        appendTable(buf, "TIME (S)", "% of dataset",
                algorithmNames, percentages,
                A, P, runtimes, false);
        buf.append(System.lineSeparator());

        // Memory table
        appendTable(buf, "MEMORY (MB)", "% of dataset",
                algorithmNames, percentages,
                A, P, toDoubleArray(memoryResults), false);
        buf.append(System.lineSeparator());

        // Output size table (always included in file; data is 0 if
        // compareOutputSize was false)
        double[][] outDouble = new double[A][P];
        for (int m = 0; m < A; m++) {
            for (int j = 0; j < P; j++) {
                outDouble[m][j] = outputSizes[m][j];
            }
        }
        appendTable(buf, "OUTPUT_SIZE (LINES)", "% of dataset",
                algorithmNames, percentages,
                A, P, outDouble, true);

        return buf.toString();
    }

    /**
     * Appends a tab-separated table section to buf.
     */
    private void appendTable(
            StringBuffer buf,
            String       title,
            String       xLabel,
            String[]     algorithmNames,
            String[]     colLabels,
            int A, int P,
            double[][]   data,
            boolean      isInteger) {

        buf.append(title).append(System.lineSeparator());
        buf.append(xLabel).append("\t");
        for (String lbl : colLabels) {
            buf.append(lbl).append("%\t");
        }
        buf.append(System.lineSeparator());

        for (int m = 0; m < A; m++) {
            buf.append(algorithmNames[m]).append("\t");
            for (int j = 0; j < P; j++) {
                double v = data[m][j];
                if (v == timeoutCode) {
                    buf.append(timeoutCodeString);
                } else if (isInteger) {
                    buf.append((long) v);
                } else {
                    buf.append(formatTwoDecimals.format(v));
                }
                buf.append("\t");
            }
            buf.append(System.lineSeparator());
        }
    }

    // ----------------------------------------------------------------
    // LaTeX output
    // ----------------------------------------------------------------

    private void writeLatexFile(
            String   outputDirectory,
            String[] algorithmNames,
            String[] percentages,
            int[]    pctValues,
            int A, int P,
            double[][] runtimes,
            double[][] memoryResults,
            int[][]    outputSizes,
            boolean    compareOutputSize) throws Exception {

        double[] xValues = new double[P];
        for (int j = 0; j < P; j++) {
            xValues[j] = pctValues[j];
        }

        double[][] outDouble = new double[A][P];
        for (int m = 0; m < A; m++) {
            for (int j = 0; j < P; j++) {
                outDouble[m][j] = outputSizes[m][j];
            }
        }

        StringBuffer buf = new StringBuffer();
        buf.append("\\documentclass{article}")
           .append(System.lineSeparator());
        buf.append("\\usepackage{tikz}")
           .append(System.lineSeparator());
        buf.append("\\usepackage{pgfplots}")
           .append(System.lineSeparator());
        buf.append("\\pgfplotsset{compat=1.18}")
           .append(System.lineSeparator());
        buf.append("\\begin{document}")
           .append(System.lineSeparator());
        buf.append(System.lineSeparator());

        buf.append(buildFigure(algorithmNames, xValues,
                "\\% of dataset", "Time (s)",
                runtimes, false));
        buf.append(System.lineSeparator());

        buf.append(buildFigure(algorithmNames, xValues,
                "\\% of dataset", "Memory (MB)",
                toDoubleArray(memoryResults), false));
        buf.append(System.lineSeparator());

        if (compareOutputSize) {
            buf.append(buildFigure(algorithmNames, xValues,
                    "\\% of dataset", "Output size (lines)",
                    outDouble, true));
            buf.append(System.lineSeparator());
        }

        buf.append("\\end{document}")
           .append(System.lineSeparator());

        String texPath = outputDirectory
                + File.separatorChar + "PGPLOT_FIGURES.tex";
        try (BufferedWriter w =
                     new BufferedWriter(new FileWriter(texPath))) {
            w.write(buf.toString());
        }
        System.out.println("LaTeX figures written to: " + texPath);
    }

    /** Builds one tikzpicture environment. */
    private String buildFigure(
            String[]   algorithmNames,
            double[]   xValues,
            String     xLabel,
            String     yLabel,
            double[][] data,
            boolean    isInteger) {

        final int A = algorithmNames.length;
        StringBuilder sb = new StringBuilder();
        sb.append("\\begin{tikzpicture}")
          .append(System.lineSeparator());
        sb.append("\\begin{axis}[")
          .append(System.lineSeparator());
        sb.append("  xlabel={").append(xLabel).append("},")
          .append(System.lineSeparator());
        sb.append("  ylabel={").append(yLabel).append("},")
          .append(System.lineSeparator());
        sb.append("  legend pos=north west,")
          .append(System.lineSeparator());
        sb.append("  cycle list name=color,")
          .append(System.lineSeparator());
        sb.append("  mark=*]")
          .append(System.lineSeparator());

        for (int m = 0; m < A; m++) {
            String name = algorithmNames[m].replace('_', '-');
            sb.append("\\addplot coordinates {");
            for (int j = 0; j < xValues.length; j++) {
                if (data[m][j] != timeoutCode) {
                    sb.append("(").append(xValues[j]).append(",");
                    if (isInteger) {
                        sb.append((long) data[m][j]);
                    } else {
                        sb.append(data[m][j]);
                    }
                    sb.append(")");
                }
            }
            sb.append("};").append(System.lineSeparator());
            sb.append("\\addlegendentry{").append(name).append("}")
              .append(System.lineSeparator());
        }

        sb.append("\\end{axis}").append(System.lineSeparator());
        sb.append("\\end{tikzpicture}")
          .append(System.lineSeparator());
        return sb.toString();
    }

    // ----------------------------------------------------------------
    // File helpers
    // ----------------------------------------------------------------

    /**
     * Counts data lines in a file (skips empty lines and lines starting
     * with '#', '%', or '@').
     */
    private int countDataLines(String filePath) throws Exception {
        int count = 0;
        try (BufferedReader br =
                     new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!isIgnoredLine(line)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Writes a subset file containing the first {@code lineCount} data
     * lines from {@code sourcePath}. Comment / metadata lines that
     * appear before the first data line are preserved; those that appear
     * after the cut-off are dropped.
     */
    private void writeSubsetFile(
            String sourcePath,
            String destPath,
            int    lineCount) throws Exception {

        try (BufferedReader br =
                     new BufferedReader(new FileReader(sourcePath));
             BufferedWriter bw =
                     new BufferedWriter(new FileWriter(destPath))) {

            String line;
            int    written = 0;

            while ((line = br.readLine()) != null) {
                if (isIgnoredLine(line)) {
                    // Copy comment / metadata lines through
                    bw.write(line);
                    bw.newLine();
                } else {
                    if (written < lineCount) {
                        bw.write(line);
                        bw.newLine();
                        written++;
                    } else {
                        break; // Enough data lines written
                    }
                }
            }
        }
    }

    /** Returns true for lines that should be skipped when counting. */
    private boolean isIgnoredLine(String line) {
        return line.isEmpty()
                || line.charAt(0) == '#'
                || line.charAt(0) == '%'
                || line.charAt(0) == '@';
    }

    /**
     * Counts non-ignored lines in an algorithm output file.
     *
     * @return number of result lines, or -1 on I/O error
     */
    private int calculateOutputFileSize(String filePath) {
        int size = 0;
        try (BufferedReader br =
                     new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!isIgnoredLine(line)) {
                    size++;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
        return size;
    }

    /** Converts int[][] to double[][] (for shared table helpers). */
    private double[][] toDoubleArray(double[][] src) {
        // Already double; method kept for symmetry
        return src;
    }

    // ----------------------------------------------------------------
    // Getters / setters
    // ----------------------------------------------------------------

    /** Set the path to spmf.jar */
    public void setSPMFJarFilePath(String path) {
        this.spmfJarPath = path;
    }

    /** Get the current log file path */
    public String getRedirectOutputPath() {
        return logFilePath;
    }

    /** Set the path of the file that receives subprocess output */
    public void setRedirectOutputPath(String path) {
        this.logFilePath = path;
    }

    /** Get the string shown in tables for timed-out runs */
    public String getTimeoutCodeString() {
        return timeoutCodeString;
    }

    /** Set the string shown in tables for timed-out runs */
    public void setTimeoutCodeS(String s) {
        this.timeoutCodeString = s;
    }
}